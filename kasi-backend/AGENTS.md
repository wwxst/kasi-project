# Agent 指南

## 适用范围

- 本文件适用于仓库根目录 `E:/JavaProjects/kasi-project/kasi-backend`。
- 在修改应用代码、数据库脚本、构建配置或测试之前，请先阅读根级 [DEVELOPMENT.md](../DEVELOPMENT.md)。
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
8. **同步沉淀**：行为、边界、命令、迁移或架构发生变化时，同步更新 `README.md`、模块文档或根级 `DEVELOPMENT.md`；重要架构决策按 [根级 ADR 索引](../docs/adr/architecture-decisions.md) 记录。
9. **保持当前/规划分离**：文档、代码审查和交接中必须明确“当前已实现”“已批准但未实施”“建议/缺口”，不得把计划描述成现状。
10. **保留用户改动**：禁止用 `git reset --hard`、`git checkout --` 或批量暂存覆盖无关改动；提交时按意图逐文件暂存并复核 diff。

## 最小实现原则（强制）

以最小、最直接、最精简的代码完成当前明确需求。

* 不主动增加测试代码，除非任务明确要求。
* 不为假设中的未来问题进行防御性编程。
* 不为尚未出现的场景增加兼容层、兜底逻辑、抽象层或扩展机制。
* 不提前设计未来可能需要的功能。
* 优先使用简单、直接、易理解的实现方式，避免过度封装和过度抽象。
* 只实现当前需求所必需的代码，并尽量保持最小改动范围。
* 对当前需求明确涉及的必要错误处理和输入边界正常处理，不得为了精简而破坏代码正确性。

**核心原则：只解决现在已经存在的问题，不为假设中的未来编写代码。**

## 当前项目现状

- 该项目是一个单模块 Spring Boot 应用，位于 `com.kasi.backend` 包下。
- **第一阶段认证模块已实现**，包含管理员（ADMIN）和推广用户（USER）双认证体系，详见 [README.md](README.md)。
- 已实现的包结构：
  - `common/` — 统一响应（ApiResponse）、错误码（ErrorCode）、全局异常处理、业务枚举
  - `security/` — JWT 令牌管理、认证过滤器、Redis 会话版本/单会话校验、AuthContext 上下文、Spring Security 配置
  - `admin/` — 管理员认证、本人资料与密码维护，以及超级管理员管理普通管理员账号
  - `user/` — 推广用户注册、登录、获取和修改本人资料、上传本人头像、退出登录、修改密码、忘记密码流程，以及管理员可用的推广用户管理 CRUD
  - `provider/` — 短剧平台定义、接入账号持久层、AES-GCM 密钥加密、GoodShort 签名/连接探测，以及管理员平台接入管理 API
  - `promotion/` — 推广用户媒体账号绑定、GoodShort 账号报备、推广链接、订单归因、CPS 佣金快照、订单共享同步服务、管理员手动补拉及管理员/用户查询导出 API
  - `drama/` — GoodShort 短剧目录与免费剧集持久层、全量/增量同步、检查点与租约、平台级分佣规则，以及经过域名校验的永久媒体 URL
  - `auth/` — 可复用的验证码服务和密码重置 Token 机制（Redis 存储，Lua 原子消费/预占，TTL 自动过期）
