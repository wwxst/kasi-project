import { Button } from 'tdesign-react'

interface AccountLogoutDialogProps {
  open: boolean
  loading: boolean
  onCancel: () => void
  onConfirm: () => void
}

export function AccountLogoutDialog({
  open,
  loading,
  onCancel,
  onConfirm,
}: AccountLogoutDialogProps) {
  if (!open) return null

  return (
    <div className="dialog-backdrop" role="presentation">
      <section
        className="logout-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="logout-dialog-title"
      >
        <h2 id="logout-dialog-title">退出当前账户？</h2>
        <p>退出后需要重新登录才能访问用户中心。</p>
        <div className="dialog-actions">
          <Button variant="outline" onClick={onCancel} disabled={loading}>
            取消
          </Button>
          <Button theme="primary" loading={loading} onClick={onConfirm}>
            确认退出
          </Button>
        </div>
      </section>
    </div>
  )
}
