# Agent 指南

## 适用范围

- 本文件适用于仓库根目录 `E:/JavaProjects/kasi-project/kasi-backend`。
- 在修改应用代码、数据库脚本、构建配置或测试之前，请先阅读 [DEVELOPMENT.md](DEVELOPMENT.md)。
- 将工作区视为用户所有。编辑前先检查 `git status` 及相关 diff，切勿重置或丢弃非当前任务所产生的更改。

## 仓库级强制开发流程

以下流程适用于功能开发、缺陷修复、重构、数据库和配置变更；文档-only 变更也必须完成相应的范围检查。

1. **先确认边界**：开始前运行 `git status --short --branch`，阅读 `DEVELOPMENT.md` 和相关模块文档，列出本次允许修改的文件与明确不修改的范围。
2. **先分析根因**：任何错误先记录现象、影响范围、可复现步骤、已有证据、根因假设和排除项；没有证据时不得直接修改代码碰运气。
3. **一次只解决一个问题**：把工作拆成可独立验证的阶段，每个阶段只有一个清晰目标、一个完成标准和一组针对性验证；发现第二个独立问题时单独登记，不顺手扩 scope。
4. **按变更级别停靠**：单模块行为修复可在当前任务内实施；跨模块、API/数据库/权限/配置契约变更必须先形成设计并汇报；破坏性重构必须明确影响面、迁移或重建方式、回滚方案并获得确认。
5. **最小改动优先**：只改动导致问题和验证所必需的代码，不为未发布 API 保留无意义兼容层，也不因“以后可能需要”提前引入抽象或复杂系统。
6. **先定义验证**：实现前确定失败路径、成功路径和边界条件的测试或可重复检查；涉及安全、并发、事务、迁移和外部平台时，必须覆盖对应风险。
7. **完成后再宣称**：运行与变更风险匹配的测试、编译、迁移/结构检查和 `git diff --check`；没有最新的零错误输出，不得宣称已修复或测试健康。
8. **同步沉淀**：行为、边界、命令、迁移或架构发生变化时，同步更新 `README.md`、模块文档或 `DEVELOPMENT.md`；重要架构决策按 [docs/architecture-decisions.md](docs/architecture-decisions.md) 记录。
9. **保持当前/规划分离**：文档、代码审查和交接中必须明确“当前已实现”“已批准但未实施”“建议/缺口”，不得把计划描述成现状。
10. **保留用户改动**：禁止用 `git reset --hard`、`git checkout --` 或批量暂存覆盖无关改动；提交时按意图逐文件暂存并复核 diff。

## 当前项目现状

- 该项目是一个单模块 Spring Boot 应用，位于 `com.kasi.backend` 包下。
- **第一阶段认证模块已实现**，包含管理员（ADMIN）和推广用户（USER）双认证体系，详见 [README.md](README.md)。
- 已实现的包结构：
  - `common/` — 统一响应（ApiResponse）、错误码（ErrorCode）、全局异常处理、业务枚举
  - `security/` — JWT 令牌管理、认证过滤器、Redis 会话版本/单会话校验、AuthContext 上下文、Spring Security 配置
  - `admin/` — 管理员认证、本人资料与密码维护，以及超级管理员管理普通管理员账号
  - `user/` — 推广用户注册、登录、获取当前用户、退出登录、修改密码、忘记密码流程，以及管理员可用的推广用户管理 CRUD
  - `provider/` — 短剧平台定义、接入账号持久层、AES-GCM 密钥加密、GoodShort 签名/连接探测，以及管理员平台接入管理 API
  - `promotion/` — 推广用户媒体账号绑定、GoodShort 账号报备、推广链接、订单归因、CPS 佣金快照、管理员手动同步及管理员/用户查询导出 API
  - `drama/` — GoodShort 短剧目录与剧集持久层、全量/增量同步、检查点与租约、平台级分佣规则、`BigDecimal` 计算器、管理员 API 和定时调度
  - `auth/` — 可复用的验证码服务和密码重置 Token 机制（Redis 存储，Lua 原子消费/预占，TTL 自动过期）
