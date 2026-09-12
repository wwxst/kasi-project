# GoodShort Media Filing API/Manual Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不拆分业务方案的前提下，分六个可验证阶段实现 GoodShort 按媒体选择 API/人工报白、统一五状态、双向切换、单条删除和全量 XLSX 导出。

**Architecture:** `short_drama_connection` 保存 API 媒体集合，每条 `provider_media_filing` 固化实际报白方式、真实状态和任务证据。现有报白 Worker 扩展为分批消费 SUBMIT/QUERY；用户与管理员使用不同状态投影；人工操作、方式切换和删除均通过短事务及单调递增任务版本隔离旧回写。当前设计文档保持为唯一业务规则，本文只拆实施顺序。

**Tech Stack:** Java 25、Spring Boot 4、MyBatis、Flyway、MySQL 8.4/H2、Apache POI、React 19、TypeScript 6、Ant Design、TDesign、JUnit 5、Vitest。

**Source of truth:** `docs/superpowers/specs/2026-09-10-media-filing-api-manual-design.md`

**Execution boundary:** 严格按阶段顺序实施。每一阶段完成聚焦测试和范围复核后停止并交给用户 review；未经用户明确授权不提交、不推送，也不提前进入下一阶段。生产 Flyway 迁移只在发布步骤执行，开发期间只运行测试 schema 和迁移契约。

---

## Stage 1: Schema、枚举和持久化契约

### Task 1: 新增 V9 迁移和最终结构

**Files:**
- Create: `kasi-backend/src/main/resources/db/migration/V9__media_filing_api_manual.sql`
- Modify: `kasi-backend/src/main/resources/db/kasi_promotion.sql`
- Modify: `kasi-backend/src/test/resources/test-schema.sql`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/MediaAccountFilingMigrationTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/MigrationSchemaParityMySqlContractIT.java`

- [ ] **Step 1: 写迁移失败测试**

在 `MediaAccountFilingMigrationTest` 断言以下列、默认值和存量映射：

```java
assertThat(columnExists(jdbc, "SHORT_DRAMA_CONNECTION", "API_FILING_MEDIA_TYPES")).isTrue();
assertThat(columnExists(jdbc, "PROVIDER_MEDIA_FILING", "FILING_METHOD")).isTrue();
assertThat(columnExists(jdbc, "PROVIDER_MEDIA_FILING", "LAST_SUBMIT_ATTEMPT_AT")).isTrue();
assertThat(columnExists(jdbc, "PROVIDER_MEDIA_FILING", "MANUAL_UPDATED_BY")).isTrue();
assertThat(columnExists(jdbc, "PROVIDER_MEDIA_FILING", "MANUAL_UPDATED_AT")).isTrue();
assertThat(jdbc.queryForObject(
        "SELECT api_filing_media_types FROM short_drama_connection WHERE id = ?",
        String.class, connectionId)).isEqualTo("[\"FACEBOOK\"]");
```

另增加 V1..V9 完整迁移 fixture：`remote_status=1` 映射 APPROVED、`remote_status=2` 映射 REJECTED、有成功提交时间映射 PENDING、无成功时间且有提交错误映射 SUBMIT_FAILED、其余映射 NOT_SUBMITTED；Facebook 为 API，其他媒体为 MANUAL 且 `next_action=NONE`。

- [ ] **Step 2: 运行测试确认 RED**

```powershell
cd kasi-backend
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd --% -Dtest=MediaAccountFilingMigrationTest test
```

Expected: FAIL，缺少 V9 字段和五状态映射。

- [ ] **Step 3: 写 V9 和两份最终结构**

V9 只新增结构并迁移证据，不修改 V1..V8。核心 SQL 固定为：

```sql
ALTER TABLE short_drama_connection
    ADD COLUMN api_filing_media_types VARCHAR(256) NOT NULL DEFAULT '["FACEBOOK"]'
        COMMENT '使用API报白的媒体类型JSON数组';

ALTER TABLE provider_media_filing
    ADD COLUMN filing_method VARCHAR(16) NOT NULL DEFAULT 'API' AFTER media_account_id,
    ADD COLUMN last_submit_attempt_at DATETIME NULL AFTER retry_count,
    ADD COLUMN manual_updated_by BIGINT UNSIGNED NULL AFTER last_queried_at,
    ADD COLUMN manual_updated_at DATETIME NULL AFTER manual_updated_by;

