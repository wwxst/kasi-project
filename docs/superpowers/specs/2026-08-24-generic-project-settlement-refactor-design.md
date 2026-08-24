# 通用项目结算与推广能力重构设计

日期：2026-08-24
状态：设计方案，尚未进入代码实现
适用范围：`kasi-backend`、同级 `kasi-admin-web`、同级 `kasi-user-web`

## 0. 设计结论先行

本方案把业务主线收敛为：

```text
项目管理 -> 结算类型 -> 分佣配置 -> 数据接口 -> 报白方式 -> 数据结算
```

项目是业务载体，`CPA/CPS/CPM` 是可插拔的结算类型，数据源和报白方式是项目级能力配置。通用服务只依赖项目编码、结算类型、规则版本和能力接口，不依赖项目名称；CapCut 的特殊处理只存在于 CapCut 人工文件解析器和规则数据中。CapCut 本期不对接甲方 API，也不保存甲方 API 凭据。

本轮只记录基于当前仓库的重构设计，不修改业务代码，不把规划中的订单、账单或 CapCut 能力描述成已经实现。

## 1. 当前代码已有相关模块和现状

### 1.1 后端包和真实职责

当前根包为 `com.kasi.backend`，服务接口与 `impl/*ServiceImpl` 分离，Controller 使用 `ApiResponse<T>`，请求模型使用 `*DTO`，响应模型使用 `*VO`。

| 模块 | 当前真实实现 | 关键类/接口 |
| --- | --- | --- |
| `admin` | 管理员登录、当前资料、密码、管理员 CRUD | `AdminAuthController`、`AdminManagementController`、`AdminAuthService`、`AdminManagementService` |
| `user` | 推广用户注册、登录、当前用户、密码重置和管理员 CRUD | `UserAuthController`、`UserManagementController`、`UserAuthService`、`UserManagementService`、`PromotionUser` |
| `security` / `auth` | JWT、Redis 会话版本/单会话、验证码、密码重置 Token | `JwtAuthenticationFilter`、`TokenService`、`SessionService`、`VerificationCodeService`、`PasswordResetTokenService` |
| `provider` | 外部短剧平台定义、接入账号、AES-GCM 密钥、能力注册和运行时解析 | `ShortDramaProvider`、`ShortDramaConnection`、`ProviderAdapter`、`ProviderAdapterRegistry`、`ProviderRuntimeConnectionService`、`GoodShortAdapter` |
| `promotion` | 推广用户媒体账号、平台报备任务、推广链接和当前工作区新增的推广任务 | `PromotionMediaAccount`、`ProviderMediaFiling`、`MediaAccountServiceImpl`、`MediaFilingTaskServiceImpl`、`PromotionLinkServiceImpl`、`PromotionTaskServiceImpl` |
| `drama` | GoodShort 短剧目录、剧集、全量/增量同步、检查点、调度和平台默认分佣规则 | `ProviderDrama`、`ProviderDramaContent`、`DramaCatalogSyncServiceImpl`、`ProviderCommissionRuleServiceImpl`、`ProviderCommissionCalculator` |
| `scheduledtask` | 固定任务的周期配置、入队和租约 | `ScheduledTaskController`、`ScheduledTaskDispatchServiceImpl`、`ScheduledTaskScheduler` |

`PromotionTask*` 文件当前是工作区未跟踪改动，`V13__promotion_task.sql` 也是工作区新增迁移；本设计按工作区可见行为记录，但不把它当成已有结算闭环。

### 1.2 当前 API

以下是当前 Controller 实际暴露的端点，括号内为 Controller 类名：

- 管理员认证：`/api/admin/auth/*`（`AdminAuthController`），包括登录、`me`、退出、密码和资料。
- 管理员管理：`/api/admin/management/*`（`AdminManagementController`），包括分页、详情、新增、编辑、启禁用、重置密码、物理删除。
- 推广用户认证：`/api/user/auth/*`（`UserAuthController`），包括注册、验证码、登录、`me`、退出、改密、忘记密码。
- 推广用户管理：`/api/user/management/*`（`UserManagementController`），包括分页、详情、新增、编辑、启禁用、重置密码、物理删除。
- 短剧平台接入：`GET /api/admin/drama/providers`、`PUT /api/admin/drama/providers/{providerId}/connection`、`POST /api/admin/drama/providers/{providerId}/connection/test`，另有平台 `filing-mode` 查询/编辑（`ProviderAdminController`）。
- 媒体账号：用户端 `/api/user/promotion/media-accounts`，管理员端 `/api/admin/promotion/media-accounts`，支持账号 CRUD、状态和报备重试/人工状态。
- 短剧目录：`/api/admin/drama/catalog/*`（`AdminDramaCatalogController`），用户端已发布目录为 `/api/user/promotion/dramas`。
- 推广链接：`GET/POST /api/user/promotion/links`（`UserPromotionLinkController`）。
- 推广任务：工作区新增 `GET/POST /api/user/promotion/tasks`（`UserPromotionTaskController`）；当前只建立任务和统计字段，不生成真实 GoodShort 链接，也不计算真实订单收益。
- 当前分佣规则：`GET/POST /api/admin/drama/providers/{providerId}/commission-rules` 与 `PUT .../{ruleId}`（`ProviderCommissionRuleController`）。代码当前是一个平台一条记录的默认配置；仓库 README 中仍有旧的 `PATCH end-time`、`DELETE` 和时间状态描述，和当前代码/`AGENTS.md` 的覆盖说明不一致，应以后续代码契约为准并在实施阶段清理旧文档。
- 定时任务：`GET/PUT /api/admin/system/scheduled-tasks/*`（`ScheduledTaskController`）。

