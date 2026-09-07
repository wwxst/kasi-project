# 模块七最终结论

```text
项目管理业务实体：❌ 未实现

项目列表：❌
项目新增/编辑/启停：❌
项目类型 CAP/CPS/CPM：❌
项目级 Provider 绑定：❌
项目级 API/MANUAL：❌
项目级分佣规则：❌

Provider 平台管理：✅
Provider Connection：✅
API 密钥加密存储：✅
API/MANUAL 平台级配置：✅
平台级默认分佣规则：✅
分佣规则不可变历史快照：✅
超级管理员写权限：✅

完整业务闭环：未完成
模块七验收：不通过
```

一句话概括：

> **当前系统有“GoodShort 平台配置”，没有“卡司项目管理”。**

---

# 1. 当前真实模型到底是什么

现在数据库实际结构是：

```text
short_drama_provider
        ↓
GoodShort
        ↓
short_drama_connection
        ↓
PID
KEY
BaseURL
filingMode
currency

        ↓

provider_commission_rule
        ↓
GoodShort 全平台默认分佣规则
```

数据库 `short_drama_provider` 只表达第三方平台，例如 GoodShort；`short_drama_connection` 表达该平台的接入账号。Connection 上保存 PID、API Key、Base URL、币种以及 `filing_mode`。更关键的是数据库对 `provider_id` 做了唯一约束，也就是当前一个 Provider 只能有一个 Connection。

现在没有：

```text
promotion_project
project
project_type
settlement_type
CAP
CPS
CPM
```

这样的业务模型。

Migration 目前也只有：

```text
V1__baseline.sql
V2__promotion_analytical_report.sql
```

没有后续项目管理 Migration。

所以当前实际上是：

```text
GoodShort = 技术平台
≈
短剧推广业务
```

把两个概念临时合并用了。

在现在只有一个 GoodShort 短剧 CPS 项目的情况下能跑。

但以后：

```text
卡司项目管理
├── CapCut 拉新
│   └── CAP
│
├── GoodShort 短剧推广
│   └── CPS
│
└── 某广告项目
    └── CPM
```

当前数据模型表达不了。

---

# 2. 管理端目前也不是“项目管理”

当前管理端真实存在的是：

```text
系统配置
├── 短剧 API 配置
├── 定时任务
├── 分佣规则
└── 短信配置
```

Router 里有：

```text
/system-config/drama-api
/system-config/commission-rules
```

但是没有：

```text
/project
/projects
/project-management
```

之类项目路由。

`ProviderManagementPage` 页面标题也明确就是：

```text
短剧 API 配置
```

内容是：

```text
接口 URL
PID
KEY
域名白名单
API / 人工报备
启用状态
```

不是项目资料。

所以：

> **“短剧 API 配置”不能当成“项目管理已经完成”。**

---

# 3. CAP / CPS / CPM 当前状态

仓库目前没有统一的：

```text
SettlementType
ProjectType
CAP
CPS
CPM
```

业务字段。

因此不能在后台建立：

| 项目        | 结算类型 |
| --------- | ---- |
| CapCut 拉新 | CAP  |
| 短剧推广      | CPS  |
| 某曝光项目     | CPM  |

---

## 但 CPS 计算逻辑确实已经存在

这一点要区分。

虽然没有：

```text
settlement_type = CPS
```

这个业务模型，

但模块五已经确认：

```text
GoodShort订单
↓
订单金额
↓
五项费率
↓
用户佣金
```

这实际上就是一条 CPS 结算逻辑。

因此正确说法是：

```text
CPS业务计算能力：✅ 已存在
CPS项目类型模型：❌ 不存在

CAP业务模型：❌
CPM业务模型：❌
```

不能因为系统能算 CPS 订单，就认为：

> “项目管理已经支持 CPS。”

这是两个层面的事情。

---

# 4. 当前分佣规则是真功能

这部分不用推翻。

现在：

```text
provider_commission_rule
```

保存五项当前费率：

```text
渠道费率
甲方手续费率
甲方分佣比例
我方手续费率
下游分佣比例
```

数据库明确限制：

```text
UNIQUE(provider_id)
```

即：

> **每个平台只有一套当前默认规则。**

后台也存在真实的：

```text
分佣规则
```

页面。

页面明确写着：

> “按短剧平台配置默认分佣费率，平台下所有短剧和接入账号共用。”

因此这个功能不是 Fake Integration。

真实链：

```text
分佣规则页面
↓
GET /commission-rules
POST /commission-rules
PUT /commission-rules/{id}
↓
ProviderCommissionRuleController
↓
ProviderCommissionRuleServiceImpl
↓
provider_commission_rule
```

已经完成。

---

# 5. 分佣历史快照也是正确设计

