# Kasi Backend 开发文档

最后核对时间：2026-08-21

## 1. 项目定位

这是卡司推广平台的后端仓库，基于 Spring Boot 4.0.7 + MyBatis 4.0.1 + MySQL 8 + JWT 构建。

**当前已完成**：管理员（ADMIN）和推广用户（USER）双认证体系、两类账号管理 CRUD、短剧平台接入与 GoodShort 账号报备，以及 GoodShort 短剧目录全量/增量同步、管理员目录管理和定时调度。详见 [§6 API、认证与业务边界](#6-api认证与业务边界)。

## 2. 当前结构

```text
src/
  main/
    java/com/kasi/backend/
      KasiBackendApplication.java          # 启动入口
      admin/                                # 管理员认证与账号管理模块
        controller/AdminAuthController.java # /api/admin/auth/* 控制器
        controller/AdminManagementController.java # /api/admin/management/* 控制器
        service/AdminAuthService.java       # 管理员认证服务接口
        service/impl/AdminAuthServiceImpl.java # 管理员认证业务实现
        service/AdminManagementService.java # 管理员管理服务接口
        service/impl/AdminManagementServiceImpl.java # 管理员管理业务实现
        entity/SysAdminUser.java            # 管理员实体
        mapper/SysAdminUserMapper.java      # 管理员 MyBatis Mapper
        dto/                                # 管理员请求 DTO
        vo/                                 # 管理员响应 VO
      user/                                 # 推广用户认证与管理模块
        controller/UserAuthController.java  # /api/user/auth/* 控制器
        service/UserAuthService.java        # 用户认证服务接口
        service/impl/UserAuthServiceImpl.java # 用户认证业务实现
        entity/PromotionUser.java           # 用户实体
        mapper/PromotionUserMapper.java     # 用户 MyBatis Mapper
        dto/                                # 用户 DTO（注册/登录/重置密码等）
      auth/                                 # 可复用的认证基础设施
        entity/PasswordResetTokenReservation.java # 密码重置 Token 预占模型
        service/PasswordResetTokenService.java # 密码重置 Token 服务接口
        service/VerificationCodeService.java # 验证码服务接口
        service/impl/PasswordResetTokenServiceImpl.java # 密码重置 Token Redis 实现
        service/impl/VerificationCodeServiceImpl.java # 验证码 Redis 实现
      security/                             # 安全基础
        config/SecurityConfig.java          # Spring Security 配置
        context/AuthContext.java            # 认证上下文
        context/AuthContextHolder.java      # 请求级上下文持有者（ThreadLocal）
        entity/                             # 会话版本与变更模型
        filter/JwtAuthenticationFilter.java # JWT 认证过滤器
        service/TokenService.java           # JWT 服务接口
        service/SessionService.java         # 会话服务接口
        service/impl/TokenServiceImpl.java  # JWT 生成与解析实现
        service/impl/SessionServiceImpl.java # Redis 账号版本与单会话状态实现
      provider/                             # 短剧平台定义和接入账号内部管理
        controller/                         # /api/admin/drama/providers 管理接口
        entity/                             # 平台与接入账号持久化实体
        mapper/                             # 平台与接入账号单表 Mapper
        enums/                              # 平台能力枚举
        spi/                                # 平台适配器与密钥边界
        goodshort/                          # GoodShort 签名、HTTP 探测和配置
        dto/                                # 接入账号配置请求 DTO
        vo/                                 # 不含平台密钥的管理响应 VO
        service/                            # 密钥加密与接入账号管理服务接口
        service/impl/                       # AES-GCM 与接入账号管理实现
      drama/                                # 短剧目录、剧集、同步检查点、管理员 API 和调度
      common/                               # 公共组件
        response/ApiResponse.java           # 统一响应体
        exception/ErrorCode.java            # 错误码枚举
        exception/BusinessException.java    # 业务异常
        exception/GlobalExceptionHandler.java # 全局异常处理器
        enums/                              # SubjectType、VerificationScene、TargetType、UserStatus
    resources/
      application.properties                # 数据源、Flyway、MyBatis、JWT、验证码配置
      db/migration/
        V1__kasi_promotion.sql              # 基础账号表和默认超级管理员
        V2__media_account_filing.sql       # 平台接入、媒体账号和通用报备表
        V4__media_filing_task_version.sql  # 报备任务资料版本隔离
        V7__drama_catalog_sync.sql          # 短剧目录、剧集与同步检查点
      mapper/                               # 2个 MyBatis XML 映射文件
  test/
    java/com/kasi/backend/
      BaseAuthTest.java                     # 测试基类（H2 + 数据初始化）
      DefaultSuperAdminMigrationTest.java   # 生产迁移初始化验证
      admin/controller/AdminAuthControllerTest.java
      user/controller/UserAuthControllerTest.java
      security/SecurityPermissionTest.java
    resources/
      application-test.properties           # 测试环境配置（H2）
      test-schema.sql                       # 测试表结构
scripts/
  dev/
    seed_goodshort_drama_catalog.sql        # 手动执行的 GoodShort 本地目录假数据
```

### Java model naming

- Request models live in `*.dto` and use the `DTO` suffix.
- Response models live in `*.vo` and use the `VO` suffix.
- Shared authentication requests live in `auth/dto`, such as `ChangePasswordDTO`.
- Service contracts use `*Service`; implementations live in `impl` and use `*ServiceImpl`.

## 3. 构建工具链

- Maven 坐标：`com.kasi:kasi-backend:0.0.1-SNAPSHOT`。
- Spring Boot：`4.0.7`。
- Java：`25`，由 [pom.xml](pom.xml) 的 `java.version` 定义。
- Maven Wrapper：`3.9.16`，入口是 `mvnw.cmd`。
- 默认打包类型：JAR。

PowerShell 中先确认 Java 版本：

```powershell
java -version
.\mvnw.cmd -v
```

如果终端仍使用 Java 21，需要临时切换到 Java 25：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## 4. 配置与启动

[application.properties](src/main/resources/application.properties) 已配置了以下内容：

| 配置项 | 说明 |
|--------|------|
| 数据源 | MySQL，必须通过环境变量 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` 注入，**字符编码统一 UTF-8** |
| Flyway | 启用，迁移脚本路径 `classpath:db/migration`；不对无 Flyway 历史的非空数据库自动建立基线 |
| MyBatis | Mapper XML 路径 `classpath:mapper/*.xml`，开启驼峰自动映射 |
| JWT | 密钥通过 `JWT_SECRET` 环境变量注入，过期时间 7200 秒；登录会话依赖 Redis |
| 验证码 | 过期 300 秒，重发间隔 60 秒，每日上限 10 次 |
| 密码重置 Token | 过期 600 秒 |
| 验证码发送器 | `local` profile 使用 Console sender；`test` profile 使用测试 sender；生产环境需提供真实实现 |
| 平台密钥主密钥 | 必须通过 `PROVIDER_CREDENTIAL_MASTER_KEY` 注入 Base64 编码的 32 字节密钥；不得提交到仓库或写入日志 |
| GoodShort 探测 | 接口 URL 从平台接入配置读取，连接超时 3 秒、读取超时 10 秒；平台密钥从数据库密文解密后仅在适配器调用链内使用 |
| 短剧目录同步 | 默认语言 `ENGLISH`、每页 100 条、每 5 分钟调度；支持配置语言、批量、分页、租约和调度开关 |

应用要连接 MySQL，至少需要提供：

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3306/kasi_promotion'
$env:SPRING_DATASOURCE_USERNAME = '<database-user>'
$env:SPRING_DATASOURCE_PASSWORD = '<database-password>'
```

之后启动：

```powershell
.\mvnw.cmd spring-boot:run
```

首次启动时 Flyway 会按版本扫描 `db/migration/`，执行 V1、V2 和 V3，创建账号、平台接入、媒体账号和通用报备表，并由 V1 植入唯一的初始超级管理员：

- 账号：`kasiadmin`
- 初始密码：`kasi123456`

密码在数据库中保存为 BCrypt 哈希。首次登录后应立即通过 `PUT /api/admin/auth/password` 修改默认密码。项目当前仍处于可重建数据库的开发阶段；如果开发数据库已经执行过旧版 `V1`，应删除并重新创建数据库，不能在保留旧 Flyway 校验和的情况下直接替换迁移脚本。

### GoodShort 本地目录假数据

`scripts/dev/seed_goodshort_drama_catalog.sql` 仅用于当前本地开发 MySQL 在尚未取得真实 PID/KEY 前准备目录联调数据，禁止用于生产、预发布或共享测试环境。它不属于 Flyway，应用启动和运行时都不会自动执行。脚本创建的安全连接固定为名称 `GoodShort 本地假数据`、币种 `USD`、禁用状态、`MANUAL` 报备模式，不写入 PID、KEY 密文或接口地址，也不会发起远端请求。连接、短剧、剧集和检查点的内部 ID 均由数据库自增；短剧外部 ID 为 `99000001..99000024`。

脚本可重复执行，预期得到 24 部短剧、204 条剧集内容和 4 个同步检查点。若已存在真实或非完全匹配的 GoodShort 连接，脚本会拒绝执行且不会覆盖；即使客户端在守卫报错后继续发送语句，脚本也会通过 DML 守卫拒绝写入。必须使用遇到首个错误即停止的 SQL 客户端执行，严禁 mysql `--force`；任何错误后先执行 `ROLLBACK` 并关闭连接，再重新尝试。

请显式连接到本地开发 schema，再以 UTF-8 文件输入运行。例如使用 mysql CLI 的批处理模式（不提供密码参数，不使用 `--force`）：

```powershell
mysql --host=127.0.0.1 --user=<database-user> --database=kasi_promotion --default-character-set=utf8mb4 --batch < scripts/dev/seed_goodshort_drama_catalog.sql
```

命令中不要打印或嵌入数据库密码。

测试环境使用 H2 内存数据库（MySQL 兼容模式），通过 `@ActiveProfiles("test")` 激活 [application-test.properties](src/test/resources/application-test.properties)，无需本地 MySQL。

## 5. 数据库现状

### 已实现的表结构（V1 至 V7）

迁移脚本 V1 至 V7 定义当前数据库持久表，验证码和密码重置 Token 等临时数据由 Redis 管理：

| 存储 | 表/Key | 说明 | 核心字段 |
|------|--------|------|----------|
| MySQL | `sys_admin_user` | 后台管理员用户 | username, password(BCrypt), real_name, mobile, email, status, is_super_admin |
| MySQL | `promotion_user` | 推广用户 | user_no(12位随机数字字符串), password(BCrypt), nickname, mobile, email, status, register_source |
| MySQL | `short_drama_provider` | 短剧平台定义 | provider_code, provider_name, status |
| MySQL | `short_drama_connection` | 平台机构接入账号（仅保存密钥密文；人工报备可不配置 API 凭据） | provider_id, base_url, partner_id, api_key_ciphertext, filing_mode, status |
| MySQL | `promotion_media_account` | 推广用户绑定的媒体账号（不可物理删除） | user_id, media_type, external_account_id, account_name, account_link, status, data_version |
| MySQL | `provider_media_filing` | 媒体账号按平台保存的报备状态和任务信息 | connection_id, media_account_id, status, next_action, retry_count |
| MySQL | `provider_drama` | 按接入账号保存的短剧目录，本地状态与远端状态分离 | connection_id, external_drama_id, language, remote_show_status, local_status |
| MySQL | `provider_drama_content` | 短剧剧集元数据 | drama_id, external_content_id, sequence_no, is_free, duration_seconds |
| MySQL | `provider_sync_checkpoint` | 全量/增量同步断点、统计、错误和数据库租约 | connection_id, sync_type, language, page_no, update_time, lease_owner, lease_until |
| Redis | `vc:*` | 验证码（临时） | 5分钟过期，60秒重发间隔，每日上限10次 |
| Redis | `pwd:*` | 密码重置 Token（临时） | 10分钟过期，一次性消费后删除 |
| Redis | `auth:version:*` | 账号会话版本（含 `ACTIVE:*` 或 `MUTATING:*`） | TTL 不超过 JWT 有效期加宽限期 |
| Redis | `auth:session:*` | 单个 JWT 会话（按 `jti`） | TTL 与 JWT 有效期一致，退出时删除 |

`V1__kasi_promotion.sql` 在建表后直接插入 `kasiadmin`，并固定写入 `status=1`、`is_super_admin=1`。该初始化同时用于开发环境重建和未来生产环境首次建库，不会在应用每次启动时重复执行。V2 只植入启用的 `GOODSHORT` 平台定义，不植入任何平台接入密钥；V3 为平台接入配置增加可由后台维护的 `base_url`。

当前已完成平台定义与接入账号持久层、AES-GCM 密钥加密、不暴露密钥的管理服务和管理员 API，以及 GoodShort 签名和连接探测适配器。媒体账号绑定与通用报备模块也已完成后端闭环：推广用户可绑定多个媒体账号，同一媒体平台账号全局唯一；创建媒体账号时不选择单个平台，系统会为所有已启用、接入配置完整且适配器声明支持账号报备的平台分别建立报备记录；系统通过 GoodShort `/open/filing/report` 和 `/open/filing/query` 完成报备提交与审核查询，持久任务支持租约、资料版本隔离、临时失败重试和三态（审核中、已加白、已失败）；用户和管理员查询/重试接口已接入，绑定媒体账号的推广用户只能禁用不能物理删除。平台接入配置支持 API 自动报备和人工报备两种模式：API 模式必须填写接口 URL、PID、KEY，人工模式无需保存这些 API 凭据，由管理员维护报备状态。

当前已实现 GoodShort 短剧目录全量 `initBooks`、增量 `incrementBooks`、断点恢复、数据库租约、定时/手动触发、管理员查询详情和本地上下架；远端未返回记录不会物理删除，本地状态不会被同步覆盖。当前仍未实现推广链接、订单、佣金计算、导出和转化分析；推广用户端页面仍待接入。

> **说明**：`sys_sequence` 表已移除，`user_no` 由后端在插入前随机生成；`promotion_user.id` 继续作为自增内部主键。`auth_verification_code` 和 `auth_password_reset_token` 表已移除，改用 Redis 存储（更高效、自动过期）。

## 6. API、认证与业务边界

### 6.1 认证架构

系统采用 **JWT + Redis 会话状态认证**，通过 `subjectType` 字段区分两种身份，实现严格的权限隔离。JWT 载荷包含 `jti` 和 `sessionVersion`；受保护请求除验签外，还必须通过 Redis 账号版本和单会话校验，并回查账号状态：

| 角色 | 路由前缀 | 权限 | 说明 |
|------|----------|------|------|
| ADMIN | `/api/admin/**` | `ROLE_ADMIN` | 管理员，仅可访问 admin 接口 |
| SUPER_ADMIN | `/api/admin/management/**` | `ROLE_SUPER_ADMIN` | 唯一超级管理员，可管理普通管理员 |
| ADMIN | `/api/user/management/**` | `ROLE_ADMIN` | 超级管理员和普通管理员均可管理推广用户 |
| USER | `/api/user/**` | `ROLE_USER` | 推广用户，仅可访问 user 接口 |

- ADMIN Token **不可**访问 USER 接口（返回 403）
- USER Token **不可**访问 ADMIN 接口（返回 403）
- 无 Token 访问受保护接口返回 401
- Redis 不可用时认证安全失败并返回 503，不降级放行；账号会话版本不存在时旧 JWT 直接失效并返回 401。
- 普通退出仅删除当前 `auth:session:{jti}`；修改密码、密码重置等敏感变更会先切换为 `MUTATING:{nonce}`，数据库成功后生成新的账号版本，使该账号旧 Token 全部失效。
- `ROLE_SUPER_ADMIN` 每次请求根据数据库中的 `is_super_admin` 派生，不写入或信任 JWT 权限声明；当前是简单超级管理员控制，不是 RBAC。

### 6.2 管理员认证 API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/admin/auth/login` | 管理员登录（账号/手机号/邮箱 + 密码） | 否 |
| GET | `/api/admin/auth/me` | 获取当前管理员信息 | ADMIN |
| POST | `/api/admin/auth/logout` | 退出登录 | ADMIN |
| PUT | `/api/admin/auth/password` | 修改本人密码（新密码 + 确认密码，无需原密码；成功后旧 Token 失效） | ADMIN |
| PUT | `/api/admin/auth/profile` | 修改本人账号、真实姓名、手机、邮箱和头像 | ADMIN |

### 6.3 管理员管理 API

以下端点只允许唯一超级管理员访问；普通管理员和推广用户返回 403。普通管理员不能被提升为超级管理员，管理接口不能编辑、禁用、重置或删除超级管理员本人。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/management` | 分页和单关键词搜索，默认 `page=1,size=20`，按 `id ASC` |
| GET | `/api/admin/management/{id}` | 获取管理员详情 |
| POST | `/api/admin/management` | 新增启用状态的普通管理员 |
| PUT | `/api/admin/management/{id}` | 编辑普通管理员资料 |
| PATCH | `/api/admin/management/{id}/status` | 启用或禁用普通管理员 |
| PUT | `/api/admin/management/{id}/password` | 重置普通管理员密码 |
| DELETE | `/api/admin/management/{id}` | 物理删除普通管理员 |

管理员只使用必填 `realName`，没有昵称字段；`sys_admin_user` 不保留 `deleted_at`。修改账号、手机号、邮箱、状态、密码或删除前，服务先将目标账号 Redis 版本切换为 `MUTATING`；Redis 失败时不执行 MySQL 写入。MySQL 提交成功后恢复新的 `ACTIVE` 版本，使旧 Token 全部失效。物理删除后原账号、手机号和邮箱可以重新使用。

### 6.4 推广用户认证 API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/user/auth/register` | 用户注册（手机号或邮箱 + 验证码 + 密码） | 否 |
| POST | `/api/user/auth/register/code` | 发送注册验证码（场景由后端固定为 `REGISTER`） | 否 |
| POST | `/api/user/auth/login` | 用户登录（手机号或邮箱 + 密码） | 否 |
| GET | `/api/user/auth/me` | 获取当前用户信息 | USER |
| POST | `/api/user/auth/logout` | 退出登录 | USER |
| PUT | `/api/user/auth/password` | 修改密码（需旧密码） | USER |
| POST | `/api/user/auth/password/forgot/code` | 发送忘记密码验证码 | 否 |
| POST | `/api/user/auth/password/forgot/verify` | 校验验证码，返回重置 Token | 否 |
| POST | `/api/user/auth/password/reset` | 使用重置 Token 修改密码 | 否 |

推广用户没有独立 `username`。`userNo` 是 12 位随机数字展示编号，不参与登录、鉴权或数据库关联；内部关联继续使用自增 `id`。普通用户登录和 `/api/user/auth/me` 的 JSON 不返回内部 `id`，但 JWT `sub` 仍按现有认证契约保存内部 `id`。手机号和邮箱至少保留一个，用户同时拥有两者时均可登录。手机号统一 `trim`，邮箱统一 `trim` 后转小写。

### 6.5 推广用户管理 API

以下端点允许超级管理员和普通管理员访问，统一要求 `ROLE_ADMIN`；推广用户返回 403，未登录返回 401。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/user/management` | 分页和单关键词搜索，默认 `page=1,size=20`，按 `id ASC` |
| GET | `/api/user/management/{id}` | 获取推广用户详情 |
| POST | `/api/user/management` | 无需验证码，直接新增启用状态推广用户 |
| PUT | `/api/user/management/{id}` | 编辑昵称、姓名、联系方式、头像和备注 |
| PATCH | `/api/user/management/{id}/status` | 启用或禁用推广用户 |
| PUT | `/api/user/management/{id}/password` | 管理员重置推广用户密码 |
| DELETE | `/api/user/management/{id}` | 物理删除推广用户 |

联系人、状态、密码或删除等敏感变更会先将 Redis 会话版本切换为 `MUTATING`；Redis 失败时不执行 MySQL 写入，数据库提交成功后生成新的 `ACTIVE` 版本，使全部旧 Token 失效。只修改昵称、姓名、头像或备注不会使会话失效。`promotion_user` 不保留 `deleted_at`，物理删除后原手机号和邮箱可以复用。

### 6.6 短剧平台接入管理 API

平台配置查询允许普通管理员和超级管理员访问；写入平台 URL、PID、KEY 与连接探测只允许超级管理员。KEY 更新时可省略以保留现有密文；所有响应只返回 `credentialConfigured`，不会返回明文密钥、密文或掩码片段。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/admin/drama/providers` | ADMIN | 查询平台、能力声明和接入账号非敏感资料 |
| PUT | `/api/admin/drama/providers/{providerId}/connection` | SUPER_ADMIN | 新增或更新平台 URL、PID、KEY 和启用状态；更新时可省略 KEY 以保留原密文 |
| POST | `/api/admin/drama/providers/{providerId}/connection/test` | SUPER_ADMIN | 解密现有凭据并执行 GoodShort 最小连接探测，不保存返回短剧 |

### 6.7 短剧目录管理 API

以下端点要求 `ROLE_ADMIN`，普通管理员和超级管理员均可使用；推广用户返回 403，未登录返回 401。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/drama/catalog` | 分页查询目录，支持平台、标题、语言、远端状态和本地状态筛选 |
| GET | `/api/admin/drama/catalog/{id}` | 查询短剧详情和剧集元数据，不返回连接 ID、PID、密钥或租约字段 |
| POST | `/api/admin/drama/catalog/sync` | 创建 FULL 或 INCREMENTAL 同步任务，不等待第三方同步完成 |
| GET | `/api/admin/drama/catalog/sync/status` | 查询各语言检查点、统计和最近错误 |
| PATCH | `/api/admin/drama/catalog/{id}/status` | 将本地状态修改为 `PUBLISHED` 或 `OFFLINE` |

同步默认语言为 `ENGLISH`。全量使用 GoodShort `/open/book/initBooks`，增量使用 `/open/book/incrementBooks`；没有成功全量基线时，增量请求自动升级为全量。同一连接和语言只允许一个 FULL/INCREMENTAL 任务排队或运行。

### 6.8 统一响应格式

所有接口返回统一结构 `ApiResponse<T>`：

```json
{
  "code": 0,
  "message": "success",
  "data": { ... }
}
```

- `code=0`：成功
- `code!=0`：失败（错误码定义见 [ErrorCode.java](src/main/java/com/kasi/backend/common/exception/ErrorCode.java)）

### 6.9 技术实现要点

- **密码存储**：BCrypt 哈希，使用 Spring Security `PasswordEncoder`
- **JWT**：HMAC-SHA256 签名，载荷包含 `userId`、`subjectType`、登录标识、`jti`、`sessionVersion`，过期时间 7200 秒；每次受保护请求都校验 Redis 会话状态
- **验证码**：SHA-256 哈希后存入 Redis（内存不存明文），TTL 5 分钟自动过期，60 秒重发间隔，每日上限 10 次
- **密码重置**：验证码校验通过后颁发一次性重置 Token（SHA-256 哈希后存入 Redis），同一用户同时只保留一个有效 Token；使用 `READY -> PROCESSING` 原子预占，数据库成功后删除，异常时不自动恢复，TTL 10 分钟
- **Redis 故障**：验证码、密码重置 Token 和会话状态无法确认时统一安全失败并返回 503
- **验证码发送**：仅 `local` profile 输出 Console；`test` profile 使用测试捕获 sender；生产环境未配置真实 sender 时不允许启动为可发送状态
- **用户编号**：使用长期复用的 `SecureRandom` 在插入前生成首位非零的 12 位数字字符串；唯一索引冲突时最多重试 3 次

## 7. 测试现状

### 测试基础设施

- 测试基类：[BaseAuthTest.java](src/test/java/com/kasi/backend/BaseAuthTest.java)，每个测试方法前自动清理数据并插入基础测试数据
- 测试数据库：H2 内存数据库（MySQL 兼容模式），通过 `@ActiveProfiles("test")` 激活
- 测试表结构：[test-schema.sql](src/test/resources/test-schema.sql)，与生产表结构一致（H2 兼容语法）
- 测试 Redis 使用随机可用端口的嵌入式实例；`BaseAuthTest` 的 MockMvc 接入真实 Spring Security FilterChain。

### 现有测试类

| 测试类 | 说明 |
|--------|------|
| `DefaultSuperAdminMigrationTest` | 使用 Flyway + H2 MySQL 模式验证生产 V1 初始化账号、权限字段和 BCrypt 密码 |
| `AdminAuthControllerTest` | 管理员登录、本人资料、退出和无需原密码的本人改密（13 个用例） |
| `AdminManagementPermissionTest` | 超级管理员权限和 401/403 边界 |
| `AdminManagementQueryTest` | 管理员分页、搜索和详情 |
| `AdminManagementMutationTest` | 新增、编辑、启禁用、重置密码和物理删除 |
| `AdminManagementServiceTest` | Redis-first、数据库失败和并发唯一键边界 |
| `SysAdminUserStructureTest` | 管理员表、Entity 和 Mapper 不保留软删除字段 |
| `HistoricalCompatibilityStructureTest` | 认证接口、Mapper、错误码和 Flyway 配置不保留无调用的历史兼容残留 |
| `UserAuthControllerTest` | 用户注册、登录、获取信息、退出、修改密码、忘记密码流程（13 个用例） |
| `SecurityPermissionTest` | 角色隔离：ADMIN/USER Token 不可互访、无 Token 返回 401（5 个用例） |
| `KasiBackendApplicationTests` | Spring 上下文加载测试 |

### 运行测试

```powershell
# 编译检查（跳过测试）
.\mvnw.cmd -DskipTests compile

# 运行全部测试
.\mvnw.cmd test
```

Java 21 下编译会因 `release 25` 失败，必须使用 Java 25。

## 8. 开发优先级

### ✅ P0：让基础设施可重复运行

1. ✅ 确认 Java 25 和 Maven Wrapper 的统一使用方式。
2. ✅ 已配置 datasource（环境变量注入）、Flyway 迁移、MyBatis 映射。
3. ✅ 数据库脚本已改为 Flyway 可识别的 `V1__kasi_promotion.sql`。
4. ✅ 测试环境使用 H2 内存数据库，不依赖本地 MySQL。

### ✅ P0：实现最小后端闭环

1. ✅ 定义了用户实体、Mapper、Service 和 Controller。
2. ✅ 定义了统一响应、参数校验、异常处理和事务边界。
3. ✅ 为管理员和推广用户建立了独立的认证入口与权限边界。

### P1：补齐安全和数据约束

1. ✅ 使用 BCrypt 密码哈希和可测试的认证流程。
2. ✅ 定义了角色权限（ROLE_ADMIN / ROLE_USER）、状态规则和 401/403 契约。
3. ✅ 管理员和推广用户管理均采用物理删除，删除后的唯一账号或联系方式可以复用。
4. ⬜ 补齐 `department_id`、`created_by`、`updated_by` 的外键或明确不加外键的理由。
5. ⬜ 补充审计字段的自动填充（`created_by`、`updated_by`）。

### P2：后续规划

1. ⬜ 接入真实短信/邮件验证码发送（`local` 开发环境当前为 Console 输出）。
2. ⬜ 实现 Token 刷新机制。
3. ⬜ 添加操作日志和登录审计。

## 9. Git 与协作

- **仓库地址**：`https://github.com/wwxst/kasi-backend.git`
- **当前分支**：`master`，已关联远程 `origin/master`
- 提交规范：使用 [Conventional Commits](https://www.conventionalcommits.org/) 格式（`feat:`、`fix:`、`docs:`、`refactor:`、`test:` 等）。
- 任何代理开始工作前都应先查看 `git status --short --branch`，只修改任务涉及的文件，不使用 `git reset --hard` 或 `git checkout --` 丢弃现有改动。
- 提交前运行 `git diff --check` 检查空白字符问题。
