# Drama Sync Record Aggregation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有短剧同步和剧集同步页面中，分别展示按一次触发聚合的统一同步记录表与子任务详情。

**Architecture:** 不修改 checkpoint、剧集任务、worker、租约或终态 SQL。新增独立展示运行表、任务关联表和只读聚合查询服务；两个前端路由分别消费目录与剧集记录接口，复用表格列结构。

**Tech Stack:** Java 25、Spring Boot、MyBatis、MySQL/H2、React、TypeScript、Ant Design、Vitest/MSW

---

### Task 1: Add display-run persistence

**Files:**
- Create: `src/main/java/com/kasi/backend/drama/entity/DramaSyncDisplayRun.java`
- Create: `src/main/java/com/kasi/backend/drama/entity/DramaSyncDisplayRunItem.java`
- Create: `src/main/java/com/kasi/backend/drama/enums/DramaSyncDomain.java`
- Create: `src/main/java/com/kasi/backend/drama/enums/DramaSyncTaskType.java`
- Create: `src/main/java/com/kasi/backend/drama/enums/SyncTriggerSource.java`
- Create: `src/main/java/com/kasi/backend/drama/mapper/DramaSyncDisplayRunMapper.java`
- Create: `src/main/resources/mapper/DramaSyncDisplayRunMapper.xml`
- Modify: `src/main/resources/db/kasi_promotion.sql`
- Modify: `src/test/resources/test-schema.sql`
- Modify: `src/test/java/com/kasi/backend/BaseAuthTest.java`
- Create: `src/test/java/com/kasi/backend/drama/mapper/DramaSyncDisplayRunPersistenceTest.java`

- [ ] **Step 1: Write the failing persistence test**

Insert a catalog run and two catalog items, read them back, then assert a second run can use the same schema without adding columns to either task table.

- [ ] **Step 2: Run the test and observe the expected missing-mapper failure**

Run: `./mvnw.cmd --% -Dtest=DramaSyncDisplayRunPersistenceTest test`

- [ ] **Step 3: Add the two tables, enums, entities, mapper and XML**

Use `VARCHAR(36)` UUID run IDs, no physical foreign keys, and a unique item key `(run_id, task_domain, task_id)`. Add cleanup ordering to `BaseAuthTest`.

- [ ] **Step 4: Re-run the persistence test**

Run: `./mvnw.cmd --% -Dtest=DramaSyncDisplayRunPersistenceTest test`

### Task 2: Record runs at existing trigger boundaries

