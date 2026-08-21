import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import {
  getDramaCatalogDetail,
  listDramaCatalog,
  listDramaSyncStatuses,
  requestDramaCatalogSync,
  updateDramaLocalStatus,
} from './dramaCatalogApi'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('dramaCatalogApi', () => {
  it('maps catalog filters to backend query parameters', async () => {
    let requestUrl: URL | undefined
    server.use(
      http.get('/api/admin/drama/catalog', ({ request }) => {
        requestUrl = new URL(request.url)
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: { list: [], page: 2, size: 50, total: 0 },
        })
      }),
    )

    await listDramaCatalog({
      page: 2,
      size: 50,
      providerId: 1,
      title: 'Reborn',
      language: 'ENGLISH',
      remoteShowStatus: '1',
      localStatus: 'PUBLISHED',
    })

    expect(requestUrl?.searchParams.get('page')).toBe('2')
    expect(requestUrl?.searchParams.get('size')).toBe('50')
    expect(requestUrl?.searchParams.get('providerId')).toBe('1')
    expect(requestUrl?.searchParams.get('title')).toBe('Reborn')
    expect(requestUrl?.searchParams.get('language')).toBe('ENGLISH')
    expect(requestUrl?.searchParams.get('remoteShowStatus')).toBe('1')
    expect(requestUrl?.searchParams.get('localStatus')).toBe('PUBLISHED')
  })

  it('calls detail, sync status, sync request and local status endpoints', async () => {
    let syncStatusProviderId: string | null = null
    let syncBody: unknown
    let localStatusBody: unknown
    server.use(
      http.get('/api/admin/drama/catalog/8', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: { id: 8, title: 'Reborn to Love' },
        }),
      ),
      http.get('/api/admin/drama/catalog/sync/status', ({ request }) => {
        syncStatusProviderId = new URL(request.url).searchParams.get(
          'providerId',
        )
        return HttpResponse.json({ code: 0, message: 'ok', data: [] })
      }),
      http.post('/api/admin/drama/catalog/sync', async ({ request }) => {
        syncBody = await request.json()
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: [
            {
              id: 41,
              syncType: 'INCREMENTAL',
              language: 'ENGLISH',
              status: 'REQUESTED',
            },
          ],
        })
      }),
      http.patch('/api/admin/drama/catalog/8/status', async ({ request }) => {
        localStatusBody = await request.json()
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: { id: 8, localStatus: 'OFFLINE' },
        })
      }),
    )

    await expect(getDramaCatalogDetail(8)).resolves.toEqual(
      expect.objectContaining({ id: 8 }),
    )
    await listDramaSyncStatuses(1)
    await requestDramaCatalogSync({
      providerId: 1,
      syncType: 'INCREMENTAL',
      languages: ['ENGLISH'],
    })
    await updateDramaLocalStatus(8, { localStatus: 'OFFLINE' })

    expect(syncStatusProviderId).toBe('1')
    expect(syncBody).toEqual({
      providerId: 1,
      syncType: 'INCREMENTAL',
      languages: ['ENGLISH'],
    })
    expect(localStatusBody).toEqual({ localStatus: 'OFFLINE' })
  })

  it('throws the backend business message', async () => {
    server.use(
      http.get('/api/admin/drama/catalog', () =>
        HttpResponse.json({
          code: 1006,
          message: '查询参数错误',
          data: null,
        }),
      ),
    )

    await expect(listDramaCatalog({ page: 1, size: 20 })).rejects.toThrow(
      '查询参数错误',
    )
  })
})