UPDATE provider_media_filing f
JOIN promotion_media_account a ON a.id = f.media_account_id
SET f.filing_method = IF(a.media_type = 'FACEBOOK', 'API', 'MANUAL'),
    f.status = CASE
        WHEN f.remote_status = '1' OR f.status = 'APPROVED' THEN 'APPROVED'
        WHEN f.remote_status = '2' THEN 'REJECTED'
        WHEN f.last_submitted_at IS NOT NULL THEN 'PENDING'
        WHEN NULLIF(TRIM(f.last_error_message), '') IS NOT NULL THEN 'SUBMIT_FAILED'
        ELSE 'NOT_SUBMITTED'
    END;

UPDATE provider_media_filing
SET next_action = CASE
        WHEN filing_method = 'MANUAL' THEN 'NONE'
        WHEN status = 'NOT_SUBMITTED' THEN 'SUBMIT'
        WHEN status = 'PENDING' THEN 'QUERY'
        ELSE 'NONE'
    END,
    next_action_at = CASE
        WHEN filing_method = 'API' AND status IN ('NOT_SUBMITTED', 'PENDING')
            THEN COALESCE(next_action_at, CURRENT_TIMESTAMP)
        ELSE NULL
    END,
    lease_owner = NULL,
    lease_until = NULL,
    retry_count = 0;

ALTER TABLE provider_media_filing
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'NOT_SUBMITTED';
```

`kasi_promotion.sql` 和 `test-schema.sql` 直接描述同一最终列集合；不回填 `last_submit_attempt_at`，不猜测旧请求是否曾发出。

- [ ] **Step 4: 运行迁移测试确认 GREEN**

运行 Step 2 相同命令。Expected: PASS，0 failures、0 errors。

### Task 2: 建立 Java/MyBatis 五状态和方式模型

**Files:**
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/enums/FilingMethod.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/enums/FilingStatus.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/entity/ProviderMediaFiling.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/entity/ShortDramaConnection.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/mapper/ProviderMediaFilingMapper.java`
- Modify: `kasi-backend/src/main/resources/mapper/ProviderMediaFilingMapper.xml`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/mapper/ShortDramaConnectionMapper.java`
- Modify: `kasi-backend/src/main/resources/mapper/ShortDramaConnectionMapper.xml`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/dto/AdminMediaAccountPageQueryDTO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/MediaFilingTaskServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/mapper/MediaAccountFilingPersistenceTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/MediaAccountServiceTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/MediaFilingTaskServiceTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/provider/goodshort/GoodShortFilingAdapterTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/provider/mapper/ProviderPersistenceTest.java`

- [ ] **Step 1: 写 Mapper 失败测试**

测试往返保存以下模型，并断言方式切换专用 UPDATE 保留远端证据且版本从 4 增为 5：

```java
public enum FilingMethod { API, MANUAL }

public enum FilingStatus {
    NOT_SUBMITTED, PENDING, APPROVED, REJECTED, SUBMIT_FAILED
}
```

```java
assertThat(stored.getFilingMethod()).isEqualTo(FilingMethod.MANUAL);
assertThat(stored.getLastSubmitAttemptAt()).isEqualTo(attemptAt);
assertThat(stored.getManualUpdatedBy()).isEqualTo(operatorId);
assertThat(stored.getTaskDataVersion()).isEqualTo(5);
assertThat(stored.getSubmittedDataVersion()).isEqualTo(4);
assertThat(stored.getRemoteStatus()).isEqualTo("1");
```

- [ ] **Step 2: 运行测试确认 RED**

```powershell
.\mvnw.cmd --% -Dtest=MediaAccountFilingPersistenceTest,ProviderPersistenceTest test
```

Expected: compilation/test FAIL，枚举与字段尚不存在。

- [ ] **Step 3: 实施模型和基础 Mapper**

`ProviderMediaFiling` 增加：

```java
private FilingMethod filingMethod;
private LocalDateTime lastSubmitAttemptAt;
private Long manualUpdatedBy;
private LocalDateTime manualUpdatedAt;
```

`ShortDramaConnection` 增加原始 JSON 字段：

```java
private String apiFilingMediaTypes;
```

更新 insert/resultMap；删除会清空真实证据的通用 `reschedule()`，改为用途明确的条件 UPDATE。所有重新排队 SQL 使用：

```sql
task_data_version = task_data_version + 1
```

