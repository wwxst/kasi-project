# GoodShort Order Scheduled Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 GoodShort 订单每分钟自动同步最近 3 天，同时保留管理员按指定时间范围手动补拉的现有接口。

**Architecture:** 复用 `system_scheduled_task`、`ScheduledTaskScheduler` 和数据库租约，不增加第二个 Spring 调度器。抽取 `PromotionOrderSyncService` 统一承载平台运行时解析、订单分页和 `PromotionOrderService.upsert(...)`，管理端与定时任务入口分别传入自己的同步时间窗。

**Tech Stack:** Java 25、Spring Boot、MyBatis、Flyway、H2 MySQL mode、Mockito、React 19、TypeScript、Vitest、MSW。

---

## Files and Boundaries

```text
Create   src/main/java/com/kasi/backend/promotion/service/PromotionOrderSyncService.java
          订单同步编排接口；无 HTTP 语义、无自动时间窗决策。
Create   src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderSyncServiceImpl.java
          复用现有分页拉取、统计和 PromotionOrderService.upsert 语义。
Create   src/main/resources/db/migration/V16__goodshort_order_scheduled_sync.sql
          插入固定订单同步任务，并保留旧周期字段的合法兼容值。
Modify   src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderAdminServiceImpl.java
          手动入口委托共享同步服务；查询和 CSV 保持原职责。
Modify   src/main/java/com/kasi/backend/scheduledtask/enums/ScheduledTaskCode.java
          声明 GOODSHORT_ORDER_SYNC。
Modify   src/main/java/com/kasi/backend/scheduledtask/service/impl/ScheduledTaskDispatchServiceImpl.java
          为订单任务生成最近 3 天窗口并调用共享服务。
Modify   src/test/java/com/kasi/backend/ScheduledTaskMigrationTest.java
          验证 V16 固定任务和最小 1 分钟的 schema 约束。
Modify   src/test/java/com/kasi/backend/promotion/service/PromotionOrderAdminServiceTest.java
          确认管理端同步委托共享服务而不改变 HTTP 数据范围语义。
Create   src/test/java/com/kasi/backend/promotion/service/PromotionOrderSyncServiceTest.java
          验证订单分页、汇总和 upsert 调用。
Modify   src/test/java/com/kasi/backend/scheduledtask/service/ScheduledTaskDispatchServiceTest.java
          验证订单任务窗口、租约和缺失平台边界。
Modify   E:/JavaProjects/kasi-project/kasi-admin-web/src/features/scheduled-task/scheduledTaskTypes.ts
          接受后端返回的第二个固定任务编码。
Modify   E:/JavaProjects/kasi-project/kasi-admin-web/src/pages/system/ScheduledTaskPage.test.tsx
          验证同一任务页渲染并操作订单同步任务。
Modify   README.md, AGENTS.md, docs/development-gaps.md, docs/architecture-decisions.md
          把当前同步现状、缺口和架构决定更新为已实施事实。
```

### Task 1: Add the Migration Contract

**Files:**
- Create: `src/main/resources/db/migration/V16__goodshort_order_scheduled_sync.sql`
- Modify: `src/main/java/com/kasi/backend/scheduledtask/enums/ScheduledTaskCode.java`
- Modify: `src/test/java/com/kasi/backend/ScheduledTaskMigrationTest.java`

- [ ] **Step 1: Write the failing Flyway assertion for the new task and one-minute effective period**

Add a second test to `ScheduledTaskMigrationTest` that reads the new row and checks all stored schedule columns:

```java
@Test
@DisplayName("V16 新增每分钟执行一次的 GoodShort 订单同步任务")
void migrationCreatesGoodShortOrderSyncTask() {
    JdbcTemplate jdbc = migrateAllMigrations();

    Map<String, Object> task = jdbc.queryForMap("""
            SELECT task_code, title, description, cycle_type, interval_value,
                   interval_minutes, enabled, next_run_at
            FROM system_scheduled_task
            WHERE task_code = 'GOODSHORT_ORDER_SYNC'
            """);
    assertThat(task.get("TASK_CODE")).isEqualTo("GOODSHORT_ORDER_SYNC");
    assertThat(task.get("TITLE")).isEqualTo("GoodShort 订单同步");
    assertThat(task.get("DESCRIPTION")).isEqualTo("每隔1分钟同步最近3天的GoodShort订单");
    assertThat(task.get("CYCLE_TYPE")).isEqualTo("INTERVAL_MINUTES");
    assertThat(((Number) task.get("INTERVAL_VALUE")).intValue()).isEqualTo(1);
    assertThat(((Number) task.get("INTERVAL_MINUTES")).intValue()).isEqualTo(5);
    assertThat(((Number) task.get("ENABLED")).intValue()).isEqualTo(1);
    assertThat(task.get("NEXT_RUN_AT")).isNotNull();
}
```

