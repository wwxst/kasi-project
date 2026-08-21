# Scheduled Task Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a compact system-configuration page that edits the fixed GoodShort incremental catalog task and make the backend enqueue incremental sync work every configured interval after a successful full baseline exists.

**Architecture:** Persist only the editable schedule metadata in `system_scheduled_task`; keep task codes and handlers fixed in Java. A one-minute dispatcher claims due rows with a database lease and delegates GoodShort work to a scheduled-only method on the existing drama sync service, while the existing five-minute catalog worker remains responsible for remote execution.

**Tech Stack:** Java 25, Spring Boot 4, Spring Security, Jakarta Validation, MyBatis, Flyway, MySQL/H2, JUnit 5, Mockito, React 19, TypeScript, Ant Design 6, React Router 7, Axios, Vitest, Testing Library, MSW.

---

## File Structure

Backend repository: `E:/JavaProjects/kasi-project/kasi-backend`

- Create `src/main/resources/db/migration/V8__scheduled_task_config.sql`: production table and fixed GoodShort task seed.
- Modify `src/test/resources/test-schema.sql`: H2 equivalent for Spring integration tests.
- Modify `src/test/java/com/kasi/backend/BaseAuthTest.java`: reset the fixed task before each integration test.
- Create `src/test/java/com/kasi/backend/ScheduledTaskMigrationTest.java`: real Flyway migration contract.
- Create `src/main/java/com/kasi/backend/scheduledtask/entity/SystemScheduledTask.java`: pure table entity.
- Create `src/main/java/com/kasi/backend/scheduledtask/enums/ScheduledTaskCode.java`: fixed handler codes.
- Create `src/main/java/com/kasi/backend/scheduledtask/mapper/SystemScheduledTaskMapper.java` and `src/main/resources/mapper/SystemScheduledTaskMapper.xml`: single-table reads, updates, due selection and lease transitions.
- Create `src/test/java/com/kasi/backend/scheduledtask/mapper/SystemScheduledTaskPersistenceTest.java`: mapper and lease behavior.
- Create `src/main/java/com/kasi/backend/scheduledtask/dto/UpdateScheduledTaskDTO.java`: validated editable fields.
- Create `src/main/java/com/kasi/backend/scheduledtask/vo/ScheduledTaskVO.java`: safe list/update response.
- Create `src/main/java/com/kasi/backend/scheduledtask/service/ScheduledTaskManagementService.java` and `impl/ScheduledTaskManagementServiceImpl.java`: read/update configuration.
- Create `src/main/java/com/kasi/backend/scheduledtask/controller/ScheduledTaskController.java`: admin REST API.
- Modify `src/main/java/com/kasi/backend/common/exception/ErrorCode.java`: reachable not-found error.
- Modify `src/main/java/com/kasi/backend/security/config/SecurityConfig.java`: super-admin-only PUT rule before the admin wildcard.
- Create `src/test/java/com/kasi/backend/scheduledtask/controller/ScheduledTaskControllerTest.java`: auth, validation and response contract.
- Create `src/main/java/com/kasi/backend/scheduledtask/config/ScheduledTaskProperties.java` and `ScheduledTaskConfig.java`: scan, batch and lease settings.
- Create `src/main/java/com/kasi/backend/scheduledtask/service/ScheduledTaskDispatchService.java` and `impl/ScheduledTaskDispatchServiceImpl.java`: fixed-code dispatch orchestration.
- Create `src/main/java/com/kasi/backend/scheduledtask/task/ScheduledTaskScheduler.java`: one-minute due-row polling.
- Modify `src/main/java/com/kasi/backend/drama/service/DramaCatalogSyncService.java` and `impl/DramaCatalogSyncServiceImpl.java`: scheduled incremental enqueue that never upgrades to FULL.
- Create `src/test/java/com/kasi/backend/scheduledtask/service/ScheduledTaskDispatchServiceTest.java` and `task/ScheduledTaskSchedulerTest.java`: baseline, duplicate and scheduler behavior.
- Modify `src/test/java/com/kasi/backend/drama/service/DramaCatalogSyncServiceTest.java`: scheduled enqueue rules.
- Modify `src/main/resources/application.properties` and `src/test/resources/application-test.properties`: dispatcher defaults and test disable switch.
- Modify `README.md` and `AGENTS.md`: current V8, API, page contract and automatic incremental behavior.

