# Frontend Legacy Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the obsolete `/account` frontend implementation while preserving the current `/workspace` application and order behavior.

**Architecture:** Move the only current consumer out of the legacy promotion feature, then delete the now-closed legacy source and test graph. Keep all current route, HTTP, session, and display contracts unchanged.

**Tech Stack:** React 19, TypeScript strict, Vite, Vitest, TanStack Query, TDesign React

---

### Task 1: Move the current order feature

**Files:**

- Create: `src/features/orders/ordersApi.ts`
- Create: `src/features/orders/ordersApi.test.ts`
- Create: `src/features/orders/types.ts`
- Modify: `src/pages/orders/OrdersPage.tsx`
- Modify: `src/pages/orders/OrdersPage.test.tsx`
- Delete: `src/features/promotion/api/promotionOrderApi.ts`
- Delete: `src/features/promotion/api/promotionOrderApi.test.ts`
- Delete: `src/features/promotion/api/promotionOrderTypes.ts`

- [x] Move the existing order API, types, and API test without changing exported behavior.
- [x] Update both order-page imports to `features/orders`.
- [x] Run the focused order tests; all three tests pass.

### Task 2: Delete the closed legacy graph

**Files:**

- Delete: `src/app/App.tsx`
- Delete: `src/app/AppProviders.tsx`
- Delete: `src/app/AppRouter.test.tsx`
- Delete: `src/features/account/**`
- Delete: `src/features/auth/api/**`
- Delete: `src/features/auth/components/**`
- Delete: `src/features/auth/model/**`
- Delete: `src/features/promotion/**`
- Delete: `src/layouts/**`
- Delete: `src/pages/account/**`
- Delete: `src/pages/auth/**`
- Delete: `src/pages/promotion/**`
- Delete: `src/shared/api/ApiError.ts`
- Delete: `src/test/server.ts`
- Delete: `src/test/setup.ts`

- [x] Delete only the files proven to have no current production consumer.
- [x] Run an exact import scan for the deleted paths, `apiRequest`, `AppProviders`, `ApiError`, and `test/server`; no matches remain in `src`.
- [x] Run `pnpm typecheck`; exit code 0.

### Task 3: Document and verify the single frontend

**Files:**

- Modify: `README.md`
- Modify: `AGENTS.md`

- [x] Document that only the root `src/App.tsx` and `/workspace` application are current.
- [x] Run `pnpm test`, `pnpm typecheck`, `pnpm lint`, and `pnpm build`; all exit with code 0. The full `pnpm format:check` remains blocked by the pre-existing user-modified `pnpm-lock.yaml`; all cleanup-owned files pass a focused Prettier check.
- [x] Run `git diff --check` from the root repository and review the final targeted status.
