# Register And Recovery Field Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace page-level client-validation banners on registration and password recovery with TDesign field-level validation.

**Architecture:** Extract reusable accessible authentication input wrappers that remain direct FormItem children. RegisterPage and ForgotPasswordPage use TDesign Form instances for field validation, while existing API calls, countdowns, recovery-step transitions, reset-token handling, notices, and navigation remain unchanged. API business failures render with TDesign Alert.

**Tech Stack:** React 19, TypeScript, TDesign React, Vitest, Testing Library, MSW

---

### Task 1: Register Page Field Validation

**Files:**

- Test: `src/pages/auth/PublicAuthPages.test.tsx`
- Create: `src/features/auth/components/AuthInputField.tsx`
- Modify: `src/pages/auth/LoginPage.tsx`
- Modify: `src/pages/auth/RegisterPage.tsx`

- [x] Add an empty-registration submission test that expects separate account, code, password, and confirmation errors inside their TDesign FormItems and rejects `请完整填写注册信息`.
- [x] Run `pnpm exec vitest run src/pages/auth/PublicAuthPages.test.tsx` and confirm the test fails against the page-level banner.
- [x] Extract `AuthInputField` and `VerificationCodeField` wrappers and reuse `AuthInputField` from LoginPage.
- [x] Convert RegisterPage to TDesign Form/FormItem with account, verification-code, password-length, and password-confirmation rules.
- [x] Validate only the account field before sending a registration code.
- [x] Render API failures with `<Alert theme="error" />`.
- [x] Rerun the focused tests and confirm registration success and field validation pass.

### Task 2: Password Recovery Field Validation

**Files:**

- Test: `src/pages/auth/PublicAuthPages.test.tsx`
- Modify: `src/pages/auth/ForgotPasswordPage.tsx`

- [x] Add failing tests for an empty target in the request step, an empty code in the verify step, and empty password fields in the reset step.
- [x] Assert each message belongs to its TDesign FormItem and no client error uses `.form-error`.
- [x] Convert the identity and reset phases to keyed TDesign Forms with conditional FormItems.
- [x] Validate the target before sending or resending, validate the code before verification, and validate both password fields before reset.
- [x] Keep the reset token in component memory and preserve existing request bodies and navigation.
- [x] Render API and reset-token failures with `<Alert theme="error" />`.
- [x] Rerun the focused tests and confirm all recovery steps pass.

### Task 3: Documentation And Full Verification

**Files:**

- Modify: `README.md`
- Review: `docs/superpowers/specs/2026-08-17-user-account-frontend-design.md`
- Update: `docs/screenshots/register-mobile.png`
- Update: `docs/screenshots/forgot-password-mobile.png`

- [x] Document field-level validation across login, registration, and password recovery.
- [x] Run `pnpm test`, `pnpm typecheck`, `pnpm lint`, `pnpm format:check`, and `pnpm build`.
- [x] Inspect registration and each password-recovery step at desktop and 390px mobile widths.
- [x] Confirm field errors do not overlap inputs, buttons, steps, or adjacent content.
