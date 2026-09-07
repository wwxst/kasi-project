# 模块四最终结论

```text
甲方接口覆盖率：1 / 1
甲方接口真实 HTTP 调用率：1 / 1

正常生成主链：完成
用户级归因：完成
推广任务级归因：部分完成
异常恢复闭环：未完成

模块四完整业务闭环：部分完成
最终验收：暂不通过
```

---

# 1. 甲方接口覆盖

本模块对应 GoodShort：

```text
POST /open/inviteCode/generate/partner/code
```

甲方完整地址：

```text
https://api.novelopen.com/creek/open/inviteCode/generate/partner/code
```

甲方要求：

```text
pid
bookId
customParams
shareUrlType
codeMedia
timestamp
sign
```

其中甲方明确规定：

```text
pid + bookId + customParams + codeMedia
```

作为唯一标识，同一组合 **2 秒限制 1 次**。

返回：

```text
code
customParams
shareUrl
```

并明确建议机构保存 `code + shareUrl` 映射。

当前 `GoodShortAdapter.generatePromotionLink()` 实际发送：

```text
pid          ← connection.partnerId
bookId       ← provider_drama.external_drama_id
customParams ← promotion_user.user_no
shareUrlType ← LANDING=1 / ONELINK=2
codeMedia    ← TIKTOK / FACEBOOK / YOUTUBE / INSTAGRAM
timestamp    ← clock.millis()
sign         ← HTTP Header
```

并真实执行：

```text
POST /open/inviteCode/generate/partner/code
```

返回的：

```text
code
shareUrl
```

也真实进入本地持久化。

### 签名

当前实现：

```text
过滤空参数
→ 参数名排序
→ key=value&...
→ &key=密钥
→ MD5
→ 大写
```

与甲方文档一致。

所以：

> **甲方生成口令接口本身没有发现“假接入”。**

---

# 2. 当前真实调用链

当前成功链路是：

```text
用户短剧列表
    ↓
点击“创建推广任务”
    ↓
选择媒体平台
TIKTOK / YOUTUBE / FACEBOOK / INSTAGRAM
    ↓
选择
LANDING / ONELINK
    ↓
填写推广名称
    ↓
前端生成 UUID requestKey
    ↓
POST /api/user/promotion/links
    ↓
UserPromotionLinkController
    ↓
PromotionLinkServiceImpl.createOrRetry()
    ↓
PromotionLinkPersistenceServiceImpl.prepareBatchPending()
    ↓
检查用户正常
检查短剧 PUBLISHED
检查 GoodShort showStatus = 1
检查短剧属于当前 Connection
    ↓
promotion_link
status = PENDING
    ↓
GoodShortAdapter.generatePromotionLink()
    ↓
真实 POST
/open/inviteCode/generate/partner/code
    ↓
甲方返回
code + shareUrl
    ↓
promotion_link
status = SUCCESS
external_code = code
share_url = shareUrl
    ↓
跳转
/workspace/promotion-links
    ↓
用户查看 / 复制口令
用户查看 / 复制分享链接
```

Controller 是真实用户接口，不是内部无消费者 Service。

创建前也会验证用户、短剧状态和 Provider Connection，不会拿任意本地 `dramaId` 直接请求甲方。

---

# 3. 已确认正确

## 3.1 用户真的可以创建推广任务

用户短剧页面存在真实的：

```text
创建推广任务
```

提交时调用 `createPromotionLinks()`。

参数真实包含：

```text
providerId
dramaId
mediaTypes
linkVariant
campaignName
```

不是一个没有后端消费者的 UI。

---

## 3.2 每次正常 UI 创建都会产生 requestKey

前端：

```ts
requestKey: input.requestKey ?? crypto.randomUUID()
```

所以普通新建操作有独立 UUID。

---

## 3.3 远程调用没有包在长数据库事务里面

当前逻辑是：

```text
REQUIRES_NEW
创建 / 重置 PENDING
        ↓
事务结束

调用 GoodShort

        ↓

REQUIRES_NEW
SUCCESS / FAILED
```

这一点是合理的。

不会让第三方网络请求长时间占着本地数据库事务。

---

## 3.4 code 和 shareUrl 都真实保存

数据库 `promotion_link` 有：

```text
external_code
share_url
```

成功以后 Mapper：

```text
status = SUCCESS
external_code = ?
share_url = ?
```

真实写库。

所以不是：

```text
生成以后只返回前端
刷新页面就没了
```

---

## 3.5 用户能够再次查看历史任务

当前：

```text
GET /api/user/promotion/links
```

