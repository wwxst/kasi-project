# 生产启动自动执行 Flyway 迁移设计

## 状态

已确认设计，尚未实施。

## 背景

当前生产 MySQL 运行在 Docker 容器 `kasi_promotion` 中，后端以 systemd 管理的 Spring Boot JAR 运行，并通过宿主机端口 `127.0.0.1:3307` 连接数据库。生产库已经存在 `flyway_schema_history`，V1 已执行；当前 V2 至 V5 由独立 Maven migration runner 在发布前执行，应用启动明确关闭 Flyway。

目标是让默认运行环境（包括生产环境）在后端启动时自动校验并执行尚未应用的版本迁移。MySQL 容器本身不安装或运行 Flyway，也不改变端口、数据卷和账号配置。

## 方案比较

### 方案一：Spring Boot 启动时自动迁移（采用）

后端 JAR 包含 Flyway 运行时依赖，Spring Boot 使用现有 datasource 在应用完成启动前执行迁移。迁移失败时应用启动失败。

优点是发布动作最少，部署新 JAR 并重启后端即可应用同版本迁移；数据库版本与应用版本不会脱节。代价是 DDL 执行与应用启动绑定，首次启用前必须备份并确认生产库已被 Flyway 纳管。

### 方案二：systemd `ExecStartPre` 调用 migration runner

在启动 JAR 前由 systemd 显式运行 Maven Flyway。它仍能实现一次发布命令完成迁移和启动，但服务器必须长期保留 Maven runner、源码迁移目录和两套数据库配置。

该方案没有被采用，因为它没有减少现有部署组件，并继续维护 JAR 与 runner 的版本一致性问题。

### 方案三：独立迁移容器

发布时先运行一次包含 Flyway CLI 和迁移脚本的短生命周期容器，再启动后端。该方案适合后端也由 Compose 或 Kubernetes 编排的环境。

当前后端不是容器而是 systemd JAR，引入迁移镜像和编排只会扩大基础设施，因此不采用。

## 运行时设计

- 在后端常规依赖中加入 Spring Boot Flyway starter 和 MySQL 数据库支持，使可执行 JAR 包含 Flyway runtime。
- 默认配置启用 `spring.flyway.enabled=true`，迁移位置固定为 `classpath:db/migration`。
- 保持 `baselineOnMigrate=false`、`validateOnMigrate=true`、`validateMigrationNaming=true`、`outOfOrder=false` 和 `cleanDisabled=true`。
- 保留 Maven `migration` profile，用于发布前或故障排查时手动执行 `info`、`validate`、`migrate`，但它不再是正常发布的必选步骤。
- 测试 profile 继续使用 H2 `test-schema.sql`，显式设置 `spring.flyway.enabled=false`，避免 Flyway 与测试 schema 初始化重复建表。

生产后端连接 `127.0.0.1:3307` 后，Flyway读取同一 datasource 的账号和密码。当前生产库已记录 V1，因此首次启用时只校验 V1 checksum 并执行待处理的 V2 至 V5；不得再次 baseline，也不得执行 clean。

服务器外部文件 `/www/wwwroot/kasixm/backend/kasi-backend.properties` 当前显式关闭 Flyway。发布新 JAR 时必须把该值改为 `true` 或删除该覆盖项，否则外部配置会继续覆盖 JAR 内默认值。

## 启动与失败行为

1. systemd 启动后端 JAR。
2. Spring Boot 创建 datasource 并连接 Docker MySQL。
3. Flyway获取 schema history 锁，校验已执行迁移并按版本顺序执行待处理迁移。
4. 全部迁移成功后继续创建应用上下文并开放服务。
5. 连接失败、checksum 不一致或任何迁移失败时，后端启动失败；不绕过迁移继续运行。

首次启用前必须备份生产数据库并确认备份可读。自动迁移不替代备份，也不自动回滚 MySQL DDL。失败后的处理方式仍是根据数据状态恢复备份，或新增更高版本的正向修复迁移。

## 文档与发布契约

更新 ADR、仓库入口、治理规则、后端 README、后端 Agent 指南和生产部署手册，使其统一描述以下当前规则：

- 生产 schema 的版本真相仍是不可变 `V*.sql` 链。
- 默认运行环境由应用启动自动执行迁移。
- 测试 profile 不执行 Flyway，继续使用 H2 schema。
- Maven migration runner 保留为手动检查和应急工具。
- 发布顺序改为先备份数据库，再部署并启动 JAR，由启动日志和 `flyway_schema_history` 验证迁移结果。

## 验证

- 修改 `DatabaseSchemaSourceTest`，先确认旧契约下失败，再验证运行时依赖、默认启用、安全属性、测试 profile 禁用及 Maven profile 保留。
- 运行该聚焦测试，确认新契约通过。
- 运行后端 `mvn verify` canonical Gate。
- 运行 `git diff --check` 并复核只包含本次迁移契约相关文件。
- 没有生产服务器访问和凭据时，不把真实生产启动迁移写成已验证；生产验收必须以备份成功、systemd 启动日志、Flyway执行结果和 `flyway_schema_history` 为准。

## 非目标

- 不修改任何已执行的 `V*.sql`，不新增 schema 变更。
- 不启用 `baselineOnMigrate`、out-of-order 或 Flyway clean。
- 不把 MySQL 或后端改造成新的 Docker Compose 架构。
- 不在仓库中保存生产数据库密码或服务器外部配置文件。
