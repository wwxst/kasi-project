# 剧集观看与素材下载 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将用户端短剧详情中的剧集播放、单集下载和批量下载，从当前的直链占位升级为可验证、可重试、可过期清理的真实能力。

**Architecture:** GoodShort 适配器继续负责 `/open/book/freeContent`，用户端服务只返回已上架且甲方在线短剧的短期资源。播放由浏览器端 HLS 播放器负责；下载统一创建后端任务，由后端下载源文件或通过 FFmpeg 将 HLS 合并为 MP4，再生成 ZIP，用户只能读取自己的任务和文件。资源地址进入 Redis 短 TTL 缓存，播放或下载遇到地址失效时只刷新一次并重试。

**Tech Stack:** Spring Boot 4、MyBatis、Flyway、Redis、GoodShort REST API、React 19、TDesign React、TanStack Query、hls.js、FFmpeg。

---

## 当前基线与范围

- 已实现：`GET /api/user/promotion/dramas/{id}/free-content`，调用 GoodShort `/open/book/freeContent`，返回 `playUrl` 和 `downloadUrl`。
- 已实现：用户端详情抽屉、播放按钮、单集下载入口和“下载全部”入口。
- 当前限制：浏览器直接加载资源地址；没有 HLS 播放器、下载任务表、分片合并、ZIP、缓存或真实 GoodShort 联调。
- 不在本计划内：付费剧集授权、订单收益、媒体账号报白、TikTok 锚点、推广链接生成规则。

## 文件责任地图

- 后端适配器：`src/main/java/com/kasi/backend/provider/spi/FreeContentProviderAdapter.java`、`src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java`
- 后端用户短剧接口：`src/main/java/com/kasi/backend/drama/controller/UserPromotionDramaController.java`、`src/main/java/com/kasi/backend/drama/service/impl/UserPromotionDramaServiceImpl.java`
- 后端下载任务：新增 `drama/download` 下的 DTO、Entity、Mapper、Service、Controller 和对应 XML。
- 数据库迁移：新增 `src/main/resources/db/migration/V20__drama_download_task.sql`，不修改已执行迁移。
- 用户端 API 类型：`E:/JavaProjects/kasi-project/kasi-user-web/src/features/dramas/types.ts`、`dramasApi.ts`
- 用户端详情交互：`E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotionLinks/PromotionLinksPage.tsx`、`PromotionLinksPage.module.less`
- 测试：现有 GoodShort 适配器测试、用户短剧 Controller 测试和 `PromotionLinksPage.test.tsx`，并新增下载任务迁移和服务测试。

### Task 1: HLS 播放支持

**Files:**
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/package.json`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/pnpm-lock.yaml`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotionLinks/PromotionLinksPage.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotionLinks/PromotionLinksPage.module.less`
- Test: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotionLinks/PromotionLinksPage.test.tsx`

- [ ] **Step 1: 写播放播放器初始化失败测试**

在 `PromotionLinksPage.test.tsx` 中为 `.m3u8` 资源增加断言：点击“播放”后存在 `video`，其 `data-testid="drama-video"` 属性存在；当浏览器不支持原生 HLS 时，调用 hls.js 的 `loadSource` 和 `attachMedia`。同时增加资源加载失败时显示“当前浏览器不支持播放该视频”。测试中 mock `hls.js` 的 `isSupported`、`loadSource` 和 `attachMedia`。

- [ ] **Step 2: 运行失败测试**

运行：`pnpm exec vitest run src/pages/promotionLinks/PromotionLinksPage.test.tsx --exclude '.worktrees/**'`

预期：失败，原因是项目未安装 `hls.js`，且组件没有调用播放器初始化逻辑。

- [ ] **Step 3: 安装并实现 HLS 播放器**

运行：`pnpm add hls.js`

在播放弹窗内给 `video` 增加 `ref`，按以下策略初始化：

```tsx
useEffect(() => {
  if (!playingEpisode?.playUrl || !videoRef.current) return
  const video = videoRef.current
  if (video.canPlayType('application/vnd.apple.mpegurl')) {
    video.src = playingEpisode.playUrl
    return
  }
  if (!Hls.isSupported()) {
    setPlaybackError('当前浏览器不支持播放该视频')
    return
  }
  const hls = new Hls({ enableWorker: true })
  hls.loadSource(playingEpisode.playUrl)
  hls.attachMedia(video)
  hls.on(Hls.Events.ERROR, (_event, data) => {
    if (data.fatal) setPlaybackError('视频加载失败，请稍后重试')
  })
  return () => hls.destroy()
}, [playingEpisode])
```