- [ ] **Step 2: Run the migration test and confirm the expected failure**

Run:

```powershell
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=ScheduledTaskMigrationTest test
```

Expected: the new test fails because `GOODSHORT_ORDER_SYNC` does not exist.

- [ ] **Step 3: Add the task code and V16 migration**

Make `ScheduledTaskCode` contain both fixed task codes:

```java
public enum ScheduledTaskCode {
    GOODSHORT_DRAMA_INCREMENTAL_SYNC,
    GOODSHORT_ORDER_SYNC
}
```

Create `V16__goodshort_order_scheduled_sync.sql`. The pre-existing V1 check constraint requires the legacy `interval_minutes` field to be at least five. The dispatcher calculates the next run from `cycle_type` and `interval_value`, so preserve the schema and store a legal legacy value:

```sql
INSERT INTO `system_scheduled_task`
    (`task_code`, `title`, `description`, `cycle_type`, `interval_value`,
     `interval_hours_part`, `interval_minutes_part`, `interval_minutes`,
     `enabled`, `next_run_at`)
VALUES
    ('GOODSHORT_ORDER_SYNC', 'GoodShort 订单同步',
     '每隔1分钟同步最近3天的GoodShort订单', 'INTERVAL_MINUTES', 1,
     0, 0, 5, 1, TIMESTAMPADD(MINUTE, 1, CURRENT_TIMESTAMP));
```

Do not modify the V1 check constraint. `ScheduledTaskScheduleCalculator` uses `INTERVAL_MINUTES` plus `interval_value=1`, so the effective execution period remains one minute.

- [ ] **Step 4: Run the migration test and confirm it passes**

Run the command from Step 2.

Expected: `ScheduledTaskMigrationTest` passes with two fixed task records; the order task stores `interval_value=1` and compatible `interval_minutes=5`, and Flyway applies V16 without schema errors.

- [ ] **Step 5: Commit the migration contract**

```powershell
git add -- src/main/resources/db/migration/V16__goodshort_order_scheduled_sync.sql src/main/java/com/kasi/backend/scheduledtask/enums/ScheduledTaskCode.java src/test/java/com/kasi/backend/ScheduledTaskMigrationTest.java
git diff --cached --check
git commit -m "feat: add GoodShort order scheduled task"
```

### Task 2: Extract Shared Order Synchronization

**Files:**
- Create: `src/main/java/com/kasi/backend/promotion/service/PromotionOrderSyncService.java`
- Create: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderSyncServiceImpl.java`
- Create: `src/test/java/com/kasi/backend/promotion/service/PromotionOrderSyncServiceTest.java`
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderAdminServiceImpl.java`
- Modify: `src/test/java/com/kasi/backend/promotion/service/PromotionOrderAdminServiceTest.java`

- [ ] **Step 1: Write failing tests for the service boundary and existing pagination behavior**

Create `PromotionOrderSyncServiceTest` using the two-page GoodShort adapter setup currently in `PromotionOrderAdminServiceTest`. Instantiate `PromotionOrderSyncServiceImpl(runtimeService, orderService)` and assert:

```java
var result = service.sync(7L, startDate, endDate);

assertThat(result.getFetchedCount()).isEqualTo(3);
assertThat(result.getInsertedCount()).isEqualTo(2);
assertThat(result.getUpdatedCount()).isEqualTo(1);
assertThat(result.getUnattributedCount()).isEqualTo(1);
verify(adapter).fetchOrders(any(), new OrderSyncRequest(startDate, endDate, 1, 500));
verify(adapter).fetchOrders(any(), new OrderSyncRequest(startDate, endDate, 2, 500));
verify(orderService).upsert(eq(runtime), eq(first), eq(startDate), eq(endDate));
verify(orderService).upsert(eq(runtime), eq(second), eq(startDate), eq(endDate));
verify(orderService).upsert(eq(runtime), eq(third), eq(startDate), eq(endDate));
```

Replace the direct adapter test in `PromotionOrderAdminServiceTest` with a mocked `PromotionOrderSyncService`. Assert that `adminService.sync(request)` calls:

