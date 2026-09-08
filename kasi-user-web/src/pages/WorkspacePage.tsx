import { useQuery } from '@tanstack/react-query'
import type { CSSProperties } from 'react'
import { getCurrentUser } from '../features/auth/authApi'
import { useAuthStore } from '../features/auth/authStore'
import './WorkspacePage.css'

const projects = [
  {
    title: 'TikTok短剧推广',
    tone: 'cpm',
    image: 'https://www.kasi730.com/uploadfile/202606/5b48b997fe49b3d.jpg',
  },
  {
    title: '海外小说推文项目',
    tone: 'novel',
    image: 'https://www.kasi730.com/uploadfile/202606/55ceacdbcc29d2c.jpg',
  },
  {
    title: 'CapCut 拉新项目',
    tone: 'capcut',
    image: 'https://www.kasi730.com/uploadfile/202606/24fc915eb727.jpg',
  },
] as const

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
      <div className="workspace-page__projects" aria-label="项目推荐">
        {projects.map((project) => (
          <article
            className={`project-card project-card--${project.tone}`}
            key={project.title}
            aria-label={project.title}
            style={{ '--card-image': `url(${project.image})` } as CSSProperties}
          >
            <div className="project-card__overlay">
              <button type="button" className="project-card__button">
                项目文档
              </button>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}