所有受保护 API 继续使用当前 JWT + Redis 会话安全边界；新项目/结算 API 不应另建一套认证体系。

### 1.3 当前数据库表

`src/main/resources/db/migration/V1__kasi_promotion.sql` 已合并当前 V1-V12 结构，工作区另有 `V13__promotion_task.sql`。当前持久化表如下：

| 表 | 当前用途 | 对本设计的结论 |
| --- | --- | --- |
| `sys_admin_user` | 后台账号 | 直接复用 |
| `promotion_user` | 推广用户，内部 `id`，公开 `user_no` | 直接复用；历史结算按内部 `id` 关联 |
| `short_drama_provider` | 外部短剧平台定义 | 保留，作为外部平台，不把它误当成通用项目 |
| `short_drama_connection` | 平台接入 URL/PID/密钥/`filing_mode` | 保留为平台适配配置；未来项目级数据源/报白配置不能只依赖此表 |
| `promotion_media_account` | 用户媒体账号基表，当前 `media_type` 为 Java 枚举 | 可作为账号兼容基表，需扩展为可配置平台编码并增加 CapCut/TikTok profile |
| `provider_media_filing` | 媒体账号在某一短剧平台接入账号下的报备任务 | 报备任务/租约逻辑可复用；目标模型需增加项目级报备记录 |
| `provider_drama`、`provider_drama_content` | 外部短剧目录和剧集 | 短剧 CPS 项目继续复用 |
| `provider_sync_checkpoint` | 短剧全量/增量同步检查点 | 通用数据同步可复用租约/检查点模式，不复用表语义 |
| `provider_commission_rule` | GoodShort 等短剧平台五项费率默认规则 | 作为 CPS 兼容来源迁移，不能承载 CPA/CPM 或 CapCut 规则 |
| `promotion_link` | GoodShort 推广链接，requestKey 幂等、trackingNo | 短剧 CPS 链接继续复用 |
| `promotion_task` | 工作区新增的推广任务及点击/订单/广告预留计数字段 | 仅是推广任务，不是订单、账单或结算明细 |
| `system_scheduled_task` | 固定调度周期和入队租约 | 通用数据同步、账单任务可复用调度框架 |

当前没有 `CapCut` 账号表、TikTok 独立报白模型、上游数据批次/原始记录、TalentID 归因、订单、账单、结算明细或税费表。

### 1.4 当前短剧 CPS 逻辑

`ProviderCommissionRuleServiceImpl` 将请求中的 `0..100` 百分比转换为数据库 `0..1` 高精度比例；`provider_commission_rule` 的五项字段为：

1. `channel_fee_rate`
2. `principal_fee_rate`
3. `principal_commission_rate`
4. `downstream_fee_rate`
5. `downstream_commission_rate`

`ProviderCommissionCalculator` 当前公式为：

```text
金额
× (1 - 渠道费率)
× (1 - 甲方手续费率)
× 甲方分佣比例
× (1 - 我方手续费率)
× 下游分佣比例
```

中间结果保持 `BigDecimal`，最终 `HALF_UP` 保留两位。`PromotionLinkServiceImpl` 只检查平台存在默认规则后生成 GoodShort 链接；它没有读取订单、没有保存订单费率快照，也没有形成结算单。

### 1.5 当前报白逻辑

`AccountFilingProviderAdapter`、`AccountFilingSubmission`、`AccountFilingQuery`、`AccountFilingResult` 和 `ProviderAdapterRegistry` 已经提供了较好的适配边界。`MediaAccountServiceImpl` 根据运行时能力创建 `ProviderMediaFiling`，`MediaFilingTaskServiceImpl` 用 `SUBMIT/QUERY/NONE`、重试、租约和周期查询执行异步任务。`FilingMode.API/MANUAL` 当前挂在 `short_drama_connection` 上：API 模式调用 `GoodShortAdapter`，人工模式由管理员更新状态。

这套机制是“外部平台报备”的实现基础，但它还不是“项目级报白配置”：没有项目、配置版本、用户可见操作说明，也不能表达 CapCut 自助加入团队后不需要回填 TikTok 视频链接的规则。

### 1.6 当前前端页面

#### 管理后台 `kasi-admin-web`

`src/router/AppRouter.tsx` 当前路由包括：

- `/dashboard` -> `DashboardPage`
- `/profile` -> `ProfilePage`
- `/admin-management` -> `AdminManagementPage`
- `/user-management` -> `UserManagementPage`
- `/promotion/media-accounts` -> `MediaAccountFilingPage`
- `/drama/catalog` -> `DramaCatalogPage`
- `/system-config/drama-api` -> `ProviderManagementPage`
- `/system-config/scheduled-tasks` -> `ScheduledTaskPage`
- `/system-config/commission-rules` -> `CommissionRulePage`

`AdminLayout` 的导航仍然按“短剧管理/推广管理/系统配置”组织，没有项目管理、数据导入、未匹配数据、账单和对账页面。

#### 推广用户端 `kasi-user-web`

`src/app/AppRouter.tsx` 当前路由包括：

- `/account` -> `AccountPage`
- `/account/security` -> `SecurityPage`
- `/account/filing` -> `MediaAccountFilingPage`
- `/promotion/links` -> `PromotionLinkPage`
- `/promotion/tasks` -> `PromotionTaskPage`

