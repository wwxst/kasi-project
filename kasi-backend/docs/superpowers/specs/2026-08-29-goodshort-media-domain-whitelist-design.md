# GoodShort 媒体根域白名单设计

## 1. 背景与证据

GoodShort `/open/book/freeContent` 返回 `chapterName` 和视频链接 `content`。当前实现通过环境变量 `GOODSHORT_MEDIA_HOSTS` 配置全局 hostname 列表；本地未配置时，所有视频地址都会被拒绝并使同步任务以 `INVALID_MEDIA_URL` 失败。

当前官方 KOC 接口文档只把 `content` 定义为“视频链接”，示例使用 `https://xx.goodshort.com/.../xxx.m3u8`，没有承诺媒体资源固定属于 `novelopen.com`。2026-08-29 对 13 种支持语言各抽样 1 部在线短剧，共获得 116 个视频地址，当前全部使用 `v-koc.novelopen.com`。实际样本不能替代官方契约，因此不自动信任未知根域。

## 2. 目标

- 在管理后台“GoodShort API 接入”表单中，在“接口 URL”上方增加“域名白名单”字段。
- 当前 GoodShort 配置填写 `novelopen.com`。
- 允许根域本身以及符合 hostname 边界的任意层级子域，例如 `api.novelopen.com`、`v-koc.novelopen.com` 和 `a.b.novelopen.com`。
- 拒绝 `evilnovelopen.com`、`novelopen.com.evil.com` 等字符串伪装域名。
- 未配置、格式非法或不匹配时安全失败，不自动放行未知域名。
- 白名单随平台接入账号保存和编辑，不建设独立白名单管理页面。

## 3. 非目标

- 不信任整个互联网中的任意 `novelopen.com` 相似字符串。
- 不关闭现有 HTTP(S)、用户信息、端口、localhost 和内网地址校验。
- 不自动学习、追加或批准 GoodShort 新返回的未知域名。
- 不在本阶段处理调度器 UTC 与 MySQL `Asia/Shanghai` 的时间基准问题。
- 不新增通用系统配置中心、缓存或多级白名单抽象。

## 4. 数据与 API 契约

在 `short_drama_connection` 增加单值字段 `media_root_domain VARCHAR(253)`，实体、Mapper、请求 DTO 和响应 VO 使用 `mediaRootDomain`。当前项目仍处于可删除重建阶段，只修改唯一初始化 SQL 和 H2 测试 schema，不增加历史迁移脚本。

现有接口保持不变：

```text
GET /api/admin/drama/providers
PUT /api/admin/drama/providers/{providerId}/connection
```

`GET` 响应的 connection 对象增加 `mediaRootDomain`；`PUT` 请求增加同名字段。字段随现有平台连接整体保存，沿用当前权限：普通管理员可读，只有超级管理员可写。

字段接收单个根域，不接收 URL、端口、路径、通配符或逗号列表。保存前转为小写并去除首尾空白；只接受总长度不超过 253、每个 label 符合 DNS hostname 规则的 ASCII 域名。API 报备模式下该字段与接口 URL、PID 和首次 KEY 一样必填；人工报备模式保持现有可空连接配置边界。

## 5. 校验与数据流

`DramaMediaUrlValidator` 不再读取全局 `GOODSHORT_MEDIA_HOSTS`，改为由调用方传入当前短剧所属连接的 `mediaRootDomain`。

```text
GoodShort content URL
  -> 解析 URI
  -> 仅允许 http/https
  -> 拒绝用户信息和非 80/443 端口
  -> 拒绝 localhost、内网和不安全 IP 字面量
  -> hostname 小写规范化
  -> hostname == mediaRootDomain
     或 hostname 以 "." + mediaRootDomain 结尾
  -> 通过后才允许写库或返回用户端
```

免费剧集同步已经读取 `ShortDramaConnection`，直接使用该连接的 `mediaRootDomain` 校验整批地址。任一地址不符合时，本次任务继续以 `INVALID_MEDIA_URL` 终态失败，且不写入本批数据。

用户播放和下载读取已持久化地址时也必须按短剧的 `connection_id` 读取当前连接根域并再次校验。这样管理员修改根域后，旧的非匹配地址不会继续返回；下载流程通过用户短剧服务复用同一结果。管理员手动创建/更新剧集同步任务后，事务提交立即异步唤醒现有 `processDueBatch()` worker；worker 会继续消费达到批次上限后的剩余到期任务，HTTP 请求不等待全部剧集完成。`GOODSHORT_DRAMA_CONTENT_SYNC` 仍按计划兜底触发同一 worker。

## 6. 管理后台交互

在现有 `ProviderManagementPage` 的 GoodShort API 接入表单内，将字段顺序调整为：

```text
域名白名单
接口 URL
PID
KEY
启用状态
账号报备方式
```

字段标签为“域名白名单”，占位示例为 `novelopen.com`，辅助文本说明“填写允许的视频根域，根域及其正规子域均可访问，不包含协议、端口或路径”。普通管理员沿用整表只读状态；超级管理员可随连接配置一起提交。

## 7. 失败与回滚

- 根域未配置或格式非法：连接保存请求返回现有参数校验错误，不写数据库。
- GoodShort 返回未知域名：同步任务记录 `INVALID_MEDIA_URL`，不自动加入白名单。
- 已保存地址与新根域不匹配：用户端不返回该地址，管理员需确认平台域名后更新配置并重新同步。
- 回滚代码时需要重建开发数据库到回滚版本的唯一初始化 SQL；不保留兼容字段或双读逻辑。

## 8. 验证

- 初始化 SQL 和 H2 schema 均包含 `media_root_domain`，数据库结构测试通过。
- Provider DTO、Mapper、Service、Controller 覆盖保存、读取、普通管理员只读和超级管理员写入契约。
- URL 校验覆盖根域、多级子域、大小写规范化、相似恶意域名、外部域名、用户信息、非标准端口和内网地址。
- 免费剧集同步覆盖匹配成功与未知域名终态失败，失败时不写剧集数据。
- 用户播放接口覆盖匹配地址返回和配置变更后旧地址不返回。
- 管理后台覆盖字段位置、展示、提交和普通管理员只读状态。
- 使用 Java 25 运行受影响聚焦测试、编译和 `git diff --check`。

## 9. 当前与规划分离

已实施：官方文档没有提供固定媒体根域保证；真实抽样地址当前均为 `v-koc.novelopen.com`；媒体根域已按连接保存，当前 GoodShort 配置使用 `novelopen.com`，并严格校验根域及其正规子域。未知根域、相似字符串域名、内网地址、用户信息和非标准端口均拒绝；播放和下载读取时会按当前连接再次校验。原全局 `GOODSHORT_MEDIA_HOSTS` 配置已删除。

仍需单独执行：由于项目采用唯一初始化 SQL，开发数据库需在确认窗口中删除并按最新脚本重建，然后重新配置 GoodShort 接入账号；本轮不自动执行数据库破坏性操作。调度器 UTC 与 MySQL `Asia/Shanghai` 时间基准问题也不属于本设计范围。