- 数据库迁移：`V1__kasi_promotion.sql` 创建基础业务表并植入唯一初始超级管理员，`V13__promotion_task.sql` 增加推广任务，`V14__provider_drama_promotion_metadata.sql` 增加短剧本地推广元数据，`V15__promotion_order_and_rule_history.sql` 增加分佣历史快照和推广订单；不植入平台接入密钥。目录默认同步 `ENGLISH`，全量调用 `initBooks`，增量调用 `incrementBooks`；目录同步不覆盖本地推广元数据。验证码和密码重置 Token 等临时数据由 Redis（`vc:*`、`pwd:*` 键）管理，TTL 自动过期。
- `scripts/dev/seed_goodshort_drama_catalog.sql` 是 Flyway 之外的手动开发 seed，仅创建禁用且无凭据的 GoodShort 本地 fixture 连接；仅限本地使用，并必须通过遇错即停的 fail-fast 客户端执行。
- 项目当前仍处于开发阶段，数据库可以删除重建；修改已执行的迁移后应重建开发数据库。未来生产首次建库也按 Flyway 版本顺序执行并植入初始账号，不新增运行时账号植入器。
- 会话状态由 Redis（`auth:version:{type}:{userId}`、`auth:session:{jti}`）管理。JWT 携带 `jti`、`sessionVersion`，受保护请求必须同时校验签名、账号状态和 Redis 会话；Redis 不可用时安全失败返回 503，不能降级放行。
- 修改密码、密码重置等敏感 MySQL 状态变更会先将账号版本切换为 `MUTATING:{nonce}`，数据库成功后再恢复新的 `ACTIVE:*` 版本，使旧 Token 失效。普通 logout 只撤销当前 `jti` 会话。
- 管理员本人通过 `PUT /api/admin/auth/password` 修改密码时只提交新密码和确认密码，不要求原密码；成功后当前账号的旧 Token 全部失效。推广用户本人改密仍要求原密码。
- 当前采用简单的 `is_super_admin` 权限控制，不是 RBAC。数据库只允许一个业务上的超级管理员；`ROLE_SUPER_ADMIN` 由数据库当前记录派生，不信任 JWT 声明。
- 超级管理员可分页查询、新增、编辑、启禁用、重置密码和物理删除普通管理员；普通管理员不能被提升为超级管理员，管理接口不能操作唯一超级管理员。
- 普通管理员和超级管理员均可查询 `/api/admin/drama/providers`；只有超级管理员可写入平台 URL、PID、KEY、启用状态或执行连接探测。平台 KEY 只保存 AES-GCM 密文，管理响应不得暴露明文密钥、密文或掩码片段。
- 管理员只使用必填 `real_name`，不使用 `nickname`。`sys_admin_user` 不保留 `deleted_at`；管理员删除只执行物理 `DELETE`，删除后账号、手机号和邮箱可以复用。
- 推广用户不使用独立 `username`，只用手机号或邮箱登录；`user_no` 是后端生成的 12 位随机数字展示编号，内部关联继续使用自增 `id`。普通用户登录和本人信息 JSON 不返回内部 `id`，JWT `sub` 仍按现有认证契约保存内部 `id`。超级管理员和普通管理员均可通过 `/api/user/management/**` 分页、搜索、新增、编辑、启禁用、重置密码和物理删除推广用户。
- 推广用户联系方式、状态、密码和删除等敏感管理操作先进入 Redis `MUTATING` 状态；Redis 失败时不得写 MySQL。绑定媒体账号的推广用户删除会返回 `USER_MEDIA_ACCOUNT_BOUND(3014)`，只能禁用；未绑定媒体账号的用户仍可物理删除。
- `sys_admin_user` 和 `promotion_user` 均不保留 `deleted_at`；媒体账号表同样不保留 `deleted_at`，媒体账号不提供物理删除。
- 媒体账号用户 API 位于 `/api/user/promotion/media-accounts`，管理员 API 位于 `/api/admin/promotion/media-accounts`；管理员支持分页查询、详情、编辑和失败报备重试，未加白时允许纠正媒体平台和账号 ID，已加白后锁定身份字段。响应不暴露平台连接 ID、PID、密钥或任务租约字段。报备任务默认每 30 秒领取到期任务，提交后 1 分钟首次查询，审核中每 5 分钟查询，已加白每 24 小时复核。
- 短剧目录管理员 API 位于 `/api/admin/drama/catalog`；普通管理员和超级管理员均可分页查询、查看详情、触发同步、查询同步状态、修改本地上下架和维护短剧推广元数据（`PUT /api/admin/drama/catalog/{id}/promotion-metadata`）。同步默认每 5 分钟处理到期任务，支持断点续跑、过期租约接管和同连接/语言跨 FULL、INCREMENTAL 互斥；远端同步不得覆盖 `local_status` 或本地推广元数据，也不得物理删除本次未返回的历史短剧。
- 短剧平台分佣规则 API 位于 `/api/admin/drama/providers/{providerId}/commission-rules`：普通管理员和超级管理员均可 `GET`，只有超级管理员可 `POST` 首次设置和 `PUT` 覆盖。每个平台一条当前规则、无时间段/状态/删除；每次写入同步产生不可变 `provider_commission_rule_history` 快照。API 使用 `0..100` 百分比，数据库和订单快照使用 `0..1` 高精度比例；计算器最终金额保留两位并按 `HALF_UP` 四舍五入。
- 推广链接和订单级 CPS 最小闭环已实现：用户通过 `/api/user/promotion/links` 生成 GoodShort 链接/口令，`requestKey` 幂等并保存 `trackingNo`；管理员通过 `POST /api/admin/promotion/orders/sync` 手动同步，订单以 `(connection_id, external_order_id)` 幂等，仅按 `customParams -> tracking_no -> user_id` 归因并保存原始 JSON 和五费率快照。管理员和用户分别可查询/CSV 导出，用户月度归属按 `paid_at`；退款保留原佣金并标记 `REVERSED`。自动订单同步、正式账单、钱包、提现和转化分析仍未实现。
- 推广任务创建已实现：用户通过 `/api/user/promotion/tasks` 提交短剧、任务名称和多个推广平台，后端按平台拆分并以 `(userId, requestKey, mediaType)` 幂等，`GET` 返回任务链接字段及点击、引流、订单、广告统计字段。当前创建状态为 `PENDING`，GoodShort 真实链接/口令生成和统计同步尚未实现；快速上线用户端继续以真实 `PromotionLink` 为入口，不展示任务中的 0 值统计。
- 定时任务管理 API 位于 `/api/admin/system/scheduled-tasks`；固定任务 `GOODSHORT_DRAMA_INCREMENTAL_SYNC` 默认每 60 分钟入队，首次全量同步必须手动完成且成功基线存在后才会自动创建增量任务。周期支持 `INTERVAL_SECONDS/MINUTES/HOURS/DAYS`、`DAILY`、`WEEKLY`、`MONTHLY`、`YEARLY`，`INTERVAL_HOURS` 使用小时数和 `interval_minutes_part` 分钟余量，`INTERVAL_DAYS` 使用天数、`interval_hours_part` 小时余量和 `interval_minutes_part` 分钟余量；日历型周期同时保存执行时间及对应星期/日期字段。每分钟调度器只负责入队，现有短剧执行器继续每 5 分钟领取并执行；普通管理员只读，超级管理员可编辑周期、说明和启停状态。
- Git 仓库：`https://github.com/wwxst/kasi-backend.git`，远程 `origin`，分支 `master`。
- 在文档和代码审查中，请将当前架构与规划架构区分开来。不要将规划中的模块描述为已实现的模块。

