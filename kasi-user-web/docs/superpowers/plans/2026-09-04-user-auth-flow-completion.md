# User Authentication Flow Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the existing login page controls to the backend registration, verification-code login, and forgot-password APIs, then produce a deployable user frontend archive.

**Architecture:** Keep authentication UI state inside `LoginPage.tsx` and reuse the existing functions in `features/auth/authApi.ts`. Store password-reset tokens only in component state, start resend countdowns only after successful API responses, and preserve the current password login and unavailable WeChat notice.

**Tech Stack:** React 19, TypeScript, TDesign React, React Router, Zustand, Vitest, React Testing Library, Vite.

---

### Task 1: Reproduce the disconnected authentication controls

**Files:**

- Modify: `src/App.test.tsx`

- [ ] Add tests that click the registration and verification-login send buttons and assert the corresponding API wrappers receive the entered account.
- [ ] Add tests that submit verification-code login and registration and assert their exact request payloads and resulting navigation or mode change.
- [ ] Add a test for the three-step forgot-password flow, including keeping the reset token out of browser storage and the URL.
- [ ] Run `pnpm test src/App.test.tsx` and confirm the new tests fail because the page does not call these APIs.

### Task 2: Connect registration and verification-code login

**Files:**

- Modify: `src/pages/LoginPage.tsx`

- [ ] Import and call `sendRegisterCode`, `registerUser`, `sendLoginCode`, and `loginUserWithCode`.
- [ ] Validate the destination field before sending and start the 60-second countdown only after a successful response.
- [ ] Submit code login through the existing session store and navigate to `/workspace` on success.
- [ ] Add password confirmation to registration, submit the exact backend DTO, and return to login after successful registration.
- [ ] Run `pnpm test src/App.test.tsx` and confirm these paths pass.

### Task 3: Connect forgot-password recovery

**Files:**

- Modify: `src/pages/LoginPage.tsx`
- Modify: `src/pages/login.css`

- [ ] Make the existing forgot-password control open a recovery form.
- [ ] Send the recovery code, verify it for a reset token, and submit matching new passwords.
- [ ] Keep the reset token in React state only and return to password login after success.
- [ ] Preserve responsive dimensions and use the existing page error treatment.
- [ ] Run `pnpm test src/App.test.tsx` and confirm the complete recovery test passes.

### Task 4: Document and verify the release

**Files:**

- Modify: `README.md`
- Modify: `AGENTS.md`
- Create: release archive outside the Git worktree

- [ ] Record the now-working registration, code-login, and password-recovery behavior.
- [ ] Run `pnpm check` and `git diff --check`.
- [ ] Inspect the production build output and create `kasi-user-dist.zip` from the contents of `dist`, not from the `dist` directory wrapper.
- [ ] List the archive entries and calculate its SHA-256 checksum.
