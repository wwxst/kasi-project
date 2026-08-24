# Login Compact Row Layout Implementation Plan

> Superseded by
> `docs/superpowers/plans/2026-08-17-login-stacked-layout-restoration-plan.md`
> after user visual review. This document records the earlier implementation and
> is not the current login-layout contract.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Place each login label and input on one row with no more than 10px vertical spacing between the account and password rows.

**Architecture:** Keep TDesign Form and FormItem validation unchanged. Express the row structure as explicit inline style contracts and use a login-only CSS scope to remove normal FormItem margins while preserving space for validation messages.

**Tech Stack:** React 19, TypeScript, TDesign React, Vitest, Testing Library

---

### Task 1: Add The Layout Regression Test

**Files:**

- Test: `src/pages/auth/PublicAuthPages.test.tsx`

- [x] Add a test that renders `/login` and asserts the form gap is `10px`.
- [x] Assert each `.form-field` uses `48px minmax(0, 1fr)` columns, centered alignment, and a `10px` column gap.
- [x] Run `pnpm exec vitest run src/pages/auth/PublicAuthPages.test.tsx`.
- [x] Confirm the test fails because the current fields use a single-column grid and the form gap is not explicit.

### Task 2: Implement The Compact Horizontal Rows

**Files:**

- Modify: `src/pages/auth/LoginPage.tsx`
- Modify: `src/pages/auth/auth-pages.css`

- [x] Set the login Form style to `{ gap: '10px' }`.
- [x] Set each accessible field wrapper style to:

```tsx
{
  alignItems: 'center',
  columnGap: '10px',
  gridTemplateColumns: '48px minmax(0, 1fr)',
  width: '100%',
}
```

- [x] Add the `login-form` class, remove normal FormItem bottom margins, and preserve 24px only for `.t-form__item-with-extra` validation content.
- [x] Keep TDesign FormItem validation, Input behavior, API calls, and error handling unchanged.
- [x] Rerun the focused test and confirm it passes.

### Task 3: Document And Verify

**Files:**

- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-17-user-account-frontend-design.md`
- Update: `docs/screenshots/login-validation-desktop.png`
- Update: `docs/screenshots/login-validation-mobile.png`

- [x] Document the horizontal label/input layout and 10px row gap.
- [x] Run `pnpm test`, `pnpm typecheck`, `pnpm lint`, `pnpm format:check`, and `pnpm build`.
- [x] Verify desktop and 390px mobile layouts in the browser.
- [x] Confirm labels and inputs share rows, row spacing is 10px, no horizontal overflow exists, and field errors remain attached to their FormItems.
