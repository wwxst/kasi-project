# 推广链接生成设计

日期：2026-08-22

状态：待用户确认

## 1. 目标

为推广用户提供基于已上架短剧和本人已加白媒体账号的推广链接生成能力。一次生成对应一条独立推广记录，同一用户、媒体账号和短剧可以生成多条链接，用于区分不同视频或投放活动。

本阶段只实现推广链接生成、本人链接查询和推广用户端页面；不实现免费内容播放、素材下载、订单同步、预计佣金、导出、每日转化统计和钱包结算。

## 2. 前置条件

生成请求必须同时满足：

- 当前推广用户账号为启用状态。
- 选择的媒体账号属于当前用户，且媒体账号为启用状态。
- 媒体账号在目标平台当前启用的接入账号下存在 `APPROVED` 报备记录。
- 目标短剧属于目标平台接入账号，远端状态有效且本地状态为 `PUBLISHED`。
- 目标平台存在当前默认分佣规则。
- 平台适配器声明支持 `PROMOTION_LINK` 或 `PROMOTION_CODE`。

失败时不创建成功链接，不绕过报备、上下架或分佣规则校验。历史已生成链接不因短剧后续下架或媒体账号停用而删除。

## 3. 领域模型

新增 `promotion_link` 表，内部关联全部使用自增 ID：

| 字段 | 作用 |
|---|---|
| `id` | 内部主键 |
| `user_id` | 推广用户 ID |
| `provider_id` | 短剧平台 ID |
| `connection_id` | 实际接入账号 ID |
| `drama_id` | 本地短剧 ID |
| `media_account_id` | 用户媒体账号 ID |
| `request_key` | 用户端幂等键，与用户 ID 组成唯一约束 |
| `tracking_no` | 本地唯一追踪号，长度不超过 64 |
| `campaign_name` | 可选推广名称 |
| `provider_code` | 外部平台编码快照 |
| `external_code` | 平台返回的推广口令或链接编码 |
| `share_url` | 平台返回的分享链接 |
| `custom_params` | 平台原样回传的自定义参数 |
| `status` | `PENDING`、`SUCCESS`、`FAILED` |
| `last_error_code` / `last_error_message` | 最近一次生成失败原因 |
| `created_at` / `updated_at` | 审计时间 |

唯一约束：`tracking_no` 全局唯一，`(user_id, request_key)` 唯一。请求重试必须复用同一个 `requestKey`，从而复用同一条记录和 `trackingNo`，不得重复创建本地链接。

## 4. 平台适配边界

在 `provider.spi` 增加独立的推广链接能力接口，不把第三方 HTTP 调用放入 Controller 或 Service 的业务校验中：

- `PromotionLinkProviderAdapter.generatePromotionLink(connection, request)`。
- 请求包含外部 `bookId`、媒体类型、外部媒体账号 ID、`trackingNo`、落地页类型和推广名称。
- 结果包含平台 `code`、`shareUrl` 和原样 `customParams`。

GoodShort 适配器在自己的实现中完成签名、`codeMedia` 映射、`shareUrlType` 映射和第三方错误转换。`bookId` 使用 `provider_drama.external_drama_id`，`customParams` 使用本地 `trackingNo`。GoodShort 的具体接口路径和请求字段以 KOC 接口文档 v1.0.5 为准，并在适配器测试中固定请求向量。

## 5. 后端 API

推广用户接口统一位于 `/api/user/promotion/links`：

| 方法 | 路径 | 作用 |
|---|---|---|
| `GET` | `/api/user/promotion/links` | 分页查询当前用户自己的链接 |
| `POST` | `/api/user/promotion/links` | 创建或重试一条推广链接 |

创建请求：

```json
{
  "providerId": 1,
  "dramaId": 23,
  "mediaAccountId": 8,
  "requestKey": "0f1d8f7f-9c3b-4f58-bd11-1c3d9a7b4e21",
  "campaignName": "夏季投放",
  "landingType": "DEFAULT"
}
```

`requestKey` 必填且长度 36；`campaignName` 选填，去除首尾空白后不超过 128；`landingType` 首期只支持 `DEFAULT`。响应只返回当前用户页面需要的字段：链接 ID、平台、短剧、媒体账号摘要、推广名称、追踪号、平台口令、分享链接、状态、失败原因和时间，不返回接入账号、PID、密钥或内部用户 ID。

列表只能按当前认证用户过滤，支持页码和页大小，不接受请求体中的 `userId`。`PENDING` 或 `FAILED` 记录使用相同 `requestKey` 再次 POST 时重试；`SUCCESS` 记录直接返回原结果，避免重复调用第三方。

## 6. 前端范围

`kasi-user-web` 在现有账户布局下增加“短剧推广”页面和路由 `/promotion/links`：

- 展示当前用户可推广的短剧列表或短剧选择器。
- 选择本人已启用媒体账号和推广名称后生成链接。
- 展示本人已生成链接列表、状态、追踪号和复制分享链接操作。
- 只展示后端返回的数据；不可选择非本人媒体账号，不显示内部 ID、接入账号或密钥。
- 使用现有 TDesign、React Query、Axios 封装和账户布局风格。

本阶段不新增管理员页面，不在管理后台定时任务页或短剧目录页中加入推广操作。

## 7. 错误与安全

新增错误码只覆盖实际可达状态：

- 目标短剧不存在或不属于目标平台。
- 短剧未上架或远端已下架。
- 媒体账号不存在、不属于当前用户或已停用。
- 媒体账号未在目标平台加白。
- 平台未配置默认分佣规则。
- 平台不支持推广链接能力。
- 第三方生成请求被拒绝、不可用或返回格式错误。

所有链接写入先在事务中创建 `PENDING` 记录并生成不可预测的 `trackingNo`，再调用第三方；成功后更新为 `SUCCESS`，失败后更新为 `FAILED` 并保存可公开的错误摘要。日志不得写入 API KEY、签名前原文或完整第三方响应。

## 8. 测试验收

后端覆盖：迁移约束、用户隔离、所有前置条件、幂等重试、第三方成功/拒绝/超时、结果持久化和权限边界。GoodShort 适配器覆盖签名参数、媒体映射和响应字段。

前端覆盖：页面路由、短剧/媒体账号选择、成功生成、失败提示、列表刷新和复制链接；普通用户只能看到自己的数据。

验收标准：同一个 `requestKey` 重试不会产生第二条记录；未加白、未上架或无分佣规则时不能调用第三方；成功记录保存 `trackingNo`、`code`、`shareUrl` 和 `customParams`；所有测试和完整回归通过。