当前用户端没有项目列表/详情、项目规则、CapCut 账号、TikTok 独立报白、推广数据、月度账单或结算明细页面。

## 2. 现有设计存在的问题

1. **平台和项目混用**：`short_drama_provider` 表示 GoodShort 这类外部平台，而用户需求中的“短剧推广”是一个结算项目。若直接把 CapCut 插入 `short_drama_provider`，项目介绍、规则、数据源、报白和结算周期会被平台字段绑死。
2. **分佣模型只有 CPS 的五费率**：`provider_commission_rule` 没有行为数量、播放量、区域阶梯、用户单价、上游单价、活动封顶、税率或规则版本，无法表达 CapCut CPA/CPM。
3. **没有结算事实链路**：当前没有原始数据批次、原始行、归因、结算月份、账单、结算明细或对账状态；`promotion_task.orderAmount` 等字段不能替代这些模型。
4. **无法保留历史价格**：当前任何规则变更都没有结算明细价格快照。目标模型必须在生成明细时保存当时的上游价、用户价、税率、规则版本和封顶结果。
5. **CapCut 关键归因字段缺失**：没有 `CAPID`、`CapCut UID/TalentID`、账号地区（`US/EU/ROW`）以及 TalentID 未匹配队列。
6. **报白配置层级不对**：`FilingMode` 在 `short_drama_connection`，只适用于某个平台接入账号；API/人工应是任何项目的配置能力。
7. **T+14 和月份口径没有实现**：不存在 CapCut 数据导入，也不存在按发稿日期归属月份的规则。未来不能用甲方“结算时间”做月份，也不能在系统重新判断 T+14。
8. **税费规则缺失且旧规则容易污染新模型**：当前代码没有 5% 手续费实现；新账单只能保存 3% 税率、税费和税后实结金额，不能改写用户单价。
9. **账号边界需要显式保护**：当前没有 CapCut/TikTok 关系，目标也不应添加关系表；如果为了“发布链路”引入绑定，会违背业务规则。
10. **前端是短剧专用页面**：`ProviderManagementPage`、`CommissionRulePage`、`MediaAccountFilingPage` 的文案和 API 都是短剧/GoodShort 语义，无法直接承载项目级配置。
11. **文档契约不一致**：README 的旧分佣章节仍描述时间状态、`PATCH` 和 `DELETE`，但当前代码和 `AGENTS.md` 的 2026-08-22 覆盖说明已经是“一个平台一条、直接覆盖、不可删除”。实施前必须以代码和最新覆盖说明为准，清理旧文档。

## 3. 可以直接复用的代码

### 3.1 必须复用

- `promotion_user`、`sys_admin_user`、JWT/Redis 会话、`AuthContextHolder`、统一错误处理和 DTO/VO 分层。
- `ProviderAdapter`、`ProviderAdapterRegistry`、`ProviderRuntimeConnectionService`、`ProviderCredentialCipher`：外部 API 连接、能力声明、密钥解密和适配器选择方式。
- `AccountFilingProviderAdapter` 及 `MediaFilingTaskServiceImpl` 的提交/查询、租约、重试和失败记录模式。
- `DramaCatalogSyncServiceImpl` 的断点、租约、幂等 upsert、远端状态与本地状态分离方式。
- `ProviderCommissionCalculator` 的 `BigDecimal` 计算纪律，迁移为 CPS 计算器时保持现有公式和舍入结果。
- `PromotionLinkServiceImpl`、`GoodShortAdapter` 的短剧链接生成和外部字段快照，继续作为“短剧 CPS 项目适配器”。
- 现有测试基类 `BaseAuthTest`、H2 MySQL 兼容模式、迁移测试和各模块 Service/Controller 测试结构。
- 前端 `AdminLayout`、`AccountLayout`、认证状态、HTTP 客户端、分页表格和现有短剧页面的交互骨架。

### 3.2 只作为兼容层，不直接扩展成通用模型

- `short_drama_provider` / `short_drama_connection`：仍代表外部短剧平台。
- `provider_commission_rule`：作为短剧 CPS 旧规则来源或兼容读取，不承载 CapCut CPA。
- `provider_media_filing`：短剧平台报备的旧持久化模型，迁移期间继续运行。
- `promotion_task` 的订单/广告计数：只表示推广统计占位，不映射为结算金额。

## 4. 推荐的整体领域模型

### 4.1 核心对象

```text
SettlementProject
  ├─ ProjectProviderBinding -> ShortDramaProvider（短剧 CPS 兼容绑定）
  ├─ ProjectRuleVersion
  │    ├─ RuleRateTier（行为/播放/销售和地区阶梯）
  │    ├─ RuleCap（按视频/活动等维度封顶）
  │    └─ RulePolicy（归因、月份、周期、T+14说明、税率）
  ├─ ProjectDataSourceConfig（API / MANUAL）
  ├─ ProjectFilingConfig（API / MANUAL）
  ├─ ProjectGuide（面向用户的规则和操作说明）
  └─ ProjectAccount
       └─ PromotionMediaAccount + CapCut/TikTok 独立 profile

SettlementBatch
  └─ SettlementRawRecord
       └─ SettlementAttribution（TalentID -> CapCut账号 -> 推广用户）
            └─ SettlementDetail -> SettlementBill
```

### 4.2 项目

建议新增 `settlement_project`，核心字段：

