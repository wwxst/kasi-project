# Promotion Stability Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Remove long-lived Redis mutation locks, keep GoodShort HTTP outside database transactions, tolerate concurrent duplicate orders, and replace the static Dashboard demo with a real welcome surface.

**Architecture:** Keep the existing `SessionService` transaction-completion boundary. Split promotion-link persistence into short transactional methods around an external HTTP call, and catch only the order insert unique-key race. Keep `/dashboard` as a shell but route all navigation entry points to `/user-management`.

**Tech Stack:** Java 25, Spring Boot, MyBatis, Redis Lua sessions, JUnit 5/Mockito, React 19, React Router, Ant Design, Vitest, MSW.

---

### Task 1: Verify and lock down session mutation recovery

**Files:**
- Modify: `src/test/java/com/kasi/backend/security/service/SessionServiceTest.java`
- Inspect: `src/main/java/com/kasi/backend/security/service/impl/SessionServiceImpl.java`
- Inspect: `src/main/java/com/kasi/backend/{admin,user}/service/impl/*ServiceImpl.java`

- [ ] **Step 1: Run the existing rollback regression first**

Run `./mvnw.cmd "-Dtest=SessionServiceTest,AdminManagementServiceTest,UserManagementServiceTest,AdminAuthServiceTest" test` with Java 25. Confirm the current shared `afterCompletion` implementation passes and record the count.

- [ ] **Step 2: Audit every sensitive caller**

Run `rg -n "beginMutation|registerMutationCompletion|completeMutation|afterCommit" src/main/java`. Every mutation caller must register through `SessionService.registerMutationCompletion`; no business service may directly register an `afterCommit` callback for session recovery.

- [ ] **Step 3: Add/retain rollback assertions**

Keep the test that invokes `afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)` and asserts a new session can be created and validated. Add a focused assertion for the administrator subject if an administrator caller lacks coverage.

- [ ] **Step 4: Run the focused session suite**

Run `./mvnw.cmd "-Dtest=SessionServiceTest,SessionAuthenticationTest,AdminManagementServiceTest,UserManagementServiceTest,AdminAuthServiceTest" test`. Expected: zero failures and zero errors.

### Task 2: Move PromotionLink HTTP outside the database transaction

**Files:**
- Create: `src/main/java/com/kasi/backend/promotion/service/PromotionLinkPersistenceService.java`
- Create: `src/main/java/com/kasi/backend/promotion/service/PromotionLinkPreparation.java`
- Create: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionLinkPersistenceServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionLinkServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/promotion/mapper/PromotionLinkMapper.java`
- Modify: `src/main/resources/mapper/PromotionLinkMapper.xml`
- Test: `src/test/java/com/kasi/backend/promotion/service/PromotionLinkServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Add orchestration tests with mocked `PromotionLinkPersistenceService` and `PromotionLinkProviderAdapter`. Use Mockito `InOrder` to assert success calls `preparePending -> adapter.generatePromotionLink -> markSuccess`; failure calls `preparePending -> adapter throws -> markFailed`, and only then propagates `BusinessException`.

- [ ] **Step 2: Run the tests and confirm the current transaction-bound behavior is exposed**

Run `./mvnw.cmd "-Dtest=PromotionLinkServiceTest" test`. The new failure-state test must fail because the current outer transaction rolls back `markFailed` when the service throws.

- [ ] **Step 3: Implement short transaction boundaries**

Remove the outer `@Transactional` from `createOrRetry`. Add `PromotionLinkPersistenceService` and `PromotionLinkPersistenceServiceImpl`; all database phases must go through this separate Spring Bean and never through same-bean invocation. `preparePending`, `markSuccess`, and `markFailed` use `@Transactional(propagation = Propagation.REQUIRES_NEW)`. The pending phase performs existing validation and create/reset work, returning `PromotionLinkPreparation` with the data needed for the provider call. `PromotionLinkServiceImpl` performs `preparePending -> GoodShort HTTP -> markSuccess/markFailed` with no transaction. `markFailed` must return successfully before the orchestrator throws `BusinessException`; the persistence Bean must never call the GoodShort adapter.

- [ ] **Step 4: Preserve requestKey concurrency behavior**

Keep the unique `(user_id, request_key)` database constraint. If concurrent creation causes `DuplicateKeyException` in the pending phase, re-read the row and return it when it is already `SUCCESS`; otherwise use the existing `PENDING` row and continue without creating another link. Do not add retries around the remote HTTP call.

- [ ] **Step 5: Run the promotion-link and adapter tests**

Run `./mvnw.cmd "-Dtest=PromotionLinkServiceTest,GoodShortPromotionLinkAdapterTest" test`. Expected: zero failures and zero errors.

