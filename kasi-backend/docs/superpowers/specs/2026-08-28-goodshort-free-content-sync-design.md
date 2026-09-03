# GoodShort 免费剧集自动同步设计

日期：2026-08-28

状态：已实施

## 1. 当前问题

当前目录同步调用 GoodShort `initBooks` 和 `incrementBooks`。根据仓库内的真实接口文档，这两个接口只返回短剧目录字段，不返回剧集列表。

当前适配器的测试响应人为加入了 `episodes` 字段，因此测试可以验证剧集映射；真实同步时 `episodes` 为空，最终只写入 `provider_drama`，不会产生 `provider_drama_content` 记录。

GoodShort 当前可用的剧集相关接口是：

```text
POST /open/book/freeContent
请求：pid、timestamp、bookId
返回：chapterName、content
```

GoodShort 文档将 `content` 定义为视频链接，没有说明 URL 的有效期。本设计按当前业务决定将该 URL 作为可长期使用的地址持久化；后续自动同步或手动补同步拿到新值时直接覆盖旧值。

因此本阶段只实现免费剧集和对应视频 URL 同步。收费剧集没有可用的剧集列表接口，不同步、不伪造收费剧集记录。

## 2. 目标与非目标

### 2.1 目标

- 目录全量同步后，自动为新增短剧排队同步免费剧集。
- 目录增量同步后，自动为新增或发生变化的短剧排队同步免费剧集。
- 提供管理员按单部短剧手动触发或重试的接口。
- 提供管理员按勾选短剧批量触发和按平台一键同步全部短剧的接口。
- 将 GoodShort 免费内容 URL 持久化，用户播放和下载直接读取本地最新地址。
- 复用统一的任务租约、状态、错误和重试语义。
- 同步过程不阻塞目录分页请求，且遵守 GoodShort 的调用限流。

### 2.2 非目标

- 不同步收费剧集。
- 不在用户请求中隐式写入剧集数据。
- 不改变现有短剧上下架、推广元数据和目录检查点语义。

## 3. 推荐方案

目录同步和剧集同步拆成两个阶段：

```text
目录同步
  -> 保存短剧
  -> 发现新短剧、目录有更新或本地没有剧集
  -> 写入剧集同步任务

剧集同步任务
  -> 领取任务租约
  -> 调用 GoodShort /open/book/freeContent
  -> 校验并短事务 upsert 免费剧集和视频 URL
  -> 更新任务成功、失败或下次重试时间
```

不在目录同步的数据库事务中调用 GoodShort。目录事务只写短剧和待处理任务，外部请求由独立 worker 执行。

## 4. 自动触发规则

目录分页处理每条短剧记录时，满足任一条件就创建或合并一条剧集同步任务：

- 该短剧是首次插入。
- 远端 `remote_updated_at` 比本地记录更新。
- 本地该短剧没有任何 `provider_drama_content` 记录，用于修复当前已经同步但没有剧集的历史数据。

同一部短剧只保留一条未完成任务。目录全量同步不会直接发起大量远端请求，而是把任务放入队列后分批执行；增量同步只为本次返回的短剧创建任务。

自动任务接入现有 `system_scheduled_task` 和每分钟调度器，增加固定任务编码：

```text
GOODSHORT_DRAMA_CONTENT_SYNC
```

该固定任务只负责调用剧集任务 worker 扫描到期任务，不复制 GoodShort 请求代码。具体周期和批量大小使用配置控制，并受 GoodShort 每分钟 100 次调用限制约束。

## 5. 剧集同步任务

新增表 `provider_drama_content_sync_task`，每个 `drama_id` 保留一条任务记录。

```text
字段                  含义
id                    任务主键
drama_id              本地短剧 ID，唯一
status                REQUESTED/RUNNING/SUCCESS/FAILED
requested_at          最近请求时间
next_run_at           下次可执行时间
retry_count           连续重试次数
total_fetched         本次获取的免费章节数
inserted_count        新增剧集数
updated_count         更新剧集数
last_error_code       最后错误代码
last_error_message    最后错误信息
lease_owner           租约持有者
lease_until           租约到期时间
created_at             创建时间
updated_at             更新时间
```

任务领取使用数据库条件更新和租约，默认租约时长沿用目录同步的 2 分钟。任务执行期间，同一 `drama_id` 不允许第二个 worker 处理。

失败策略：

- 网络异常、5xx、429：自动重试，使用递增的 `next_run_at`，达到配置的最大次数后变为 `FAILED`。
- GoodShort 业务拒绝、响应格式错误或短剧不存在：直接记录 `FAILED`，由管理员手动重试。
- 手动重试会清除错误信息，将任务重新置为 `REQUESTED`；任务正在 `RUNNING` 时返回任务执行中的业务错误。

## 6. 免费剧集字段映射

GoodShort `freeContent` 只返回章节名和视频 URL，不能填充完整的远端剧集元数据。`provider_drama_content` 增加可空的 `content_url` 字段，映射规则固定为：

```text
GoodShort chapterName    -> provider_drama_content.title
GoodShort content        -> provider_drama_content.content_url
返回顺序或章节号        -> provider_drama_content.sequence_no
固定 true                -> provider_drama_content.is_free
无对应字段              -> external_content_id 为空
无对应字段              -> duration_seconds 为空
无对应字段              -> remote_updated_at 为空
```

如果 `chapterName` 能解析出末尾章节数字，优先使用该数字作为 `sequence_no`；无法解析时使用本次响应顺序。现有唯一键 `(drama_id, sequence_no)` 继续使用，重复同步执行 upsert。

