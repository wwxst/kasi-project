模块 1：短剧目录同步 Deep Review｜深度审查
1. 模块结论

结论：⚠️ 部分打通，不能判定为完整闭环。

甲方本模块 2 个接口：

POST /open/book/initBooks
POST /open/book/incrementBooks

当前代码中 2 / 2 都已经真实实现并进入业务链路，不是只写了 Client / DTO。

但是目前存在 1 个 P0 + 5 个 P1，其中最明确的问题是：

当前默认 pageSize = 100，而甲方明确规定最大 pageSize = 50。

因此即使两个接口都有真实调用，也不能判定为“契约完全一致”。

2. 当前真实业务拓扑

当前短剧目录同步真实执行链可以画成：

                    ┌────────────────────┐
                    │     管理端手动同步    │
                    └─────────┬──────────┘
                              ↓
                  Admin Drama Controller
                              ↓
                DramaCatalogSyncServiceImpl
                              ↓
                 创建 / 更新 Checkpoint
                              ↓
                     事务提交 afterCommit
                              ↓
                    processDueBatch()
                              ↓
                ProviderSyncCheckpointMapper
                              ↓
                     claim / RUNNING
                              ↓
                      GoodShortAdapter
                    ↙                 ↘
          /book/initBooks      /book/incrementBooks
                    ↘                 ↙
                       GoodShort
                              ↓
                   DramaCatalogRecord
                              ↓
                     ProviderDrama 落库
                              ↓
              更新 checkpoint / 同步统计
                    ↙                 ↘
              管理端同步记录        用户端短剧列表

另外还有两条调度入口：

DramaCatalogScheduler
每 5 分钟 fixedDelay
        ↓
processDueBatch()

以及：

系统定时任务
GOODSHORT_DRAMA_INCREMENTAL_SYNC
        ↓
requestScheduledIncremental()
        ↓
Checkpoint
        ↓
processDueBatch()

所以这一模块并不存在“Client 写了但没人调用”的情况。Scheduler、Worker、Checkpoint、数据库和前端消费者都是实际存在的。

3. 甲方接口覆盖矩阵
接口	Client	Service	DB	Scheduler	前端消费者	契约一致	状态
/open/book/initBooks	✅	✅	✅	✅	✅	❌	⚠️ 部分完整
/open/book/incrementBooks	✅	✅	✅	✅	✅	❌	⚠️ 部分完整
本模块接口覆盖率

2 / 2

Client / DTO Only｜只有壳

0 / 2

Missing API｜完全缺失

0 / 2

严格契约完全一致

0 / 2

原因不是 URL 或签名错误，而是这两个接口共同受到：

pageSize=100 > 50
showStatus DTO 类型不一致
100次/min 没有主动限流

影响。

4. 甲方契约逐项核查
4.1 URL / Method

甲方：

POST /creek/open/book/initBooks
POST /creek/open/book/incrementBooks

当前 GoodShortAdapter 使用：

/open/book/initBooks
/open/book/incrementBooks

配合 GoodShort 的 /creek Base URL 使用。

这一项：✅ 正确。

不是旧接口，也没有发现短剧目录同步仍然调用另一套历史 URL。

4.2 timestamp

甲方要求：

毫秒级时间戳。

当前代码使用：

clock.millis()

这一项：✅ 正确。

5. Signature｜签名专项

当前 GoodShortSigner 的逻辑是：

排除 sign
排除 null / 空值
参数按 key 排序
拼接 key=value&...
最后追加 &key=secret
MD5
转大写
UTF-8

并且 GoodShortAdapter 最终把：

sign

放进 HTTP Header。

这与甲方当前文档规则一致。

结论

签名：✅ 通过

目前没有证据证明短剧目录接口的签名实现存在问题。

6. P0：pageSize 明确违反甲方契约

这是这个模块目前最严重的问题。

甲方明确规定：

pageSize
最大 50

但当前：

DramaSyncProperties.java

默认：

pageSize = 100

而这个值不是一个没用的配置。

真实调用是：

DramaSyncProperties.pageSize
        ↓
DramaCatalogSyncServiceImpl.fetchPages()
        ↓
GoodShortAdapter.fetchCatalog()
        ↓
request.pageSize
        ↓
甲方 API

也就是说：

现在真实业务代码确实可能把 pageSize=100 发给甲方。

并不是“配置里写了100，但调用时又截成50”。

当前行为
请求最多 100 条
甲方允许
最多 50 条
正确行为
pageSize <= 50
优先级