- `id`、`project_code`（唯一、稳定、用于 API/适配器选择）、`project_name`、`introduction`、`rule_description`。
- `settlement_type`：`CPA`、`CPS`、`CPM`。
- `status`：`DRAFT`、`ENABLED`、`DISABLED`。
- `currency`、`data_source_mode`、`filing_mode`、`current_rule_version_id`。
- 审计字段和创建/更新管理员。

初始种子项目：

| `project_code` | 名称 | 类型 | 绑定/说明 |
| --- | --- | --- | --- |
| `CAPCUT_ACQUISITION` | CapCut 拉新 | `CPA` | 固定使用 `MANUAL` 数据源和 CapCut 文件解析器；含 `NEW_USER`、`ACTIVE_USER` 两类指标 |
| `SHORT_DRAMA_PROMOTION` | 短剧推广 | `CPS` | 绑定现有 `GOODSHORT`，继续使用短剧目录、链接、报备和 CPS 五费率公式 |

新增项目只需新增项目记录、规则版本、数据源/报白配置和适配器注册，不允许在服务中写 `if (projectName.equals(...))`。

### 4.3 规则与展示说明分离

`ProjectGuide`/`rule_description` 是展示文本，可包含“月结流程、团队链接、网络节点、T+14 已由甲方完成”等说明；`ProjectRuleVersion` 和子表是程序执行配置。不要因为一条说明就新增业务表。

程序规则至少包含：

- 规则版本、生效时间、失效时间、币种。
- 指标/结算单位：`NEW_USER`、`ACTIVE_USER`、`SALE_AMOUNT`、`PLAY_COUNT` 等。
- 地区维度：`US`、`NON_US`、`EU`、`ROW`、`GLOBAL` 等，原始地区仍保存于数据记录。
- 上游结算单价和用户展示/结算单价，分别保存。
- 封顶维度和金额，例如 `VIDEO` + `$2500`。
- 归因模型、结算周期、月份依据、是否信任上游已完成 T+14。
- 税率快照默认 `0.03`；不再写入旧的“非卡司学员 5% 手续费”。

CapCut 初始规则数据建议：

| 指标 | 条件 | 上游单价 | 用户税前单价 |
| --- | --- | ---: | ---: |
| 拉新 `NEW_USER` | `US` | `$5.00` | `$4.00` |
| 拉新 `NEW_USER` | 非美区（初始可落为 `NON_US`，原始 `EU/ROW` 保留） | `$1.00` | `$0.80` |
| 拉活 `ACTIVE_USER` | `GLOBAL` | `$0.28` | `$0.25` |

拉新、拉活各自拥有封顶配置 `$2500 / VIDEO`，不能把封顶写到项目全局。归因“锚点归因”、`T+14`、月结和“甲方已完成 T+14”属于规则策略和展示说明，不由代码重新计算 T+14。

CapCut 加白操作说明应作为项目规则展示数据保存，不能散落在前端常量中：

| 账号注册地区 | 地区解释 | 团队链接 |
| --- | --- | --- |
| `US` | 美国 | `https://www.capcut.com/activities/mcn/pioneer-plan?team_id=204940513286` |
| `EU` | 英国、法国、德国 | `https://www.capcut.com/activities/mcn/pioneer-plan?team_id=153564697348` |
| `ROW` | 除美国、英国、法国、德国以外的其他地区 | `https://www.capcut.com/activities/mcn/pioneer-plan?team_id=153814238468` |

用户必须在对应国家/区域网络节点下复制链接打开；加入团队后 CapCut 自动完成加白，系统不要求回填已经发布的 TikTok 推广视频链接。链接、地区说明和“自动加白”是展示/操作规则；本期 CapCut 不调用甲方报白 API，账号加白状态由用户操作后按项目配置人工维护。

## 5. 数据库表新增/修改方案

### 5.1 项目和配置表

建议新增：

1. `settlement_project`：项目基本信息、结算类型、状态和货币。
2. `settlement_project_provider`：项目与外部平台的绑定；短剧项目绑定 `short_drama_provider`，CapCut 不强行绑定短剧平台表。
3. `settlement_rule`：规则版本、指标族、周期、月份依据、归因策略、默认税率和审计字段。
4. `settlement_rule_rate`：规则版本下的指标/地区阶梯，保存上游价和用户价。
5. `settlement_rule_cap`：规则版本的封顶维度和金额。
6. `settlement_project_guide`：用户可见规则/操作说明、地区团队链接、网络节点提示等；纯展示内容不参与计算。
7. `settlement_data_source`：项目级 `API`/`MANUAL`、适配器编码、接口元数据、启停、同步周期和凭据引用。密钥通过现有 `ProviderCredentialCipher` 同类能力加密，响应不返回密文。
8. `settlement_filing_config`：项目级 `API`/`MANUAL`、报白适配器编码、接口元数据、启停、人工处理说明和凭据引用。

API URL、PID、KEY 等外部平台字段仍可留在 `short_drama_connection`，但项目服务只能通过配置/适配器读取，不能假定所有项目都有这些字段。

### 5.2 账号和报白表

采用“一个账号基表 + 独立 profile”的增量方案，避免推翻现有账号关联：

- `promotion_media_account` 保留为账号基表，逐步将 Java `MediaType` 和数据库 `media_type` 迁移为可扩展 `platform_code`。
- 新增 `promotion_project_account`：`project_id + media_account_id`，表示账号参与哪个项目。它不表达 CapCut 与 TikTok 的关系。
- 新增 `promotion_capcut_account_profile`：`media_account_id`、`capid`、`capcut_uid`（甲方 `TalentID`）、`region`（`US/EU/ROW`）。
- 如 TikTok 需要超出基表的字段，新增 `promotion_tiktok_account_profile`；该表只通过自己的 `media_account_id` 关联用户账号和项目，不引用 CapCut profile。
- 新增 `promotion_project_filing`：项目账号、`filing_config_id`、模式、状态、远端状态、人工处理人、任务租约和错误摘要。短剧迁移期间由它兼容映射 `provider_media_filing`。

