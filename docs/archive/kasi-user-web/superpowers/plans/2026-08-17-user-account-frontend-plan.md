# Kasi User Account Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a tested React 19 account frontend that connects to the existing `kasi-backend` user-authentication API.

**Architecture:** Use a feature-first Vite SPA. Zustand owns only the session token and expiry, TanStack Query owns current-user server state, and one Axios boundary unwraps backend responses and normalizes failures.

**Tech Stack:** React 19, TypeScript, Vite, TDesign React, React Router, TanStack Query, Zustand, Axios, Vitest, Testing Library, MSW, Oxlint, Prettier, pnpm.

---

### Task 1: Create The Tooling And Test Harness

**Files:**

- Create: `package.json`
- Create: `pnpm-workspace.yaml`
- Create: `vite.config.ts`
- Create: `tsconfig.json`
- Create: `tsconfig.app.json`
- Create: `tsconfig.node.json`
- Create: `.oxlintrc.json`
- Create: `.prettierrc.json`
- Create: `.prettierignore`
- Create: `.gitignore`
- Create: `index.html`
- Create: `src/test/setup.ts`
- Create: `src/test/server.ts`
- Create: `src/test/renderApp.tsx`

- [ ] Install the pinned dependency families with `pnpm install`.
- [ ] Configure Vite to proxy `/api` to `VITE_PROXY_TARGET` or `http://localhost:8080`.
- [ ] Configure Vitest with jsdom and a shared setup file.
- [ ] Configure TypeScript strict mode and the `dev`, `build`, `test`, `typecheck`, `lint`, and `format:check` scripts.
- [ ] Run `pnpm typecheck` and confirm the harness is syntactically valid.

### Task 2: Implement The Session And HTTP Boundary With TDD

**Files:**

- Test: `src/features/auth/model/authStore.test.ts`
- Test: `src/shared/api/httpClient.test.ts`
- Create: `src/features/auth/model/authStore.ts`
- Create: `src/shared/api/ApiError.ts`
- Create: `src/shared/api/types.ts`
- Create: `src/shared/api/httpClient.ts`

- [ ] Write a failing store test proving that a session stores an absolute expiry and that expired sessions are removed.
- [ ] Run `pnpm test src/features/auth/model/authStore.test.ts` and confirm it fails because `authStore` does not exist.
- [ ] Implement the minimal persisted Zustand session store.
- [ ] Run the store test and confirm it passes.
- [ ] Write failing MSW tests proving that business errors become `ApiError`, 401 clears the session, and 503 does not clear it.
- [ ] Run `pnpm test src/shared/api/httpClient.test.ts` and confirm the expected failures.
- [ ] Implement the Axios request/response boundary and rerun both test files.

Expected session API:

```ts
type Session = { accessToken: string; expiresAt: number }

useAuthStore.getState().setSession({
  accessToken: response.accessToken,
  expiresAt: Date.now() + response.expiresIn * 1000,
})
```

### Task 3: Implement Public Authentication Flows With TDD

**Files:**

- Test: `src/pages/auth/PublicAuthPages.test.tsx`
- Create: `src/features/auth/api/authApi.ts`
- Create: `src/features/auth/api/authTypes.ts`
- Create: `src/features/auth/components/AuthShell.tsx`
- Create: `src/pages/auth/LoginPage.tsx`
- Create: `src/pages/auth/RegisterPage.tsx`
- Create: `src/pages/auth/ForgotPasswordPage.tsx`
- Create: `src/pages/auth/auth-pages.css`

- [ ] Write failing route-level tests for login success/failure, registration, and the three password-reset stages.
- [ ] Verify the tests fail because the pages and API functions do not exist.
- [ ] Implement typed API functions for all anonymous `/api/user/auth/**` endpoints.
- [ ] Implement semantic forms with TDesign controls and client validation.
- [ ] Keep `resetToken` in `ForgotPasswordPage` state only.
- [ ] Rerun the public-page tests and confirm all cases pass.

The forgot-password assertion must prove:

```ts
expect(window.location.search).not.toContain('resetToken')
expect(window.sessionStorage.getItem('resetToken')).toBeNull()
expect(window.localStorage.getItem('resetToken')).toBeNull()
```

### Task 4: Implement Session Bootstrap And Protected Account Pages With TDD

**Files:**

- Test: `src/app/AppRouter.test.tsx`
- Create: `src/features/account/api/accountApi.ts`
- Create: `src/layouts/AccountLayout.tsx`
- Create: `src/layouts/account-layout.css`
- Create: `src/pages/account/AccountPage.tsx`
- Create: `src/pages/account/SecurityPage.tsx`
- Create: `src/pages/account/account-pages.css`
- Create: `src/app/AppProviders.tsx`
- Create: `src/app/AppRouter.tsx`
- Create: `src/app/App.tsx`
- Create: `src/main.tsx`
- Create: `src/styles/global.css`

- [ ] Write failing tests for unauthenticated redirects, `/me` bootstrap, account rendering, password change, logout, 401, and 503.
- [ ] Verify the tests fail because the application router does not exist.
- [ ] Implement query providers, protected/public-only routes, and startup verification.
- [ ] Implement the compact TDesign account layout and read-only account page.
- [ ] Implement password change and logout with mandatory local session cleanup.
- [ ] Rerun the router tests and all earlier tests.

### Task 5: Documentation And Full Verification

**Files:**

- Create: `README.md`
- Create: `.env.example`
- Review: `docs/superpowers/specs/2026-08-17-user-account-frontend-design.md`

- [ ] Document Node 24, pnpm 11, local proxy behavior, backend prerequisites, routes, scripts, and current scope.
- [ ] Run `pnpm test` and require zero failures and no console errors.
- [ ] Run `pnpm typecheck` and require zero errors.
- [ ] Run `pnpm lint` and require zero errors.
- [ ] Run `pnpm format:check` and require zero differences.
- [ ] Run `pnpm build` and require a successful production bundle.
- [ ] Start the Vite server and inspect desktop and mobile screenshots.
- [ ] Confirm no overlapping text, clipped controls, blank content, or failed assets.
