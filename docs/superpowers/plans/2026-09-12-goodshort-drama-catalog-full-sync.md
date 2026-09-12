# GoodShort 短剧目录每日全量同步 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增每天 03:00 的 GoodShort 短剧目录全量同步，并在单语言完整成功后把甲方本轮未返回的短剧标记为 `MISSING / OFFLINE`。

**Architecture:** 复用现有 `system_scheduled_task` 分发器、目录 checkpoint、展示运行记录和 worker。每轮全量使用稳定的 `requested_at` 划分本次快照；末页完成后在同一事务中先按租约收口 checkpoint，再批量标记未见记录。管理端只扩展现有远端状态标签，不增加 schema 或新的 API。

**Tech Stack:** Java 25、Spring Boot 4、MyBatis、MySQL 8/H2、JUnit 5、Mockito、React 19、TypeScript、Ant Design、Vitest。

---

## 执行边界

- 工作目录固定为 `E:\JavaProjects\kasi-project`。
- 保留当前脏工作区，不重置、不覆盖、不清理无关文件；编辑重叠文件前先复核当前 diff。
- 当前已有未提交的 `V10__localize_goodshort_analytical_report_task.sql`，本功能新增 `V11__add_goodshort_drama_full_sync_task.sql`，不得修改或改号现有迁移。
- 不连接生产数据库，不执行生产 Flyway，不调用真实 GoodShort。
- 未经用户明确授权，不执行 `git commit`、`git push` 或合并；每个任务末尾只做范围复核并停靠。
- 后端命令使用命令局部 JDK 25：

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## 文件结构

- `kasi-backend/src/main/java/com/kasi/backend/drama/mapper/ProviderDramaMapper.java`：声明按全量快照标记未返回短剧的方法。
- `kasi-backend/src/main/resources/mapper/ProviderDramaMapper.xml`：限定连接、语言和快照时间执行 `MISSING / OFFLINE` 更新。
- `kasi-backend/src/main/resources/mapper/ProviderSyncCheckpointMapper.xml`：失败续跑时保留原 `requested_at`。
- `kasi-backend/src/main/java/com/kasi/backend/drama/service/DramaCatalogSyncService.java`：声明定时全量入队入口。
- `kasi-backend/src/main/java/com/kasi/backend/drama/service/impl/DramaCatalogSyncServiceImpl.java`：创建 `FULL / SCHEDULED` 任务，并原子完成 checkpoint 与缺失对账。
- `kasi-backend/src/main/java/com/kasi/backend/scheduledtask/enums/ScheduledTaskCode.java`：增加固定任务编码和中文标题。
- `kasi-backend/src/main/java/com/kasi/backend/scheduledtask/service/impl/ScheduledTaskDispatchServiceImpl.java`：分发每天全量任务。
- `kasi-backend/src/main/resources/db/migration/V11__add_goodshort_drama_full_sync_task.sql`：为存量库增加固定任务。
- `kasi-backend/src/main/resources/db/kasi_promotion.sql`：保持开发空库固定数据一致。
- `kasi-backend/src/test/java/com/kasi/backend/BaseAuthTest.java`：为接口测试植入全量固定任务。
- `kasi-backend/src/test/java/com/kasi/backend/drama/mapper/DramaCatalogPersistenceTest.java`：验证快照边界、缺失标记和重新出现。
- `kasi-backend/src/test/java/com/kasi/backend/drama/service/DramaCatalogSyncServiceTest.java`：验证定时全量入队和成功收口。
- `kasi-backend/src/test/java/com/kasi/backend/scheduledtask/service/ScheduledTaskDispatchServiceTest.java`：验证 03:00 固定任务分发。
- `kasi-backend/src/test/java/com/kasi/backend/ScheduledTaskMigrationTest.java`：验证开发初始化与 V11 固定数据。
- `kasi-backend/src/test/java/com/kasi/backend/scheduledtask/controller/ScheduledTaskControllerTest.java`：验证管理 API 返回新任务中文标题。
- `kasi-admin-web/src/pages/drama/DramaCatalogPage.tsx`：展示 `MISSING` 为“全量未返回”。
- `kasi-admin-web/src/pages/drama/DramaCatalogPage.test.tsx`：验证列表与详情状态文案。
- `kasi-backend/AGENTS.md`、`kasi-backend/README.md`、`docs/architecture/current.md`：实施完成后记录已验证的当前行为。

### Task 1: 固定全量快照边界和缺失短剧持久化规则

**Files:**

- Modify: `kasi-backend/src/test/java/com/kasi/backend/drama/mapper/DramaCatalogPersistenceTest.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/drama/mapper/ProviderDramaMapper.java`
- Modify: `kasi-backend/src/main/resources/mapper/ProviderDramaMapper.xml`
- Modify: `kasi-backend/src/main/resources/mapper/ProviderSyncCheckpointMapper.xml`

