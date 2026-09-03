# Logical Relations Schema Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重建一份只保存当前真实业务数据的单一初始化 schema，数据库层不使用物理外键或级联，并删除 `promotion_task` 及已确认无消费者、重复或兼容字段。

**Architecture:** 所有 `*_id` 保留为普通关联 ID；Service 在写入时校验必要的存在性、归属和平台链路一致性。订单、推广链接、佣金历史和审计记录作为历史事实独立保留，主体删除不会触发数据库删除；临时下载任务仅由下载 Service 显式按过期时间清理。`system_scheduled_task` 以 `task_code` 作为主键，固定标题由 `ScheduledTaskCode` 提供。

**Tech Stack:** Java 25、Spring Boot 4、MyBatis XML、MySQL 8、H2 MySQL mode、JUnit 5、Maven Wrapper

---

### Task 1: Lock the schema contract in existing structure tests

**Files:**
- Modify: `src/test/java/com/kasi/backend/DefaultSuperAdminMigrationTest.java`
- Modify: `src/test/java/com/kasi/backend/PromotionLinkMigrationTest.java`
- Modify: `src/test/java/com/kasi/backend/PromotionOrderMigrationTest.java`
- Modify: `src/test/java/com/kasi/backend/ProviderDramaPromotionMetadataMigrationTest.java`
- Modify: `src/test/java/com/kasi/backend/ScheduledTaskMigrationTest.java`
- Modify: `src/test/java/com/kasi/backend/DramaDownloadTaskMigrationTest.java`
- Modify: `src/test/java/com/kasi/backend/support/DatabaseInitializationTestSupport.java`
- Modify: `src/test/resources/test-schema.sql`

- [ ] **Step 1: Replace physical-foreign-key assertions with zero-constraint assertions**

Update the existing structure tests to assert that the initialized schema has no imported keys and that the production SQL text contains none of the forbidden clauses:

```java
String sql = Files.readString(resourcePath, StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
assertThat(sql).doesNotContain("FOREIGN KEY", "REFERENCES", "ON DELETE CASCADE",
        "ON DELETE RESTRICT", "ON DELETE SET NULL");
```

For the executed H2 database, use `DatabaseMetaData.getImportedKeys(null, null, tableName)` and assert the result set has zero rows for every initialized table.

- [ ] **Step 2: Update existing column assertions for the approved removals**

Assert that the new schema omits `promotion_task`, `system_scheduled_task.id/title/interval_minutes/created_at/updated_at`, the two `promotion_link` fields, `promotion_order.media_account_id/first_synced_at`, the checkpoint fields `started_at/finished_at/total_upserted/skipped_count`, and the approved timestamp columns in filing, content, provider, and download tables. Assert that `provider_sync_checkpoint.total_fetched` remains present.

- [ ] **Step 3: Add the two missing history-retention cases to existing tests**

The current persistence tests have no parent-deletion coverage. Add one method to `PromotionOrderPersistenceTest` that inserts an order with `user_id`, deletes that user, and then reads the order by source. Add one method to `PromotionLinkPersistenceTest` that deletes the provider and drama after inserting a link, then reads the page and asserts the link remains with nullable display names. Do not create a new test class.

- [ ] **Step 4: Run the adjusted structure tests and record the expected failures**

