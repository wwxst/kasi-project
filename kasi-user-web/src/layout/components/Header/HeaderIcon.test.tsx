import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import HeaderIcon from './HeaderIcon'
import { LayoutProvider } from '../../LayoutProvider'
import { useAuthStore } from '../../../features/auth/authStore'
import { getCurrentUser } from '../../../features/auth/authApi'

vi.mock('../../../features/auth/authApi', () => ({
  getCurrentUser: vi.fn(),
}))

afterEach(cleanup)

describe('HeaderIcon', () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: 'test-token' })
    vi.mocked(getCurrentUser).mockResolvedValue({
      userNo: '701804677763',
      nickname: '卡司用户77763',
      realName: null,
      mobile: '13600136000',
      email: null,
      avatarUrl: '/uploads/avatar.png',
      status: 1,
      lastLoginAt: null,
      lastLoginIp: null,
      createdAt: null,
    })
  })

  it('loads and renders the persisted nickname and avatar from /me', async () => {
    render(
      <MemoryRouter>
        <QueryClientProvider
          client={
            new QueryClient({ defaultOptions: { queries: { retry: false } } })
          }
        >
          <LayoutProvider>
            <HeaderIcon />
          </LayoutProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    )

    expect(await screen.findByText('卡司用户77763')).toBeTruthy()
    expect(
      (await screen.findByAltText('卡司用户77763')).getAttribute('src'),
    ).toBe('/uploads/avatar.png')
    expect(getCurrentUser).toHaveBeenCalledTimes(1)
  })

  it('uses the nickname initial in a gray avatar when no image is available', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue({
      userNo: '701804677763',
      nickname: '系统管理员',
      realName: null,
      mobile: '13600136000',
      email: null,
      avatarUrl: null,
      status: 1,
      lastLoginAt: null,
      lastLoginIp: null,
      createdAt: null,
    })

    render(
      <MemoryRouter>
        <QueryClientProvider
          client={
            new QueryClient({ defaultOptions: { queries: { retry: false } } })
          }
        >
          <LayoutProvider>
            <HeaderIcon />
          </LayoutProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    )

    expect(await screen.findByText('系')).toBeTruthy()
    expect(await screen.findByText('系统管理员')).toBeTruthy()
    expect(screen.getByTestId('header-avatar-fallback').textContent).toBe('系')
  })
})