播放弹窗关闭时清理 `video.src`、播放器实例和错误状态；不要把第三方 URL 写入 localStorage。

- [ ] **Step 4: 运行播放测试并检查布局**

运行：`pnpm exec vitest run src/pages/promotionLinks/PromotionLinksPage.test.tsx --exclude '.worktrees/**'`

预期：播放测试通过，视频元素在弹窗内自适应宽度，不溢出移动端；hls.js 销毁函数在关闭弹窗时执行。

- [ ] **Step 5: 提交 Task 1**

```bash
git add package.json pnpm-lock.yaml src/pages/promotionLinks/PromotionLinksPage.tsx src/pages/promotionLinks/PromotionLinksPage.module.less src/pages/promotionLinks/PromotionLinksPage.test.tsx
git commit -m "feat: add hls playback for drama episodes"
```

### Task 2: 后端下载任务、分片合并与 ZIP

**Files:**
- Create: `src/main/resources/db/migration/V20__drama_download_task.sql`
- Create: `src/main/java/com/kasi/backend/drama/download/entity/DramaDownloadTask.java`
- Create: `src/main/java/com/kasi/backend/drama/download/enums/DramaDownloadTaskStatus.java`
- Create: `src/main/java/com/kasi/backend/drama/download/dto/CreateDramaDownloadTaskDTO.java`
- Create: `src/main/java/com/kasi/backend/drama/download/vo/DramaDownloadTaskVO.java`
- Create: `src/main/java/com/kasi/backend/drama/download/mapper/DramaDownloadTaskMapper.java`
- Create: `src/main/resources/mapper/DramaDownloadTaskMapper.xml`
- Create: `src/main/java/com/kasi/backend/drama/download/service/DramaDownloadTaskService.java`
- Create: `src/main/java/com/kasi/backend/drama/download/service/impl/DramaDownloadTaskServiceImpl.java`
- Create: `src/main/java/com/kasi/backend/drama/download/controller/UserDramaDownloadController.java`
- Modify: `src/main/java/com/kasi/backend/drama/controller/UserPromotionDramaController.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/UserPromotionDramaServiceImpl.java`
- Test: `src/test/java/com/kasi/backend/DramaDownloadTaskMigrationTest.java`
- Test: `src/test/java/com/kasi/backend/drama/download/service/DramaDownloadTaskServiceTest.java`
- Test: `src/test/java/com/kasi/backend/drama/download/controller/UserDramaDownloadControllerTest.java`

- [ ] **Step 1: 写迁移和任务服务失败测试**

迁移测试必须验证表名、状态枚举约束、用户/短剧索引、过期时间索引和文件路径字段。服务测试必须覆盖：只能为已上架且甲方在线短剧创建任务；空剧集列表被拒绝；用户不能读取其他用户任务；任务状态从 `PENDING` 到 `RUNNING` 再到 `SUCCESS`；源文件失败后变为 `FAILED` 并保存错误信息。

Controller 测试固定验证以下契约：

```text
POST /api/user/promotion/dramas/{dramaId}/downloads
body: { "contentIds": [101, 102] }
response.data: { "taskId": 9, "status": "PENDING", "totalCount": 2 }

GET /api/user/promotion/downloads/{taskId}
response.data: { "taskId": 9, "status": "SUCCESS", "completedCount": 2, "downloadUrl": "..." }

GET /api/user/promotion/downloads/{taskId}/file
response: application/zip with Content-Disposition attachment
```

- [ ] **Step 2: 运行失败测试**

运行：`$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd --% -Dtest=DramaDownloadTaskMigrationTest,DramaDownloadTaskServiceTest,UserDramaDownloadControllerTest test`

预期：编译或测试失败，原因是迁移、任务模型和接口尚未存在。

- [ ] **Step 3: 创建任务表和领域模型**

迁移使用以下字段：`id`、`user_id`、`drama_id`、`status`、`content_ids_json`、`file_path`、`file_name`、`total_count`、`completed_count`、`error_message`、`expires_at`、`created_at`、`updated_at`。为 `(user_id, created_at)` 和 `(status, expires_at)` 建索引；文件路径只保存服务端生成的绝对路径，不接受客户端路径。

DTO 使用 `@NotEmpty`、`@Size(max = 100)` 和 `@NotNull` 校验 `contentIds`；Service 只允许当前用户读取自己的任务。

- [ ] **Step 4: 实现下载执行器**

