# 短剧 CPS 完整化范围与重构边界

日期：2026-08-24
状态：提议，尚未进入代码实现
范围：当前 `kasi-backend`、同级 `kasi-admin-web`、同级 `kasi-user-web` 的 GoodShort 短剧推广链路

## 1. 本轮结论

本轮不做 CapCut、CPA、CPM、通用项目管理、甲方 API 数据源和通用结算框架，只把现有短剧 CPS 做成可追溯的完整链路：

```text
短剧目录 -> 媒体账号报白 -> 推广链接/任务 -> GoodShort 订单同步
       -> trackingNo 归因 -> CPS 费率快照 -> 佣金明细
       -> 月度账单 -> 管理员对账/用户结算查询
```

这里的“完成”指系统能保存原始订单、准确归属推广用户、固定历史费率并生成可核对的账单；不包含钱包余额、提现、付款通道和财务出款。

## 2. 当前已具备的能力

### 2.1 后端

| 能力 | 当前代码 | 当前状态 |
| --- | --- | --- |
| 外部平台接入 | `ShortDramaProvider`、`ShortDramaConnection`、`ProviderRuntimeConnectionServiceImpl` | 已实现 GoodShort 连接、AES-GCM 密钥解密和能力解析 |
| GoodShort 适配 | `GoodShortAdapter`、`GoodShortSigner` | 已实现连接探测、目录、报备、推广链接；已声明 `ORDER_SYNC`/`ANALYTICS_SYNC`，但尚无对应 SPI 方法和同步服务 |
| 目录同步 | `DramaCatalogSyncServiceImpl`、`ProviderSyncCheckpoint` | 已实现全量/增量、检查点、租约、远端与本地状态分离 |
| 推广元数据 | `ProviderDrama`、`V14__provider_drama_promotion_metadata.sql`、`UpdateDramaPromotionMetadataDTO` | 工作区当前正在补齐，目录同步不会覆盖本地推广说明和分佣范围 |
| 媒体账号 | `PromotionMediaAccount`、`MediaAccountServiceImpl` | 已实现用户绑定、启停、资料版本和全局平台账号唯一 |
| 报白 | `ProviderMediaFiling`、`AccountFilingProviderAdapter`、`MediaFilingTaskServiceImpl` | 已实现 API/人工模式、提交/查询、租约、重试和管理员人工状态 |
| 链接 | `PromotionLink`、`PromotionLinkServiceImpl`、`GoodShortAdapter.generatePromotionLink` | 已实现 `requestKey` 幂等、`trackingNo`、`customParams`、口令/链接保存 |
| 推广任务 | `PromotionTask*`、`V13__promotion_task.sql` | 工作区新增；目前只创建任务和统计占位字段，未生成真实 GoodShort 链接 |
| 分佣规则 | `provider_commission_rule`、`ProviderCommissionRuleServiceImpl`、`ProviderCommissionCalculator` | 当前每个平台一条五费率默认配置，`BigDecimal` 计算器已实现；直接覆盖规则 |

### 2.2 前端

- 管理端：`DramaCatalogPage`、`ProviderManagementPage`、`MediaAccountFilingPage`、`CommissionRulePage`、`ScheduledTaskPage` 已有页面。
- 用户端：`MediaAccountFilingPage`、`PromotionLinkPage`、`PromotionTaskPage` 已有页面。
- 当前没有管理员订单、未归因订单、账单对账、结算明细页面，也没有用户订单/佣金/账单页面。

## 3. 必须新增的业务能力

### 3.1 GoodShort 订单同步

新增订单同步 SPI 和服务，不能只在 `ProviderCapability` 中声明能力：

- `GoodShortOrderProviderAdapter`：订单分页、状态映射、原始字段解析。
- `GoodShortAnalyticsProviderAdapter`：`analyticalReport` 或实际接口的每日转化数据解析。
- `DramaOrderSyncService`：按接入账号、日期窗口、分页检查点和数据库租约执行。
- `DramaOrderSyncScheduler`：短周期拉取当天数据，并按配置重拉最近窗口；管理员可手动补拉。