Frontend repository: `E:/JavaProjects/kasi-project/kasi-admin-web`

- Create `src/features/scheduled-task/scheduledTaskTypes.ts`: response and update request types.
- Create `src/features/scheduled-task/scheduledTaskApi.ts` and `.test.ts`: list/update HTTP mapping.
- Create `src/pages/system/ScheduledTaskPage.tsx`, `.test.tsx` and `scheduled-task-page.css`: exact five-column table and compact edit modal.
- Modify `src/layouts/AdminLayout.tsx`: “系统配置 / 定时任务” navigation item.
- Modify `src/router/AppRouter.tsx`: lazy route `/system-config/scheduled-tasks`.
- Modify `src/App.test.tsx`: route/menu integration and ordinary-admin access.
- Modify `README.md`: document the delivered page and API dependency.

### Task 1: Add the scheduled-task schema contract

**Files:**
- Create: `src/test/java/com/kasi/backend/ScheduledTaskMigrationTest.java`
- Create: `src/main/resources/db/migration/V8__scheduled_task_config.sql`
- Modify: `src/test/resources/test-schema.sql`
- Modify: `src/test/java/com/kasi/backend/BaseAuthTest.java`

- [ ] **Step 1: Write the failing Flyway migration test**

Create a migration test that runs all production migrations against isolated H2 MySQL mode and asserts the fixed row exactly:

```java
@Test
@DisplayName("V8创建定时任务配置并植入GoodShort增量同步任务")
void migrateCreatesScheduledTaskConfig() {
    JdbcTemplate jdbc = migrateAllMigrations();

    assertThat(tableExists(jdbc, "SYSTEM_SCHEDULED_TASK")).isTrue();
    Map<String, Object> task = jdbc.queryForMap("""
            SELECT task_code, title, description, interval_minutes, enabled
            FROM system_scheduled_task
            WHERE task_code = 'GOODSHORT_DRAMA_INCREMENTAL_SYNC'
            """);
    assertThat(task.get("TASK_CODE")).isEqualTo("GOODSHORT_DRAMA_INCREMENTAL_SYNC");
    assertThat(task.get("TITLE")).isEqualTo("GoodShort 短剧增量同步");
    assertThat(((Number) task.get("INTERVAL_MINUTES")).intValue()).isEqualTo(60);
    assertThat(((Number) task.get("ENABLED")).intValue()).isEqualTo(1);
}
```

Reuse the isolated `DriverManagerDataSource`, `Flyway.configure()` and `tableExists` pattern from `MediaAccountFilingMigrationTest`.

- [ ] **Step 2: Run the migration test and verify RED**

Run:

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=ScheduledTaskMigrationTest test
```

Expected: FAIL because `SYSTEM_SCHEDULED_TASK` does not exist.

- [ ] **Step 3: Create V8 with the fixed task seed**

Use this production contract:

```sql
CREATE TABLE `system_scheduled_task`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_code`        VARCHAR(64)     NOT NULL COMMENT '固定任务编码',
    `title`            VARCHAR(128)    NOT NULL COMMENT '任务标题',
    `description`      VARCHAR(255)    NOT NULL COMMENT '任务说明',
    `interval_minutes` INT             NOT NULL COMMENT '执行间隔分钟数',
    `enabled`          TINYINT         NOT NULL DEFAULT 1 COMMENT '是否开启',
    `next_run_at`      DATETIME                 DEFAULT NULL COMMENT '下次入队时间',
    `lease_owner`      VARCHAR(64)              DEFAULT NULL COMMENT '租约持有者',
    `lease_until`      DATETIME                 DEFAULT NULL COMMENT '租约到期时间',
    `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_system_scheduled_task_code` (`task_code`),
    KEY `idx_system_scheduled_task_due` (`enabled`, `next_run_at`, `lease_until`),
    CONSTRAINT `chk_system_scheduled_task_interval` CHECK (`interval_minutes` BETWEEN 5 AND 1440)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统固定定时任务配置';

INSERT INTO `system_scheduled_task`
    (`task_code`, `title`, `description`, `interval_minutes`, `enabled`, `next_run_at`)
VALUES
    ('GOODSHORT_DRAMA_INCREMENTAL_SYNC', 'GoodShort 短剧增量同步',
     '每隔60分钟执行一次GoodShort短剧目录增量同步', 60, 1,
     DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 60 MINUTE));
```

