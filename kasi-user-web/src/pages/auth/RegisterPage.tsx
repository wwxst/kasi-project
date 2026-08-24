import { useEffect, useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import { Alert, Button, Form } from 'tdesign-react'
import {
  registerUser,
  sendRegistrationCode,
} from '../../features/auth/api/authApi'
import {
  AuthInputField,
  VerificationCodeField,
} from '../../features/auth/components/AuthInputField'
import { AuthShell } from '../../features/auth/components/AuthShell'
import {
  isPhoneOrEmail,
  isVerificationCode,
} from '../../features/auth/model/authValidation'
import { ApiError } from '../../shared/api/ApiError'

type RegistrationFields = {
  account: string
  verificationCode: string
  password: string
  confirmPassword: string
}

export function RegisterPage() {
  const navigate = useNavigate()
  const [form] = Form.useForm()
  const [countdown, setCountdown] = useState(0)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [sending, setSending] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (countdown <= 0) return undefined
    const timer = window.setInterval(() => {
      setCountdown((current) => Math.max(0, current - 1))
    }, 1_000)
    return () => window.clearInterval(timer)
  }, [countdown])

  async function handleSendCode() {
    setError('')
    setNotice('')
    const validationResult = await form.validate({ fields: ['account'] })
    if (validationResult !== true) return

    const account = String(form.getFieldValue('account') ?? '').trim()
    setSending(true)
    try {
      await sendRegistrationCode(account)
      setCountdown(60)
      setNotice('验证码已发送')
    } catch (submissionError) {
      setError(
        submissionError instanceof ApiError
          ? submissionError.message
          : '验证码发送失败，请稍后重试',
      )
    } finally {
      setSending(false)
    }
  }

  async function handleSubmit(fields: RegistrationFields) {
    setError('')
    setNotice('')
    setSubmitting(true)
    try {
      await registerUser({
        account: fields.account.trim(),
        verificationCode: fields.verificationCode,
        password: fields.password,
        confirmPassword: fields.confirmPassword,
      })
      navigate('/login', {
        replace: true,
        state: { notice: '注册成功，请登录' },
      })
    } catch (submissionError) {
      setError(
        submissionError instanceof ApiError
          ? submissionError.message
          : '注册失败，请稍后重试',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell
      title="创建账户"
      description="完成注册后即可进入 Kasi 用户中心"
      footer={
        <span>
          已有账户？ <RouterLink to="/login">返回登录</RouterLink>
        </span>
      }
    >
      {notice ? <p className="form-notice">{notice}</p> : null}
      {error ? (
        <Alert className="auth-alert" theme="error" message={error} />
      ) : null}
      <Form
        form={form}
        className="auth-form auth-validation-form"
        initialData={{
          account: '',
          verificationCode: '',
          password: '',
          confirmPassword: '',
        }}
        labelAlign="top"
        requiredMark={false}
        scrollToFirstError="smooth"
        onValuesChange={() => setError('')}
        onSubmit={({ validateResult, fields }) => {
          if (validateResult === true) {
            void handleSubmit(fields as RegistrationFields)
          }
        }}
      >
        <Form.FormItem
          name="account"
          rules={[
            { required: true, message: '请输入手机号或邮箱', trigger: 'blur' },
            {
              validator: (value) => isPhoneOrEmail(String(value ?? '')),
              message: '请输入正确的手机号或邮箱',
              trigger: 'blur',
            },
          ]}
        >
          <AuthInputField
            label="手机号或邮箱"
            placeholder="请输入手机号或邮箱"
            autocomplete="email"
          />
        </Form.FormItem>
        <Form.FormItem
          name="verificationCode"
          valueFormat={(value) =>
            String(value ?? '')
              .replace(/\D/g, '')
              .slice(0, 6)
          }
          rules={[
            { required: true, message: '请输入验证码', trigger: 'blur' },
            {
              validator: (value) => isVerificationCode(String(value ?? '')),
              message: '请输入 6 位数字验证码',
              trigger: 'blur',
            },
          ]}
        >
          <VerificationCodeField
            label="验证码"
            placeholder="6 位数字验证码"
            maxlength={6}
            autocomplete="one-time-code"
            action={
              <Button
                type="button"
                variant="outline"
                loading={sending}
                disabled={countdown > 0}
                onClick={() => void handleSendCode()}
              >
                {countdown > 0 ? `${countdown} 秒后重试` : '获取验证码'}
              </Button>
            }
          />
        </Form.FormItem>
        <Form.FormItem
          name="password"
          rules={[
            { required: true, message: '请输入密码', trigger: 'blur' },
            {
              validator: (value) => String(value ?? '').length >= 8,
              message: '密码长度不能少于 8 位',
              trigger: 'blur',
            },
          ]}
        >
          <AuthInputField
            label="密码"
            type="password"
            placeholder="至少 8 位字符"
            autocomplete="new-password"
          />
        </Form.FormItem>
        <Form.FormItem
          name="confirmPassword"
          rules={[
            { required: true, message: '请再次输入密码', trigger: 'blur' },
            {
              validator: (value) => value === form.getFieldValue('password'),
              message: '两次输入的密码不一致',
              trigger: 'blur',
            },
          ]}
        >
          <AuthInputField
            label="确认密码"
            type="password"
            placeholder="再次输入密码"
            autocomplete="new-password"
          />
        </Form.FormItem>
        <Button
          type="submit"
          theme="primary"
          size="large"
          block
          loading={submitting}
        >
          注册
        </Button>
      </Form>
    </AuthShell>
  )
}
