# 模块五最终结论

```text
甲方订单接口覆盖率：1 / 1
甲方接口真实 HTTP 调用率：1 / 1

订单同步：✅ 完成
订单幂等：✅ 完成
用户级归因：✅ 完成
单笔 CPS 分佣：✅ 基本完成
退款处理：⚠️ 后端状态完成，但用户收益展示有错误
推广任务级归因：❌ 未完成
月度收益汇总：❌ 计算错误
完整业务闭环：部分完成

最终验收：暂不通过
```

模块五现在不是“订单没做”。

准确状态是：

> **订单事实链已经基本打通，但“钱”的展示和推广任务级归因还有验收级问题。**

---

# 1. 甲方订单接口

GoodShort：

```text
POST /open/partner/orders
```

完整地址：

```text
https://api.novelopen.com/creek/open/partner/orders
```

甲方要求：

```text
pid
timestamp
sign
pageNo
pageSize
startDate
endDate
```

其中：

* `pageSize` 最大 500；
* `payMoney` 单位为美分；
* `payStatus`：

  * `0` 未支付
  * `1` 已支付
  * `3` 退款
* `customParams` 原样返回，用于判断订单属于哪个达人；
* 同时返回 `bookId / searchCode / channelCode / utime`。
* 接口限流 100 次/min。

当前代码真实发送：

```text
pid
timestamp
pageNo
pageSize = 500
startDate
endDate
sign Header
```

并真实：

```text
POST /open/partner/orders
```

不是 Mock，也不是只写 DTO。

因此：

```text
接口覆盖：1 / 1
真实 HTTP：1 / 1
```

---

# 2. 当前真实订单链路

```text
SystemScheduledTask
GOODSHORT_ORDER_SYNC
        ↓
ScheduledTaskDispatchServiceImpl
        ↓
最近 3 天
start = now - 3 days
end = now
        ↓
PromotionOrderSyncServiceImpl
        ↓
GoodShortAdapter.fetchOrders()
        ↓
POST /open/partner/orders
pageSize = 500
        ↓
逐页读取
        ↓
PromotionOrderServiceImpl.upsert()
        ↓
connectionId + externalOrderId
查已有订单
        ↓
新订单 / 更新订单
        ↓
customParams
        ↓
promotion_user.user_no
        ↓
user_id
        ↓
找到当前分佣规则历史快照
        ↓
五项费率
        ↓
计算 commission_amount
        ↓
promotion_order
        ↓
管理端订单
+
用户端我的订单
```

定时任务不是假的。

当前 Baseline 直接初始化：

```text
GOODSHORT_ORDER_SYNC
每隔 1 分钟
同步最近 3 天 GoodShort 订单
```

Scheduler 又有数据库 Lease｜租约，所以正常多实例不会简单地同时执行同一固定任务。

---

# 3. 已确认正确

## 3.1 分页正确

代码：

```text
SYNC_PAGE_SIZE = 500
```

然后：

```text
pageNo = 1
↓
fetch
↓
hasNext
↓
pageNo++
```

直到全部读取结束。

和甲方 `pageSize ≤ 500` 一致。

---

## 3.2 金额“美分 → 美元”转换正确

甲方：

```text
payMoney = 美分
```

例如：

```text
999
→
9.99
```

当前：

```java
BigDecimal.valueOf(amountMinor)
    .movePointLeft(2)
```

正确。

---

## 3.3 订单状态映射正确

```text
0 → UNPAID
1 → PAID
3 → REFUNDED
其他 → UNKNOWN
```

与甲方定义一致。

---

## 3.4 订单幂等正确

数据库唯一键：

```text
connection_id
+
external_order_id
```

即：

```text
同一个 GoodShort Connection
+
同一个甲方订单 ID
```

只能有一条订单。

Service 同时：

```text
findBySourceForUpdate()
```

并对并发插入保留 `DuplicateKeyException` 后重新读取。

因此：

```text
每分钟重复同步最近3天
```

不会不断制造重复订单。

这一块是合理的。

---

# 4. 用户级归因：完成

模块四生成链接时：

```text
customParams = promotion_user.user_no
```

GoodShort 又明确承诺：

> `customParams` 会在订单接口原样返回，用于区分达人。

当前订单代码：