Add the H2-compatible table and seed to `test-schema.sql`. In `BaseAuthTest.baseSetUp()`, delete `system_scheduled_task` before parent tables and insert the same fixed row with `DATEADD('MINUTE', 60, CURRENT_TIMESTAMP)` so every test starts deterministically.

- [ ] **Step 4: Run the migration test and verify GREEN**

Run: `.\mvnw.cmd -Dtest=ScheduledTaskMigrationTest test`

Expected: 1 test, 0 failures, 0 errors.

- [ ] **Step 5: Commit the schema slice**

```powershell
git add src/main/resources/db/migration/V8__scheduled_task_config.sql src/test/resources/test-schema.sql src/test/java/com/kasi/backend/BaseAuthTest.java src/test/java/com/kasi/backend/ScheduledTaskMigrationTest.java
git commit -m "feat: add scheduled task configuration schema"
```

### Task 2: Add scheduled-task persistence and lease transitions

**Files:**
- Create: `src/main/java/com/kasi/backend/scheduledtask/entity/SystemScheduledTask.java`
- Create: `src/main/java/com/kasi/backend/scheduledtask/enums/ScheduledTaskCode.java`
- Create: `src/main/java/com/kasi/backend/scheduledtask/mapper/SystemScheduledTaskMapper.java`
- Create: `src/main/resources/mapper/SystemScheduledTaskMapper.xml`
- Create: `src/test/java/com/kasi/backend/scheduledtask/mapper/SystemScheduledTaskPersistenceTest.java`

- [ ] **Step 1: Write failing mapper tests**

Cover these exact transitions:

```java
@Test
@DisplayName("定时任务配置可查询并更新")
void taskCanBeReadAndUpdated() {
    SystemScheduledTask task = mapper.findByTaskCode(ScheduledTaskCode.GOODSHORT_DRAMA_INCREMENTAL_SYNC);
    LocalDateTime nextRunAt = LocalDateTime.now().plusMinutes(30);

    assertThat(mapper.updateConfig(task.getTaskCode(), "更新后的说明", 30, true, nextRunAt)).isEqualTo(1);
    SystemScheduledTask stored = mapper.findByTaskCode(task.getTaskCode());
    assertThat(stored.getDescription()).isEqualTo("更新后的说明");
    assertThat(stored.getIntervalMinutes()).isEqualTo(30);
    assertThat(stored.getNextRunAt()).isEqualToIgnoringNanos(nextRunAt);
}

@Test
@DisplayName("同一个到期任务只能被一个实例领取")
void dueTaskHasSingleLeaseOwner() {
    SystemScheduledTask task = dueTask();
    LocalDateTime now = LocalDateTime.now();

    assertThat(mapper.claimLease(task.getId(), "worker-a", now, now.plusMinutes(2))).isEqualTo(1);
    assertThat(mapper.claimLease(task.getId(), "worker-b", now, now.plusMinutes(2))).isZero();
    assertThat(mapper.completeRun(task.getId(), "worker-a", now.plusMinutes(60))).isEqualTo(1);
}
```

- [ ] **Step 2: Run mapper tests and verify RED**

Run: `.\mvnw.cmd -Dtest=SystemScheduledTaskPersistenceTest test`

Expected: compilation failure because the entity and mapper do not exist.

- [ ] **Step 3: Implement the entity, enum and mapper**

