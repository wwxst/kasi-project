# 推广平台账号报白方式配置 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为每个推广平台增加“API 自动报白/人工报白”单选配置，并让管理员在人工模式下直接把账号报白状态改为“已加白”或“已失败”。

**Architecture:** 报白方式数据归属平台接入账号 `short_drama_connection`，但管理员入口放在“推广管理 / 账号配置”。平台 API 凭据接口保持不变，新增独立的报白方式接口避免系统配置页面覆盖该字段。创建媒体账号时根据平台模式决定是否排入 API 任务；人工状态更新复用 `provider_media_filing`，只写状态、操作人和操作时间，不保存人工失败原因。

**Tech Stack:** Spring Boot、MyBatis、Flyway、H2 MySQL 模式、Java 25、React 19、Ant Design、Ant Design Pro Components、Vitest、Testing Library。

---

### Task 1: 增加平台报白方式持久化和管理接口

**Files:**
- Create: `src/main/resources/db/migration/V5__provider_filing_mode.sql`
- Modify: `src/test/resources/test-schema.sql`
- Modify: `src/main/java/com/kasi/backend/provider/entity/ShortDramaConnection.java`
- Create: `src/main/java/com/kasi/backend/provider/enums/FilingMode.java`
- Create: `src/main/java/com/kasi/backend/provider/dto/UpdateProviderFilingModeDTO.java`
- Create: `src/main/java/com/kasi/backend/provider/vo/ProviderFilingModeVO.java`
- Modify: `src/main/java/com/kasi/backend/provider/mapper/ShortDramaConnectionMapper.java`
- Modify: `src/main/resources/mapper/ShortDramaConnectionMapper.xml`
- Modify: `src/main/java/com/kasi/backend/provider/service/ProviderConnectionService.java`
- Modify: `src/main/java/com/kasi/backend/provider/service/impl/ProviderConnectionServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/provider/controller/ProviderAdminController.java`
- Modify: `src/main/java/com/kasi/backend/security/config/SecurityConfig.java`
- Test: `src/test/java/com/kasi/backend/provider/controller/ProviderAdminControllerTest.java`
- Test: `src/test/java/com/kasi/backend/provider/service/ProviderConnectionServiceTest.java`
- Test: `src/test/java/com/kasi/backend/MediaAccountFilingMigrationTest.java`

- [ ] **Step 1: Write the failing migration and service tests**

  Add assertions that `short_drama_connection.filing_mode` defaults to `API`, existing provider responses expose `API`, a super administrator can update it to `MANUAL`, and a normal administrator receives 403. Add a migration assertion that the H2 schema contains `filing_mode`.

- [ ] **Step 2: Run the focused tests and verify they fail**

  Run from the backend root:

  ```powershell
  $env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'; $env:Path="$env:JAVA_HOME\bin;$env:Path"
  .\mvnw.cmd -Dtest=ProviderAdminControllerTest,ProviderConnectionServiceTest,MediaAccountFilingMigrationTest test
  ```

  Expected failure: `filing_mode` is absent and the filing-mode endpoint/types do not exist.

- [ ] **Step 3: Add the schema and domain types**

  `V5__provider_filing_mode.sql` must contain:

  ```sql
  ALTER TABLE `short_drama_connection`
      ADD COLUMN `filing_mode` VARCHAR(16) NOT NULL DEFAULT 'API'
          COMMENT '账号报白方式：API自动报白或MANUAL人工报白';
  ```

  Mirror the column in `test-schema.sql`. Add `FilingMode { API, MANUAL }`, `filingMode` to `ShortDramaConnection`, a validated DTO with `@NotNull FilingMode filingMode`, and a response VO containing `providerId`, `providerName`, and `filingMode`.

