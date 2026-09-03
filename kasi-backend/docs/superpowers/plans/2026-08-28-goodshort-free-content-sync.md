# GoodShort Free Content Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 自动或手动为 GoodShort 短剧同步免费剧集，将视频 URL 持久化，并让用户播放和下载直接读取本地数据。

**Architecture:** 目录同步只在同一数据库事务内创建按短剧唯一的剧集任务；固定系统任务每分钟领取任务租约，在事务外调用 `freeContent`，校验全部 URL 后用短事务 upsert 剧集并完成任务。单部、勾选批量和一键全部三个管理员入口共用同一任务服务，用户端不再实时调用 GoodShort。

**Tech Stack:** Java 25、Spring Boot、Spring Security、MyBatis XML、MySQL/H2、Jakarta Validation、JUnit 5、Mockito、MockMvc

---

## File Structure

```text
src/main/java/com/kasi/backend/drama/config/DramaContentSyncProperties.java
src/main/java/com/kasi/backend/drama/config/DramaContentSyncConfig.java
    免费剧集任务批量、租约和重试配置。

src/main/java/com/kasi/backend/drama/entity/DramaContentSyncTask.java
src/main/java/com/kasi/backend/drama/enums/DramaContentSyncStatus.java
src/main/java/com/kasi/backend/drama/mapper/DramaContentSyncTaskMapper.java
src/main/resources/mapper/DramaContentSyncTaskMapper.xml
    按 drama_id 唯一的任务、到期扫描、租约、成功和失败持久化。

src/main/java/com/kasi/backend/drama/service/DramaContentSyncService.java
src/main/java/com/kasi/backend/drama/service/impl/DramaContentSyncServiceImpl.java
    手动/自动入队、批量入队、GoodShort 调用、URL 校验、重试和剧集 upsert。

src/main/java/com/kasi/backend/drama/dto/RequestDramaContentBatchSyncDTO.java
src/main/java/com/kasi/backend/drama/dto/RequestAllDramaContentSyncDTO.java
src/main/java/com/kasi/backend/drama/vo/DramaContentSyncTaskVO.java
src/main/java/com/kasi/backend/drama/vo/DramaContentSyncBatchVO.java
    管理员请求和任务状态契约。

src/main/java/com/kasi/backend/drama/controller/AdminDramaCatalogController.java
    单部、勾选批量、全部同步和状态端点。

src/main/java/com/kasi/backend/drama/service/impl/DramaCatalogSyncServiceImpl.java
    新短剧、远端更新时间变化或本地 URL 缺失时自动入队。

src/main/java/com/kasi/backend/scheduledtask/**
    注册并分发 GOODSHORT_DRAMA_CONTENT_SYNC 固定任务。

src/main/java/com/kasi/backend/drama/service/impl/UserPromotionDramaServiceImpl.java
    用户免费内容改为读取 provider_drama_content.content_url。

src/main/resources/db/kasi_promotion.sql
src/test/resources/test-schema.sql
    content_url、剧集任务表和固定任务唯一初始化结构。
```

### Task 1: Add schema and persistence contracts

**Files:**
- Modify: `src/main/resources/db/kasi_promotion.sql`
- Modify: `src/test/resources/test-schema.sql`
- Modify: `src/main/java/com/kasi/backend/drama/entity/ProviderDramaContent.java`
- Modify: `src/main/java/com/kasi/backend/drama/mapper/ProviderDramaMapper.java`
- Modify: `src/main/resources/mapper/ProviderDramaMapper.xml`
- Create: `src/main/java/com/kasi/backend/drama/enums/DramaContentSyncStatus.java`
- Create: `src/main/java/com/kasi/backend/drama/entity/DramaContentSyncTask.java`
- Create: `src/main/java/com/kasi/backend/drama/mapper/DramaContentSyncTaskMapper.java`
- Create: `src/main/resources/mapper/DramaContentSyncTaskMapper.xml`
- Modify: `src/test/java/com/kasi/backend/BaseAuthTest.java`
- Test: `src/test/java/com/kasi/backend/drama/mapper/DramaContentSyncPersistenceTest.java`
- Test: `src/test/java/com/kasi/backend/drama/mapper/DramaCatalogPersistenceTest.java`

- [ ] **Step 1: Write failing persistence tests**

Add tests that require URL persistence and a leased task:

