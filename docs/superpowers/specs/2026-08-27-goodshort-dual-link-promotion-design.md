# GoodShort 双链接推广生成设计

日期：2026-08-27

状态：用户已批准设计，待实施

## 1. 目标

用户在一次操作中选择一部短剧和一个或多个媒体平台，系统为每个平台生成两种 GoodShort 链接：落地页和 OneLink。推广链接不绑定具体媒体账号，也不依赖账号报白状态。

首期媒体平台固定为 `TIKTOK`、`YOUTUBE`、`FACEBOOK`、`INSTAGRAM`。

## 2. GoodShort 约束

生成接口为 `POST /open/inviteCode/generate/partner/code`。

必填参数为 `pid`、`bookId`、`customParams`、`timestamp` 和 `sign`；`shareUrlType` 与 `codeMedia` 为可选参数。

`shareUrlType=1` 表示落地页，`shareUrlType=2` 表示 OneLink。一次响应只返回一个 `code`、一个 `customParams` 和一个 `shareUrl`。

GoodShort 使用 `pid + bookId + customParams + codeMedia` 作为唯一标识。同一组参数再次申请另一种 `shareUrlType` 会被拒绝；真实测试返回 `status=20005` 和 `code already generated, can not generate again`。

因此同一平台的落地页和 OneLink必须使用不同的 `customParams`，不能复用同一个归因参数。

## 3. 用户流程

1. 用户选择短剧。
2. 用户多选媒体平台。
3. 用户选择链接类型策略。首期固定为“同时生成落地页和 OneLink”，不再让用户逐条申请。
4. 用户点击一次“创建推广”。
5. 后端按所选平台生成两条链接。
6. 页面按平台和链接类型展示口令、链接和生成状态。

一次选择 N 个平台会生成 2N 条链接。平台之间相互独立，某个平台失败不回滚其他已成功链接。

## 4. 数据模型

推广链接继续使用 `promotion_link` 作为真实记录，不使用当前只保存 `PENDING` 和零值统计的 `promotion_task` 主链路。

`promotion_link` 需要移除具体媒体账号关联，并增加以下语义：

- `media_type`：GoodShort 的 `codeMedia`，取四个平台编码。
- `link_variant`：`LANDING` 或 `ONELINK`。
- `batch_no`：一次用户操作生成的批次标识，用于把同一平台的两条链接归组。
- `request_key`：用户请求幂等键。
- `tracking_no`：本条链接唯一追踪号，同时作为 GoodShort `customParams`。

同一批次的每个平台、每种链接类型各保存一条记录。建议唯一约束为：

```text
(user_id, request_key, media_type, link_variant)
```

`tracking_no` 仍保持全局唯一。历史数据迁移必须先定义旧 `media_account_id` 的处理方式，不能直接删除已存在的生产记录；开发阶段数据库允许重建。

## 5. 后端 API

保留 `/api/user/promotion/links` 作为用户端入口，但将创建请求改为批量语义：

```json
{
  "providerId": 1,
  "dramaId": 23,
  "mediaTypes": ["TIKTOK", "YOUTUBE"],
  "requestKey": "0f1d8f7f-9c3b-4f58-bd11-1c3d9a7b4e21",
  "campaignName": "夏季投放"
}
```

后端固定为每个平台生成 `LANDING` 和 `ONELINK` 两个变体，为每个平台和变体生成独立追踪号，并调用 GoodShort 一次。响应返回批次号及每条链接的状态、口令、分享链接、追踪号和错误摘要。

创建前只校验：用户有效、短剧属于目标平台、短剧本地已上架、甲方状态有效、平台连接可用、平台存在分佣规则以及适配器支持推广链接。不得校验或要求 `mediaAccountId`、账号启用状态或账号报白状态。

## 6. 调用与幂等

GoodShort HTTP 调用必须在数据库事务外执行。每条链接独立经历：

```text
PENDING -> SUCCESS
PENDING -> FAILED
```

本地先为所有目标变体创建 `PENDING` 记录，再逐条调用外部接口，随后用独立短事务写入结果。已经 `SUCCESS` 的变体重试时直接返回，不重复调用；`FAILED` 或未完成变体只允许重试自身。

由于 GoodShort 对相同唯一参数有 2 秒限制，服务端不得对同一 `customParams + codeMedia` 重复申请。两个变体必须生成不同 `customParams`。调用顺序采用串行，遇到限流或远程失败只标记当前变体失败。

订单归因按每条记录自己的 `customParams/trackingNo` 完成；同一批次的两条链接可以在页面上聚合展示，但不能在数据库中复用同一个追踪号。

## 7. 前端展示

创建抽屉只展示：短剧信息、媒体平台多选、生成结果和复制操作。不展示具体媒体账号、账号 ID 或报白状态。

列表推荐字段：

```text
创建时间 | 推广名称 | 推广平台 | 短剧名称 | 推广口令 | 落地页 | OneLink | 状态 | 操作
```

“锚点链接”“会员搭售链接”等截图字段不是 GoodShort v1.0.5 返回字段，不加入本功能。

## 8. 错误处理

- 单条变体失败：保留同批次其他成功结果，显示该变体失败原因。
- 远程返回 `20005`：标记当前变体失败，不覆盖已有成功记录。
- 平台连接、短剧或分佣规则不满足：创建前整体拒绝，不调用 GoodShort。
- 请求重复提交：按 `(user_id, request_key, media_type, link_variant)` 返回已有结果。

## 9. 验收

后端至少覆盖：

- 一个媒体平台生成落地页和 OneLink 两条记录。
- 多个平台生成 2N 条记录。
- 两个变体使用不同 `customParams`。
- 同一请求重试不重复调用成功变体。
- 单条远程失败不影响其他变体。
- 不需要媒体账号或报白记录即可生成。
- 订单可按每条 `trackingNo` 独立归因。

前端至少覆盖：

- 多选四个平台。
- 一次点击展示两种链接结果。
- 成功、部分失败、全部失败和重试状态。
- 复制口令和链接。
- 页面不出现具体媒体账号选择和 TikTok 锚点。

## 10. 范围边界

本设计只解决 GoodShort 推广口令、落地页和 OneLink 的批量生成与展示。免费内容播放、单集下载、批量下载、真实转化分析和会员搭售链接不在本阶段实现。