## 工具链

- 使用 Java 25。POM 中设置了 `<java.version>25</java.version>`。
- IDE（IntelliJ IDEA）中已配置 JDK 25，编译和运行通过 IDE 完成。
- 命令行终端可能仍指向 Java 21，此时可通过以下方式临时切换：

```powershell
# 临时切换到 JDK 25（路径以实际安装为准，通常在 IDEA 目录下）
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 验证版本
java -version
.\mvnw.cmd -v
```

- 使用 Maven Wrapper：Unix 类 Shell 中使用 `./mvnw`，PowerShell 中使用 `./mvnw.cmd`。
- IDEA 内运行 Maven 时会自动使用已配置的 JDK 25，无需额外设置 JAVA_HOME。

## 校验

- 若仅做编译检查，在 Java 25 环境下运行 `./mvnw.cmd -DskipTests compile`。
- 运行完整测试套件，执行 `./mvnw.cmd test`。测试使用 H2 内存数据库（MySQL 兼容模式），通过 `application-test.properties` 配置，不依赖本地 MySQL。
- 平台接入模块聚焦校验：`./mvnw.cmd -Dtest=MediaAccountFilingMigrationTest,ProviderCredentialCipherTest,ProviderPersistenceTest,ProviderConnectionServiceTest,GoodShortSignerTest,GoodShortAdapterTest,ProviderAdminControllerTest test`。
- 短剧目录聚焦校验：`./mvnw.cmd -Dtest=GoodShortDramaCatalogSeedTest,GoodShortCatalogAdapterTest,DramaCatalogPersistenceTest,DramaCatalogSyncServiceTest,DramaCatalogAdminServiceTest,AdminDramaCatalogControllerTest,DramaCatalogSchedulerTest test`。
- 平台分佣规则聚焦校验（PowerShell 需使用 `--%` 原样传递逗号列表）：`./mvnw.cmd --% -Dtest=ProviderCommissionRuleMigrationTest,ProviderCommissionRulePersistenceTest,ProviderCommissionCalculatorTest,ProviderCommissionRuleServiceTest,ProviderCommissionRuleConcurrencyTest,ProviderCommissionRuleControllerTest test`。
- 提交更改前运行 `git diff --check`。
- 在没有显示零错误的最新输出之前，不要宣称测试套件是健康的。
- 每新增一个控制器、服务、映射器、迁移脚本或安全规则，都应添加针对性的测试。优先使用可复现的测试数据库，而非开发人员本机数据库。

