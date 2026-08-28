import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { queryClient } from './app/queryClient'
import { getCurrentUser } from './features/auth/authApi'
import { useAuthStore } from './features/auth/authStore'
import { getMediaAccounts } from './features/mediaAccounts/mediaAccountsApi'

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
  vi.clearAllMocks()
})

describe('media account route', () => {
  it('renders the filter list instead of the workspace placeholder', async () => {
    window.history.replaceState({}, '', '/workspace/media-accounts')
    useAuthStore.setState({ accessToken: 'test-token' })
    vi.mocked(getCurrentUser).mockResolvedValue({
      userNo: '701804677763',
      nickname: 'Test User',
      realName: null,
      mobile: '13600136000',
      email: null,
      avatarUrl: null,
      status: 1,
      lastLoginAt: null,
      lastLoginIp: null,
      createdAt: null,
    })
    vi.mocked(getMediaAccounts).mockResolvedValue([])

    render(<App />)

    expect(
      (await screen.findAllByText('媒体平台')).length,
    ).toBeGreaterThanOrEqual(2)
    expect(screen.getAllByText('账号报白').length).toBeGreaterThanOrEqual(1)
    expect(screen.queryByText(/当前页面已接入用户工作区布局/)).toBeNull()
  })

  it('redirects unauthenticated workspace visits without calling the API', async () => {
    window.history.replaceState({}, '', '/workspace/media-accounts')
    vi.mocked(getMediaAccounts).mockResolvedValue([])

    render(<App />)

    await waitFor(() => expect(window.location.pathname).toBe('/login'))
    expect(getMediaAccounts).not.toHaveBeenCalled()
  })
})