- [x] **Step 1: 写 checkpoint 时间边界和缺失标记失败测试**

在 `requestRunResetsSuccessAndResumesFailure()` 中补充断言，证明成功任务重跑产生新快照时间，而失败续跑保留原快照时间：

```java
assertThat(restarted.getRequestedAt()).isEqualTo(now.plusMinutes(1));

// failure resume assertions
assertThat(resumed.getRequestedAt()).isEqualTo(now.plusMinutes(1));
```

新增持久层测试，准备同连接英语的已见/未见记录和其他语言记录，然后调用尚不存在的 Mapper 方法：

```java
@Test
@DisplayName("全量成功只下架同连接同语言本轮未返回的短剧")
void fullSnapshotMarksOnlyMissingDramasOffline() {
    Long connectionId = insertConnection();
    LocalDateTime snapshotStartedAt = LocalDateTime.of(2026, 8, 21, 3, 0);

    ProviderDrama missing = drama(connectionId, "missing-book");
    missing.setRemoteShowStatus("1");
    missing.setLastSeenAt(snapshotStartedAt.minusMinutes(1));
    missing.setCommissionScope("ORDER");
    missing.setPromotionDescription("保留推广说明");
    dramaMapper.upsert(missing);

    ProviderDrama seen = drama(connectionId, "seen-book");
    seen.setRemoteShowStatus("1");
    seen.setLastSeenAt(snapshotStartedAt.plusMinutes(1));
    dramaMapper.upsert(seen);

    ProviderDrama otherLanguage = drama(connectionId, "other-language-book");
    otherLanguage.setLanguage("SPANISH");
    otherLanguage.setRemoteShowStatus("1");
    otherLanguage.setLastSeenAt(snapshotStartedAt.minusMinutes(1));
    dramaMapper.upsert(otherLanguage);

    jdbcTemplate.update("""
            INSERT INTO short_drama_provider (provider_code, provider_name, status)
            VALUES ('OTHER', 'Other provider', 1)
            """);
    Long otherProviderId = jdbcTemplate.queryForObject(
            "SELECT id FROM short_drama_provider WHERE provider_code='OTHER'", Long.class);
    jdbcTemplate.update("""
            INSERT INTO short_drama_connection (provider_id, connection_name, currency)
            VALUES (?, 'Other connection', 'USD')
            """, otherProviderId);
    Long otherConnectionId = jdbcTemplate.queryForObject(
            "SELECT id FROM short_drama_connection WHERE provider_id=?", Long.class, otherProviderId);
    ProviderDrama otherConnection = drama(otherConnectionId, "other-connection-book");
    otherConnection.setRemoteShowStatus("1");
    otherConnection.setLastSeenAt(snapshotStartedAt.minusMinutes(1));
    dramaMapper.upsert(otherConnection);

    assertThat(dramaMapper.markMissingAfterFullSync(
            connectionId, "ENGLISH", snapshotStartedAt)).isEqualTo(1);

    ProviderDrama missingStored = dramaMapper.findByConnectionAndExternalId(connectionId, "missing-book");
    assertThat(missingStored.getRemoteShowStatus()).isEqualTo("MISSING");
    assertThat(missingStored.getLocalStatus()).isEqualTo(DramaLocalStatus.OFFLINE);
    assertThat(missingStored.getLastSeenAt()).isEqualTo(snapshotStartedAt.minusMinutes(1));
    assertThat(missingStored.getCommissionScope()).isEqualTo("ORDER");
    assertThat(missingStored.getPromotionDescription()).isEqualTo("保留推广说明");
    assertThat(dramaMapper.findByConnectionAndExternalId(connectionId, "seen-book").getRemoteShowStatus())
            .isEqualTo("1");
    assertThat(dramaMapper.findByConnectionAndExternalId(connectionId, "other-language-book").getRemoteShowStatus())
            .isEqualTo("1");
    assertThat(dramaMapper.findByConnectionAndExternalId(otherConnectionId, "other-connection-book").getRemoteShowStatus())
            .isEqualTo("1");
}
```

把现有“甲方重新上架”测试的初始远端状态改为 `MISSING`，并断言 upsert 后 `remote_show_status='1'`、`local_status='OFFLINE'`、`last_seen_at` 刷新。

- [x] **Step 2: 运行聚焦测试确认 RED**

```powershell
cd E:\JavaProjects\kasi-project\kasi-backend
.\mvnw.cmd "-Dtest=DramaCatalogPersistenceTest" test
```

Expected: 编译失败，提示 `ProviderDramaMapper.markMissingAfterFullSync` 不存在；这是预期 RED。

- [x] **Step 3: 增加最小 Mapper 契约**

在 `ProviderDramaMapper.java` 增加：

