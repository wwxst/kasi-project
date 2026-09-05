# Kasi 生产部署运行手册

## 文档状态

- 整理日期：2026-09-05
- 依据：2026-09-04 宝塔部署现场记录、当前仓库配置和已发布前端构建结果
- 适用环境：阿里云 ECS、宝塔、Apache 2.4、Docker、MySQL 8.0、Redis 7.2、Java 25
- 说明：本文是交接手册，不是实时探针。每次发布前仍要在服务器执行检查命令。

## 一、当前部署拓扑

~~~text
用户浏览器  ->  https://xm.kasi730.com      -> Apache 静态文件 + /api/ 代理
管理员浏览器 ->  https://xmadmin.kasi730.com -> Apache 静态文件 + /api/ 代理
                                                      |
                                                      v
                                         127.0.0.1:8080 Spring Boot
                                             |             |
                                             v             v
                                      MySQL 8.0:3307   Redis:6380
                                      Docker            Docker
~~~

| 组件 | 当前名称/地址 | 目录或端口 | 当前状态 |
|---|---|---|---|
| MySQL | Docker 容器 kasi_promotion，镜像 mysql:8.0.35 | 宿主机 3307 -> 3306，数据库 kasi_promotion | 已初始化，Flyway V1 |
| Redis | Docker 容器 kasi_redis，镜像 redis:7.2-alpine | 127.0.0.1:6380 -> 6379，无密码 | 已启用，数据目录 /www/wwwroot/kasixm/redis-data |
| 后端 | systemd 服务 spring_kasi_backend | 127.0.0.1:8080 | JAR /www/wwwroot/kasixm/backend/kasi-backend.jar |
| 后端配置 | 外部 properties 文件 | /www/wwwroot/kasixm/backend/kasi-backend.properties | Spring additional-location 读取 |
| 数据库迁移 | 独立 Maven runner | /www/wwwroot/kasixm/migration-runner | 应用启动不自动迁移 |
| 用户端 | https://xm.kasi730.com | /www/wwwroot/kasixm/user-web | HTTPS 200，HTTP 301 |
| 管理端 | https://xmadmin.kasi730.com | /www/wwwroot/kasixm/admin-web | HTTPS 200，HTTP 301 |
| 服务器 | 阿里云 ECS | 139.224.54.105（记录中的地址） | DNS 变更后须重新核对 |

### 当前实现边界

- 已实现：双端认证、后端 API、MySQL、Redis、阿里云短信验证码、Apache HTTPS 和 /api/ 代理。
- 当前头像仍写入本地：APP_UPLOAD_DIR 下的 user-avatars、admin-avatars；更换成功后清理旧本地头像。
- OSS Bucket kasixm 已创建，但当前发布版本没有 OSS SDK/上传服务。不要只填 OSS 配置就认为头像已转存 OSS。
- 正式账单、钱包、提现、自动对账和转化分析不属于首发实现。

## 二、发布包与目录

后端和两个前端分别发布：

~~~text
/www/wwwroot/kasixm/backend/kasi-backend.jar
/www/wwwroot/kasixm/backend/kasi-backend.properties
/www/wwwroot/kasixm/migration-runner/
/www/wwwroot/kasixm/user-web/
/www/wwwroot/kasixm/admin-web/
~~~

本机最近一次用户端发布包：

~~~text
E:\JavaProjects\kasi-project-release-20260904-user-web\kasi-user-dist.zip
~~~

前端压缩包解压后顶层必须直接包含：

~~~text
index.html
assets/
~~~

不能多套一层 dist/ 或项目目录。

## 三、服务器前置检查

FTP 不是必需项，第一版可通过宝塔文件管理、SFTP 或 SSH 上传。

~~~bash
hostname
date
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}'
/www/server/java/jdk-25.0.3/bin/java -version
/www/server/apache/bin/apachectl -t
~~~

安全组至少放行 80/tcp、443/tcp。3307 和 6380 不应对公网开放；当前 MySQL 曾显示 0.0.0.0:3307->3306，必须用安全组和主机防火墙限制来源。

## 四、MySQL Docker

### 4.1 检查

~~~bash
docker ps --filter name=kasi_promotion \
  --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}'
docker exec -it kasi_promotion mysql -uroot -p -e \
  "SELECT VERSION(), @@character_set_server, @@collation_server, @@time_zone;"
~~~

