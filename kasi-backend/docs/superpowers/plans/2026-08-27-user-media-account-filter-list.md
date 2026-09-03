# User Media Account Filter List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 TDesign Starter 的筛选列表页结构和视觉迁移到用户前端 `/workspace/media-accounts`，并接入现有用户媒体账号接口。

**Architecture:** 使用 React Query 请求 `GET /api/user/promotion/media-accounts` 返回的完整列表；页面通过纯函数完成筛选、状态映射和分页，不改变后端契约。筛选表单和列表页分别拆成组件，页面容器与表格配置沿用 Starter 的 Form/Row/Col/Table.pagination 组织。

**Tech Stack:** React 19, TypeScript, TDesign React, React Query, Vitest, Testing Library, Less modules, Vite.

---

### Task 1: Add Media Account API Contract

**Files:**
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/features/mediaAccounts/types.ts`
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/features/mediaAccounts/mediaAccountsApi.ts`
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/features/mediaAccounts/mediaAccountsApi.test.ts`

- [ ] **Step 1: Write the failing test**

Mock `httpClient.get`, call `getMediaAccounts`, and assert it unwraps a successful `ApiResponse<MediaAccount[]>`; add a failure case asserting a non-zero API code throws its message.

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test -- --run src/features/mediaAccounts/mediaAccountsApi.test.ts`

Expected: FAIL because the API module and types do not exist.

- [ ] **Step 3: Write minimal implementation**

Define `MediaType`, `FilingStatus`, `MediaFiling`, and `MediaAccount` from the backend VO. Implement `getMediaAccounts()` with the existing `httpClient` and the same response unwrapping convention as `features/auth/authApi.ts`.

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm test -- --run src/features/mediaAccounts/mediaAccountsApi.test.ts`

Expected: PASS.

- [ ] **Step 5: Record the focused change**

`kasi-user-web` has no `.git` directory, so leave these files in its working tree and verify their paths with `Get-ChildItem`; do not stage unrelated backend changes.

### Task 2: Build Starter-Style Search Form

**Files:**
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/mediaAccounts/components/SearchForm.tsx`
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/mediaAccounts/components/SearchForm.module.less`
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/mediaAccounts/components/SearchForm.test.tsx`

- [ ] **Step 1: Write the failing test**

Render the form and assert the media type, account keyword, account status, and filing status controls exist; submit values and assert the parent callback receives the normalized form object; reset and assert the reset callback runs.

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test -- --run src/pages/mediaAccounts/components/SearchForm.test.tsx`

Expected: FAIL because the component does not exist.

- [ ] **Step 3: Write minimal implementation**

Copy Starter's `Form + Row + Col` structure and responsive spans. Use TDesign `Select` options for `TIKTOK/FACEBOOK/YOUTUBE/INSTAGRAM`, account `ENABLED/DISABLED`, and filing `PENDING/APPROVED/FAILED`. Keep query/reset buttons in the fixed right action column and emit values through typed callbacks.

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm test -- --run src/pages/mediaAccounts/components/SearchForm.test.tsx`

Expected: PASS.

- [ ] **Step 5: Record the focused change**

`kasi-user-web` has no `.git` directory; leave the component, style, and test in place for the parent repository integration.

### Task 3: Implement Filtered Table and Client Pagination

**Files:**
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/mediaAccounts/mediaAccountList.ts`
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/mediaAccounts/mediaAccountList.test.ts`
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/mediaAccounts/MediaAccountsPage.tsx`
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/mediaAccounts/MediaAccountsPage.module.less`
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/mediaAccounts/MediaAccountsPage.test.tsx`

- [ ] **Step 1: Write the failing pure-function tests**

Test that filtering matches account name or external ID case-insensitively, filters media/account/filing status, and slices the requested page while returning the filtered total. Test empty input returns an empty page without throwing.

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test -- --run src/pages/mediaAccounts/mediaAccountList.test.ts`

Expected: FAIL because the list derivation helper does not exist.

- [ ] **Step 3: Write minimal implementation**

Implement typed `filterAndPaginateMediaAccounts(accounts, filters, page, pageSize)`. Treat a row's filing status as matching when any filing has the selected status. Render the page with Starter's `pageWithPadding/pageWithColor` equivalent, `SearchForm`, and TDesign `Table` using fixed/ellipsis columns, custom `Tag` cells, hover, row key `id`, and `pagination={{ pageSize, current, total, showJumper, onCurrentChange, onPageSizeChange }}`. Use React Query for loading/error data and reset page to `1` whenever filters change.

- [ ] **Step 4: Run focused tests to verify behavior**

Run: `pnpm test -- --run src/pages/mediaAccounts/mediaAccountList.test.ts src/pages/mediaAccounts/MediaAccountsPage.test.tsx`

Expected: PASS for initial loading, successful rows, filter submission, reset, page-size change, empty state, and API error state.

- [ ] **Step 5: Record the focused change**

`kasi-user-web` has no `.git` directory; verify only the listed files changed and leave unrelated worktrees untouched.

### Task 4: Replace the Placeholder Route

**Files:**
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/app/routes.tsx`
- Modify or Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/App.test.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/README.md`

- [ ] **Step 1: Write the failing route test**

Navigate a rendered authenticated router to `/workspace/media-accounts` and assert the page heading, filter controls, and table region are present instead of the placeholder copy.

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test -- --run src/App.test.tsx`

Expected: FAIL because the route still renders `WorkspacePage`.

- [ ] **Step 3: Write minimal implementation**

Import `MediaAccountsPage` and use it only for the `/workspace/media-accounts` route; preserve all other routes and titles. Update the user frontend README to distinguish the now-implemented media-account list from remaining placeholder pages.

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm test -- --run src/App.test.tsx`

Expected: PASS.

- [ ] **Step 5: Record the focused change**

`kasi-user-web` has no `.git` directory; verify only the route, focused test, and README changed.

### Task 5: Full Verification and Browser Review

**Files:**
- No additional source files unless a focused verification exposes a regression.

- [ ] **Step 1: Run the full frontend checks**

Run from `E:/JavaProjects/kasi-project/kasi-user-web`:

```powershell
pnpm test
pnpm typecheck
pnpm lint
pnpm format:check
pnpm build
```

Expected: tests, typecheck, format, and build exit successfully; lint may retain the existing Fast Refresh warnings but must have no errors.

- [ ] **Step 2: Verify in a browser**

Open the user frontend, authenticate with the existing local test account, visit `/workspace/media-accounts`, and verify the Starter container spacing, responsive filter grid, table loading/error/empty states, status tags, and pagination. Remove the generated `dist` directory after the build if it is not tracked.

- [ ] **Step 3: Review the final diff**

Run `git diff --check` for the relevant repository and `git status --short`; confirm no backend files or unrelated user changes were staged.
