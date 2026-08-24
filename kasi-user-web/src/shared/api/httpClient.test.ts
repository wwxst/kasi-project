import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../../features/auth/model/authStore'
import { server } from '../../test/server'
import { ApiError } from './ApiError'
import { apiRequest } from './httpClient'

describe('apiRequest', () => {
  beforeEach(() => {
    useAuthStore.getState().clearSession()
  })

  it('adds the current bearer token and unwraps successful data', async () => {
    useAuthStore.getState().setSession({
      accessToken: 'user-token',
      expiresAt: Date.now() + 60_000,
    })
    server.use(
      http.get('/api/test', ({ request }) => {
        expect(request.headers.get('Authorization')).toBe('Bearer user-token')
        return HttpResponse.json({
          code: 0,
          message: 'success',
          data: { value: 'ok' },
        })
      }),
    )

    await expect(
      apiRequest<{ value: string }>({ method: 'GET', url: '/api/test' }),
    ).resolves.toEqual({ value: 'ok' })
  })

  it('turns a non-zero business code into ApiError', async () => {
    server.use(
      http.post('/api/test', () =>
        HttpResponse.json({ code: 3003, message: '账号或密码错误' }),
      ),
    )

    await expect(
      apiRequest({ method: 'POST', url: '/api/test' }),
    ).rejects.toEqual(
      expect.objectContaining({
        name: 'ApiError',
        code: 3003,
        message: '账号或密码错误',
      }),
    )
  })

  it('clears the session after HTTP 401', async () => {
    useAuthStore.getState().setSession({
      accessToken: 'expired-token',
      expiresAt: Date.now() + 60_000,
    })
    server.use(
      http.get('/api/test', () =>
        HttpResponse.json(
          { code: 1002, message: '未登录或Token已过期' },
          { status: 401 },
        ),
      ),
    )

    await expect(
      apiRequest({ method: 'GET', url: '/api/test' }),
    ).rejects.toBeInstanceOf(ApiError)
    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it('keeps the session after HTTP 503', async () => {
    useAuthStore.getState().setSession({
      accessToken: 'retryable-token',
      expiresAt: Date.now() + 60_000,
    })
    server.use(
      http.get('/api/test', () =>
        HttpResponse.json(
          { code: 1007, message: '认证状态服务不可用' },
          { status: 503 },
        ),
      ),
    )

    await expect(
      apiRequest({ method: 'GET', url: '/api/test' }),
    ).rejects.toMatchObject({ code: 1007, status: 503, retryable: true })
    expect(useAuthStore.getState().accessToken).toBe('retryable-token')
  })
})