每次：

```text
创建分佣规则
或
修改分佣规则
```

Service 都会：

```text
appendHistory()
```

生成新的：

```text
provider_commission_rule_history
```

并保存：

```text
providerId
ruleId
五项费率
createdBy
createdAt
```

订单自己又保存：

```text
rule_history_id

channel_fee_rate
principal_fee_rate
principal_commission_rate
downstream_fee_rate
downstream_commission_rate

commission_amount
```

因此已经计算完成的订单：

```text
管理员今天改分佣
↓
昨天订单
```

不会跟着当前规则直接漂移。

这一点设计方向正确。

---

# 6. 权限边界也正确

这一轮专门核了，不能误判成“只靠前端隐藏按钮”。

SecurityConfig 对以下写接口明确要求：

```text
SUPER_ADMIN
```

包括：

```text
修改 Provider Connection
连接测试
修改 filingMode
新增分佣规则
修改分佣规则
```

前端也同时：

```text
isSuperAdmin
```

控制编辑能力。

普通管理员只能查看。

因此：

```text
普通管理员抓包修改 PID/KEY     ❌ 被后端拦截
普通管理员抓包修改分佣比例      ❌ 被后端拦截
```

当前不存在这个权限漏洞。

---

# 7. P0：Project｜项目这一层完全不存在

这是模块七最大的阻塞问题。

你需要的是：

```text
项目管理
        ↓
项目列表
        ↓
CapCut拉新
短剧推广
……
```

每个项目应该至少能够表达：

```text
项目名称
结算类型
状态
所属业务/平台
```

然后不同项目再决定：

```text
CAP
CPS
CPM

API
MANUAL

分佣规则
```

但现在：

```text
用户
↓
直接进入 GoodShort 短剧
```

系统中间没有 Project。

也就是说没有办法表达：

```text
这个用户参加了哪个项目？
这条推广链接属于哪个项目？
这个订单属于哪个项目？
这条报白属于哪个项目？
这套分佣是谁的项目规则？
```

现在这些业务事实上全部隐含成：

```text
providerId = GoodShort
```

### 判定

**P0。**

如果模块七的目标就是“项目管理”，这一条本身就足以判定：

> **模块七未完成。**

---

# 8. P0：结算类型没有真正建模

当前没有：

```text
CAP
CPS
CPM
```

统一结算类型。

导致未来业务没办法通过项目配置决定：

```text
这个项目应该按什么计算。
```

---

## 当前 GoodShort

硬编码业务路径事实上是：

```text
订单金额
×
分佣规则
=
佣金
```

所以属于 CPS。

---

## 如果增加 CapCut

例如：

```text
美国拉新：每人 $X
非美国：每人 $Y
```

属于 CAP。

当前：

```text
provider_commission_rule
```

五项百分比公式根本不适用于这种项目。

---

## CPM 同理

```text
每1000播放
×
单价
```

也不是当前五项 CPS 费率模型可以表达的。

因此不能做成：

```text
所有项目
↓
全部硬塞进 provider_commission_rule
```

### 判定

**P0。**

应该先有：

```text
settlementType
```

然后不同结算类型再走自己的计算规则。

---

# 9. P0：当前“分佣规则”挂错业务层

现在：

```text
provider_commission_rule
```

唯一维度是：

```text
provider_id
```

而管理端甚至明确告诉管理员：

> 平台下所有短剧和接入账号共用。

假设以后：

```text
GoodShort 项目A
给用户 70%

GoodShort 项目B
给用户 75%
```

当前无法表达。

因为数据库只能有：

```text
GOODSHORT
→ 一套规则
```

所以现在所谓：

```text
分佣规则
```

准确叫：

> **Provider Default Commission Rule｜平台默认分佣规则**

而不是：

> **Project Commission Rule｜项目分佣规则**

如果未来项目层上线，当前规则可以保留为：

```text
平台默认值
```

但不能继续作为最终业务 Owner。

### 判定

对于模块七：

**P0。**

---

# 10. P1：一个 Provider 只能配置一个 Connection

数据库：

```text
UNIQUE KEY uk_drama_connection_provider(provider_id)
```

所以：

```text
GoodShort
↓
只能一个 PID / KEY
```

假设未来业务出现：

```text
GoodShort 项目A
PID = 1001

GoodShort 项目B
PID = 2002
```

当前数据库无法保存两个 Connection。

现在只有一个 GoodShort 机构账号时没问题。

因此这一条不是当前线上立即 Bug，但会直接阻止真正的多项目模型。

### 判定

**P1。**

---

# 11. P1：API / MANUAL 目前属于平台接入账号，不属于项目

当前：

```text
short_drama_connection.filing_mode
```