- [ ] **Step 4: Extend mapper and service without changing API credential upsert**

  Add mapper method `updateFilingMode(Long connectionId, FilingMode filingMode)`. Extend the provider query projection/result mapping to read `filing_mode`. Add service methods:

  ```java
  ProviderFilingModeVO getFilingMode(Long providerId);
  ProviderFilingModeVO updateFilingMode(Long operatorId, Long providerId, UpdateProviderFilingModeDTO request);
  ```

  Keep the existing `/connection` URL/PID/KEY DTO unchanged. Stopping pending tasks when switching to `MANUAL` is implemented in Task 2 together with the filing mapper, so this task remains focused on storing and exposing the platform mode.

- [ ] **Step 5: Expose secured controller endpoints**

  Add:

  ```text
  GET /api/admin/drama/providers/{providerId}/filing-mode
  PUT /api/admin/drama/providers/{providerId}/filing-mode
  Body: { "filingMode": "API" | "MANUAL" }
  ```

  Restrict the PUT route to `ROLE_SUPER_ADMIN`; allow both admin roles to read it. Use `@Valid` and return `ApiResponse<ProviderFilingModeVO>`. Add the route rule to `SecurityConfig`.

- [ ] **Step 6: Run focused tests and commit the backend configuration slice**

  ```powershell
  .\mvnw.cmd -Dtest=ProviderAdminControllerTest,ProviderConnectionServiceTest,MediaAccountFilingMigrationTest test
  git diff --check
  git add src/main/resources/db/migration/V5__provider_filing_mode.sql src/test/resources/test-schema.sql src/main/java/com/kasi/backend/provider src/main/java/com/kasi/backend/security/config/SecurityConfig.java src/test/java/com/kasi/backend/provider src/test/java/com/kasi/backend/MediaAccountFilingMigrationTest.java
  git commit -m "feat: configure provider account filing mode"
  ```

  Expected result: focused tests pass and only the files listed for this task are committed.

### Task 2: Implement manual filing status operations and mode-aware task creation

**Files:**
- Modify: `src/main/java/com/kasi/backend/promotion/entity/ProviderMediaFiling.java`
- Create: `src/main/resources/db/migration/V6__manual_filing_operator.sql`
- Modify: `src/main/java/com/kasi/backend/provider/service/impl/ProviderConnectionServiceImpl.java`
- Modify: `src/test/resources/test-schema.sql`
- Create: `src/main/java/com/kasi/backend/promotion/dto/UpdateMediaFilingStatusDTO.java`
- Modify: `src/main/java/com/kasi/backend/promotion/mapper/ProviderMediaFilingMapper.java`
- Modify: `src/main/resources/mapper/ProviderMediaFilingMapper.xml`
- Modify: `src/main/java/com/kasi/backend/promotion/service/MediaAccountService.java`
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/promotion/service/MediaAccountAdminService.java`
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountAdminServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/promotion/controller/AdminMediaAccountController.java`
- Modify: `src/main/java/com/kasi/backend/promotion/vo/MediaFilingVO.java`
- Test: `src/test/java/com/kasi/backend/promotion/service/MediaAccountServiceTest.java`
- Test: `src/test/java/com/kasi/backend/promotion/controller/AdminMediaAccountControllerTest.java`
- Test: `src/test/java/com/kasi/backend/promotion/mapper/MediaAccountFilingPersistenceTest.java`

- [ ] **Step 1: Write failing tests for API/manual creation and manual status updates**

  Cover these exact cases:

  ```java
  // MANUAL connection: filing is PENDING but nextAction is NONE
  // API connection: filing is PENDING and nextAction is SUBMIT
  // APPROVED/FAILED update records operator and operateTime
  // FAILED update accepts no reason field and clears no user-entered reason
  // APPROVED or FAILED cannot be changed a second time
  ```

  Add controller tests for `PATCH /api/admin/promotion/media-accounts/{id}/filings/{providerId}/status`, including valid `APPROVED`, valid `FAILED`, missing/invalid status, unauthorized request, and a normal admin request.

- [ ] **Step 2: Run the focused tests and verify they fail**

  ```powershell
  .\mvnw.cmd -Dtest=MediaAccountServiceTest,AdminMediaAccountControllerTest,MediaAccountFilingPersistenceTest test
  ```

  Expected failure: no manual mode branch, operator column, or status endpoint exists.