P0

因为这是明确的第三方 API 契约错误，有可能直接导致主同步请求失败。

后续最小修改

进入第二阶段时不能只改：

pageSize = 50

还要检查数据库已经存在的 checkpoint。

因为 checkpoint 本身会保存：

page_size

已经生成的 100 数据不能因为 Java 默认值改成 50 就自动消失。

应最小处理：

Java默认值 → 50
Schema默认值 → 50
已有 checkpoint page_size > 50 → 修正为50

不需要增加额外配置框架。

7. Response DTO｜响应契约

大部分字段已经跟上甲方最新版本。

当前已经接收：

bookId
bookName
bookNameZh
bookCover
labelNames
introduce
typeTwoName
language
rank
showStatus
novelType
novelSubType
ctime
utime

也就是说：

v1.0.4

bookNameZh

✅ 已接。

v1.0.5

novelType

novelSubType

✅ 已接。

而且这些新字段并不是只停在 DTO，后续实际进入实体并保存。

8. P1：showStatus 类型与甲方不一致

甲方定义：

showStatus: int

0 = 不展示
1 = 展示

但是当前：

GoodShortBookData

定义成了：

String showStatus

后续内部业务也是按照：

"1"

这样的字符串判断。

现在之所以可能正常，是 JSON 反序列化存在数字 → String 的转换能力。

但这不等于契约正确。

当前
甲方 int
↓
Java String
↓
数据库 String
↓
业务判断 "1"
正确边界

应该至少在第三方 DTO 层忠实表达：

Integer showStatus

然后如果内部数据库确实需要字符串，再明确转换。

而不是依赖 JSON 自动强制转换。

优先级

P1

不是当前已经证明的数据事故，但属于明确接口契约不一致。

9. 13 个语言专项检查

当前默认配置确实包含甲方全部 13 种语言：

ENGLISH
SPANISH
PORTUGUESE
DEUTSCH
FRENCH
BAHASA_INDONESIA
KOREAN
ARAB
THAI
JAPANESE
TRADITIONAL_CHINESE
POLISH
TURKISH

所以之前担心的：

“代码是不是只配置了10个语言？”

不是。13 个都配置了。

10. language 是否进行了没必要的转换

这一点目前是正确的。

从甲方返回：

language

进入 DramaCatalogSyncServiceImpl.toEntity() 后，当前直接保存甲方语言值。

也就是说：

ENGLISH
SPANISH
...

并没有再做：

ENGLISH → en → English → ENGLISH

这样的多余转换。

结论

✅ 当前短剧数据的 language 保存方式符合我们之前确定的原则。

11. Full → Incremental｜全量到增量

当前逻辑符合甲方基本设计。

甲方：

第一次
→ initBooks

之后
→ 根据 utime 做 incrementBooks

当前：

没有 successful FULL baseline
→ FULL

已经存在 successful FULL
→ INCREMENTAL

定时增量同步同样会检查是否存在成功的 FULL baseline。

没有初始化成功的语言，不会凭空直接跑增量。

这一项

✅ 正确。

12. Checkpoint｜同步游标

Checkpoint 不是假表。

真实执行链包含：

REQUESTED
   ↓
findDue()
   ↓
claimLease()
   ↓
RUNNING
   ↓
分页请求
   ↓
updateProgress()
   ↓
SUCCESS

失败任务也有实际的查找/重新执行条件。

所以：

Checkpoint 当前确实参与控制同步过程。

并不存在：

“数据库有 checkpoint 表，但整个业务没人使用。”

这种 Fake Integration。

13. Scheduler｜Worker 是否真的注册
DramaCatalogScheduler

真实存在：

@Component
@ConditionalOnProperty(...)
@Scheduled(fixedDelayString = "...5m")

最终调用：

syncService.processDueBatch()

默认：

schedulerEnabled = true
fixedDelay = 5m

所以：

✅ Scheduler 真正注册。

✅ Worker 真正有消费者。

✅ 默认每 5 分钟会兜底处理 Due Task。

14. 时区问题

这个模块的 Worker 用的是：

fixedDelay = 5m

而不是：

每天08:00

这种绝对时间 Cron。

因此之前出现过的：

UTC
vs
Asia/Shanghai

并不是当前 DramaCatalogScheduler 5分钟 Worker 的核心问题。

结论

这里没有发现新的“8小时时差导致任务永远未到执行时间”的问题。

不能把以前其他调度链路出现的问题机械套到这个模块。