禁止新增 `capcut_account_tiktok_account`、`capcut_video_tiktok_video` 或任何 CapCut-TikTok 绑定/发布关系表。CapCut 跳转多个 TikTok 发布属于项目操作规则，只写入 `settlement_project_guide`。

### 5.3 数据、归因和结算表

建议新增：

1. `settlement_batch`：项目、数据源、上游批次号、导入/同步时间、原始文件引用、状态、行数、错误数和幂等键。
2. `settlement_data_record`：不可变或追加式的标准化原始行。至少保存：
   - `talent_id`、视频 ID、视频链接、发稿日期；
   - 甲方结算/更新时间；
   - `metric_code`（拉新/拉活）、原始 `region`（US/EU/ROW）；
   - 甲方原始金额、有效数量、上游原始字段、原始 JSON、来源批次和行号。
   - 不得用视频 ID 做唯一键；同一视频在多个结算时间出现时以来源批次/来源行/外部记录标识区分。
3. `settlement_attribution`：TalentID 匹配结果、CapCut profile、推广用户、匹配状态（`MATCHED/UNMATCHED/MANUAL_CONFIRMED`）、处理审计。收益归属只走 `TalentID -> CapCut UID -> promotion_user`，不走 TikTok。
4. `settlement_bill`：项目、推广用户、结算月份、币种、税前总额、税率、税费、税后实结金额、对账/开票/付款状态。
5. `settlement_detail`：原始记录、规则版本、规则费率快照、有效数量、用户税前单价、税前佣金、封顶前后金额、税率、税费、实结金额、结算月份和异常原因。
6. `settlement_reconciliation`（可在对账阶段加入）：甲方账单批次、核对结果、差异原因和管理员处理记录。

规则修改后，新的 `settlement_detail` 保存新的价格快照；已有账单和明细不重新读取当前规则。普通推广用户 VO 只返回用户单价、税前佣金、税费和实结金额，不返回上游价。

## 6. 后端模块及 Service 分层方案

### 6.1 新模块建议

保持现有包结构风格，新增：

```text
com.kasi.backend.project
  controller / dto / entity / enums / mapper / service / service.impl / vo

com.kasi.backend.settlement
  calculator/
  datasource/
  filing/
  controller/
  dto/ entity/ enums/ mapper/ service/ service.impl/ vo/
```

CapCut 的字段映射和文件解析放在 `settlement.datasource.capcut` 或 `project.adapter.capcut`，不污染 `settlement.service.impl`。

### 6.2 Service 接口边界

- `ProjectManagementService`：项目 CRUD、启停、基础信息。
- `ProjectRuleService`：规则版本、费率阶梯、封顶、税率和展示规则；写入需要锁定项目行。
- `ProjectDataSourceService`：API/人工数据源配置和测试；CapCut 项目只允许保存 `MANUAL` 配置，其他项目才可选择 API。
- `ProjectFilingConfigService`：API/人工报白配置和人工说明。
- `ProjectCatalogService`：用户项目列表/详情和规则展示，按权限隐藏上游价。
- `ProjectAccountService`：项目账号挂载和账号 profile；不创建 CapCut/TikTok 关系。
- `SettlementDataIngestionService`：接收 API 或人工批次，写入批次和原始记录。
- `SettlementAttributionService`：按项目声明的归因键匹配账号和用户，输出未匹配队列。
- `SettlementCalculationService`：读取规则版本，调用结算类型计算器，生成不可变结算明细。
- `SettlementBillService`：按项目规则的月份依据聚合月度账单、应用税率快照、推进对账/开票/付款状态。
- `SettlementQueryService`：管理员全量查询、推广用户自身项目数据/账单/明细。

所有 Service 继续采用接口 + `impl`；多表写操作使用 `@Transactional`，只读查询使用 `@Transactional(readOnly = true)`。敏感账号/profile 变更沿用当前 Redis `MUTATING` 先行策略。

## 7. CPA / CPS / CPM 如何抽象

### 7.1 统一接口

建议定义：

```java
public interface SettlementCalculator {
    SettlementType type();
    SettlementLineResult calculate(SettlementFact fact, SettlementRuleSnapshot rule);
}
```

由 `SettlementCalculatorRegistry` 按 `SettlementType` 注册 `CpaSettlementCalculator`、`CpsSettlementCalculator`、`CpmSettlementCalculator`。`SettlementCalculationService` 只按 `project.settlementType` 取计算器，不读取项目名称。

统一输入包含项目、规则版本、币种、指标、数量、上游金额、地区、视频维度、发稿日期和上游结算时间；统一输出包含上游金额、用户税前佣金、封顶结果、税费和税后金额。

### 7.2 CPA

`valid_quantity × user_unit_price` 得到税前用户佣金；上游金额和上游单价独立保存。封顶按规则版本指定的维度（当前 CapCut 为单条视频 `$2500`）执行。有效数量由甲方确认数据提供，系统不重新判断 T+14。

### 7.3 CPS

