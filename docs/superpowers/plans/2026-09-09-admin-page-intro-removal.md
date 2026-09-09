# Admin Page Intro Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove page-level introductory headings and descriptions from every authenticated admin page while retaining navigation and content-specific headings.

**Architecture:** Keep the existing page containers and business components. Remove only `PageContainer` header props and custom introductory nodes, then delete props and CSS rules that become unused; tests distinguish removed page introductions from retained table, section, drawer, modal, and login headings.

**Tech Stack:** React 19, TypeScript, Ant Design Pro Components, Vitest, React Testing Library, CSS

---

### Task 1: Define the absent-intro behavior

**Files:**
- Modify: `kasi-admin-web/src/App.test.tsx`
- Modify: `kasi-admin-web/src/pages/dashboard/DashboardPage.test.tsx`
- Modify: `kasi-admin-web/src/pages/drama/DramaSyncCenterPage.test.tsx`
- Modify: existing focused page tests that currently use the page heading as their render-ready assertion

- [x] **Step 1: Write failing assertions**

For representative routed and focused page tests, wait for a stable business control or table cell, then assert the former page intro is absent. Example:

```tsx
expect(
  screen.queryByText('管理推广用户资料和状态'),
).not.toBeInTheDocument()
expect(
  screen.queryByRole('heading', { name: '欢迎 平台负责人 使用卡司短剧推广平台' }),
).not.toBeInTheDocument()
expect(
  screen.queryByText('按一次目录同步触发聚合展示各语言任务。'),
).not.toBeInTheDocument()
```

Keep positive assertions for `ProTable` titles and business controls so removal cannot blank functional content accidentally.

- [x] **Step 2: Run focused tests and verify RED**

Run:

```powershell
cd kasi-admin-web
pnpm test -- src/App.test.tsx src/pages/dashboard/DashboardPage.test.tsx src/pages/drama/DramaSyncCenterPage.test.tsx
```

Expected: FAIL because the current page intro title or description is still rendered.

### Task 2: Remove `PageContainer` headers

**Files:**
- Modify: `kasi-admin-web/src/features/management/ManagementTablePage.tsx`
- Modify: `kasi-admin-web/src/pages/management/AdminManagementPage.tsx`
- Modify: `kasi-admin-web/src/pages/management/UserManagementPage.tsx`
- Modify: `kasi-admin-web/src/pages/provider/ProviderManagementPage.tsx`
- Modify: `kasi-admin-web/src/pages/drama/DramaCatalogPage.tsx`
- Modify: `kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.tsx`
- Modify: `kasi-admin-web/src/pages/promotion/PromotionLinkPage.tsx`
- Modify: `kasi-admin-web/src/pages/promotion/PromotionOrderPage.tsx`
- Modify: `kasi-admin-web/src/pages/promotion/PromotionProjectPage.tsx`
- Modify: `kasi-admin-web/src/pages/system/CommissionRulePage.tsx`
- Modify: `kasi-admin-web/src/pages/system/ScheduledTaskPage.tsx`
- Modify: `kasi-admin-web/src/pages/system/SmsConfigPage.tsx`

- [x] **Step 1: Remove header props without replacing the containers**

Change pages from:

```tsx
<PageContainer title="页面标题" content="页面说明" className="page-class">
```

to:

```tsx
<PageContainer className="page-class">
```

In `ManagementTablePage`, keep `title` for `ProTable.headerTitle` and action copy, remove the page-level `content={description}`, delete the unused `description` prop, and remove that prop from both management page callers.

- [x] **Step 2: Run affected tests**

Run:

```powershell
cd kasi-admin-web
pnpm test -- src/App.test.tsx src/pages/provider/ProviderManagementPage.test.tsx src/pages/drama/DramaCatalogPage.test.tsx src/pages/promotion/MediaAccountFilingPage.test.tsx src/pages/promotion/PromotionLinkPage.test.tsx src/pages/promotion/PromotionOrderPage.test.tsx src/pages/promotion/PromotionProjectPage.test.tsx src/pages/system/CommissionRulePage.test.tsx src/pages/system/ScheduledTaskPage.test.tsx
```

Expected: PASS with all page functions still rendered.

### Task 3: Remove custom page introductions

**Files:**
- Modify: `kasi-admin-web/src/pages/dashboard/DashboardPage.tsx`
- Modify: `kasi-admin-web/src/pages/dashboard/dashboard-page.css`
- Modify: `kasi-admin-web/src/pages/profile/ProfilePage.tsx`
- Modify: `kasi-admin-web/src/pages/profile/profile-page.css`
- Modify: `kasi-admin-web/src/pages/drama/DramaSyncCenterPage.tsx`
- Modify: `kasi-admin-web/src/pages/drama/drama-sync-center-page.css`

- [x] **Step 1: Delete only introductory nodes**

Remove the dashboard welcome `h1`, profile `profile-page__heading` block, and sync page title/description portion. Move the sync platform selector and refresh button into the existing toolbar so those controls remain available.

- [x] **Step 2: Delete orphaned selectors and imports**

Delete `analysis-page__welcome` and `profile-page__heading` rules. Delete sync heading rules after its controls have moved to `drama-sync-center-page__toolbar`. Remove `useAuthStore` from `DashboardPage` if it is no longer used.

- [x] **Step 3: Run focused tests and verify GREEN**

Run:

```powershell
cd kasi-admin-web
pnpm test -- src/App.test.tsx src/pages/dashboard/DashboardPage.test.tsx src/pages/drama/DramaSyncCenterPage.test.tsx
```

Expected: PASS, with intro text absent and sync controls present.

### Task 4: Canonical verification and scope review

**Files:**
- Verify all files changed in Tasks 1-3

- [x] **Step 1: Run the admin canonical Gate**

```powershell
cd kasi-admin-web
pnpm check
```

Expected: lint, format check, all Vitest tests, TypeScript compilation, and Vite build exit 0. Record test counts from current output.

- [x] **Step 2: Check whitespace and scope**

```powershell
cd ..
git diff --check
git diff -- kasi-admin-web docs/superpowers/specs/2026-09-09-admin-page-intro-removal-design.md docs/superpowers/plans/2026-09-09-admin-page-intro-removal.md
```

Expected: `git diff --check` exits 0; diff contains no changes to APIs, schema, navigation, table columns, modal titles, or login branding.

- [x] **Step 3: Do not commit**

Leave all changes uncommitted because the user has not authorized a commit or push.
