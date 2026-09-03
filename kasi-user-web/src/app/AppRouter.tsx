import { Navigate, Route, Routes } from 'react-router-dom'
import LoginPage from '../pages/LoginPage'
import AppShell from '../layout/AppShell'
import { useAuthStore } from '../features/auth/authStore'
import { appRoutes } from './routes'

function ProtectedWorkspace() {
  const accessToken = useAuthStore((store) => store.accessToken)

  return accessToken ? <AppShell /> : <Navigate to="/login" replace />
}

export default function AppRouter() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedWorkspace />}>
        {appRoutes.map(({ path, title, element: Component }) => (
          <Route key={path} path={path} element={<Component title={title} />} />
        ))}
      </Route>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="*" element={<Navigate to="/workspace" replace />} />
    </Routes>
  )
}
