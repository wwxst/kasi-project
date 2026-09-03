import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { queryClient } from './app/queryClient'
import { getCurrentUser, loginUser } from './features/auth/authApi'
import { useAuthStore } from './features/auth/authStore'

vi.mock('./features/auth/authApi', () => ({
  getCurrentUser: vi.fn(),
  loginUser: vi.fn(),
}))

vi.mock('./features/mediaAccounts/mediaAccountsApi', () => ({
  getMediaAccounts: vi.fn(),
}))

afterEach(() => {
  cleanup()
  window.history.replaceState({}, '', '/')
  useAuthStore.getState().clearSession()
  queryClient.clear()
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
    expect(await screen.findByText('使用微信扫码登录')).toBeTruthy()
    expect(await screen.findByText('使用验证码登录')).toBeTruthy()
  })

  it('uses one contact field for registration', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.click(screen.getByRole('button', { name: '注册新账号' }))

    expect(screen.getByPlaceholderText('请输入手机号或邮箱')).toBeTruthy()
    expect(screen.getByPlaceholderText('请输入验证码')).toBeTruthy()
    expect(screen.getByRole('button', { name: '发送验证码' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: '使用邮箱注册' })).toBeNull()
    expect(screen.queryByRole('button', { name: '使用手机号注册' })).toBeNull()
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