```java
int markMissingAfterFullSync(@Param("connectionId") Long connectionId,
                             @Param("language") String language,
                             @Param("snapshotStartedAt") LocalDateTime snapshotStartedAt);
```

在 `ProviderDramaMapper.xml` 增加：

```xml
<update id="markMissingAfterFullSync">
    UPDATE provider_drama
    SET remote_show_status = 'MISSING',
        local_status = 'OFFLINE',
        updated_at = CURRENT_TIMESTAMP
    WHERE connection_id = #{connectionId}
      AND language = #{language}
      AND (last_seen_at IS NULL OR last_seen_at &lt; #{snapshotStartedAt})
      AND (COALESCE(remote_show_status, '') &lt;&gt; 'MISSING' OR local_status &lt;&gt; 'OFFLINE')
</update>
```

在 `ProviderSyncCheckpointMapper.xml` 的 `requestRun` 中只调整 `requested_at`：

```xml
requested_at=CASE
    WHEN #{restart} OR requested_at IS NULL THEN #{requestedAt}
    ELSE requested_at
END,
```

其余页码、游标、计数和错误字段规则保持不变。

- [x] **Step 4: 运行测试确认 GREEN**

```powershell
cd E:\JavaProjects\kasi-project\kasi-backend
.\mvnw.cmd "-Dtest=DramaCatalogPersistenceTest" test
```

Expected: `DramaCatalogPersistenceTest` 全部通过，0 failures、0 errors。

- [x] **Step 5: 复核任务范围并停靠**

```powershell
cd E:\JavaProjects\kasi-project
git diff -- kasi-backend/src/main/java/com/kasi/backend/drama/mapper/ProviderDramaMapper.java kasi-backend/src/main/resources/mapper/ProviderDramaMapper.xml kasi-backend/src/main/resources/mapper/ProviderSyncCheckpointMapper.xml kasi-backend/src/test/java/com/kasi/backend/drama/mapper/DramaCatalogPersistenceTest.java
```

Expected: 只包含快照时间和缺失标记规则；不提交。

### Task 2: 增加 `FULL / SCHEDULED` 目录任务入队入口

**Files:**

- Modify: `kasi-backend/src/test/java/com/kasi/backend/drama/service/DramaCatalogSyncServiceTest.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/drama/service/DramaCatalogSyncService.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/drama/service/impl/DramaCatalogSyncServiceImpl.java`

- [x] **Step 1: 写定时全量入队失败测试**

在 `DramaCatalogSyncServiceTest` 增加两项测试。成功测试必须验证旧 `FAILED` checkpoint 也使用 `restart=true` 开始新快照，并产生 `FULL / SCHEDULED` 展示记录：

```java
@Test
@DisplayName("定时全量从第一页创建新的完整快照和展示记录")
void scheduledFullStartsFreshSnapshot() {
    when(runtimeService.resolve(7L, ProviderCapability.FULL_DRAMA_SYNC)).thenReturn(runtime());
    when(connectionMapper.lockById(3L))
            .thenReturn(mock(com.kasi.backend.provider.entity.ShortDramaConnection.class));
    ProviderSyncCheckpoint failed = checkpoint(11L, DramaSyncType.FULL);
    failed.setStatus(DramaSyncStatus.FAILED);
    failed.setPageNo(3);
    when(checkpointMapper.find(3L, DramaSyncType.FULL, "ENGLISH")).thenReturn(failed);
    when(checkpointMapper.findById(11L)).thenReturn(failed);

    var tasks = service.requestScheduledFull(7L, List.of("ENGLISH"));

    assertThat(tasks).singleElement()
            .extracting(task -> task.syncType()).isEqualTo(DramaSyncType.FULL);
    verify(checkpointMapper).requestRun(
            11L, LocalDateTime.of(2026, 8, 20, 8, 0), true);
    verify(displayRunService).createRun(7L, null, DramaSyncDomain.CATALOG,
            DramaSyncTaskType.FULL, SyncTriggerSource.SCHEDULED,
            LocalDateTime.of(2026, 8, 20, 8, 0));
}

@Test
@DisplayName("定时全量只跳过存在活动任务的语言")
void scheduledFullSkipsOnlyActiveLanguage() {
    when(runtimeService.resolve(7L, ProviderCapability.FULL_DRAMA_SYNC)).thenReturn(runtime());
    when(connectionMapper.lockById(3L))
            .thenReturn(mock(com.kasi.backend.provider.entity.ShortDramaConnection.class));
    when(checkpointMapper.findActive(3L, "ENGLISH"))
            .thenReturn(List.of(checkpoint(12L, DramaSyncType.INCREMENTAL)));
    ProviderSyncCheckpoint spanish = checkpoint(13L, DramaSyncType.FULL);
    spanish.setLanguage("SPANISH");
    when(checkpointMapper.find(3L, DramaSyncType.FULL, "SPANISH")).thenReturn(spanish);
    when(checkpointMapper.findById(13L)).thenReturn(spanish);

    var tasks = service.requestScheduledFull(7L, List.of("ENGLISH", "SPANISH"));

    assertThat(tasks).singleElement().extracting(task -> task.language()).isEqualTo("SPANISH");
    verify(checkpointMapper, never()).requestRun(eq(12L), any(), anyBoolean());
    verify(checkpointMapper).requestRun(eq(13L), any(), eq(true));
}
```