Use a pure Lombok `@Data` entity with `Long id`, `ScheduledTaskCode taskCode`, `String title`, `String description`, `Integer intervalMinutes`, `Boolean enabled`, `LocalDateTime nextRunAt`, lease fields and audit timestamps.

Define these mapper operations and matching XML conditions:

```java
List<SystemScheduledTask> findAll();
SystemScheduledTask findByTaskCode(@Param("taskCode") ScheduledTaskCode taskCode);
List<SystemScheduledTask> findDue(@Param("now") LocalDateTime now, @Param("limit") int limit);
int updateConfig(@Param("taskCode") ScheduledTaskCode taskCode,
                 @Param("description") String description,
                 @Param("intervalMinutes") int intervalMinutes,
                 @Param("enabled") boolean enabled,
                 @Param("nextRunAt") LocalDateTime nextRunAt);
int claimLease(@Param("id") Long id, @Param("owner") String owner,
               @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);
int completeRun(@Param("id") Long id, @Param("owner") String owner,
                @Param("nextRunAt") LocalDateTime nextRunAt);
```

`findDue` requires `enabled=1`, `next_run_at <= now` and an absent/expired lease. `claimLease` repeats those conditions atomically. `completeRun` advances `next_run_at` and clears the lease only when `lease_owner` matches.

- [ ] **Step 4: Run mapper tests and verify GREEN**

Run: `.\mvnw.cmd -Dtest=SystemScheduledTaskPersistenceTest test`

Expected: all persistence tests pass.

- [ ] **Step 5: Commit the persistence slice**

```powershell
git add src/main/java/com/kasi/backend/scheduledtask src/main/resources/mapper/SystemScheduledTaskMapper.xml src/test/java/com/kasi/backend/scheduledtask/mapper
git commit -m "feat: persist fixed scheduled tasks"
```

### Task 3: Add the read/update API and permissions

**Files:**
- Create: `src/main/java/com/kasi/backend/scheduledtask/dto/UpdateScheduledTaskDTO.java`
- Create: `src/main/java/com/kasi/backend/scheduledtask/vo/ScheduledTaskVO.java`
- Create: `src/main/java/com/kasi/backend/scheduledtask/service/ScheduledTaskManagementService.java`
- Create: `src/main/java/com/kasi/backend/scheduledtask/service/impl/ScheduledTaskManagementServiceImpl.java`
- Create: `src/main/java/com/kasi/backend/scheduledtask/controller/ScheduledTaskController.java`
- Create: `src/test/java/com/kasi/backend/scheduledtask/controller/ScheduledTaskControllerTest.java`
- Modify: `src/main/java/com/kasi/backend/common/exception/ErrorCode.java`
- Modify: `src/main/java/com/kasi/backend/security/config/SecurityConfig.java`

- [ ] **Step 1: Write failing controller tests**

Extend `BaseAuthTest` and cover anonymous 401, ordinary-admin GET 200, ordinary-admin PUT 403, super-admin PUT 200, and validation code 1006:

```java
mockMvc.perform(put("/api/admin/system/scheduled-tasks/{taskCode}",
                "GOODSHORT_DRAMA_INCREMENTAL_SYNC")
        .header("Authorization", "Bearer " + loginAsAdmin())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
                {"intervalMinutes":30,"description":"每30分钟同步一次","enabled":true}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("GoodShort 短剧增量同步"))
        .andExpect(jsonPath("$.data.intervalMinutes").value(30))
        .andExpect(jsonPath("$.data.enabled").value(true))
        .andExpect(jsonPath("$.data.nextRunAt").doesNotExist());
```

Send `intervalMinutes` values 4 and 1441, blank description, over-255 description and absent enabled; each must return HTTP 200 with application code 1006.

- [ ] **Step 2: Run controller tests and verify RED**

Run: `.\mvnw.cmd -Dtest=ScheduledTaskControllerTest test`

Expected: 404/no handler because the controller does not exist.

- [ ] **Step 3: Implement DTO, VO, service and controller**

DTO contract:

