# 模块三最终结论

```text
甲方接口覆盖率：2 / 2
甲方接口真实 HTTP 调用率：2 / 2
API 正常主链：完成
完整业务闭环：部分完成
最终验收：暂不通过
```

核心原因不是 GoodShort API 没接，而是：

> **首次正常报白链路已经打通，但“失败/拒绝 → 用户自己修改账号 → 重新报白”没有前端闭环；MANUAL 人工报白模式也没有真正闭环。**

---

# 1. 甲方接口覆盖矩阵

甲方文档当前账号报备只有两个接口：

* `POST /creek/open/filing/report`
* `POST /creek/open/filing/query`

两者限流均为 `100次/min`；查询状态为 `0审核中 / 1已加白 / 2拒绝加白`。

| 甲方接口             | Adapter / Client | Service | 真实 HTTP | 业务消费者              | 最终状态            |
| ---------------- | ---------------- | ------- | ------- | ------------------ | --------------- |
| `/filing/report` | ✅                | ✅       | ✅ POST  | ✅ 创建账号 / 身份修改 / 重试 | ⚠️ 技术链完成，失败闭环缺失 |
| `/filing/query`  | ✅                | ✅       | ✅ POST  | ✅ Scheduler        | ✅ 主链完成          |

GoodShort Adapter 使用：

```text
/open/filing/report
/open/filing/query
```

配合后台配置的 Base URL：

```text
https://api.novelopen.com/creek
```

最终 URL 与甲方一致。请求会真实经过 `RestClient.post()`，`sign` 放 Header，timestamp 使用毫秒时间戳。状态 `0/1/2` 也分别映射为 `PENDING / APPROVED / FAILED`。

所以这一块不能再说：

> “报白接口还没有接完。”

准确说法是：

> **报白两个 HTTP API 已经全部接通。**

---

# 2. 当前真实业务调用链

## API 自动报白正常路径

```text
用户创建媒体账号
    ↓
POST /api/user/promotion/media-accounts
    ↓
MediaAccountServiceImpl.create()
    ↓
promotion_media_account
    ↓
创建 provider_media_filing
status = PENDING
next_action = SUBMIT
    ↓
事务提交
    ↓
TransactionSynchronization.afterCommit()
    ↓
MediaFilingTaskService.submitNow()
    ↓
GoodShortAdapter.submitAccountFiling()
    ↓
POST /open/filing/report
    ↓
提交成功
    ↓
completeSubmit()
    ↓
next_action = QUERY
    ↓
MediaFilingScheduler
默认每 30 秒扫描
    ↓
findDueIds()
只查询 QUERY
    ↓
GoodShortAdapter.queryAccountFiling()
    ↓
POST /open/filing/query
    ↓
0 → PENDING → 继续 QUERY
1 → APPROVED → NONE
2 → FAILED → NONE
    ↓
用户端 / 管理端展示状态
```

用户 Controller 确实存在创建、修改以及显式重新提交接口，不是只有 Service。

而 Mapper 的定时扫描现在明确：

```text
WHERE next_action = 'QUERY'
```

所以以前那种：

```text
SUBMIT
→ 等 Worker
→ 再去 report
```

已经不存在。

**Worker 现在真的只负责 QUERY。**

这是正确的。

---

# 3. 已确认正确的部分

## 3.1 用户提交后是真正“立即报白”

当前不是把任务扔数据库以后等待 Scheduler。

创建 API 模式的报白任务后：

```text
事务提交
→ afterCommit
→ submitNow()
→ /filing/report
```

也就是说数据库成功落库以后马上向甲方提交。

这符合之前确定的业务要求。

---

## 3.2 afterCommit 用法正确

远端 API 没有被硬塞进本地数据库事务里。

当前是：

```text
本地事务成功
        ↓
afterCommit
        ↓
调用甲方
```

不会出现甲方已经收到报白、但本地事务最后回滚这种明显的不一致。

---

## 3.3 修改“平台 / 账号 ID”才触发重新报白

当前：

```text
mediaType 改变
或
externalAccountId 改变
        ↓
identityChanged = true
        ↓
reschedule
        ↓
afterCommit
        ↓
重新 /filing/report
```

而：

```text
账号名称改变
主页链接改变
```

只更新资料，**不会重新报白**。

这个判断符合我们之前确定的规则。

---

