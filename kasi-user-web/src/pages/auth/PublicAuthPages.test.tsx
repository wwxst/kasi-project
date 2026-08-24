import { HttpResponse, http } from 'msw'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../../features/auth/model/authStore'
import { server } from '../../test/server'
import { ForgotPasswordPage } from './ForgotPasswordPage'
import { LoginPage } from './LoginPage'
import { RegisterPage } from './RegisterPage'

function renderAuthPage(path: string, element: React.ReactNode) {
  window.history.replaceState({}, '', path)
  return render(
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/account" element={<div>账户概览</div>} />
        {element}
      </Routes>
    </BrowserRouter>,
  )
}

describe('public authentication pages', () => {
  beforeEach(() => {
    useAuthStore.getState().clearSession()
  })

  it('logs in with an account and navigates to the account page', async () => {
    let requestBody: unknown
    server.use(
      http.post('/api/user/auth/login', async ({ request }) => {
        requestBody = await request.json()
        return HttpResponse.json({
          code: 0,
          message: '登录成功',
          data: {
            accessToken: 'login-token',
            tokenType: 'Bearer',
            expiresIn: 7200,
            user: {
              userNo: '123456789012',
              nickname: '测试用户',
              mobile: '13800138000',
              email: null,
              avatarUrl: null,
            },
          },
        })
      }),
    )

    const user = userEvent.setup()
    renderAuthPage('/login', null)
    await user.type(screen.getByLabelText('账号'), '13800138000')
    await user.type(screen.getByLabelText('密码'), 'Password123')
    await user.click(screen.getByRole('button', { name: '登录' }))

    expect(await screen.findByText('账户概览')).toBeInTheDocument()
    expect(requestBody).toEqual({
      account: '13800138000',
      password: 'Password123',
    })
    expect(useAuthStore.getState().accessToken).toBe('login-token')
  })

  it('shows the backend business message when login fails', async () => {
    server.use(
      http.post('/api/user/auth/login', () =>
        HttpResponse.json({ code: 3003, message: '账号或密码错误' }),
      ),
    )

    const user = userEvent.setup()
    renderAuthPage('/login', null)
    await user.type(screen.getByLabelText('账号'), '13800138000')
    await user.type(screen.getByLabelText('密码'), 'wrong-pass')
    await user.click(screen.getByRole('button', { name: '登录' }))

    expect(await screen.findByText('账号或密码错误')).toBeInTheDocument()
    expect(window.location.pathname).toBe('/login')
  })

  it('shows account and password validation in their TDesign form items', async () => {
    const user = userEvent.setup()
    renderAuthPage('/login', null)

    await user.click(screen.getByRole('button', { name: '登录' }))

    const accountError = await screen.findByText('请输入账号')
    const passwordError = await screen.findByText('请输入密码')

    expect(accountError.closest('.t-form__item')).toHaveTextContent('账号')
    expect(passwordError.closest('.t-form__item')).toHaveTextContent('密码')
    expect(screen.queryByText('请输入账号和密码')).not.toBeInTheDocument()
  })

  it('stacks login labels above full-width inputs', () => {
    renderAuthPage('/login', null)

    const accountField = screen
      .getByLabelText('账号')
      .closest<HTMLElement>('.form-field')
    const passwordField = screen
      .getByLabelText('密码')
      .closest<HTMLElement>('.form-field')
    const loginForm = accountField?.closest<HTMLFormElement>('form')
    const accountFormItem = accountField?.closest<HTMLElement>('.t-form__item')
    const passwordFormItem =
      passwordField?.closest<HTMLElement>('.t-form__item')

    expect(accountField).not.toBeNull()
    expect(passwordField).not.toBeNull()
    expect(loginForm).not.toBeNull()
    expect(accountFormItem).not.toBeNull()
    expect(passwordFormItem).not.toBeNull()
    expect(loginForm).toHaveClass('login-form')
    expect(loginForm!.style.gap).toBe('20px')
    expect(accountField!.style.rowGap).toBe('8px')
    expect(passwordField!.style.rowGap).toBe('8px')
    expect(accountField!.style.gridTemplateColumns).toBe('')
    expect(passwordField!.style.gridTemplateColumns).toBe('')
    expect(accountField!.style.alignItems).toBe('')
    expect(passwordField!.style.alignItems).toBe('')
    expect(accountField!.style.columnGap).toBe('')
    expect(passwordField!.style.columnGap).toBe('')
    expect(accountField!.style.width).toBe('100%')
    expect(passwordField!.style.width).toBe('100%')
  })

  it('sends a registration code and registers the user', async () => {
    let registrationBody: unknown
    server.use(
      http.post('/api/user/auth/register/code', () =>
        HttpResponse.json({ code: 0, message: '验证码已发送' }),
      ),
      http.post('/api/user/auth/register', async ({ request }) => {
        registrationBody = await request.json()
        return HttpResponse.json({ code: 0, message: '注册成功' })
      }),
    )

    const user = userEvent.setup()
    renderAuthPage('/register', null)
    await user.type(screen.getByLabelText('手机号或邮箱'), 'user@example.com')
    await user.click(screen.getByRole('button', { name: '获取验证码' }))
    expect(await screen.findByText('验证码已发送')).toBeInTheDocument()
    await user.type(screen.getByLabelText('验证码'), '123456')
    await user.type(screen.getByLabelText('密码'), 'Password123')
    await user.type(screen.getByLabelText('确认密码'), 'Password123')
    await user.click(screen.getByRole('button', { name: '注册' }))

    expect(
      await screen.findByRole('heading', { name: '登录' }),
    ).toBeInTheDocument()
    expect(registrationBody).toEqual({
      account: 'user@example.com',
      verificationCode: '123456',
      password: 'Password123',
      confirmPassword: 'Password123',
    })
  })

  it('shows registration validation in the owning TDesign form items', async () => {
    const user = userEvent.setup()
    renderAuthPage('/register', null)

    await user.click(screen.getByRole('button', { name: '注册' }))

    const accountError = await screen.findByText('请输入手机号或邮箱')
    const codeError = await screen.findByText('请输入验证码')
    const passwordError = await screen.findByText('请输入密码')
    const confirmationError = await screen.findByText('请再次输入密码')

    expect(accountError.closest('.t-form__item')).toHaveTextContent(
      '手机号或邮箱',
    )
    expect(codeError.closest('.t-form__item')).toHaveTextContent('验证码')
    expect(passwordError.closest('.t-form__item')).toHaveTextContent('密码')
    expect(confirmationError.closest('.t-form__item')).toHaveTextContent(
      '确认密码',
    )
    expect(screen.queryByText('请完整填写注册信息')).not.toBeInTheDocument()
    expect(document.querySelector('.form-error')).toBeNull()
  })

  it('shows forgot-password target validation in its TDesign form item', async () => {
    const user = userEvent.setup()
    renderAuthPage('/forgot-password', null)

    await user.click(screen.getByRole('button', { name: '获取验证码' }))

    const targetError = await screen.findByText('请输入手机号或邮箱')
    expect(targetError.closest('.t-form__item')).toHaveTextContent(
      '手机号或邮箱',
    )
    expect(document.querySelector('.form-error')).toBeNull()
  })

  it('shows forgot-password code validation in its TDesign form item', async () => {
    server.use(
      http.post('/api/user/auth/password/forgot/code', () =>
        HttpResponse.json({ code: 0, message: '验证码已发送' }),
      ),
    )

    const user = userEvent.setup()
    renderAuthPage('/forgot-password', null)
    await user.type(screen.getByLabelText('手机号或邮箱'), 'user@example.com')
    await user.click(screen.getByRole('button', { name: '获取验证码' }))
    await user.click(screen.getByRole('button', { name: '验证并继续' }))

    const codeError = await screen.findByText('请输入验证码')
    expect(codeError.closest('.t-form__item')).toHaveTextContent('验证码')
    expect(document.querySelector('.form-error')).toBeNull()
  })

  it('shows forgot-password reset validation in its TDesign form items', async () => {
    server.use(
      http.post('/api/user/auth/password/forgot/code', () =>
        HttpResponse.json({ code: 0, message: '验证码已发送' }),
      ),
      http.post('/api/user/auth/password/forgot/verify', () =>
        HttpResponse.json({
          code: 0,
          message: '验证成功',
          data: { resetToken: 'one-time-token', expiresIn: 600 },
        }),
      ),
    )

    const user = userEvent.setup()
    renderAuthPage('/forgot-password', null)
    await user.type(screen.getByLabelText('手机号或邮箱'), 'user@example.com')
    await user.click(screen.getByRole('button', { name: '获取验证码' }))
    await user.type(screen.getByLabelText('验证码'), '123456')
    await user.click(screen.getByRole('button', { name: '验证并继续' }))
    await user.click(screen.getByRole('button', { name: '重置密码' }))

    const passwordError = await screen.findByText('请输入新密码')
    const confirmationError = await screen.findByText('请再次输入新密码')

    expect(passwordError.closest('.t-form__item')).toHaveTextContent('新密码')
    expect(confirmationError.closest('.t-form__item')).toHaveTextContent(
      '确认密码',
    )
    expect(document.querySelector('.form-error')).toBeNull()
  })

  it('completes forgot-password without persisting the reset token', async () => {
    const requestBodies: unknown[] = []
    server.use(
      http.post('/api/user/auth/password/forgot/code', async ({ request }) => {
        requestBodies.push(await request.json())
        return HttpResponse.json({ code: 0, message: '验证码已发送' })
      }),
      http.post(
        '/api/user/auth/password/forgot/verify',
        async ({ request }) => {
          requestBodies.push(await request.json())
          return HttpResponse.json({
            code: 0,
            message: '验证成功',
            data: { resetToken: 'one-time-token', expiresIn: 600 },
          })
        },
      ),
      http.post('/api/user/auth/password/reset', async ({ request }) => {
        requestBodies.push(await request.json())
        return HttpResponse.json({ code: 0, message: '密码重置成功' })
      }),
    )

    const user = userEvent.setup()
    renderAuthPage('/forgot-password', null)
    await user.type(screen.getByLabelText('手机号或邮箱'), 'user@example.com')
    await user.click(screen.getByRole('button', { name: '获取验证码' }))
    await user.type(screen.getByLabelText('验证码'), '123456')
    await user.click(screen.getByRole('button', { name: '验证并继续' }))
    await user.type(screen.getByLabelText('新密码'), 'NewPassword123')
    await user.type(screen.getByLabelText('确认密码'), 'NewPassword123')
    await user.click(screen.getByRole('button', { name: '重置密码' }))

    expect(
      await screen.findByRole('heading', { name: '登录' }),
    ).toBeInTheDocument()
    expect(requestBodies).toEqual([
      { target: 'user@example.com' },
      { target: 'user@example.com', code: '123456' },
      {
        resetToken: 'one-time-token',
        newPassword: 'NewPassword123',
        confirmPassword: 'NewPassword123',
      },
    ])
    expect(window.location.search).not.toContain('resetToken')
    expect(window.sessionStorage.getItem('resetToken')).toBeNull()
    expect(window.localStorage.getItem('resetToken')).toBeNull()
  })
})
