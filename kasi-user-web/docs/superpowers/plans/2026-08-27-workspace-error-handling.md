# Workspace Error Handling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Protect authenticated workspace routes and replace raw media-account query errors with TDesign Starter message prompts.

**Architecture:** `AppRouter` owns the authentication route boundary through a small `ProtectedWorkspace` component subscribed to Zustand. `httpClient` remains the single 401 session-clearing boundary, while `MediaAccountsPage` handles only non-401 presentation with `MessagePlugin.error`.

**Tech Stack:** React 19, React Router 7, Zustand, TanStack Query 5, Axios, TDesign React, Vitest, Testing Library

---

### Task 1: Protect workspace routes

**Files:**

- Modify: `src/app/AppRouter.tsx`
- Test: `src/mediaAccountsRoute.test.tsx`

- [ ] Add a route test that opens `/workspace/media-accounts` without a token and expects `/login` while `getMediaAccounts` remains uncalled.
- [ ] Run `pnpm test src/mediaAccountsRoute.test.tsx` and confirm the new test fails because the workspace currently renders.
- [ ] Add `ProtectedWorkspace`, subscribe to `useAuthStore`, and render either `AppShell` or `<Navigate to="/login" replace />`.
- [ ] Run `pnpm test src/mediaAccountsRoute.test.tsx` and confirm both authenticated and unauthenticated paths pass.

### Task 2: Replace inline API errors with Starter messages

**Files:**

- Modify: `src/shared/api/httpClient.ts`
- Modify: `src/pages/mediaAccounts/MediaAccountsPage.tsx`
- Modify: `src/pages/mediaAccounts/MediaAccountsPage.module.less`
- Test: `src/pages/mediaAccounts/MediaAccountsPage.test.tsx`

- [ ] Replace the existing inline-error test with tests that expect `MessagePlugin.error('媒体账号加载失败，请稍后重试')` for ordinary failures, keep the filter form, do not render the original error, and suppress the page-level message for an Axios 401.
- [ ] Run `pnpm test src/pages/mediaAccounts/MediaAccountsPage.test.tsx` and confirm the new test fails against the inline error implementation.
- [ ] Export `isUnauthorizedError` from the shared Axios module and reuse it in the response interceptor.
- [ ] Add a query-error effect that calls `MessagePlugin.error` for non-401 failures and always renders the `Table` below the filter form.
- [ ] Remove the unused `.error` Less rule.
- [ ] Run `pnpm test src/pages/mediaAccounts/MediaAccountsPage.test.tsx` and confirm the Starter message behavior passes.

### Task 3: Verify and document current behavior

**Files:**

- Modify: `README.md`

- [ ] Document the protected workspace and Starter-style non-401 error prompt in the current behavior section.
- [ ] Run `pnpm test`, `pnpm typecheck`, `pnpm lint`, `pnpm format:check`, and `pnpm build`.
- [ ] Check `/workspace/media-accounts` in desktop and mobile browser viewports; verify no raw 401 text, no content overlap, and no document-level horizontal overflow.

The user frontend has no independent Git repository, so each green test checkpoint replaces the plan's normal per-task commit step.