## 3.4 重报白会清理上一轮状态

`reschedule` 当前确实会清：

```text
submitted_data_version
remote_status
external_filing_id
filing_time
operate_time
operate_by
last_submitted_at
last_queried_at
last_error_code
last_error_message
```

以及租约、重试等任务字段。

所以不存在上一轮：

```text
已拒绝
↓
重新提交
↓
数据库还挂着旧 remote_status / error
```

这种污染。

这一块设计正确。

---

## 3.5 甲方状态映射正确

甲方定义：

```text
0 = 审核中
1 = 已加白
2 = 拒绝加白
```

当前实现：

```text
0 → PENDING
1 → APPROVED
2 → FAILED
```

并且：

```text
PENDING
→ 继续 QUERY

APPROVED / FAILED
→ next_action = NONE
```

这一层没有发现状态反转或错误映射。

---

# 4. 当前问题

# P0

## P0-1 用户失败后的自助重报链路没有完成

这是目前模块三最主要的验收阻塞项。

### 后端其实已经支持

后端已经有：

```text
PUT /api/user/promotion/media-accounts/{id}
```

用户可以修改账号。

还有：

```text
POST /api/user/promotion/media-accounts/{id}/filings/{providerId}
```

可以执行提交 / 重试。

Service 也已经支持：

```text
修改平台
或
修改账号 ID
    ↓
reschedule
    ↓
afterCommit
    ↓
重新 report
```

### 但是用户端没接

当前用户媒体账号页面实际只有：

```text
新增账号
查看账号列表
查看报白状态
```

没有形成：

```text
编辑
重新提交
```

的用户操作闭环。

也就是说实际用户遇到：

```text
账号 ID 填错
        ↓
甲方拒绝
        ↓
页面显示“已拒绝”
        ↓
用户想修改账号 ID
        ↓
❌ 页面没有编辑入口
```

或者：

```text
/report 因网络 / 429 / 技术错误失败
        ↓
页面显示提交失败
        ↓
❌ 用户没有重新提交入口
```

因此现在真实链路是：

```text
报白失败 / 被拒绝
        ↓
用户只能看见
        X
用户无法自己修正后重新报白
```

而不是要求的：

```text
失败
↓
修改错误资料
↓
重新提交
↓
重新审核
```

### 影响

这是用户侧核心业务闭环，不是后台维护便利性问题。

所以这一项定：

**P0 / 阻塞模块验收。**

---

# P1

## P1-1 MANUAL 人工报白只是“后端有概念”，管理端没有业务闭环

平台配置页面确实允许：

```text
API 自动报备
MANUAL 人工报备
```

而且 MANUAL 创建任务时：

```text
status = PENDING
next_action = NONE
```

不会请求甲方。

这本身正确。

问题是管理端媒体账号报白页面实际只有：

```text
查看
编辑账号
API 提交失败后的重新提交
```

没有真正提供：

```text
人工通过
人工拒绝
```

的操作。

后端虽然存在：

```text
PATCH /api/admin/promotion/media-accounts/{id}/filings/{providerId}/status
```

但当前管理端页面没有把它接起来。

结果就是：

```text
管理员选择 MANUAL
        ↓
用户提交账号
        ↓
PENDING + NONE
        ↓
不会请求甲方
        ↓
管理页面又没有人工审核操作
        ↓
一直 PENDING
```

因此：

**MANUAL 目前属于 Fake Integration｜假闭环。**

如果系统允许管理员选择 MANUAL，那么它就应该真的能用。

**优先级：P1。**

---

## P1-2 人工状态接口没有限制只能修改 MANUAL 报白

这比“前端没按钮”更重要。

当前管理员后端存在：

```text
PATCH .../filings/{providerId}/status
```

可以人工把 Filing 改成：

```text
APPROVED
FAILED
```

但是 `MediaAccountAdminServiceImpl.updateFilingStatus()` 没有看到：

```text
filingMode == MANUAL
```

的校验。

Mapper 的 `updateManualStatus` 也只限制：

```text
id = ?
AND status = 'PENDING'
```

没有限制当前 Connection 必须为 MANUAL。

于是理论上：

```text
GoodShort API 正在审核
status = PENDING
filingMode = API
        ↓
管理员直接调用后端接口
        ↓
人工改成 APPROVED
```

数据库就会认为：

```text
已加白
```