并以旧 `task_data_version` 作为 WHERE 条件返回影响行数。不得再写入 `PromotionMediaAccount.dataVersion` 作为任务版本。

- [ ] **Step 4: 更新旧状态引用并确认 GREEN**

将生产代码和已有测试中的业务拒绝改为 REJECTED、提交阶段失败改为 SUBMIT_FAILED、提交前改为 NOT_SUBMITTED；查询技术失败保持 PENDING。运行：

```powershell
.\mvnw.cmd --% -Dtest=MediaAccountFilingMigrationTest,MediaAccountFilingPersistenceTest,ProviderPersistenceTest,MediaAccountServiceTest,MediaFilingTaskServiceTest,GoodShortFilingAdapterTest test
```

Expected: PASS。随后停止 Stage 1，汇报变更文件、测试数量和未执行环境项，等待 review。

---

## Stage 2: SUBMIT/QUERY Worker 和不确定提交

### Task 3: Worker 分批处理 SUBMIT 与 QUERY

**Files:**
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/MediaFilingTaskService.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/MediaFilingTaskServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/mapper/ProviderMediaFilingMapper.java`
- Modify: `kasi-backend/src/main/resources/mapper/ProviderMediaFilingMapper.xml`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountServiceImpl.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/MediaFilingTaskServiceTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/MediaAccountServiceTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/mapper/MediaAccountFilingPersistenceTest.java`

- [ ] **Step 1: 写 Worker 失败测试**

覆盖：到期扫描同时返回 API+SUBMIT 和 API+QUERY，排除 MANUAL；每次领取生成唯一 lease token；SUBMIT 在 HTTP 前写尝试时间；成功后写 PENDING+QUERY；批量排队不调用 `submitNow()`；单条创建的立即提交失败后仍可由 Worker 接续。

```java
verify(filingMapper).markSubmitAttempt(
        eq(filingId), eq(leaseToken), eq(version), eq(now));
verify(adapter).submitAccountFiling(any(), any());
verify(filingMapper).completeSubmit(
        eq(filingId), eq(leaseToken), eq(version), eq(now), any());
```

- [ ] **Step 2: 运行测试确认 RED**

```powershell
.\mvnw.cmd --% -Dtest=MediaFilingTaskServiceTest,MediaAccountServiceTest,MediaAccountFilingPersistenceTest test
```

Expected: FAIL，因为到期 SQL 只扫描 QUERY，且没有提交尝试写入。

- [ ] **Step 3: 扩展任务领取与完成条件**

`findDueIds` 条件改为：

```sql
WHERE filing_method = 'API'
  AND next_action IN ('SUBMIT', 'QUERY')
  AND next_action_at <= #{now}
  AND (lease_until IS NULL OR lease_until <= #{now})
```

每次 claim 使用 `instanceId + ':' + UUID.randomUUID()`，并在 claim/mark/complete/retry SQL 同时校验 `filing_method='API'`、预期 action、任务版本和该次 lease token。不得只用服务实例级固定 owner，避免租约过期后旧调用与新调用使用同一个 owner。

- [ ] **Step 4: 保留单条立即提交并让批量只入队**

`MediaAccountServiceImpl.create()` 对 API 记录写 `NOT_SUBMITTED + SUBMIT + next_action_at=now` 并注册一次 afterCommit `submitNow()`；MANUAL 记录不注册。配置切换产生的记录只写到期任务，不注册逐条回调。

- [ ] **Step 5: 运行聚焦测试确认 GREEN**

运行 Step 2 相同命令。Expected: PASS。

### Task 4: 收敛提交结果不确定和单调版本重试

**Files:**
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/MediaFilingTaskServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/mapper/ProviderMediaFilingMapper.java`
- Modify: `kasi-backend/src/main/resources/mapper/ProviderMediaFilingMapper.xml`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/common/exception/ErrorCode.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/MediaFilingTaskServiceTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/MediaAccountServiceTest.java`

- [ ] **Step 1: 写失败、崩溃恢复和重试 RED 测试**

覆盖 report 超时/连接中断写 `SUBMIT_FAILED + NONE + SUBMIT_OUTCOME_UNKNOWN`；模拟尝试时间已写、租约过期后 Worker 不再次调用 adapter；普通 retry 拒绝 UNKNOWN；管理员确认未收到后 retry 将版本 `n` 增为 `n+1`；确认已收到后转 PENDING+QUERY但不写 `submitted_data_version`。

