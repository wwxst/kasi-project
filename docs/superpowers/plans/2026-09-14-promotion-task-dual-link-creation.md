# Promotion Task Dual-Link Creation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make one promotion-task request generate LANDING and ONELINK for every selected media platform, and present both URLs in one compact user-table column.

**Architecture:** Remove the single-variant choice from the public create request and let the backend persistence boundary expand each media platform into the fixed `LANDING`/`ONELINK` pair. Keep the existing variant-level persistence and batch response, while the user list continues consuming the already-implemented code-level projection.

**Tech Stack:** Java 25, Spring Boot, MyBatis, JUnit 5, React 19, TypeScript, TDesign React, Vitest.

**Execution boundary:** Work directly in the current checkout because it contains the reviewed uncommitted code-level projection. Do not commit, push, reset, stash, create a worktree, call real GoodShort, or connect to production/MySQL environments.

---

### Task 1: Lock the backend dual-variant create contract

**Files:**
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/controller/UserPromotionLinkControllerTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/PromotionLinkPersistenceServiceTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/PromotionLinkServiceTest.java`

- [ ] **Step 1: Change the HTTP contract test so `linkVariant` is absent**

Post a valid body containing `providerId`, `dramaId`, `mediaTypes`, `requestKey`, and `campaignName`. Capture the DTO passed to `promotionLinkService.createOrRetry` and assert that the request succeeds without a variant field. Remove the old invalid-`linkVariant` validation case because the property is leaving the request model.

- [ ] **Step 2: Add a failing persistence preparation test for the fixed cross-product**

Use one published drama and one active user, submit `mediaTypes=[TIKTOK,YOUTUBE]`, and stub both variant lookups. Assert the preparation keys are exactly:

```text
TIKTOK/LANDING
TIKTOK/ONELINK
YOUTUBE/LANDING
YOUTUBE/ONELINK
```

Also assert all four rows share the request key, batch number, and campaign name while each retains its own tracking number.

- [ ] **Step 3: Change idempotency tests to validate the complete pair**

Construct an existing batch containing both variants for every requested media platform. Assert that a repeated identical request is accepted, a changed provider/drama/media set is rejected with `PROMOTION_LINK_REQUEST_CONFLICT`, and an incomplete or foreign variant set is rejected rather than silently treated as the same batch.

- [ ] **Step 4: Add a service test proving partial failure controls `complete`**

Return two preparations for one media platform, make one adapter call succeed and the other throw the existing provider exception, and assert the response contains both variants with `complete=false`.

- [ ] **Step 5: Run the focused tests and record RED**

Run from `kasi-backend`:

```powershell
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd "-Dtest=UserPromotionLinkControllerTest,PromotionLinkPersistenceServiceTest,PromotionLinkServiceTest" test
```

Expected RED: preparation currently returns one item per media and the DTO/service tests still require or expose the single `linkVariant` behavior.

### Task 2: Implement backend fixed dual-variant generation

**Files:**
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/dto/CreatePromotionLinkDTO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/PromotionLinkPersistenceServiceImpl.java`

- [ ] **Step 1: Remove `linkVariant` from `CreatePromotionLinkDTO`**

Keep all existing validation on provider, drama, media types, request key, and campaign name. Do not add a replacement request property because both variants are mandatory.

- [ ] **Step 2: Expand each media platform into two preparations**

Use the fixed local list:

```java
List<String> linkVariants = List.of("LANDING", "ONELINK");
```

Inside the existing media loop, iterate this list and apply the current lookup, success reuse, pending insert/reset, and `PromotionLinkRequest` construction once per pair. Preserve the existing GoodShort runtime lookup and transaction boundary.

- [ ] **Step 3: Validate an existing request key against the complete cross-product**

Build expected identity keys from requested media and both fixed variants, build actual keys from the existing batch, and require exact equality together with the existing provider/drama checks. Use a local string key such as `mediaType + "/" + linkVariant`; do not introduce a new manager or generalized identity type.

- [ ] **Step 4: Run focused tests and record GREEN**

Run the Task 1 Maven command. Expected: all selected tests pass with zero failures and errors.

- [ ] **Step 5: Review checkpoint**

Inspect the backend diff and confirm no migration, order, commission, settlement, or GoodShort adapter file changed.

### Task 3: Simplify the user creation dialog

**Files:**
- Modify: `kasi-user-web/src/features/promotionLinks/types.ts`
- Modify: `kasi-user-web/src/pages/drama/DramaPage.test.tsx`
- Modify: `kasi-user-web/src/pages/drama/DramaPage.tsx`

- [ ] **Step 1: Change the dialog test and record RED**

Assert that opening the promotion dialog shows `媒体平台` and `推广名称`, does not show `链接类型`, submits one request without `linkVariant`, and uses the labels `创建推广任务` and `生成推广任务`. Keep the existing tests proving request failure and `complete=false` do not navigate or show success.

Run from `kasi-user-web`:

