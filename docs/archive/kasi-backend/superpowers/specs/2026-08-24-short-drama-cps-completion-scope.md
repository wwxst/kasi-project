# 短剧 CPS 完整化范围与重构边界

日期：2026-08-24
状态：快速上线范围已实现；后置能力继续保留为未来规划
范围：当前 `kasi-backend`、同级 `kasi-admin-web`、同级 `kasi-user-web` 的 GoodShort 短剧推广链路

## 1. 快速上线版结论

本轮不做 CapCut、CPA、CPM、通用项目管理、每日转化统计和完整财务账单工作流。首发只保留最小可用 CPS 闭环：

```text
短剧目录 -> 媒体账号报白 -> 真实推广链接
       -> 管理员手动触发一次 GoodShort 订单同步
       -> trackingNo 归因 -> CPS 费率快照 -> 订单佣金明细
       -> 按月查询/导出
```

首发验收标准是：用户可以生成真实推广链接；管理员可以按日期窗口手动触发一次订单同步；系统能保留原始订单、准确归属推广用户、固定历史费率、计算佣金并按月份查询/导出。首发不生成可编辑/关闭/付款的账单实体，不包含钱包余额、提现、付款通道和财务出款。

### 1.1 本轮明确砍掉的内容

- `PromotionTask` 不进入首发主链路；先用现有 `PromotionLink` 作为真实推广记录。任务页面暂时下线、隐藏或改成链接列表，不能展示 0 值收益。
- GoodShort 订单首发采用管理员手动触发 API 同步；不做自动轮询、分页检查点、订单同步定时任务和每日复核窗口。
- 不做 CSV/Excel/JSON 上传、模板解析、批次预览和错误行下载；只有在 GoodShort API 无法满足上线条件时，才另开人工文件导入切片。
- 不做 `promotion_daily_metric`、点击/注册/充值分析和 `ANALYTICS_SYNC`。
- 不做 `promotion_settlement_bill`、`promotion_settlement_detail`、对账状态流转；月度账单先用订单佣金明细聚合查询和导出实现。
- 分佣规则只增加不可变历史记录和订单快照，不做完整规则时间线页面；当前管理入口保持最小改动。

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

## 3. 快速上线必须新增的业务能力

### 3.1 管理员手动触发订单同步

首发接入 GoodShort 订单 API，但只提供管理员手动触发，不做后台自动调度：

- `GoodShortOrderProviderAdapter`：订单分页、状态映射、原始字段解析。
- `PromotionOrderSyncService`：接收管理员日期窗口，调用一次 GoodShort 订单接口并幂等落库。
- `AdminPromotionOrderController`：手动同步、同步结果、订单查询和未归因查询。

同步请求只需要 `from/to` 日期窗口和平台；响应原始 JSON、第三方订单号、状态、支付时间、退款时间、订单金额、币种、`customParams`、短剧/平台外部 ID 和第三方更新时间全部保存。单页失败记录错误并返回同步失败，首发不做检查点恢复。

自动 API 同步、最近 7 日/90 日复核、分页检查点和定时任务全部列入后续版本。

### 3.2 原始订单、归因、幂等和快照

新增 `promotion_order`，至少保存：

- `connection_id`、`provider_id`、`external_order_id`，唯一键为 `(connection_id, external_order_id)`；
- 原始状态码、标准状态（`UNPAID/PAID/REFUNDED/UNKNOWN`）、支付时间、退款时间、平台更新时间；
- 原始金额最小单位、币种、格式化金额；
- `custom_params`、解析出的 `tracking_no`、`promotion_link_id`、`user_id`；
- 推广用户、媒体账号、短剧、推广名称的快照字段；
- 同步请求窗口、原始响应 JSON、首次同步时间和异常摘要；
- 五项费率快照、规则历史记录 ID、佣金金额、佣金状态和计算错误原因。

重复同步必须更新原订单，不重复插入、不重复计算。未知状态保留原始值并标记异常，不参与佣金计算。

归因链路固定为：

```text
GoodShort customParams -> promotion_link.tracking_no
                         -> promotion_link.user_id
                         -> promotion_media_account / provider_drama
```

找不到链接、trackingNo 或用户时，订单保留在 `UNATTRIBUTED` 队列，管理员可人工处理；禁止按短剧、媒体账号或最近用户猜测归属。

### 3.3 CPS 历史费率快照（最小实现）

当前 `ProviderCommissionRuleServiceImpl` 的直接覆盖模型必须重构。原因是订单可能延迟同步，规则被覆盖后无法证明旧订单使用的价格。

快速上线不做完整规则版本 UI，只做一张不可变历史表 `provider_commission_rule_history`：

