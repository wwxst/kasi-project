# 模块六最终结论

```text
甲方接口代码覆盖率：1 / 1
真实 HTTP 调用代码：1 / 1
生产 URL 正确率：0 / 1（按当前 Base URL 配置约定）

数据库落库：✅
分页同步：✅
每日定时任务：✅
前一天数据同步：✅
数据幂等：✅

用户级原始归因：✅
推广任务级归因：❌

管理端后端查询：✅
管理端页面：❌
用户端接口：❌
用户端页面：❌

完整业务闭环：未完成
最终验收：不通过
```

目前准确状态是：

> **analyticalReport 的后端基础设施已经基本写完，但生产接口地址存在错误，而且数据目前基本停留在数据库，没有真正成为用户的“推广转化数据”。**

---

# 1. 甲方接口要求

甲方接口：

```text
POST https://api.novelopen.com/creek/open/promotion/analyticalReport
```

限流：

```text
100 次/min
```

甲方明确建议：

```text
每天早上 8 点以后调用
获取前一天的数据
```

请求：

```text
pid
timestamp
sign
pageNo
pageSize ≤ 500
startTime
endTime

可选：
code
bookId
customParams
```

日期范围不能超过 30 天。

返回：

```text
reportDate
pId / pid
customParams
bookId
code

clickCount
attributedUserCount
newRegisteredUserCount
newPaidUserCount
newMemberUserCount
paidUserCount
orderCount
orderAmount
```

对应：

```text
点击
导端
注册
新充值
新会员
充值用户
充值订单
充值金额
```

均与甲方文档一致。

---

# 2. 当前代码已经实现的调用链

当前真实链路：

```text
system_scheduled_task
GOODSHORT_ANALYTICAL_REPORT_SYNC
        ↓
每天 08:00
        ↓
ScheduledTaskDispatchServiceImpl
        ↓
yesterday
        ↓
PromotionAnalyticalReportSyncServiceImpl
        ↓
pageNo = 1
pageSize = 500
        ↓
GoodShortAdapter.fetchAnalyticalReports()
        ↓
POST analyticalReport
        ↓
逐页读取
        ↓
PromotionAnalyticalReportServiceImpl
        ↓
promotion_analytical_report
        ↓
ON DUPLICATE KEY UPDATE
```

V2 Migration 确实创建了：

```text
promotion_analytical_report
```

并创建：

```text
GOODSHORT_ANALYTICAL_REPORT_SYNC
DAILY
08:00
```

所以前面检查 V1 时没有看到表和任务，不代表功能不存在——它们是在 V2 补上的。

Scheduler 也确实存在真实消费分支：

```text
case GOODSHORT_ANALYTICAL_REPORT_SYNC
    → dispatchGoodShortAnalyticalReportSync()
```

并且：

```java
yesterday = today.minusDays(1)
```

然后只拉昨天。

因此：

> **定时任务不是 Fake Integration。**

---

# 3. API 参数实现基本正确

当前 Adapter：

```text
pid
timestamp
pageNo
pageSize
startTime
endTime
code
bookId
customParams
```

都真实进入请求。

日期使用：

```text
yyyy-MM-dd
```

分页：

```text
500 / page
```

完全符合甲方最大 500 条要求。

同步 Service 也限制：

```text
startDate ≤ endDate
最大 30 个自然日
```

与甲方一致。

---

# 4. 返回字段映射完整

当前 GoodShort Adapter 会解析：

```text
reportDate
pId / pid
customParams
bookId
code
clickCount
attributedUserCount
newRegisteredUserCount
newPaidUserCount
newMemberUserCount
paidUserCount
orderCount
orderAmount
```

全部进入：

```text
ProviderAnalyticalReportRecord
```

再全部保存至：

```text
promotion_analytical_report
```

没有发现类似：

```text
甲方返回 8 个指标
系统只保存 4 个
```

的问题。

---

# 5. 数据幂等设计正确

当前数据库唯一键：

```text
report_date
+ pid
+ custom_params
+ book_id
+ code
```

重复同步同一天同一维度时：

```sql
ON DUPLICATE KEY UPDATE
```

会更新指标，而不是插入第二条。

所以：

```text
昨天数据重新拉一次
↓
不会产生重复日报
↓
会覆盖为甲方最新值
```

这一点是正确的。

---

# 6. P0：analyticalReport 当前生产 URL 与 Base URL 约定冲突