但甲方可能根本没有通过。

### 影响

这会破坏：

> **API 模式状态必须以 GoodShort 返回结果为准**

这个数据真实性边界。

前端现在虽然没暴露该按钮，但后端 API 本身就不应该允许这种状态。

**优先级：P1。**

---

## P1-3 MANUAL → API 后，已有人工待处理任务不会恢复执行

这一条是本轮重新审查额外发现的。

当前配置切换：

### API → MANUAL

已经有处理：

```text
API
↓
切 MANUAL
↓
stopPendingTasksByConnectionId()
```

即把正在等待 API 查询的任务停止。

这部分合理。

但是反方向：

```text
MANUAL
↓
切 API
```

当前只修改：

```text
filing_mode = API
```

没有把已经存在的：

```text
PENDING
next_action = NONE
```

重新变成：

```text
PENDING
next_action = SUBMIT
```

也没有立即 report。

所以会出现：

```text
人工模式期间有 100 个账号待处理
        ↓
管理员决定改回 API 自动报白
        ↓
配置成功显示 API
        ↓
这 100 个旧任务仍然 NONE
        ↓
Scheduler 永远不会处理
```

而 Scheduler 又只扫 `QUERY`。

因此这些存量报白会卡住。

**优先级：P1。**

---

## P1-4 Filing Report 没有主动保证甲方 100 次/min 限流

甲方明确：

```text
/report：100 次/min
/query：100 次/min
```

当前 `/filing/report` 的调用方式是：

```text
每创建一个 API 账号
→ afterCommit
→ 立即调用一次
```

以及：

```text
身份修改
→ 立即调用

管理员重试
→ 立即调用
```

在这条真实调用链里没有主动的 100/min 请求频率控制。

GoodShort Adapter 的处理只是：

```text
甲方返回 429
→ ProviderTransientException
```

但 SUBMIT 失败以后，当前设计又不会让 Scheduler 自动重做 report，而是等待新的用户动作。

因此一次突发超过限制可能变成：

```text
大量创建账号
↓
超过100/min
↓
甲方429
↓
本地提交失败
↓
next_action 不再自动 SUBMIT
↓
而当前用户端又没有重试按钮
```

所以这不是单纯“已经捕获 429 就没事”。

### QUERY 相对好一些

默认配置大致是：

```text
30 秒 / 批
batchSize = 50
```

单实例理论上约：

```text
50 × 2 = 100/min
```

本身已经贴着甲方上限。

单实例由于请求执行需要时间，实际通常不会严格达到 100，但没有明显余量；多实例则没有全局 100/min 保证。

这里不建议为了它建设复杂通用 RateLimiter Framework。

但 **GoodShort Filing 的真实甲方限流约束必须有实际保证。**

**优先级：P1。**

---

## P1-5 QUERY 技术错误可以无限重试，没有终止条件

QUERY 出现临时错误后会：

```text
1m
→ 5m
→ 15m
→ 30m
→ 60m
```

之后继续使用最后一个 `60m` 重试间隔。

但 Filing 配置里没有类似：

```text
maxRetries
```

的最终终止条件。

因此如果某条记录因为永久性异常一直失败：

```text
QUERY
↓
失败
↓
60分钟
↓
QUERY
↓
失败
↓
60分钟
↓
……
```

可以长期持续下去。

### 影响

* 永久保持 `PENDING`；
* 用户不知道到底是审核中还是系统坏了；
* 持续消耗 GoodShort 查询额度；
* 错误数据永远不会自动进入明确终态。

这不是要求做复杂补偿系统，但至少应该存在明确的失败终止语义。

**优先级：P1。**

---

# P2

## P2-1 GoodShort 返回的带时区时间被直接丢掉 Offset

甲方示例时间是：

```text
2025-08-28T11:26:18.000+0000
```

即返回值自带 `+0000` Offset。

当前解析逻辑相当于：

```java
OffsetDateTime.parse(...)
    .toLocalDateTime()
```

这会直接把：

```text
+00:00 的 11:26
```

变成没有时区概念的：

```text
11:26
```

而不是转换为上海时区的：

```text
19:26
```

数据库连接又统一使用 `+08:00`。

所以：

```text
filing_time
operate_time
```

存在最多 8 小时显示偏差。

它不会导致审核状态判断错误，所以不定 P0/P1。

**优先级：P2。**

