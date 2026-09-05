import { useEffect, useRef, useState } from 'react'
import { Button, Checkbox, Form, Input, MessagePlugin } from 'tdesign-react'
import type { FormInstanceFunctions, SubmitContext } from 'tdesign-react'
import {
  BrowseIcon,
  BrowseOffIcon,
  LockOnIcon,
  UserIcon,
} from 'tdesign-icons-react'
import logoUrl from '../assets/image/kasi-brand-logo.png'
import { useNavigate } from 'react-router-dom'
import {
  loginUser,
  loginUserWithCode,
  registerUser,
  resetPassword,
  sendForgotPasswordCode,
  sendLoginCode,
  sendRegisterCode,
  verifyForgotPasswordCode,
} from '../features/auth/authApi'
import { useAuthStore } from '../features/auth/authStore'
import { isHandledRequestError } from '../shared/api/httpClient'
import './login.css'

type LoginType = 'password' | 'phone'
type PageMode = 'login' | 'register' | 'forgot-password'

const mobileRules = [
  { required: true, message: '手机号必填', type: 'error' as const },
  {
    validator: (value: unknown) => /^1\d{10}$/.test(String(value ?? '')),
    message: '请输入正确的11位手机号',
    type: 'error' as const,
  },
]

const verificationCodeRules = [
  { required: true, message: '验证码必填', type: 'error' as const },
  {
    validator: (value: unknown) => /^\d{6}$/.test(String(value ?? '')),
    message: '请输入6位验证码',
    type: 'error' as const,
  },
]

const passwordRules = [
  { required: true, message: '密码必填', type: 'error' as const },
  { min: 8, message: '密码长度不能少于8位', type: 'error' as const },
]