- 生产数据库结构由 `src/main/resources/db/migration/V*.sql` 不可变 Flyway 链管理，Flyway 只通过 Maven `migration` profile 作为独立发布步骤执行；`src/main/resources/db/kasi_promotion.sql` 保留为开发空库最终结构重建脚本。两条路径必须保持最终结构和固定数据一致；所有 `*_id` 仅作为逻辑关联，由 Service 校验存在性与归属，不使用物理外键或数据库级联，也不植入平台接入密钥。
- 业务时间唯一语义为 `Asia/Shanghai`；Java 使用该 `ZoneId`，MySQL datasource 连接 session 使用等价 `+08:00`。`+08:00` 只是连接实现，不是第二套业务时区定义。
- `scripts/dev/seed_goodshort_drama_catalog.sql` 是初始化之外的手动开发 seed，仅创建禁用且无凭据的 GoodShort 本地 fixture 连接；仅限本地使用，并必须通过遇错即停的 fail-fast 客户端执行。
- 开发数据库仍可删除重建：schema 变化后对空库重新执行 `kasi_promotion.sql`。生产数据库按版本迁移且不得删库重建；应用启动不自动建表或升级。
- 会话状态由 Redis（`auth:version:{type}:{userId}`、`auth:session:{jti}`）管理。JWT 携带 `jti`、`sessionVersion`，受保护请求必须同时校验签名、账号状态和 Redis 会话；Redis 不可用时安全失败返回 503，不能降级放行。
- 修改密码、密码重置等敏感 MySQL 状态变更会先将账号版本切换为 `MUTATING:{nonce}`，事务提交或回滚完成后都按 nonce 恢复新的 `ACTIVE:*` 版本，使旧 Token 失效且数据库异常不会长期遗留 `MUTATING`。普通 logout 只撤销当前 `jti` 会话。
- 管理员本人通过 `PUT /api/admin/auth/password` 修改密码时只提交新密码和确认密码，不要求原密码；成功后当前账号的旧 Token 全部失效。推广用户本人改密仍要求原密码。
- 当前采用简单的 `is_super_admin` 权限控制，不是 RBAC。数据库只允许一个业务上的超级管理员；`ROLE_SUPER_ADMIN` 由数据库当前记录派生，不信任 JWT 声明。
- 超级管理员可分页查询、新增、编辑、启禁用、重置密码和物理删除普通管理员；普通管理员不能被提升为超级管理员，管理接口不能操作唯一超级管理员。
- 普通管理员和超级管理员均可查询 `/api/admin/drama/providers`；只有超级管理员可写入平台 URL、PID、KEY、启用状态或执行连接探测。平台 KEY 只保存 AES-GCM 密文，管理响应不得暴露明文密钥、密文或掩码片段。
- 管理员只使用必填 `real_name`，不使用 `nickname`。`sys_admin_user` 不保留 `deleted_at`；管理员删除只执行物理 `DELETE`，删除后账号、手机号和邮箱可以复用。
- 管理员头像只允许通过详情头像上传入口修改：本人使用 `PUT /api/admin/auth/avatar`，超级管理员修改普通管理员使用 `PUT /api/admin/management/{id}/avatar`。文件限制为 JPG/PNG/WebP、最大 2 MB，本地目录由 `APP_UPLOAD_DIR` 配置；新增和资料编辑 DTO 不接收头像 URL。
- 推广用户本人使用 `PUT /api/user/auth/profile` 只修改昵称和真实姓名，使用 `PUT /api/user/auth/avatar` 上传头像；手机号、邮箱、用户编号、状态和登录信息不属于本人资料修改契约。头像限制为 JPG/PNG/WebP、最大 2 MB，本地目录由 `APP_UPLOAD_DIR` 配置，资料 DTO 不接收头像 URL。
- 推广用户不使用独立 `username`，只用手机号或邮箱登录；`user_no` 是后端生成的 12 位随机数字展示编号，内部关联继续使用自增 `id`。普通用户登录和本人信息 JSON 不返回内部 `id`，JWT `sub` 仍按现有认证契约保存内部 `id`。超级管理员和普通管理员均可通过 `/api/user/management/**` 分页、搜索、新增、编辑、启禁用、重置密码和物理删除推广用户。
- 普通用户自助注册时，后端在创建账号时生成并持久化默认昵称 `卡司用户` 加 5 位数字后缀；后缀取本次 12 位随机 `user_no` 的末 5 位并保留前导零。管理员创建或编辑推广用户时继续使用请求中的昵称。
- 推广用户联系方式、状态、密码和删除等敏感管理操作先进入 Redis `MUTATING` 状态；Redis 失败时不得写 MySQL。绑定媒体账号的推广用户删除会返回 `USER_MEDIA_ACCOUNT_BOUND(3014)`，只能禁用；未绑定媒体账号的用户仍可物理删除。
- 推广链接生成的 GoodShort HTTP 调用必须在数据库事务之外；`PENDING`、`SUCCESS`、`FAILED` 状态分别通过独立短事务持久化。订单 upsert 使用 `READ_COMMITTED`，遇到 `(connection_id, external_order_id)` 唯一键并发冲突时回读已有订单，不重写同步流程；不得改回会对不存在行产生 gap lock 竞争的默认 `REPEATABLE_READ`。
- 管理后台 Dashboard 当前只显示当前管理员欢迎语，不展示静态 Demo 卡片；侧边栏、品牌链接、搜索回车和兜底路由统一使用 `/user-management`，真实数据大屏仍未实现。
- `sys_admin_user` 和 `promotion_user` 均不保留 `deleted_at`；媒体账号表同样不保留 `deleted_at`，媒体账号不提供物理删除。
- 媒体账号用户 API 位于 `/api/user/promotion/media-accounts`，管理员 API 位于 `/api/admin/promotion/media-accounts`；推广用户创建或编辑媒体账号时，媒体平台、账号 ID、账号名称和账号主页链接均为必填，主页链接必须使用 HTTPS。管理员支持分页查询、详情、编辑和失败报备重试，未加白时允许纠正媒体平台和账号 ID，已加白后锁定身份字段。响应不暴露平台连接 ID、PID、密钥或任务租约字段。报备任务默认每 30 秒领取到期任务，提交后 1 分钟首次查询，审核中每 5 分钟查询，已加白每 24 小时复核。
- 短剧目录管理员 API 位于 `/api/admin/drama/catalog`；普通管理员和超级管理员均可分页查询、查看详情、触发同步、查询同步状态、修改本地上下架和维护短剧推广元数据（`PUT /api/admin/drama/catalog/{id}/promotion-metadata`）。手动同步语言留空时按配置展开 GoodShort 全部 13 种支持语言并逐语言创建任务，固定增量任务使用同一完整语言集合；同步默认每 5 分钟兜底处理到期任务，支持提交后立即唤醒、断点续跑、过期租约接管和同连接/语言跨 FULL、INCREMENTAL 互斥。新同步的甲方在线短剧默认 `PUBLISHED`，甲方非在线时同步为 `OFFLINE`，已有已上架短剧遇甲方下架时自动下架，甲方恢复在线不自动重新上架；本地推广元数据不被覆盖，也不物理删除本次未返回的历史短剧。目录同步会为新增、`remoteUpdatedAt` 变化或本地缺少 `content_url` 的短剧自动排队免费剧集同步。
- 用户端短剧 API `/api/user/promotion/dramas` 只返回本地已上架且甲方在线的短剧，列表按甲方 `remoteCreatedAt` 发布时间倒序（`remote_created_at DESC, id DESC`）分页，不使用本地 `createdAt` 代替发布时间。
- GoodShort 免费剧集同步管理员 API 为 `POST /api/admin/drama/catalog/{id}/contents/sync`、`POST /api/admin/drama/catalog/contents/sync`、`POST /api/admin/drama/catalog/contents/sync/all` 和 `GET /api/admin/drama/catalog/{id}/contents/sync/status`。勾选批量最多 100 部；全部同步按平台分页，可选语言和仅缺失过滤。三个写入口只创建/更新任务，事务提交后立即异步唤醒现有 worker；worker 在单批达到上限时继续提交下一轮，后台消费当前待执行任务，不阻塞手动请求；固定任务每分钟兜底处理到期任务，支持租约、重试和失败状态。GoodShort 没有收费剧集列表或收费资源接口，因此只同步免费剧集且不创建收费占位记录。
- 用户端短剧详情资源 API 为 `GET /api/user/promotion/dramas/{id}/free-content`，仅对已上架且甲方在线的短剧读取 MySQL 中永久保存的 `provider_drama_content.content_url`，不实时调用 GoodShort、不使用 Redis 剧集资源缓存；`refresh=true` 仅保留客户端兼容性且仍读取数据库。返回 URL 必须命中所属平台接入配置的媒体根域名或其正规子域，且不得指向未知域名、内网、非标准端口或含用户信息；不再使用 `GOODSHORT_MEDIA_HOSTS`。
- 用户端短剧素材下载由浏览器直接读取免费剧集资源接口返回的 `downloadUrl`；后端不创建下载任务、不运行 FFmpeg、不生成 ZIP，也不保存或清理用户下载文件。
- GoodShort 目录响应的 `bookId`、`bookName`、`bookNameZh`、`bookCover`、`labelNames`、`introduce`、`typeTwoName`、`language`、`rank`、`showStatus`、`novelType`、`novelSubType`、`ctime`、`utime` 全部转换为本地领域字段并保存；`labelNames` 使用 JSON 文本保存，管理端和用户端目录 VO 返回对应的 `titleZh`、`coverUrl`、`labelNames`、`categoryName`、`remoteRank`、`novelType`、`novelSubType`、`remoteCreatedAt`、`remoteUpdatedAt` 字段。增量请求按文档发送 `utimeStart`/`utimeEnd`。
- 短剧平台分佣规则 API 位于 `/api/admin/drama/providers/{providerId}/commission-rules`：普通管理员和超级管理员均可 `GET`，只有超级管理员可 `POST` 首次设置和 `PUT` 覆盖。每个平台一条当前规则、无时间段/状态/删除；每次写入同步产生不可变 `provider_commission_rule_history` 快照。API 使用 `0..100` 百分比，数据库和订单快照使用 `0..1` 高精度比例；计算器最终金额保留两位并按 `HALF_UP` 四舍五入。
- 推广链接和订单级 CPS 最小闭环已实现：用户通过 `/api/user/promotion/links` 生成 GoodShort 链接/口令，`requestKey` 幂等并保存 `trackingNo`；生成链接/口令不要求平台预先配置分佣规则，分佣规则只在已支付订单计算佣金时读取。`GOODSHORT_ORDER_SYNC` 每分钟自动同步最近 3 天，管理员仍可通过 `POST /api/admin/promotion/orders/sync` 手动补拉指定范围，订单以 `(connection_id, external_order_id)` 幂等，仅按 `customParams -> tracking_no -> user_id` 归因并保存原始 JSON 和五费率快照。本地订单状态只有 `PAID`/`REFUNDED`，未支付或未知甲方状态不落库。管理员可查询/CSV 导出完整核对字段；用户可按 `paid_at` 查询/导出本人订单，但只返回甲方订单号、支付状态、支付时间、跟踪号和本人收益，不返回本地主键、完整订单金额或内部佣金状态。退款保留原佣金并标记 `REVERSED`。正式账单、钱包、提现和转化分析仍未实现。
- 推广任务壳已删除；用户端以真实 `PromotionLink` 为推广入口。
- 定时任务管理 API 位于 `/api/admin/system/scheduled-tasks`；固定任务 `GOODSHORT_DRAMA_INCREMENTAL_SYNC` 默认每 60 分钟入队，`GOODSHORT_DRAMA_CONTENT_SYNC` 默认每 1 分钟处理免费剧集队列，`GOODSHORT_ORDER_SYNC` 默认每 1 分钟同步最近 3 天；首次全量同步必须手动完成且成功基线存在后才会自动创建增量任务。周期支持 `INTERVAL_SECONDS/MINUTES/HOURS/DAYS`、`DAILY`、`WEEKLY`、`MONTHLY`、`YEARLY`，`INTERVAL_HOURS` 使用小时数和 `interval_minutes_part` 分钟余量，`INTERVAL_DAYS` 使用天数、`interval_hours_part` 小时余量和 `interval_minutes_part` 分钟余量；日历型周期同时保存执行时间及对应星期/日期字段。每分钟调度器扫描并执行到期任务，订单、目录和免费剧集任务复用同一分发器和数据库租约；普通管理员只读，超级管理员可编辑周期、说明和启停状态。
- Git 根仓库：`https://github.com/wwxst/kasi-project.git`，远程 `origin`，分支 `master`；所有 Git 操作从根目录执行。
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
- 运行常规完整 Gate，执行 `./mvnw.cmd verify`。测试使用 H2 内存数据库（MySQL 兼容模式），通过 `application-test.properties` 配置，不依赖本地 MySQL。
- 分类 Gate 使用 `./mvnw.cmd -Punit-tests verify` 和 `./mvnw.cmd -Pintegration-tests verify`；真实数据库使用 `./mvnw.cmd -Pmysql-contract-tests -Dtest='*MySqlContractIT' test`；静态分析使用 `./mvnw.cmd -Pstatic-analysis -DskipTests verify`。
- 生产 Flyway 只通过 `./mvnw.cmd -Pmigration flyway:info|validate|migrate` 独立运行，连接参数必须由 `FLYWAY_URL`、`FLYWAY_USER`、`FLYWAY_PASSWORD` 注入；应用启动不得执行迁移。
- Unit/Integration 的 JaCoCo 仅生成报告，不设置硬阈值；SpotBugs High 和 CI Dependency Review high/critical 为阻断项；GoodShort real smoke 只允许手动、Secret 保护的承诺执行。
- 平台接入模块聚焦校验：`./mvnw.cmd -Dtest=MediaAccountFilingMigrationTest,ProviderCredentialCipherTest,ProviderPersistenceTest,ProviderConnectionServiceTest,GoodShortSignerTest,GoodShortAdapterTest,ProviderAdminControllerTest test`。
- 短剧目录聚焦校验：`./mvnw.cmd -Dtest=GoodShortDramaCatalogSeedTest,GoodShortCatalogAdapterTest,DramaCatalogPersistenceTest,DramaCatalogSyncServiceTest,DramaCatalogAdminServiceTest,AdminDramaCatalogControllerTest,DramaCatalogSchedulerTest test`。
- 平台分佣规则聚焦校验（PowerShell 需使用 `--%` 原样传递逗号列表）：`./mvnw.cmd --% -Dtest=ProviderCommissionRuleMigrationTest,ProviderCommissionRulePersistenceTest,ProviderCommissionCalculatorTest,ProviderCommissionRuleServiceTest,ProviderCommissionRuleConcurrencyTest,ProviderCommissionRuleControllerTest test`。
- 提交更改前运行 `git diff --check`。
- 真实 MySQL Contract 与 GoodShort smoke 的环境和 PASS/FAIL/SKIP 语义以根级 `docs/development/testing.md` 为准；本机环境缺失只能记录 SKIP。
- 在没有显示零错误的最新输出之前，不要宣称测试套件是健康的。
- 每新增一个控制器、服务、映射器、数据库结构或安全规则，都应添加针对性的测试。优先使用可复现的测试数据库，而非开发人员本机数据库。