- [x] **Step 2: 运行聚焦测试确认 RED**

```powershell
cd E:\JavaProjects\kasi-project\kasi-backend
.\mvnw.cmd "-Dtest=DramaCatalogSyncServiceTest" test
```

Expected: 编译失败，提示 `requestScheduledFull` 不存在。

- [x] **Step 3: 声明并实现最小定时全量入口**

在接口增加：

```java
List<DramaSyncTaskVO> requestScheduledFull(Long providerId, List<String> languages);
```

在实现类增加与现有定时增量相同事务边界的直接实现：

```java
@Override
@Transactional
public List<DramaSyncTaskVO> requestScheduledFull(Long providerId, List<String> languages) {
    ProviderRuntimeConnection runtime;
    try {
        runtime = runtimeService.resolve(providerId, ProviderCapability.FULL_DRAMA_SYNC);
    } catch (BusinessException exception) {
        return List.of();
    }
    if (!(runtime.adapter() instanceof DramaCatalogProviderAdapter)) {
        return List.of();
    }
    connectionMapper.lockById(runtime.connectionId());
    List<DramaSyncTaskVO> tasks = new ArrayList<>();
    List<Long> checkpointIds = new ArrayList<>();
    LocalDateTime now = LocalDateTime.now(clock);
    for (String language : normalizeLanguages(languages)) {
        if (!checkpointMapper.findActive(runtime.connectionId(), language).isEmpty()) {
            continue;
        }
        ProviderSyncCheckpoint checkpoint = ensureCheckpoint(
                runtime.connectionId(), DramaSyncType.FULL, language);
        if (checkpointMapper.requestRun(checkpoint.getId(), now, true) != 1) {
            continue;
        }
        ProviderSyncCheckpoint requested = checkpointMapper.findById(checkpoint.getId());
        if (requested == null) {
            throw new IllegalStateException("Requested catalog checkpoint cannot be reloaded");
        }
        tasks.add(DramaSyncTaskVO.from(requested));
        checkpointIds.add(requested.getId());
    }
    if (tasks.isEmpty()) {
        return tasks;
    }
    DramaSyncDisplayRun run = displayRunService.createRun(providerId, null, DramaSyncDomain.CATALOG,
            DramaSyncTaskType.FULL, SyncTriggerSource.SCHEDULED, now);
    displayRunService.createRun(providerId, run.getId(), DramaSyncDomain.CONTENT,
            DramaSyncTaskType.CATALOG_AUTO, SyncTriggerSource.SCHEDULED, now);
    for (Long checkpointId : checkpointIds) {
        displayRunService.attachTask(run.getId(), DramaSyncDomain.CATALOG, checkpointId);
    }
    return tasks;
}
```

不调用 `triggerAfterCommit`，保持与现有固定增量任务一致，由目录 worker 的现有兜底扫描消费。

- [x] **Step 4: 运行测试确认 GREEN**

```powershell
cd E:\JavaProjects\kasi-project\kasi-backend
.\mvnw.cmd "-Dtest=DramaCatalogSyncServiceTest" test
```

Expected: `DramaCatalogSyncServiceTest` 全部通过。

- [x] **Step 5: 复核任务范围并停靠**

确认没有抽取新的 Runner/Manager、没有改变手动同步或定时增量契约；不提交。

### Task 3: 全量成功时原子执行缺失对账

**Files:**

- Modify: `kasi-backend/src/test/java/com/kasi/backend/drama/service/DramaCatalogSyncServiceTest.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/drama/service/impl/DramaCatalogSyncServiceImpl.java`

- [x] **Step 1: 写成功、失败和租约边界失败测试**

先让测试 helper 为 checkpoint 设置固定快照时间：

```java
checkpoint.setRequestedAt(LocalDateTime.of(2026, 8, 20, 7, 0));
```

扩展 `processPageThenAdvancesCheckpoint()` 的顺序断言：

```java
order.verify(checkpointMapper).markSuccess(11L, "worker-test",
        LocalDateTime.of(2026, 8, 20, 8, 0), 2, null);
order.verify(dramaMapper).markMissingAfterFullSync(
        3L, "ENGLISH", LocalDateTime.of(2026, 8, 20, 7, 0));
```

在第二页失败和进度更新丢失租约的测试中增加：

```java
verify(dramaMapper, never()).markMissingAfterFullSync(anyLong(), any(), any());
```

