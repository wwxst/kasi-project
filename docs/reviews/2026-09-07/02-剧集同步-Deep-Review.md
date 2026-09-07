模块 2：剧集同步 Deep Review｜深度审查
1. 本模块结论

剧集同步：⚠️ 已真实打通主体链路，但完整业务闭环仍不成立。

甲方本模块只有 1 个接口：

POST /creek/open/book/freeContent

当前已经形成真实链路：

短剧目录同步
→ 判断需要同步剧集
→ 创建剧集同步任务
→ Worker
→ GoodShort /freeContent
→ provider_drama_content
→ 用户端剧集列表
→ HLS 播放

所以它不是“只有 Client / DTO”，也不是“任务创建后永远没人执行”。

但目前存在几个真实业务问题，尤其是：

没有主动保证 100次/min 限流
甲方返回异常/空 content 时被静默过滤，可能把部分同步当成功
甲方已经删除的剧集，本地不会删除，可能留下旧视频
用户端 refresh=true 是假的，实际上不会刷新甲方 URL
用户端把 .m3u8 直接下载并命名为 .mp4，素材下载功能实际上不成立
同步“更新数”仍然存在虚高

因此这一模块目前不能标记成 ✅。

2. 甲方接口基准

甲方定义：

POST https://api.novelopen.com/creek/open/book/freeContent

限流：

100 次 / min

请求：

pid
timestamp
sign
bookId

其中：

timestamp：毫秒
sign：HTTP Header
bookId：短剧 ID

返回每一集只有：

chapterName
content

而甲方示例中的 content 明确就是 .m3u8 视频播放地址。

3. 当前真实业务拓扑

当前代码真实执行方式是：

GoodShort 短剧目录
        ↓
DramaCatalogSyncServiceImpl.persistPage()
        ↓
新短剧
或 remoteUpdatedAt 变化
或剧集地址缺失
        ↓
DramaContentSyncService.requestAutomatic()
        ↓
provider_drama_content_sync_task
        ↓
GOODSHORT_DRAMA_CONTENT_SYNC
        ↓
ScheduledTaskScheduler
        ↓
ScheduledTaskDispatchServiceImpl
        ↓
DramaContentSyncServiceImpl.processDueBatch()
        ↓
GoodShortAdapter.fetchFreeContent()
        ↓
POST /open/book/freeContent
        ↓
DramaMediaUrlValidator
        ↓
provider_drama_content
        ↓
用户端 /free-content
        ↓
播放 / 下载

目录同步确实会在新短剧、甲方更新时间变化或者内容缺失时创建剧集任务。

4. 甲方接口覆盖矩阵
项目	状态
Client	✅
Request	✅
Response DTO	✅
Service	✅
DB Task	✅
Worker	✅
Scheduler	✅
剧集落库	✅
用户端查询	✅
用户端播放	✅
用户端刷新	❌
用户端素材下载	❌
100次/min限制	⚠️
数据完全一致性	⚠️
本模块接口覆盖率

1 / 1

仅 Client / DTO

0 / 1

完全未实现

0 / 1

5. /freeContent 请求契约

当前 GoodShortAdapter.fetchFreeContent() 实际发送：

pid
timestamp = clock.millis()
bookId

然后：

signer.sign(...)

最后：

POST /open/book/freeContent
Content-Type: application/json
Header: sign

与甲方接口一致。

URL

✅ 正确

HTTP Method

✅ POST

pid

✅ 正确

bookId

✅ 使用 externalDramaId

timestamp

✅ 毫秒

sign Header

✅ 正确

6. Signature｜签名

继续沿用统一 GoodShortSigner：

非空参数
→ 排除 sign
→ key ASCII 排序
→ key=value&...
→ &key=secret
→ MD5
→ 大写

符合甲方规则。

本模块签名

✅ 通过

7. Response DTO｜响应字段

甲方 freeContent 真正有业务价值的字段只有：

chapterName
content

当前：

GoodShortFreeContentResponse

正好接：

String chapterName;
String content;

没有发现：

类型错误
字段改错
当前业务需要却漏掉的字段

Response DTO

