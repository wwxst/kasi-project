# User Order Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the user workspace order placeholder with the existing personal monthly order API, server-side pagination, localized status display, and CSV export.

**Architecture:** Keep the backend contract unchanged. Extend the existing promotion order API wrapper with page parameters, add one focused `OrdersPage`, and register it only on `/workspace/orders`; `/workspace/commission` remains a placeholder.

**Tech Stack:** React 19, TypeScript strict, TanStack Query, TDesign React, Vitest, Testing Library, MSW

---

### Task 1: Paginated order API contract

**Files:**

- Create: `src/features/promotion/api/promotionOrderApi.test.ts`
- Modify: `src/features/promotion/api/promotionOrderApi.ts`

- [x] **Step 1: Write the failing API test**

Add a test that calls `fetchPromotionOrders('2026-08', 2, 50)` and asserts the request query contains `month=2026-08&page=2&size=50`.

- [x] **Step 2: Run the API test and verify RED**

Run: `pnpm exec vitest run src/features/promotion/api/promotionOrderApi.test.ts`

Expected: FAIL because `fetchPromotionOrders` does not accept or forward `page` and `size`.

- [x] **Step 3: Implement the minimal API change**

Change the function signature to:

```ts
export async function fetchPromotionOrders(
  month: string,
  page = 1,
  size = 20,
): Promise<PromotionOrderPage>
```

Forward `{ month, page, size }` to `apiRequest` and leave the monthly summary endpoint unchanged.

- [x] **Step 4: Run the API test and verify GREEN**

Run: `pnpm exec vitest run src/features/promotion/api/promotionOrderApi.test.ts`

Expected: PASS with no warnings.

### Task 2: User order page and route

**Files:**

- Create: `src/pages/orders/OrdersPage.test.tsx`
- Create: `src/pages/orders/OrdersPage.tsx`
- Create: `src/pages/orders/OrdersPage.module.less`
- Modify: `src/app/routes.tsx`

- [x] **Step 1: Write the failing page test**

Cover the current-month request, localized order/commission status, amount and time rendering, CSV button, and server pagination query parameters. Assert that the page uses only `/api/user/promotion/orders` and does not request `/monthly`.

- [x] **Step 2: Run the page test and verify RED**

Run: `pnpm exec vitest run src/pages/orders/OrdersPage.test.tsx`

Expected: FAIL because `OrdersPage` does not exist and the route still points to `WorkspacePage`.

- [x] **Step 3: Implement the minimal page**

Create an unframed TDesign page with a month picker, icon CSV export button, horizontally scrollable table, and server pagination. Render the backend fields `externalOrderId`, `orderAmount`, `currency`, `status`, `paidAt`, `trackingNo`, `commissionAmount`, and `commissionStatus`; map enum values to Chinese labels without changing raw API values.

- [x] **Step 4: Register only the order route**

Import `OrdersPage` in `src/app/routes.tsx` and replace the `/workspace/orders` placeholder element. Keep `/workspace/commission` unchanged.

- [x] **Step 5: Run focused tests and verify GREEN**

Run: `pnpm exec vitest run src/features/promotion/api/promotionOrderApi.test.ts src/pages/orders/OrdersPage.test.tsx`

Expected: both files pass with no warnings.

### Task 3: Current-behavior documentation and verification

**Files:**

- Modify: `README.md`
- Modify: `AGENTS.md`

- [x] **Step 1: Update current behavior**

Document that `/workspace/orders` now reads the signed-in user's monthly attributed orders with server pagination and CSV export, while `/workspace/commission` remains a placeholder. State that the frontend displays backend commission snapshots and does not recalculate them.

- [ ] **Step 2: Run frontend verification**

Run:

```powershell
pnpm test
pnpm typecheck
pnpm lint
pnpm format:check
pnpm build
```

Expected: all commands exit with code 0.

- [ ] **Step 3: Run repository diff validation**

Run: `git diff --check -- kasi-user-web`

Expected: exit code 0 and no output.

- [ ] **Step 4: Visually verify desktop and mobile**

Start the Vite development server and inspect `/workspace/orders` at a desktop viewport and at 390px width. Confirm the page does not widen the document and only the table region scrolls horizontally.