1. `POST`/`PUT` 修改当前费率时，先把旧五项费率写入历史表，再更新 `provider_commission_rule`。
2. 订单导入时保存当前历史记录 ID 和五项费率快照；历史订单不重新读取当前规则。
3. 首发按“导入时有效规则”计算，不实现按支付时间自动匹配规则；如果订单延迟跨越费率修改，管理员必须在导入前确认当前规则，异常订单允许人工标记。
4. 后续再升级为 `effective_from/effective_to` 规则版本和自动按支付时间匹配。

`ProviderCommissionCalculator` 本身保持无数据库依赖，提取为 `CpsCommissionCalculator` 或由新 Service 包装，公式和 `HALF_UP` 结果必须用对照测试证明不变。

### 3.4 月度佣金查询（不落账单表）

直接从 `promotion_order` 的已归因佣金明细按订单支付时间聚合月份，提供管理员和用户只读查询/CSV 导出。订单明细已经保存平台、短剧、链接、规则 ID、五项费率、佣金和退款状态，因此首发不新增账单表。

首发不提供账单锁定、开票、付款、对账状态流转、钱包、提现或银行卡处理。月度查询不是财务付款凭证。

### 3.5 转化统计（后置）

`promotion_daily_metric`、`ANALYTICS_SYNC`、点击/注册/充值分析不进入首发。首发只展示订单数、订单金额和佣金汇总；每日转化数据后续单独接入，不能用当前 `promotion_task` 的 0 值字段冒充真实统计。

## 4. 哪些代码直接复用

- `ProviderAdapterRegistry`、`ProviderRuntimeConnectionService`、`ProviderConnectionSecret`：继续负责 GoodShort 适配器和凭据边界。
- `GoodShortAdapter` 的签名、HTTP 异常分类和时间解析：链接和目录现有调用链继续复用；订单同步新增同一适配器能力，不做统计 API。
- `DramaCatalogSyncServiceImpl` 的分页、检查点、租约、幂等 upsert 模式。
- `MediaFilingTaskServiceImpl` 的任务租约、重试和人工失败处理。
- `PromotionLinkServiceImpl` 的 `trackingNo`、`customParams` 和链接快照。
- `ProviderCommissionCalculator` 的五费率公式和金额精度。
- 当前认证、权限、`ApiResponse`、H2 MySQL 模式、`BaseAuthTest` 和 Mapper/Controller 测试模式。

## 5. 哪些必须改造

### 5.1 首发移除 `PromotionTask` 主链路

当前 `PromotionTaskServiceImpl` 按媒体类型插入任务，但没有 `media_account_id`，也没有调用 `PromotionLinkService` 或 `GoodShortAdapter.generatePromotionLink`。为了快速上线，首发不修这条新任务链路，也不把 `promotion_task` 纳入订单和佣金模型。

首发只使用已经可工作的 `PromotionLink`：

- 用户选择本人媒体账号和短剧；
- `PromotionLinkServiceImpl` 生成或重试 GoodShort 口令/链接；
- 订单通过 `promotion_link.tracking_no` 归因；
- 用户端“推广任务”路由暂时隐藏或重命名为“推广链接”，不得展示 `promotion_task.order_amount` 等占位字段。

后续如果确实需要活动聚合，再让 `PromotionTask` 引用 `promotion_link`；不要复制第二套 trackingNo。

### 5.2 `GoodShortAdapter` 能力声明

当前 `CAPABILITIES` 已包含 `ORDER_SYNC`、`ANALYTICS_SYNC`，但 `GoodShortAdapter` 只实现 `AccountFilingProviderAdapter`、`DramaCatalogProviderAdapter`、`PromotionLinkProviderAdapter`。首发应补 `ORDER_SYNC` 的最小 SPI/实现并移除或隐藏未实现的 `ANALYTICS_SYNC`；订单只由管理员手动触发，不能让能力列表误导前端和调度器。

### 5.3 分佣规则管理

当前 README、旧专项设计和 `AGENTS.md` 对“时间版本规则”和“每个平台一条直接覆盖”存在漂移。本轮只增加 `provider_commission_rule_history` 和订单费率快照，按导入时有效规则计算；完整 `effective_from/effective_to` 版本化和按支付时间自动匹配后置。该降级必须在运营口径中明确，不能默默当成精确历史结算。

### 5.4 定时任务

首发不新增订单/统计定时任务。保留现有 `system_scheduled_task` 和 `ScheduledTaskScheduler` 只负责短剧目录增量同步，管理员手动触发订单同步。后续自动同步时再复用调度框架，并为每项任务定义独立 `task_code`、检查点、租约和错误状态；不要把订单逻辑塞进 `DramaCatalogScheduler`。

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
promotion/order
  controller       管理员订单导入、查询和未归因处理
  dto/entity/mapper
  service/impl     手动同步、幂等、归因和佣金快照

