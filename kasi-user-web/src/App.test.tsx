import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { queryClient } from './app/queryClient'
import {
  getCurrentUser,
  loginUser,
  loginUserWithCode,
  registerUser,
  resetPassword,
  sendForgotPasswordCode,
  sendLoginCode,
  sendRegisterCode,
  verifyForgotPasswordCode,
} from './features/auth/authApi'
import { useAuthStore } from './features/auth/authStore'

vi.mock('./features/auth/authApi', () => ({
  getCurrentUser: vi.fn(),
  loginUser: vi.fn(),
  loginUserWithCode: vi.fn(),
  registerUser: vi.fn(),
  resetPassword: vi.fn(),
  sendForgotPasswordCode: vi.fn(),
  sendLoginCode: vi.fn(),
  sendRegisterCode: vi.fn(),
  verifyForgotPasswordCode: vi.fn(),
}))

vi.mock('./features/mediaAccounts/mediaAccountsApi', () => ({
  getMediaAccounts: vi.fn(),
}))

afterEach(() => {
  cleanup()
  window.history.replaceState({}, '', '/')
  useAuthStore.getState().clearSession()
  queryClient.clear()
  vi.clearAllMocks()
})

describe('App', () => {
  it('renders the Kasi short drama promotion brand', async () => {
    render(<App />)

    expect(await screen.findAllByText('卡司短剧推广平台')).not.toHaveLength(0)
    expect(await screen.findByAltText('卡司短剧推广平台')).toBeTruthy()
    expect(screen.queryByText('TDesign Starter')).toBeNull()
    expect(await screen.findByRole('heading', { name: '登录到' })).toBeTruthy()
    expect(
      await screen.findByPlaceholderText('请输入手机号或者邮箱'),
    ).toBeTruthy()
    expect(await screen.findByPlaceholderText('请输入登录密码')).toBeTruthy()
    expect(await screen.findByRole('button', { name: '登录' })).toBeTruthy()
    expect(
      await screen.findByRole('button', { name: '忘记密码？' }),
    ).toBeTruthy()
    expect(await screen.findByText('微信登录暂未开放')).toBeTruthy()
    expect(
      screen.queryByRole('button', { name: '使用微信扫码登录' }),
    ).toBeNull()
    expect(await screen.findByText('使用验证码登录')).toBeTruthy()
  })

  it('uses one contact field for registration', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.click(screen.getByRole('button', { name: '注册新账号' }))

    expect(screen.getByPlaceholderText('请输入手机号')).toBeTruthy()
    expect(screen.getByPlaceholderText('请输入验证码')).toBeTruthy()
    expect(screen.getByRole('button', { name: '发送验证码' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: '使用邮箱注册' })).toBeNull()
    expect(screen.queryByRole('button', { name: '使用手机号注册' })).toBeNull()
  })

  it('sends the registration code and submits the registration DTO', async () => {
    const user = userEvent.setup()
    vi.mocked(sendRegisterCode).mockResolvedValue(undefined)
    vi.mocked(registerUser).mockResolvedValue(undefined)
    render(<App />)

    await user.click(screen.getByRole('button', { name: '注册新账号' }))
    await user.type(screen.getByPlaceholderText('请输入手机号'), '13600136000')
    await user.type(screen.getByPlaceholderText('请输入验证码'), '123456')
    await user.type(
      screen.getByPlaceholderText('请输入登录密码'),
      'password123',
    )
    await user.type(
      screen.getByPlaceholderText('请再次输入登录密码'),
      'password123',
    )
    const sendButton = screen.getByRole('button', { name: '发送验证码' })
    await user.click(sendButton)

    await waitFor(() => {
      expect(sendRegisterCode).toHaveBeenCalledWith('13600136000')
    })
    await waitFor(() => {
      const countdownButton = document.querySelector(
        '.starter-login-verification',
      )
      expect(countdownButton?.textContent).toMatch(/秒后可重发/)
    })

    await user.click(screen.getByRole('button', { name: '注册' }))

    await waitFor(() => {
      expect(registerUser).toHaveBeenCalledWith({
        account: '13600136000',
        verificationCode: '123456',
        password: 'password123',
        confirmPassword: 'password123',
      })
    })
    expect(
      await screen.findByRole('button', { name: '注册新账号' }),
    ).toBeTruthy()
  })

  it('sends a login code and signs in with the verification code', async () => {
    const user = userEvent.setup()
    vi.mocked(sendLoginCode).mockResolvedValue(undefined)
    vi.mocked(loginUserWithCode).mockResolvedValue({
      accessToken: 'code-login-token',
      tokenType: 'Bearer',
      expiresIn: 7200,
      user: {
        userNo: '701804677763',
        nickname: '卡司用户77763',
        mobile: '13600136000',
        email: null,
        avatarUrl: null,
      },
    })
    vi.mocked(getCurrentUser).mockResolvedValue({
      userNo: '701804677763',
      nickname: '卡司用户77763',
      realName: null,
      mobile: '13600136000',
      email: null,
      avatarUrl: null,
      status: 1,
      lastLoginAt: null,
      lastLoginIp: null,
      createdAt: null,
    })
    render(<App />)

    await user.click(screen.getByRole('button', { name: '使用验证码登录' }))
    await user.type(screen.getByPlaceholderText('请输入手机号'), '13600136000')
    await user.click(screen.getByRole('button', { name: '发送验证码' }))

    await waitFor(() => {
      expect(sendLoginCode).toHaveBeenCalledWith('13600136000')
    })

    await user.type(screen.getByPlaceholderText('请输入验证码'), '123456')
    await user.click(screen.getByRole('button', { name: '登录' }))

    expect(
      await screen.findByRole('heading', {
        name: '欢迎 卡司用户77763 使用卡司短剧推广平台',
      }),
    ).toBeTruthy()
    expect(loginUserWithCode).toHaveBeenCalledWith('13600136000', '123456')
  })

  it('completes forgot-password recovery without persisting the reset token', async () => {
    const user = userEvent.setup()
    vi.mocked(sendForgotPasswordCode).mockResolvedValue(undefined)
    vi.mocked(verifyForgotPasswordCode).mockResolvedValue({
      resetToken: 'reset-token-only-in-memory',
      expiresIn: 300,
    })
    vi.mocked(resetPassword).mockResolvedValue(undefined)
    render(<App />)

    await user.click(screen.getByRole('button', { name: '忘记密码？' }))
    await user.type(screen.getByPlaceholderText('请输入手机号'), '13600136000')
    await user.click(screen.getByRole('button', { name: '发送验证码' }))

    await waitFor(() => {
      expect(sendForgotPasswordCode).toHaveBeenCalledWith('13600136000')
    })

    await user.type(screen.getByPlaceholderText('请输入验证码'), '123456')
    await user.click(screen.getByRole('button', { name: '下一步' }))

    await screen.findByPlaceholderText('请输入新密码')
    expect(verifyForgotPasswordCode).toHaveBeenCalledWith(
      '13600136000',
      '123456',
    )
    await user.type(
      screen.getByPlaceholderText('请输入新密码'),
      'newPassword123',
    )
    await user.type(
      screen.getByPlaceholderText('请再次输入新密码'),
      'newPassword123',
    )
    await user.click(screen.getByRole('button', { name: '重置密码' }))

    await waitFor(() => {
      expect(resetPassword).toHaveBeenCalledWith(
        'reset-token-only-in-memory',
        'newPassword123',
        'newPassword123',
      )
    })
    expect(window.location.href).not.toContain('reset-token-only-in-memory')
    expect(JSON.stringify(window.localStorage)).not.toContain(
      'reset-token-only-in-memory',
    )
    expect(JSON.stringify(window.sessionStorage)).not.toContain(
      'reset-token-only-in-memory',
    )
    expect(
      await screen.findByRole('button', { name: '忘记密码？' }),
    ).toBeTruthy()
  })

  it('navigates to the workspace after a valid password login', async () => {
    const user = userEvent.setup()
    vi.mocked(loginUser).mockResolvedValue({
      accessToken: 'test-token',
      tokenType: 'Bearer',
      expiresIn: 7200,
      user: {
        userNo: '701804677763',
        nickname: '卡司用户77763',
        mobile: '13600136000',
        email: null,
        avatarUrl: null,
      },
    })
    vi.mocked(getCurrentUser).mockResolvedValue({
      userNo: '701804677763',
      nickname: '卡司用户77763',
      realName: null,
      mobile: '13600136000',
      email: null,
      avatarUrl: null,
      status: 1,
      lastLoginAt: null,
      lastLoginIp: null,
      createdAt: null,
    })
    render(<App />)

    await user.type(
      screen.getByPlaceholderText('请输入手机号或者邮箱'),
      'user@example.com',
    )
    await user.type(
      screen.getByPlaceholderText('请输入登录密码'),
      'password123',
    )
    await user.click(screen.getByRole('button', { name: '登录' }))

    expect(
      await screen.findByRole('heading', {
        name: '欢迎 卡司用户77763 使用卡司短剧推广平台',
      }),
    ).toBeTruthy()
  })

  it('keeps the login page and shows an error when login fails', async () => {
    const user = userEvent.setup()
    vi.mocked(loginUser).mockRejectedValue(new Error('账号或密码错误'))
    render(<App />)

    await user.type(
      screen.getByPlaceholderText('请输入手机号或者邮箱'),
      'user@example.com',
    )
    await user.type(
      screen.getByPlaceholderText('请输入登录密码'),
      'password123',
    )
    await user.click(screen.getByRole('button', { name: '登录' }))

    expect((await screen.findByRole('alert')).textContent).toContain(
      '账号或密码错误',
    )
    expect(screen.getByRole('heading', { name: '登录到' })).toBeTruthy()
  })
})