按当前用户查询自己的任务。

推广任务页真实展示：

```text
创建时间
推广名称
短剧
媒体平台
链接类型
口令
分享链接
```

并支持复制口令、复制链接以及打开分享链接。

这一段已经形成：

```text
创建
↓
持久化
↓
刷新页面
↓
还能看到
```

的完整成功闭环。

---

# 4. customParams 归因专项结论

这部分需要分两个层次说。

## 用户级归因：正确

当前生成请求：

```java
new PromotionLinkRequest(
    drama.getExternalDramaId(),
    user.getUserNo(),
    mediaType,
    linkVariant
)
```

也就是说：

```text
customParams = promotion_user.user_no
```

而数据库：

```text
promotion_user.user_no
```

存在：

```text
UNIQUE KEY uk_user_no
```

GoodShort 后续订单又会把 `customParams` 原样返回。

订单服务当前真实执行：

```text
order.customParams
↓
PromotionUserMapper.findByUserNo()
↓
promotion_order.user_id
↓
ATTRIBUTED
```

所以：

> **订单属于哪个卡司用户，现在可以准确归因。**

这部分是完成的。

---

# 5. P0

## P0-1 甲方生成失败时，用户端仍然提示“推广链接和口令已生成”

这是模块四当前最严重的问题。

后端遇到：

```text
网络错误
429
5xx
甲方拒绝
```

不会让整个 Controller 请求报错。

而是：

```text
catch ProviderTransientException
    ↓
promotion_link = FAILED

catch ProviderRemoteRejectedException
    ↓
promotion_link = FAILED

然后仍然返回：
ApiResponse.success(...)
```

同时 Batch 里只是：

```text
complete = false
```

但前端 `createPromotionLinks()` 只判断：

```text
ApiResponse.code == 0
data != null
```

并不会检查：

```text
batch.complete
```

随后页面无条件执行：

```text
跳转推广任务页
+
“推广链接和口令已生成”
```

所以真实情况可以变成：

```text
GoodShort 429
        ↓
数据库 FAILED
        ↓
后端 HTTP 仍正常返回
        ↓
前端：
“推广链接和口令已生成”
        ↓
用户进入推广任务页
        ↓
口令：暂无
分享链接：暂无
```

这是明显错误的产品状态。

### 影响

用户会认为任务已经成功生成。

实际上：

```text
根本没有可使用的推广链接
```

因此定：

**P0。**

---

## P0-2 FAILED 任务用户既看不到失败状态，也不能重试

数据库和后端 VO 已经存在：

```text
status
lastErrorCode
lastErrorMessage
```

后端会真实保存：

```text
PENDING
SUCCESS
FAILED
```

前端 TypeScript 类型甚至也定义了：

```text
status
lastErrorCode
lastErrorMessage
```

但推广任务页实际只展示：

```text
时间
名称
短剧
媒体
链接类型
口令
链接
```

完全没有：

```text
状态
失败原因
重新生成 / 重试
```

因此失败以后真实路径：

```text
创建
↓
甲方失败
↓
promotion_link = FAILED
↓
用户进入任务页
↓
只看见：
口令“暂无”
链接“暂无”
↓
不知道为什么失败
↓
也没有重试入口
```

后端类虽然叫：

```text
createOrRetry
```

但用户 UI 并没有消费真正的 Retry 能力。

这一项和 P0-1 合起来意味着：

> **成功链完整，失败链完全没有产品闭环。**

因此同样属于验收阻塞。

---

# 6. P1

## P1-1 本地幂等键和甲方唯一键不是一套东西

甲方明确：

```text
pid
+
bookId
+
customParams
+
codeMedia
```

是一条唯一口令的标识，并且这个组合受 **2 秒 1 次**限制。

但当前本地唯一键是：

```text
user_id
+
request_key
+
media_type
+
link_variant
```

数据库：

```text
uk_promotion_link_variant
(user_id, request_key, media_type, link_variant)
```

注意其中：

```text
requestKey
linkVariant
```

甲方唯一键里没有。

而：

```text
bookId
```

反而没有进入本地幂等唯一键。

这会产生语义错位。

---

## 例子

用户第一次：

```text
用户A
短剧100
TikTok
LANDING
requestKey = UUID-1
```

调用甲方：

```text
pid + book100 + userA + TIKTOK
```

第二次用户再次点创建：

```text
用户A
短剧100
TikTok
LANDING
requestKey = UUID-2
```

因为前端默认重新生成 UUID，所以本地认为：

```text
全新的任务
```

然后再次调用甲方：

```text
pid + book100 + userA + TIKTOK
```