✅ 一致

这一块不需要为了“接全字段”再增加东西。

8. 剧集任务到底会不会执行？

这个问题现在可以明确回答：

会最终执行

但有两种路径。

管理员直接手动发起剧集同步

走：

request()
/requestBatch()
/requestAll()
        ↓
写任务
        ↓
triggerAfterCommit()
        ↓
taskExecutor
        ↓
processDueBatch()

这种属于：

✅ 提交后立即唤醒 Worker。

代码明确在事务提交后执行。

短剧目录同步自动产生剧集任务

这里不同。

走：

DramaCatalogSync
↓
requestAutomatic()
↓
taskMapper.request()

但是 requestAutomatic() 到这里就结束了。

它没有调用：

triggerAfterCommit()

所以：

目录同步完成以后，自动生成的剧集任务不会立刻被这一方法直接执行。

它依赖下面的系统任务。

9. Scheduler｜定时任务是不是摆设？

不是。

当前：

ScheduledTaskScheduler

每：

1分钟

扫描一次系统任务。

而 Dispatcher 明确存在：

case GOODSHORT_DRAMA_CONTENT_SYNC ->
    contentSyncService.processDueBatch();

因此：

目录产生剧集任务
↓
等待 GOODSHORT_DRAMA_CONTENT_SYNC
↓
Worker 消费

是真实链路。

准确结论

不是：

剧集任务创建了以后永远没人执行。

而是：

目录自动产生的剧集任务默认依赖每分钟系统调度最终执行，不是目录同步完成后立即级联执行。

如果当前产品要求只是：

最终同步

这条链没问题。

如果要求：

管理员手动同步目录以后，关联剧集也立即开始

那么目前没有完全满足。

我暂时不把这一项定成 P0/P1，因为甲方没有要求 /freeContent 必须紧跟目录接口执行，这是我们自己产品的执行语义问题。

10. Worker 本身是真实的

当前 Worker：

findDueIds()
↓
claimLease()
↓
processClaimed()
↓
fetchFreeContent()
↓
persistAndComplete()

任务确实有：

REQUESTED
RUNNING
SUCCESS
FAILED
retry
lease

而且 request 对已有非 RUNNING 任务使用 upsert，可以重新把 FAILED / SUCCESS 任务改回 REQUESTED。

所以：

✅ 失败后不是只能删任务重建。

11. Retry｜失败处理

当前第三方：

网络错误
HTTP 5xx
HTTP 429

会被识别成：

ProviderTransientException

然后剧集 Worker 重试。

默认：

maxRetries = 5

重试间隔：

1 min
5 min
15 min
30 min
60 min

超过次数以后：

FAILED
这一部分

✅ 当前有真实意义。

这不属于无意义的通用 Retry Framework，而是针对真实第三方网络/429故障。

12. P1：没有真正限制甲方 100次/min

这是这个模块的明确契约缺口。

甲方：

/freeContent 100 次/min。

当前默认：

content-sync.batch-size = 50

而且 processDueBatch() 有这一段：

如果本轮拿满50
→ taskExecutor.execute(this::processDueBatch)
→ 继续下一批

也就是说：

50
→ 50
→ 50
→ 50
...

可能连续跑。

每个剧集任务都会调用一次 /freeContent。

所以只要积压几百部短剧：

几秒/几十秒内
→ 完全可能超过100次

目前只是：

超过以后
→ 收到429
→ Retry

而不是：

调用之前
→ 确保 <=100/min
优先级

P1

最小修改方向

只针对 GoodShort 当前接口控制调用频率。

不要引入通用限流框架。

13. P1：甲方坏数据会被静默过滤，最后还可能 SUCCESS

这是一个比较隐蔽的问题。

当前 GoodShortAdapter：

response.getData().stream()
    .filter(item ->
        item != null
        && item.getContent() != null
        && !item.getContent().isBlank())

也就是说：

假设甲方返回：

Chapter 1 → 正常URL
Chapter 2 → ""
Chapter 3 → 正常URL

代码会变成：

Chapter 1
Chapter 3

然后继续往下走。

并不会告诉 Worker：