这是模块六现在最严重的问题。

管理端要求配置：

```text
接口 URL
```

页面官方示例明确是：

```text
https://api.novelopen.com/creek
```

而且说明：

> 填写平台 API 的基础地址，不包含具体接口路径。

这和甲方文档提供的接口域名：

```text
https://api.novelopen.com/creek/
```

也是一致的。

其他 GoodShort API 的 Path 都正确采用：

```text
/open/book/initBooks
/open/partner/orders
/open/filing/report
/open/inviteCode/...
```

例如订单：

```java
ORDER_PATH = "/open/partner/orders";
```

这样：

```text
Base URL
https://api.novelopen.com/creek

+

/open/partner/orders

=

https://api.novelopen.com/creek/open/partner/orders
```

是正确的。

---

## 但 analyticalReport 不一样

当前代码：

```java
ANALYTICAL_REPORT_PATH =
    "/creek/open/promotion/analyticalReport";
```

它自己又把：

```text
/creek
```

写了一遍。

于是和当前系统要求的 Base URL 约定冲突。

正确的 Path 应当和其他 GoodShort 接口一致，只承担接口路径部分：

```text
/open/promotion/analyticalReport
```

而不是再次写基础路径。

---

# 7. 为什么测试没有发现这个问题

当前 `GoodShortAnalyticalReportAdapterTest` 使用：

```text
baseUrl =
https://goodshort.test
```

而不是系统生产配置约定：

```text
https://goodshort.test/creek
```

然后测试期望：

```text
https://goodshort.test/creek/open/promotion/analyticalReport
```

因此测试会通过。

但订单测试同样使用：

```text
https://goodshort.test
```

却期望：

```text
https://goodshort.test/open/partner/orders
```

这实际上暴露了两个测试对 Base URL 的假设不统一：

```text
订单测试：
baseUrl = host
path = /open/...

analytical 测试：
baseUrl = host
path = /creek/open/...
```

而生产管理页面明确告诉管理员填写：

```text
https://api.novelopen.com/creek
```

因此：

> **当前 analyticalReport 单测验证的 URL 模型和生产配置模型不一致。**

### 判定

**P0。**

因为在这个问题解决前：

```text
Scheduler 有
Service 有
Mapper 有
数据库有

但真实甲方接口可能根本请求不到正确地址
```

整个模块无法验收。

---

# 8. 用户级原始归因：基础数据是有的

模块四生成推广链接时：

```text
customParams = promotion_user.user_no
```

甲方 analyticalReport 又会把：

```text
customParams
```

返回。

当前数据库也保存：

```text
custom_params
```

所以数据本身具备：

```text
转化日报
→ customParams
→ user_no
→ 用户
```

的基础。

甚至 Admin 后端查询接口已经支持：

```text
userNo
```

然后内部直接转成：

```text
customParams
```

查询。

因此不能说：

> “完全无法知道是谁的数据。”

准确说是：

> **原始用户级归因字段存在，但没有形成真正的用户产品能力。**

---

# 9. P0：用户完全看不到自己的转化数据

这是本模块第二个验收阻塞点。

当前用户路由只有：

```text
首页
账号报白
短剧推广
推广任务
订单
个人中心
```

没有：

```text
推广数据
转化数据
数据统计
```

用户端也没有：

```text
/api/user/.../analytical-reports
```

之类 Controller。

当前后端 Controller 只有：

```text
/api/admin/promotion/analytical-reports
```

所以真实业务链目前是：

```text
GoodShort
↓
analyticalReport
↓
数据库
↓
结束
```

并没有：

```text
数据库
↓
当前用户
↓
点击量
↓
导端数
↓
注册数
↓
充值人数
↓
订单数
↓
订单金额
↓
用户推广任务页
```

因此用户现在生成一条推广任务以后：

```text
有多少点击？
有多少导端？
注册多少？
充值多少？
产生多少订单？
产生多少订单金额？
```

**全部看不到。**

这正是 analyticalReport 接入的核心产品价值。

所以：

**P0。**

---

# 10. 管理端也没有真正页面

后端已经有：

```text
POST /api/admin/promotion/analytical-reports/sync

GET /api/admin/promotion/analytical-reports
```

也就是说管理员后端理论上已经可以：

```text
手动同步
查询日报
```

但是管理端当前 Promotion 页面只有：

```text
MediaAccountFilingPage
PromotionOrderPage
```

