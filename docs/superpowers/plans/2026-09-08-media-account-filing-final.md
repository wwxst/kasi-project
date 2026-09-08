# Media Account Filing Final Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将媒体账号报白收敛为不可变、全局唯一、仅 GoodShort API 自动执行的闭环，并补齐管理员失败删除/技术重试、Filing 限流、查询终止和时区转换。

**Architecture:** 保留现有 `afterCommit -> submitNow()` 和只消费 QUERY 的 Scheduler。删除 MANUAL、媒体账号编辑和用户 Retry 契约；管理员服务负责精确 Retry 与事务删除；GoodShort Adapter 边界负责 report/query 独立限流和 Offset 转换；现有 Filing 字段派生“查询失败”。

**Tech Stack:** Java 25、Spring Boot 4、MyBatis、Flyway、MySQL/H2、React 19、TypeScript、Ant Design、TDesign、Vitest、JUnit 5。

**Execution note:** 设计和实施阶段未提交或推送；完成验证后，用户已明确授权提交并推送。

---

### Task 1: 删除 MANUAL schema 和平台配置契约

**Files:**
- Create: `kasi-backend/src/main/resources/db/migration/V4__remove_manual_media_filing.sql`
- Modify: `kasi-backend/src/main/resources/db/kasi_promotion.sql`
- Modify: `kasi-backend/src/test/resources/test-schema.sql`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/MediaAccountFilingMigrationTest.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/entity/ShortDramaConnection.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/vo/ProviderConnectionVO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/dto/UpsertProviderConnectionDTO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/service/ProviderConnectionService.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/service/impl/ProviderConnectionServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/service/impl/ProviderRuntimeConnectionServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/controller/ProviderAdminController.java`
- Delete: `kasi-backend/src/main/java/com/kasi/backend/provider/enums/FilingMode.java`
- Delete: `kasi-backend/src/main/java/com/kasi/backend/provider/dto/UpdateProviderFilingModeDTO.java`
- Delete: `kasi-backend/src/main/java/com/kasi/backend/provider/vo/ProviderFilingModeVO.java`

- [ ] **Step 1: 写失败的迁移和平台契约测试**

在 `MediaAccountFilingMigrationTest` 断言最终重建脚本包含全局媒体账号唯一索引，但不再包含 `filing_mode` 或 `operate_by`。更新 `ProviderConnectionServiceTest` 和 `ProviderRuntimeConnectionServiceTest`，期望 API 配置始终要求 URL、媒体根域、PID 和首次 KEY，且运行连接不再存在 MANUAL 分支。

- [ ] **Step 2: 运行测试确认 RED**

```powershell
cd kasi-backend
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd --% -Dtest=MediaAccountFilingMigrationTest,ProviderConnectionServiceTest,ProviderRuntimeConnectionServiceTest,ProviderAdminControllerTest test
```

Expected: FAIL，因为当前 schema、DTO、Controller 和 Service 仍暴露 MANUAL。

- [ ] **Step 3: 实施最小 schema 和后端清理**

迁移内容固定为：

```sql
ALTER TABLE short_drama_connection DROP COLUMN filing_mode;
ALTER TABLE provider_media_filing DROP COLUMN operate_by;
```

`UpsertProviderConnectionDTO` 删除 `filingMode` 和条件式 `@AssertTrue`；`ProviderConnectionServiceImpl` 始终按 API 配置校验；Controller 删除 `/filing-mode` GET/PUT；运行时连接服务删除 `resolveManual()`。

- [ ] **Step 4: 运行聚焦测试确认 GREEN**

运行 Step 2 相同命令，Expected: PASS。

### Task 2: 删除媒体账号修改、人工审核和用户 Retry API

**Files:**
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/controller/UserMediaAccountController.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/controller/AdminMediaAccountController.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/MediaAccountService.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/MediaAccountAdminService.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountAdminServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/mapper/PromotionMediaAccountMapper.java`
- Modify: `kasi-backend/src/main/resources/mapper/PromotionMediaAccountMapper.xml`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/mapper/ProviderMediaFilingMapper.java`
- Modify: `kasi-backend/src/main/resources/mapper/ProviderMediaFilingMapper.xml`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/entity/ProviderMediaFiling.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/vo/MediaFilingVO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/common/exception/ErrorCode.java`
- Delete: `kasi-backend/src/main/java/com/kasi/backend/promotion/dto/UpdateMediaAccountDTO.java`
- Delete: `kasi-backend/src/main/java/com/kasi/backend/promotion/dto/AdminUpdateMediaAccountDTO.java`
- Delete: `kasi-backend/src/main/java/com/kasi/backend/promotion/dto/UpdateMediaAccountStatusDTO.java`
- Delete: `kasi-backend/src/main/java/com/kasi/backend/promotion/dto/UpdateMediaFilingStatusDTO.java`