Chapter 2 数据异常。

之后 Service 只验证剩下来的 URL，然后：

persist
→ markSuccess

结果

可能出现：

甲方实际返回了不完整剧集数据，但我们的同步任务显示 SUCCESS。

这正是本次 Review 要找的：

Fake Success｜假成功。

正确行为

既然甲方契约规定：

content = 视频链接

本次完整返回中只要有非法剧集数据，就不能偷偷丢掉然后宣称整次同步成功。

优先级

P1

14. P1：甲方已经删除的旧剧集，本地不会删除

当前保存算法是：

查询本地全部内容
↓
甲方本次返回内容
↓
按 sequenceNo upsert

但是整个 persistAndComplete()：

没有删除甲方本次已经不存在的 sequence。

例如第一次：

甲方：
1
2
3
4
5

本地：

1
2
3
4
5

后来甲方调整免费内容，只返回：

1
2
3

当前代码同步以后，本地仍然是：

1
2
3
4 ← 旧
5 ← 旧

因为只做：

INSERT / UPDATE

没有处理“不再存在”。

更明显的情况：

甲方 data = []

当前可以把：

totalFetched = 0

标成 SUCCESS，

但是以前保存的所有内容仍然留在数据库。

用户端又会把旧数据继续返回

UserPromotionDramaServiceImpl：

dramaMapper.findContents(id)

把数据库现有内容全部返回。

因此这是：

甲方已经没有
↓
数据库还有
↓
用户还能看到
优先级

P1

这是实际数据一致性问题。

最小修改

成功拿到一部短剧的完整 freeContent 后：

同一事务中删除该 drama 本次远端结果中已经不存在的旧 sequence。

不用引入版本表、历史表、软删除框架。

15. 剧集序号怎么来的？

甲方没有：

episodeId
sequenceNo
chapterId

只有：

chapterName
content

所以当前系统自己解析：

Chapter 12
→ 12

如果尾部没有数字：

按甲方返回顺序
→ 1 / 2 / 3...

在甲方当前契约下，这是一个合理的最小方案。

这一项

✅ 暂时没有必要增加复杂 Episode Mapping。

16. URL 安全校验

当前实际 master 上的 DramaMediaUrlValidator 是：

isAllowed(url, mediaRootDomain)

会校验：

http/https
host
端口
localhost / 内网 literal
host 是否等于配置根域名
或是否是其子域名

所以前面检索时出现的单参数版本属于抓取缓存不一致；GitHub 当前 blob 页显示的是两参数版本。

这一块

没有发现代码当前直接编译不通过的问题。

同时，它不是单纯依赖文件扩展名，所以 .m3u8 本身不会因为格式而被拒绝。

17. 用户端剧集播放：是真的

用户端用：

Hls.js

读取后端提供的 playUrl。

原生支持 HLS 的浏览器直接：

video.src = source

否则：

Hls.loadSource(source)

而后端：

provider_drama_content.content_url
↓
playUrl

因此：

HLS 播放链路

✅ 是真实实现。

不是把 m3u8 当普通 MP4 播放。

18. P1：refresh=true 是 Fake Integration

这是本模块非常明确的一处“假打通”。

Controller 提供：

GET /api/user/promotion/dramas/{id}/free-content
?refresh=true

而且把：

refresh

真实传给 Service。

前端也真实使用它。

HLS 播放发生 fatal error 后：

getPublishedDramaFreeContent(id, true)

希望拿一个更新后的资源地址。

但是后端：

getFreeContent(Long id, boolean refresh)

方法里面：

完全没有使用 refresh。

它只是：

findContents(id)
↓
返回数据库旧 URL

于是实际行为：

视频地址失效
↓
前端 refresh=true
↓
后端完全不刷新甲方
↓
返回同一个URL
↓
前端发现URL没变
↓
仍然播放失败
状态

⚠️ Fake Integration

优先级

P1

最小修改有两个选择

如果业务现在不需要用户实时刷新：

删除 refresh 参数和前端假的刷新逻辑。

如果业务确实需要：

refresh=true 时真实触发该 drama 的 /freeContent 同步，再返回最新 DB 数据。