```java
@Data
public class UpdateScheduledTaskDTO {
    @NotNull @Min(5) @Max(1440)
    private Integer intervalMinutes;
    @NotBlank @Size(max = 255)
    private String description;
    @NotNull
    private Boolean enabled;
}
```

Service contract:

```java
public interface ScheduledTaskManagementService {
    List<ScheduledTaskVO> getTasks();
    ScheduledTaskVO updateTask(ScheduledTaskCode taskCode, UpdateScheduledTaskDTO request);
}
```

The implementation uses the shared `Clock`. It trims `description`, calculates `nextRunAt = enabled ? now.plusMinutes(intervalMinutes) : null`, updates exactly one row, and throws `BusinessException(ErrorCode.SCHEDULED_TASK_NOT_FOUND)` when the task code is unknown or no row is updated. Add `SCHEDULED_TASK_NOT_FOUND(1008, "定时任务不存在")` as the only new error.

Controller contract:

```java
@RestController
@RequestMapping("/api/admin/system/scheduled-tasks")
@RequiredArgsConstructor
public class ScheduledTaskController {
    private final ScheduledTaskManagementService managementService;

    @GetMapping
    public ApiResponse<List<ScheduledTaskVO>> getTasks() {
        return ApiResponse.success(managementService.getTasks());
    }

    @PutMapping("/{taskCode}")
    public ApiResponse<ScheduledTaskVO> updateTask(
            @PathVariable ScheduledTaskCode taskCode,
            @Valid @RequestBody UpdateScheduledTaskDTO request) {
        return ApiResponse.success(managementService.updateTask(taskCode, request));
    }
}
```

Place this security matcher before `/api/admin/**`:

```java
.requestMatchers(HttpMethod.PUT, "/api/admin/system/scheduled-tasks/*")
.hasRole("SUPER_ADMIN")
```

- [ ] **Step 4: Run controller tests and verify GREEN**

Run: `.\mvnw.cmd -Dtest=ScheduledTaskControllerTest test`

Expected: all authorization, update and validation tests pass.

- [ ] **Step 5: Commit the API slice**

```powershell
git add src/main/java/com/kasi/backend/scheduledtask src/main/java/com/kasi/backend/common/exception/ErrorCode.java src/main/java/com/kasi/backend/security/config/SecurityConfig.java src/test/java/com/kasi/backend/scheduledtask/controller
git commit -m "feat: manage fixed scheduled tasks"
```

### Task 4: Enqueue scheduled incremental catalog work

**Files:**
- Create: `src/main/java/com/kasi/backend/scheduledtask/config/ScheduledTaskProperties.java`
- Create: `src/main/java/com/kasi/backend/scheduledtask/config/ScheduledTaskConfig.java`
- Create: `src/main/java/com/kasi/backend/scheduledtask/service/ScheduledTaskDispatchService.java`
- Create: `src/main/java/com/kasi/backend/scheduledtask/service/impl/ScheduledTaskDispatchServiceImpl.java`
- Create: `src/main/java/com/kasi/backend/scheduledtask/task/ScheduledTaskScheduler.java`
- Create: `src/test/java/com/kasi/backend/scheduledtask/service/ScheduledTaskDispatchServiceTest.java`
- Create: `src/test/java/com/kasi/backend/scheduledtask/task/ScheduledTaskSchedulerTest.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/DramaCatalogSyncService.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/DramaCatalogSyncServiceImpl.java`
- Modify: `src/test/java/com/kasi/backend/drama/service/DramaCatalogSyncServiceTest.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/resources/application-test.properties`

- [ ] **Step 1: Write failing scheduled-enqueue tests on the drama service**

Add `requestScheduledIncremental(Long providerId, List<String> languages)` tests proving:

```java
assertThat(service.requestScheduledIncremental(7L, List.of("ENGLISH"))).isEmpty();
verify(checkpointMapper, never()).requestRun(anyLong(), any(), anyBoolean());
```

when no successful FULL checkpoint exists; and proving an INCREMENTAL checkpoint becomes `REQUESTED` when FULL is `SUCCESS` with a non-null `lastSuccessAt`. Add an active-task case that returns an empty list instead of throwing `DRAMA_SYNC_TASK_RUNNING`.