预期为 MySQL 8.0.x、utf8mb4，业务连接时区为 +08:00。

### 4.2 初始化新库或新用户

只在目标库尚未初始化时执行。密码通过交互提示输入，不要写入命令历史或本文。

~~~bash
docker exec -it kasi_promotion mysql -uroot -p
~~~

~~~sql
CREATE DATABASE IF NOT EXISTS kasi_promotion
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'kasi_app'@'%' IDENTIFIED BY '<数据库密码>';
ALTER USER 'kasi_app'@'%' IDENTIFIED BY '<数据库密码>';
GRANT ALL PRIVILEGES ON kasi_promotion.* TO 'kasi_app'@'%';
FLUSH PRIVILEGES;

SHOW DATABASES LIKE 'kasi_promotion';
SHOW GRANTS FOR 'kasi_app'@'%';
~~~

连接测试：

~~~bash
docker exec -it kasi_promotion mysql -ukasi_app -p -D kasi_promotion -e \
  "SELECT CURRENT_USER(), DATABASE();"
~~~

### 4.3 生产迁移规则

- 当前生产库已有 flyway_schema_history，V1 已执行；不要重复 baseline，不要执行 clean。
- 应用启动关闭 Flyway：spring.flyway.enabled=false。
- 后续只能新增 V2__...sql、V3__...sql，不能修改已经执行的迁移。
- 每次迁移前先备份 MySQL，并确认备份文件可读；任一步失败立即停止发布。

已经由旧初始化 SQL 创建、且经核对与 V1 完全一致的库，首次纳管才执行一次：

~~~bash
./mvnw -Pmigration flyway:baseline -Dflyway.baselineVersion=1
~~~

### 4.4 运行 migration-runner

~~~bash
cd /www/wwwroot/kasixm/migration-runner
chmod +x mvnw
export JAVA_HOME=/www/server/java/jdk-25.0.3
export PATH="$JAVA_HOME/bin:$PATH"
export FLYWAY_URL='jdbc:mysql://127.0.0.1:3307/kasi_promotion?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true'
export FLYWAY_USER='kasi_app'
read -r -s -p '请输入 kasi_app 数据库密码: ' FLYWAY_PASSWORD < /dev/tty
printf '\n'
export FLYWAY_PASSWORD

./mvnw -Pmigration flyway:info
./mvnw -Pmigration flyway:validate
./mvnw -Pmigration flyway:migrate
~~~

characterEncoding 必须写 UTF-8，不能写 utf8mb4。

## 五、Redis Docker

已有容器只检查，不要重复创建：

~~~bash
docker ps --filter name=kasi_redis \
  --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}'
docker exec -it kasi_redis redis-cli ping
docker inspect kasi_redis --format '{{range .Mounts}}{{println .Source "->" .Destination}}{{end}}'
~~~

预期为 PONG，并看到：

~~~text
/www/wwwroot/kasixm/redis-data -> /data
~~~

没有容器时：

~~~bash
mkdir -p /www/wwwroot/kasixm/redis-data
docker run -d \
  --name kasi_redis \
  --restart unless-stopped \
  -p 127.0.0.1:6380:6379 \
  -v /www/wwwroot/kasixm/redis-data:/data \
  redis:7.2-alpine \
  redis-server --appendonly yes
~~~

后端 Redis 配置：

~~~text
REDIS_HOST=127.0.0.1
REDIS_PORT=6380
REDIS_PASSWORD=
SPRING_DATA_REDIS_DATABASE=0
~~~

Redis 不可用时认证请求必须安全失败为 503，不能放行。

## 六、后端配置与 systemd

生产密码、JWT 和提供商主密钥只放外部配置或密钥服务，不提交 Git。配置文件建议权限为 600。

文件：/www/wwwroot/kasixm/backend/kasi-backend.properties

~~~properties
spring.datasource.url=jdbc:mysql://127.0.0.1:3307/kasi_promotion?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true
spring.datasource.username=kasi_app
spring.datasource.password=<数据库密码>

spring.flyway.enabled=false
app.jwt.secret=<JWT_SECRET>
app.provider-credentials.master-key=<PROVIDER_CREDENTIAL_MASTER_KEY>

spring.data.redis.host=127.0.0.1
spring.data.redis.port=6380
spring.data.redis.password=
spring.data.redis.database=0

app.upload.dir=/www/wwwroot/kasixm/backend/data/uploads
~~~