只能二选一。

不要保留现在这种“参数存在但不起作用”。

19. P1：素材下载功能实际上是错的

这个问题非常明确。

甲方示例给的是：

xxx.m3u8

后端给用户：

playUrl(url)
downloadUrl(url)

也就是说：

播放 URL 和下载 URL 是完全同一个地址。

前端点击“下载”以后：

fetch(url)
→ response.blob()
→ <a download="第01集.mp4">

这不会：

m3u8
↓
下载ts/fmp4分片
↓
合并
↓
转成MP4

它只是：

下载 m3u8 请求本身的响应
↓
强行把文件名写成 .mp4

所以如果 CDN 返回的是正常 HLS playlist：

最终得到的其实可能是：

一个 m3u8 播放清单，被命名成了 .mp4。

并不是真正的 MP4 视频。

这正好印证之前我们讨论过的下载冲突

现在用户端页面明确写着：

剧集观看与素材下载

还有：

下载
下载全部

所以不能说：

“只是一个没用的小按钮。”

它是明确提供给用户的业务能力。

当前状态

❌ 素材下载没有真正打通。

优先级

P1

20. 怎么解决下载，不需要重新造下载中心

这里后续第二阶段要遵循你的最小实现原则。

现在不需要恢复以前那套庞大的：

下载任务
下载中心
进度中心
长期任务状态

但如果产品要求：

用户点击“下载”得到真正的 .mp4

那么必须存在一个真正完成：

m3u8
→ 分片
→ MP4

的地方。

可以是非常小的一条后端下载转换接口。

不能继续：

前端 fetch(m3u8)
→ 改后缀.mp4

这种假下载。

21. P1：剧集同步“更新数”也不准确

这个模块与目录同步存在类似问题。

当前：

ProviderDramaContent old = existing.get(sequence);

if (old != null) {
    updated++;
}

意思实际上是：

以前存在这一集
=
本次更新1条

哪怕：

chapterName没变
content URL没变

仍然：

updated + 1

因此管理端看到的：

新增
更新
总处理

中，“更新”不等于：

真正发生数据变化。

优先级

P1

和模块1的统计问题性质相同。

22. 管理端是不是假的？

不是。

当前已经存在真实的：

单部剧集同步
批量剧集同步
全部在线短剧同步
仅补齐缺失视频
状态查询
同步记录

并且手动任务最终真实进入 DramaContentSyncService。

所以：

✅ 管理端剧集同步能力是真实消费者。

不是只有页面没后端。

但前面的 updated_count 会让同步统计失真。

23. missingOnly 是否有真实意义

有。

Mapper 明确会找：

没有任何 provider_drama_content

或者：

存在剧集但是 content_url 为空

的短剧。

所以“仅补齐缺失视频地址”并不是假配置。

这一项

✅ 有真实业务消费者。

24. Fake Integration｜本模块专项清单
真正已经打通
链路	结果
/freeContent Client	✅
请求参数	✅
签名	✅
Response DTO	✅
目录自动创建剧集任务	✅
系统 Scheduler	✅
Worker	✅
Retry	✅
数据落库	✅
用户端读取	✅
HLS 播放	✅
管理端手动同步	✅
假打通 / 部分打通
能力	实际情况
refresh=true	❌ 参数存在，但完全没刷新
素材下载	❌ m3u8 被直接命名为 mp4
同步完整性	⚠️ 远端删除，本地不会删除
异常数据处理	⚠️ blank content 被静默丢弃后仍可能 SUCCESS
100次/min	⚠️ 429会Retry，但没有主动限速
更新数	⚠️ existing 就统计为 updated
25. 数据库 Review｜本模块
provider_drama_content

✅ 有真实写入

✅ 有真实查询

✅ 用户端真实消费

不能删。

provider_drama_content_sync_task

✅ Worker真实使用

✅ Scheduler真实使用

✅ 管理端真实读取

不能删。

当前数据库真正的问题

不是多余表，而是：

远端删除无法同步到本地

当前只有：

upsert

缺少：

删除本次远端已经不存在的旧内容

