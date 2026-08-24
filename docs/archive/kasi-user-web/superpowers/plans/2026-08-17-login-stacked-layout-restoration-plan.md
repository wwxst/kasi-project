# Login Stacked Layout Restoration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the original stacked login-field visual while retaining TDesign field validation and full-width inputs.

**Architecture:** Keep TDesign Form, FormItem, Input, and Alert behavior unchanged. Remove the horizontal two-column field contract, use an 8px label-to-input row gap, and use a 20px gap between the account and password groups. Preserve the login-only FormItem error-spacing rules.

**Tech Stack:** React 19, TypeScript, TDesign React, Vitest, Testing Library

---

### Task 1: Replace The Horizontal Layout Test

**Files:**

- Test: `src/pages/auth/PublicAuthPages.test.tsx`

- [x] Replace the horizontal-row assertion with a stacked-field assertion.
- [x] Assert the form gap is `20px`, each field row gap is `8px`, horizontal grid styles are absent, and fields remain `width: 100%`.
- [x] Run `pnpm exec vitest run src/pages/auth/PublicAuthPages.test.tsx`.
- [x] Confirm the test fails because the current form uses a 10px gap and horizontal grid columns.

### Task 2: Restore The Original Field Composition

**Files:**

- Modify: `src/pages/auth/LoginPage.tsx`

- [x] Replace the field layout style with:

```tsx
{
  rowGap: '8px',
  width: '100%',
}
```

- [x] Set the login form gap to `20px`.
- [x] Keep `login-form`, TDesign FormItem validation, API calls, and error handling unchanged.
- [x] Rerun the focused test and confirm it passes.

### Task 3: Update Current Documentation And Verify

**Files:**

- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-17-user-account-frontend-design.md`
- Modify: `docs/superpowers/plans/2026-08-17-login-compact-row-layout-plan.md`
- Update: `docs/screenshots/login-validation-desktop.png`
- Update: `docs/screenshots/login-validation-mobile.png`

- [x] Mark the compact horizontal-row plan as superseded.
- [x] Document labels above full-width inputs, an 8px inner gap, and a 20px field-group gap.
- [x] Run `pnpm test`, `pnpm typecheck`, `pnpm lint`, `pnpm format:check`, and `pnpm build`.
- [x] Verify desktop and 390px mobile layouts in the browser.
- [x] Confirm no horizontal overflow, clipped labels, input-width mismatch, or error-message overlap.