- [ ] **Step 2: Run drama service tests and verify RED**

Run: `.\mvnw.cmd -Dtest=DramaCatalogSyncServiceTest test`

Expected: compilation failure because `requestScheduledIncremental` is absent.

- [ ] **Step 3: Implement the scheduled-only drama service method**

Add to the interface:

```java
List<DramaSyncTaskVO> requestScheduledIncremental(Long providerId, List<String> languages);
```

The implementation resolves `INCREMENTAL_DRAMA_SYNC`, locks the connection, normalizes languages with the existing helper, skips languages with an active FULL/INCREMENTAL task, and requires:

```java
ProviderSyncCheckpoint full = checkpointMapper.find(connectionId, DramaSyncType.FULL, language);
if (full == null || full.getStatus() != DramaSyncStatus.SUCCESS || full.getLastSuccessAt() == null) {
    continue;
}
```

It then ensures the INCREMENTAL checkpoint and calls `requestRun`; unlike manual `requestSync`, this method never invokes `effectiveType` and therefore cannot create or upgrade to FULL.

- [ ] **Step 4: Run drama service tests and verify GREEN**

Run: `.\mvnw.cmd -Dtest=DramaCatalogSyncServiceTest test`

Expected: existing tests plus the three scheduled-enqueue cases pass.

- [ ] **Step 5: Write failing dispatcher and scheduler tests**

Use Mockito with a fixed clock. The dispatcher test must verify one due GoodShort row calls:

```java
verify(syncService).requestScheduledIncremental(providerId, List.of("ENGLISH"));
verify(taskMapper).completeRun(taskId, workerId,
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).plusMinutes(60));
```

Also verify a lost lease does not invoke the handler and an unknown fixed code is never representable because `ScheduledTaskCode` controls mapping.

The scheduler context test mirrors `DramaCatalogSchedulerTest`: enabled by default delegates to `processDueBatch()`, while `app.scheduled-task.scheduler-enabled=false` prevents bean creation.

- [ ] **Step 6: Run dispatcher tests and verify RED**

Run: `.\mvnw.cmd -Dtest=ScheduledTaskDispatchServiceTest,ScheduledTaskSchedulerTest test`

Expected: compilation failure because dispatcher types do not exist.

- [ ] **Step 7: Implement properties, dispatcher and scheduler**

Properties:

```java
@Data
@ConfigurationProperties(prefix = "app.scheduled-task")
public class ScheduledTaskProperties {
    private boolean schedulerEnabled = true;
    private Duration fixedDelay = Duration.ofMinutes(1);
    private int batchSize = 10;
    private Duration leaseDuration = Duration.ofMinutes(2);
}
```

`ScheduledTaskConfig` enables these properties. `ScheduledTaskScheduler` is conditional on `scheduler-enabled=true` and uses `${app.scheduled-task.fixed-delay:1m}`.

`ScheduledTaskDispatchServiceImpl.processDueBatch()` finds due rows, claims each lease in a short transaction, dispatches by `ScheduledTaskCode`, and advances `nextRunAt` by the row's `intervalMinutes` in a `finally` block. The GoodShort handler finds provider code `GOODSHORT` through `ShortDramaProviderMapper` and calls `requestScheduledIncremental(providerId, dramaSyncProperties.getLanguages())`. Missing/disabled/unconfigured providers naturally produce no queued work and still advance the schedule.

Add defaults:

```properties
app.scheduled-task.scheduler-enabled=true
app.scheduled-task.fixed-delay=1m
app.scheduled-task.batch-size=10
app.scheduled-task.lease-duration=2m
```

Disable it in `application-test.properties` so Spring integration tests remain deterministic.

- [ ] **Step 8: Run all focused backend tests**

Run:

```powershell
.\mvnw.cmd -Dtest=ScheduledTaskMigrationTest,SystemScheduledTaskPersistenceTest,ScheduledTaskControllerTest,DramaCatalogSyncServiceTest,ScheduledTaskDispatchServiceTest,ScheduledTaskSchedulerTest test
```

