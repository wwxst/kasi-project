# Account Filing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the account filing dialog and row actions while filtering and displaying the GoodShort whitelist status.

**Architecture:** Keep the existing media-account API module as the only HTTP boundary. Extract GoodShort filing selection into the list helper so filtering and rendering share the same rule; keep dialog form state local to the page and invalidate the existing TanStack Query after mutations.

**Tech Stack:** React 19, TypeScript, TDesign React, TanStack Query, Axios, Vitest, Testing Library

---

### Task 1: GoodShort API and status selection

**Files:**

- Modify: `src/features/mediaAccounts/types.ts`
- Modify: `src/features/mediaAccounts/mediaAccountsApi.ts`
- Test: `src/features/mediaAccounts/mediaAccountsApi.test.ts`
- Modify: `src/pages/mediaAccounts/mediaAccountList.ts`
- Test: `src/pages/mediaAccounts/mediaAccountList.test.ts`

- [x] Add typed create and submit API functions for the two existing user endpoints and tests asserting exact method, path, payload, and unwrapped response.
- [x] Run the focused API/list tests and confirm new tests fail because functions and GoodShort selection are missing.
- [x] Add `CreateMediaAccountInput`, `createMediaAccount`, `submitMediaFiling`, and `getGoodShortFiling` with case-insensitive `providerName === 'GoodShort'` matching.
- [x] Change filing-status filtering to use only `getGoodShortFiling(account)` and rerun focused tests.

### Task 2: Account filing dialog

**Files:**

- Create: `src/pages/mediaAccounts/components/AccountFilingDialog.tsx`
- Create: `src/pages/mediaAccounts/components/AccountFilingDialog.module.less`
- Test: `src/pages/mediaAccounts/components/AccountFilingDialog.test.tsx`

- [x] Add tests for required field validation and successful submission payload.
- [x] Run the dialog test and confirm it fails before the component exists.
- [x] Implement the Starter-style `Dialog + Form` with media platform Select, required account ID Input, optional name/link Inputs, controlled confirm loading, and parent-controlled mutation feedback.
- [x] Rerun the dialog test and confirm all dialog behaviors pass.

### Task 3: Integrate page toolbar, GoodShort status, and row actions

**Files:**

- Modify: `src/pages/mediaAccounts/components/SearchForm.tsx`
- Modify: `src/pages/mediaAccounts/MediaAccountsPage.tsx`
- Modify: `src/pages/mediaAccounts/MediaAccountsPage.test.tsx`
- Modify: `src/pages/mediaAccounts/components/SearchForm.test.tsx`

- [x] Add failing assertions for the “账号报白” toolbar button, “GoodShort 报白状态” filter, GoodShort-only row status, and failed-row “重新报白” action.
- [x] Run focused page tests and confirm the assertions fail against the current page.
- [x] Add the toolbar button and Dialog integration, refresh the query after create, rename status labels/options, render GoodShort status, and call `submitMediaFiling` for failed rows.
- [x] Rerun focused page tests and confirm success/error/retry flows pass.

### Task 4: Document and verify

**Files:**

- Modify: `README.md`

- [x] Document that `/workspace/media-accounts` is the account-filing page and its status/filter are GoodShort-specific.
- [x] Run `pnpm test`, `pnpm typecheck`, `pnpm lint`, `pnpm format:check`, and `pnpm build`.
- [x] Verify the unauthenticated browser route redirects to `/login` without a raw API error; dialog, status filter, row action, and mutation flows are covered by focused component/page tests.

The user frontend has no independent Git repository, so green verification checkpoints replace per-task commits.
