# 模块三：媒体账号报白最终设计

## 目标

模块三只保留 GoodShort API 自动报白。媒体账号创建后作为不可变历史记录保存；推广用户不能编辑、删除或主动重试；管理员只能处理技术提交失败的重试，以及删除尚未成功提交或已被 GoodShort 明确拒绝的记录。

## 用户端

用户端媒体账号列表继续只展示：

- 媒体平台
- 账号名称
- 账号 ID
- 账号链接
- GoodShort 报白状态

页面不增加操作列、失败原因列或编辑入口。用户只能新增和查看媒体账号，不能修改、启停、删除或触发 Filing Retry。

展示状态继续从现有 Filing 字段派生，不新增数据库状态字段：

- 从未成功提交且没有错误：待提交
- 从未成功提交且存在错误：提交失败
- 已成功提交且仍等待 GoodShort 结果：审核中
- `remote_status = 1`：已加白
- `remote_status = 2`：已拒绝
- 已成功提交，但连续查询技术异常达到上限：查询失败

## 媒体账号不可变和唯一性

保留 `promotion_media_account(media_type, external_account_id)` 全局唯一约束。

媒体账号创建后不允许用户或管理员修改，也不允许通过启用/禁用改变记录。删除以下能力：

- `PUT /api/user/promotion/media-accounts/{id}`
- `PATCH /api/user/promotion/media-accounts/{id}/status`
- `PUT /api/admin/promotion/media-accounts/{id}`
- 对应 Controller、DTO、Service、Mapper、前端 API、管理端编辑界面和测试

保留 `promotion_media_account.status` 作为历史只读字段和当前管理查询字段，本次不扩大为无关 schema 清理。保留资料版本字段和现有任务版本隔离，不重构正确工作的主链。

## 自动报白主链

主链保持不变：

```text
用户创建媒体账号
-> 创建 Filing
-> 本地事务提交
-> afterCommit
-> submitNow()
-> /creek/open/filing/report
-> 成功后 next_action = QUERY
-> Scheduler / Worker
-> /creek/open/filing/query
-> 0 继续审核
-> 1 已加白并停止
-> 2 已拒绝并停止
```

Scheduler 继续只扫描 `QUERY`，不恢复定时扫描 `SUBMIT`。

## 技术提交失败重试

推广用户的 Retry API 删除：

```text
POST /api/user/promotion/media-accounts/{id}/filings/{providerId}
```

只保留管理员 Retry：

```text
POST /api/admin/promotion/media-accounts/{id}/filings/{providerId}/retry
```

管理员 Retry 只允许同时满足：

- Filing 已存在；
- `/filing/report` 从未成功，即 `last_submitted_at IS NULL`；
- 当前任务已停止，即 `next_action = NONE`；
- 最后错误类型为 `REMOTE_TRANSIENT`，即网络、HTTP 429 或 GoodShort 5xx。

每次临时技术失败后，只要条件仍成立，管理员可以继续重试；一旦 report 成功，或 GoodShort 已进入审核/通过/拒绝状态，就永久禁止该 Filing Retry。Retry 复用原媒体账号和原 Filing，不新建记录、不修改资料。

## 管理员删除

新增管理端接口和详情页删除入口：

```text
DELETE /api/admin/promotion/media-accounts/{id}
```

普通管理员和超级管理员沿用当前媒体账号管理权限。管理端详情页仅在后端规则允许时显示删除操作，并进行二次确认；后端始终重新校验，不能信任前端判断。

允许删除：

- report 从未成功、任务已经停止且存在提交错误的记录；包括临时技术错误和 report 阶段的非临时拒绝；
- GoodShort 查询明确返回 `remote_status = 2` 的已拒绝记录。

禁止删除：

- 待提交或正在提交；
- 已成功 report、仍在审核；
- 已加白；
- 已成功 report，但连续查询技术异常达到上限的“查询失败”。