Run:

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd --% -Dtest=DefaultSuperAdminMigrationTest,PromotionLinkMigrationTest,PromotionOrderMigrationTest,ProviderDramaPromotionMetadataMigrationTest,ScheduledTaskMigrationTest,DramaDownloadTaskMigrationTest test
```

The tests should fail only on the not-yet-updated schema and mappings; do not broaden the test set or add unrelated cases.

### Task 2: Rewrite the single production and test schema

**Files:**
- Modify: `src/main/resources/db/kasi_promotion.sql`
- Modify: `src/test/resources/test-schema.sql`

- [ ] **Step 1: Remove every database-level relationship clause**

Delete every `CONSTRAINT ... FOREIGN KEY`, `FOREIGN KEY (...) REFERENCES ...`, `ON DELETE CASCADE`, and `ON DELETE RESTRICT` clause from both schema files. Keep the referenced `*_id` columns as ordinary columns, and retain only the existing primary keys, unique keys, checks, and query indexes that still reference surviving columns.

- [ ] **Step 2: Remove the `promotion_task` table definition and seed references**

Delete the complete `CREATE TABLE promotion_task` block and its indexes. Do not replace it with another task table or a compatibility view.

- [ ] **Step 3: Make scheduled tasks use `task_code` as the primary key**

Change the table definition to use:

```sql
`task_code` VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '固定任务编码',
```

Remove `id`, `title`, `interval_minutes`, `created_at`, `updated_at`, the old unique key on `task_code`, and the old interval check. Keep the structured cycle fields, `description`, `enabled`, `next_run_at`, `lease_owner`, and `lease_until`. Seed only the two fixed task codes and their structured cycles.

- [ ] **Step 4: Remove approved redundant and unreachable columns**

Apply the exact list from the design document. Keep `provider_sync_checkpoint.total_fetched` as the upstream fetched count. Remove only `total_upserted` and `skipped_count`; keep `inserted_count`, `updated_count`, and `error_count`.

- [ ] **Step 5: Remove indexes whose leading columns no longer exist**

Drop the scheduled-task indexes tied to `id` or removed timestamps and drop `idx_drama_download_user_created` with `created_at`. Preserve indexes for `next_run_at/lease_until`, link idempotency and tracking, order source/user/attribution lookups, checkpoint due scans, and download expiration scans.

- [ ] **Step 6: Execute both schemas in isolated H2 and verify identical table/column sets**

Use the existing `DatabaseInitializationTestSupport` to execute `kasi_promotion.sql`; execute `test-schema.sql` through the existing test setup. Compare `DatabaseMetaData` table and column names and assert no imported keys in either database.

### Task 3: Remove the unused promotion-task module

**Files:**
- Delete: `src/main/java/com/kasi/backend/promotion/controller/UserPromotionTaskController.java`
- Delete: `src/main/java/com/kasi/backend/promotion/dto/CreatePromotionTaskDTO.java`
- Delete: `src/main/java/com/kasi/backend/promotion/dto/PromotionTaskPageQueryDTO.java`
- Delete: `src/main/java/com/kasi/backend/promotion/entity/PromotionTask.java`
- Delete: `src/main/java/com/kasi/backend/promotion/enums/PromotionTaskStatus.java`
- Delete: `src/main/java/com/kasi/backend/promotion/mapper/PromotionTaskMapper.java`
- Delete: `src/main/java/com/kasi/backend/promotion/service/PromotionTaskService.java`
- Delete: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionTaskServiceImpl.java`
- Delete: `src/main/java/com/kasi/backend/promotion/vo/PromotionTaskVO.java`
- Delete: `src/main/java/com/kasi/backend/promotion/vo/PromotionTaskPageVO.java`
- Delete: `src/main/resources/mapper/PromotionTaskMapper.xml`
- Delete: `src/test/java/com/kasi/backend/promotion/controller/UserPromotionTaskControllerTest.java`
- Delete: `src/test/java/com/kasi/backend/promotion/service/PromotionTaskServiceTest.java`
- Modify: `README.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: Remove Java and XML references**

Delete the files above and remove `PromotionTask` imports, error-code references, test fixtures, and active documentation statements. Do not add a replacement endpoint.

- [ ] **Step 2: Compile the promotion package**

Run `.\mvnw.cmd -DskipTests test-compile` with JDK 25 and confirm no remaining `PromotionTask` type or `/api/user/promotion/tasks` string exists under `src/main` or active README/AGENTS sections.

### Task 4: Normalize scheduled-task persistence without changing its API meaning

**Files:**
- Modify: `src/main/java/com/kasi/backend/scheduledtask/entity/SystemScheduledTask.java`
- Modify: `src/main/java/com/kasi/backend/scheduledtask/enums/ScheduledTaskCode.java`
- Modify: `src/main/java/com/kasi/backend/scheduledtask/vo/ScheduledTaskVO.java`
- Modify: `src/main/java/com/kasi/backend/scheduledtask/dto/UpdateScheduledTaskDTO.java`
- Modify: `src/main/java/com/kasi/backend/scheduledtask/mapper/SystemScheduledTaskMapper.java`
- Modify: `src/main/resources/mapper/SystemScheduledTaskMapper.xml`
- Modify: `src/main/java/com/kasi/backend/scheduledtask/service/impl/ScheduledTaskManagementServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/scheduledtask/service/impl/ScheduledTaskDispatchServiceImpl.java`
- Modify: existing scheduled-task tests under `src/test/java/com/kasi/backend/scheduledtask`

- [ ] **Step 1: Remove storage-only fields and add enum display titles**

Remove `id`, `title`, `intervalMinutes`, `createdAt`, and `updatedAt` from `SystemScheduledTask`. Add a display-title property to `ScheduledTaskCode` and have `ScheduledTaskVO.from` use `task.getTaskCode().title()` so the response still contains the fixed title without storing it.

- [ ] **Step 2: Remove the legacy request field**

Delete `intervalMinutes` from `UpdateScheduledTaskDTO` and make `cycleType` plus `intervalValue` the only interval input. `isScheduleValid()` must reject a missing `cycleType` or `intervalValue` instead of falling back to the removed field. Keep all calendar-field validation unchanged.

- [ ] **Step 3: Change mapper identity and lease methods to `task_code`**

Change `claimLease` and `completeRun` to accept `ScheduledTaskCode taskCode`, and use `WHERE task_code = #{taskCode}`. `findAll` and `findDue` order by `task_code` after the due-time comparison; no query selects `id`, `title`, or timestamps. Lease updates set only lease columns, and configuration updates set only surviving schedule columns.