---

## P2-2 本地把甲方可选字段强制成了必填

甲方明确：

```text
accountId    必填
accountName  可选
accountLink  可选
```

GoodShort Adapter 其实也是按照这个标准实现的：

```text
accountName 有值才发送
accountLink 有值才发送
```

说明第三方适配层是正确的。

但是本地用户 DTO / 前端又要求：

```text
accountName 必填
accountLink 必填
accountLink 必须 HTTPS
```

结果变成：

```text
甲方：
平台 + accountId
即可合法报白

当前系统：
平台 + accountId + name + HTTPS link
才允许
```

如果这是卡司明确的产品资料采集要求，那可以保留；

如果没有这个业务要求，它就是对甲方契约无依据地收紧。

因此这里定 **P2，需要业务确认后决定是否改，不作为当前最大验收阻塞项。**

---

# 5. Fake Integration / Dead Flow

当前确认有以下几类。

### Fake Integration 1：MANUAL 报白

```text
配置项有 MANUAL
数据库支持 MANUAL
后端状态修改接口也有
```

但：

```text
管理端没有人工通过 / 拒绝入口
```

所以产品层没有闭环。

---

### Fake Integration 2：用户重新提交能力

后端已经存在：

```text
PUT 更新账号
POST submitOrRetry
```

但用户媒体账号页面没有接完整。

属于：

```text
后端能力存在
≠
用户业务能力完成
```

---

### Dead / stranded flow：MANUAL → API 存量任务

切回 API 以后已有：

```text
PENDING + NONE
```

不会恢复 SUBMIT。

配置状态和真实任务状态脱节。

---

### 不是 Dead Flow：SUBMIT Scheduler

这里反而已经清理正确。

当前数据库 Worker：

```text
只扫 QUERY
```

旧的定时 `SUBMIT` 路径已经不再是生产消费者。

这部分不应该再重新加回来。

---

# 6. 数据库专项判断

目前 `promotion_media_account` 和 `provider_media_filing` 职责总体是合理的：

```text
promotion_media_account
= 用户自己的媒体账号资料

provider_media_filing
= 某个媒体账号针对某个 Provider 的报白状态 / 执行状态
```

这种拆分是有必要的，因为：

```text
一个媒体账号
理论上可以面对不同 Provider
拥有不同 Filing 状态
```

没有必要把所有报白状态塞回 `promotion_media_account`。

同时本轮没有发现需要通过物理 Foreign Key 或 Cascade 才能解决的问题，继续保持项目的：

```text
数据库零物理外键
零数据库级联
应用层维护逻辑关联
```

即可。

---

# 7. 最终判定

## 已经可以确认完成的

```text
✅ /filing/report 真实 HTTP 接通
✅ /filing/query 真实 HTTP 接通
✅ pid / timestamp / sign / ACCOUNT / media / accountId 参数正确
✅ 签名算法正确
✅ 创建账号事务提交后立即 report
✅ Scheduler 只负责 query
✅ 0 / 1 / 2 状态映射正确
✅ 平台 / accountId 修改会触发重新报白
✅ accountName / accountLink 修改不会误触发
✅ reschedule 会清理旧远端状态
✅ 用户端和管理端能够展示报白状态
```

## 当前没有完成的

```text
❌ 用户失败后无法自己编辑并重新提交
❌ MANUAL 没有管理端人工审核闭环
❌ manual status 后端接口没有限制 MANUAL
❌ MANUAL → API 不恢复旧待处理任务
⚠️ report/query 没有严格保证 100/min
⚠️ query 永久异常可能无限重试
⚠️ 甲方 Offset 时间转换存在问题
```

---

# 验收结论

**模块三目前不建议验收通过。**

但要明确：

> 它不是“报白接口没做完”。

而是：

> **GoodShort 报白 API 技术主链已经完成，剩余问题集中在异常闭环、MANUAL 模式和少量执行边界。**

其中真正必须先解决的是：

```text
P0
用户：失败 / 被拒绝
→ 编辑账号
→ 修改平台或账号 ID
→ 重新报白
```

这个补齐以后，API 自动报白这条产品链才算完整。

然后处理 P1：

```text
MANUAL 真正闭环
API/MANUAL 状态边界
MANUAL→API 存量任务
限流
QUERY 无限重试
```

完成这些之后，再做模块三最终验收。