```java
content.setContentUrl("https://cdn.test/episode-1.m3u8");
dramaMapper.upsertContent(content);
assertThat(dramaMapper.findContents(dramaId).getFirst().getContentUrl())
        .isEqualTo("https://cdn.test/episode-1.m3u8");

DramaContentSyncTask task = requestedTask(dramaId, now);
assertThat(taskMapper.insert(task)).isEqualTo(1);
assertThat(taskMapper.claimLease(task.getId(), "worker-a", now, now.plusMinutes(2))).isEqualTo(1);
assertThat(taskMapper.claimLease(task.getId(), "worker-b", now, now.plusMinutes(2))).isZero();
```

- [ ] **Step 2: Run tests and confirm schema/mappers are missing**

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd --% -Dtest=DramaCatalogPersistenceTest,DramaContentSyncPersistenceTest test
```

Expected: compilation or SQL failure for `content_url` and missing task types/table.

- [ ] **Step 3: Add the production and H2 schema**

Add `content_url TEXT NULL` to `provider_drama_content`, then add:

```sql
CREATE TABLE `provider_drama_content_sync_task`
(
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `drama_id`           BIGINT UNSIGNED NOT NULL,
    `status`             VARCHAR(16)     NOT NULL DEFAULT 'REQUESTED',
    `requested_at`       DATETIME        NOT NULL,
    `next_run_at`        DATETIME        NOT NULL,
    `retry_count`        INT             NOT NULL DEFAULT 0,
    `total_fetched`      INT             NOT NULL DEFAULT 0,
    `inserted_count`     INT             NOT NULL DEFAULT 0,
    `updated_count`      INT             NOT NULL DEFAULT 0,
    `last_error_code`    VARCHAR(64)              DEFAULT NULL,
    `last_error_message` VARCHAR(512)             DEFAULT NULL,
    `lease_owner`        VARCHAR(64)              DEFAULT NULL,
    `lease_until`        DATETIME                 DEFAULT NULL,
    `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_drama_content_sync_task_drama` (`drama_id`),
    KEY `idx_drama_content_sync_task_due` (`status`, `next_run_at`, `lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短剧免费剧集同步任务';
```

- [ ] **Step 4: Add entity and mapper contracts**

Use these task operations:

```java
DramaContentSyncTask findById(Long id);
DramaContentSyncTask findByDramaId(Long dramaId);
int insert(DramaContentSyncTask task);
int request(Long dramaId, LocalDateTime requestedAt);
List<Long> findDueIds(LocalDateTime now, int limit);
int claimLease(Long id, String owner, LocalDateTime now, LocalDateTime leaseUntil);
int markSuccess(Long id, String owner, int totalFetched, int insertedCount, int updatedCount);
int recordRetry(Long id, String owner, LocalDateTime nextRunAt, int retryCount, String code, String message);
int markFailed(Long id, String owner, int retryCount, String code, String message);
```

Extend `ProviderDramaMapper` with URL upsert and candidate queries:

```java
boolean needsContentSync(@Param("dramaId") Long dramaId);
List<Long> findContentSyncCandidateIds(@Param("providerId") Long providerId,
                                       @Param("language") String language,
                                       @Param("missingOnly") boolean missingOnly,
                                       @Param("afterId") Long afterId,
                                       @Param("limit") int limit);
```

- [ ] **Step 5: Update test cleanup order and rerun persistence tests**

Delete `provider_drama_content_sync_task` before `provider_drama`, then run the Task 1 command.

Expected: `Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit Task 1**

```powershell
git add src/main/resources/db/kasi_promotion.sql src/test/resources/test-schema.sql src/main/java/com/kasi/backend/drama src/main/resources/mapper src/test/java/com/kasi/backend/BaseAuthTest.java src/test/java/com/kasi/backend/drama/mapper
git commit -m "feat: persist GoodShort free content sync tasks"
```

### Task 2: Implement the free-content worker

**Files:**
- Create: `src/main/java/com/kasi/backend/drama/config/DramaContentSyncProperties.java`
- Create: `src/main/java/com/kasi/backend/drama/config/DramaContentSyncConfig.java`
- Create: `src/main/java/com/kasi/backend/drama/service/DramaContentSyncService.java`
- Create: `src/main/java/com/kasi/backend/drama/service/impl/DramaContentSyncServiceImpl.java`
- Create: `src/main/java/com/kasi/backend/drama/vo/DramaContentSyncTaskVO.java`
- Create: `src/main/java/com/kasi/backend/drama/vo/DramaContentSyncBatchVO.java`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/com/kasi/backend/drama/service/DramaContentSyncServiceTest.java`

- [ ] **Step 1: Write failing worker tests**

Cover successful persistence, deterministic sequence assignment, invalid URL rollback, transient retry, final rejection and lease loss:

```java
when(adapter.fetchFreeContent(any(), eq("book-1"))).thenReturn(List.of(
        new FreeContentResult("Chapter 1", "https://cdn.test/1.m3u8"),
        new FreeContentResult("Bonus", "https://cdn.test/bonus.mp4")));

service.processDueBatch();

verify(dramaMapper, times(2)).upsertContent(contentCaptor.capture());
assertThat(contentCaptor.getAllValues()).extracting(ProviderDramaContent::getSequenceNo)
        .containsExactly(1, 2);
verify(taskMapper).markSuccess(taskId, "content-worker-test", 2, 2, 0);
```

- [ ] **Step 2: Run the service test and confirm the service is absent**

```powershell
.\mvnw.cmd '-Dtest=DramaContentSyncServiceTest' test
```

Expected: compilation failure for missing service and properties.

- [ ] **Step 3: Implement task configuration and service interface**

Use:

```properties
app.promotion.drama.content-sync.batch-size=50
app.promotion.drama.content-sync.candidate-page-size=500
app.promotion.drama.content-sync.lease-duration=2m
app.promotion.drama.content-sync.max-retries=5
app.promotion.drama.content-sync.retry-delays=1m,5m,15m,30m,60m
```

Register only this property class in the new focused config:

```java
@Configuration
@EnableConfigurationProperties(DramaContentSyncProperties.class)
public class DramaContentSyncConfig {
}
```

Expose:

```java
DramaContentSyncTaskVO request(Long dramaId);
DramaContentSyncBatchVO requestBatch(List<Long> dramaIds);
DramaContentSyncBatchVO requestAll(Long providerId, String language, boolean missingOnly);
DramaContentSyncTaskVO getStatus(Long dramaId);
void requestAutomatic(Long dramaId);
void processDueBatch();
```

- [ ] **Step 4: Implement worker data flow**

The worker must:

```java
ProviderRuntimeConnection runtime = runtimeService.resolve(
        connection.getProviderId(), ProviderCapability.FREE_CONTENT_PREVIEW);
List<FreeContentResult> remote = adapter.fetchFreeContent(runtime.secret(), drama.getExternalDramaId());
if (remote.stream().anyMatch(item -> !urlValidator.isAllowed(item.contentUrl()))) {
    markFailed(task, "INVALID_MEDIA_URL", "GoodShort returned an invalid media URL");
    return;
}
transactionTemplate.executeWithoutResult(status -> persistAndComplete(task, remote));
```

Parse trailing chapter numbers with `Pattern.compile("(\\d+)\\s*$")`; when missing or duplicated, allocate the first unused positive sequence. Load existing contents once to calculate inserted/updated counts, upsert `title`, `sequenceNo`, `free=true`, and `contentUrl`, and never delete absent history.

- [ ] **Step 5: Implement retries and batch requests**

`ProviderTransientException` uses configured delay and becomes `FAILED` after five attempts. `ProviderRemoteRejectedException`, invalid URL, missing drama/connection, or unsupported capability becomes `FAILED` immediately. Manual request resets non-running tasks to `REQUESTED`; running tasks are counted as skipped.

`requestAll` pages `ProviderDramaMapper.findContentSyncCandidateIds(...)` by `id` and enqueues each page without calling GoodShort.

- [ ] **Step 6: Run worker tests**

```powershell
.\mvnw.cmd '-Dtest=DramaContentSyncServiceTest' test
```

Expected: `Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit Task 2**

```powershell
git add src/main/java/com/kasi/backend/drama src/main/resources/application.properties src/test/java/com/kasi/backend/drama/service/DramaContentSyncServiceTest.java
git commit -m "feat: process GoodShort free content sync tasks"
```

### Task 3: Connect catalog and fixed scheduling

**Files:**
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/DramaCatalogSyncServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/scheduledtask/enums/ScheduledTaskCode.java`
- Modify: `src/main/java/com/kasi/backend/scheduledtask/service/impl/ScheduledTaskDispatchServiceImpl.java`
- Modify: `src/main/resources/db/kasi_promotion.sql`
- Modify: `src/test/java/com/kasi/backend/BaseAuthTest.java`
- Test: `src/test/java/com/kasi/backend/drama/service/DramaCatalogSyncServiceTest.java`
- Test: `src/test/java/com/kasi/backend/scheduledtask/service/ScheduledTaskDispatchServiceTest.java`
- Test: `src/test/java/com/kasi/backend/scheduledtask/controller/ScheduledTaskControllerTest.java`
- Test: `src/test/java/com/kasi/backend/ScheduledTaskMigrationTest.java`

- [ ] **Step 1: Write failing catalog and scheduler tests**

```java
verify(contentSyncService).requestAutomatic(storedDramaId);
verify(contentSyncService).processDueBatch();
assertThat(task.get("TASK_CODE")).isEqualTo("GOODSHORT_DRAMA_CONTENT_SYNC");
```

Cover new drama, changed `remoteUpdatedAt`, unchanged complete drama, unchanged drama missing URL, fixed-task dispatch, and the new fixed task appearing in the management API without relying on the old task remaining at array index zero.

- [ ] **Step 2: Run focused tests and confirm missing integration**

```powershell
.\mvnw.cmd --% -Dtest=DramaCatalogSyncServiceTest,ScheduledTaskDispatchServiceTest,ScheduledTaskControllerTest,ScheduledTaskMigrationTest test
```

Expected: failures for missing service dependency and enum constant.

- [ ] **Step 3: Enqueue from catalog persistence**

Before upsert, retain the existing `remoteUpdatedAt`; after reload, call `requestAutomatic` when the row is new, the timestamp changed, or `dramaMapper.needsContentSync(id)` is true. Keep this call inside the page transaction so the short-drama row and task commit together.

- [ ] **Step 4: Register and dispatch the fixed task**

Add:

```java
GOODSHORT_DRAMA_CONTENT_SYNC("GoodShort 免费剧集同步")
```

and dispatch it with:

```java
case GOODSHORT_DRAMA_CONTENT_SYNC -> contentSyncService.processDueBatch();
```

Seed it as enabled `INTERVAL_MINUTES=1` in the unique initialization SQL and test data.

- [ ] **Step 5: Run focused tests and commit**

```powershell
.\mvnw.cmd --% -Dtest=DramaCatalogSyncServiceTest,ScheduledTaskDispatchServiceTest,ScheduledTaskControllerTest,ScheduledTaskMigrationTest test
git add src/main/java/com/kasi/backend/drama src/main/java/com/kasi/backend/scheduledtask src/main/resources/db/kasi_promotion.sql src/test/java/com/kasi/backend
git commit -m "feat: automate GoodShort free episode synchronization"
```

Expected: `Failures: 0, Errors: 0`.

### Task 4: Expose single, selected, and all admin APIs

**Files:**
- Create: `src/main/java/com/kasi/backend/drama/dto/RequestDramaContentBatchSyncDTO.java`
- Create: `src/main/java/com/kasi/backend/drama/dto/RequestAllDramaContentSyncDTO.java`
- Modify: `src/main/java/com/kasi/backend/drama/controller/AdminDramaCatalogController.java`
- Modify: `src/main/java/com/kasi/backend/common/exception/ErrorCode.java`
- Test: `src/test/java/com/kasi/backend/drama/controller/AdminDramaCatalogControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Test these requests:

```http
POST /api/admin/drama/catalog/{id}/contents/sync
POST /api/admin/drama/catalog/contents/sync
POST /api/admin/drama/catalog/contents/sync/all
GET  /api/admin/drama/catalog/{id}/contents/sync/status
```

Use a batch body with `[dramaId]`, an all body with `providerId`, `language`, and `missingOnly`, a 101-ID validation failure, anonymous 401, and user 403.

- [ ] **Step 2: Run controller test and confirm routes are missing**

```powershell
.\mvnw.cmd '-Dtest=AdminDramaCatalogControllerTest' test
```

Expected: 404 or compilation failure for missing DTO/service methods.

- [ ] **Step 3: Add validated DTOs and controller methods**

```java
@NotEmpty
@Size(max = 100)
private List<@NotNull @Positive Long> dramaIds;

@NotNull
@Positive
private Long providerId;
@Size(max = 32)
private String language;
private boolean missingOnly;
```

Controller methods return `ApiResponse<DramaContentSyncTaskVO>` or `ApiResponse<DramaContentSyncBatchVO>` and delegate only to `DramaContentSyncService`.

- [ ] **Step 4: Add reachable error codes**

Add only:

```java
DRAMA_CONTENT_SYNC_TASK_RUNNING(6016, "短剧剧集同步任务正在执行"),
DRAMA_CONTENT_SYNC_TASK_NOT_FOUND(6017, "短剧剧集同步任务不存在"),
```

- [ ] **Step 5: Run controller tests and commit**

```powershell
.\mvnw.cmd '-Dtest=AdminDramaCatalogControllerTest' test
git add src/main/java/com/kasi/backend/drama src/main/java/com/kasi/backend/common/exception/ErrorCode.java src/test/java/com/kasi/backend/drama/controller/AdminDramaCatalogControllerTest.java
git commit -m "feat: expose GoodShort free episode sync APIs"
```

Expected: `Failures: 0, Errors: 0`.

### Task 5: Read persisted URLs in user and download flows

**Files:**
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/UserPromotionDramaServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/UserPromotionDramaService.java`
- Delete: `src/main/java/com/kasi/backend/drama/service/DramaResourceCacheService.java`
- Delete: `src/main/java/com/kasi/backend/drama/service/impl/DramaResourceCacheServiceImpl.java`
- Delete: `src/test/java/com/kasi/backend/drama/service/DramaResourceCacheServiceTest.java`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/com/kasi/backend/drama/controller/UserPromotionDramaControllerTest.java`
- Test: `src/test/java/com/kasi/backend/drama/download/service/DramaDownloadTaskServiceTest.java`

- [ ] **Step 1: Rewrite the user controller test to require database-only URLs**

Insert:

```sql
INSERT INTO provider_drama_content
    (drama_id, external_content_id, sequence_no, title, is_free, duration_seconds, content_url)
VALUES (?, 'episode-1', 1, 'Chapter 1', 1, 90, 'https://cdn.test/episode-1.m3u8')
```

Then assert two `/free-content` calls and `refresh=true` return the same URL without mocking or verifying `ProviderRuntimeConnectionService`.

- [ ] **Step 2: Run user/download tests and confirm old remote behavior fails expectations**

```powershell
.\mvnw.cmd --% -Dtest=UserPromotionDramaControllerTest,DramaDownloadTaskServiceTest test
```

- [ ] **Step 3: Replace remote/cache reads with local URL reads**

`getFreeContent` must load the published drama, iterate local contents, validate each stored `contentUrl`, and return it as both `playUrl` and `downloadUrl` only when `is_free=true`. Keep the `refresh` parameter for API compatibility but perform no remote request.

- [ ] **Step 4: Remove the unused Redis resource cache**

Delete the cache interface, implementation, test, and these unused properties:

```properties
app.drama.resource-cache-ttl
app.drama.resource-lock-ttl
```

Do not change Redis authentication/session behavior.

- [ ] **Step 5: Run user/download tests and commit**

```powershell
.\mvnw.cmd --% -Dtest=UserPromotionDramaControllerTest,DramaDownloadTaskServiceTest test
git add src/main/java/com/kasi/backend/drama src/main/resources/application.properties src/test/java/com/kasi/backend/drama
git commit -m "feat: serve persisted GoodShort episode URLs"
```

Expected: `Failures: 0, Errors: 0`.

### Task 6: Update current documentation and verify the feature

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-08-28-goodshort-free-content-sync-design.md`

- [ ] **Step 1: Update current behavior documentation**

Record that:

```text
当前已实现：目录同步自动排队免费剧集任务；后台每分钟分批调用 freeContent；
管理员支持单部、勾选批量和一键全部同步；content_url 永久保存；
用户播放和下载读取本地 URL；收费剧集仍不可同步。
```

Remove statements that free-content URLs are only cached for five minutes or refreshed from GoodShort during playback.

- [ ] **Step 2: Run focused feature tests under Java 25**

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd --% -Dtest=DramaContentSyncPersistenceTest,DramaContentSyncServiceTest,DramaCatalogPersistenceTest,DramaCatalogSyncServiceTest,AdminDramaCatalogControllerTest,UserPromotionDramaControllerTest,DramaDownloadTaskServiceTest,ScheduledTaskDispatchServiceTest,ScheduledTaskMigrationTest test
```

Expected: `Failures: 0, Errors: 0`.

- [ ] **Step 3: Run the full test suite**

```powershell
.\mvnw.cmd test
```

Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`.

- [ ] **Step 4: Run compile and diff checks**

```powershell
.\mvnw.cmd -DskipTests compile
git diff --check
git status --short
```

Expected: compile succeeds; `git diff --check` is silent; status contains only intended files.

- [ ] **Step 5: Commit documentation**

```powershell
git add README.md AGENTS.md docs/superpowers/specs/2026-08-28-goodshort-free-content-sync-design.md
git commit -m "docs: document GoodShort free episode synchronization"
```
