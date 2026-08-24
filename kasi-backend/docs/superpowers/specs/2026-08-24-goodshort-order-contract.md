# GoodShort 订单同步接口契约

- 日期：2026-08-24
- 状态：已实现并通过聚焦/全量测试
- 来源：`Goodshort-KOC推广接口文档.md` v1.0.5
- 范围：GoodShort KOC 订单列表，不包含订单转化明细

## 1. 请求

- Method：`POST`
- Path：`/open/partner/orders`
- 限流：100 次/分钟
- 推荐调用时间：每天 08:00 后
- 分页上限：500 条/页

请求体：

| 字段 | 类型 | 必填 | 本系统字段 |
| --- | --- | --- | --- |
| `pid` | string | 是 | `ShortDramaConnection.partnerId` |
| `timestamp` | long | 是 | `Clock.millis()` |
| `pageNo` | int | 是 | 从 1 开始 |
| `pageSize` | int | 是 | 首发固定 500 |
| `startDate` | string | 否 | `yyyy-MM-dd HH:mm:ss` |
| `endDate` | string | 否 | `yyyy-MM-dd HH:mm:ss` |

`sign` 不进入请求体，在 HTTP header 传递。签名继续复用 `GoodShortSigner`：非空请求参数按字段名 ASCII 升序拼接，再追加 `&key={apiKey}`，计算大写 MD5。

日期窗口按文档定义为自然日闭区间，例如 `2025-07-01 00:00:00` 到 `2025-07-01 23:59:59`。首发管理端限制一次最多查询 31 个自然日，不创建自动任务或检查点。

## 2. 响应映射

| GoodShort 字段 | 类型 | 本系统字段 | 说明 |
| --- | --- | --- | --- |
| `orderId` | string | `externalOrderId` | 与连接 ID 组成幂等键 |
| `userId` | string | `externalUserId` | 仅追溯，不用于收益归属 |
| `payMoney` | integer | `orderAmountMinor` | 美分，分成前金额 |
| `payTime` | string | `paidAt` | `yyyy-MM-dd HH:mm:ss` |
| `payStatus` | integer | `rawStatus` / `status` | 0 未支付、1 已支付、3 退款 |
| `customParams` | string | `customParams` | 原样返回，匹配 `promotion_link.tracking_no` |
| `bookId` | string | `externalDramaId` | 仅追溯，不猜测用户归属 |
| `searchCode` | string | `searchCode` | GoodShort 口令 |
| `channelCode` | string | `channelCode` | 渠道号 |
| `pid` | string | `partnerId` | KOC 机构 ID |
| `utime` | string | `providerUpdatedAt` | 供应方更新时间，可为空 |

标准状态映射：`0 -> UNPAID`、`1 -> PAID`、`3 -> REFUNDED`，其他值保存原值并映射为 `UNKNOWN`。未知状态不计算佣金。

`payMoney` 以美分保存为 `order_amount_minor`，展示/分佣金额使用 `payMoney / 100`，保留两位小数。接口不返回币种，币种使用当前平台连接的 `currency` 快照。接口不返回退款时间，退款订单只保存 `payStatus=3` 和 `utime`，不得把 `utime` 伪装为退款发生时间。

## 3. 分页终止

响应分页字段为 `pageNo`、`pageSize`、`pages`、`total`、`records`。满足任一条件即结束：

- `records` 为空；
- `pageNo >= pages`；
- 已读取记录数达到 `total`。

同一 `(connection_id, external_order_id)` 重复同步执行更新，不插入第二条订单。归因只允许：

```text
orders.customParams -> promotion_link.tracking_no -> promotion_link.user_id
```

无法匹配时保存原始订单并标记 `UNATTRIBUTED`，不按 `bookId`、`searchCode`、媒体账号或最近用户猜测。

## 4. 首发不使用的接口

`POST /open/promotion/analyticalReport` 是自然日转化汇总，不是订单明细。点击量、导端人数、注册人数等统计不进入首发，也不写入 `promotion_order`。