- [ ] **Step 1: 写 Controller 和 Service 失败测试**

更新 `UserMediaAccountControllerTest`，断言用户 PUT、PATCH status 和 POST Filing Retry 返回 405。更新 `AdminMediaAccountControllerTest`，断言管理员 PUT 和 PATCH Filing status 返回 405。

更新 `MediaAccountServiceTest`，用期望的新方法表达技术 Retry：

```java
service.retryFailedSubmission(mediaAccountId, providerId);
verify(filingMapper).reschedule(filingId, FilingStatus.PENDING,
        FilingAction.SUBMIT, version, version, now);
```

并覆盖 Filing 不存在、已有 `lastSubmittedAt`、`nextAction != NONE`、错误码不是 `REMOTE_TRANSIENT` 时返回 `MEDIA_FILING_RETRY_NOT_ALLOWED`。

- [ ] **Step 2: 运行测试确认 RED**

```powershell
.\mvnw.cmd --% -Dtest=UserMediaAccountControllerTest,AdminMediaAccountControllerTest,MediaAccountServiceTest test
```

Expected: FAIL，因为旧接口仍存在且 Retry 条件过宽。

- [ ] **Step 3: 删除旧契约并收紧 Retry**

`MediaAccountService` 只保留查询、创建和管理员内部使用的：

```java
MediaFilingVO retryFailedSubmission(Long mediaAccountId, Long providerId);
```

Retry 必须验证现有 Filing 的 `lastSubmittedAt == null`、`nextAction == NONE`、`lastErrorCode == "REMOTE_TRANSIENT"`，然后复用原 Filing 排队并在事务提交后立即 `submitNow()`。删除人工状态 Mapper、`operateBy` 映射和已无消费者的错误码。

- [ ] **Step 4: 运行聚焦测试确认 GREEN**

运行 Step 2 相同命令，Expected: PASS。

### Task 3: 增加管理员条件删除

**Files:**
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/controller/AdminMediaAccountControllerTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/MediaAccountServiceTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/mapper/MediaAccountFilingPersistenceTest.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/controller/AdminMediaAccountController.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/MediaAccountAdminService.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountAdminServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/mapper/PromotionMediaAccountMapper.java`
- Modify: `kasi-backend/src/main/resources/mapper/PromotionMediaAccountMapper.xml`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/mapper/ProviderMediaFilingMapper.java`
- Modify: `kasi-backend/src/main/resources/mapper/ProviderMediaFilingMapper.xml`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/common/exception/ErrorCode.java`

- [ ] **Step 1: 写删除规则失败测试**

覆盖允许删除：

```text
last_submitted_at IS NULL + next_action NONE + last_error_message 非空
remote_status = 2 + next_action NONE
```

覆盖拒绝删除：待提交/SUBMIT、审核中/QUERY、APPROVED、查询失败（已提交、FAILED、错误非空、remoteStatus 非 2）。覆盖多个 Filing 中任意一个不可删除时整体拒绝。

- [ ] **Step 2: 运行测试确认 RED**

```powershell
.\mvnw.cmd --% -Dtest=AdminMediaAccountControllerTest,MediaAccountServiceTest,MediaAccountFilingPersistenceTest test
```

Expected: FAIL，因为 DELETE API 和 Mapper 删除方法不存在。

- [ ] **Step 3: 实施事务删除**

新增错误码：

```java
MEDIA_ACCOUNT_DELETE_NOT_ALLOWED(7013, "当前报白状态不允许删除")
```

管理员服务先 `findByIdForUpdate()`，再验证所有 Filing；验证通过后调用：

```java
filingMapper.deleteByMediaAccountId(id);
mediaMapper.deleteById(id);
```

Controller 暴露 `DELETE /api/admin/promotion/media-accounts/{id}`。Mapper SQL 使用显式 DELETE，不增加数据库级联。

- [ ] **Step 4: 运行测试确认 GREEN**

运行 Step 2 相同命令，Expected: PASS。

### Task 4: 限制 QUERY 连续技术异常并派生查询失败

**Files:**
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/config/MediaFilingProperties.java`
- Modify: `kasi-backend/src/main/resources/application.properties`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/MediaFilingTaskServiceImpl.java`
- Modify: `kasi-backend/src/main/resources/mapper/PromotionMediaAccountMapper.xml`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/MediaFilingTaskServiceTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/controller/AdminMediaAccountControllerTest.java`

- [ ] **Step 1: 写查询终止失败测试**