```powershell
pnpm exec vitest run src/pages/drama/DramaPage.test.tsx
```

Expected RED: the current dialog still renders and submits `linkVariant`.

- [ ] **Step 2: Remove the frontend create-request variant**

Delete `linkVariant` from `CreatePromotionLinksInput`. Keep `LinkVariant` on `PromotionLinkVariant` because the batch response still identifies each generated result.

- [ ] **Step 3: Apply the minimal dialog implementation**

Remove `linkVariant` from the submitted form value type and API input, delete the link-type `FormItem`, rename the dialog header to `创建推广任务`, and rename the confirm button to `生成推广任务`. Do not change media selection, campaign length, loading, cache invalidation, navigation, or error handling.

- [ ] **Step 4: Run the focused dialog test and record GREEN**

Run the Step 1 Vitest command. Expected: the dialog test file passes with zero failures.

### Task 4: Compact the user task list links

**Files:**
- Modify: `kasi-user-web/src/pages/promotionLinks/PromotionLinksPage.test.tsx`
- Modify: `kasi-user-web/src/pages/promotionLinks/PromotionLinksPage.tsx`
- Modify: `kasi-user-web/src/pages/promotionLinks/PromotionLinksPage.module.less`

- [ ] **Step 1: Change the list test and record RED**

Replace assertions for separate `落地页` and `OneLink` table headers with one `推广链接` header. Assert the row contains two named anchors, each has the correct `href`, each has an accessible copy button, and an absent historical variant renders `暂无`. Keep the existing conflict and seven-metric assertions.

Run from `kasi-user-web`:

```powershell
pnpm exec vitest run src/pages/promotionLinks/PromotionLinksPage.test.tsx
```

Expected RED: the current table still has two URL columns and exposes raw URL text as anchor names.

- [ ] **Step 2: Replace the two URL columns with one**

Add one `推广链接` column whose cell renders two stable rows labeled `落地页` and `OneLink`. Each row uses the full URL as the anchor text and keeps it in `href` and `title`, with CSS ellipsis for long display text and the existing square copy-icon button beside it. A missing URL keeps its row label and renders `暂无` without an active copy button.

- [ ] **Step 3: Add narrowly scoped styles**

Add fixed row spacing and stable inline dimensions for the two link rows. Keep the existing table container and horizontal scrolling. Add a narrow-screen media query that reduces page padding without changing font scale or allowing controls to overlap.

- [ ] **Step 4: Reduce empty-name noise**

Change an empty campaign name from `未填写` to the existing em-dash placeholder. Keep all backend values unchanged.

- [ ] **Step 5: Run both affected user tests**

```powershell
pnpm exec vitest run src/pages/drama/DramaPage.test.tsx src/pages/promotionLinks/PromotionLinksPage.test.tsx
```

Expected: both test files pass with zero failures.

### Task 5: Synchronize current documentation

**Files:**
- Modify: `kasi-backend/README.md`
- Modify: `kasi-backend/AGENTS.md`
- Modify: `kasi-user-web/README.md`
- Modify: `kasi-user-web/AGENTS.md`
- Modify: `docs/architecture/current.md`

- [ ] **Step 1: Update the create contract**

Replace current statements that users choose LANDING or ONELINK with the verified rule: each selected media platform creates both variants under one request/batch, and only a complete batch causes frontend navigation.

- [ ] **Step 2: Update the user list presentation**

Document that one code-level row contains a compact two-link display plus one conversion set. Preserve the existing conflict rule and the absence of order amount, state, retry, and operation columns.

- [ ] **Step 3: Keep historical documents separate**

Do not edit the existing closure plan checkboxes or the older final-design document. The confirmed design spec and current docs describe the new behavior.

### Task 6: Full verification and visual review

- [ ] **Step 1: Run the backend canonical Gate under Java 25**

```powershell
cd kasi-backend
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd verify
```

Expected: `BUILD SUCCESS`; report test, failure, error, and skip counts.

- [ ] **Step 2: Run both frontend canonical Gates**

```powershell
cd ..\kasi-user-web
pnpm check
cd ..\kasi-admin-web
pnpm check
```

Expected: both commands exit 0. The admin Gate guards the already-reviewed dirty admin files even though this stage does not edit them.

- [ ] **Step 3: Run root diff and scope checks**

```powershell
cd ..
git diff --check
git status --short --branch
git diff --name-only
```

Check the two new untracked Markdown/Java files separately for trailing whitespace. Confirm no migration, order, commission, settlement, or GoodShort adapter file was added to the diff.

- [ ] **Step 4: Run browser visual verification**

Start the user development server on an available local port. Inspect the creation dialog and promotion-task list at a desktop viewport and at 320px or wider mobile viewport. Confirm the dialog has no link-type field, the two-link cell is readable, the table scrolls without overlap, and the page has no console error.

- [ ] **Step 5: Report external skips and stop at Review**

Report real MySQL and GoodShort as `SKIP` unless their explicit test environment and authorization are present. Do not commit or push; leave all changes for user review.
