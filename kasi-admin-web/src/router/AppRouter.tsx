import { lazy, Suspense } from 'react'
import {
  BrowserRouter,
  Navigate,
  Outlet,
  Route,
  Routes,
} from 'react-router-dom'
import { Spin } from 'antd'
import { useAuthStore } from '../features/auth/authStore'
import { AdminLayout } from '../layouts/AdminLayout'

const DashboardPage = lazy(() =>
  import('../pages/dashboard/DashboardPage').then((module) => ({
    default: module.DashboardPage,
  })),
)
const LoginPage = lazy(() =>
  import('../pages/login/LoginPage').then((module) => ({
    default: module.LoginPage,
  })),
)
const ProfilePage = lazy(() =>
  import('../pages/profile/ProfilePage').then((module) => ({
    default: module.ProfilePage,
  })),
)
const AdminManagementPage = lazy(() =>
  import('../pages/management/AdminManagementPage').then((module) => ({
    default: module.AdminManagementPage,
  })),
)
const UserManagementPage = lazy(() =>
  import('../pages/management/UserManagementPage').then((module) => ({
    default: module.UserManagementPage,
  })),
)
const ProviderManagementPage = lazy(() =>
  import('../pages/provider/ProviderManagementPage').then((module) => ({
    default: module.ProviderManagementPage,
  })),
)
const MediaAccountFilingPage = lazy(() =>
  import('../pages/promotion/MediaAccountFilingPage').then((module) => ({
    default: module.MediaAccountFilingPage,
  })),
)
const PromotionOrderPage = lazy(() =>
  import('../pages/promotion/PromotionOrderPage').then((module) => ({
    default: module.PromotionOrderPage,
  })),
)
const DramaCatalogPage = lazy(() =>
  import('../pages/drama/DramaCatalogPage').then((module) => ({
    default: module.DramaCatalogPage,
  })),
)
const ScheduledTaskPage = lazy(() =>
  import('../pages/system/ScheduledTaskPage').then((module) => ({
    default: module.ScheduledTaskPage,
  })),
)
const CommissionRulePage = lazy(() =>
  import('../pages/system/CommissionRulePage').then((module) => ({
    default: module.CommissionRulePage,
  })),
)

function ProtectedRoute() {
  const accessToken = useAuthStore((state) => state.accessToken)
  return accessToken ? <Outlet /> : <Navigate to="/login" replace />
}

function SuperAdminRoute() {
  const admin = useAuthStore((state) => state.admin)
  return admin?.isSuperAdmin === 1 ? (
    <Outlet />
  ) : (
    <Navigate to="/user-management" replace />
  )
}

export function AppRouter() {
  return (
    <BrowserRouter>
      <Suspense
        fallback={
          <div className="route-loading" role="status" aria-label="页面加载中">
            <Spin size="large" />
          </div>
        }
      >
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AdminLayout />}>
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="/user-management" element={<UserManagementPage />} />
              <Route
                path="/promotion/media-accounts"
                element={<MediaAccountFilingPage />}
              />
              <Route
                path="/promotion/orders"
                element={<PromotionOrderPage />}
              />
              <Route path="/drama/catalog" element={<DramaCatalogPage />} />
              <Route
                path="/system-config/drama-api"
                element={<ProviderManagementPage />}
              />
              <Route
                path="/system-config/scheduled-tasks"
                element={<ScheduledTaskPage />}
              />
              <Route
                path="/system-config/commission-rules"
                element={<CommissionRulePage />}
              />
              <Route
                path="/provider-management"
                element={<Navigate to="/system-config/drama-api" replace />}
              />
              <Route element={<SuperAdminRoute />}>
                <Route
                  path="/admin-management"
                  element={<AdminManagementPage />}
                />
              </Route>
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/user-management" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  )
}