新增增量成功不执行缺失对账测试：

```java
@Test
@DisplayName("增量同步成功不执行全量缺失对账")
void incrementalSuccessDoesNotMarkMissingDramas() {
    ProviderSyncCheckpoint checkpoint = checkpoint(12L, DramaSyncType.INCREMENTAL);
    ProviderSyncCheckpoint full = checkpoint(11L, DramaSyncType.FULL);
    full.setStatus(DramaSyncStatus.SUCCESS);
    full.setLastSuccessAt(LocalDateTime.of(2026, 8, 19, 8, 0));
    when(checkpointMapper.findDue(any(), eq(10))).thenReturn(List.of(checkpoint));
    when(checkpointMapper.claimLease(eq(12L), eq("worker-test"), any(), any())).thenReturn(1);
    when(checkpointMapper.findById(12L)).thenReturn(checkpoint);
    when(checkpointMapper.find(3L, DramaSyncType.FULL, "ENGLISH")).thenReturn(full);
    var connection = new com.kasi.backend.provider.entity.ShortDramaConnection();
    connection.setProviderId(7L);
    when(connectionMapper.findById(3L)).thenReturn(connection);
    when(runtimeService.resolve(7L, ProviderCapability.INCREMENTAL_DRAMA_SYNC)).thenReturn(runtime());
    when(adapter.fetchIncrementalDramas(any(), any())).thenReturn(
            new DramaCatalogPage(List.of(), 1, 50, 0, false, 1700000000123L));
    when(checkpointMapper.updateProgress(anyLong(), anyString(), anyInt(), any(), anyInt(), anyInt(),
            anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(1);
    when(checkpointMapper.markSuccess(anyLong(), anyString(), any(), anyInt(), any())).thenReturn(1);

    service.processDueBatch();

    verify(checkpointMapper).markSuccess(eq(12L), eq("worker-test"), any(), anyInt(), any());
    verify(dramaMapper, never()).markMissingAfterFullSync(anyLong(), any(), any());
}
```

- [x] **Step 2: 运行聚焦测试确认 RED**

```powershell
cd E:\JavaProjects\kasi-project\kasi-backend
.\mvnw.cmd "-Dtest=DramaCatalogSyncServiceTest" test
```

Expected: 全量成功测试因缺少 `markMissingAfterFullSync` 调用而失败。

- [x] **Step 3: 将成功收口和缺失更新放入同一事务**

把 `fetchPages()` 末尾的直接 `markSuccess` 替换为：

```java
LocalDateTime finishedAt = LocalDateTime.now(clock);
transactionTemplate.executeWithoutResult(status -> {
    if (checkpointMapper.markSuccess(checkpoint.getId(), workerId,
            finishedAt, pageNo, updateTime) != 1) {
        throw new LeaseLostException();
    }
    if (effectiveType == DramaSyncType.FULL) {
        if (checkpoint.getRequestedAt() == null) {
            throw new IllegalStateException("Full catalog snapshot start time is missing");
        }
        dramaMapper.markMissingAfterFullSync(runtime.connectionId(),
                checkpoint.getLanguage(), checkpoint.getRequestedAt());
    }
});
```

必须保持调用顺序为先 `markSuccess` 验证 `RUNNING + lease_owner`，再更新目录；同一事务失败时两项一起回滚。`markMissingAfterFullSync` 返回 `0` 表示没有缺失记录，属于正常成功。

- [x] **Step 4: 运行目录持久层和服务测试确认 GREEN**

```powershell
cd E:\JavaProjects\kasi-project\kasi-backend
.\mvnw.cmd "-Dtest=DramaCatalogPersistenceTest,DramaCatalogSyncServiceTest" test
```

Expected: 两个测试类全部通过；页面失败、租约丢失和增量成功路径均不执行缺失对账。

- [x] **Step 5: 复核事务和失败语义并停靠**

检查本次 diff，确认未把分页远端调用包入数据库长事务，只有末尾成功收口与批量更新共用短事务；不提交。

### Task 4: 增加每天 03:00 固定任务、迁移和分发

**Files:**