首期直接把现有 `ProviderCommissionCalculator` 提取为 `CpsSettlementCalculator` 的 GoodShort 费率策略，保持五费率公式和 `BigDecimal` 舍入结果。`provider_commission_rule` 通过 `settlement_project_provider` + `settlement_rule` 兼容映射；短剧现有目录、链接、报备、Provider Adapter 继续使用。新增订单同步/退款时，订单应先保存规则和费率快照，再生成 CPS 明细；当前仓库尚未实现这些订单表，不能把现有链接/任务描述成已完成收益闭环。

### 7.4 CPM

`billable_play_count / 1000 × user_unit_price`（千次展示/播放）得到税前佣金，具体单位必须由规则版本明确；上游曝光量和价格仍独立记录。后续新增 CPM 项目只需新增规则数据和计算器，不改项目服务分支。

## 8. API/人工数据源如何抽象

定义项目级 `DataSourceMode`：`API`、`MANUAL`，并用 `SettlementDataSourceAdapter` 隔离具体来源。该抽象是为未来项目保留扩展能力，不意味着每个项目都必须接 API；`CAPCUT_ACQUISITION` 的配置校验必须限制为 `MANUAL`。

```java
public interface SettlementDataSourceAdapter {
    String adapterCode();
    Set<DataSourceMode> supportedModes();
    default SettlementBatchFetchResult fetch(ProjectDataSourceConfig config, FetchWindow window) {
        throw new UnsupportedOperationException("当前数据源不支持 API 获取");
    }
    List<SettlementRawRow> parseManual(ProjectDataSourceConfig config, InputStream input);
}
```

- API 模式：仅供未来确有甲方接口的项目使用，调度器按项目配置调用 adapter，记录请求窗口、响应摘要、原始 payload 和批次幂等键。
- 人工模式：后台上传 CSV/Excel/JSON 或提交人工批次，解析结果仍走同一 `SettlementBatch -> SettlementDataRecord` 管道。CapCut 只走这条路径：管理员从甲方系统导出文件，上传后由 CapCut 文件解析器校验并导入。
- CapCut 不配置 API URL、PID、KEY、同步任务或 API 凭据；人工导入页面应显示文件模板、列映射、批次预览、错误行下载和确认导入。
- 两种模式都先落原始数据，再做标准化、校验、归因和计算；任何异常行进入批次错误/未匹配列表，不能只生成最终金额。
- 凭据和 API URL 通过配置引用/加密存储，普通管理员和推广用户响应不暴露密钥。
- 调度、重试、租约和去重沿用 `ScheduledTaskScheduler`、`DramaCatalogSyncServiceImpl` 的模式；项目编码只作为配置键，不作为业务 `if/else`。

## 9. API 报白/人工报白如何抽象

项目级 `FilingMode` 仍只有 `API`、`MANUAL`，但不再固定在 `short_drama_connection`：

- `ProjectFilingConfig` 选择 `adapter_code`、接口/凭据、支持的账号平台和人工操作说明。
- `ProjectFilingService` 根据模式：API 模式创建异步任务并调用 `ProjectFilingAdapter`；人工模式创建 `MANUAL_PENDING` 记录，管理员操作后写状态和操作人。
- 现有 `AccountFilingProviderAdapter` 可以作为短剧实现适配器；`MediaFilingTaskServiceImpl` 的状态机和租约逻辑抽到通用任务服务，旧 `provider_media_filing` 继续兼容运行。
- CapCut 自助加入团队的 US/EU/ROW 链接和网络节点说明属于项目规则展示；本期配置为 `MANUAL`/自助操作，不创建或伪造甲方 API 报白任务。
- TikTok 账号单独添加、单独提交、单独查看报白状态。它可以和 CapCut 项目同时存在，但数据库中没有彼此 FK。

## 10. CapCut 数据导入与结算完整调用链

```text
管理员配置 CAPCUT_ACQUISITION(CPA)
  -> 配置规则版本/费率/封顶/3%税率/发稿日期月份口径
  -> 项目固定配置为 MANUAL 数据源，管理员从甲方系统导出文件
  -> 用户创建 CapCut 账号(profile.capcut_uid = TalentID, region)
  -> 用户按 US/EU/ROW 查看团队链接并自行加入
  -> 用户另行添加 TikTok 账号并走独立报白（无关联）
  -> API 同步或人工导入创建 SettlementBatch
  -> 原始行落库 SettlementDataRecord（保留 TalentID、视频、发稿日期、结算时间、类型、地区、金额、数量、payload）
  -> SettlementAttributionService 用 TalentID 匹配 CapCut UID
       -> MATCHED：得到 promotion_user
       -> UNMATCHED：进入后台异常队列，允许人工确认，不删除原始行
  -> SettlementCalculationService 按 publish_at 和规则版本取价
  -> CpaSettlementCalculator 计算数量×用户单价，按该规则的单视频封顶
  -> 生成 SettlementDetail，保存用户单价/规则版本/封顶前后金额快照
  -> 按 publish_at 所在月份聚合 SettlementBill
  -> 账单单独计算 3% 税费和税后实结金额
  -> 管理员核对甲方账单、开票、标记甲方付款和用户结算
  -> 用户查看项目数据、月度账单和结算明细
```

关键约束：

- 结算月份只取发稿日期；“甲方结算时间”只记录数据产生/更新时间，不参与月份归属。
- 同一视频多次出现按来源记录保留，不能按视频 ID 去重删除。
- T+14 已由甲方完成；系统将导入行视为甲方确认数据，不等待或重算 T+14。
- 用户展示单价仍为 `$4/$0.8/$0.25`；账单另列税前佣金、3% 税费和税后实结金额。
- 上游价只给管理员对账使用，不出现在普通用户 VO 或用户页面。