但从甲方看：

> **这是同一唯一组合。**

---

# P1-2 没有真正保证甲方“同一组合 2 秒 1 次”

当前没看到针对：

```text
pid + bookId + customParams + codeMedia
```

做：

```text
最近生成时间检查
或
2 秒限流
或
远端唯一组合复用
```

当前只根据 `requestKey` 判断是否已经 SUCCESS。

因此：

```text
第一次创建
↓
成功

1 秒后再次点击创建
↓
新 requestKey
↓
本地允许
↓
再次调用同一个 GoodShort 唯一组合
```

会直接撞甲方限制。

GoodShort Adapter 对 429 的处理只是：

```text
429
→ ProviderTransientException
```

然后又会进入刚才的：

```text
FAILED
+
用户页面却提示成功
```

问题。

这不是要求建设一个通用复杂 RateLimiter Framework。

但：

> **甲方明确写死的 2 秒限制，业务代码至少必须真实满足。**

所以定 **P1**。

---

# P1-3 requestKey 重试语义存在数据错配风险

这个是比较隐蔽但真实的后端问题。

当前查已有任务只根据：

```text
userId
requestKey
mediaType
linkVariant
```

没有同时验证：

```text
providerId
dramaId
```

假设第一次：

```text
requestKey = ABC
dramaId = 100
```

任务 FAILED。

然后客户端错误地使用相同：

```text
requestKey = ABC
```

但提交：

```text
dramaId = 200
```

当前代码会找到旧记录。

随后：

```text
resetPending()
```

只更新：

```text
status
tracking_no
error
updated_at
```

不会更新：

```text
drama_id
provider_id
connection_id
campaign_name
```

但是新发给 GoodShort 的请求，却使用的是：

```text
drama 200
```

于是可能形成：

```text
数据库记录：
drama_id = 100

实际生成的 GoodShort 链接：
bookId = drama 200
```

这属于：

> **本地任务和真实推广链接对应短剧不一致。**

正常网页每次自动生成 UUID，所以普通用户现在不容易触发。

但既然后端明确支持：

```text
createOrRetry
```

那么 Retry 的业务语义必须保证原始请求内容一致。

定：

**P1。**

---

# 7. 推广任务级归因问题

这个需要和“用户级归因”分开。

当前甲方收到：

```text
customParams = userNo
```

所以能知道：

```text
是谁推广的
```

这是对的。

但本地还有：

```text
requestKey
trackingNo
campaignName
batchNo
```

其中：

```text
trackingNo
campaignName
requestKey
```

没有任何一个传给 GoodShort。

实际传给甲方的只有：

```text
userNo
bookId
codeMedia
```

因此假设一个用户：

```text
同一部短剧
TikTok
活动A

过几天又建
同一部短剧
TikTok
活动B
```

本地有：

```text
推广任务 A
推广任务 B
```

但是 `customParams` 都是：

```text
同一个 userNo
```

甲方视角的核心唯一组合还是：

```text
同一个 pid
同一个 bookId
同一个 customParams
同一个 codeMedia
```

所以：

> **当前能够唯一确认“这是谁的订单”，但不能仅靠 customParams 唯一确认“这是这个用户的哪一个本地推广任务/哪一个 campaign”。**

---

# 8. analyticalReport 进一步验证了这个问题

GoodShort 转化明细会返回：

```text
customParams
bookId
code
```

当前系统也确实把这些字段保存进：

```text
promotion_analytical_report
```

但是当前保存逻辑只是：

```text
GoodShort report
↓
customParams
bookId
code
↓
promotion_analytical_report
```

没有继续解析成：

```text
promotion_link_id
requestKey
trackingNo
campaignName
```

因此现在是：

```text
GoodShort 转化数据有 code
本地推广任务也有 external_code

但是两边尚未形成真正的任务级业务关联
```

这部分最终会在后面的**转化数据模块**继续专项审查。

但从模块四角度已经可以判定：

```text
用户级归因：✅
推广任务级归因：⚠️
```

---

# 9. 数据库判断

当前已经只有：

```text
promotion_link
```

作为推广链接主模型。

没有再保留一套：

```text
promotion_task
+
promotion_link
```

双模型互相重复。

这是正确的简化方向。

`promotion_link` 当前字段基本能够覆盖生成任务：

```text
user_id
provider_id
connection_id
drama_id
batch_no
media_type
link_variant
request_key
tracking_no
campaign_name
external_code
share_url
status
error
```

所以不建议重新创建一张 `promotion_task`。