- [ ] **Step 2: 实施保守的不确定结果规则**

调用 adapter 前成功写 `last_submit_attempt_at`。adapter 已开始后抛出的 `ProviderTransientException` 或未分类运行异常统一停止为：

```java
status = FilingStatus.SUBMIT_FAILED;
nextAction = FilingAction.NONE;
lastErrorCode = "SUBMIT_OUTCOME_UNKNOWN";
```

Worker 重新领取一个 `last_submit_attempt_at != null && submitted_data_version == null` 的过期 SUBMIT 时，只执行 `markSubmissionUnknown()`，不得再次 report。明确业务拒绝保存 `REMOTE_REJECTED`，本地前置校验失败保存对应本地错误，均不伪装为甲方审核未通过。

- [ ] **Step 3: 修正 retry 的版本语义**

普通 retry 只接受管理员已确认未收到的记录，专用 UPDATE 清理旧尝试/错误并执行：

```sql
SET status = 'NOT_SUBMITTED',
    next_action = 'SUBMIT',
    next_action_at = #{now},
    task_data_version = task_data_version + 1,
    last_submit_attempt_at = NULL,
    retry_count = 0,
    lease_owner = NULL,
    lease_until = NULL
WHERE id = #{id}
  AND filing_method = 'API'
  AND status = 'SUBMIT_FAILED'
  AND last_error_code = 'SUBMIT_CONFIRMED_NOT_RECEIVED'
  AND task_data_version = #{expectedVersion}
```

- [ ] **Step 4: 运行聚焦测试确认 GREEN**

```powershell
.\mvnw.cmd --% -Dtest=MediaFilingTaskServiceTest,MediaAccountServiceTest,MediaAccountFilingPersistenceTest test
```

Expected: PASS。停止 Stage 2 并等待 review。

---

## Stage 3: 媒体配置、双向切换和人工处理

### Task 5: GoodShort 媒体复选框后端契约与切换事务

**Files:**
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/MediaFilingMethodService.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/MediaFilingMethodServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/dto/UpsertProviderConnectionDTO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/vo/ProviderConnectionVO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/service/impl/ProviderConnectionServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/mapper/ShortDramaConnectionMapper.java`
- Modify: `kasi-backend/src/main/resources/mapper/ShortDramaConnectionMapper.xml`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/mapper/ProviderMediaFilingMapper.java`
- Modify: `kasi-backend/src/main/resources/mapper/ProviderMediaFilingMapper.xml`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/provider/service/ProviderConnectionServiceTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/provider/controller/ProviderAdminControllerTest.java`
- Create: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/MediaFilingMethodServiceTest.java`

- [ ] **Step 1: 写配置和切换 RED 测试**

验证新建必须显式提交集合、`[]` 表示全部人工、更新缺省保留原值、未知/重复媒体被拒绝、只有超级管理员可写。验证变化媒体影响已有记录：人工 NOT_SUBMITTED→API SUBMIT、人工 PENDING→API QUERY、终态保留且 NONE、API→人工全部 NONE；重复保存不改版本；任一活动租约使整个保存事务失败。

- [ ] **Step 2: 增加类型化 API 契约**

```java
private List<MediaType> apiFilingMediaTypes;
```

请求中 null 仅对已有连接表示保留；新建连接 null 返回 VALIDATION_ERROR。保留 List 输入以便显式拒绝重复媒体，校验通过后按枚举顺序去重排序再保存。实体继续存 JSON 字符串，Service 使用项目现有 Jackson 3 `ObjectMapper` 序列化为稳定枚举数组；响应始终返回类型化列表。

- [ ] **Step 3: 实施原子切换**

`ProviderConnectionServiceImpl.upsert()` 在同一事务中锁定现有连接、保存配置，并把旧集合与新集合的差集交给 `MediaFilingMethodService`。切换 UPDATE 必须：版本加一、失效租约、保留 `submitted_data_version`/远端状态/真实时间；存在活动租约或未处理 UNKNOWN 时抛业务错误并回滚连接配置与全部 filing 变化。事务内不调用 GoodShort HTTP。

- [ ] **Step 4: 运行聚焦测试确认 GREEN**

```powershell
.\mvnw.cmd --% -Dtest=ProviderConnectionServiceTest,ProviderAdminControllerTest,MediaFilingMethodServiceTest,MediaAccountFilingPersistenceTest test
```