- [ ] **Step 4: Update management and dispatch services**

Use `task.getTaskCode()` when completing a run. Remove the old null-cycle fallback to `task.getIntervalMinutes()`; the schedule calculator is the sole source for the next run. Keep the existing lease transaction and no new global locking or retry mechanism.

- [ ] **Step 5: Run existing scheduled-task tests**

Run:

```powershell
.\mvnw.cmd --% -Dtest=ScheduledTaskControllerTest,ScheduledTaskDispatchServiceTest,SystemScheduledTaskPersistenceTest,ScheduledTaskMigrationTest test
```

Update fixtures to send structured cycle fields and assert that the response title is enum-derived.

### Task 5: Remove link/order storage fields while preserving history snapshots

**Files:**
- Modify: `src/main/java/com/kasi/backend/promotion/entity/PromotionLink.java`
- Modify: `src/main/java/com/kasi/backend/promotion/vo/PromotionLinkVO.java`
- Modify: `src/main/java/com/kasi/backend/promotion/mapper/PromotionLinkMapper.java`
- Modify: `src/main/resources/mapper/PromotionLinkMapper.xml`
- Modify: `src/main/java/com/kasi/backend/promotion/service/PromotionLinkPersistenceService.java`
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionLinkPersistenceServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionLinkServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/provider/spi/PromotionLinkResult.java`
- Modify: `src/main/java/com/kasi/backend/provider/goodshort/dto/GoodShortPromotionLinkResponse.java`
- Modify: `src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java`
- Modify: `src/main/java/com/kasi/backend/promotion/entity/PromotionOrder.java`
- Modify: `src/main/java/com/kasi/backend/promotion/vo/PromotionOrderVO.java`
- Modify: `src/main/resources/mapper/PromotionOrderMapper.xml`
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderAdminServiceImpl.java`
- Modify: existing promotion link/order tests

- [ ] **Step 1: Remove link-only duplicate fields**

Remove `providerCode` and `customParams` from the link entity, response, result object, mapper result map, insert/update SQL, and service signatures. GoodShort link generation may still send `trackingNo` as the provider parameter, but the link table does not persist the returned custom parameter. Remove the unused Java-only `mediaAccountId`, `mediaAccountName`, and `landingType` fields at the same time.

- [ ] **Step 2: Keep link source snapshot IDs and make history queries parent-tolerant**