GoodShort 没有回调时，首期按轮询实现。频率、最近 7 日复核和最近 90 日复核应进入接入账号/任务配置，不写死在 Controller。

### 3.2 原始订单、归因和幂等

新增 `promotion_order`，至少保存：

- `connection_id`、`provider_id`、`external_order_id`，唯一键为 `(connection_id, external_order_id)`；
- 原始状态码、标准状态（`UNPAID/PAID/REFUNDED/UNKNOWN`）、支付时间、退款时间、平台更新时间；
- 原始金额最小单位、币种、格式化金额；
- `custom_params`、解析出的 `tracking_no`、`promotion_link_id`、`user_id`；
- 推广用户、媒体账号、短剧、推广名称的快照字段；
- 原始响应 JSON、同步批次、首次发现时间、最近同步时间和异常摘要。

重复拉取必须更新原订单，不重复插入、不重复计算。未知状态保留原始值并标记异常，不参与佣金计算。

归因链路固定为：

```text
GoodShort customParams -> promotion_link.tracking_no
                         -> promotion_link.user_id
                         -> promotion_media_account / provider_drama
```

找不到链接、trackingNo 或用户时，订单保留在 `UNATTRIBUTED` 队列，管理员可人工处理；禁止按短剧、媒体账号或最近用户猜测归属。

### 3.3 CPS 历史费率快照

当前 `ProviderCommissionRuleServiceImpl` 的直接覆盖模型必须重构。原因是订单可能延迟同步，规则被覆盖后无法证明旧订单使用的价格。

推荐方案：

1. 将规则改为带 `effective_from/effective_to` 的版本记录，或者新增 `provider_commission_rule_history` 保留不可变版本；不再对已生效费率做原地覆盖。
2. 保留当前 `GET/POST/PUT` 入口的业务语义，但 `PUT` 变成结束旧版本并创建新版本，前端同时展示当前规则和历史版本。
3. 订单支付时按 `paid_at` 匹配规则；支付状态尚未确认或无法匹配规则时，订单保存但佣金状态为 `PENDING/ERROR`，禁止回退到最新规则。
4. 订单佣金计算结果保存五项费率、规则 ID、订单原金额、计算金额和错误原因。后续规则修改不影响历史订单。

`ProviderCommissionCalculator` 本身保持无数据库依赖，提取为 `CpsCommissionCalculator` 或由新 Service 包装，公式和 `HALF_UP` 结果必须用对照测试证明不变。

### 3.4 月度账单和结算明细

新增：

- `promotion_settlement_bill`：用户、平台、账单月份、币种、订单数、订单金额、应结佣金、已退款扣回、账单状态、对账状态、管理员备注。
- `promotion_settlement_detail`：账单、订单、短剧、推广链接、指标、规则 ID、五项费率快照、税前佣金/扣回金额和生成时间。

短剧首期账单月份按订单支付时间归属；如果 GoodShort 财务账单采用其他口径，应在规则配置中明确后再改变，不能在代码中写项目名称判断。

本轮只生成“可核对/可结算账单”，不实现钱包、提现、付款接口或银行卡处理。

### 3.5 转化统计

新增 `promotion_daily_metric`，按 `(link_id, metric_date)` 唯一保存 GoodShort 每日数据：点击、归因用户、新注册、新充值、新开会员、充值用户、订单数和充值金额。每日指标用于分析，不直接重新计算佣金；佣金唯一来源是订单和费率快照。

## 4. 哪些代码直接复用