没有 AnalyticalReport 页面。

管理端 `features/promotion` 也只有：

```text
filing
mediaAccount
promotionOrder
```

没有 analytical API 封装。

Router 里也只有：

```text
/promotion/media-accounts
/promotion/orders
```

没有转化数据路由。

所以：

```text
管理端 Controller       ✅
管理端 Service          ✅
管理端查询              ✅

管理端 API 封装         ❌
管理端页面              ❌
管理端菜单              ❌
```

属于典型：

> **Backend-only Integration｜只有后端能力。**

P1。

---

# 11. P1：定时同步失败以后会直接跳到第二天

这个问题很重要。

Scheduler 当前：

```java
try {
    dispatch(task);
} catch (...) {
    log.error(...)
}

taskMapper.completeRun(... nextRun(...));
```

也就是说即使：

```text
GoodShort 网络失败
429
5xx
远程错误
```

当天任务失败以后，仍然会执行：

```text
completeRun()
↓
计算下一次 DAILY
↓
明天 08:00
```

---

## 举例

9 月 7 日 08:00：

```text
计划拉 9 月 6 日
```

但是 GoodShort 临时 500：

```text
sync 失败
↓
记录日志
↓
任务下一次改成
9 月 8 日 08:00
```

9 月 8 日执行：

```text
yesterday
=
9 月 7 日
```

于是：

```text
9 月 6 日
```

就永久跳过去了。

除非管理员以后人工调用后台 API：

```text
POST /analytical-reports/sync
startDate=09-06
endDate=09-06
```

但是前面已经确认：

> **管理端甚至没有这个手动同步页面。**

因此真实异常路径是：

```text
一天同步失败
↓
这一天的转化数据可能永远缺失
```

### 判定

**P1，数据完整性风险。**

不需要复杂重试框架。

最小业务要求至少应保证：

```text
失败的昨日数据
不会因为第二天到来而永远跳过
```

---

# 12. P1：没有形成推广任务级归因

当前表只保存：

```text
customParams
bookId
code
```

并没有解析为：

```text
user_id
drama_id
promotion_link_id
tracking_no
campaign_name
```

`PromotionAnalyticalReportServiceImpl` 当前只是：

```text
甲方字段
↓
原样 set
↓
mapper.upsert
```

没有：

```text
PromotionUserMapper
ProviderDramaMapper
PromotionLinkMapper
```

参与。

---

## 现在能做到

```text
customParams
→ 知道大致是哪个 userNo

bookId
→ 知道是哪部甲方短剧

code
→ 有一个甲方口令
```

---

## 现在不能直接做到

```text
这条日报
↓
属于哪一条 promotion_link？
↓
属于哪个 trackingNo？
↓
属于用户创建的哪个推广任务？
↓
campaignName 是什么？
```

所以推广任务页目前无法自然展示：

```text
任务A

点击：128
导端：32
注册：15
充值：4
订单：6
订单金额：$XX
```

这和模块四发现的问题是同一条断链。

### 判定

**P1。**

---

# 13. 注意：这里不应该拿 analyticalReport 做用户结算

这一点反而需要明确。

`analyticalReport` 是：

```text
日报汇总 / Conversion Analytics
```

包含：

```text
clickCount
registered
paid users
orderCount
orderAmount
```

但它没有：

```text
orderId
payStatus
退款状态
单笔订单事实
```

甲方真正的订单接口 `/partner/orders` 才返回：

```text
orderId
payMoney
payStatus
退款
customParams
```

所以当前：

```text
用户分佣
↓
promotion_order
↓
/partner/orders
```

而不是：

```text
analyticalReport
↓
直接算佣金
```

**这个方向是正确的。**

不要为了“闭环”把 analyticalReport 再接进结算计算。

模块六应该负责：

```text
推广效果统计
```

模块五负责：

```text
真实订单与结算
```

两者职责要继续分开。

---

# 14. P2：100 次/min 没有主动限流

甲方：

```text
100次/min
```

系统：

```text
pageSize = 500
顺序分页
```

正常日数据规模下问题不大。

只有一天超过：

```text
50,000 条日报维度
```

才会超过 100 页。

所以目前没必要建设复杂 RateLimiter。

定：

**P2 / 容量边界。**

---

# 15. P2：管理端查询仍然只是技术字段

当前后台 VO 输出：

```text
pid
customParams
bookId
code
clickCount
...
```