每个视频 URL 在写库前必须通过现有 `DramaMediaUrlValidator` 校验，只允许 `GOODSHORT_MEDIA_HOSTS` 配置的域名及其子域名，并拒绝内网地址、用户信息和非标准端口。任一 URL 无效时，本次任务失败且不覆盖已有剧集数据。

视频 URL 持久化到 MySQL，不设置业务 TTL。后续自动同步或手动补同步返回新 URL 时执行 upsert 覆盖旧值。现有 Redis 免费内容缓存不再作为用户播放和下载的数据源，避免数据库与缓存返回不同地址。

GoodShort 本次未返回的历史剧集不物理删除。收费剧集由于没有来源数据，不创建占位记录。

## 7. 管理员 API

### 7.1 手动请求同步

```text
POST /api/admin/drama/catalog/{id}/contents/sync
权限：ROLE_ADMIN
```

接口只创建或重置该短剧的剧集同步任务，不在 HTTP 请求中等待 GoodShort 完成。响应返回任务 ID、状态和最近一次统计信息。

### 7.2 按勾选短剧批量请求同步

```text
POST /api/admin/drama/catalog/contents/sync
权限：ROLE_ADMIN
```

请求体：

```json
{
  "dramaIds": [1, 2, 3]
}
```

`dramaIds` 必填、去重且最多 100 个。接口为每个有效短剧创建或重置任务；已经 `RUNNING` 的任务跳过，不中断当前 worker。响应返回请求数量、成功排队数量、运行中跳过数量、不存在或不可同步数量，以及各短剧的任务状态。

### 7.3 一键同步全部短剧

```text
POST /api/admin/drama/catalog/contents/sync/all
权限：ROLE_ADMIN
```

请求体：

```json
{
  "providerId": 1,
  "language": "ENGLISH",
  "missingOnly": false
}
```

`providerId` 必填；`language` 可选，不传时处理该平台全部已同步语言；`missingOnly` 默认 `false`，为 `true` 时只排队没有剧集或 `content_url` 为空的短剧。

一键全部同步只选择甲方当前在线的短剧，不受本地 `PUBLISHED/OFFLINE` 状态影响。接口按数据库分页创建或合并任务，不在 HTTP 请求中调用 GoodShort。响应只返回匹配数量、成功排队数量和运行中跳过数量，不返回全部任务明细。

### 7.4 查询任务状态

```text
GET /api/admin/drama/catalog/{id}/contents/sync/status
权限：ROLE_ADMIN
```

响应返回任务状态、重试次数、获取/新增/更新数量和最后错误信息。

### 7.5 现有接口保持不变

```text
GET /api/admin/drama/catalog/{id}
GET /api/user/promotion/dramas/{id}
GET /api/user/promotion/dramas/{id}/free-content
```

管理员和用户详情继续读取本地 `provider_drama_content`。用户端免费资源接口改为读取已持久化的 `content_url`，并将同一地址作为 `playUrl` 和 `downloadUrl` 返回；该接口不再实时调用 GoodShort，也不负责创建剧集记录。

三个手动同步入口都在事务提交后立即唤醒同一个剧集任务 worker。它们只改变任务状态，不复制同步逻辑，也不绕过限流、租约和重试。

## 8. 数据库与实现边界

- `src/main/resources/db/kasi_promotion.sql` 增加任务表和固定定时任务初始记录。
- `src/test/resources/test-schema.sql` 同步增加 H2 结构。
- `provider_drama_content` 增加可空的 `content_url TEXT`，用于保存 GoodShort 免费内容视频地址。
- 新增 `DramaContentSyncService` 接口及实现、任务 entity/mapper、管理员 DTO/VO/controller。
- 剧集任务服务复用现有 `FreeContentProviderAdapter.fetchFreeContent(...)`、签名、连接解密和异常转换，不复制 GoodShort 请求代码。
- 目录同步服务只负责创建任务，不直接调用免费内容适配器。
- 不修改 `provider_drama_content` 的现有唯一键和本地推广元数据字段。

开发数据库按仓库约定删除后重新执行唯一初始化 SQL，不增加历史迁移脚本。

## 9. 验证要求

至少覆盖：

```text
GoodShort 免费内容适配器：成功、空 data、业务失败、5xx、429、网络异常
剧集任务服务：新增任务、合并重复任务、URL 校验和持久化、成功 upsert、失败重试、租约互斥
目录同步联动：新短剧、远端更新时间变化、本地无剧集时自动入队
管理员接口：单部触发、勾选批量、一键全部、仅缺失过滤、查询状态、任务运行中、匿名 401、普通用户 403
用户免费资源接口：只读取数据库 URL，不调用 GoodShort，非法或缺失 URL 不返回
数据库结构：任务表唯一约束、状态默认值、索引和初始化脚本
```

验收标准是：完成一次目录同步后，新增或本地没有剧集的短剧最终能在 `provider_drama_content` 中出现免费剧集和视频 URL；用户播放和下载不再实时调用 GoodShort；重复执行不产生重复记录；收费剧集不出现在本地数据中。

## 10. 当前与规划分离

当前已实现：目录同步为新增、远端更新时间变化或本地缺少 URL 的短剧自动排队；固定任务每分钟分批调用 `freeContent`；任务使用数据库租约、失败重试和状态统计；`content_url` 永久保存；管理员支持单部、勾选批量、按平台全部同步和状态查询；用户播放与下载只读取本地 URL。

当前保留的兼容行为：用户免费资源接口仍接受 `refresh=true`，但不会清除缓存或调用 GoodShort，仍只读取数据库。

明确缺口：GoodShort 收费剧集列表和收费资源接口尚未提供，当前不纳入实现范围。