- [ ] **Step 3: Add operator tracking and mapper operations**

  Add V6 and the H2 test schema column with:

  ```sql
  ALTER TABLE `provider_media_filing`
      ADD COLUMN `operate_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '人工处理管理员ID';
  ```

  Add `operateBy` to the entity/VO result mapping. Add a mapper update that atomically checks `status = 'PENDING'`, sets `status`, `next_action = 'NONE'`, `next_action_at = NULL`, `operate_by`, `operate_time = CURRENT_TIMESTAMP`, clears lease fields, and updates the timestamp. Return affected-row count so repeated updates fail with a business error. Add `stopPendingTasksByConnectionId` to `ProviderMediaFilingMapper` and invoke it from `ProviderConnectionServiceImpl` only when the mode transitions to `MANUAL`; preserve task history and do not resubmit rows when switching back to `API`.

- [ ] **Step 4: Make creation and rescheduling honor `FilingMode`**

  When creating a filing, read the connection mode. For `API`, preserve the current `PENDING + SUBMIT` behavior. For `MANUAL`, create `PENDING + NONE` and do not enqueue a task. When a user edits account identity/details, preserve `NONE` for manual-mode connections; do not re-enqueue API work. User retry in manual mode must return a business error instead of calling the provider adapter.

- [ ] **Step 5: Add the administrator status service and endpoint**

  Add `updateFilingStatus(Long operatorId, Long mediaAccountId, Long providerId, UpdateMediaFilingStatusDTO request)` to the admin service. Resolve the provider connection, verify the filing belongs to the media account, verify the requested status is only `APPROVED` or `FAILED`, and update it transactionally. Do not accept a failure-reason field. Return the refreshed `MediaFilingVO`.

  Endpoint:

  ```text
  PATCH /api/admin/promotion/media-accounts/{id}/filings/{providerId}/status
  Body: { "status": "APPROVED" | "FAILED" }
  ```

  Use `AuthContextHolder.getAdminId()` and retain existing admin-role protection for the controller.

- [ ] **Step 6: Run promotion tests and commit the workflow slice**

  ```powershell
  .\mvnw.cmd -Dtest=MediaAccountServiceTest,AdminMediaAccountControllerTest,MediaAccountFilingPersistenceTest,MediaFilingTaskServiceTest test
  git diff --check
  git add src/main/resources/db/migration/V6__manual_filing_operator.sql src/test/resources/test-schema.sql src/main/java/com/kasi/backend/provider/service/impl/ProviderConnectionServiceImpl.java src/main/java/com/kasi/backend/promotion src/main/resources/mapper/ProviderMediaFilingMapper.xml src/test/java/com/kasi/backend/promotion
  git commit -m "feat: support manual media account filing"
  ```

### Task 3: Add the administrator account configuration page

**Files:**
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/features/promotion/filingModeTypes.ts`
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/features/promotion/filingModeApi.ts`
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/features/promotion/filingModeApi.test.ts`
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/pages/promotion/AccountFilingConfigPage.tsx`
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/pages/promotion/account-filing-config-page.css`
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/pages/promotion/AccountFilingConfigPage.test.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/src/router/AppRouter.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/src/layouts/AdminLayout.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/src/features/provider/providerTypes.ts`

- [ ] **Step 1: Write failing API and page tests**

  Assert that the API wrapper calls the two filing-mode endpoints, trims no values, and sends exactly `{ filingMode: 'API' | 'MANUAL' }`. Render the page with GoodShort data and assert:

  ```text
  getByText('账号配置')
  getByLabelText('API 自动报白')
  getByLabelText('人工报白')
  ```

  Assert the radios are mutually exclusive, the current value is selected, Save calls PUT once, and a non-super-admin sees the radios disabled with an informational message.

- [ ] **Step 2: Run the frontend tests and verify they fail**

  From `E:/JavaProjects/kasi-project/kasi-admin-web` run:

  ```powershell
  pnpm test -- src/features/promotion/filingModeApi.test.ts src/pages/promotion/AccountFilingConfigPage.test.tsx
  ```

  Expected failure: the API module, route, and page do not exist.

- [ ] **Step 3: Implement the API types and wrapper**

  Define:

  ```ts
  export type FilingMode = 'API' | 'MANUAL'
  export interface ProviderFilingMode {
    providerId: number
    providerName: string
    filingMode: FilingMode
  }
  ```

  Implement `listProviderFilingModes()` and `updateProviderFilingMode(providerId, filingMode)` using `/api/admin/drama/providers/{providerId}/filing-mode` and the existing `unwrapApiResponse` helper.

- [ ] **Step 4: Implement the page using Ant Design radio groups**

  Build a `PageContainer` with one vertical `Form.Item` per provider. Each row contains the provider name and a single `Radio.Group` with `API 自动报白` and `人工报白`, plus a short helper description. Use `Radio.Group` rather than `Switch`; do not add a failure-reason field. Disable edits for non-super-admin users, keep the current value visible, show a single Save button, and reload values after a successful save.

  Keep CSS aligned with the existing provider management form: restrained spacing, horizontal label/value alignment on desktop, one-column layout under 760px, no nested cards.

- [ ] **Step 5: Register the route and menu item**

  Add lazy route `/promotion/account-config` and add it under the existing `promotion-management` menu group with label `账号配置`. Keep `/system-config/drama-api` unchanged for URL/PID/KEY configuration. Ensure selected menu state works for both desktop sider and mobile drawer.

- [ ] **Step 6: Run frontend verification**

  ```powershell
  pnpm test -- src/features/promotion/filingModeApi.test.ts src/pages/promotion/AccountFilingConfigPage.test.tsx
  pnpm typecheck
  pnpm lint
  pnpm format:check
  pnpm build
  ```

  Expected result: all commands exit 0; the account configuration page renders the single-select layout shown in the approved reference.

### Task 4: Synchronize documentation and run full verification

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md` only if the current API/menu contract needs a durable rule
- Modify: `docs/superpowers/specs/2026-08-19-promotion-account-filing-mode-design.md` if implementation details differ from the approved design
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/docs/superpowers/plans/2026-08-19-promotion-account-filing-mode.md` only if the frontend repository later gains its own documentation root

- [ ] **Step 1: Update current-state documentation**

  Document the V5/V6 migrations, `filing_mode`, manual operator tracking, manual status endpoint, three filing statuses, and the menu split between promotion account configuration and system API credentials. Do not describe future providers as implemented.

- [ ] **Step 2: Run backend full verification**

  ```powershell
  $env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'; $env:Path="$env:JAVA_HOME\bin;$env:Path"
  .\mvnw.cmd test
  git diff --check
  ```

  Expected result: Maven reports zero test failures/errors and `git diff --check` is clean.

- [ ] **Step 3: Re-run frontend verification and inspect the page**

  Run the Task 3 commands again, start the existing Vite dev server on an available port, and verify `/promotion/account-config` in a desktop viewport and a narrow viewport. Confirm the two radio options never overlap and the menu item is under “推广管理”.

- [ ] **Step 4: Review the final diff and commit documentation**

  ```powershell
  git status --short
  git diff --stat
  git add README.md docs/superpowers/plans/2026-08-19-promotion-account-filing-mode.md
  git commit -m "docs: document provider filing mode configuration"
  ```

  Stage only the files changed for this feature; leave unrelated existing worktree changes untouched.

## Self-review checklist

- Spec coverage: single-select UI, platform isolation, API/manual flows, no failure reason, operator/time audit, permissions, task stopping, tests, and menu/API separation are covered by Tasks 1–4.
- Placeholder scan: no `TBD`, `TODO`, or unspecified “add appropriate” steps remain.
- Type consistency: backend uses `FilingMode` and `ProviderFilingModeVO`; frontend uses the matching string union and `{ filingMode }` request; manual status uses `FilingStatus.APPROVED`/`FAILED` consistently.
- Scope: the plan does not add order, commission, withdrawal, or other promotion modules.