```text
order.customParams
        ↓
PromotionUserMapper.findByUserNo()
        ↓
user.id
        ↓
promotion_order.user_id
        ↓
ATTRIBUTED
```

是真实执行的。

所以目前：

> **“这笔订单属于卡司哪个用户”能够判断。**

这一层可以验收。

---

# 5. CPS 分佣计算：单笔基本正确

找到用户以后，PAID 订单会读取：

```text
ProviderCommissionRuleHistory
```

然后把以下五项费率直接保存到订单：

```text
channelFeeRate
principalFeeRate
principalCommissionRate
downstreamFeeRate
downstreamCommissionRate
```

同时保存：

```text
rule_history_id
commission_amount
```

也就是说：

> 后面管理员修改分佣比例，不会直接篡改已经计算过的订单费率快照。

当前公式：

```text
订单金额
× (1 - 渠道费率)
× (1 - 甲方手续费率)
× 甲方给我方分佣比例
× (1 - 下游手续费率)
× 我方给用户分佣比例
```

最后保留两位小数。

结构上是完整的。

至于生产数据库里现在具体配置了多少比例，本轮只看代码仓库，**无法证明生产配置值是否和当前商务合同一致**，这个需要实际数据库配置验收，不属于代码实现缺失。

---

# 6. P0：退款订单仍然给用户显示正收益

这是当前最明显的用户侧钱款错误。

订单最初：

```text
PAID
commission_amount = 10.00
commission_status = CALCULATED
```

后来甲方返回退款：

```text
REFUNDED
```

后端执行：

```text
commission_status = REVERSED
```

但是：

```text
commission_amount = 10.00
```

仍然保留。

保存原佣金金额本身没问题，因为历史审计需要。

问题出在用户接口。

`UserPromotionOrderVO` 只返回：

```text
externalOrderId
currency
status
paidAt
commissionAmount
```

**没有返回 commissionStatus。**

用户页面又直接：

```text
commissionAmount
→ “我的收益”
```

没有判断订单是不是退款。

所以真实效果：

```text
订单状态：已退款
我的收益：$10.00
```

这是错误的。

用户看到的会是：

> **订单明明已经退款，但页面仍显示这笔钱是“我的收益”。**

### 结论

**P0，阻塞模块五验收。**

---

# 7. P0：月度净收益退款计算重复扣除

后端已经存在：

```text
GET /api/user/promotion/orders/monthly
```

SQL 当前计算：

```text
calculatedCommission
=
commission_status = CALCULATED 的佣金

reversedCommission
=
commission_status = REVERSED 的佣金
```

然后 Service：

```text
netCommission
=
calculatedCommission
-
reversedCommission
```

问题在于：

退款以后，那一行已经从：

```text
CALCULATED
```

变成：

```text
REVERSED
```

所以它本来就已经不在 `calculatedCommission` 中了。

现在再减一次，就是**重复扣款**。

---

## 举例

有两笔订单：

```text
订单A
佣金 $10
正常

订单B
佣金 $10
后来退款
```

当前数据库状态：

```text
A = CALCULATED $10
B = REVERSED   $10
```

SQL 得：

```text
calculated = $10
reversed   = $10
```

当前代码：

```text
net
=
10 - 10
=
$0
```

但真实有效收益应该是：

```text
$10
```

更加明显的情况：

只有一笔：

```text
$10
后来退款
```

当前代码最后：

```text
0 - 10
=
-$10
```

实际上这笔订单退款后的当前有效收益应该是：

```text
$0
```

因此这是一个明确的**财务汇总计算错误**。

### 结论

**P0。**

---

# 8. P1：订单没有关联到具体推广任务

这是模块四留下来的问题，到模块五已经证明是真断点。

数据库其实预留了：

```text
tracking_no
promotion_link_id
user_id
drama_id
```

而 `promotion_link` 已经保存：

```text
tracking_no
external_code
user_id
drama_id
```

同一个 GoodShort 订单还会返回：

```text
customParams
bookId
searchCode
channelCode
```

理论上已经具备继续解析推广来源的数据基础。

但实际 `PromotionOrderServiceImpl` 归因只做了：

```text
customParams
↓
user_no
↓
user_id
```

然后结束。

完全没有：

```text
PromotionLinkMapper
```

参与归因，也没有设置：

```text
promotionLinkId
trackingNo
```

