import { useQuery } from '@tanstack/react-query'
import type { CSSProperties } from 'react'
import { getCurrentUser } from '../features/auth/authApi'
import { useAuthStore } from '../features/auth/authStore'
import { getProjects } from '../features/projects/projectApi'
import './WorkspacePage.css'

export default function WorkspacePage() {
  const accessToken = useAuthStore((state) => state.accessToken)
  const { data: user } = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: getCurrentUser,
    enabled: Boolean(accessToken),
  })
  const projectsQuery = useQuery({
    queryKey: ['promotion-projects'],
    queryFn: getProjects,
    enabled: Boolean(accessToken),
  })
  const nickname = user?.nickname?.trim() || '用户'

  return (
    <section className="workspace-page">
      <h1 className="workspace-page__welcome">
        欢迎 {nickname} 使用卡司短剧推广平台
      </h1>
      <div
        className="workspace-page__projects"
        aria-label="项目推荐"
        role="region"
      >
        {projectsQuery.isPending ? (
          <p className="workspace-page__state" role="status">
            项目加载中...
          </p>
        ) : projectsQuery.isError ? (
          <div className="workspace-page__state" role="alert">
            <p>项目加载失败</p>
            <button type="button" onClick={() => void projectsQuery.refetch()}>
              重试
            </button>
          </div>
        ) : projectsQuery.data?.length ? (
          projectsQuery.data.map((project) => (
            <article
              className="project-card"
              key={project.id}
              aria-label={project.name}
              style={
                {
                  '--card-image': `url(${project.coverImageUrl})`,
                } as CSSProperties
              }
            >
              <div className="project-card__overlay">
                <a
                  className="project-card__button"
                  href={project.projectDocumentUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  项目文档
                </a>
              </div>
            </article>
          ))
        ) : (
          <p className="workspace-page__state">暂无项目</p>
        )}
      </div>
    </section>
  )
}