- Create: `kasi-backend/src/main/resources/db/migration/V11__add_goodshort_drama_full_sync_task.sql`
- Modify: `kasi-backend/src/main/resources/db/kasi_promotion.sql`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/scheduledtask/enums/ScheduledTaskCode.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/scheduledtask/service/impl/ScheduledTaskDispatchServiceImpl.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/BaseAuthTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/ScheduledTaskMigrationTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/scheduledtask/controller/ScheduledTaskControllerTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/scheduledtask/service/ScheduledTaskDispatchServiceTest.java`

- [x] **Step 1: 写固定数据、API 和分发失败测试**

在 `ScheduledTaskMigrationTest` 增加初始化断言，并使用相同查询 helper 分别验证 `kasi_promotion.sql` 与 V1 + V11：

```java
private void assertDailyDramaFullTask(JdbcTemplate jdbc) {
    Map<String, Object> task = jdbc.queryForMap("""
            SELECT task_code, description, cycle_type, time_of_day, enabled, next_run_at
            FROM system_scheduled_task
            WHERE task_code = 'GOODSHORT_DRAMA_FULL_SYNC'
            """);
    assertThat(task.get("DESCRIPTION")).isEqualTo("每天 03:00 同步 GoodShort 全量短剧目录");
    assertThat(task.get("CYCLE_TYPE")).isEqualTo("DAILY");
    assertThat(task.get("TIME_OF_DAY").toString()).startsWith("03:00");
    assertThat(((Number) task.get("ENABLED")).intValue()).isEqualTo(1);
    assertThat(task.get("NEXT_RUN_AT")).isNotNull();
}
```

V11 执行测试使用以下完整步骤，并补充 `ClassPathResource`、`ResourceDatabasePopulator` import：

```java
@Test
@DisplayName("V11迁移植入每天三点的GoodShort目录全量任务")
void v11AddsDailyGoodShortDramaFullTask() {
    JdbcTemplate jdbc = initializeDatabase(
            "scheduled_task_v11", "db/migration/V1__baseline.sql");
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
    populator.addScript(new ClassPathResource(
            "db/migration/V11__add_goodshort_drama_full_sync_task.sql"));
    populator.execute(jdbc.getDataSource());

    assertDailyDramaFullTask(jdbc);
}
```

在 `ScheduledTaskControllerTest` 的普通管理员查询断言中增加：

```java
.andExpect(jsonPath("$.data[?(@.taskCode == 'GOODSHORT_DRAMA_FULL_SYNC')].title")
        .value(hasItem("GoodShort 短剧目录全量同步")))
```

在 `ScheduledTaskDispatchServiceTest` 增加：

```java
@Test
@DisplayName("到期GoodShort全量任务按配置语言入队并推进到次日三点")
void dueGoodShortFullTaskIsDispatchedAndAdvanced() {
    SystemScheduledTask task = scheduledFullTask();
    when(taskMapper.findDue(NOW, 10)).thenReturn(List.of(task));
    when(taskMapper.claimLease(ScheduledTaskCode.GOODSHORT_DRAMA_FULL_SYNC,
            "scheduled-worker-test", NOW, NOW.plusMinutes(2))).thenReturn(1);
    ShortDramaProvider provider = new ShortDramaProvider();
    provider.setId(7L);
    provider.setProviderCode("GOODSHORT");
    provider.setStatus(1);
    when(providerMapper.findByCode("GOODSHORT")).thenReturn(provider);

    service.processDueBatch();

    verify(syncService).requestScheduledFull(7L, DramaSyncProperties.DEFAULT_LANGUAGES);
    verify(taskMapper).completeRun(ScheduledTaskCode.GOODSHORT_DRAMA_FULL_SYNC,
            "scheduled-worker-test", LocalDateTime.of(2026, 8, 21, 3, 0));
}

private SystemScheduledTask scheduledFullTask() {
    SystemScheduledTask task = scheduledTask();
    task.setTaskCode(ScheduledTaskCode.GOODSHORT_DRAMA_FULL_SYNC);
    task.setCycleType(ScheduledTaskCycleType.DAILY);
    task.setIntervalValue(null);
    task.setTimeOfDay(java.time.LocalTime.of(3, 0));
    return task;
}
```

- [x] **Step 2: 运行聚焦测试确认 RED**

```powershell
cd E:\JavaProjects\kasi-project\kasi-backend
.\mvnw.cmd "-Dtest=ScheduledTaskMigrationTest,ScheduledTaskControllerTest,ScheduledTaskDispatchServiceTest" test
```

Expected: 编译或断言失败，因为新枚举、V11 数据、测试 fixture 和分发分支尚不存在。

- [x] **Step 3: 增加枚举、V11 和开发初始化数据**

枚举增加：

```java
GOODSHORT_DRAMA_FULL_SYNC("GoodShort 短剧目录全量同步"),
```

V11 使用可重复执行保护和下一个 03:00 初始时间：

```sql
INSERT INTO system_scheduled_task
    (task_code, description, cycle_type, time_of_day, enabled, next_run_at)
SELECT 'GOODSHORT_DRAMA_FULL_SYNC',
       '每天 03:00 同步 GoodShort 全量短剧目录',
       'DAILY', '03:00:00', 1,
       CASE
           WHEN CURRENT_TIME < '03:00:00' THEN TIMESTAMPADD(HOUR, 3, CURRENT_DATE)
           ELSE TIMESTAMPADD(DAY, 1, TIMESTAMPADD(HOUR, 3, CURRENT_DATE))
       END