所以目前只能知道：

```text
这是树文的订单
```

不能知道：

```text
这是树文哪一条推广任务产生的订单
```

---

# 9. “推广跟踪号”现在属于 Fake Integration

这个问题甚至已经直接反映到前端。

## 用户端

页面有一列：

```text
推广跟踪号
```

前端类型也定义：

```text
trackingNo
```

但后端 `UserPromotionOrderVO`：

```text
根本没有 trackingNo
```

所以用户页面这一列实际上只能长期：

```text
-
```

---

## 管理端

管理端也有：

```text
追踪号
```

这一列。

前端 TypeScript 也要求：

```text
trackingNo
```

但是后端 `PromotionOrderVO` 也根本没有 `trackingNo` 字段。

即使未来数据库写进去了，现在 VO 也不会返回。

因此当前是三层都断：

```text
订单归因 Service
❌ 没有匹配 promotion_link

数据库
⚠️ 字段有，但是没人写

后端 VO
❌ 不返回 trackingNo

前端
✅ 有“推广跟踪号”列
```

所以：

> **“推广跟踪号”目前就是典型 Fake Integration｜假功能。**

P1。

---

# 10. P1：自动同步最近 3 天，老订单退款没有可靠覆盖保证

当前固定任务：

```text
每 1 分钟
同步 now - 3 days ～ now
```

这样做有一个好处：

```text
最近3天不断重复拉
```

可以补偿短期延迟，不容易漏最近订单。

但是退款存在另一个问题。

假设：

```text
8月1日支付
8月15日退款
```

8 月 15 日自动任务的窗口只有：

```text
8月12日 ～ 8月15日
```

甲方订单接口文档给的是：

```text
startDate
endDate
```

同时订单返回里另有：

```text
payTime
utime
```

但甲方文档没有承诺：

> “旧订单只要 utime 更新，即使 payTime 不在 startDate/endDate 内也一定会返回。”

所以从当前第三方契约来看，系统**无法证明**：

```text
三天以前支付
但今天刚退款
```

一定会重新被自动同步到。

如果 GoodShort 的日期窗口按支付日期筛选，这类老退款就会直接漏掉。

后果就是：

```text
甲方已经退款
↓
卡司数据库还保持 PAID
↓
commission_status 还是 CALCULATED
↓
用户仍然看到收益
```

管理端虽然提供最多 31 天手动补拉，但这是人工补救，不是自动完整性保证。

### 结论

这一条我不定成“已确认必现 Bug”，因为甲方没有写清后台具体日期筛选实现。

但它是：

> **P1 财务数据完整性风险，必须向甲方确认接口窗口语义，或者用实际接口验证。**

---

# 11. 用户月度收益功能实际上没有真正接到页面

这是另一个半成品。

后端已经存在：

```text
GET /api/user/promotion/orders/monthly
```

用户前端 API 文件也已经写了：

```text
fetchMonthlyCommission()
```

类型也完整：

```text
paidOrderCount
calculatedCommission
reversedCommission
netCommission
```

但是实际 `OrdersPage`：

```text
只调用 fetchPromotionOrders()
```

没有调用：

```text
fetchMonthlyCommission()
```

所以现在用户只能看：

```text
一笔
一笔
一笔订单
```

看不到：

```text
本月有效订单
本月收益
退款扣除
本月净收益
```

因此：

```text
后端月收益接口        ✅
前端 API 封装         ✅
页面真实消费           ❌
```

属于 **Partial / Fake Integration**。

而且即使现在接上页面，还会立即碰到前面的 **P0 月度退款计算错误**。

---

# 12. P2：甲方 100 次/min 没有代码级硬限制

甲方规定：

```text
100次/min
```

当前：

```text
pageSize = 500
顺序分页
```

一般业务量较小时问题不大。

例如：

```text
10,000条订单
=
20次请求
```

不会超过。

但如果某次 3 天窗口超过：

```text
50,000条
```

就需要超过 100 页。

当前没有明确的订单接口节流器。

所以理论上存在超过：

```text
100/min
```

的可能。

不过当前接口是顺序 HTTP 请求，而且实际卡司订单量远未必达到这个规模，因此这里没必要上复杂分布式限流系统。

定：

**P2 / 容量边界。**

---