Keep `provider_id`, `connection_id`, `drama_id`, and `user_id` in `promotion_link`. Change `findPageByUserId` from inner joins to `LEFT JOIN` so a deleted provider or drama does not hide the historical link; nullable `provider_name` and `drama_title` are acceptable display values.

- [ ] **Step 3: Remove order fields with proven no business meaning**

Remove `mediaAccountId` and `firstSyncedAt` from the entity, VO, mapper result map, insert columns, source-column list, update code, and tests. The order's `created_at` remains the first persistence time. Keep order `provider_id`, `connection_id`, `user_id`, `drama_id`, `tracking_no`, attribution status, raw payload, sync window, rule history ID, and all fee-rate snapshots as independent historical facts.

- [ ] **Step 4: Preserve order custom-parameter attribution**

Keep `promotion_order.custom_params` unchanged. `PromotionOrderServiceImpl` must continue to pass `record.customParams()` to `linkMapper.findByTrackingNo(...)`; do not replace this with the removed link-table custom parameter or with a join to a live parent.

- [ ] **Step 5: Update existing link/order persistence tests**

Remove obsolete column writes and assertions. Keep tests for idempotent link variants, tracking lookup, order source uniqueness, attribution, refund reversal, and fee snapshots. Run:

```powershell
.\mvnw.cmd --% -Dtest=PromotionLinkPersistenceTest,PromotionLinkServiceTest,PromotionOrderPersistenceTest,PromotionOrderServiceTest,PromotionOrderUserServiceTest,UserPromotionLinkControllerTest,AdminPromotionOrderControllerTest test
```

### Task 6: Trim operational timestamps and derived checkpoint counters

**Files:**
- Modify: `src/main/java/com/kasi/backend/drama/entity/ProviderSyncCheckpoint.java`
- Modify: `src/main/java/com/kasi/backend/drama/mapper/ProviderSyncCheckpointMapper.java`
- Modify: `src/main/resources/mapper/ProviderSyncCheckpointMapper.xml`
- Modify: `src/main/java/com/kasi/backend/drama/vo/DramaSyncTaskVO.java`
- Modify: `src/main/java/com/kasi/backend/drama/vo/DramaSyncStatusVO.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/DramaCatalogSyncServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/promotion/entity/ProviderMediaFiling.java`
- Modify: `src/main/resources/mapper/ProviderMediaFilingMapper.xml`
- Modify: `src/main/java/com/kasi/backend/drama/entity/ProviderDramaContent.java`
- Modify: `src/main/resources/mapper/ProviderDramaMapper.xml`
- Modify: `src/main/java/com/kasi/backend/provider/entity/ShortDramaProvider.java`
- Modify: `src/main/java/com/kasi/backend/drama/download/entity/DramaDownloadTask.java`
- Modify: `src/main/resources/mapper/DramaDownloadTaskMapper.xml`
- Modify: existing drama, filing, provider and download tests

- [ ] **Step 1: Simplify checkpoint persistence**

Remove `startedAt`, `finishedAt`, `totalUpserted`, and `skippedCount` from the entity and result map. Change `updateProgress` to add only fetched, inserted, updated, and error counts. `markSuccess` receives a completion time only for `last_success_at`; `claimLease` no longer writes a start timestamp.

- [ ] **Step 2: Keep upstream fetched count distinct from writes**

Preserve `total_fetched` as the accumulated number returned by the provider. Change `PageStats` and the mapper call so fetched comes from `safeRecords.size()`, while `total_upserted` is calculated in `DramaSyncTaskVO.from` as `insertedCount + updatedCount`. Keep the response's existing `skippedCount` property as a computed zero for the current frontend contract; it must never be read from SQL.

- [ ] **Step 3: Remove unused local timestamps from filing, content, provider and download mappings**

Remove the approved timestamp properties and stop writing `updated_at = CURRENT_TIMESTAMP` in their mapper updates. For download tasks, retain explicit expiration cleanup and remove only the obsolete user/created-time index; do not change file deletion behavior.

- [ ] **Step 4: Run existing affected tests**

Run the focused drama/provider/download suites already present in the repository. Update SQL fixtures to omit removed columns; do not create a second batch of migration tests.

### Task 7: Enforce logical associations and history-safe reads in application code

