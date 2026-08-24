# Kasi User Account Frontend Design

## 1. Goal

Create a standalone desktop-first user frontend at
`E:\JavaProjects\kasi-project\kasi-user-web`. The first release provides the
complete promotion-user authentication and account-security flows already
implemented by `kasi-backend`.

The application is an actual account workspace, not a marketing landing page
or a placeholder dashboard.

## 2. Scope

Included:

- User login with mobile number or email.
- Registration code delivery and user registration.
- Three-step forgot-password flow: send code, verify code, reset password.
- Session restoration after a page refresh.
- Read-only current-user profile.
- Current-user password change.
- Current-session logout.

Excluded:

- Editing nickname, real name, mobile, email, or avatar.
- Promotion links, orders, earnings, invitations, or analytics.
- Refresh tokens, silent renewal, and remember-me behavior.
- Administrator endpoints under `/api/admin/**` or `/api/user/management/**`.
- Backend changes.

## 3. Technology

- React 19 and React DOM 19.
- TypeScript in strict mode.
- Vite 8.
- TDesign React and TDesign Icons React.
- React Router for public and protected routes.
- TanStack Query for server state and mutations.
- Zustand for the minimal session state.
- Axios for the HTTP client.
- Vitest, React Testing Library, user-event, and MSW for tests.
- Oxlint and Prettier for static checks.
- Node.js 24 and pnpm 11.

## 4. Architecture

Use a feature-first single-page application:

```text
src/
  app/                 providers, bootstrap, router
  shared/
    api/               Axios client, response envelope, ApiError
    config/            environment configuration
    ui/                small cross-feature UI primitives
  features/
    auth/              login, registration, password recovery, session
    account/           current-user profile, password change, logout
  layouts/             authenticated account shell
  pages/               route-level composition
  styles/              global styles and TDesign theme variables
  test/                MSW server and render helpers
```

Pages compose feature APIs and focused UI. API contracts and state remain in
their owning feature. Shared code must not contain user-domain behavior.

## 5. Routes

| Route               | Access        | Purpose                                 |
| ------------------- | ------------- | --------------------------------------- |
| `/login`            | Public-only   | Login                                   |
| `/register`         | Public-only   | Send registration code and register     |
| `/forgot-password`  | Public-only   | Verify identity and reset password      |
| `/account`          | Authenticated | Read-only profile and login information |
| `/account/security` | Authenticated | Change current password                 |
| `/`                 | Any           | Redirect according to session state     |

Authenticated routes wait for session bootstrap before redirecting. A valid
locally stored token is verified through `GET /api/user/auth/me` before the
account workspace renders.

## 6. Session And Data Flow

Zustand stores only `accessToken`, absolute `expiresAt`, and bootstrap state.
These values use `sessionStorage`, so reloads preserve the session while a
closed tab normally requires a new login. Current-user data belongs to the
TanStack Query cache and is never duplicated in Zustand.

The Axios request interceptor adds the Bearer token. The response layer unwraps
`ApiResponse<T>` and raises `ApiError` when HTTP 200 contains a non-zero business
code.

- HTTP 401 clears session state and cached user data, then returns to login.
- HTTP 403 preserves the session and reports insufficient permission.
- HTTP 503 and business code 1007 preserve the session and offer retry.
- Network and HTTP 500 failures show a generic retryable message.
- Password-change success clears all local session data and returns to login.
- Logout attempts server revocation, then clears the local session even if the
  request fails.

The password-reset token stays only in component memory. It is never written to
the URL, Zustand, localStorage, or sessionStorage.

## 7. UI

The interface is desktop-first and remains usable on a narrow viewport. Public
authentication pages use a focused branded workspace with TDesign inputs,
buttons, alerts, and steps. Login validation uses TDesign Form and FormItem so
account and password errors appear below their own fields; backend business
errors use TDesign Alert. Each login label sits above a full-width input on
desktop and narrow viewports. Labels use an 8px inner gap to their controls, and
the account and password groups use a 20px gap. Validation errors reserve their
own space instead of overlapping the next field. The authenticated workspace
uses a compact top bar and a constrained content area rather than an
administration sidebar.

Login, registration, and all password-recovery steps use TDesign Form and
FormItem for client validation. Required, format, verification-code, password
length, and password-confirmation errors appear below the owning field. Backend
business failures use TDesign Alert; client validation must not use a page-level
`.form-error` banner.

The account page displays user number, nickname, available contact methods,
registration time, last login time, and last login address. Values are
read-only. The security page contains the old password, new password, and
confirmation fields.

Client validation mirrors stable backend constraints: mobile or email account,
six-digit verification code, password length of at least eight characters, and
matching password confirmation. Backend messages remain authoritative.

## 8. Testing And Verification

MSW integration tests cover:

- Route protection and startup session verification.
- Login success and business failure.
- Registration code and registration behavior.
- The complete three-step forgot-password flow.
- Read-only account rendering.
- Password change clearing the session.
- Logout clearing the session.
- HTTP 401 redirect and HTTP 503 session preservation.
- Reset tokens never entering URL or browser storage.

Completion requires clean output from `pnpm test`, `pnpm typecheck`,
`pnpm lint`, `pnpm format:check`, and `pnpm build`, followed by desktop and
mobile browser screenshots with no overlap or blank content.