# 13. P2：管理端 CSV 最多只导出 10,000 条

当前：

```text
EXPORT_LIMIT = 10_000
```

然后一次：

```text
findPage(..., 0, 10000)
```

如果筛选结果：

```text
15,000条
```

实际 CSV 只有：

```text
10,000条
```

目前没有在文件名或页面上明显告诉管理员：

```text
已截断
```

正常现阶段数据规模不大，可以定 P2。

无需为此建设复杂异步导出中心。

---

# 14. 当前数据库模型判断

`promotion_order` 整体设计没有必要推翻。

保留：

```text
connection_id
provider_id
external_order_id

订单原始金额
原始状态
raw_payload_json

custom_params

规则历史 ID
五项费率快照
commission_amount

同步窗口
last_synced_at
```

都是有真实业务意义的。

唯一键：

```text
connection_id + external_order_id
```

也正确。

继续保持：

```text
零物理外键
零数据库级联
```

没有问题。

这里**不要再创建第二套 Order / Commission 主表**。

---

# 15. Fake Integration / Dead Flow 汇总

## Fake Integration 1

```text
用户“推广跟踪号”列
```

UI 有，后端 VO 没有，数据库也没人写。

---

## Fake Integration 2

```text
管理端“追踪号”列
```

同样没有真实数据来源。

---

## Fake Integration 3

```text
fetchMonthlyCommission()
```

前端 API 已封装，但页面没有消费者。

---

## Partial Integration

```text
promotion_order.promotion_link_id
promotion_order.tracking_no
```

Schema 有业务意图，但当前订单归因代码完全没有写入。

---

# 16. 不建议做的事情

这一模块没必要：

```text
❌ 新建第二套订单表
❌ 新建 CommissionTask
❌ 新建 RefundTask
❌ 建复杂事件总线
❌ 建通用财务计算框架
❌ 建复杂分布式限流中心
❌ 推翻当前订单 Upsert
```

当前：

```text
partner/orders
→ PromotionOrderSyncService
→ PromotionOrderService
→ promotion_order
```

这条主架构是可以继续用的。

需要做的是把现有链路收干净。

---

# 17. 最终问题等级

## P0

```text
P0-1
退款订单在用户端仍显示正数“我的收益”

P0-2
月度净收益对 REVERSED 订单重复扣除，
净收益计算错误
```

---

## P1

```text
P1-1
订单只归因到用户，未归因到 promotion_link / 推广任务

P1-2
用户端和管理端“推广跟踪号”是假展示

P1-3
月度收益后端和前端 API 已存在，
但页面没有真正消费

P1-4
自动只滚动同步最近3天，
无法从甲方契约证明老订单后续退款一定能被捕获
```

---

## P2

```text
P2-1
没有代码级 100次/min 限流保证

P2-2
管理端 CSV 最多 10000 条且没有明显截断提示
```

---

# 18. 最终验收结论

模块五现在：

```text
GoodShort /partner/orders     ✅
真实HTTP                      ✅
分页                          ✅
金额转换                       ✅
订单状态                       ✅
重复同步幂等                   ✅
用户级归因                     ✅
分佣规则快照                   ✅
单笔分佣计算                   ✅
退款状态反转                   ✅

退款后的用户收益展示            ❌
月度净收益                      ❌
promotion_link级归因           ❌
推广跟踪号                     ❌
月度收益页面                   ❌
老退款自动补同步保证            ⚠️
```

## 最终判定

> **模块五暂不建议验收通过。**

但不需要大改架构。

优先收敛顺序应该是：

```text
1. 修正退款订单的用户收益显示

2. 修正月度净收益算法

3. 订单利用现有 GoodShort 数据
   关联现有 promotion_link
   → promotion_link_id
   → tracking_no
   → drama_id

4. 用户端 / 管理端真正返回并展示 trackingNo

5. 接通月度收益页面

6. 和 GoodShort 确认：
   老订单退款后的 startDate/endDate 查询语义
```

这几项完成以后，**订单 → 用户 → 推广任务 → 佣金 → 退款 → 用户收益** 才算真正闭环。

下一步模块六再单独审：

```text
POST /promotion/analyticalReport
```

也就是：

**点击 → 导端 → 注册 → 充值人数 → 订单数 → 订单金额 → 推广任务转化数据。**
