# 推广任务双链接创建设计

日期：2026-09-14

## 目标

用户为一个短剧和一个或多个媒体平台创建推广任务时，不再选择链接类型。后端对每个媒体平台固定生成同一口令下的落地页和 OneLink，用户端任务列表以一个口令一行展示两个链接和一份转化指标。

本设计接续现有口令级列表聚合，不改变订单归因、佣金、数据库结构、GoodShort 适配器参数映射或管理端页面。

## 创建契约

`POST /api/user/promotion/links` 请求保留：

- `providerId`
- `dramaId`
- `mediaTypes`
- `requestKey`
- 可选 `campaignName`

请求删除 `linkVariant`。后端对每个 `mediaType` 按固定顺序准备并生成 `LANDING`、`ONELINK` 两条持久化记录；两条记录共用本次 `requestKey`、`batchNo` 和推广名称，各自保留独立 `trackingNo`、状态与链接地址。

相同 `requestKey` 的幂等校验以平台、短剧、媒体平台集合和完整双变体集合为准。重复请求只重试未成功记录，不重新生成已成功记录；任一变体未成功时批次响应 `complete=false`。响应继续使用现有 `PromotionLinkBatchVO.links`，每个媒体平台返回两个 `PromotionLinkVariant`，不新增第二套响应模型。

GoodShort 调用继续经过现有限流链路。`shareUrlType` 仍由 `LANDING/ONELINK` 映射，`pid + bookId + customParams + codeMedia` 的调用间隔、成功码判断和持久化事务边界不变。

## 用户端创建流程

短剧详情中的创建弹窗调整为：

- 标题为“创建推广任务”；
- 保留必填媒体平台和可选推广名称；
- 删除链接类型字段；
- 确认按钮为“生成推广任务”。

前端只发送一次创建请求，不串行编排两个请求。`complete=true` 时关闭弹窗、刷新推广任务缓存、进入推广任务页并提示成功；`complete=false` 或请求失败时停留在弹窗，沿用当前错误提示边界，不跳转、不提示成功。

## 用户端任务列表

列表继续消费现有口令级 `PromotionLink`：一个口令一行，`landingUrl`、`oneLinkUrl` 和七项转化指标不改变数据语义。

展示调整如下：

- 将“落地页”和“OneLink”合并为一个“推广链接”列；
- 单元格固定两行，分别显示“落地页”和“OneLink”，每行提供打开与复制操作；
- 单元格直接显示 URL，过长时以省略号收束，并保留完整地址用于打开、悬停提示和复制；
- 推广名称为空时显示 `—`；
- 历史单变体数据的缺失链接继续显示“暂无”；
- 归因冲突标签和七项指标 `—` 的现有规则保持不变；
- 表格仍允许横向滚动，在桌面端固定创建时间、短剧、媒体平台和口令等识别列；窄屏不允许控件或文本重叠。

不增加卡片视图、详情抽屉、操作列、状态、失败原因、充值金额、重试或修改入口。

## 数据流与失败边界

1. 用户提交媒体平台和推广名称。
2. 后端按媒体平台生成 LANDING、ONELINK 两个准备项。
3. Service 依次调用现有 GoodShort adapter，并分别保存成功或失败状态。
4. 所有准备项成功时返回 `complete=true`；否则返回 `complete=false`。
5. 用户端只在完整成功时进入推广任务列表。
6. 列表查询继续按 `user_id + connection_id + drama_id + external_code` 聚合两个 URL，不在前端合并原始变体记录。

真实平台若未返回完整结果，失败状态继续留在数据库且不进入只展示成功记录的用户列表。本次不增加补偿任务或自动重试框架。

## 测试与验证

后端先增加失败测试，再做最小实现：

- 一个媒体平台产生 LANDING、ONELINK 两个准备项；
- 多媒体产生 `媒体数量 × 2` 个准备项；
- 相同 `requestKey` 重复请求复用已成功变体并只重试失败变体；
- 批次任一变体失败时 `complete=false`；
- HTTP 请求不再需要 `linkVariant`，非法或重复媒体校验保持不变。

用户端先更新失败测试，再做最小实现：

- 创建弹窗不显示链接类型；
- 单次请求不发送 `linkVariant`；
- 完整成功才跳转，失败或不完整批次继续停留；
- 任务列表一个“推广链接”列同时提供两个链接的打开与复制操作；
- 历史缺失链接和归因冲突展示保持正确。

完成前运行后端聚焦测试和 `mvnw.cmd verify`、用户端 `pnpm check`、根目录 `git diff --check`，并在桌面与 320px 以上窄屏完成页面截图检查。没有真实 MySQL 或 GoodShort 授权时相应验证记为 `SKIP`，不记为 `PASS`。

## 文档与非目标

同步更新 `kasi-backend/README.md`、`kasi-backend/AGENTS.md`、`kasi-user-web/README.md`、`kasi-user-web/AGENTS.md` 和当前架构文档中的创建契约。既有方案文档保留为历史设计依据，不回写勾选项。

明确不修改：

- 数据库迁移和开发重建脚本；
- GoodShort adapter 的接口字段映射；
- 订单归因、订单同步、佣金和结算；
- 管理端推广任务页面；
- 当前工作区内与本设计无关的已有改动。