Expected: PASS。

### Task 6: 人工状态和未知提交核实接口

**Files:**
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/enums/SubmissionResolution.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/dto/ResolveFilingSubmissionDTO.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/dto/UpdateManualFilingStatusDTO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/controller/AdminMediaAccountController.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/MediaAccountAdminService.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountAdminServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/mapper/ProviderMediaFilingMapper.java`
- Modify: `kasi-backend/src/main/resources/mapper/ProviderMediaFilingMapper.xml`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/controller/AdminMediaAccountControllerTest.java`
- Create: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/MediaAccountAdminServiceTest.java`

- [ ] **Step 1: 写状态切换 RED 测试**

验证新建 MANUAL 记录直接为 PENDING；MANUAL 可执行 NOT_SUBMITTED→APPROVED/REJECTED、PENDING→APPROVED/REJECTED、APPROVED↔REJECTED、SUBMIT_FAILED→APPROVED/REJECTED，不接受人工标记 PENDING 且不要求原因。验证 API 记录拒绝人工状态操作，并发条件更新只能一个成功。

- [ ] **Step 2: 实施人工状态接口**

```http
PATCH /api/admin/promotion/media-accounts/{id}/filings/{providerId}/status
Content-Type: application/json

{"status":"APPROVED"}
```

DTO 只允许 APPROVED/REJECTED。成功更新写 `manual_updated_by/manual_updated_at`、任务版本加一、`next_action=NONE` 并清除已经处理的技术错误；人工创建和状态操作都不虚构甲方提交时间。

- [ ] **Step 3: 实施 UNKNOWN 核实接口**

```http
POST /api/admin/promotion/media-accounts/{id}/filings/{providerId}/submission-resolution
Content-Type: application/json

{"resolution":"RECEIVED"}
```

RECEIVED→PENDING+QUERY并写管理员确认时间；NOT_RECEIVED→SUBMIT_FAILED+NONE+`SUBMIT_CONFIRMED_NOT_RECEIVED`，之后才允许 retry。两者都写操作人/时间、版本加一，不伪造 `submitted_data_version`。

- [ ] **Step 4: 运行聚焦测试确认 GREEN**

```powershell
.\mvnw.cmd --% -Dtest=AdminMediaAccountControllerTest,MediaAccountAdminServiceTest,MediaFilingMethodServiceTest test
```

Expected: PASS。停止 Stage 3 并等待 review。

---

## Stage 4: 管理端/用户端展示和不限状态单条删除

### Task 7: 拆分管理员真实状态与用户展示状态

**Files:**
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/vo/AdminMediaFilingVO.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/vo/AdminMediaAccountVO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/vo/AdminMediaAccountDetailVO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/vo/AdminMediaAccountListItemVO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/vo/MediaFilingVO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountAdminServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/dto/AdminMediaAccountPageQueryDTO.java`
- Modify: `kasi-backend/src/main/resources/mapper/PromotionMediaAccountMapper.xml`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/controller/UserMediaAccountControllerTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/controller/AdminMediaAccountControllerTest.java`

- [ ] **Step 1: 写投影和筛选 RED 测试**

同一 SUBMIT_FAILED 记录断言用户响应为 PENDING、无错误码/原因，管理员响应为 SUBMIT_FAILED、包含错误和 filingMethod。筛选五项直接匹配真实 `f.status`，删除旧的 QUERY_FAILED/派生判断；多 filing 时 provider/status/method 必须由同一 filing 同时满足。

- [ ] **Step 2: 实施两套 VO 转换**

用户转换规则固定为：

```java
FilingStatus visibleStatus = filing.getStatus() == FilingStatus.SUBMIT_FAILED
        ? FilingStatus.PENDING : filing.getStatus();