```java
verify(syncService).sync(7L, request.getStartDate(), request.getEndDate());
```

and returns the exact `PromotionOrderSyncResultVO` supplied by the shared service.

- [ ] **Step 2: Run the two tests and confirm compilation or assertions fail before implementation**

Run:

```powershell
.\mvnw.cmd -Dtest=PromotionOrderSyncServiceTest,PromotionOrderAdminServiceTest test
```

Expected: compilation fails because `PromotionOrderSyncService` and its implementation do not exist, or the former direct construction no longer matches the test.

- [ ] **Step 3: Add the service interface and move only synchronization orchestration**

Create the interface:

```java
public interface PromotionOrderSyncService {
    PromotionOrderSyncResultVO sync(Long providerId, LocalDateTime startDate, LocalDateTime endDate);
}
```

Create `PromotionOrderSyncServiceImpl` with the existing `SYNC_PAGE_SIZE = 500`, `ProviderRuntimeConnectionService`, and `PromotionOrderService`. Move the current `do/while` loop without changing these behaviors:

```java
var runtime = runtimeService.resolve(providerId, ProviderCapability.ORDER_SYNC);
OrderSyncProviderAdapter adapter = (OrderSyncProviderAdapter) runtime.adapter();
int pageNo = 1;
int fetched = 0;
int inserted = 0;
int updated = 0;
int unattributed = 0;
boolean hasNext;
do {
    var page = adapter.fetchOrders(runtime.secret(),
            new OrderSyncRequest(startDate, endDate, pageNo, SYNC_PAGE_SIZE));
    for (var record : page.records()) {
        var result = orderService.upsert(runtime, record, startDate, endDate);
        fetched++;
        if (result.inserted()) inserted++; else updated++;
        if (!result.attributed()) unattributed++;
    }
    hasNext = page.hasNext();
    pageNo++;
} while (hasNext);
return PromotionOrderSyncResultVO.builder().fetchedCount(fetched).insertedCount(inserted)
        .updatedCount(updated).unattributedCount(unattributed).build();
```

Change `PromotionOrderAdminServiceImpl` to depend on `PromotionOrderSyncService` and delegate its `sync` method:

```java
return orderSyncService.sync(request.getProviderId(), request.getStartDate(), request.getEndDate());
```

Leave `getPage`, `exportCsv`, `toVO`, and CSV helpers in `PromotionOrderAdminServiceImpl`; they are administration read/export responsibilities, not order synchronization.

- [ ] **Step 4: Run the focused order tests and confirm they pass**

Run the command from Step 2.

Expected: both tests pass; the manual endpoint has no changed DTO, controller route, response fields, pagination size, attribution, or upsert behavior.

- [ ] **Step 5: Commit the shared synchronization service**

```powershell
git add -- src/main/java/com/kasi/backend/promotion/service/PromotionOrderSyncService.java src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderSyncServiceImpl.java src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderAdminServiceImpl.java src/test/java/com/kasi/backend/promotion/service/PromotionOrderSyncServiceTest.java src/test/java/com/kasi/backend/promotion/service/PromotionOrderAdminServiceTest.java
git diff --cached --check
git commit -m "refactor: share promotion order synchronization"
```

### Task 3: Dispatch the One-Minute Sliding Window

**Files:**
- Modify: `src/main/java/com/kasi/backend/scheduledtask/service/impl/ScheduledTaskDispatchServiceImpl.java`
- Modify: `src/test/java/com/kasi/backend/scheduledtask/service/ScheduledTaskDispatchServiceTest.java`

- [ ] **Step 1: Write failing dispatcher tests**

Extend `ScheduledTaskDispatchServiceTest` with a mocked `PromotionOrderSyncService` and construct a `GOODSHORT_ORDER_SYNC` task with `cycleType=INTERVAL_MINUTES`, `intervalValue=1`, and `intervalMinutes=1`.

For a successfully claimed task and a provider with `id=7L`, assert:

```java
verify(orderSyncService).sync(7L, NOW.minusDays(3), NOW);
verify(taskMapper).completeRun(1L, "scheduled-worker-test", NOW.plusMinutes(1));
```

Add a lease-loss test that asserts no provider lookup, no `orderSyncService.sync(...)`, and no `completeRun(...)`.

Add a missing-provider test that asserts no synchronization call but does assert `completeRun(..., NOW.plusMinutes(1))`. Keep the existing catalog task tests unchanged, proving dispatch remains task-code driven rather than provider-name conditionals.