15. P1：手动同步并没有真正做到“全部立即执行”

这个问题已经可以明确证明。

当前默认：

语言数 = 13
batchSize = 10

用户在管理端手动发起全部语言同步后：

requestSync()
   ↓
创建13个语言任务
   ↓
afterCommit
   ↓
只调用一次 processDueBatch()
   ↓
findDue(... LIMIT 10)

因此：

一次手动提交13个语言，第一次最多立即拿10个任务。

剩下 3 个：

等待后续 scheduler

默认最迟进入下一轮 5 分钟 Worker。

甚至还有一个细节：

findDue() 查的是全局到期任务。

如果队列里本来就存在其他旧任务，那第一次拿到的10个里不一定全部都是这次手动提交的任务。

所以实际可能：

本次13个
立即执行 < 10
这和当前业务要求不一致

我们之前已经明确：

定时任务按调度时间执行；但是管理员主动点击“手动同步”，就应该开始执行这次手动同步。

目前实现其实是：

手动提交任务 + 顺手唤醒一次全局 Worker。

这两者不是完全一回事。

优先级

P1

后续最小修改方向

不要为了它上：

通用 Queue
Workflow
Event Bus
新任务框架

只需要让：

手动同步
→ 优先处理此次创建的任务

即可。

16. 默认情况下会不会永远 PENDING？

正常默认配置下：

不会因为13 > 10就永久 PENDING。

因为还有：

DramaCatalogScheduler
→ 每5分钟 processDueBatch()

剩下的任务最终还会被取走。

但是：

如果 Scheduler 被禁用

一次手动创建：

13个

afterCommit 只跑一轮：

10个

那么剩余任务没有新的唤醒来源时，就可能继续处于 REQUESTED，直到另一次事件调用 Worker。

所以准确说法是：

默认 Scheduler 开启时不会因为 batchSize=10 永久卡死，但手动同步的“立即执行语义”没有真正完成。

17. P1：100次/min 限流没有真正实现

甲方对：

initBooks
incrementBooks

都明确限制：

100 次 / min。

现在 fetchPages() 是：

page 1
→ HTTP

page 2
→ HTTP

page 3
→ HTTP

...

连续执行。

目前看到的 GoodShort Adapter 对 HTTP 429 有异常处理，但：

收到 429 再报错 ≠ 主动遵守 100次/min。

也就是说，现在代码只有：

超了
→ 甲方返回429
→ 我们识别为第三方临时异常

没有：

主动控制每分钟请求数 <= 100

的机制。

尤其：

13语言 × 每语言多页

完全存在一分钟累计超过100次的可能。

准确结论

不是：

“当前一定已经超过100次/min。”

而是：

当前实现无法保证满足甲方100次/min契约。

优先级

P1

以后只需要针对 GoodShort 当前真实接口做最小限速。

不需要造一个通用 Rate Limit Framework｜限流框架。

18. 数据落库是否真实

是。

甲方数据进入：

GoodShortBookData
→ DramaCatalogRecord
→ ProviderDrama

当前真正保存：

中文名
标题
封面
标签
简介
类型
language
rank
remoteShowStatus
novelType
novelSubType
ctime / utime 等同步数据

所以不存在：

“DTO 收到了，但是没有保存。”

这类 Fake Integration。

19. P1：新增数正确，但更新数不真实

这是管理端当前一个比较明确的数据口径 Bug。

当前 persistPage() 大致逻辑：

if (existing == null) {
    inserted++;
} else {
    updated++;
}

问题在于：

只要数据库里已经存在，就直接算“更新1条”。

没有先证明数据真的发生了变化。

比如：

甲方今天返回：

bookId = 123
title = A
utime = 原来的时间
showStatus = 1

数据库完全一样。

当前仍可能统计：

更新数 + 1
但另外一部分代码其实知道有没有变化

当前是否创建后续剧集同步任务，会进一步判断：

是不是新短剧
remoteUpdatedAt 有没有变化
是否需要重新同步内容

这说明：

业务本身已经有“真的变化”和“只是重复拉到”两个概念。

但统计 updatedCount 没有按这个口径做。

管理端真的展示这个错误数字

管理端同步记录页面显示：

新增数
更新数
总处理数

其中：

updatedCount

就是后端这套统计直接返回的。

所以这不是没人看的内部字段。

当前
数据库存在
=
更新
正确
本次甲方数据导致真实字段变化
=
更新
优先级

P1

这会让管理端同步结果具有误导性。