## 11. 管理后台需要重构/新增的页面

### 11.1 重构现有页面

- `ProviderManagementPage`：保留为“外部平台接入/适配器配置”，从项目管理中剥离；GoodShort URL/PID/KEY 仍在这里维护。
- `CommissionRulePage`：改为项目规则入口，支持项目、结算类型、规则版本、指标/地区单价、封顶、税率和用户可见说明；短剧 CPS 详情继续显示现有五费率。
- `MediaAccountFilingPage`：改成通用“项目账号与报白”工作台，保留 GoodShort 媒体账号兼容查询，增加 CapCut/TikTok 分开筛选。
- `DramaCatalogPage`：保留为短剧 CPS 项目内容管理，不改成通用项目数据页。
- `ScheduledTaskPage`：增加项目数据同步/账单任务的可观测入口，但仍由固定任务编码和配置驱动。

### 11.2 新增页面

- `ProjectManagementPage`：项目列表、新增、编辑、启停和项目类型。
- `ProjectDetailPage`：介绍、规则展示、结算类型、数据源、报白方式和能力状态。
- `ProjectRuleEditorPage`：规则版本、指标/地区费率、上游价/用户价、封顶、税率、月份口径和生效时间。
- `ProjectDataSourcePage`：API/人工选择、API 配置、手工导入入口和同步记录。
- `ProjectFilingConfigPage`：API 报白/人工报白配置、凭据和操作说明。
- `CapCutAccountPage`：CAPID、CapCut UID/TalentID、地区、加白状态和用户归属。
- `TikTokFilingManagementPage`：TikTok 账号独立列表、提交报白、审核状态和失败重试。
- `SettlementBatchPage`：甲方批次/API 同步、原始行数、失败行和导入结果。
- `SettlementUnmatchedPage`：未匹配 TalentID、原始数据查看、人工归属和处理审计。
- `SettlementReconciliationPage`：按发稿月核对甲方账单、差异、开票、甲方付款和用户结算。
- `MonthlyBillPage`、`SettlementDetailPage`：管理员查看账单汇总和逐行结算明细。

建议后台 API 使用 `/api/admin/projects/**`、`/api/admin/settlements/**`、`/api/admin/projects/{projectId}/accounts/**` 等资源路径；这些是目标 API，不是当前已存在端点。

## 12. 推广用户端需要重构/新增的页面

- `ProjectListPage`：项目列表和启用状态。
- `ProjectDetailPage`：项目介绍、结算类型、用户单价、结算周期、规则说明、税费说明和报白入口；隐藏上游价。
- `ProjectAccountPage`：按项目维护账号。CapCut 录入 CAPID、CapCut UID、地区并展示对应团队链接/网络节点；TikTok 账号在独立区域维护。
- `TikTokFilingPage`：TikTok 独立报白入口和状态查询。
- `PromotionDataPage`：项目维度查看已归因数量、视频、拉新/拉活等推广数据；不显示未确认收益为真实订单。
- `MonthlyBillPage`：按发稿月查看税前应结佣金、3% 税费和税后实结金额及账单状态。
- `SettlementDetailPage`：查看明细的发稿日期、视频、指标、地区、有效数量、用户单价、封顶、税费和实结金额。

保留现有 `PromotionLinkPage`、`PromotionTaskPage` 作为短剧 CPS 的推广入口；它们不应被改造成 CapCut 视频回填页面。CapCut 流程不要求用户回填已经发布的 TikTok 视频链接。

建议用户 API 使用 `/api/user/projects/**`、`/api/user/projects/{projectId}/accounts/**`、`/api/user/settlements/**`；这些是目标 API，不是当前已存在端点。

## 13. 对现有代码的影响范围

### 高影响

- 新增 `project`、`settlement` 领域实体、Mapper、Service、Controller、VO/DTO 和迁移测试。
- `provider_commission_rule` 的读取入口需要改为 CPS 兼容适配，不能再被当成全平台分佣表。
- `promotion_media_account` 的平台编码扩展、CapCut/TikTok profile、项目账号挂载和项目报白记录。
- `ProviderCommissionCalculator` 提取到结算计算器注册表，同时确保 GoodShort 结果不变。
- 前后台路由、导航、API 类型和页面测试。

### 中影响

- 未来若某项目需要 API 数据源，再增加独立的数据源适配器解析入口；不把 CapCut 数据导入接到 `ProviderRuntimeConnectionService`，短剧 Provider 路径保持独立。
- `MediaFilingTaskServiceImpl`、`ProviderMediaFilingMapper` 抽取通用状态机/任务租约，旧表继续工作到迁移完成。
- `ScheduledTaskCode`、调度配置增加项目数据同步和账单任务；调度器仍只入队，执行器负责业务。
- README、`AGENTS.md` 和旧分佣设计文档要统一当前契约与目标设计边界。

### 无需改变

- 管理员/推广用户认证、JWT、Redis 会话和权限模型。
- GoodShort 短剧目录、剧集同步的外部适配字段。
- CapCut 与 TikTok 不建关联的业务约束。

## 14. 数据库迁移及历史数据兼容方案

### 14.1 迁移顺序

当前项目开发阶段允许删除数据库重建，但不能把这项便利当成生产迁移策略。建议生产按 Flyway 新版本增量迁移：