这是数据一致性问题。

26. 本模块优先级
优先级	问题	关键位置
P1	/freeContent 无100次/min主动限制	DramaContentSyncServiceImpl.processDueBatch()
P1	blank content 被静默过滤，可能假成功	GoodShortAdapter.fetchFreeContent()
P1	远端已删除剧集，本地残留	persistAndComplete() / ProviderDramaMapper
P1	refresh=true 完全没生效	UserPromotionDramaServiceImpl.getFreeContent()
P1	.m3u8 假装 .mp4 下载	DramaPage.tsx + DramaContentResourceVO
P1	updatedCount 把“已存在”当“更新”	persistAndComplete()
P0

0

目前没有证据证明 /freeContent 主调用链完全不可运行或整个剧集表无法生成。

P1

6

27. 自动执行语义单独说明

还有一个需要我们后续决定，但我现在不强行算 Bug：

目录同步
→ requestAutomatic
→ 不立即唤醒
→ 等每分钟定时任务

它能够：

最终执行。

所以从“会不会永久 PENDING”的角度：

✅ 默认环境下不会。

但如果我们的产品要求是：

管理员手动同步目录以后，刚同步出来的短剧立即继续同步剧集。

那么后续可以非常小地改成：

目录事务成功
→ 若本次生成content task
→ afterCommit唤醒一次content worker

不需要增加新 Scheduler、新队列或者新状态机。

28. 本模块量化
甲方 API

甲方本模块接口：

1

当前 Client 实现：

1

真实 Service 调用：

1

真实 Worker 调用：

1

真实落库：

1

真实前端消费者：

1

完全未实现：

0

仅 Client / DTO：

0

接口覆盖率

1 / 1

核心请求/响应契约

URL / Method / Request / Response / Sign：

✅ 基本一致

但是限流契约未真正保证，因此不能算严格完整通过。

完整业务闭环覆盖率

按本次 Deep Review 严格口径：

0 / 1

因为当前虽然：

GoodShort → DB → 用户播放

已经打通，

但明确提供给用户的：

URL刷新
素材下载

并没有真正打通，同时本地可能残留甲方已删除内容。

29. 最终直接回答

剧集接口实现了吗？

实现了，1 / 1。

是不是只有 Client？

不是。

短剧目录同步完成后会不会创建剧集任务？

会。

创建以后最终会不会执行？

默认 Scheduler 开启时，会。

是不是立即执行？

管理员直接手动剧集同步会立即唤醒；目录自动生成的剧集任务不会立即唤醒，主要等待每分钟 Scheduler。

Worker是真实的吗？

是。

失败会怎么办？

网络、5xx、429 等临时错误最多按配置重试5次，最终进入 FAILED；之后可以重新提交。

剧集真的保存了吗？

会保存到 provider_drama_content。

用户真的能看到吗？

能。

m3u8 能播放吗？

用户端通过 Hls.js，播放链路是正常设计。

m3u8 能按现在的代码下载成 MP4 吗？

不能。现在只是下载 m3u8 响应并把文件名改成 .mp4。

refresh=true 会重新向甲方拉 URL 吗？

不会。当前是假参数。

甲方删除一集以后，本地会跟着删除吗？

不会。当前会残留。

是否遵守100次/min？

无法保证。当前只处理429，没有主动限速。

模块 2 最终判定

剧集同步：⚠️ 部分完成

/freeContent 已完整进入真实生产调用链，任务、Scheduler、Worker、第三方请求、数据库、管理端、用户端和 HLS 播放均存在真实消费者，因此不是 Fake Integration。

但目前存在 6 个 P1：限流没有保证、异常剧集可被静默过滤后假成功、远端删除不会同步删除本地旧数据、refresh=true 无效、m3u8 素材下载是假 MP4、更新数统计失真。

所以这一模块不能按完整业务闭环验收。

下一模块按我们定好的顺序是 模块 3：媒体账号报白 / Filing，会只审：

用户提交 → DB → /filing/report → afterCommit → /filing/query → 状态映射 → 修改后重报 → 用户端 → 管理端旧人工流程。