- [ ] **Step 2: Run dispatcher tests and confirm the new tests fail**

Run:

```powershell
.\mvnw.cmd -Dtest=ScheduledTaskDispatchServiceTest test
```

Expected: compilation fails because the dispatcher constructor does not accept `PromotionOrderSyncService`, and no order task switch case exists.

- [ ] **Step 3: Add the minimal dispatch branch**

Inject `PromotionOrderSyncService` into `ScheduledTaskDispatchServiceImpl`. Preserve the existing `DramaCatalogSyncService` dependency and explicit test convenience constructor, extending that constructor with the shared order service rather than constructing a production dependency internally.

Extend dispatch:

```java
private void dispatch(SystemScheduledTask task) {
    switch (task.getTaskCode()) {
        case GOODSHORT_DRAMA_INCREMENTAL_SYNC -> dispatchGoodShortDramaIncremental();
        case GOODSHORT_ORDER_SYNC -> dispatchGoodShortOrderSync();
    }
}

private void dispatchGoodShortOrderSync() {
    ShortDramaProvider provider = providerMapper.findByCode("GOODSHORT");
    if (provider == null) {
        return;
    }
    LocalDateTime endDate = LocalDateTime.now(clock);
    orderSyncService.sync(provider.getId(), endDate.minusDays(3), endDate);
}
```

Do not add another `@Scheduled`, do not add a second task table, and do not catch exceptions inside this branch. Existing `processDueBatch()` logging and `finally` completion retain the previously documented failure behavior.

- [ ] **Step 4: Run focused scheduling tests and confirm they pass**

Run:

```powershell
.\mvnw.cmd -Dtest=ScheduledTaskDispatchServiceTest,ScheduledTaskSchedulerTest,SystemScheduledTaskPersistenceTest test
```

Expected: catalog scheduling behavior remains green; order scheduling uses the injected `Clock`, only runs after lease acquisition, and calculates an exact three-day window.

- [ ] **Step 5: Commit the scheduler dispatch change**

```powershell
git add -- src/main/java/com/kasi/backend/scheduledtask/service/impl/ScheduledTaskDispatchServiceImpl.java src/test/java/com/kasi/backend/scheduledtask/service/ScheduledTaskDispatchServiceTest.java
git diff --cached --check
git commit -m "feat: schedule GoodShort order synchronization"
```

### Task 4: Keep the Existing Admin Task Page Type-Safe

**Files:**
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/src/features/scheduled-task/scheduledTaskTypes.ts`
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/src/pages/system/ScheduledTaskPage.test.tsx`

- [ ] **Step 1: Write a failing page test with both fixed backend tasks**

Import `ScheduledTask` as a type in `ScheduledTaskPage.test.tsx`, then create the second row as a typed value. This makes the test fail before the union is extended:

```ts
const orderTask: ScheduledTask = {
  taskCode: 'GOODSHORT_ORDER_SYNC',
  title: 'GoodShort 订单同步',
  description: '每隔1分钟同步最近3天的GoodShort订单',
  cycleType: 'INTERVAL_MINUTES',
  intervalValue: 1,
  intervalMinutes: 1,
  enabled: true,
}
```

Change the MSW `GET /api/admin/system/scheduled-tasks` response in that test to return `[task, orderTask]`. Assert both rows render, then click the order task's labelled switch and assert the request path is:

```ts
'/api/admin/system/scheduled-tasks/GOODSHORT_ORDER_SYNC'
```

and the body retains one-minute scheduling and changes only `enabled`:

```ts
{
  cycleType: 'INTERVAL_MINUTES',
  intervalValue: 1,
  description: '每隔1分钟同步最近3天的GoodShort订单',
  enabled: false,
}
```

- [ ] **Step 2: Run the page test and confirm its type check fails**

Run from `E:/JavaProjects/kasi-project/kasi-admin-web`:

```powershell
pnpm vitest run src/pages/system/ScheduledTaskPage.test.tsx
```

Expected: TypeScript rejects `GOODSHORT_ORDER_SYNC` because it is absent from `ScheduledTaskCode`.

- [ ] **Step 3: Extend only the task code union**

Change the existing type to:

```ts
export type ScheduledTaskCode =
  | 'GOODSHORT_DRAMA_INCREMENTAL_SYNC'
  | 'GOODSHORT_ORDER_SYNC'
```

Do not create a new page, route, API client, form, or second timer. `ScheduledTaskPage` already renders data-driven fixed tasks and uses `taskCode` in its update request.