## 配置与密钥

- 不得提交数据库密码、JWT 密钥、API 密钥或其他凭据。
- 数据源设置优先使用环境变量或特定 profile 的本地配置。标准的 Spring Boot 变量为 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME` 和 `SPRING_DATASOURCE_PASSWORD`。
- 将生产环境与测试环境的数据源配置分开。不要让默认测试依赖于开发人员的 MySQL 实例。

## 数据库与 Flyway

- Flyway 当前按 `V1`、`V14`、`V15` 顺序迁移；开发阶段可删除数据库后重建，已有 V14 数据库通过 V15 增量增加订单和规则历史表。后续 schema 变更继续新增更高版本迁移，不修改已发布迁移。
- 当前不启用 `baseline-on-migrate`。没有 Flyway 历史表的非空数据库必须明确失败，禁止为兼容旧库而静默跳过 V1。
- 迁移脚本必须针对已选定的 schema。不要在应用迁移脚本中放置针对固定本地数据库的 `CREATE DATABASE` 或 `USE` 语句。
- 迁移中修改的会话设置（包括 `FOREIGN_KEY_CHECKS`），若确实需要，应在迁移完成后恢复。
- 慎重添加外键和约束。`department_id`、`created_by` 和 `updated_by` 目前没有对应的引用表或约束。
- 在实现账号复用或恢复流程之前，请协调好软删除行为与唯一账号字段之间的关系。
- 保持 schema 注释、状态值、密码存储假设以及服务层校验的一致性。
- **字符集统一为 UTF-8**：JDBC URL 中 `characterEncoding` 必须使用 `UTF-8`（Java 标准名称），禁止使用 `utf8mb4`（MySQL 内部名称，MySQL Connector/J 9.x 不识别）。SQL 脚本中建表统一使用 `utf8mb4`。

## 应用边界

- 将根包保持在 `com.kasi.backend` 之下，以确保 Spring 组件扫描行为可预测。
- 添加业务代码时，将 HTTP 关注点放在 controller/web 包中，编排逻辑放在 service 中，持久化放在 MyBatis mapper 包中，传输对象与持久化对象分离。
- 不要仅为与规划设计保持一致而添加框架抽象层。在有具体用例和测试需要时再添加。
- 在修改多条记录或更新审计字段的服务操作中定义事务边界。

## 代码分层与职责

| 层 | 包路径 | 职责 | 禁止事项 |
|----|--------|------|----------|
| Controller | `*.controller` | 接收请求、参数校验（`@Valid`）、调用 Service、组装响应 | 不可直接调用 Mapper，不可包含业务逻辑 |
| Service | `*.service` / `*.service.impl` | 接口定义在 `*.service`，业务实现、事务管理及 Mapper/其他 Service 调用放在 `*.service.impl` | 不可操作 `HttpServletRequest`/`HttpServletResponse` |
| Mapper | `*.mapper` | 数据库 CRUD，每个 Mapper 只操作一张主表 | 不可包含业务逻辑 |
| Entity | `*.entity` | 纯数据对象，与数据库表一一对应 | 不可包含业务方法（除 getter/setter） |
| DTO | `*.dto` | 请求传输对象，类名以 `DTO` 结尾，使用 Jakarta Validation 注解 | 不可承载响应展示模型 |
| VO | `*.vo` | Controller 返回的业务展示对象，类名以 `VO` 结尾 | 不可用于接收请求或映射数据库表 |

- 仓库内所有以 `Service` 命名的组件均采用接口与实现分离：调用方依赖 `*Service` 接口，Spring 组件与事务注解放在对应 `impl/*ServiceImpl` 实现类。
- 业务请求统一使用 `*DTO`，业务响应统一使用 `*VO`；通用响应包装器仍使用 `ApiResponse<VO>`，不因 VO 分层重复包装。
- `auth` 与 `security` 基础模块同样使用统一的 `service/impl` 结构；安全过滤器放在 `security.filter`，会话/重置凭证内部模型放在各自 `entity` 包。

## DTO 校验规范

- 所有请求 DTO 必须使用 Jakarta Validation 注解（`@NotBlank`、`@Email`、`@Size` 等）。
- Controller 层必须对请求体使用 `@Valid` 或 `@Validated` 触发校验。
- **不要在 Service 层重复校验** DTO 字段的基础格式 —— Validation 框架已处理。
- Service 层只做业务级校验（如"手机号是否已注册"、"旧密码是否正确"），校验失败抛出 `BusinessException`。
- 校验失败时，全局异常处理器返回 `ErrorCode.VALIDATION_ERROR(1006)`。

## 错误码管理规范

- [ErrorCode.java](src/main/java/com/kasi/backend/common/exception/ErrorCode.java) 是所有错误码的**唯一真理源**。
- 新增错误码必须遵循分段规则：`1xxx`通用、`2xxx`管理员、`3xxx`用户、`4xxx`验证码、`5xxx`密码重置、`6xxx`短剧平台/短剧领域。
- 只保留当前业务路径能够实际返回的错误码；不要为尚未实现或无法区分的状态预留不可达枚举值。
- 不要硬编码数字错误码 —— 始终使用 `ErrorCode` 枚举引用。
- 不要在 Controller 中直接构造错误码字符串 —— 通过 `throw new BusinessException(ErrorCode.XXX)` 交给全局异常处理器。
- 对外暴露的错误信息要兼顾安全（登录失败不区分"用户不存在"和"密码错误"，统一返回"账号或密码错误"）。

## 测试编写规范

- 认证模块测试**必须继承** [BaseAuthTest.java](src/test/java/com/kasi/backend/BaseAuthTest.java)，它提供了 H2 数据库初始化、测试数据准备和登录辅助方法。
- 数据库迁移测试不属于认证接口测试，可以不继承 `BaseAuthTest`；应使用隔离的 H2 MySQL 模式数据库实际执行生产迁移。
- 每个测试方法使用 `@DisplayName` 注解写中文描述。
- 测试命名采用 `{方法名}_{场景}_{预期结果}` 驼峰格式（如 `loginWithWrongPassword`）。
- 调用受保护接口时，通过 `loginAsAdmin()` / `loginAsUser()` 获取 Token，不要手动构造 JWT。
- 每个测试类必须覆盖正常路径和异常路径（错误密码、禁用账号、权限越界等）。
- 测试不依赖外部 MySQL，使用 H2 内存数据库 + `application-test.properties`。
- `BaseAuthTest` 的 `@BeforeEach` 会清理所有数据并重新插入，每个测试独立运行。

## API 路径命名约定

- 路径格式：`/api/{module}/{domain}/{action}`
  - `{module}`：业务模块（`admin`、`user`）
  - `{domain}`：领域子路径（`auth`、后续可能有 `profile`、`order` 等）
  - `{action}`：具体操作
- RESTful 动词：查询=`GET`、创建=`POST`、全量更新=`PUT`、部分更新=`PATCH`、删除=`DELETE`
- 示例：`POST /api/user/auth/register`、`PUT /api/admin/auth/password`
- 当前认证端点包括：注册验证码 `POST /api/user/auth/register/code`，忘记密码重置 `POST /api/user/auth/password/reset`。
- 管理员管理端点统一位于 `/api/admin/management/**`，仅 `ROLE_SUPER_ADMIN` 可访问；本人资料使用 `PUT /api/admin/auth/profile`。
- 推广用户管理端点统一位于 `/api/user/management/**`，超级管理员和普通管理员均以 `ROLE_ADMIN` 访问。
- 平台分佣规则使用三个固定端点：`GET/POST /api/admin/drama/providers/{providerId}/commission-rules` 和 `PUT /api/admin/drama/providers/{providerId}/commission-rules/{ruleId}`；`GET` 要求 `ROLE_ADMIN`，写操作要求 `ROLE_SUPER_ADMIN`。

## 事务管理规范

- Service 层涉及**多条写操作**（插入/更新/删除）的方法必须使用 `@Transactional`。
- 只读操作（`SELECT`）建议使用 `@Transactional(readOnly = true)` 以优化数据库性能。
- 事务边界应定义在 Service 方法上，**不要在 Controller 层开启事务**。
- 当前认证模块中需要事务的场景：用户注册（插入用户 + 标记验证码已用）、密码重置（数据库更新密码；Redis Token 在成功提交后删除）。敏感状态变更前必须先完成 Redis 会话失效/进入 `MUTATING`，Redis 失败不得继续修改密码等关键状态。

## 安全

- 仅引入 Security Starter 并不构成一个认证设计方案。在暴露账号端点之前，请先定义登录机制、会话或令牌生命周期、密码哈希、角色以及 401/403 行为。
- 切勿比较或持久化原始密码。使用强 `PasswordEncoder`，并同时测试认证成功和被拒绝的路径。
- 固定初始超级管理员账号为 `admin`，初始推广用户邮箱为 `19193171667@163.com`；两者密码只以 BCrypt 哈希写入 V1，首次登录后应立即修改默认密码。不得把明文密码写入数据库、日志或 API 响应。
- 将 `is_super_admin` 和 `status` 视为领域规则，而非受信任的请求字段。
- 验证码发送器按 profile 隔离：`local` 仅使用 `ConsoleVerificationCodeSender`，`test` 使用测试 sender；生产环境必须提供真实 sender，不能以 Console 输出代替实际投递。

## 文档规则

- **每次完成一个功能更新或代码变更后，必须同步更新和沉淀相关文档**（包括本 AGENTS.md、README.md 以及相关模块的设计文档），确保文档与代码保持一致。
- 当命令、模块边界、数据库迁移或运行时前置条件发生变化时，请更新 [README.md]。
- 将已验证的当前行为与未来工作或建议分开记录。
- 除非有意更改 schema 契约，否则保留原有的中文数据库注释和技术名称。
- 不要在聚焦任务中添加无关的重构、生成文件或元数据变更。
# 当前分佣规则覆盖说明（2026-08-22）

平台分佣规则采用默认配置：每个平台一条记录、无时间限制、无状态、不可删除；POST 首次设置，PUT 直接覆盖五项费率。普通管理员只读，超级管理员可设置和编辑。
