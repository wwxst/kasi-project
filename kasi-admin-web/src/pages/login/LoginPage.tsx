import { useMutation } from '@tanstack/react-query'
import { App, Button, Form, Input } from 'antd'
import { ArrowRight, LockKeyhole, PanelsTopLeft } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { loginAdmin } from '../../features/auth/authApi'
import { useAuthStore } from '../../features/auth/authStore'
import type { AdminLoginRequest } from '../../features/auth/authTypes'
import './login-page.css'

export function LoginPage() {
  const navigate = useNavigate()
  const { message } = App.useApp()
  const setSession = useAuthStore((state) => state.setSession)
  const loginMutation = useMutation({
    mutationFn: loginAdmin,
    onSuccess: (session) => {
      setSession(session)
      navigate('/dashboard', { replace: true })
    },
    onError: (error) => {
      void message.error(
        error instanceof Error ? error.message : '登录失败，请稍后重试',
      )
    },
  })

  return (
    <main className="login-page">
      <div className="login-brand" aria-label="Kasi">
        <span className="login-brand__mark" aria-hidden="true">
          <PanelsTopLeft size={20} strokeWidth={2} />
        </span>
        <span>Kasi</span>
      </div>

      <section className="login-panel" aria-labelledby="login-title">
        <div className="login-panel__icon" aria-hidden="true">
          <LockKeyhole size={22} strokeWidth={1.8} />
        </div>
        <div className="login-panel__heading">
          <h1 id="login-title">Kasi 管理后台</h1>
          <p>运营管理中心</p>
        </div>

        <Form<AdminLoginRequest>
          layout="vertical"
          requiredMark={false}
          size="large"
          onFinish={(values) => loginMutation.mutate(values)}
        >
          <Form.Item
            label="登录账号"
            name="account"
            rules={[{ required: true, message: '请输入登录账号' }]}
          >
            <Input autoComplete="username" placeholder="账号、手机号或邮箱" />
          </Form.Item>
          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              autoComplete="current-password"
              placeholder="请输入密码"
            />
          </Form.Item>
          <Button
            type="primary"
            htmlType="submit"
            block
            loading={loginMutation.isPending}
            icon={<ArrowRight size={18} />}
          >
            登录
          </Button>
        </Form>
      </section>

      <p className="login-footer">Kasi Promotion Platform</p>
    </main>
  )
}