所以业务表达的是：

```text
GoodShort
→ API
```

或者：

```text
GoodShort
→ MANUAL
```

但表达不了：

```text
GoodShort 项目A
→ API报白

GoodShort 项目B
→ 人工报白
```

而你项目管理的要求本来就是：

```text
项目
↓
报白方式
├─ API
└─ 人工
```

所以 Owner｜归属层级目前放在 Connection 上。

单项目时代还能工作。

多项目之后就不够。

### 判定

**P1。**

---

# 12. P1：历史规则保存了，但选规则的时间语义不完整

这个是财务边界问题。

当前订单算佣金时执行：

```text
findLatestByProviderId(providerId)
```

也就是：

> **订单被系统计算的这一刻，取最新一条规则。**

它没有：

```text
findEffectiveAt(providerId, paidAt)
```

之类逻辑。

---

## 举例

假设：

```text
9月1日
用户分佣 = 70%

9月5日
改成 75%
```

有一笔订单：

```text
9月3日已经支付
```

但由于第三方延迟：

```text
9月6日才第一次同步到系统
```

当前代码：

```text
9月6日计算
↓
findLatest
↓
取 75%
```

而不是自动按：

```text
9月3日支付时有效的 70%
```

计算。

---

## 当前历史快照解决的是什么

它解决：

```text
已经算过 $10
↓
以后管理员修改规则
↓
这笔订单仍然保持 $10
```

这个是正确的。

但没有解决：

```text
历史订单第一次迟到
↓
到底用历史时点规则还是现在规则
```

的问题。

### 判定

如果商务规则要求：

> **按订单支付时生效的分佣规则计算**

那么当前是明确 **P1 财务错误风险**。

如果商务规则明确规定：

> **按系统首次归因计算时的当前规则计算**

那现状可以接受。

这个必须明确业务口径。

---

# 13. P1：API → MANUAL 会丢掉部分 API 配置

管理端从：

```text
API
→
MANUAL
```

时，前端保存请求不会再发送：

```text
baseUrl
partnerId
mediaRootDomain
```

Service 因此构建：

```text
baseUrl = null
partnerId = null
mediaRootDomain = null
```

Mapper 更新又会无条件执行：

```text
base_url = null
media_root_domain = null
partner_id = null
```

所以：

```text
API → MANUAL
```

以后会清掉：

```text
Base URL
PID
媒体根域
```

---

## KEY 不会被删除

这一点专门核过。

虽然 Service 此时给：

```text
apiKeyCiphertext = null
```

但是 Mapper：

```xml
<if test="apiKeyCiphertext != null">
    api_key_ciphertext = ...
</if>
```

所以 KEY 会保留。

因此准确情况是：

```text
KEY            ✅ 保留

BaseURL        ❌ 清空
PID            ❌ 清空
媒体根域        ❌ 清空
```

以后：

```text
MANUAL → API
```

管理员必须重新填写这些资料。

### 判定

这不是必须的数据删除行为。

**P1。**

切换工作模式原则上应该：

```text
改变模式
≠
删除之前的 Connection 配置
```

---

# 14. P1：MANUAL → API 的存量报白任务仍然没有恢复

这个模块三已经发现，现在从配置 Owner 角度再次确认。

当前：

```text
API → MANUAL
```

会执行：

```text
stopPendingTasksByConnectionId()
```

但是：

```text
MANUAL → API
```

没有对应：

```text
恢复 PENDING
重新调度
立即 report
```

所以：

```text
MANUAL期间创建的待报白账号
↓
管理员切回API
↓
配置显示 API
↓
旧任务仍然不动
```

这是 Provider 配置和 Filing 状态之间的跨模块边界缺口。

仍定：

**P1。**

---

# 15. P2：分佣历史有数据，但后台没有历史查看能力

数据库实际已经保存：

```text
provider_commission_rule_history
```

Mapper 也有：

```text
findAllByProviderId()
```

但当前 Controller 只提供：

```text
GET 当前规则
POST 创建
PUT 更新
```

没有：

```text
GET /history
```

管理端页面也只显示：

```text
当前五项比例
```

没有：

```text
谁在什么时候
从70%改成75%
```

的历史记录界面。

底层数据没有丢，所以不是 P0。

但涉及เงินจริง的配置最好能够查看历史。

### 判定

**P2。**

不用建设复杂审计中心。

直接消费现有历史表即可。

---

# 16. 目前正确的地方

这部分不要重做。