- [ ] **Step 4: Run focused admin tests and build check**

Run from `E:/JavaProjects/kasi-project/kasi-admin-web`:

```powershell
pnpm vitest run src/features/scheduled-task/scheduledTaskApi.test.ts src/pages/system/ScheduledTaskPage.test.tsx
pnpm build
```

Expected: the page displays both tasks and updates the order task through the existing endpoint; the production bundle completes without TypeScript errors.

- [ ] **Step 5: Commit the admin type and coverage**

```powershell
git -C E:/JavaProjects/kasi-project/kasi-admin-web add -- src/features/scheduled-task/scheduledTaskTypes.ts src/pages/system/ScheduledTaskPage.test.tsx
git -C E:/JavaProjects/kasi-project/kasi-admin-web diff --cached --check
git -C E:/JavaProjects/kasi-project/kasi-admin-web commit -m "feat: display GoodShort order scheduled task"
```

### Task 5: Update Current-State Documentation and Verify the Whole Change

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `docs/development-gaps.md`
- Modify: `docs/architecture-decisions.md`

- [ ] **Step 1: Update only statements made stale by this delivery**

Apply these factual wording changes:

```text
README.md
  "管理员手动同步订单" -> "每分钟自动同步最近3天订单；管理员可按时间范围手动补拉"

AGENTS.md
  当前订单同步现状改为：自动任务 GOODSHORT_ORDER_SYNC 每分钟回查3天，
  手动 POST /api/admin/promotion/orders/sync 保留用于历史补拉；
  订单幂等、trackingNo 归因、五费率快照和退款语义未变。

docs/development-gaps.md
  从明确后置能力中移除“GoodShort 订单定时同步”；
  保留未实施的同步检查点、自动补偿、运行历史、失败重试和告警页面。

docs/architecture-decisions.md
  新增简短 ADR：订单自动同步复用 system_scheduled_task 及其租约，
  不增加第二个 Spring 调度器；自动窗口为最近3天，历史补拉继续走管理端接口。
```

- [ ] **Step 2: Run backend focused verification**

Run:

```powershell
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=ScheduledTaskMigrationTest,PromotionOrderMigrationTest,PromotionOrderSyncServiceTest,PromotionOrderAdminServiceTest,ScheduledTaskDispatchServiceTest,ScheduledTaskSchedulerTest,SystemScheduledTaskPersistenceTest test
.\mvnw.cmd -DskipTests compile
```

Expected: all selected tests pass and compilation reports `BUILD SUCCESS`.

- [ ] **Step 3: Run backend full regression if the focused gate is green**

Run:

```powershell
.\mvnw.cmd test
```

Expected: Maven reports `BUILD SUCCESS`. If a pre-existing unrelated test fails, record its exact class and output; do not alter unrelated production code to make the suite green.

- [ ] **Step 4: Inspect final diffs and commit documentation separately**

Run:

```powershell
git diff --check
git status --short
git diff -- README.md AGENTS.md docs/development-gaps.md docs/architecture-decisions.md
```

Expected: no whitespace errors, no unreviewed unrelated files, and documentation states implemented behavior separately from remaining gaps.

Then commit only documentation:

```powershell
git add -- README.md AGENTS.md docs/development-gaps.md docs/architecture-decisions.md
git diff --cached --check
git commit -m "docs: record GoodShort order scheduled sync"
```

## Plan Self-Review

Spec coverage:

- One-minute, recent-three-day automatic synchronization: Tasks 1 and 3.
- Existing scheduler, task page and lease reuse: Tasks 1, 3 and 4.
- Shared manual/automatic orchestration: Task 2.
- Existing attribution, idempotency, CPS calculation, snapshot and refund semantics: Task 2 keeps the existing `PromotionOrderService.upsert(...)` call path; no data-model task changes these rules.
- Manual historical supplementation: Task 2 retains `POST /api/admin/promotion/orders/sync` and its DTO unchanged.
- Migration, rollback-relevant data behavior and current-state documentation: Tasks 1 and 5.

Consistency checks:

- `PromotionOrderSyncService.sync(Long, LocalDateTime, LocalDateTime)` is the only shared synchronization API throughout the plan.
- `GOODSHORT_ORDER_SYNC` is the only added task code throughout the plan.
- The automatic range is consistently `endDate.minusDays(3)` through `endDate`, with `endDate` sourced from the injected `Clock`.
- The existing V1 five-minute compatibility-field check remains unchanged; the order task uses `interval_value=1` for its effective period.
