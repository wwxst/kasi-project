# GoodShort Endpoint Normalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every GoodShort request use a root-domain `baseUrl` plus a complete `/creek/open/...` endpoint path.

**Architecture:** Keep URL ownership split at the existing boundary: provider connection storage owns only the normalized base URL, while `GoodShortAdapter` owns every provider-specific endpoint. Do not add compatibility rewriting for saved URLs that already include `/creek`.

**Tech Stack:** Java 25, Spring Boot RestClient, JUnit 5, AssertJ, Mockito, React 19, TypeScript, Ant Design, Vitest

---

### Task 1: Normalize GoodShort endpoint paths

**Files:**
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java`
- Test: `kasi-backend/src/test/java/com/kasi/backend/provider/goodshort/GoodShortAdapterTest.java`
- Test: `kasi-backend/src/test/java/com/kasi/backend/provider/goodshort/GoodShortCatalogAdapterTest.java`
- Test: `kasi-backend/src/test/java/com/kasi/backend/provider/goodshort/GoodShortPromotionLinkAdapterTest.java`
- Test: `kasi-backend/src/test/java/com/kasi/backend/provider/goodshort/GoodShortOrderAdapterTest.java`
- Test: `kasi-backend/src/test/java/com/kasi/backend/provider/goodshort/GoodShortFilingAdapterTest.java`
- Test: `kasi-backend/src/test/java/com/kasi/backend/provider/goodshort/GoodShortAnalyticalReportAdapterTest.java`

- [x] **Step 1: Change request expectations to complete `/creek/open/...` paths**

Update every `requestTo("https://goodshort.test/open/..." )` expectation to `requestTo("https://goodshort.test/creek/open/..." )`. Keep the existing analytical-report expectation unchanged because it already uses the complete path.

- [x] **Step 2: Run the adapter tests and verify RED**

Run:

```powershell
cd kasi-backend
.\mvnw.cmd --% -Dtest=GoodShortAdapterTest,GoodShortCatalogAdapterTest,GoodShortPromotionLinkAdapterTest,GoodShortOrderAdapterTest,GoodShortFilingAdapterTest,GoodShortAnalyticalReportAdapterTest test
```

Expected: FAIL because current requests omit `/creek` for connection probing, catalog, free content, promotion links, orders, and filing.

- [x] **Step 3: Give all endpoints complete provider paths**

Change the `GoodShortAdapter` endpoint constants to:

```java
private static final String CONNECTION_PROBE_PATH = "/creek/open/book/initBooks";
private static final String FILING_REPORT_PATH = "/creek/open/filing/report";
private static final String FILING_QUERY_PATH = "/creek/open/filing/query";
private static final String FULL_CATALOG_PATH = "/creek/open/book/initBooks";
private static final String INCREMENTAL_CATALOG_PATH = "/creek/open/book/incrementBooks";
private static final String ORDER_PATH = "/creek/open/partner/orders";
private static final String FREE_CONTENT_PATH = "/creek/open/book/freeContent";
private static final String PROMOTION_LINK_PATH = "/creek/open/inviteCode/generate/partner/code";
private static final String ANALYTICAL_REPORT_PATH = "/creek/open/promotion/analyticalReport";
```

Use `PROMOTION_LINK_PATH` from `generatePromotionLink` instead of an inline string.

- [x] **Step 4: Re-run the adapter tests and verify GREEN**

Run the command from Step 2. Expected: all selected tests pass with zero failures and zero errors.

### Task 2: Normalize stored base URLs

**Files:**
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/service/impl/ProviderConnectionServiceImpl.java`
- Test: `kasi-backend/src/test/java/com/kasi/backend/provider/service/ProviderConnectionServiceTest.java`

- [x] **Step 1: Add a failing trailing-slash assertion**

Make the service test submit `https://api.goodshort.test/` and assert that the inserted connection stores `https://api.goodshort.test`. Update root-domain fixtures used by the same test class from `https://api.goodshort.test/creek` to `https://api.goodshort.test`.

- [x] **Step 2: Run the service test and verify RED**

Run:

```powershell
cd kasi-backend
.\mvnw.cmd -Dtest=ProviderConnectionServiceTest test
```

Expected: FAIL because the inserted connection still contains the trailing slash.

- [x] **Step 3: Implement minimal base URL normalization**

Add a private `normalizeBaseUrl` method that calls `trimToNull`, then removes trailing `/` characters. Use it for validation and when building the persisted connection. Do not append or remove `/creek`.

- [x] **Step 4: Re-run the service test and verify GREEN**

Run the command from Step 2. Expected: all selected tests pass with zero failures and zero errors.

### Task 3: Align the management UI and verify

**Files:**
- Modify: `kasi-admin-web/src/pages/provider/ProviderManagementPage.tsx`
- Modify: `kasi-backend/README.md`

- [x] **Step 1: Update the base URL placeholder and current endpoint documentation**

Change the example from `https://api.novelopen.com/creek` to `https://api.novelopen.com`.
Update the backend current README to show complete `/creek/open/...` endpoint paths.

- [x] **Step 2: Run canonical project gates**

Run:

```powershell
cd kasi-backend
.\mvnw.cmd verify

cd ..\kasi-admin-web
pnpm check

cd ..
git diff --check
```

Expected: every command exits 0. Report MySQL or GoodShort real-environment checks only as `SKIP` when their required configuration is absent.

## Execution Results

- GoodShort adapter tests: 20 run, 0 failures, 0 errors, 0 skipped.
- Provider connection service tests: 14 run, 0 failures, 0 errors, 0 skipped.
- Admin canonical Gate: 18 test files and 92 tests passed; lint, formatting, and production build passed.
- Backend canonical Gate: 415 run, 0 failures, 0 errors, 1 skipped after correcting the two pre-existing media-account test defects exposed by the first run.
- Root `git diff --check`: exit 0; line-ending conversion warnings only.
