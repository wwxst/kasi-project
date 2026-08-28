import './WorkspacePage.css'

export default function WorkspacePage({ title }: { title: string }) {
  return (
    <section className="workspace-page">
      <h1>{title}</h1>
      <div className="workspace-page__surface">
        <p>当前页面已接入用户工作区布局，业务数据将在对应功能接入后展示。</p>
      </div>
    </section>
  )
}