设置 `maxQueryRetries=5`，验证第 4 次 `ProviderTransientException` 仍排队，第 5 次写入 `FAILED + NONE`。验证成功 `status=0` 通过 `completeQuery()` 将 `retry_count` 清零。验证 `ProviderRemoteRejectedException` 和其他不可恢复查询错误直接写入 `FAILED + NONE`。

增加管理筛选 `QUERY_FAILED`，匹配：

```sql
f.status = 'FAILED'
AND f.last_submitted_at IS NOT NULL
AND COALESCE(f.remote_status, '') <> '2'
AND f.last_error_message IS NOT NULL
```

- [ ] **Step 2: 运行测试确认 RED**

```powershell
.\mvnw.cmd --% -Dtest=MediaFilingTaskServiceTest,AdminMediaAccountControllerTest test
```

Expected: FAIL，因为当前查询异常无限退避且没有 `QUERY_FAILED` 筛选。

- [ ] **Step 3: 实施最小终止逻辑**

新增：

```java
private int maxQueryRetries = 5;
```

`recordRetry()` 仅对 QUERY 临时错误比较 `retries >= maxQueryRetries`；达到上限调用现有 Mapper 写 `FAILED + NONE`。查询永久错误直接终止。report 临时失败仍保持可由管理员 Retry 的 `PENDING + NONE + REMOTE_TRANSIENT`。

- [ ] **Step 4: 运行聚焦测试确认 GREEN**

运行 Step 2 相同命令，Expected: PASS。

### Task 5: GoodShort Filing 限流和时区转换

**Files:**
- Create: `kasi-backend/src/main/java/com/kasi/backend/provider/goodshort/GoodShortFilingRateLimiter.java`
- Create: `kasi-backend/src/test/java/com/kasi/backend/provider/goodshort/GoodShortFilingRateLimiterTest.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/provider/goodshort/GoodShortFilingAdapterTest.java`

- [ ] **Step 1: 写限流和时间失败测试**

限流测试用可控 `LongSupplier`/`LongConsumer` 验证相同连接的 REPORT 间隔 650ms、QUERY 间隔 650ms，REPORT 与 QUERY 互不阻塞。Adapter 测试验证每次真实 filing HTTP 前调用：

```java
filingRateLimiter.acquire(baseUrl + "|" + partnerId + "|REPORT");
filingRateLimiter.acquire(baseUrl + "|" + partnerId + "|QUERY");
```

时间测试以 `Asia/Shanghai` Clock 断言 `+0000` 的 11:26 转为 19:26，`+0800` 保持 11:26，并覆盖 ISO Offset 与 epoch millis。

- [ ] **Step 2: 运行测试确认 RED**

```powershell
.\mvnw.cmd --% -Dtest=GoodShortFilingRateLimiterTest,GoodShortFilingAdapterTest test
```

Expected: FAIL，因为 Filing 限流器不存在且 Offset 被直接丢弃。

- [ ] **Step 3: 实施专属限流器和统一 Offset 转换**

限流器只保存每个 Key 的 `nextAllowedAt`，默认间隔 `Duration.ofMillis(650)`，沿用现有 GoodShort 专属限流器的同步 Map 模式。所有 Offset 分支统一调用：

```java
private LocalDateTime toBusinessTime(OffsetDateTime value) {
    return value.atZoneSameInstant(clock.getZone()).toLocalDateTime();
}
```

- [ ] **Step 4: 运行聚焦测试确认 GREEN**

运行 Step 2 相同命令，Expected: PASS。

### Task 6: 更新管理端页面和 API

**Files:**
- Modify: `kasi-admin-web/src/features/provider/providerTypes.ts`
- Modify: `kasi-admin-web/src/features/provider/providerApi.test.ts`
- Modify: `kasi-admin-web/src/pages/provider/ProviderManagementPage.tsx`
- Modify: `kasi-admin-web/src/pages/provider/ProviderManagementPage.test.tsx`
- Delete: `kasi-admin-web/src/features/promotion/filingModeTypes.ts`
- Delete: `kasi-admin-web/src/features/promotion/filingModeApi.ts`
- Delete: `kasi-admin-web/src/features/promotion/filingModeApi.test.ts`
- Modify: `kasi-admin-web/src/features/promotion/mediaAccountTypes.ts`
- Modify: `kasi-admin-web/src/features/promotion/mediaAccountApi.ts`
- Modify: `kasi-admin-web/src/features/promotion/mediaAccountApi.test.ts`
- Modify: `kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.tsx`
- Modify: `kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.test.tsx`

- [ ] **Step 1: 写管理端失败测试**