- `ProviderAdapterRegistry`、`ProviderRuntimeConnectionService`、`ProviderConnectionSecret`：继续负责 GoodShort 适配器和凭据边界。
- `GoodShortAdapter` 的签名、HTTP 异常分类和时间解析：提取公共 HTTP 辅助后复用，订单/统计响应映射单独实现。
- `DramaCatalogSyncServiceImpl` 的分页、检查点、租约、幂等 upsert 模式。
- `MediaFilingTaskServiceImpl` 的任务租约、重试和人工失败处理。
- `PromotionLinkServiceImpl` 的 `trackingNo`、`customParams` 和链接快照。
- `ProviderCommissionCalculator` 的五费率公式和金额精度。
- 当前认证、权限、`ApiResponse`、H2 MySQL 模式、`BaseAuthTest` 和 Mapper/Controller 测试模式。

## 5. 哪些必须改造

### 5.1 `PromotionTask` 与 `PromotionLink` 的关系

当前 `PromotionTaskServiceImpl` 按媒体类型插入任务，但没有 `media_account_id`，也没有调用 `PromotionLinkService` 或 `GoodShortAdapter.generatePromotionLink`；因此任务页面里的链接和订单统计不能成为真实数据源。

建议保留两个概念但重接调用链：

- `PromotionTask` 是用户的一次推广活动/任务聚合；
- `PromotionLink` 是具体短剧 + 具体媒体账号的一条可归因链接。

创建任务时必须选择用户已有媒体账号，按账号创建或复用 `PromotionLink`，任务保存 `link_id`/媒体账号快照；订单只归因到 `PromotionLink`，任务页面聚合链接和订单结果。不要复制一套 trackingNo，也不要让 `promotion_task.order_amount` 成为佣金来源。

### 5.2 `GoodShortAdapter` 能力声明

当前 `CAPABILITIES` 已包含 `ORDER_SYNC`、`ANALYTICS_SYNC`，但 `GoodShortAdapter` 只实现 `AccountFilingProviderAdapter`、`DramaCatalogProviderAdapter`、`PromotionLinkProviderAdapter`。必须补 SPI 和实现，或者在未实现前移除能力声明；不能让能力列表误导前端和调度器。

### 5.3 分佣规则管理

当前 README、旧专项设计和 `AGENTS.md` 对“时间版本规则”和“每个平台一条直接覆盖”存在漂移。本轮应先确定历史快照方案，再同步 Controller、DTO、Mapper、前端 `CommissionRulePage`、迁移和测试。推荐版本化；如果业务明确接受“按导入时当前规则”而非支付时间规则，必须在文档中显式降级，不可默默采用。

### 5.4 定时任务

现有 `system_scheduled_task` 和 `ScheduledTaskScheduler` 只覆盖短剧增量目录同步。新增订单/统计任务应使用同一调度框架，但每项任务有独立 `task_code`、检查点、租约和错误状态；不要把订单同步逻辑塞进 `DramaCatalogScheduler`。

## 6. 哪些暂时不要重构

- 不新增 `settlement_project`、CPA/CPM 计算器和 CapCut 账号模型。
- 不把 `short_drama_provider` 改名为通用项目表；它继续表示 GoodShort 等外部平台。
- 不重写 JWT、Redis 会话、用户编号和管理员权限。
- 不推翻 `provider_drama` 目录同步；本地推广元数据保持独立字段，不由远端 upsert 覆盖。
- 不建立 TikTok 与其他账号的绑定/发布关系表；短剧本轮只维护现有媒体账号和平台报白。
- 不把 `promotion_task` 的 0 值统计、订单金额预留字段描述成真实收益。
- 不引入钱包、提现、付款和财务出款状态。

## 7. 推荐后端分层

保持当前接口/实现分离，新增模块建议：

```text
provider/spi
  OrderSyncProviderAdapter
  AnalyticsProviderAdapter

promotion/order
  controller       管理员订单查询、同步和未归因处理
  dto/entity/mapper
  service/impl     同步、幂等、归因

promotion/settlement
  calculator       CpsCommissionCalculator
  entity/mapper
  service/impl     快照、账单、明细、对账查询
```

Controller 不能直接调用 Mapper；订单同步、归因、账单生成是多表写操作，必须定义事务边界。外部 API 调用不能放在数据库长事务中，采用“拉取批次 -> 短事务 upsert -> 计算/聚合”的边界。

