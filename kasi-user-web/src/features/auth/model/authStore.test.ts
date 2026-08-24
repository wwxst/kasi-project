import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from './authStore'

describe('authStore', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    useAuthStore.getState().clearSession()
  })

  it('persists the access token and absolute expiry', () => {
    useAuthStore.getState().setSession({
      accessToken: 'user-token',
      expiresAt: 5_000,
    })

    expect(useAuthStore.getState().accessToken).toBe('user-token')
    expect(useAuthStore.getState().expiresAt).toBe(5_000)
    expect(window.sessionStorage.getItem('kasi-user-auth')).toContain(
      'user-token',
    )
  })

  it('clears and rejects an expired access token', () => {
    vi.spyOn(Date, 'now').mockReturnValue(6_000)
    useAuthStore.setState({ accessToken: 'expired-token', expiresAt: 5_000 })

    expect(useAuthStore.getState().getValidAccessToken()).toBeNull()
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useAuthStore.getState().expiresAt).toBeNull()
  })
})
