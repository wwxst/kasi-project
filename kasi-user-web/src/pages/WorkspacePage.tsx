import { useQuery } from '@tanstack/react-query'
import { getCurrentUser } from '../features/auth/authApi'
import { useAuthStore } from '../features/auth/authStore'
import './WorkspacePage.css'

export default function WorkspacePage() {
  const accessToken = useAuthStore((state) => state.accessToken)
  const { data: user } = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: getCurrentUser,
    enabled: Boolean(accessToken),
  })
  const nickname = user?.nickname?.trim() || '用户'

  return (
    <section className="workspace-page">
      <h1 className="workspace-page__welcome">
        欢迎 {nickname} 使用卡司短剧推广平台
      </h1>
    </section>
  )
}
