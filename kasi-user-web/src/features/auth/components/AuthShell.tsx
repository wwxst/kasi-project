import type { ReactNode } from 'react'

interface AuthShellProps {
  title: string
  description: string
  children: ReactNode
  footer: ReactNode
}

export function AuthShell({
  title,
  description,
  children,
  footer,
}: AuthShellProps) {
  return (
    <main className="auth-page">
      <section className="auth-brand-panel" aria-label="Kasi 用户中心">
        <div className="brand-mark" aria-hidden="true">
          K
        </div>
        <p className="brand-kicker">KASI PROMOTION</p>
        <h2>把每一次分享，变成可持续的连接。</h2>
        <p className="brand-copy">
          从一个清晰的账户中心开始，管理你的推广身份与安全设置。
        </p>
        <div className="brand-detail" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
      </section>
      <section className="auth-card" aria-labelledby="auth-title">
        <div className="auth-card-header">
          <p className="auth-eyebrow">用户中心</p>
          <h1 id="auth-title">{title}</h1>
          <p>{description}</p>
        </div>
        {children}
        <div className="auth-footer">{footer}</div>
      </section>
    </main>
  )
}