~~~bash
mkdir -p /www/wwwroot/kasixm/backend/data/uploads
chmod 600 /www/wwwroot/kasixm/backend/kasi-backend.properties
~~~

当前启动命令：

~~~bash
/www/server/java/jdk-25.0.3/bin/java \
  -Duser.timezone=Asia/Shanghai \
  -Xms256M -Xmx1024M \
  -jar /www/wwwroot/kasixm/backend/kasi-backend.jar \
  --spring.config.additional-location=file:/www/wwwroot/kasixm/backend/kasi-backend.properties
~~~

重启、日志和健康检查：

~~~bash
systemctl restart spring_kasi_backend
systemctl status spring_kasi_backend --no-pager
journalctl -u spring_kasi_backend --since '10 minutes ago' --no-pager
curl -i http://127.0.0.1:8080/actuator/health
~~~

预期 HTTP 200 且 status 为 UP。Connection refused 时先看 systemctl 和 journalctl。

## 七、Apache 与宝塔站点

| 域名 | DocumentRoot | API 目标 |
|---|---|---|
| xm.kasi730.com | /www/wwwroot/kasixm/user-web | http://127.0.0.1:8080/api/ |
| xmadmin.kasi730.com | /www/wwwroot/kasixm/admin-web | http://127.0.0.1:8080/api/ |

宝塔配置文件：

~~~text
/www/server/panel/vhost/apache/html_xm.kasi730.com.conf
/www/server/panel/vhost/apache/html_xmadmin.kasi730.com.conf
~~~

Apache 必须满足：

- /、/login 等 SPA 路由回退到 index.html，刷新不能 404。
- /api/ 代理到 127.0.0.1:8080/api/。
- /uploads/ 代理到 127.0.0.1:8080/uploads/。
- HTTP 301 跳 HTTPS。
- /.well-known/acme-challenge/ 不被 SPA 重写。

伪静态规则应“真实文件/目录直接返回，否则回退 /index.html”，不要把 API 和 ACME 路径回退到前端。

### SSL 核对

宝塔列表显示 SSL 未部署不一定代表 Apache 没有 HTTPS，以实际响应和证书为准。

~~~bash
/www/server/apache/bin/apachectl -t
/www/server/apache/bin/apachectl -k graceful
curl -I http://xm.kasi730.com
curl -I https://xm.kasi730.com
curl -I http://xmadmin.kasi730.com
curl -I https://xmadmin.kasi730.com
~~~

HTTP 预期 301，HTTPS 预期 200。证书目录通常为：

~~~text
/www/server/panel/vhost/cert/xm.kasi730.com/
/www/server/panel/vhost/cert/xmadmin.kasi730.com/
~~~

~~~bash
openssl x509 -in /www/server/panel/vhost/cert/xm.kasi730.com/fullchain.pem \
  -noout -subject -issuer -dates -checkhost xm.kasi730.com
openssl x509 -in /www/server/panel/vhost/cert/xmadmin.kasi730.com/fullchain.pem \
  -noout -subject -issuer -dates -checkhost xmadmin.kasi730.com
~~~

证书续期时，ACME 别名可能使用：

~~~text
Alias /.well-known/ /www/wwwroot/java_node_ssl/
/www/wwwroot/java_node_ssl/acme-challenge/
~~~

先用域名 curl 验证挑战文件可读，再申请/续期证书。

## 八、前端上传与验证

用户端解压到 /www/wwwroot/kasixm/user-web，管理端解压到 /www/wwwroot/kasixm/admin-web。覆盖静态文件后：

~~~bash
/www/server/apache/bin/apachectl -t
/www/server/apache/bin/apachectl -k graceful
~~~

验证前端和 API：

~~~bash
curl -I https://xm.kasi730.com/login
curl -I https://xmadmin.kasi730.com/login
curl -i -X POST https://xm.kasi730.com/api/user/auth/login \
  -H 'Content-Type: application/json' -d '{}'
curl -i -X POST https://xmadmin.kasi730.com/api/admin/auth/login \
  -H 'Content-Type: application/json' -d '{}'
~~~

空登录请求预期返回后端 JSON 校验错误，例如“账号不能为空；密码不能为空”，这能证明 /api/ 已代理到后端。

## 九、短信验证码

短信配置由超级管理员在管理端保存，AccessKey 只加密保存，接口不回显明文。当前生产配置记录：