**Files:**
- Create: `src/main/java/com/kasi/backend/drama/service/DramaSyncDisplayRunService.java`
- Create: `src/main/java/com/kasi/backend/drama/service/impl/DramaSyncDisplayRunServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/DramaCatalogSyncService.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/DramaCatalogSyncServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/DramaContentSyncService.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/DramaContentSyncServiceImpl.java`
- Create: `src/test/java/com/kasi/backend/drama/service/DramaSyncDisplayRunServiceTest.java`
- Modify: `src/test/java/com/kasi/backend/drama/service/DramaCatalogSyncServiceTest.java`
- Modify: `src/test/java/com/kasi/backend/drama/service/DramaContentSyncServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Assert one catalog request across multiple languages creates one run and attaches every checkpoint; scheduled catalog requests use `SCHEDULED`; content single/batch/all requests use their task types; automatic content tasks attach to the catalog child run.

- [ ] **Step 2: Run focused tests and observe missing behavior**

Run: `./mvnw.cmd --% -Dtest=DramaSyncDisplayRunServiceTest,DramaCatalogSyncServiceTest,DramaContentSyncServiceTest test`

- [ ] **Step 3: Implement the smallest trigger hooks**

Generate one UUID and request time per public trigger, attach existing task IDs after their existing writes, and pass an optional child-run ID to `requestAutomatic`. Do not alter task worker or lease methods.

- [ ] **Step 4: Re-run focused service tests**

Run: `./mvnw.cmd --% -Dtest=DramaSyncDisplayRunServiceTest,DramaCatalogSyncServiceTest,DramaContentSyncServiceTest test`

### Task 3: Add read-only aggregate records and details

**Files:**
- Create: `src/main/java/com/kasi/backend/drama/enums/SyncRecordStatus.java`
- Create: `src/main/java/com/kasi/backend/drama/vo/DramaSyncRecordVO.java`
- Create: `src/main/java/com/kasi/backend/drama/vo/DramaSyncRecordDetailVO.java`
- Create: `src/main/java/com/kasi/backend/drama/vo/DramaContentSyncRecordDetailVO.java`
- Create: `src/main/java/com/kasi/backend/drama/vo/DramaContentSyncDetailVO.java`
- Create: `src/main/java/com/kasi/backend/drama/service/DramaSyncRecordQueryService.java`
- Create: `src/main/java/com/kasi/backend/drama/service/impl/DramaSyncRecordQueryServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/drama/mapper/ProviderSyncCheckpointMapper.java`
- Modify: `src/main/resources/mapper/ProviderSyncCheckpointMapper.xml`
- Modify: `src/main/java/com/kasi/backend/drama/mapper/DramaContentSyncTaskMapper.java`
- Modify: `src/main/resources/mapper/DramaContentSyncTaskMapper.xml`
- Modify: `src/main/java/com/kasi/backend/drama/controller/AdminDramaCatalogController.java`
- Create: `src/test/java/com/kasi/backend/drama/service/DramaSyncRecordQueryServiceTest.java`
- Modify: `src/test/java/com/kasi/backend/drama/controller/AdminDramaCatalogControllerTest.java`

- [ ] **Step 1: Write failing aggregation tests**

Cover `WAITING`, `RUNNING`, `SUCCESS`, `PARTIAL_FAILED`, `FAILED`, sum inserted/updated/total, provider/domain isolation, and child detail fields.

- [ ] **Step 2: Run focused tests and observe missing service/endpoint behavior**

Run: `./mvnw.cmd --% -Dtest=DramaSyncRecordQueryServiceTest,AdminDramaCatalogControllerTest test`

- [ ] **Step 3: Implement read-only queries and four GET endpoints**

Use the display-run item IDs to load existing tasks; keep `/sync/status` and single-drama status endpoints unchanged. Treat `IDLE` as waiting only when it belongs to a display run.

- [ ] **Step 4: Re-run focused backend tests**

Run: `./mvnw.cmd --% -Dtest=DramaSyncRecordQueryServiceTest,AdminDramaCatalogControllerTest test`

### Task 4: Unify the two existing page tables

**Files:**
- Modify: `../kasi-admin-web/src/features/drama/dramaCatalogTypes.ts`
- Modify: `../kasi-admin-web/src/features/drama/dramaCatalogApi.ts`
- Modify: `../kasi-admin-web/src/features/drama/dramaCatalogApi.test.ts`
- Modify: `../kasi-admin-web/src/pages/drama/DramaSyncCenterPage.tsx`
- Modify: `../kasi-admin-web/src/pages/drama/DramaSyncCenterPage.test.tsx`
- Modify: `../kasi-admin-web/src/pages/drama/drama-sync-center-page.css`
- Modify: `../kasi-admin-web/src/pages/drama/DramaSyncStatusDrawer.tsx`
- Modify: `../kasi-admin-web/src/pages/drama/DramaCatalogPage.tsx`
- Modify: `../kasi-admin-web/src/pages/drama/DramaCatalogPage.test.tsx`

- [ ] **Step 1: Write failing UI tests**

Assert the catalog route calls only catalog records, the content route calls only content records, both tables render the eight common columns, details show language/drama children, and failed details invoke the existing retry APIs.

- [ ] **Step 2: Run focused Vitest and observe missing UI behavior**

Run: `pnpm exec vitest run src/features/drama/dramaCatalogApi.test.ts src/pages/drama/DramaSyncCenterPage.test.tsx --exclude '.worktrees/**'`

- [ ] **Step 3: Implement independent data flows and shared presentation**

Keep the two existing routes/tabs. Give each panel its own provider, loading, records and detail state; use only a shared column definition and status labels. The list remains one row per display run.

- [ ] **Step 4: Re-run focused Vitest**

Run: `pnpm exec vitest run src/features/drama/dramaCatalogApi.test.ts src/pages/drama/DramaSyncCenterPage.test.tsx --exclude '.worktrees/**'`

### Task 5: Documentation and verification

**Files:**
- Modify: `kasi-backend/README.md`
- Modify: `kasi-backend/AGENTS.md`
- Modify: `kasi-admin-web/README.md`

- [ ] **Step 1: Document the two pages and display-only boundary**

Record the eight columns, aggregate status rules, detail/retry behavior, and the fact that checkpoint/worker/lease models remain unchanged.

- [ ] **Step 2: Run Java 25 backend validation**

Run: `$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; ./mvnw.cmd test`

- [ ] **Step 3: Run frontend validation**

Run from `kasi-admin-web`: `pnpm test -- --run --exclude '.worktrees/**'`; then `pnpm typecheck`; then `pnpm build`.

- [ ] **Step 4: Check only this change's files**

Run `git diff --check --` with the explicit file list from Tasks 1-5. Report unrelated pre-existing whitespace failures separately.