没有转成：

```text
用户昵称
用户编号
短剧名称
推广名称
媒体
trackingNo
```

所以即使马上简单做一个管理端表格，也很可能看到：

```text
583104726918
FhxQGC2cRAG...
54788
```

而不是管理员真正需要看的：

```text
用户：张三
短剧：XXX
推广任务：TikTok测试1
点击：100
注册：20
订单：5
```

这属于 P2/P1 边界问题。

如果要做管理端转化数据页，应该直接消费现有关系，把技术标识翻译成业务信息。

不需要再建一套数据表。

---

# 16. Fake Integration / Partial Integration

## Partial 1：Admin Analytical Report

```text
Controller     ✅
Service        ✅
Mapper         ✅
DB             ✅

Admin Web      ❌
```

---

## Partial 2：用户转化数据

```text
数据库有数据       ✅
customParams有用户标识 ✅

User Controller   ❌
User API          ❌
User 页面         ❌
```

---

## Partial 3：推广任务转化

```text
code       ✅
bookId     ✅
userNo     ✅

promotion_link_id ❌
trackingNo        ❌
campaignName      ❌
```

---

# 17. 当前正确部分总结

```text
✅ analyticalReport Adapter 存在
✅ ANALYTICS_SYNC Capability 存在
✅ timestamp 正确
✅ sign 正确
✅ pageNo 正确
✅ pageSize=500
✅ startTime/endTime 正确
✅ 最大30天正确
✅ 可按 code 查询
✅ 可按 bookId 查询
✅ 可按 customParams 查询
✅ 8点定时任务存在
✅ 自动拉前一天
✅ Scheduler 真实调用
✅ V2 建表
✅ 指标字段完整
✅ 唯一键合理
✅ Upsert 幂等
```

---

# 18. 当前问题等级

## P0

```text
P0-1
analyticalReport Path 与系统 Base URL 约定冲突，
生产接口地址存在重复 /creek 问题。

P0-2
用户完全没有转化数据接口和页面，
analyticalReport 的核心用户业务价值没有闭环。
```

---

## P1

```text
P1-1
每天08:00任务失败以后仍直接进入下一天，
失败日期没有自动补拉。

P1-2
管理端虽然有后端查询/同步接口，
但没有 API 消费、页面、菜单。

P1-3
日报没有真正解析关联 promotion_link，
无法形成“推广任务 → 转化数据”。

P1-4
无法展示 trackingNo / campaignName 级推广效果。
```

---

## P2

```text
P2-1
没有100次/min主动限流。

P2-2
管理端返回的仍然主要是技术字段，
缺少用户、短剧、推广任务业务展示信息。
```

---

# 19. 最终验收判定

当前模块六：

```text
GoodShort analyticalReport接口代码   ✅
参数                               ✅
签名                               ✅
分页                               ✅
数据库                             ✅
幂等                               ✅
每天8点                            ✅
拉前一天                            ✅

生产URL                            ❌
异常补拉                            ❌

用户级原始标识                      ✅
用户数据产品                        ❌

推广任务级转化                       ❌
管理端页面                           ❌
用户端页面                           ❌
```

因此：

> **模块六目前不能验收。**

它现在更准确地说是：

> **“后端数据采集骨架完成，但真实接口地址、失败补拉和前端数据消费都没有收口。”**

---

# 20. 最小修复顺序

不要重构架构。

建议只按下面顺序收敛：

```text
1. 修正 analyticalReport Path
   统一所有 GoodShort API 的 Base URL 语义

2. 增加真实 Base URL 测试
   baseUrl =
   https://api.novelopen.com/creek

3. 确保每日同步失败不会永久跳过该日期

4. 用现有：
   customParams + bookId + code
   关联现有用户 / 短剧 / promotion_link

5. 用户端提供推广转化数据接口

6. 推广任务页展示：
   点击
   导端
   注册
   新充值
   充值用户
   订单数
   订单金额

7. 管理端增加转化数据查询/手动补同步页面
```

不建议：

```text
❌ 新建另一套统计表
❌ 新建 PromotionAnalyticsTask
❌ 建复杂事件总线
❌ 把 analyticalReport 接进佣金结算
❌ 建复杂通用限流框架
```

现有：

```text
promotion_analytical_report
+
PromotionAnalyticalReportSyncService
+
GoodShortAdapter
```

完全可以继续用。

问题是把最后的业务链路真正接起来。
