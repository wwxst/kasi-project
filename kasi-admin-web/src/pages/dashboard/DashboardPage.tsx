import { useAuthStore } from '../../features/auth/authStore'
import './dashboard-page.css'

export function DashboardPage() {
  const realName = useAuthStore((state) => state.admin?.realName || '管理员')

  return (
    <div className="analysis-page">
      <h1 className="analysis-page__welcome">
        欢迎 {realName} 使用卡司短剧推广平台
      </h1>
    </div>
  )
}