**现在这张 `promotion_link` 本身就可以承担“推广任务”的业务实体。**

---

# 10. P2

## P2-1 前端定义了 customParams，但后端根本不返回

前端类型：

```ts
customParams: string | null
```

但后端 `PromotionLinkVO` 转换时只返回：

```text
trackingNo
externalCode
shareUrl
status
error...
```

没有 `customParams`。

当前页面又没有使用这个字段，所以暂时不影响生产功能。

属于：

**Dead Field / 前端残留类型。**

P2。

---

## P2-2 mediaTypes 后端允许重复元素

DTO 当前是：

```text
@NotEmpty
@Size(max = 4)
List<mediaType>
```

但没有保证 List 元素唯一。

理论上直接请求：

```json
["TIKTOK", "TIKTOK"]
```

能够进入循环两次。

结合当前 `prepareBatchPending()` 逻辑，存在在同一次请求中重复准备同一任务并重复请求甲方唯一组合的风险。

正常网页多选组件不会这么提交，因此目前定 P2，不需要引入复杂机制，简单在真实输入边界保证媒体不重复即可。

---

# 11. Fake Integration / Dead Flow

## Fake 1：后端 Retry 能力

后端名字和设计：

```text
createOrRetry
```

以及：

```text
FAILED
→ resetPending
→ 再请求甲方
```

都存在。

但用户端：

```text
没有重试按钮
没有使用旧 requestKey
```

所以当前：

> **Retry 是后端能力，不是完整产品能力。**

---

## Fake 2：前端错误状态数据

前端 TypeScript 已经有：

```text
status
lastErrorCode
lastErrorMessage
```

但是任务页面完全没有消费。

所以：

```text
后端失败状态完整
≠
用户能处理失败
```

---

## Dead Field

```text
PromotionLink.customParams
```

前端定义但后端没有返回，页面也没用。

---

# 12. 不应该修改的部分

本轮审查不建议：

```text
❌ 重新创建 promotion_task 表
❌ 建复杂 PromotionTaskManager
❌ 给生成链接增加 Scheduler
❌ 做通用任务框架
❌ 为一个 2 秒规则引入复杂分布式限流平台
❌ 推翻现有 Provider Adapter 架构
```

现有主架构：

```text
promotion_link
+
PromotionLinkService
+
PromotionLinkPersistenceService
+
Provider Adapter
```

已经够用了。

现在需要的是**最小收敛现有链路**。

---

# 13. 最终问题清单

## P0

```text
P0-1
甲方生成失败，前端仍提示“推广链接和口令已生成”

P0-2
FAILED 用户看不到状态/原因，也没有重试入口
```

## P1

```text
P1-1
本地幂等键与甲方真实唯一组合不一致

P1-2
没有真实保证同一
pid + bookId + customParams + codeMedia
2秒1次

P1-3
同 requestKey 改 provider/drama 重试
可能导致本地 drama 与真实生成链接错配

P1-4
customParams 只能唯一到用户，
不能唯一到本地推广任务 / campaign
```

## P2

```text
P2-1
前端残留 customParams 字段

P2-2
mediaTypes 缺少重复元素约束
```

---

# 14. 最终判定

模块四不是“还没做”。

现在的准确状态是：

```text
甲方生成口令接口          ✅ 完成
真实 HTTP                ✅ 完成
签名                      ✅ 完成
用户选择短剧生成           ✅ 完成
code 保存                 ✅ 完成
shareUrl 保存             ✅ 完成
历史推广任务展示           ✅ 完成
口令 / 链接复制            ✅ 完成
用户级订单归因             ✅ 完成

失败状态展示               ❌
失败后重新生成             ❌
前端正确判断生成结果        ❌
甲方2秒限制                ⚠️
幂等语义                   ⚠️
推广任务级归因             ⚠️
```

## 验收结论

> **模块四成功主链已经完整，但整个模块暂时不建议验收。**

最先修的不是重构。

先把：

```text
甲方失败
↓
前端明确显示失败
↓
任务页显示失败原因
↓
用户点击重新生成
↓
沿原任务安全 Retry
↓
成功后展示 code + shareUrl
```

补通。

然后统一：

```text
本地幂等语义
↔
甲方 pid + bookId + customParams + codeMedia 唯一语义
```

并保证真实的 **2 秒限制**。

这几处补完后，模块四的生成链路就可以进入最终验收。

下一模块如果继续按接口顺序，应进入 **模块五：订单同步 / 订单归因与分佣**；而 `analyticalReport` 转化明细我建议单独作为后一个模块审，因为它和推广任务级转化数据直接相关。
