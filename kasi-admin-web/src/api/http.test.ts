import { HttpResponse, http } from 'msw'
import { setupServer } from 'msw/node'
import {
  afterAll,
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest'
import { useAuthStore } from '../features/auth/authStore'
import { httpClient } from './http'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  useAuthStore.getState().clearSession()
})
afterAll(() => server.close())

describe('httpClient authentication handling', () => {
  beforeEach(() => {
    useAuthStore.getState().setSession({
      accessToken: 'expired-token',
      tokenType: 'Bearer',
      expiresIn: 7200,
      admin: {
        id: 1,
        username: 'admin',
        realName: '管理员',
        mobile: null,
        email: null,
        avatarUrl: null,
        isSuperAdmin: 1,
      },
    })
  })

  it('clears the session after a 401 response', async () => {
    server.use(
      http.get('/api/protected', () =>
        HttpResponse.json(
          { code: 1002, message: '未登录或Token已过期' },
          { status: 401 },
        ),
      ),
    )

    await expect(httpClient.get('/api/protected')).rejects.toMatchObject({
      response: { status: 401 },
    })

    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it('keeps the session after a 403 response', async () => {
    server.use(
      http.get('/api/protected', () =>
        HttpResponse.json(
          { code: 1003, message: '无权限访问' },
          { status: 403 },
        ),
      ),
    )

    await expect(httpClient.get('/api/protected')).rejects.toMatchObject({
      response: { status: 403 },
    })

    expect(useAuthStore.getState().accessToken).toBe('expired-token')
  })

  it('keeps the session after a 503 response', async () => {
    server.use(
      http.get('/api/protected', () =>
        HttpResponse.json(
          { code: 1007, message: '认证状态服务不可用' },
          { status: 503 },
        ),
      ),
    )

    await expect(httpClient.get('/api/protected')).rejects.toMatchObject({
      response: { status: 503 },
    })

    expect(useAuthStore.getState().accessToken).toBe('expired-token')
  })
})