Expected: all focused tests pass with 0 failures and 0 errors.

- [ ] **Step 9: Commit the dispatch slice**

```powershell
git add src/main/java/com/kasi/backend/scheduledtask src/main/java/com/kasi/backend/drama/service src/test/java/com/kasi/backend/scheduledtask src/test/java/com/kasi/backend/drama/service/DramaCatalogSyncServiceTest.java src/main/resources/application.properties src/test/resources/application-test.properties
git commit -m "feat: schedule incremental drama synchronization"
```

### Task 5: Add the frontend API contract

**Files:**
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/features/scheduled-task/scheduledTaskTypes.ts`
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/features/scheduled-task/scheduledTaskApi.ts`
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/features/scheduled-task/scheduledTaskApi.test.ts`

- [ ] **Step 1: Write failing MSW API tests**

Assert GET unwrapping and the exact PUT body:

```ts
expect(await listScheduledTasks()).toEqual([
  {
    taskCode: 'GOODSHORT_DRAMA_INCREMENTAL_SYNC',
    title: 'GoodShort 短剧增量同步',
    description: '每隔60分钟执行一次GoodShort短剧目录增量同步',
    intervalMinutes: 60,
    enabled: true,
  },
])

expect(requestBody).toEqual({
  intervalMinutes: 30,
  description: '每隔30分钟执行一次GoodShort短剧目录增量同步',
  enabled: false,
})
```

- [ ] **Step 2: Run API tests and verify RED**

Run from `kasi-admin-web`: `pnpm test -- src/features/scheduled-task/scheduledTaskApi.test.ts`

Expected: module-not-found failure.

- [ ] **Step 3: Implement types and API mapping**

```ts
export interface ScheduledTask {
  taskCode: 'GOODSHORT_DRAMA_INCREMENTAL_SYNC'
  title: string
  description: string
  intervalMinutes: number
  enabled: boolean
}