配置 `APP_FFMPEG_PATH`、`APP_DOWNLOAD_DIR`、`APP_DOWNLOAD_MAX_EPISODES`、`APP_DOWNLOAD_EXPIRE_HOURS`。下载执行器按 URL 后缀和响应 `Content-Type` 分流：普通 MP4 直接流式保存；HLS 使用 FFmpeg 输出 MP4；多个剧集完成后使用 `ZipOutputStream` 生成 ZIP。每个任务限制 100 集、单集 2GB、总任务 10GB，并使用临时文件后缀 `.part`，成功后原子重命名。

任务执行必须在 Controller 返回 `PENDING` 后异步运行；更新进度使用短事务。任何异常都删除未完成临时文件，任务标记 `FAILED`，不把远端 URL 或密钥写入错误信息。

- [ ] **Step 5: 实现用户接口和过期清理**

新增 Controller：`POST /api/user/promotion/dramas/{dramaId}/downloads` 创建任务，`GET /api/user/promotion/downloads/{taskId}` 查询任务，`GET /api/user/promotion/downloads/{taskId}/file` 下载成功文件。下载文件前重新校验任务用户、状态和 `expires_at`；过期任务返回业务错误并删除文件。

增加每小时清理任务，删除 `expires_at < NOW()` 的数据库记录和对应文件；清理路径必须限制在 `APP_DOWNLOAD_DIR` 下。

- [ ] **Step 6: 运行后端测试**

运行：`$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd --% -Dtest=DramaDownloadTaskMigrationTest,DramaDownloadTaskServiceTest,UserDramaDownloadControllerTest,GoodShortAdapterTest,UserPromotionDramaControllerTest test`

预期：所有测试通过，新增迁移在 H2 MySQL 模式下执行成功。

- [ ] **Step 7: 提交 Task 2**

```bash
git add src/main/resources/db/migration/V20__drama_download_task.sql src/main/java/com/kasi/backend/drama/download src/main/java/com/kasi/backend/drama/controller/UserPromotionDramaController.java src/main/java/com/kasi/backend/drama/service/impl/UserPromotionDramaServiceImpl.java src/main/resources/mapper/DramaDownloadTaskMapper.xml src/test/java/com/kasi/backend/DramaDownloadTaskMigrationTest.java src/test/java/com/kasi/backend/drama/download
git commit -m "feat: add drama download tasks and archives"
```

### Task 3: 资源缓存与失效刷新

**Files:**
- Create: `src/main/java/com/kasi/backend/drama/service/DramaResourceCacheService.java`
- Create: `src/main/java/com/kasi/backend/drama/service/impl/DramaResourceCacheServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/UserPromotionDramaServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/drama/download/service/impl/DramaDownloadTaskServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/kasi/backend/drama/service/DramaResourceCacheServiceTest.java`
- Test: `src/test/java/com/kasi/backend/drama/controller/UserPromotionDramaControllerTest.java`

- [ ] **Step 1: 写缓存和刷新失败测试**

测试覆盖：首次请求调用一次 GoodShort；同一短剧在 TTL 内命中 Redis 不重复调用；缓存 JSON 损坏时删除并刷新；播放/下载收到 403 或 404 时只刷新一次；第二次仍失败则返回 `PROVIDER_REMOTE_UNAVAILABLE` 或 `PROVIDER_REMOTE_REJECTED`，不能无限重试。

- [ ] **Step 2: 运行失败测试**

运行：`$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd --% -Dtest=DramaResourceCacheServiceTest,UserPromotionDramaControllerTest test`

预期：失败，原因是资源缓存服务不存在且当前 Service 每次直接请求 GoodShort。

- [ ] **Step 3: 实现 Redis 缓存**

使用键 `drama:free-content:{dramaId}`，值为资源列表 JSON，默认 TTL 5 分钟；用 `drama:free-content:lock:{dramaId}` 做 10 秒短锁，避免并发请求同时击穿 GoodShort。缓存只保存 `chapterName`、`contentUrl` 和抓取时间，不保存 API key。

- [ ] **Step 4: 收紧 URL 校验和刷新逻辑**

将当前仅检查 `http/https` 的逻辑改为：解析 URI、拒绝用户名密码和非标准端口、拒绝内网 IP，并要求 host 命中 `GOODSHORT_MEDIA_HOSTS` 配置。播放和下载收到远端 403/404 时删除对应短剧缓存并重新获取一次，重试仍失败则结束。

- [ ] **Step 5: 运行缓存与回归测试**

运行：`$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd --% -Dtest=DramaResourceCacheServiceTest,UserPromotionDramaControllerTest,DramaDownloadTaskServiceTest test`

