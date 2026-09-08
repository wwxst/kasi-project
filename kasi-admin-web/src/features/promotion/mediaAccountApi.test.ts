import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import {
  getAdminMediaAccount,
  deleteAdminMediaAccount,
  listAdminMediaAccounts,
  listDramaProviderOptions,
  retryMediaFiling,
} from './mediaAccountApi'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('mediaAccountApi', () => {
  it('sends administrator list filters', async () => {
    let requestUrl: URL | undefined
    server.use(
      http.get('/api/admin/promotion/media-accounts', ({ request }) => {
        requestUrl = new URL(request.url)
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: { list: [], page: 1, size: 20, total: 0 },
        })
      }),
    )

    await listAdminMediaAccounts({
      page: 1,
      size: 20,
      userNo: '123456789012',
      mediaType: 'TIKTOK',
      accountStatus: 1,
      providerId: 1,
      filingStatus: 'FAILED',
    })

    expect(requestUrl?.searchParams.get('userNo')).toBe('123456789012')
    expect(requestUrl?.searchParams.get('filingStatus')).toBe('FAILED')
  })

  it('calls detail, retry, delete and provider option endpoints', async () => {
    let deleted = false
    server.use(
      http.get('/api/admin/promotion/media-accounts/8', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: { id: 8, userNo: '123456789012' },
        }),
      ),
      http.delete('/api/admin/promotion/media-accounts/8', () => {
        deleted = true
        return HttpResponse.json({ code: 0, message: 'ok', data: null })
      }),
      http.post('/api/admin/promotion/media-accounts/8/filings/1/retry', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: { providerId: 1 } }),
      ),
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: [
            { id: 1, providerCode: 'GOODSHORT', providerName: 'GoodShort' },
          ],
        }),
      ),
    )

    await getAdminMediaAccount(8)
    await retryMediaFiling(8, 1)
    await deleteAdminMediaAccount(8)
    await expect(listDramaProviderOptions()).resolves.toEqual([
      expect.objectContaining({ providerCode: 'GOODSHORT' }),
    ])

    expect(deleted).toBe(true)
  })
})