1. `V14__settlement_project.sql`：项目、项目与短剧平台绑定、数据源/报白配置、规则展示说明；植入 `CAPCUT_ACQUISITION` 和 `SHORT_DRAMA_PROMOTION`。
2. `V15__settlement_rule.sql`：规则版本、费率阶梯、封顶、税费和索引；把当前 `provider_commission_rule` 映射为短剧 CPS 基线规则。
3. `V16__project_account_filing.sql`：项目账号、CapCut/TikTok profile、项目报白；保留 `promotion_media_account` 和 `provider_media_filing`。
4. `V17__settlement_ingestion.sql`：批次、原始记录、归因和未匹配索引。
5. `V18__settlement_bill_detail.sql`：账单、明细、税费快照、对账状态和审计字段。
6. 后续若其他项目需要 API 数据源，再增加对应的非敏感配置字段；CapCut 不增加甲方 API adapter 或 API 凭据字段，任何密钥都不写入迁移脚本。

### 14.2 历史兼容

- 现有用户、管理员、短剧平台、连接、媒体账号、报备、目录、链接和任务数据不删除。
- `short_drama_provider` 与 `short_drama_connection` 继续由短剧 CPS 使用；项目绑定只是新的一层语义。
- `provider_commission_rule` 的现有行迁移成 `SHORT_DRAMA_PROMOTION` 的初始 CPS 规则，并保留 `legacy_rule_id`；在切换完成前支持双读/对账。
- 当前没有真正的订单/账单/结算明细，因此不存在 CapCut 历史结算迁移；不能用 `promotion_task` 计数反推账单。
- 新规则生效后只影响新生成的 `settlement_detail`；旧明细永远使用已保存的价格/税率快照。
- 开发数据库可在 V14-V18 期间按指南删除重建；生产环境必须逐个版本执行并验证外键、唯一键和金额精度。

## 15. 推荐的分阶段实施顺序

### 阶段 0：契约和文档整理

- 统一 README/`AGENTS.md` 中当前分佣规则契约，删除旧时间状态 API 的误导描述。
- 确认 `project_code`、`SettlementType`、金额精度、月份依据、税费和上游字段隐藏策略。
- 先补结构测试和迁移测试，不改现有业务路径。

### 阶段 1：项目管理和规则展示

- 建立 `settlement_project`、项目绑定、规则版本、展示说明和项目级 API/人工配置。
- 种子 CapCut CPA 与短剧 CPS；后台先上线项目列表/详情/规则配置，用户端上线项目列表/规则展示。
- 这阶段不导入 CapCut 数据，不改变现有短剧链接和报备。

### 阶段 2：通用账号和报白

- 扩展账号平台编码，新增 CapCut/TikTok profile、项目账号和项目报白记录。
- 抽取现有 `AccountFilingProviderAdapter` / `MediaFilingTaskServiceImpl` 的通用状态机；GoodShort 走兼容适配器。
- 上线 CapCut 加白说明和 TikTok 独立报白页面；增加“无 CapCut-TikTok 关系”的约束测试。

### 阶段 3：数据源、原始数据和归因

- 实现 API/人工同管道、批次幂等、原始数据保存、TalentID 匹配和未匹配后台。
- 先接 CapCut 人工导入，用固定 CSV/JSON 模板测试完整链路；API 数据源只作为未来其他项目的扩展，不属于 CapCut 本期范围。
- 仅验证数据质量和归属，不生成用户账单。

### 阶段 4：CPA 结算和月度账单

- 实现 `CpaSettlementCalculator`、规则价格快照、按发稿月聚合、单视频封顶、3% 税费和账单状态。
- 用 CapCut 拉新/拉活、US/EU/ROW、重复结算时间、未匹配 TalentID、价格变更和税费案例覆盖 Service/Mapper/Controller 测试。
- 上线后台对账/账单和用户账单/明细页面。

### 阶段 5：CPS 兼容迁移

- 将当前 `ProviderCommissionCalculator` 接入通用 `CpsSettlementCalculator`，先用 GoodShort 对照测试证明金额不变。
- 后续实现订单同步、订单费率快照和 CPS 账单；在此之前继续明确“短剧链接/推广任务不是收益闭环”。

### 阶段 6：CPM 和新项目扩展

- 增加 CPM 规则和计算器，使用同一数据批次、归因、账单和税费管道。
- 新项目只新增项目配置、规则数据、数据源/报白 adapter 和页面配置，不新增项目名称判断。

## 16. 验证和验收标准

- 后端每新增 Controller、Service、Mapper、迁移或安全规则，都有对应 H2/MySQL 模式测试；认证测试继续继承 `BaseAuthTest`。
- 结算计算器使用参数化测试覆盖 CPA/CPS/CPM、金额精度、封顶和 `HALF_UP`；CPS 迁移必须与 `ProviderCommissionCalculator` 结果一致。
- 迁移测试验证唯一键、外键、金额精度、3% 税率默认值和初始项目种子；任何密钥不出现在 SQL、日志或 VO。
- 人工导入测试验证文件列映射、批次预览/确认、同一原始行幂等、重复视频不同结算时间不丢失、原始文件和原始行可追溯、TalentID 未匹配不会生成账单；通用 API 数据源另行测试，不纳入 CapCut 验收。
- CapCut 测试验证按发稿日期归属月份、不会重新计算 T+14、CapCut/TikTok 无关联表、用户看不到上游价、历史价格修改不影响旧明细。
- 前端测试覆盖项目规则展示、CapCut/TikTok 分离、税前/税费/实结金额展示和短剧 CPS 现有页面回归。