### Task 3: Add the minimum duplicate-order race guard

**Files:**
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderServiceImpl.java`
- Test: `src/test/java/com/kasi/backend/promotion/service/PromotionOrderServiceTest.java`

- [ ] **Step 1: Write the failing duplicate-key test**

Configure `orderMapper.findBySourceForUpdate` to return `null`, make `orderMapper.insert` throw `DuplicateKeyException`, and make a subsequent source lookup return an existing `PromotionOrder`. Assert `upsert` returns `created=false` and does not propagate the exception.

- [ ] **Step 2: Run the test and confirm it fails**

Run `./mvnw.cmd "-Dtest=PromotionOrderServiceTest" test`. Expected: the new test fails with `DuplicateKeyException` from `insert`.

- [ ] **Step 3: Catch only the unique-key race**

Wrap only `orderMapper.insert(order)` in `try/catch (DuplicateKeyException)`. On catch, call `findBySource(connectionId, externalOrderId)`; if found, return `new PromotionOrderUpsertResult(false, existing.getAttributionStatus() == ATTRIBUTED)`. Re-throw if the row still cannot be read, preserving unrelated database failures.

- [ ] **Step 4: Run order-sync regression tests**

Run `./mvnw.cmd "-Dtest=PromotionOrderServiceTest,PromotionOrderAdminServiceTest,PromotionOrderControllerTest" test`. Expected: zero failures and zero errors.

### Task 4: Replace static Dashboard demo content

**Files:**
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/src/pages/dashboard/DashboardPage.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/src/layouts/AdminLayout.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/src/router/AppRouter.tsx`
- Test: `E:/JavaProjects/kasi-project/kasi-admin-web/src/pages/dashboard/DashboardPage.test.tsx`
- Test: `E:/JavaProjects/kasi-project/kasi-admin-web/src/layouts/AdminLayout.test.tsx`

- [ ] **Step 1: Write failing Dashboard tests**

Assert `DashboardPage` renders the current admin real name in the welcome text and does not render demo card headings or chart containers. Assert the layout has no `/dashboard` menu item, brand link points to `/user-management`, and search Enter navigates to `/user-management`. The existing `App.test.tsx` covers restricted and wildcard redirects to `/user-management`.

- [ ] **Step 2: Run the frontend tests and confirm the failures**

From `E:/JavaProjects/kasi-project/kasi-admin-web`, run `pnpm vitest run src/pages/dashboard/DashboardPage.test.tsx src/layouts/AdminLayout.test.tsx`. Expected: failures against the existing demo cards and `/dashboard` navigation.

- [ ] **Step 3: Implement the welcome-only Dashboard**

Replace the Dashboard children with one semantic welcome element centered in the existing page shell. Use the authenticated admin store real name, with a neutral fallback such as `管理员`, and keep the text large but responsive. Remove imports of static card components from `DashboardPage.tsx`.

- [ ] **Step 4: Update navigation targets**

Remove the Dashboard menu item and `Gauge` import from `AdminLayout.tsx`; change brand link and search Enter destination to `/user-management`. Keep the Dashboard lazy import and `/dashboard` route so the welcome-only page remains directly reachable. Change `SuperAdminRoute` denial, wildcard route, and any remaining default redirects to `/user-management`; `/dashboard` must have no navigation entry.

- [ ] **Step 5: Run frontend verification**

Run `pnpm vitest run src/pages/dashboard/DashboardPage.test.tsx src/layouts/AdminLayout.test.tsx src/App.test.tsx` and `pnpm build` from `E:/JavaProjects/kasi-project/kasi-admin-web`. Expected: zero test failures and a successful production build.

### Task 5: Documentation and final verification

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: Document current behavior**

Update the authentication section to state rollback-safe `MUTATING` recovery, the promotion-link short transaction/HTTP boundary, duplicate-order unique-key handling, and Dashboard welcome-only behavior. Keep future real analytics explicitly in the not-implemented section.

- [ ] **Step 2: Run focused backend and frontend suites**

Run the Task 1, Task 2, Task 3 Maven commands and the Task 4 Vitest/build commands. Capture exact counts and any timeout separately.

- [ ] **Step 3: Run compile and diff checks**

Run `./mvnw.cmd -DskipTests compile`, `git diff --check`, and `git status --short`. Do not stage or revert unrelated existing changes.

- [ ] **Step 4: Commit only intended implementation files**

Review `git diff --stat` and stage the backend, frontend, test, and documentation files changed by these four tasks. Preserve the previously existing uncommitted session-fix files unless they are intentionally included in the same implementation commit.