**Files:**
- Modify only the existing Service/Mapper methods that currently depend on removed database constraints, including:
  - `src/main/java/com/kasi/backend/promotion/service/impl/PromotionLinkPersistenceServiceImpl.java`
  - `src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountServiceImpl.java`
  - `src/main/java/com/kasi/backend/promotion/service/impl/MediaFilingTaskServiceImpl.java`
  - `src/main/java/com/kasi/backend/drama/download/service/impl/DramaDownloadTaskServiceImpl.java`
  - `src/main/java/com/kasi/backend/user/service/impl/UserManagementServiceImpl.java`
  - `src/main/resources/mapper/PromotionLinkMapper.xml`
  - `src/main/resources/mapper/PromotionOrderMapper.xml`
  - `src/main/resources/mapper/ProviderCommissionRuleHistoryMapper.xml`
- Modify: existing tests only; add the minimal history-retention test from Task 1 if absent

- [ ] **Step 1: Keep write-time existence and ownership checks explicit**

Retain the existing checks for active users, provider/connection/drama consistency, media-account ownership, available commission rules, and download content ownership. Do not catch a database foreign-key exception; return the existing domain error or fail the transaction on an explicit missing-row check.

- [ ] **Step 2: Keep historical facts independent of live parents**

Do not add joins to parent tables to `PromotionOrderMapper.findBySource`, `findPage`, or monthly summaries. If a display query needs a parent label, use `LEFT JOIN`; never use an inner join for order, link, commission-history, or audit history rows.

- [ ] **Step 3: Keep temporary cleanup explicit and narrow**

Leave `DramaDownloadTaskService.cleanupExpired()` responsible for deleting expired rows and files. Do not add a generic parent-delete cascade, global lock, retry, or compensation framework. Historical orders and links no longer block user deletion; the existing media-account guard remains the only user-delete business rule, and provider/drama delete behavior remains unchanged because no such delete API exists.

- [ ] **Step 4: Verify current logical-association tests**

Run existing media filing, promotion link, order attribution, user deletion, and download cleanup tests. Add no new concurrency tests; the approved design explicitly excludes theoretical concurrent-delete machinery.

### Task 8: Synchronize active documentation and verify the final tree

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `DEVELOPMENT.md`
- Modify: `docs/architecture-decisions.md`
- Modify: `docs/superpowers/specs/2026-08-28-logical-relations-schema-cleanup-design.md`
- Leave historical `docs/superpowers/plans/*` and superseded design records unchanged

- [ ] **Step 1: Update current-state documentation**

Document 15 final tables, the single initialization SQL, zero physical foreign keys and cascades, logical-ID validation in Service, history-safe reads after parent deletion, explicit temporary cleanup, and the removal of `promotion_task`. Remove active statements claiming that `promotion_task` exists or that `interval_minutes` is a current schema field.

- [ ] **Step 2: Add the architecture decision**

Append an ADR entry marking the logical-association/no-cascade policy as the current decision, including the rejected physical-FK/cascade alternative, the disposable development database, and the no-migration rebuild procedure.

- [ ] **Step 3: Mark the design document implemented only after verification**

Change the design status from `设计已确认，实施计划已完成，待实施` to `已实施` only after all tests and schema checks pass. Keep the exact distinction between current implementation and future work.

- [ ] **Step 4: Run final verification commands**

Run the focused suites first, then the complete suite:

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
git diff --check
rg -n -i "FOREIGN KEY|REFERENCES|ON DELETE (CASCADE|RESTRICT|SET NULL)" src/main/resources/db/kasi_promotion.sql src/test/resources/test-schema.sql
rg -n "promotion_task|interval_minutes|first_synced_at|media_account_id|provider_code|custom_params" src/main/resources/db/kasi_promotion.sql src/test/resources/test-schema.sql src/main/java src/main/resources/mapper
git status --short --branch
```

The first `rg` command must return no matches. The second command may return `promotion_order.custom_params`, surviving source-snapshot fields, or historical documentation references only where the field is intentionally retained; inspect every match before claiming completion. Do not stage, commit, push, delete, or rebuild the user's current development database in this plan.
