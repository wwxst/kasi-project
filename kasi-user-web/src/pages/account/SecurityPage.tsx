import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Button, Input } from 'tdesign-react'
import { changePassword } from '../../features/account/api/accountApi'
import { useAuthStore } from '../../features/auth/model/authStore'
import { ApiError } from '../../shared/api/ApiError'

export function SecurityPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const clearSession = useAuthStore((state) => state.clearSession)
  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    if (!oldPassword || !newPassword || !confirmPassword) {
      setError('请完整填写密码信息')
      return
    }
    if (newPassword.length < 8) {
      setError('密码长度不能少于 8 位')
      return
    }
    if (newPassword !== confirmPassword) {
      setError('两次输入的密码不一致')
      return
    }

    setSubmitting(true)
    try {
      await changePassword({ oldPassword, newPassword, confirmPassword })
      clearSession()
      queryClient.clear()
      navigate('/login', {
        replace: true,
        state: { notice: '密码修改成功，请重新登录' },
      })
    } catch (submissionError) {
      setError(
        submissionError instanceof ApiError
          ? submissionError.message
          : '密码修改失败，请稍后重试',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="security-page" aria-labelledby="security-page-title">
      <div className="page-heading">
        <div>
          <p className="page-eyebrow">SECURITY</p>
          <h1 id="security-page-title">安全设置</h1>
          <p>修改密码后，其他设备上的登录状态也会失效。</p>
        </div>
      </div>
      {error ? (
        <p className="form-error" role="alert">
          {error}
        </p>
      ) : null}
      <article className="account-panel security-panel">
        <div className="panel-heading">
          <div>
            <h2>修改登录密码</h2>
            <p>请使用至少 8 位的新密码。</p>
          </div>
        </div>
        <form className="security-form" onSubmit={handleSubmit} noValidate>
          <label className="form-field">
            <span>原密码</span>
            <Input
              type="password"
              value={oldPassword}
              onChange={setOldPassword}
              autocomplete="current-password"
            />
          </label>
          <label className="form-field">
            <span>新密码</span>
            <Input
              type="password"
              value={newPassword}
              onChange={setNewPassword}
              autocomplete="new-password"
            />
          </label>
          <label className="form-field">
            <span>确认密码</span>
            <Input
              type="password"
              value={confirmPassword}
              onChange={setConfirmPassword}
              autocomplete="new-password"
            />
          </label>
          <Button type="submit" theme="primary" loading={submitting}>
            更新密码
          </Button>
        </form>
      </article>
    </section>
  )
}
