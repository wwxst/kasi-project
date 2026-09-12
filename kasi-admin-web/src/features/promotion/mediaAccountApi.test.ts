import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import {
  getAdminMediaAccount,
  deleteAdminMediaAccount,
  exportAdminMediaAccounts,
  listAdminMediaAccounts,
  listDramaProviderOptions,
  resolveMediaFilingSubmission,
  retryMediaFiling,
  updateManualMediaFilingStatus,
} from './mediaAccountApi'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('mediaAccountApi', () => {
  it('exports XLSX with all current filing filters', async () => {
    let requestUrl: URL | undefined
    server.use(
      http.get(
        '/api/admin/promotion/media-accounts/export.xlsx',
        ({ request }) => {
          requestUrl = new URL(request.url)
          return new HttpResponse(new Blob(['xlsx']))
        },
      ),
    )
    await exportAdminMediaAccounts({
      page: 3,
      size: 1,
      userNo: '583104726918',
      mediaType: 'TIKTOK',
      accountStatus: 1,
      providerId: 7,
      filingMethod: 'MANUAL',
      filingStatus: 'REJECTED',
    })

    expect(requestUrl?.pathname).toBe(
      '/api/admin/promotion/media-accounts/export.xlsx',
    )
    expect(requestUrl?.searchParams.get('filingMethod')).toBe('MANUAL')
    expect(requestUrl?.searchParams.get('filingStatus')).toBe('REJECTED')
  })

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
      filingMethod: 'MANUAL',
      filingStatus: 'REJECTED',
    })

    expect(requestUrl?.searchParams.get('userNo')).toBe('123456789012')
    expect(requestUrl?.searchParams.get('filingMethod')).toBe('MANUAL')
    expect(requestUrl?.searchParams.get('filingStatus')).toBe('REJECTED')
  })

  it('calls manual status and unknown submission resolution endpoints', async () => {
    let manualBody: unknown
    let resolutionBody: unknown
    server.use(
      http.patch(
        '/api/admin/promotion/media-accounts/8/filings/1/status',
        async ({ request }) => {
          manualBody = await request.json()
          return HttpResponse.json({ code: 0, message: 'ok', data: {} })
        },
      ),
      http.post(
        '/api/admin/promotion/media-accounts/8/filings/1/submission-resolution',
        async ({ request }) => {
          resolutionBody = await request.json()
          return HttpResponse.json({ code: 0, message: 'ok', data: {} })
        },
      ),
    )

    await updateManualMediaFilingStatus(8, 1, 'APPROVED')
    await resolveMediaFilingSubmission(8, 1, 'NOT_RECEIVED')

    expect(manualBody).toEqual({ status: 'APPROVED' })
    expect(resolutionBody).toEqual({ resolution: 'NOT_RECEIVED' })
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