20. 管理端消费者

管理端这部分不是假页面。

当前真实存在：

POST 同步
GET 同步状态
GET 同步记录
GET 详情

页面也真实消费：

状态
创建时间
同步类型
新增数
更新数
总处理数
操作

因此：

✅ 管理端确实消费短剧同步数据。

但是前面说的 updatedCount 会导致页面展示的“更新数”不准确。

所以：

页面是真的，数据统计有问题。

21. 用户端消费者

用户端也不是假链路。

后端：

UserPromotionDramaServiceImpl

会真正从短剧表查询：

已经发布
+
符合 language

的数据。

详情还会检查：

localStatus == PUBLISHED
remoteShowStatus == "1"

也就是说甲方目录同步下来的 showStatus 最终确实参与用户能不能看到短剧的判断。

所以业务链：

GoodShort
↓
短剧目录
↓
数据库
↓
发布状态
↓
用户短剧列表

确实存在。

22. P1：用户端标题搜索是假完整

这里发现一个典型的 Partial Integration｜部分打通。

用户前端请求会发送：

title
language
page
size

但是后端：

UserPromotionDramaServiceImpl.getPublished()

实际上只使用：

language

没有把 title 参与数据库查询。

然后前端又做：

后端先分页
↓
拿当前页
↓
前端根据 title 再过滤

这会产生错误。

例如有100部短剧：

第一页：1~20
第二页：21~40
...

用户搜索：

“霸道总裁”

目标短剧在第4页。

后端先返回第一页20条：

前端再搜索
→ 0条

用户看到：

没有结果

实际上数据库里有。

并且：

total

还是后端没有按 title 筛选的总数。

正确链路应该是
title
↓
后端
↓
SQL WHERE title ...
↓
分页
↓
返回当前搜索结果

而不是：

先分页
↓
再过滤当前页
优先级

P1

最小修改

让：

pagePublished(title, language...)
countPublished(title, language...)

在 SQL 层完成过滤。

然后去掉前端重复的分页后标题过滤。

23. Fake Integration｜假打通专项结论
这些看起来可能像假的，但实际上是真的
能力	结论
initBooks Client	✅ 有真实调用
incrementBooks Client	✅ 有真实调用
afterCommit	✅ 真正注册并触发 Worker
Checkpoint	✅ 真正参与任务推进
Scheduler	✅ 真正注册
Worker	✅ 真正消费任务
数据落库	✅ 真正保存
管理端同步记录	✅ 真正消费
用户短剧列表	✅ 真正消费
13语言	✅ 全部配置
language 原样存储	✅
v1.0.4 / v1.0.5 字段	✅ 已进入真实链路

所以不能把这个模块描述成：

“代码有了，但整个同步根本没跑起来。”

这个结论不准确。

真正的 Partial / Fake 部分
1. 手动同步

页面看起来是：

立即同步

实际：

提交任务
+
只唤醒一次最多10条的全局Worker

⚠️ 语义没有完全实现。

2. 更新数

页面显示：

更新数

实际统计更接近：

已存在记录数

⚠️ 数据语义是假完整。

3. 用户标题搜索

页面有搜索功能，参数也发给后端。

但：

后端没使用title

⚠️ 典型 Fake Integration。

4. 限流

代码处理了429。

但是没有真正保证：

<=100次/min

⚠️ 只有失败响应处理，不等于契约限流。

24. Database Review｜本模块数据库问题

本轮只说短剧目录同步相关。

① page_size = 100

P0

已经违反甲方最大50条要求。

而且 checkpoint 会持久化 pageSize，所以不是简单改一个 Java 默认值就结束。

② updated_count 业务口径错误

字段本身有消费者，不应该删除。

问题是写入语义错误：

当前：existing → updated
正确：changed → updated

属于修逻辑，不是删字段。

③ Scheduled Incremental 空 Display Run

目前还有一个较低优先级问题。

requestScheduledIncremental() 会先创建：

display run
content child run

然后才检查每个语言是否：

已有 active checkpoint
有没有 FULL baseline
是否真的需要创建任务

如果最终所有语言全部被跳过：

可能留下一个没有 item 的 display run

而管理端查询又要求存在 item 才展示。

结果就是：

DB 有
前端永远看不到

这个目前没有造成主业务失败。

优先级

P2

后续最小方案：

真正出现第一条需要执行的语言任务以后，再创建 display run。

不需要专门造“孤儿任务清理框架”。

25. 删除候选