## 配置与密钥

- 不得提交数据库密码、JWT 密钥、API 密钥或其他凭据。
- 数据源设置优先使用环境变量或特定 profile 的本地配置。标准的 Spring Boot 变量为 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME` 和 `SPRING_DATASOURCE_PASSWORD`。
- 将生产环境与测试环境的数据源配置分开。不要让默认测试依赖于开发人员的 MySQL 实例。

## 数据库初始化与迁移

- `src/main/resources/db/migration/V*.sql` 是生产数据库版本真理源。当前 `V1__baseline.sql` 执行后不可修改，后续 schema 变化只能新增更高版本迁移。
- Flyway 仅存在于 Maven `migration` profile；应用无 Flyway 运行时依赖，并通过 `spring.flyway.enabled=false` 禁止启动迁移。生产连接不得有默认值。
- `src/main/resources/db/kasi_promotion.sql` 是开发空库重建脚本，必须随每个版本迁移同步为最新最终结构；MySQL Contract 比较两条初始化路径。
- 已由旧初始化 SQL 创建且结构核对无误的数据库，首次纳管时由发布人员显式执行 version `1` baseline；禁止打开 `baselineOnMigrate`、执行 `clean`、修改已执行迁移或手工改写历史表。
- 开发初始化 SQL 和生产迁移都不包含针对固定数据库的 `CREATE DATABASE` 或 `USE` 语句。
- 初始化中若修改会话设置（包括 `FOREIGN_KEY_CHECKS`），必须在脚本完成前恢复。
- 数据库统一不使用物理外键及数据库级联；`department_id`、`created_by` 和 `updated_by` 等关联字段由应用层按业务需要校验，不依赖数据库外键异常维护完整性。
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
- 数据库初始化测试不属于认证接口测试，可以不继承 `BaseAuthTest`；应使用隔离的 H2 MySQL 模式数据库实际执行生产初始化 SQL。
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
- 当前认证模块中需要事务的场景：用户注册（插入用户 + 标记验证码已用）、密码重置（数据库更新密码；Redis Token 在成功提交后删除、回滚时恢复 `READY`）。敏感状态变更前必须先完成 Redis 会话失效/进入 `MUTATING`，并在 MySQL 写入前注册事务完成回调；Redis 失败不得继续修改密码等关键状态。

## 安全

- 仅引入 Security Starter 并不构成一个认证设计方案。在暴露账号端点之前，请先定义登录机制、会话或令牌生命周期、密码哈希、角色以及 401/403 行为。
- 切勿比较或持久化原始密码。使用强 `PasswordEncoder`，并同时测试认证成功和被拒绝的路径。
- 固定初始超级管理员账号为 `admin`，初始推广用户邮箱为 `19193171667@163.com`；两者密码只以 BCrypt 哈希写入开发重建脚本和生产 `V1`，首次登录后应立即修改默认密码。不得把明文密码写入数据库、日志或 API 响应。
- 将 `is_super_admin` 和 `status` 视为领域规则，而非受信任的请求字段。
- 验证码发送器按 profile 隔离：`local` 仅使用 `ConsoleVerificationCodeSender`，`test` 使用测试 sender；生产环境必须提供真实 sender，不能以 Console 输出代替实际投递。

## 文档规则

- 管理端短剧同步 `/drama/sync/catalog` 与剧集同步 `/drama/sync/content` 为两个独立页面；各自通过展示运行记录按一次触发聚合多语言或多短剧子任务，统一展示创建时间、触发方式、任务类型、状态、新增数、更新数、总处理数和操作。详情查看子任务并支持失败重试；展示层不改变 checkpoint、剧集任务、worker、租约和终态更新模型。

- **每次完成一个功能更新或代码变更后，必须同步更新和沉淀相关文档**（包括本 AGENTS.md、README.md 以及相关模块的设计文档），确保文档与代码保持一致。
- 当命令、模块边界、数据库初始化或运行时前置条件发生变化时，请更新 [README.md]。
- 将已验证的当前行为与未来工作或建议分开记录。
- 除非有意更改 schema 契约，否则保留原有的中文数据库注释和技术名称。
- 不要在聚焦任务中添加无关的重构、生成文件或元数据变更。
# 会话敏感变更补充说明

`MUTATING:{nonce}` 必须通过 `SessionService` 绑定事务完成回调；事务提交和回滚都恢复新的 `ACTIVE:*` 版本，禁止仅注册 `afterCommit` 导致数据库异常后长期遗留 `MUTATING`。

# 当前分佣规则覆盖说明（2026-08-22）

平台分佣规则采用默认配置：每个平台一条记录、无时间限制、无状态、不可删除；POST 首次设置，PUT 直接覆盖五项费率。普通管理员只读，超级管理员可设置和编辑。
- 推广链接当前按批次、媒体平台和用户选择的 `LANDING/ONELINK` 变体生成，不绑定媒体账号或报白状态；每个平台只生成一条所选类型记录，使用独立 `tracking_no/customParams`，失败记录可单独重试。未传变体时兼容旧客户端默认 `LANDING`。