```text
✅ Provider 与 Connection 分层正确

✅ API KEY 使用密文保存

✅ 编辑 API 配置时 KEY 留空可以保留旧 KEY

✅ Provider Connection 写操作只允许超级管理员

✅ 分佣规则写操作只允许超级管理员

✅ 五项费率 0~100 校验存在

✅ 百分比输入正确转换为 0~1 Ratio

✅ 每次创建/修改规则生成不可变历史快照

✅ 订单保存 ruleHistoryId

✅ 订单保存五项费率快照

✅ 已经计算的历史订单不会随着当前规则修改而直接漂移
```

权限尤其已经在后端 SecurityConfig 真正限制，不是只做 UI 防护。

---

# 17. Fake Integration / 错位模型

这里不是传统意义“按钮假的”，而是业务层级错位。

## 错位 1

```text
Provider
```

现在承担了：

```text
第三方平台
+
业务项目
```

两个职责。

---

## 错位 2

```text
provider_commission_rule
```

当前其实只是：

```text
平台默认分佣
```

却承担了：

```text
项目实际分佣
```

---

## 错位 3

```text
short_drama_connection.filing_mode
```

是：

```text
接入账号报白方式
```

目前同时承担：

```text
项目报白方式
```

---

## 缺失层

真正缺的是：

```text
PromotionProject｜推广项目
```

---

# 18. 最终问题等级

## P0

```text
P0-1
Project 项目领域模型完全不存在。

P0-2
CAP / CPS / CPM 没有结算类型模型。

P0-3
分佣规则只能按 Provider 配，
没有项目级分佣规则。
```

---

## P1

```text
P1-1
一个 Provider 只能一个 Connection，
不能承载同平台多个 PID / 项目。

P1-2
API / MANUAL 属于 Connection 全局配置，
不能项目级选择。

P1-3
订单第一次计算永远使用最新规则，
没有按 paidAt 选择历史有效规则。

P1-4
API → MANUAL 会清空 BaseURL / PID / 媒体根域。

P1-5
MANUAL → API 不恢复已有待处理报白任务。
```

---

## P2

```text
P2-1
分佣规则历史已经有数据，
但后台没有历史查看入口。
```

---

# 19. 最终验收判定

现在整个模块七准确状态：

```text
Provider平台管理                ✅
GoodShort API Connection       ✅
API凭证                        ✅
API/MANUAL                     ✅ 平台级
分佣规则                        ✅ 平台级
分佣历史                        ✅
超级管理员权限                   ✅

Project项目管理                 ❌
项目列表                        ❌
CAP/CPS/CPM                    ❌
项目级Provider绑定              ❌
项目级Connection               ❌
项目级报白方式                  ❌
项目级分佣                      ❌
```

因此：

> **模块七不能验收。**

而且这里不应该继续在现有 Provider 上不停塞字段。

---

# 20. 最小正确收敛方向

现有 Provider 架构不要删。

正确关系应该变成：

```text
Provider｜第三方平台
例如：
GoodShort
CapCut

        ↓

Connection｜第三方接入账号
例如：
PID / KEY / BaseURL

        ↓

Project｜卡司业务项目
例如：
短剧推广
CapCut拉新
```

项目再决定：

```text
Project
├── 项目名称
├── settlementType
│   ├── CAP
│   ├── CPS
│   └── CPM
├── Provider / Connection
├── 报白方式
│   ├── API
│   └── MANUAL
├── 状态
└── 项目结算规则
```

例如第一期只需要：

```text
项目1
名称：短剧推广
结算：CPS
Provider：GoodShort
Connection：当前 GoodShort Connection
报白：API
分佣：现有 CPS 五项规则
```

以及：

```text
项目2
名称：CapCut 拉新
结算：CAP
Provider：CapCut
报白：API / MANUAL
结算规则：按拉新数量和地区单价
```

这样才是真正的：

```text
平台
≠
接入账号
≠
业务项目
```

---

# 21. 不建议的过度设计

这一轮也不需要搞：

```text
❌ RBAC
❌ 工作流引擎
❌ 通用规则引擎
❌ Expression Engine
❌ Strategy Factory 套十层
❌ 给 CAP/CPS/CPM 建一堆空表等未来使用
```

先建立最小的：

```text
Project
+
SettlementType
+
Project → Connection
+
Project级结算配置
```

就够了。

现有：

```text
Provider
Connection
CommissionHistory
```

都可以复用。

核心不是推翻现在的系统，而是在：

```text
Provider
```

和：

```text
用户推广业务
```

之间补上目前真正缺失的：

# Project｜项目层

```


所以模块七与前面模块相比，属于**结构性未完成**：前 3–6 模块很多是“主链已有，补异常闭环”，而模块七的 **Project 层目前是 0 → 1**。这一块后续设计时最好先定清楚项目实体和三种结算类型，再让 Codex 动代码，不然继续往 `Provider` 上加字段，后面 CapCut 一接进来就会越来越乱。
```