function getRequestError(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

function useCountDown(duration: number) {
  const [countdown, setCountdown] = useState(0)

  useEffect(() => {
    if (countdown <= 0) return undefined
    const timer = window.setInterval(() => {
      setCountdown((current) => Math.max(0, current - 1))
    }, 1000)
    return () => window.clearInterval(timer)
  }, [countdown])

  return {
    countdown,
    setupCountdown: () => setCountdown(duration),
  }
}

function LoginHeader() {
  return (
    <header className="starter-login-header">
      <div className="starter-login-brand">
        <img
          className="starter-login-logo"
          src={logoUrl}
          alt="卡司短剧推广平台"
        />
        <strong>卡司短剧推广平台</strong>
      </div>
    </header>
  )
}

function PasswordFields({
  showPassword,
  onTogglePassword,
  onForgotPassword,
}: {
  showPassword: boolean
  onTogglePassword: () => void
  onForgotPassword: () => void
}) {
  return (
    <>
      <Form.FormItem name="account" rules={mobileRules}>
        <Input
          size="large"
          maxlength={11}
          placeholder="请输入手机号"
          prefixIcon={<UserIcon />}
        />
      </Form.FormItem>
      <Form.FormItem
        name="password"
        rules={[{ required: true, message: '密码必填', type: 'error' }]}
      >
        <Input
          size="large"
          type={showPassword ? 'text' : 'password'}
          clearable
          placeholder="请输入登录密码"
          prefixIcon={<LockOnIcon />}
          suffixIcon={
            showPassword ? (
              <BrowseIcon onClick={onTogglePassword} />
            ) : (
              <BrowseOffIcon onClick={onTogglePassword} />
            )
          }
        />
      </Form.FormItem>
      <div className="starter-login-check starter-login-remember">
        <Checkbox>记住账号</Checkbox>
        <button
          type="button"
          className="starter-login-link"
          onClick={onForgotPassword}
        >
          忘记密码？
        </button>
      </div>
    </>
  )
}

function VerificationCodeField({
  countdown,
  sending,
  onSend,
}: {
  countdown: number
  sending: boolean
  onSend: () => void
}) {
  return (
    <Form.FormItem name="verificationCode" rules={verificationCodeRules}>
      <Input maxlength={6} size="large" placeholder="请输入验证码" />
      <Button
        type="button"
        variant="outline"
        className="starter-login-verification"
        loading={sending}
        disabled={countdown > 0}
        onClick={onSend}
      >
        {countdown === 0 ? '发送验证码' : `${countdown}秒后可重发`}
      </Button>
    </Form.FormItem>
  )
}

function LoginForm({ onForgotPassword }: { onForgotPassword: () => void }) {
  const [loginType, setLoginType] = useState<LoginType>('password')
  const [showPassword, setShowPassword] = useState(false)
  const { countdown, setupCountdown } = useCountDown(60)
  const formRef = useRef<FormInstanceFunctions | null>(null)
  const navigate = useNavigate()
  const setSession = useAuthStore((store) => store.setSession)
  const [loginError, setLoginError] = useState<string | null>(null)
  const [sendingCode, setSendingCode] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const switchType = (nextType: LoginType) => {
    formRef.current?.reset?.()
    setLoginType(nextType)
  }

  const submit = async (event: SubmitContext) => {
    if (event.validateResult === true) {
      setLoginError(null)
      setSubmitting(true)
      try {
        const result =
          loginType === 'password'
            ? await loginUser(
                String(event.fields?.account ?? '').trim(),
                String(event.fields?.password ?? ''),
              )
            : await loginUserWithCode(
                String(event.fields?.phone ?? '').trim(),
                String(event.fields?.verificationCode ?? '').trim(),
              )
        setSession(result.accessToken)
        navigate('/workspace')
      } catch (error) {
        if (!isHandledRequestError(error)) {
          setLoginError(getRequestError(error, '登录失败'))
        }
      } finally {
        setSubmitting(false)
      }
    }
  }

  const sendVerificationCode = async () => {
    const validateResult = await formRef.current?.validate({
      fields: ['phone'],
    })
    if (validateResult !== true) return
    const phone = String(formRef.current?.getFieldValue('phone') ?? '').trim()
    setLoginError(null)
    setSendingCode(true)
    try {
      await sendLoginCode(phone)
      setupCountdown()
      void MessagePlugin.success('验证码已发送')
    } catch (error) {
      if (!isHandledRequestError(error)) {
        setLoginError(getRequestError(error, '验证码发送失败'))
      }
    } finally {
      setSendingCode(false)
    }
  }

  return (
    <Form
      ref={formRef}
      className={`starter-login-form starter-login-form-${loginType}`}
      labelWidth={0}
      onSubmit={submit}
    >
      {loginType === 'password' ? (
        <PasswordFields
          showPassword={showPassword}
          onTogglePassword={() => setShowPassword((current) => !current)}
          onForgotPassword={onForgotPassword}
        />
      ) : null}

      {loginType === 'phone' ? (
        <>
          <Form.FormItem name="phone" rules={mobileRules}>
            <Input
              maxlength={11}
              size="large"
              placeholder="请输入手机号"
              prefixIcon={<UserIcon />}
            />
          </Form.FormItem>
          <VerificationCodeField
            countdown={countdown}
            sending={sendingCode}
            onSend={() => void sendVerificationCode()}
          />
        </>
      ) : null}

      {loginError ? (
        <div className="starter-login-error" role="alert">
          {loginError}
        </div>
      ) : null}

      <Form.FormItem className="starter-login-submit">
        <Button block size="large" type="submit" loading={submitting}>
          登录
        </Button>
      </Form.FormItem>

      <div className="starter-login-switches">
        {loginType !== 'password' ? (
          <button
            type="button"
            className="starter-login-link"
            onClick={() => switchType('password')}
          >
            使用账号密码登录
          </button>
        ) : null}
        <span className="starter-login-unavailable">微信登录暂未开放</span>
        {loginType !== 'phone' ? (
          <button
            type="button"
            className="starter-login-link"
            onClick={() => switchType('phone')}
          >
            使用验证码登录
          </button>
        ) : null}
      </div>
    </Form>
  )
}

function RegisterForm({ onSuccess }: { onSuccess: () => void }) {
  const [showPassword, setShowPassword] = useState(false)
  const { countdown, setupCountdown } = useCountDown(60)
  const formRef = useRef<FormInstanceFunctions | null>(null)
  const [sendingCode, setSendingCode] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [registerError, setRegisterError] = useState<string | null>(null)

  const sendVerificationCode = async () => {
    const validateResult = await formRef.current?.validate({
      fields: ['account'],
    })
    if (validateResult !== true) return
    const phone = String(formRef.current?.getFieldValue('account') ?? '').trim()
    setRegisterError(null)
    setSendingCode(true)
    try {
      await sendRegisterCode(phone)
      setupCountdown()
      void MessagePlugin.success('验证码已发送')
    } catch (error) {
      if (!isHandledRequestError(error)) {
        setRegisterError(getRequestError(error, '验证码发送失败'))
      }
    } finally {
      setSendingCode(false)
    }
  }

  const submit = async (event: SubmitContext) => {
    if (event.validateResult !== true) return
    setRegisterError(null)
    setSubmitting(true)
    try {
      await registerUser({
        account: String(event.fields?.account ?? '').trim(),
        verificationCode: String(event.fields?.verificationCode ?? '').trim(),
        password: String(event.fields?.password ?? ''),
        confirmPassword: String(event.fields?.confirmPassword ?? ''),
      })
      onSuccess()
      void MessagePlugin.success('注册成功，请登录')
    } catch (error) {
      if (!isHandledRequestError(error)) {
        setRegisterError(getRequestError(error, '注册失败'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Form
      ref={formRef}
      className="starter-login-form starter-register-form"
      labelWidth={0}
      onSubmit={submit}
    >
      <Form.FormItem name="account" rules={mobileRules}>
        <Input
          maxlength={11}
          size="large"
          placeholder="请输入手机号"
          prefixIcon={<UserIcon />}
        />
      </Form.FormItem>
      <Form.FormItem name="password" rules={passwordRules}>
        <Input
          size="large"
          type={showPassword ? 'text' : 'password'}
          clearable
          placeholder="请输入登录密码"
          prefixIcon={<LockOnIcon />}
          suffixIcon={
            showPassword ? (
              <BrowseIcon
                onClick={() => setShowPassword((current) => !current)}
              />
            ) : (
              <BrowseOffIcon
                onClick={() => setShowPassword((current) => !current)}
              />
            )
          }
        />
      </Form.FormItem>
      <Form.FormItem
        name="confirmPassword"
        rules={[
          { required: true, message: '请再次输入密码', type: 'error' },
          {
            validator: (value) =>
              value === formRef.current?.getFieldValue('password'),
            message: '两次输入的密码不一致',
            type: 'error',
          },
        ]}
      >
        <Input
          size="large"
          type={showPassword ? 'text' : 'password'}
          clearable
          placeholder="请再次输入登录密码"
          prefixIcon={<LockOnIcon />}
        />
      </Form.FormItem>
      <VerificationCodeField
        countdown={countdown}
        sending={sendingCode}
        onSend={() => void sendVerificationCode()}
      />
      <Form.FormItem
        className="starter-register-agreement"
        name="checked"
        initialData={false}
      >
        <Checkbox>我已阅读并同意</Checkbox>{' '}
        <button type="button" className="starter-login-link">
          卡司服务协议
        </button>{' '}
        和{' '}
        <button type="button" className="starter-login-link">
          卡司隐私声明
        </button>
      </Form.FormItem>
      {registerError ? (
        <div className="starter-login-error" role="alert">
          {registerError}
        </div>
      ) : null}
      <Form.FormItem>
        <Button block size="large" type="submit" loading={submitting}>
          注册
        </Button>
      </Form.FormItem>
    </Form>
  )
}

function ForgotPasswordForm({
  onBack,
  onSuccess,
}: {
  onBack: () => void
  onSuccess: () => void
}) {
  const formRef = useRef<FormInstanceFunctions | null>(null)
  const { countdown, setupCountdown } = useCountDown(60)
  const [resetToken, setResetToken] = useState<string | null>(null)
  const [showPassword, setShowPassword] = useState(false)
  const [sendingCode, setSendingCode] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [resetError, setResetError] = useState<string | null>(null)

  const sendVerificationCode = async () => {
    const validateResult = await formRef.current?.validate({
      fields: ['target'],
    })
    if (validateResult !== true) return
    const phone = String(formRef.current?.getFieldValue('target') ?? '').trim()
    setResetError(null)
    setSendingCode(true)
    try {
      await sendForgotPasswordCode(phone)
      setupCountdown()
      void MessagePlugin.success('验证码已发送')
    } catch (error) {
      if (!isHandledRequestError(error)) {
        setResetError(getRequestError(error, '验证码发送失败'))
      }
    } finally {
      setSendingCode(false)
    }
  }

  const submit = async (event: SubmitContext) => {
    if (event.validateResult !== true) return
    setResetError(null)
    setSubmitting(true)
    try {
      if (!resetToken) {
        const result = await verifyForgotPasswordCode(
          String(event.fields?.target ?? '').trim(),
          String(event.fields?.verificationCode ?? '').trim(),
        )
        setResetToken(result.resetToken)
        return
      }
      await resetPassword(
        resetToken,
        String(event.fields?.newPassword ?? ''),
        String(event.fields?.confirmPassword ?? ''),
      )
      onSuccess()
      void MessagePlugin.success('密码重置成功，请重新登录')
    } catch (error) {
      if (!isHandledRequestError(error)) {
        setResetError(getRequestError(error, '密码重置失败'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Form
      ref={formRef}
      className="starter-login-form starter-forgot-password-form"
      labelWidth={0}
      onSubmit={submit}
    >
      {!resetToken ? (
        <>
          <Form.FormItem name="target" rules={mobileRules}>
            <Input
              maxlength={11}
              size="large"
              placeholder="请输入手机号"
              prefixIcon={<UserIcon />}
            />
          </Form.FormItem>
          <VerificationCodeField
            countdown={countdown}
            sending={sendingCode}
            onSend={() => void sendVerificationCode()}
          />
        </>
      ) : (
        <>
          <Form.FormItem name="newPassword" rules={passwordRules}>
            <Input
              size="large"
              type={showPassword ? 'text' : 'password'}
              clearable
              autocomplete="new-password"
              placeholder="请输入新密码"
              prefixIcon={<LockOnIcon />}
              suffixIcon={
                showPassword ? (
                  <BrowseIcon
                    onClick={() => setShowPassword((current) => !current)}
                  />
                ) : (
                  <BrowseOffIcon
                    onClick={() => setShowPassword((current) => !current)}
                  />
                )
              }
            />
          </Form.FormItem>
          <Form.FormItem
            name="confirmPassword"
            rules={[
              { required: true, message: '请再次输入新密码', type: 'error' },
              {
                validator: (value) =>
                  value === formRef.current?.getFieldValue('newPassword'),
                message: '两次输入的新密码不一致',
                type: 'error',
              },
            ]}
          >
            <Input
              size="large"
              type={showPassword ? 'text' : 'password'}
              clearable
              autocomplete="new-password"
              placeholder="请再次输入新密码"
              prefixIcon={<LockOnIcon />}
            />
          </Form.FormItem>
        </>
      )}

      {resetError ? (
        <div className="starter-login-error" role="alert">
          {resetError}
        </div>
      ) : null}

      <Form.FormItem className="starter-login-submit starter-forgot-password-submit">
        <Button block size="large" type="submit" loading={submitting}>
          {resetToken ? '重置密码' : '下一步'}
        </Button>
      </Form.FormItem>

      <div className="starter-login-switches">
        <button type="button" className="starter-login-link" onClick={onBack}>
          返回登录
        </button>
      </div>
    </Form>
  )
}

export default function LoginPage() {
  const [mode, setMode] = useState<PageMode>('login')

  return (
    <main className="starter-login-page light">
      <LoginHeader />
      <div className="starter-login-container">
        <div className="starter-login-title-container">
          <h1>{mode === 'forgot-password' ? '找回密码' : '登录到'}</h1>
          <h1>卡司短剧推广平台</h1>
          <div className="starter-login-subtitle">
            <p>
              {mode === 'register'
                ? '已有账号？'
                : mode === 'forgot-password'
                  ? '想起密码了？'
                  : '没有账号吗？'}
            </p>
            <button
              type="button"
              className="starter-login-link"
              onClick={() => setMode(mode === 'login' ? 'register' : 'login')}
            >
              {mode === 'login' ? '注册新账号' : '登录'}
            </button>
          </div>
        </div>
        {mode === 'login' ? (
          <LoginForm onForgotPassword={() => setMode('forgot-password')} />
        ) : mode === 'register' ? (
          <RegisterForm onSuccess={() => setMode('login')} />
        ) : (
          <ForgotPasswordForm
            onBack={() => setMode('login')}
            onSuccess={() => setMode('login')}
          />
        )}
      </div>
      <footer className="starter-login-copyright">
        Copyright © {new Date().getFullYear()} 卡司短剧推广平台
      </footer>
    </main>
  )
}