预期：缓存命中、失效刷新、权限校验和下载任务回归全部通过。

- [ ] **Step 6: 提交 Task 3**

```bash
git add src/main/java/com/kasi/backend/drama/service/DramaResourceCacheService.java src/main/java/com/kasi/backend/drama/service/impl/DramaResourceCacheServiceImpl.java src/main/java/com/kasi/backend/drama/service/impl/UserPromotionDramaServiceImpl.java src/main/java/com/kasi/backend/drama/download/service/impl/DramaDownloadTaskServiceImpl.java src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java src/main/resources/application.yml src/test/java/com/kasi/backend/drama/service/DramaResourceCacheServiceTest.java src/test/java/com/kasi/backend/drama/controller/UserPromotionDramaControllerTest.java
git commit -m "feat: cache and refresh drama media resources"
```

### Task 4: 真实 GoodShort 配置联调与上线检查

**Files:**
- Create: `scripts/dev/smoke-goodshort-free-content.ps1`
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotionLinks/PromotionLinksPage.test.tsx`
- Test: `src/test/java/com/kasi/backend/provider/goodshort/GoodShortFreeContentIntegrationTest.java`

- [ ] **Step 1: 写真实联调前置检查**

脚本启动前检查 `GOODSHORT_BASE_URL`、`GOODSHORT_PARTNER_ID`、`GOODSHORT_API_KEY`、`DRAMA_EXTERNAL_ID` 四个环境变量；任一缺失立即退出，禁止把值打印到终端或日志。集成测试默认 `@Disabled`，只在显式 `-Dgoodshort.integration=true` 时启用。

- [ ] **Step 2: 增加接口响应契约测试**

真实联调验证：请求路径为 `/open/book/freeContent`；请求参数只有 `pid`、`timestamp`、`bookId`；签名与 GoodShort 文档一致；响应 `status=0` 且 `success=true` 时提取 `chapterName/content`；非 0 状态、空 data、缺少 content、非 HTTP(S) 地址都失败并隐藏密钥。

- [ ] **Step 3: 执行真实联调**

运行：`$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd --% -Dgoodshort.integration=true -Dtest=GoodShortFreeContentIntegrationTest test`

同时启动用户端和后端，使用真实短剧打开详情抽屉，依次验证：资源加载、Chrome 播放、单集下载、批量任务进度、ZIP 下载、过期任务拒绝。记录 HTTP 状态、GoodShort 响应字段和浏览器控制台错误，但不记录密钥和完整临时 URL。

- [ ] **Step 4: 修正文档和配置说明**

README 必须明确：播放依赖 hls.js/原生 HLS；下载依赖 FFmpeg；资源缓存 TTL 为 5 分钟；下载文件默认 24 小时过期；付费剧集仍未实现。AGENTS.md 同步当前接口、任务状态和环境变量，区分已实现与后续规划。

- [ ] **Step 5: 执行最终验证**

后端：`$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd test`

用户端：`pnpm run typecheck; pnpm exec vitest run --exclude '.worktrees/**'; pnpm run build`

格式：`git diff --check`

验收标准：后端完整测试无失败；用户端类型检查、测试和构建无错误；真实联调成功或明确记录外部平台阻断原因；没有提交任何凭据、临时下载文件或构建产物。

- [ ] **Step 6: 提交 Task 4**

```bash
git add scripts/dev/smoke-goodshort-free-content.ps1 README.md AGENTS.md src/test/java/com/kasi/backend/provider/goodshort/GoodShortFreeContentIntegrationTest.java E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotionLinks/PromotionLinksPage.test.tsx
git commit -m "test: verify drama playback and download with GoodShort"
```

## 依赖与验收顺序

1. Task 1 只依赖现有资源接口，完成后即可验证 Chrome 播放。
2. Task 2 依赖资源接口，但不依赖缓存；完成后获得真实下载任务和 ZIP。
3. Task 3 修改 Task 2 的资源读取路径，必须在下载任务测试稳定后实施。
4. Task 4 依赖前三项全部通过，并需要真实 GoodShort 配置和 FFmpeg 可执行文件。

## 计划自检

- 覆盖了四项用户确认内容：HLS 播放、下载任务/分片合并/失败重试/ZIP、资源缓存/失效刷新、真实 GoodShort 联调。
- 所有新增接口、表、状态和环境变量均在任务中给出名称和路径。
- 每个任务先写失败测试，再实现，再运行针对性测试，最后才运行完整回归。
- 当前已实现能力与规划中的 FFmpeg、Redis 缓存、真实联调明确分开，没有把规划描述成现状。