```

且 SUBMIT_FAILED 时不映射 `lastErrorCode/lastErrorMessage`。管理员详情不再调用 `getMineById()`，直接构建 Admin VO，返回真实五状态、方式、错误、人工操作信息和是否可执行 retry/UNKNOWN 核实。

- [ ] **Step 3: 简化数据库筛选**

允许的 `filingStatus` 仅为五枚举；新增 `filingMethod`。列表仍是一账号一行，但通过 EXISTS 保证 provider/method/status 条件命中同一 filing，Service 选择该匹配 filing 展示；导出不复用此分页 SQL。

- [ ] **Step 4: 运行后端聚焦测试确认 GREEN**

```powershell
.\mvnw.cmd --% -Dtest=UserMediaAccountControllerTest,AdminMediaAccountControllerTest,MediaAccountFilingPersistenceTest test
```

Expected: PASS。

### Task 8: 管理端配置、状态操作和用户端文案

**Files:**
- Modify: `kasi-admin-web/src/features/provider/providerTypes.ts`
- Modify: `kasi-admin-web/src/pages/provider/ProviderManagementPage.tsx`
- Modify: `kasi-admin-web/src/pages/provider/ProviderManagementPage.test.tsx`
- Modify: `kasi-admin-web/src/features/provider/providerApi.test.ts`
- Modify: `kasi-admin-web/src/features/promotion/mediaAccountTypes.ts`
- Modify: `kasi-admin-web/src/features/promotion/mediaAccountApi.ts`
- Modify: `kasi-admin-web/src/features/promotion/mediaAccountApi.test.ts`
- Modify: `kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.tsx`
- Modify: `kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.test.tsx`
- Modify: `kasi-admin-web/src/pages/promotion/media-account-filing-page.css`
- Modify: `kasi-user-web/src/features/mediaAccounts/types.ts`
- Modify: `kasi-user-web/src/pages/mediaAccounts/mediaAccountList.ts`
- Modify: `kasi-user-web/src/pages/mediaAccounts/MediaAccountsPage.test.tsx`
- Modify: `kasi-user-web/src/pages/mediaAccounts/components/AccountFilingDialog.test.tsx`

- [ ] **Step 1: 写前端 RED 测试**

配置页断言 Facebook/TikTok/YouTube/Instagram 复选框回显并随保存发送；空集合可保存。报白页断言五状态、API/人工筛选和按钮：已加白人工记录只显示“报白未通过”，未通过只显示“报白通过”，不出现原因输入；API 记录无人工按钮；UNKNOWN 显示两个核实操作。用户页断言接口即使意外带错误字段也不显示“提交失败”或错误原因。

- [ ] **Step 2: 实施管理端交互**

Provider 表单字段使用 `Checkbox.Group`，保存时始终发送 `apiFilingMediaTypes`。账号报白页使用后端真实状态，不再根据时间/error/remoteStatus 猜状态；按钮直接调用 PATCH，操作成功重新获取详情并刷新列表。

- [ ] **Step 3: 实施用户端类型和显示**

用户端 FilingStatus 只接收 NOT_SUBMITTED/PENDING/APPROVED/REJECTED；显示逻辑按响应 status，不增加 SUBMIT_FAILED 展示分支，也不显示管理员错误字段。

- [ ] **Step 4: 运行两端聚焦测试确认 GREEN**

```powershell
cd ..\kasi-admin-web
pnpm test -- ProviderManagementPage.test.tsx MediaAccountFilingPage.test.tsx mediaAccountApi.test.ts providerApi.test.ts
cd ..\kasi-user-web
pnpm test -- MediaAccountsPage.test.tsx AccountFilingDialog.test.tsx mediaAccountList.test.ts
```

Expected: 两条命令 PASS。

### Task 9: 移除所有状态下的单条删除限制

**Files:**
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountAdminServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/common/exception/ErrorCode.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/controller/AdminMediaAccountControllerTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/mapper/MediaAccountFilingPersistenceTest.java`
- Modify: `kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.tsx`
- Modify: `kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.test.tsx`

- [ ] **Step 1: 写不限状态删除 RED 测试**

分别创建五状态、MANUAL/API、无 filing、多 filing 和活动租约记录；逐条 DELETE 均成功，只删除选中账号及其 filings。并发旧结果 UPDATE 必须影响 0 行，相同外部 ID 重建使用新 filing ID且不被旧任务写入。

- [ ] **Step 2: 简化事务删除**

保留 `findByIdForUpdate(id)` 和权限/不存在校验，删除 `cannotDelete()` 及 `MEDIA_ACCOUNT_DELETE_NOT_ALLOWED`：

```java
filingMapper.deleteByMediaAccountId(id);
if (mediaMapper.deleteById(id) != 1) {
    throw new IllegalStateException("媒体账号删除未生效");
}
```

不增加批量删除、软删除、tombstone 或甲方撤销调用。

- [ ] **Step 3: 管理端所有详情保持单条删除按钮**

按钮不再按状态、方式或租约禁用；确认文案明确只删除本系统账号及报白记录，不会删除甲方记录。

