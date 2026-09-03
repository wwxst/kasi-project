import { useEffect, useRef, useState } from 'react'
import { Button, Checkbox, Form, Input, QRCode } from 'tdesign-react'
import type { FormInstanceFunctions, SubmitContext } from 'tdesign-react'
import {
  BrowseIcon,
  BrowseOffIcon,
  LockOnIcon,
  RefreshIcon,
  UserIcon,
} from 'tdesign-icons-react'
import logoUrl from '../assets/image/kasi-brand-logo.png'
import { useNavigate } from 'react-router-dom'
import { loginUser } from '../features/auth/authApi'
import { useAuthStore } from '../features/auth/authStore'
import { isHandledRequestError } from '../shared/api/httpClient'
import './login.css'

type LoginType = 'password' | 'phone' | 'qrcode'

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
}: {
  showPassword: boolean
  onTogglePassword: () => void
}) {
  return (
    <>
      <Form.FormItem
        name="account"
        rules={[{ required: true, message: '账号必填', type: 'error' }]}
      >
        <Input
          size="large"
          placeholder="请输入手机号或者邮箱"
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
        <button type="button" className="starter-login-link">
          忘记密码？
        </button>
      </div>
    </>
  )
}

function LoginForm() {
  const [loginType, setLoginType] = useState<LoginType>('password')
  const [showPassword, setShowPassword] = useState(false)
  const { countdown, setupCountdown } = useCountDown(60)
  const formRef = useRef<FormInstanceFunctions | null>(null)
  const navigate = useNavigate()
  const setSession = useAuthStore((store) => store.setSession)
  const [loginError, setLoginError] = useState<string | null>(null)

  const switchType = (nextType: LoginType) => {
    formRef.current?.reset?.()
    setLoginType(nextType)
  }

  const submit = async (event: SubmitContext) => {
    if (event.validateResult === true) {
      if (loginType !== 'password') return
      setLoginError(null)
      try {
        const account = String(event.fields?.account ?? '')
        const password = String(event.fields?.password ?? '')
        const result = await loginUser(account, password)
        setSession(result.accessToken)
        navigate('/workspace')
      } catch (error) {
        if (!isHandledRequestError(error)) {
          setLoginError(error instanceof Error ? error.message : '登录失败')
        }
      }
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
        />
      ) : null}

      {loginType === 'qrcode' ? (
        <>
          <div className="starter-login-qr-tip">
            <span>请使用微信扫一扫登录</span>
            <button
              type="button"
              className="starter-login-link starter-login-refresh"
            >
              刷新 <RefreshIcon />
            </button>
          </div>
          <QRCode value="https://tdesign.tencent.com/" size={200} />
        </>
      ) : null}

      {loginType === 'phone' ? (
        <>
          <Form.FormItem
            name="phone"
            rules={[{ required: true, message: '手机号必填', type: 'error' }]}
          >
            <Input
              maxlength={11}
              size="large"
              placeholder="请输入您的手机号或者邮箱"
              prefixIcon={<UserIcon />}
            />
          </Form.FormItem>
          <Form.FormItem
            name="verifyCode"
            rules={[{ required: true, message: '验证码必填', type: 'error' }]}
          >
            <Input size="large" placeholder="请输入验证码" />
            <Button
              variant="outline"
              className="starter-login-verification"
              disabled={countdown > 0}
              onClick={setupCountdown}
            >
              {countdown === 0 ? '发送验证码' : `${countdown}秒后可重发`}
            </Button>
          </Form.FormItem>
        </>
      ) : null}

      {loginError ? (
        <div className="starter-login-error" role="alert">
          {loginError}
        </div>
      ) : null}

      {loginType !== 'qrcode' ? (
        <Form.FormItem className="starter-login-submit">
          <Button block size="large" type="submit">
            登录
          </Button>
        </Form.FormItem>
      ) : null}

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
        {loginType !== 'qrcode' ? (
          <button
            type="button"
            className="starter-login-link"
            onClick={() => switchType('qrcode')}
          >
            使用微信扫码登录
          </button>
        ) : null}
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

function RegisterForm() {
  const [showPassword, setShowPassword] = useState(false)
  const { countdown, setupCountdown } = useCountDown(60)
  const navigate = useNavigate()

  const submit = (event: SubmitContext) => {
    if (event.validateResult === true) navigate('/workspace')
  }

  return (
    <Form
      className="starter-login-form starter-register-form"
      labelWidth={0}
      onSubmit={submit}
    >
      <Form.FormItem
        name="account"
        rules={[
          { required: true, message: '请输入手机号或邮箱', type: 'error' },
        ]}
      >
        <Input
          size="large"
          placeholder="请输入手机号或邮箱"
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
        name="verifyCode"
        rules={[{ required: true, message: '验证码必填', type: 'error' }]}
      >
        <Input size="large" placeholder="请输入验证码" />
        <Button
          variant="outline"
          className="starter-login-verification"
          disabled={countdown > 0}
          onClick={setupCountdown}
        >
          {countdown === 0 ? '发送验证码' : `${countdown}秒后可重发`}
        </Button>
      </Form.FormItem>
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
      <Form.FormItem>
        <Button block size="large" type="submit">
          注册
        </Button>
      </Form.FormItem>
    </Form>
  )
}

export default function LoginPage() {
  const [mode, setMode] = useState<'login' | 'register'>('login')

  return (
    <main className="starter-login-page light">
      <LoginHeader />
      <div className="starter-login-container">
        <div className="starter-login-title-container">
          <h1>登录到</h1>
          <h1>卡司短剧推广平台</h1>
          <div className="starter-login-subtitle">
            <p>{mode === 'register' ? '已有账号？' : '没有账号吗？'}</p>
            <button
              type="button"
              className="starter-login-link"
              onClick={() =>
                setMode((current) =>
                  current === 'login' ? 'register' : 'login',
                )
              }
            >
              {mode === 'register' ? '登录' : '注册新账号'}
            </button>
          </div>
        </div>
        {mode === 'login' ? <LoginForm /> : <RegisterForm />}
      </div>
      <footer className="starter-login-copyright">
        Copyright © {new Date().getFullYear()} 卡司短剧推广平台
      </footer>
    </main>
  )
}