export interface UpdateScheduledTaskRequest {
  intervalMinutes: number
  description: string
  enabled: boolean
}
```

Implement `listScheduledTasks()` with GET and `updateScheduledTask(taskCode, request)` with PUT against `/api/admin/system/scheduled-tasks`, using `httpClient` and `unwrapApiResponse`.

- [ ] **Step 4: Run API tests and verify GREEN**

Run: `pnpm test -- src/features/scheduled-task/scheduledTaskApi.test.ts`

Expected: API tests pass.

- [ ] **Step 5: Commit the frontend API slice**

```powershell
git add src/features/scheduled-task
git commit -m "feat: add scheduled task api client"
```

### Task 6: Build the exact compact table page

**Files:**
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/pages/system/ScheduledTaskPage.tsx`
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/pages/system/ScheduledTaskPage.test.tsx`
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/pages/system/scheduled-task-page.css`
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/src/layouts/AdminLayout.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/src/router/AppRouter.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/src/App.test.tsx`

- [ ] **Step 1: Write failing page tests**

Mock the GET response and assert exactly these visible column headers:

```ts
expect(screen.getByRole('columnheader', { name: '标题' })).toBeInTheDocument()
expect(screen.getByRole('columnheader', { name: '任务说明' })).toBeInTheDocument()
expect(screen.getByRole('columnheader', { name: '执行周期' })).toBeInTheDocument()
expect(screen.getByRole('columnheader', { name: '是否开启' })).toBeInTheDocument()
expect(screen.getByRole('columnheader', { name: '操作' })).toBeInTheDocument()
expect(screen.queryByText('下次执行时间')).not.toBeInTheDocument()
expect(screen.queryByRole('button', { name: '新增' })).not.toBeInTheDocument()
```

For a super admin, click “编辑” and assert only the labels `执行周期`、`任务说明`、`是否开启`; changing 60 to 30 must show `每隔30分钟执行一次`, and save must PUT the exact three fields. Assert direct row Switch update sends the same object with only `enabled` changed.

For an ordinary admin, assert the row Switch is disabled and the operation cell contains `-` with no “编辑” action.

- [ ] **Step 2: Run page tests and verify RED**

Run: `pnpm test -- src/pages/system/ScheduledTaskPage.test.tsx`

Expected: module-not-found failure.

- [ ] **Step 3: Implement the page**

Use `PageContainer` without marketing copy, an Ant Design `Table<ScheduledTask>` with `pagination={false}`, and fixed columns:

```tsx
const columns: ColumnsType<ScheduledTask> = [
  { title: '标题', dataIndex: 'title', width: 240 },
  { title: '任务说明', dataIndex: 'description' },
  {
    title: '执行周期',
    dataIndex: 'intervalMinutes',
    width: 220,
    render: (minutes) => `每隔${minutes}分钟执行一次`,
  },
  { title: '是否开启', width: 150, render: renderEnabledSwitch },
  { title: '操作', width: 100, render: renderEditAction },
]
```

The compact modal contains an inline disabled Select showing `每隔N分钟`, an `InputNumber` with `min={5}` and `max={1440}`, a live helper line, `Input.TextArea`, and a Switch. Do not render title, logs, next-run data, cards, filters, pagination, add/delete/execute buttons or other fields.

Use Lucide `Clock3` for the menu item. Add a lazy route at `/system-config/scheduled-tasks`; both admin roles may open it.

- [ ] **Step 4: Run page and app route tests and verify GREEN**

Run:

```powershell
pnpm test -- src/pages/system/ScheduledTaskPage.test.tsx src/App.test.tsx
```

Expected: page tests and route/menu tests pass.

- [ ] **Step 5: Run responsive visual verification**

Start the Vite server and inspect `/system-config/scheduled-tasks` at 1440x900 and 390x844. Confirm the five columns remain readable through horizontal table scrolling on mobile, modal fields do not overlap, and no extra panels appear.

- [ ] **Step 6: Commit the frontend page slice**

```powershell
git add src/pages/system src/layouts/AdminLayout.tsx src/router/AppRouter.tsx src/App.test.tsx
git commit -m "feat: add scheduled task settings page"
```

### Task 7: Update current documentation and verify both repositories

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/README.md`
- Modify: `docs/superpowers/specs/2026-08-21-scheduled-task-management-design.md`

- [ ] **Step 1: Update current-truth documentation**

Backend README/AGENTS changes must record V8, `system_scheduled_task`, `/api/admin/system/scheduled-tasks`, the one-minute dispatcher, editable 60-minute default, successful-FULL prerequisite, and the distinction between schedule enqueueing and the existing five-minute sync executor.

Frontend README must record “系统配置 / 定时任务”, route `/system-config/scheduled-tasks`, exact five columns, super-admin write/ordinary-admin read-only permissions, and the absence of add/delete/log/history functions.

Change the design status from `已确认，待实施` to `已实施并验证（2026-08-21）` only after all verification below succeeds.

- [ ] **Step 2: Run complete backend verification with Java 25**

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
.\mvnw.cmd -DskipTests compile
git diff --check
```

Expected: Maven reports 0 failures and 0 errors; compile exits 0; `git diff --check` has no output.

- [ ] **Step 3: Run complete frontend verification**

From `E:/JavaProjects/kasi-project/kasi-admin-web`:

```powershell
pnpm test
pnpm typecheck
pnpm lint
pnpm format:check
pnpm build
git diff --check
```

Expected: all Vitest files pass, typecheck/lint/format/build exit 0, and `git diff --check` has no output.

- [ ] **Step 4: Commit documentation in each repository**

Backend:

```powershell
git add README.md AGENTS.md docs/superpowers/specs/2026-08-21-scheduled-task-management-design.md
git commit -m "docs: record scheduled task management"
```

Frontend:

```powershell
git add README.md
git commit -m "docs: record scheduled task settings page"
```

- [ ] **Step 5: Inspect final repository state**

Run `git status --short`, `git log -6 --oneline` and `git diff HEAD~4..HEAD --stat` in each repository. Expected: no uncommitted changes and only scheduled-task implementation/documentation files in the new commits.