promotion/settlement
  calculator       CpsCommissionCalculator
  entity/mapper
  service/impl     月度聚合和明细查询
```

Controller 不能直接调用 Mapper；手动同步、归因和佣金快照是多表写操作，必须定义事务边界。外部 HTTP 拉取与数据库写入分开，采用“调用一次接口 -> 短事务 upsert/计算”的边界。

## 8. 前端页面范围

### 管理端

保留并完善：

- `DramaCatalogPage`：目录、上下架、推广元数据编辑。
- `MediaAccountFilingPage`：媒体账号和 GoodShort 报白。
- `ProviderManagementPage`：GoodShort 接入和连接测试。
- `CommissionRulePage`：保留当前规则编辑，增加“修改会产生历史快照”的提示；不做完整版本时间线。
- `ScheduledTaskPage`：首发不增加订单/统计任务。

新增：

- `PromotionOrderPage`：订单筛选、状态、金额、归因用户、短剧、链接和同步时间；页面提供日期窗口和“手动同步”按钮。
- `MonthlyCommissionPage`：按月份/用户查询订单金额和佣金汇总并导出。
- `UnattributedOrderPage`：首发只展示未匹配 trackingNo 和原始 customParams，提供按日期窗口重新同步，不做复杂人工归属工作流。

### 用户端

保留并完善：

- `PromotionLinkPage`：选择真实媒体账号，展示实际口令/链接和 trackingNo。
- `PromotionTaskPage`：首发隐藏或重定向到 `PromotionLinkPage`，不展示预留 0 值字段。
- `MediaAccountFilingPage`：继续独立维护媒体账号和报白状态。

新增：

- `PromotionOrderPage`：只查看本人已归因订单、佣金和状态。
- `MonthlyCommissionPage`：查看月份、订单金额、佣金汇总和订单明细。
- `PromotionOrderDetailPage`：查看单笔订单原始字段、trackingNo 和五项费率快照。

## 9. 推荐实施顺序

### 阶段 0：首发范围收敛

- 确认 GoodShort 订单接口字段、日期窗口和状态映射。
- 增加最小 `provider_commission_rule_history` 历史快照表，不做完整版本 UI。
- 清理 README、`AGENTS.md`、旧专项设计中关于规则 API 的冲突描述。
- 完成当前短剧元数据工作区改动的验证和提交边界。

### 阶段 1：真实推广链接

- 将 `PromotionLinkPage` 改为选择真实媒体账号并调用已有链接接口。
- 用户端展示口令、直达链接和 trackingNo；失败可重试且不产生重复归因号。
- 隐藏/下线 `PromotionTask` 首发页面和未实现统计字段。

### 阶段 2：手动订单同步和佣金

- 新增订单表和最小订单同步 SPI。
- 实现管理员按日期窗口手动同步、订单幂等、状态更新、trackingNo 归因、未归因列表和五项费率快照。
- 增加管理员同步、查询和 CSV/Excel 导出 API；文件导入作为后续备用切片。

### 阶段 3：月度聚合查询

- 按订单支付时间聚合月份佣金。
- 用户和管理员查询订单明细、退款状态和佣金汇总。
- 暂不落账单表、账单状态或财务对账流。

### 阶段 4：后续能力

- GoodShort 自动订单同步、每日转化数据、完整账单/对账、任务聚合、规则时间版本 UI，以及必要时的人工文件导入。

### 阶段 5：回归和上线准备

- 迁移测试、订单状态矩阵、重复同步、延迟支付、退款、规则修改、未归因和权限测试。
- 完整后端测试、前端测试、Java 25 编译、`git diff --check` 和开发库重建验证。

## 10. 验收标准

- 同一 `(connection_id, external_order_id)` 重复同步不产生重复订单或重复佣金。
- GoodShort `customParams` 能唯一归因到 `promotion_link.tracking_no`；无法归因的订单不会自动分配。
- 订单保存原始状态、金额、时间和响应数据；未知状态可追溯且不参与结算。
- 规则修改后新旧订单的五项费率快照不被改写。
- 退款订单能被标记并按已确认的整单退款规则处理；当前 GoodShort 没有部分退款模型时不自行拆分。
- 月度汇总和明细可从订单、链接、用户和规则快照反向追溯；首发不承诺账单付款状态。
- 普通推广用户看不到平台密钥、上游内部字段和其他用户订单。
- 当前目录同步、报白、链接生成和认证回归不受影响。