本模块目前：

没有充分证据可以直接删除
GoodShortAdapter ❌不能删
Catalog Service ❌不能删
Checkpoint Mapper ❌不能删
Scheduler ❌不能删
Catalog DTO ❌不能删
管理端同步页面 ❌不能删

这些都有真实消费者。

可以作为后续清理候选

仅：

没有任何 item 的 Scheduled Display Run 产生逻辑

但这属于改变创建时机，不是简单删除一张表。

26. 本模块问题优先级清单
优先级	问题	位置	当前行为	正确行为
P0	pageSize=100 超甲方最大50	DramaSyncProperties、DramaCatalogSyncServiceImpl.fetchPages、Checkpoint	请求100	≤50
P1	手动13语言不能全部立即启动	requestSync、triggerAfterCommit、processDueBatch、findDue	一次最多10	本次手动任务立即进入执行
P1	没主动遵守100次/min	fetchPages、GoodShortAdapter	连续请求，429后才失败	请求前控制≤100/min
P1	showStatus 类型错误	GoodShortBookData	String	int / Integer
P1	更新数虚高	persistPage	existing=updated	真正变化才updated
P1	用户title搜索只过滤当前页	UserPromotionDramaServiceImpl、Mapper、DramaPage.tsx	先分页再前端过滤	DB过滤后分页
P2	空 Scheduled Display Run	requestScheduledIncremental	先建run，可能0 item	有任务才创建
27. 本模块量化结果
甲方接口

本模块接口总数：

2

完整存在 Client：

2

真实调用：

2

真实落库：

2

有管理端/用户端最终消费者：

2

完全未实现：

0

仅 Client / DTO：

0

接口覆盖率

2 / 2

严格契约通过率

0 / 2

主要因为两个目录接口共用：

pageSize=100

同时共用存在 showStatus DTO 类型不一致与限流缺口的实现。

本模块完整业务闭环

按严格验收：

0 / 1

即：

⚠️ 短剧目录同步已经形成真实业务链，但当前还不能验收为完整。

问题数量

P0：1

P1：5

P2：1

28. 你最关心的几个问题，直接回答

短剧同步到底有没有真正跑通？

有真实链路，已经跑通主体，但还不能验收为完整。

全量和增量两个甲方接口都有吗？

有，2 / 2。

是不是只写了 Client？

不是，Service、DB、Checkpoint、Scheduler、管理端、用户端都有真实消费者。

13个语言齐不齐？

齐，13个全部存在。

13个语言手动同步会不会全部立即执行？

不会。当前一次 Worker 最多取10个，剩余语言等后续 Worker。

剩余3个会不会永远卡死？

默认 Scheduler 开启时不会，5分钟 Worker 会继续处理；但这依旧不符合手动“立即同步”的语义。

Scheduler 是真的还是摆设？

真的，已注册并真实调用 processDueBatch()。

这个模块还有UTC差8小时问题吗？

当前目录 Worker 使用 fixedDelay，这里没有发现该时区问题。

checkpoint 有用吗？

有，是真实任务执行核心，不是历史空表。

language 有没有乱转换？

没有，当前核心落库保持甲方语言码。

新增、更新、总处理数准不准？

新增基本有真实语义；更新数不准，当前把“数据库已经存在”直接统计成更新。

用户真的能看到同步后的短剧吗？

能。

用户搜索短剧正常吗？

不完全正常。title 没进入后端 SQL，是当前页前端过滤。

当前最先必须修什么？

第一项就是：

把 pageSize 从100修到不超过50，并处理已经持久化为100的 checkpoint。

这属于明确甲方契约错误，应先于其他体验或代码精简问题处理。

模块 1 最终判定

短剧目录同步：⚠️ 部分完成

两个甲方目录接口已经全部真实接入，完整经过 Client → Service → Checkpoint → Worker → DB，并存在管理端和用户端真实消费者，不属于“接口写了但没跑”的 Fake Integration。

但当前 pageSize=100 明确违反甲方最大50的契约；手动13语言同步一次最多立即处理10个；100次/min没有主动约束；showStatus DTO类型不一致；更新数统计失真；用户标题搜索没有真正进入后端查询。

因此本模块现在不能标记 ✅ 完整闭环。

模块 1 到这里结束，不混入剧集同步。下一模块是「模块 2：剧集同步」，重点就查：目录同步 → 创建剧集任务 → Worker → /freeContent → m3u8 → DB → 用户端，到底是不是完整闭环。
