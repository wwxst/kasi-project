import { HttpResponse, http } from 'msw'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import App from './App'
import { useAuthStore } from '../features/auth/model/authStore'
import { server } from '../test/server'

const currentUser = {
  userNo: '123456789012',
  nickname: '测试用户',
  realName: '用户本人',
  mobile: '13800138000',
  email: 'user@example.com',
  avatarUrl: null,
  status: 1,
  lastLoginAt: '2026-08-17T09:30:00',
  lastLoginIp: '127.0.0.1',
  createdAt: '2026-08-01T09:00:00',
}

function setSession() {
  useAuthStore.getState().setSession({
    accessToken: 'user-token',
    expiresAt: Date.now() + 60_000,
  })
}

describe('AppRouter', () => {
  beforeEach(() => {
    useAuthStore.getState().clearSession()
  })

  it('redirects unauthenticated account visits to login', async () => {
    window.history.replaceState({}, '', '/account')
    render(<App />)

    expect(
      await screen.findByRole('heading', { name: '登录' }),
    ).toBeInTheDocument()
  })

  it('restores a valid session through /me and renders the account page', async () => {
    setSession()
    server.use(
      http.get('/api/user/auth/me', () =>
        HttpResponse.json({ code: 0, message: 'success', data: currentUser }),
      ),
      http.get('/api/user/promotion/media-accounts', () =>
        HttpResponse.json({ code: 0, message: 'success', data: [] }),
      ),
    )
    window.history.replaceState({}, '', '/account')
    render(<App />)

    expect(
      await screen.findByRole('heading', { name: '账户概览' }),
    ).toBeInTheDocument()
    expect(screen.getByText('账号总数')).toBeInTheDocument()
    expect(screen.getByText('推广数据')).toBeInTheDocument()
    expect(screen.getByText('123456789012')).toBeInTheDocument()
    expect(screen.getAllByText('测试用户').length).toBeGreaterThan(0)
  })

  it('keeps the session and offers retry when auth state is unavailable', async () => {
    setSession()
    server.use(
      http.get('/api/user/auth/me', () =>
        HttpResponse.json(
          { code: 1007, message: '认证状态服务不可用' },
          { status: 503 },
        ),
      ),
    )
    window.history.replaceState({}, '', '/account')
    render(<App />)

    expect(await screen.findByText('认证状态服务不可用')).toBeInTheDocument()
    expect(useAuthStore.getState().accessToken).toBe('user-token')
    expect(screen.getByRole('button', { name: '重试' })).toBeInTheDocument()
  })

  it('returns to login after the startup session is rejected with 401', async () => {
    setSession()
    server.use(
      http.get('/api/user/auth/me', () =>
        HttpResponse.json(
          { code: 1002, message: '未登录或Token已过期' },
          { status: 401 },
        ),
      ),
    )
    window.history.replaceState({}, '', '/account')
    render(<App />)

    expect(
      await screen.findByRole('heading', { name: '登录' }),
    ).toBeInTheDocument()
    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it('clears the session after a successful password change', async () => {
    setSession()
    let passwordBody: unknown
    server.use(
      http.get('/api/user/auth/me', () =>
        HttpResponse.json({ code: 0, message: 'success', data: currentUser }),
      ),
      http.put('/api/user/auth/password', async ({ request }) => {
        passwordBody = await request.json()
        return HttpResponse.json({ code: 0, message: '密码修改成功' })
      }),
    )
    window.history.replaceState({}, '', '/account/security')
    const user = userEvent.setup()
    render(<App />)

    await screen.findByRole('heading', { name: '安全设置' })
    await user.type(screen.getByLabelText('原密码'), 'OldPassword123')
    await user.type(screen.getByLabelText('新密码'), 'NewPassword123')
    await user.type(screen.getByLabelText('确认密码'), 'NewPassword123')
    await user.click(screen.getByRole('button', { name: '更新密码' }))

    expect(
      await screen.findByRole('heading', { name: '登录' }),
    ).toBeInTheDocument()
    expect(passwordBody).toEqual({
      oldPassword: 'OldPassword123',
      newPassword: 'NewPassword123',
      confirmPassword: 'NewPassword123',
    })
    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it('revokes the current session and returns to login on logout', async () => {
    setSession()
    let logoutCalled = false
    server.use(
      http.get('/api/user/auth/me', () =>
        HttpResponse.json({ code: 0, message: 'success', data: currentUser }),
      ),
      http.get('/api/user/promotion/media-accounts', () =>
        HttpResponse.json({ code: 0, message: 'success', data: [] }),
      ),
      http.post('/api/user/auth/logout', () => {
        logoutCalled = true
        return HttpResponse.json({ code: 0, message: '退出成功' })
      }),
    )
    window.history.replaceState({}, '', '/account')
    const user = userEvent.setup()
    render(<App />)

    await screen.findByRole('heading', { name: '账户概览' })
    await user.click(screen.getByRole('button', { name: '测试用户 用户菜单' }))
    await user.click(await screen.findByText('退出登录', { exact: true }))
    await user.click(await screen.findByRole('button', { name: '确认退出' }))

    expect(
      await screen.findByRole('heading', { name: '登录' }),
    ).toBeInTheDocument()
    expect(logoutCalled).toBe(true)
    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it('renders the account filing page inside the protected account layout', async () => {
    setSession()
    server.use(
      http.get('/api/user/auth/me', () =>
        HttpResponse.json({ code: 0, message: 'success', data: currentUser }),
      ),
      http.get('/api/user/promotion/media-accounts', () =>
        HttpResponse.json({ code: 0, message: 'success', data: [] }),
      ),
    )
    window.history.replaceState({}, '', '/account/filing')
    render(<App />)

    expect(
      await screen.findByRole('heading', { name: '账号报白' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '账号报白' })).toHaveAttribute(
      'href',
      '/account/filing',
    )
    expect(screen.getByRole('link', { name: 'Kasi 用户中心' })).toHaveAttribute(
      'href',
      '/account',
    )
    expect(screen.getByRole('button', { name: '收起菜单' })).toBeInTheDocument()
    expect(
      screen.getByRole('navigation', { name: '用户中心主导航' }),
    ).toBeInTheDocument()
    const navigation = screen.getByRole('navigation', {
      name: '用户中心主导航',
    })
    expect(within(navigation).queryByText('工作台')).not.toBeInTheDocument()
    expect(within(navigation).queryByText('推广管理')).not.toBeInTheDocument()
    expect(within(navigation).queryByText('账户设置')).not.toBeInTheDocument()
    expect(
      within(navigation).getByRole('link', { name: '账户概览' }),
    ).toHaveAttribute('href', '/account')
    expect(
      within(navigation).getByRole('link', { name: '创建推广' }),
    ).toHaveAttribute('href', '/promotion/links')
    expect(
      within(navigation).getByRole('link', {
        name: '\u4f63\u91d1\u660e\u7ec6',
      }),
    ).toHaveAttribute('href', '/promotion/income')
    expect(screen.getByText('测试用户')).toBeInTheDocument()
  })
})
