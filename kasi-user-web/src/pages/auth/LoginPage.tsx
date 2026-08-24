import { useState } from 'react'
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom'
import { Alert, Button, Form } from 'tdesign-react'
import { loginUser } from '../../features/auth/api/authApi'
import { AuthInputField } from '../../features/auth/components/AuthInputField'
import { useAuthStore } from '../../features/auth/model/authStore'
import { isPhoneOrEmail } from '../../features/auth/model/authValidation'
import { ApiError } from '../../shared/api/ApiError'
import { AuthShell } from '../../features/auth/components/AuthShell'

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const setSession = useAuthStore((state) => state.setSession)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const notice = (location.state as { notice?: string } | null)?.notice

  async function handleSubmit(fields: { account: string; password: string }) {
    setError('')

    setSubmitting(true)
    try {
      const response = await loginUser({
        account: fields.account.trim(),
        password: fields.password,
      })
      if (!response) {
        throw new Error('登录响应缺少用户会话')
      }
      setSession({
        accessToken: response.accessToken,
        expiresAt: Date.now() + response.expiresIn * 1000,
      })
      navigate('/account', { replace: true })
    } catch (submissionError) {
      setError(
        submissionError instanceof ApiError
          ? submissionError.message
          : '登录失败，请稍后重试',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell
      title="登录"
      description="使用手机号或邮箱进入你的账户中心"
      footer={
        <span>
          还没有账户？ <RouterLink to="/register">立即注册</RouterLink>
        </span>
      }
    >
      {notice ? <p className="form-notice">{notice}</p> : null}
      {error ? (
        <Alert className="auth-alert" theme="error" message={error} />
      ) : null}
      <Form
        className="auth-form auth-validation-form login-form"
        style={{ gap: '20px' }}
        initialData={{ account: '', password: '' }}
        labelAlign="top"
        requiredMark={false}
        scrollToFirstError="smooth"
        onValuesChange={() => setError('')}
        onSubmit={({ validateResult, fields }) => {
          if (validateResult === true) {
            void handleSubmit(fields as { account: string; password: string })
          }
        }}
      >
        <Form.FormItem
          name="account"
          rules={[
            { required: true, message: '请输入账号', trigger: 'blur' },
            {
              validator: (value) => isPhoneOrEmail(String(value ?? '')),
              message: '请输入正确的手机号或邮箱',
              trigger: 'blur',
            },
          ]}
        >
          <AuthInputField
            label="账号"
            placeholder="手机号或邮箱"
            autocomplete="username"
          />
        </Form.FormItem>
        <Form.FormItem
          name="password"
          rules={[{ required: true, message: '请输入密码', trigger: 'blur' }]}
        >
          <AuthInputField
            label="密码"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </Form.FormItem>
        <div className="form-actions-row">
          <RouterLink to="/forgot-password">忘记密码？</RouterLink>
        </div>
        <Button
          type="submit"
          theme="primary"
          size="large"
          block
          loading={submitting}
        >
          登录
        </Button>
      </Form>
    </AuthShell>
  )
}