~~~text
enabled = 1
sign_name = 卡司科技
register_template_code = SMS_512290238
login_template_code = SMS_512095251
reset_password_template_code = SMS_512405236
~~~

阿里云短信模板变量使用：

~~~text
${code}
~~~

用户端手机号验证码接口：

~~~text
注册：       /api/user/auth/register/code
验证码登录： /api/user/auth/login/code
找回密码：   /api/user/auth/password/forgot/code
~~~

验证码有效期 300 秒，重发间隔 60 秒，每日上限 10 次。生产验证使用真实手机号，同时查看后端日志和阿里云发送记录；日志不应出现完整手机号、验证码或 AccessKey。

## 十、OSS 当前状态

已创建 Bucket：

~~~text
Bucket：kasixm
地域：华东 2（上海）
Endpoint：oss-cn-shanghai.aliyuncs.com
存储：标准存储
冗余：同城冗余
读写：私有
阻止公共访问：开启
服务端加密：OSS 完全托管，AES256
~~~

当前代码仍使用 app.upload.dir 的本地文件路径，因此不要把 APP_UPLOAD_DIR 填成 OSS Endpoint，也不要把 AccessKey 放进前端。后续接入 OSS 还需要后端 SDK、对象上传服务、权限配置和私有对象临时签名 URL。

## 十一、标准发布顺序

1. 本地运行后端 mvn verify、管理端 pnpm check、用户端 pnpm check，并确认 git diff --check。
2. 上传发布包到服务器临时目录，核对 SHA-256。
3. 备份 MySQL 并确认备份可读；涉及 schema 时先执行 Flyway info、validate、migrate。
4. 复制新的 JAR 和外部配置，检查权限后重启 spring_kasi_backend。
5. 检查 actuator/health、后端日志和 127.0.0.1:8080。
6. 覆盖前端静态文件，执行 Apache -t 和 graceful reload。
7. 验证两个域名的 HTTP 301、HTTPS 200、SPA 刷新、空登录和真实登录/短信流程。
8. 记录发布版本、迁移版本、构建包哈希和验证结果。

## 十二、常见问题

### java: command not found

~~~bash
export JAVA_HOME=/www/server/java/jdk-25.0.3
export PATH="$JAVA_HOME/bin:$PATH"
java -version
~~~

### 输入数据库密码后命令没有继续

不要把 read、echo 或密码文本混在同一行粘贴，只执行：

~~~bash
read -r -s -p '请输入数据库密码: ' FLYWAY_PASSWORD < /dev/tty
printf '\n'
export FLYWAY_PASSWORD
~~~

### 8080 refused

~~~bash
systemctl status spring_kasi_backend --no-pager
journalctl -u spring_kasi_backend -n 100 --no-pager
ss -lntp | grep ':8080'
~~~

### ACME 返回 404

核对 Alias 目标目录、挑战文件、权限和 DNS，再执行：

~~~bash
curl -i http://xm.kasi730.com/.well-known/acme-challenge/<文件名>
~~~

### HTTPS 证书域名不匹配

~~~bash
echo | openssl s_client -connect 127.0.0.1:443 \
  -servername xm.kasi730.com 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates
~~~

替换证书后执行 apachectl -t 和 apachectl -k graceful。

### 宝塔仍显示 SSL 未部署

宝塔状态可能没有识别手工添加的 443 VirtualHost。只要 HTTPS 返回 200、证书 checkhost 匹配、Apache 配置测试通过，就以实际 HTTPS 结果为准。

## 十三、安全检查清单

- [ ] 数据库密码、JWT_SECRET、PROVIDER_CREDENTIAL_MASTER_KEY、短信和 OSS AccessKey 未写入 Git、前端或本文。
- [ ] 外部后端配置文件权限为 600。
- [ ] MySQL 3307 未对公网开放，Redis 6380 只监听本机。
- [ ] OSS 保持私有、阻止公共访问；当前代码未接入 OSS 时不改 APP_UPLOAD_DIR。
- [ ] 主账号 AccessKey 仅作为临时方案，后续换成限定目标 Bucket 的 RAM 用户。
- [ ] 生产 schema 只通过 Flyway 升级，不执行 clean、删库重建或修改历史迁移。
- [ ] 发布后保存 JAR、前端包、迁移版本和 SHA-256 记录。