## 8. 前端页面范围

### 管理端

保留并完善：

- `DramaCatalogPage`：目录、上下架、推广元数据编辑。
- `MediaAccountFilingPage`：媒体账号和 GoodShort 报白。
- `ProviderManagementPage`：GoodShort 接入和连接测试。
- `CommissionRulePage`：改为当前规则 + 历史版本 + 生效时间/快照说明。
- `ScheduledTaskPage`：增加订单同步、统计同步和账单任务状态。

新增：

- `PromotionOrderPage`：订单筛选、状态、金额、归因用户、短剧、链接和同步时间。
- `UnattributedOrderPage`：未匹配 trackingNo、原始 customParams、人工绑定和审计。
- `PromotionMetricPage`：每日点击/转化统计和最后同步时间。
- `SettlementBillPage`：按月份、用户、平台查询账单。
- `SettlementReconciliationPage`：订单、佣金、退款和账单差异核对。

### 用户端

保留并完善：

- `PromotionLinkPage`：创建任务时选择真实媒体账号，展示实际口令/链接和 trackingNo。
- `PromotionTaskPage`：从 `PromotionLink` 和订单/统计聚合展示，不直接读预留 0 值字段。
- `MediaAccountFilingPage`：继续独立维护媒体账号和报白状态。

新增：

- `PromotionOrderPage`：只查看本人已归因订单和状态。
- `PromotionMetricPage`：查看本人链接的每日数据。
- `MonthlySettlementBillPage`：查看账单月份、订单金额、税前/应结佣金、退款扣回和账单状态。
- `SettlementDetailPage`：查看逐订单佣金和历史费率快照。

## 9. 推荐实施顺序

### 阶段 0：契约收敛

- 确认 GoodShort 订单/统计接口字段和状态映射。
- 决定分佣规则是否版本化；本方案推荐版本化。
- 清理 README、`AGENTS.md`、旧专项设计中关于规则 API 的冲突描述。
- 完成当前短剧元数据工作区改动的验证和提交边界。

### 阶段 1：推广创建链路

- 修正 `PromotionTask` DTO/表，使任务选择真实媒体账号。
- 任务创建调用链接生成，保存 `link_id` 和 trackingNo。
- 用户端页面展示真实链接；失败可重试且不产生重复归因号。

### 阶段 2：订单同步和归因

- 新增 Order/Analytics SPI、GoodShort 映射、订单表、同步检查点和租约。
- 实现订单幂等、状态更新、trackingNo 归因和未归因队列。
- 增加管理员同步/补拉/失败重试 API。

### 阶段 3：佣金快照和账单

- 规则版本化或历史表迁移。
- 订单支付时匹配规则并保存五项费率快照。
- 生成月度账单和逐订单明细，处理退款和无法匹配规则的异常。

### 阶段 4：统计与页面

- 同步每日转化数据，和订单数据分离展示。
- 完成后台订单、未归因、账单、对账页面及用户订单、统计、账单页面。

### 阶段 5：回归和上线准备

- 迁移测试、订单状态矩阵、重复同步、延迟支付、退款、规则修改、未归因和权限测试。
- 完整后端测试、前端测试、Java 25 编译、`git diff --check` 和开发库重建验证。

## 10. 验收标准

- 同一 `(connection_id, external_order_id)` 重复同步不产生重复订单或重复佣金。
- GoodShort `customParams` 能唯一归因到 `promotion_link.tracking_no`；无法归因的订单不会自动分配。
- 订单保存原始状态、金额、时间和响应数据；未知状态可追溯且不参与结算。
- 规则修改后新旧订单的五项费率快照不被改写。
- 退款订单能被标记并按已确认的整单退款规则处理；当前 GoodShort 没有部分退款模型时不自行拆分。
- 账单和明细可从订单、链接、用户和规则快照反向追溯。
- 普通推广用户看不到平台密钥、上游内部字段和其他用户订单。
- 当前目录同步、报白、链接生成和认证回归不受影响。