Provider 配置测试断言不再出现“账号报备方式”或“人工报备”。媒体账号测试断言没有编辑按钮；技术提交失败和已拒绝详情显示删除按钮；审核中、已加白、查询失败不显示；删除二次确认后调用 DELETE 并关闭详情、刷新表格。增加 `QUERY_FAILED -> 查询失败` 标签和筛选。

- [ ] **Step 2: 运行测试确认 RED**

```powershell
cd kasi-admin-web
pnpm test -- src/pages/provider/ProviderManagementPage.test.tsx src/features/promotion/mediaAccountApi.test.ts src/pages/promotion/MediaAccountFilingPage.test.tsx
```

Expected: FAIL，因为 MANUAL/编辑仍存在且 DELETE、查询失败 UI 不存在。

- [ ] **Step 3: 实施管理端最小 UI**

Provider 配置始终显示 API 字段。媒体账号详情移除编辑 Drawer；保留满足 `REMOTE_TRANSIENT` 的重新提交；按后端同样的派生规则显示带二次确认的删除按钮。API 新增：

```ts
export async function deleteAdminMediaAccount(id: number): Promise<void> {
  const response = await httpClient.delete<ApiResponse<null>>(`${basePath}/${id}`)
  unwrapApiResponse(response.data)
}
```

- [ ] **Step 4: 运行聚焦测试确认 GREEN**

运行 Step 2 相同命令，Expected: PASS。

### Task 7: 更新用户端派生状态并删除用户 Retry 客户端

**Files:**
- Modify: `kasi-user-web/src/features/mediaAccounts/types.ts`
- Modify: `kasi-user-web/src/features/mediaAccounts/mediaAccountsApi.ts`
- Modify: `kasi-user-web/src/features/mediaAccounts/mediaAccountsApi.test.ts`
- Modify: `kasi-user-web/src/pages/mediaAccounts/MediaAccountsPage.tsx`
- Modify: `kasi-user-web/src/pages/mediaAccounts/MediaAccountsPage.test.tsx`

- [ ] **Step 1: 写用户端失败测试**

增加已成功提交、`status=FAILED`、`remoteStatus=null`、存在最后错误的记录，期望显示“查询失败”而不是“审核中”。删除 `submitMediaFiling` API 测试并断言页面仍没有操作列。

- [ ] **Step 2: 运行测试确认 RED**

```powershell
cd kasi-user-web
pnpm test -- src/features/mediaAccounts/mediaAccountsApi.test.ts src/pages/mediaAccounts/MediaAccountsPage.test.tsx
```

Expected: FAIL，因为查询失败仍显示审核中。

- [ ] **Step 3: 实施派生状态和客户端清理**

在 `remoteStatus=2` 之后、默认审核中之前识别：

```ts
if (filing.status === 'FAILED' && filing.lastErrorMessage) return '查询失败'
```

删除未被页面消费的用户 Retry 客户端函数和 `operateBy` 类型字段，不增加操作列。

- [ ] **Step 4: 运行聚焦测试确认 GREEN**

运行 Step 2 相同命令，Expected: PASS。

### Task 8: 同步 current docs 并执行完整验证

**Files:**
- Modify: `kasi-backend/AGENTS.md`
- Modify: `kasi-backend/README.md`
- Modify: `kasi-admin-web/README.md`
- Modify: `kasi-user-web/README.md`
- Modify if necessary: `docs/architecture/current.md`

- [ ] **Step 1: 更新当前事实**

文档只描述已实现结果：API-only、媒体账号不可变且全局唯一、管理员技术 Retry/条件删除、查询失败、限流和时区规则。删除 MANUAL、编辑和用户 Retry 的旧描述。

- [ ] **Step 2: 运行后端完整 Gate**

```powershell
cd kasi-backend
.\mvnw.cmd verify
```

Expected: BUILD SUCCESS，记录测试总数、Failures、Errors、Skipped。

- [ ] **Step 3: 运行两个前端完整 Gate**

```powershell
cd kasi-admin-web
pnpm check

cd ..\kasi-user-web
pnpm check
```

Expected: 两个命令均退出 0。

- [ ] **Step 4: 运行 MySQL Contract**

```powershell
cd kasi-backend
.\mvnw.cmd -Pmysql-contract-tests -Dtest='*MySqlContractIT' test
```

Expected: 有真实环境时 PASS；缺少允许的 MySQL 环境变量时明确 SKIP，不能报告 PASS。

- [ ] **Step 5: 最终范围检查**

```powershell
cd E:\JavaProjects\kasi-project
git diff --check
git status --short --branch
```

Expected: `git diff --check` 退出 0；只包含模块三实现、批准的设计/计划和必要 current docs，不提交、不推送。