- [ ] **Step 4: 运行 Stage 4 Gate**

```powershell
cd ..\kasi-backend
.\mvnw.cmd --% -Dtest=AdminMediaAccountControllerTest,UserMediaAccountControllerTest,MediaAccountFilingPersistenceTest test
cd ..\kasi-admin-web
pnpm test -- ProviderManagementPage.test.tsx MediaAccountFilingPage.test.tsx mediaAccountApi.test.ts providerApi.test.ts
cd ..\kasi-user-web
pnpm test -- MediaAccountsPage.test.tsx AccountFilingDialog.test.tsx mediaAccountList.test.ts
```

Expected: 全部 PASS。停止 Stage 4 并等待 review。

---

## Stage 5: 账号报白和订单 XLSX

两处导出均覆盖当前筛选条件下的全部匹配数据，不设置固定行数上限，不受页面分页影响，也不允许静默截断。

### Task 10: 建立真正的 XLSX 写入与账号报白导出

**Files:**
- Modify: `kasi-backend/pom.xml`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/export/XlsxExportSupport.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/vo/MediaAccountFilingExportRow.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/mapper/MediaAccountFilingExportMapper.java`
- Create: `kasi-backend/src/main/resources/mapper/MediaAccountFilingExportMapper.xml`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/controller/AdminMediaAccountController.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/MediaAccountAdminService.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountAdminServiceImpl.java`
- Create: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/MediaAccountFilingExportTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/controller/AdminMediaAccountControllerTest.java`

- [ ] **Step 1: 写 11 列和长 ID RED 测试**

用 Apache POI 重新打开响应，断言唯一 sheet 恰好 11 列，顺序为：

```java
List.of("创建时间", "昵称", "姓名", "电话", "微信号", "媒体平台",
        "账号 ID", "账号名称", "账号链接", "报备状态", "短剧平台")
```

断言 `7580215976701887501`、电话、微信号和以 `=` 开头的用户输入均为字符串单元格；筛选命中多个 filing 时每个“账号+短剧平台”一行，跨分页全部导出且无固定行数截断。

- [ ] **Step 2: 增加单一 Excel 依赖和写入工具**

在 `pom.xml` 只加入 `org.apache.poi:poi-ooxml:5.4.1`。`XlsxExportSupport` 使用 `SXSSFWorkbook(100)`、`Asia/Shanghai` 日期格式和显式 STRING/NUMERIC 单元格，写入传入 `OutputStream` 并在 finally 调用 `dispose()`；不创建通用导出平台。

- [ ] **Step 3: 增加 filing 级导出查询与端点**

独立 Mapper 使用：

```sql
FROM promotion_media_account a
JOIN promotion_user u ON u.id = a.user_id
JOIN provider_media_filing f ON f.media_account_id = a.id
JOIN short_drama_connection c ON c.id = f.connection_id
JOIN short_drama_provider p ON p.id = c.provider_id
```

providerId、mediaType、filingMethod、filingStatus 必须同时作用于当前 `f`。Controller 暴露 `/export.xlsx`，响应 MIME 为 `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，文件名 `media-account-filings.xlsx`。

- [ ] **Step 4: 运行账号导出测试确认 GREEN**

```powershell
.\mvnw.cmd --% -Dtest=MediaAccountFilingExportTest,AdminMediaAccountControllerTest test
```

Expected: PASS。

### Task 11: 将订单 CSV 完整替换为 XLSX

**Files:**
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/controller/AdminPromotionOrderController.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/PromotionOrderAdminService.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderAdminServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/mapper/PromotionOrderMapper.java`
- Modify: `kasi-backend/src/main/resources/mapper/PromotionOrderMapper.xml`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/PromotionOrderAdminServiceTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/controller/AdminPromotionOrderControllerTest.java`
- Modify: `kasi-admin-web/src/features/promotion/promotionOrderApi.ts`
- Modify: `kasi-admin-web/src/pages/promotion/PromotionOrderPage.tsx`
- Modify: `kasi-admin-web/src/pages/promotion/PromotionOrderPage.test.tsx`
- Modify: `kasi-admin-web/src/features/promotion/mediaAccountApi.ts`
- Modify: `kasi-admin-web/src/features/promotion/mediaAccountApi.test.ts`
- Modify: `kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.tsx`
- Modify: `kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.test.tsx`

