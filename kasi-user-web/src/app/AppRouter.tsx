import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { Button } from 'tdesign-react'
import { useCurrentUser } from '../features/account/api/accountApi'
import { useAuthStore } from '../features/auth/model/authStore'
import { ApiError } from '../shared/api/ApiError'
import { AccountLayout } from '../layouts/AccountLayout'
import { AccountPage } from '../pages/account/AccountPage'
import { SecurityPage } from '../pages/account/SecurityPage'
import { MediaAccountFilingPage } from '../pages/promotion/MediaAccountFilingPage'
import { PromotionLinkPage } from '../pages/promotion/PromotionLinkPage'
import { PromotionCreatePage } from '../pages/promotion/PromotionCreatePage'
import { PromotionIncomePage } from '../pages/promotion/PromotionIncomePage'
import { ForgotPasswordPage } from '../pages/auth/ForgotPasswordPage'
import { LoginPage } from '../pages/auth/LoginPage'
import { RegisterPage } from '../pages/auth/RegisterPage'

function LoadingState() {
  return (
    <main className="bootstrap-state" role="status">
      <span className="loading-mark" aria-hidden="true" />
      <p>正在验证账户状态…</p>
    </main>
  )
}

function AuthUnavailableState({
  message,
  onRetry,
}: {
  message: string
  onRetry: () => void
}) {
  return (
    <main className="bootstrap-state bootstrap-error">
      <p className="page-eyebrow">TEMPORARILY UNAVAILABLE</p>
      <h1>暂时无法验证登录状态</h1>
      <p role="alert">{message}</p>
      <Button theme="primary" onClick={onRetry}>
        重试
      </Button>
    </main>
  )
}

function ProtectedRoute() {
  const accessToken = useAuthStore((state) => state.accessToken)
  const expiresAt = useAuthStore((state) => state.expiresAt)
  const hasValidSession = Boolean(
    accessToken && expiresAt && expiresAt > Date.now(),
  )
  const { isPending, error, refetch } = useCurrentUser(hasValidSession)

  if (!accessToken || !expiresAt || expiresAt <= Date.now()) {
    return <Navigate to="/login" replace />
  }
  if (isPending) return <LoadingState />
  if (error) {
    return (
      <AuthUnavailableState
        message={
          error instanceof ApiError && error.retryable
            ? error.message
            : '暂时无法完成账户验证，请稍后重试'
        }
        onRetry={() => void refetch()}
      />
    )
  }
  return <Outlet />
}

function PublicOnlyRoute() {
  const accessToken = useAuthStore((state) => state.accessToken)
  const expiresAt = useAuthStore((state) => state.expiresAt)
  return accessToken && expiresAt && expiresAt > Date.now() ? (
    <Navigate to="/account" replace />
  ) : (
    <Outlet />
  )
}

export function AppRouter() {
  return (
    <Routes>
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      </Route>
      <Route element={<ProtectedRoute />}>
        <Route element={<AccountLayout />}>
          <Route path="/account" element={<AccountPage />} />
          <Route path="/account/security" element={<SecurityPage />} />
          <Route path="/account/filing" element={<MediaAccountFilingPage />} />
          <Route path="/promotion/create" element={<PromotionCreatePage />} />
          <Route
            path="/promotion/links"
            element={<PromotionLinkPage mode="history" />}
          />
          <Route path="/promotion/income" element={<PromotionIncomePage />} />
          <Route
            path="/promotion/tasks"
            element={<Navigate to="/promotion/create" replace />}
          />
        </Route>
      </Route>
      <Route path="/" element={<Navigate to="/account" replace />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