如果媒体账号存在多个 Filing，只有全部 Filing 都满足可删除条件时才允许删除。删除操作在同一个事务中先删除全部 `provider_media_filing`，再删除 `promotion_media_account`，从而释放全局唯一账号。项目继续保持零物理外键和零数据库级联。

## 取消 MANUAL

产品只保留 GoodShort API 自动报白。删除：

- 管理端 API/MANUAL 配置入口；
- `GET/PUT /api/admin/drama/providers/{providerId}/filing-mode`；
- 管理员人工通过/拒绝 Filing 的接口；
- `FilingMode`、相关 DTO、VO、Mapper 分支、错误码、前端类型及测试；
- `ProviderRuntimeConnectionService` 和媒体账号创建/重试中的 MANUAL 分支。

当前没有历史 MANUAL 报白记录。新增不可变 Flyway 迁移，删除：

- `short_drama_connection.filing_mode`；
- `provider_media_filing.operate_by`。

同步更新开发重建脚本和测试 schema；不修改已执行的 `V1__baseline.sql`。`operate_time` 是 GoodShort 审核时间，继续保留。

## GoodShort Filing 限流

在 `GoodShortAdapter` 边界增加一个小型、单 Java 实例的 Filing 限流器，不使用 Redis、第三方框架或通用限流基础设施。

report 和 query 使用独立 Key：

```text
baseUrl + partnerId + REPORT
baseUrl + partnerId + QUERY
```

同一 Key 的相邻真实 HTTP 请求至少间隔约 650ms，分别低于 GoodShort 的 100 次/分钟上限。现有 HTTP 429 到 `ProviderTransientException` 的映射保持不变。

## QUERY 最大连续技术重试

在 `MediaFilingProperties` 增加 `maxQueryRetries`，默认值为 5。

- `ProviderTransientException` 才累计连续查询技术错误；
- 第 1 至第 4 次失败按现有退避时间重新排队；
- 第 5 次失败写入 `FAILED + NONE`，保留错误信息并停止查询；
- GoodShort 正常返回 `status = 0` 时清零连续错误次数并继续查询；
- GoodShort 明确返回 `1/2` 时按现有终态处理；
- 查询业务拒绝、未知状态、畸形响应或本地不可恢复错误直接进入查询失败，不当作临时异常无限重试。

“查询失败”通过 `status = FAILED`、`last_submitted_at IS NOT NULL`、`remote_status` 不是 `2` 且存在错误信息派生。用户端、管理端标签和管理端筛选保持一致。

## GoodShort 时间转换

所有带 Offset 的时间先转换到 `clock.getZone()`，再保存为 `LocalDateTime`：

```java
offsetDateTime.atZoneSameInstant(clock.getZone()).toLocalDateTime()
```

`parseRemoteTime()` 和 `parseRemoteTimeFlexible()` 的固定 Offset、无毫秒 Offset、`ISO_OFFSET_DATE_TIME` 路径统一使用该规则。epoch millis 继续通过 `Instant` 转换到 `clock.getZone()`；无 Offset 的本地时间保持原有本地语义。

## 验证

聚焦测试覆盖：

- 用户和管理员编辑/状态/人工审核/用户 Retry API 不存在；
- 管理员 Retry 的精确允许和拒绝条件；
- 管理员删除允许条件、拒绝条件、事务删除顺序和唯一值释放；
- MANUAL 代码与 schema 清理；
- report/query 独立主动限流；
- QUERY 第 5 次连续临时异常终止，`status=0` 清零；
- 查询永久错误直接终止；
- 查询失败的两端展示和管理筛选；
- `+0000`、`+0800`、ISO Offset 和 epoch millis 时间转换。

完成后运行后端完整 `mvn verify`、管理端和用户端 `pnpm check`、可用环境下的 MySQL migration contract，以及根目录 `git diff --check`。真实 GoodShort 未执行时不得声明真实平台验收通过。