WHERE NOT EXISTS (
    SELECT 1 FROM system_scheduled_task
    WHERE task_code = 'GOODSHORT_DRAMA_FULL_SYNC'
);
```

`kasi_promotion.sql` 写入与 V11 相同的 `INSERT ... SELECT ... WHERE NOT EXISTS`。`BaseAuthTest` 增加以下 fixture，避免接口测试只看见旧任务：

```java
jdbcTemplate.update("""
        INSERT INTO system_scheduled_task
            (task_code, description, cycle_type, time_of_day, enabled, next_run_at)
        VALUES (?, ?, 'DAILY', '03:00:00', 1, TIMESTAMPADD(HOUR, 3, CURRENT_DATE))
        """,
        "GOODSHORT_DRAMA_FULL_SYNC", "每天 03:00 同步 GoodShort 全量短剧目录");
```

- [x] **Step 4: 增加调度分发分支**

在 switch 中增加：

```java
case GOODSHORT_DRAMA_FULL_SYNC -> dispatchGoodShortDramaFull();
```

增加直接分发方法：

```java
private void dispatchGoodShortDramaFull() {
    ShortDramaProvider provider = providerMapper.findByCode("GOODSHORT");
    if (provider == null) {
        return;
    }
    syncService.requestScheduledFull(provider.getId(), dramaProperties.getLanguages());
}
```

不改变既有异常推进规则、租约和周期计算器。

- [x] **Step 5: 运行固定任务测试确认 GREEN**

```powershell
cd E:\JavaProjects\kasi-project\kasi-backend
.\mvnw.cmd "-Dtest=ScheduledTaskMigrationTest,ScheduledTaskControllerTest,ScheduledTaskDispatchServiceTest" test
```

Expected: 三个测试类全部通过，V11 能在 H2 MySQL 模式执行。

- [x] **Step 6: 复核迁移边界并停靠**

确认没有修改 V1-V10，开发初始化和 V11 的任务说明、周期、时间、启用状态一致；不运行生产 Flyway，不提交。

### Task 5: 管理端展示“全量未返回”

**Files:**

- Modify: `kasi-admin-web/src/pages/drama/DramaCatalogPage.test.tsx`
- Modify: `kasi-admin-web/src/pages/drama/DramaCatalogPage.tsx`

- [x] **Step 1: 写列表和详情失败测试**

增加 fixture：

```tsx
const missingDrama = {
  ...publishedDrama,
  id: 10,
  externalDramaId: 'book-1003',
  title: 'Removed From Catalog',
  remoteShowStatus: 'MISSING',
  localStatus: 'OFFLINE',
}
```

把目录 handler 的列表加入 `missingDrama` 并增加断言：

```tsx
expect(
  within(await screen.findByTestId('mock-row-10')).getByText('全量未返回'),
).toBeInTheDocument()
```

详情测试使用以下 handler 和断言，证明哨兵值不会被描述成甲方原始值：

```tsx
server.use(
  http.get('/api/admin/drama/catalog/10', () =>
    HttpResponse.json({
      code: 0,
      message: 'ok',
      data: {
        ...missingDrama,
        description: null,
        createdAt: '2026-08-20T10:35:00',
        contents: [],
      },
    }),
  ),
)
const user = userEvent.setup()
renderPage()
await screen.findByTestId('mock-row-10')
await user.click(screen.getByTestId('drama-detail-10'))
const drawer = await screen.findByTestId('drama-detail-drawer')
expect(
  within(drawer).getByText('本地判断：最近一次完整目录未返回'),
).toBeInTheDocument()
expect(within(drawer).queryByText('原始值：MISSING')).not.toBeInTheDocument()
```

- [x] **Step 2: 运行管理端聚焦测试确认 RED**

```powershell
cd E:\JavaProjects\kasi-project\kasi-admin-web
pnpm test -- src/pages/drama/DramaCatalogPage.test.tsx
```

Expected: `MISSING` 当前被显示为“已下架”，新增断言失败。

- [x] **Step 3: 实现最小状态映射和详情说明**

更新状态标签：

```tsx
function RemoteStatusTag({ status }: { status: string | null }) {
  if (!status) return <Tag>未知</Tag>
  if (status === '1') return <Tag color="success">在线</Tag>
  if (status === 'MISSING') return <Tag>全量未返回</Tag>
  return <Tag color="warning">已下架</Tag>
}
```

详情状态值改为：

```tsx
{detail.remoteShowStatus === 'MISSING' ? (
  <span>本地判断：最近一次完整目录未返回</span>
) : detail.remoteShowStatus ? (
  <span>原始值：{detail.remoteShowStatus}</span>
) : null}
```

不新增 DTO、API 字段或新的页面操作。

- [x] **Step 4: 运行管理端聚焦测试确认 GREEN**

```powershell
cd E:\JavaProjects\kasi-project\kasi-admin-web
pnpm test -- src/pages/drama/DramaCatalogPage.test.tsx
```

Expected: 该测试文件全部通过。

- [x] **Step 5: 复核 UI 范围并停靠**

确认 `1` 仍为“在线”、其他甲方非在线值仍为“已下架”、`MISSING` 单独显示；不改样式文件，不提交。

### Task 6: 同步当前文档并执行完整 Gate

**Files:**

- Modify: `kasi-backend/AGENTS.md`
- Modify: `kasi-backend/README.md`
- Modify: `docs/architecture/current.md`
- Modify: `docs/superpowers/specs/2026-09-12-goodshort-drama-catalog-full-sync-design.md`
- Modify: `docs/superpowers/plans/2026-09-12-goodshort-drama-catalog-full-sync.md`

- [x] **Step 1: 更新已实施行为文档**

实施和聚焦测试通过后再更新 current docs，明确写入：

```text
GOODSHORT_DRAMA_FULL_SYNC 默认每天 03:00 为配置中的全部语言创建全量目录任务。
单语言全量完整成功后，本轮未返回的历史短剧标记为 MISSING / OFFLINE；同步失败不批量下架。
短剧重新出现时恢复真实远端状态和最近可见时间，但本地仍保持下架，必须由管理员手动上架。
```

删除或改写 `kasi-backend/AGENTS.md`、`README.md` 中“本次未返回历史短剧不处理”“首次全量只能手动完成”等已经过时的描述。设计文档状态改为“已实施并验证”，计划勾选实际完成项；未实际运行的真实环境验证不得标为完成。

- [x] **Step 2: 运行后端聚焦回归**

```powershell
cd E:\JavaProjects\kasi-project\kasi-backend
.\mvnw.cmd "-Dtest=DramaCatalogPersistenceTest,DramaCatalogSyncServiceTest,ScheduledTaskMigrationTest,ScheduledTaskControllerTest,ScheduledTaskDispatchServiceTest" test
```

Expected: 所列测试全部通过，记录 tests run、failures、errors、skipped 和退出码。

- [x] **Step 3: 运行后端 canonical Gate**

```powershell
cd E:\JavaProjects\kasi-project\kasi-backend
.\mvnw.cmd verify
```

Expected: exit code 0；记录测试总数和 SKIP。不得沿用此前 531 tests 的历史结果。

Result: 使用命令局部 JDK 25 运行，537 tests、0 failures、0 errors、1 skipped，exit code 0。已有 JaCoCo class-data mismatch warning 保留可见，不影响 Gate 结果。

- [x] **Step 4: 运行管理端 canonical Gate**

```powershell
cd E:\JavaProjects\kasi-project\kasi-admin-web
pnpm check
```

Expected: lint、format check、Vitest、TypeScript build 全部成功，exit code 0。

Result: 首次运行在 `DramaCatalogPage.test.tsx` 的 Prettier 检查失败；仅格式化该本次文件后完整重跑，lint、format check、21 test files / 111 tests、TypeScript build 和 Vite build 全部成功，exit code 0。

- [x] **Step 5: 运行根目录差异检查**

```powershell
cd E:\JavaProjects\kasi-project
git diff --check
git status --short --branch
```

Expected: `git diff --check` exit code 0；状态中保留用户原有无关改动，本功能文件范围与计划一致。

Result: 最终 `git diff --check` exit code 0；工作区仍保留用户原有报白、订单、文档等未提交改动，本次未重置、覆盖或清理。

- [x] **Step 6: 记录条件验证**

```powershell
cd E:\JavaProjects\kasi-project\kasi-backend
.\mvnw.cmd -Pmysql-contract-tests "-Dtest=*MySqlContractIT" test
```

Expected: 配置 `MYSQL_CONTRACT_URL` 和 `MYSQL_MIGRATION_URL` 时必须实际 PASS；缺失时明确记录 MySQL/Flyway `SKIP`。真实 GoodShort 不调用，记录 `SKIP`。

Result: `MYSQL_CONTRACT_URL` 和 `MYSQL_MIGRATION_URL` 均未配置，MySQL Contract 与 Flyway 真实迁移验证记为 `SKIP`；未调用真实 GoodShort，记为 `SKIP`。

- [x] **Step 7: Simplification Review 和停靠**

只检查本次文件及直接调用链：没有新 schema、没有新 Controller API、没有自动本地上架、没有物理删除、没有新增 Runner/Manager。汇总 RED/GREEN、完整 Gate、SKIP 和文件清单后停止，等待用户审查；不提交、不推送。

Result: 复核通过。实现复用现有 checkpoint、worker、展示记录、事务模板和定时分发器；数据库只新增固定任务数据，没有新增业务表字段，没有新 Controller API、自动本地上架、物理删除或新增 Runner/Manager。不提交、不推送。