- [ ] **Step 1: 写订单 XLSX 和前端下载 RED 测试**

后端断言原 CSV 的 10 列集合、筛选和退款佣金口径不变，金额为 NUMERIC、订单 ID/用户 ID 为 STRING；旧 `/export.csv` 不再存在。前端断言两个按钮均显示“导出 Excel”，请求 `.xlsx` 并下载正确文件名。

- [ ] **Step 2: 实施无固定上限的导出查询**

新增不带 LIMIT 的 `findForExport(...)`，复用订单分页相同 WHERE，但不传 `Integer.MAX_VALUE` 假装全量。删除 `EXPORT_LIMIT`、CSV 拼接和 BOM；Service 使用同一 `XlsxExportSupport` 写工作簿。

- [ ] **Step 3: 切换路由和前端调用**

订单端点改为 `/export.xlsx`、文件名 `promotion-orders.xlsx`。账号页面新增导出按钮，将当前 ProTable 筛选条件传给账号导出 API；两处均以 Blob 下载，不保留 CSV 兼容别名。

- [ ] **Step 4: 运行 Stage 5 Gate**

```powershell
cd ..\kasi-backend
.\mvnw.cmd --% -Dtest=MediaAccountFilingExportTest,AdminMediaAccountControllerTest,PromotionOrderAdminServiceTest,AdminPromotionOrderControllerTest test
cd ..\kasi-admin-web
pnpm test -- MediaAccountFilingPage.test.tsx PromotionOrderPage.test.tsx mediaAccountApi.test.ts
```

Expected: 全部 PASS。停止 Stage 5 并等待 review。

---

## Stage 6: 存量核对、完整 Gate 和发布文档

### Task 12: 完成存量验证和当前文档更新

**Files:**
- Modify: `kasi-backend/README.md`
- Modify: `docs/architecture/current.md`
- Modify: `docs/development/testing.md` only if verification commands or test classification change
- Move after implementation: `docs/superpowers/plans/2026-09-08-media-account-filing-final.md` to `docs/archive/`
- Keep: `docs/superpowers/specs/2026-09-10-media-filing-api-manual-design.md`

- [ ] **Step 1: 运行静态范围复核**

```powershell
rg -n "export\.csv|text/csv|QUERY_FAILED|FilingStatus\.FAILED|MEDIA_ACCOUNT_DELETE_NOT_ALLOWED|filing_mode" kasi-backend/src kasi-admin-web/src kasi-user-web/src docs/architecture/current.md kasi-backend/README.md
```

Expected: 当前实现和 current docs 无旧契约；历史归档中的旧文字不做机械替换。

- [ ] **Step 2: 运行三个应用 canonical Gate**

```powershell
cd kasi-backend
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd verify
cd ..\kasi-admin-web
pnpm check
cd ..\kasi-user-web
pnpm check
cd ..
git diff --check
```

Expected: 四条命令均 exit 0；记录 Maven/JUnit/Vitest 测试数量、warnings 和 build 结果。

- [ ] **Step 3: 运行真实环境契约或明确 SKIP**

```powershell
cd kasi-backend
.\mvnw.cmd -Pmysql-contract-tests "-Dtest=*MySqlContractIT" test
```

Expected: 配置真实 MySQL 时 PASS 并验证开发重建与 V1..V9 一致；无 `MYSQL_CONTRACT_URL`/`MYSQL_MIGRATION_URL` 时按测试输出记录 SKIP，不能写 PASS。GoodShort report/query 只有获得授权账号和环境后才执行；否则记录 SKIP，不用免费内容 smoke 代替。

- [ ] **Step 4: 准备发布前只读核对结果**

统计每种旧状态证据、媒体类型、当前任务动作、活动租约和矛盾组合；确认 GoodShort 非 Facebook 切为 MANUAL 的影响行数。只读核对结束后停止，不连接生产执行 V9，不批量调用甲方接口。

- [ ] **Step 5: 更新 current docs 并归档旧实施计划**

只有全部实现和 Gate 完成后，才把已实现行为写入 `current.md`/backend README，并将被替代的 2026-09-08 旧实施计划移入 archive。设计文档保留为决策依据。

- [ ] **Step 6: 最终 review 停止点**

汇报各 Gate 的 PASS/FAIL/SKIP、生产迁移尚未执行、实际 diff 和剩余发布步骤。未经用户明确授权，不执行 Git commit/push，不运行生产 Flyway migrate。
