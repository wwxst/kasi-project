import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import {
  getDramaCatalogDetail,
  listDramaLanguageOptions,
  getDramaContentSyncRecordDetails,
  getDramaContentSyncStatus,
  getDramaSyncRecordDetails,
  listDramaContentSyncRecords,
  listDramaSyncRecords,
  listDramaCatalog,
  listDramaSyncStatuses,
  requestAllDramaContentSync,
  requestDramaCatalogSync,
  requestDramaContentBatchSync,
  requestDramaContentSync,
  updateDramaLocalStatus,
} from './dramaCatalogApi'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('dramaCatalogApi', () => {
  it('loads backend-owned drama language options', async () => {
    server.use(
      http.get('/api/drama/languages', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: [{ value: 'JAPANESE', label: '日语' }],
        }),
      ),
    )

    await expect(listDramaLanguageOptions()).resolves.toEqual([
      { value: 'JAPANESE', label: '日语' },
    ])
  })

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

  it('loads independent aggregated catalog and content records with details', async () => {
    let catalogProvider: string | null = null
    let contentProvider: string | null = null
    server.use(
      http.get('/api/admin/drama/catalog/sync/records', ({ request }) => {
        catalogProvider = new URL(request.url).searchParams.get('providerId')
        return HttpResponse.json({ code: 0, message: 'ok', data: [] })
      }),
      http.get(
        '/api/admin/drama/catalog/contents/sync/records',
        ({ request }) => {
          contentProvider = new URL(request.url).searchParams.get('providerId')
          return HttpResponse.json({ code: 0, message: 'ok', data: [] })
        },
      ),
      http.get('/api/admin/drama/catalog/sync/records/run-1', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [] }),
      ),
      http.get('/api/admin/drama/catalog/contents/sync/records/run-2', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [] }),
      ),
    )
    await listDramaSyncRecords(1)
    await listDramaContentSyncRecords(2)
    await getDramaSyncRecordDetails(1, 'run-1')
    await getDramaContentSyncRecordDetails(2, 'run-2')
    expect(catalogProvider).toBe('1')
    expect(contentProvider).toBe('2')
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

  it('calls all free content sync endpoints and maps missing status to null', async () => {
    let singleRequested = false
    let batchBody: unknown
    let allBody: unknown
    let statusRequestCount = 0
    const task = {
      id: 51,
      dramaId: 8,
      status: 'REQUESTED',
      requestedAt: '2026-08-29T08:00:00',
      nextRunAt: '2026-08-29T08:00:03',
      retryCount: 0,
      totalFetched: 0,
      insertedCount: 0,
      updatedCount: 0,
      lastErrorCode: null,
      lastErrorMessage: null,
    }

    server.use(
      http.post('/api/admin/drama/catalog/8/contents/sync', () => {
        singleRequested = true
        return HttpResponse.json({ code: 0, message: 'ok', data: task })
      }),
      http.post(
        '/api/admin/drama/catalog/contents/sync',
        async ({ request }) => {
          batchBody = await request.json()
          return HttpResponse.json({
            code: 0,
            message: 'ok',
            data: {
              requestedCount: 2,
              queuedCount: 1,
              skippedCount: 1,
              invalidCount: 0,
              tasks: [task],
            },
          })
        },
      ),
      http.post(
        '/api/admin/drama/catalog/contents/sync/all',
        async ({ request }) => {
          allBody = await request.json()
          return HttpResponse.json({
            code: 0,
            message: 'ok',
            data: {
              requestedCount: 24,
              queuedCount: 20,
              skippedCount: 3,
              invalidCount: 1,
              tasks: [],
            },
          })
        },
      ),
      http.get('/api/admin/drama/catalog/8/contents/sync/status', () => {
        statusRequestCount += 1
        return statusRequestCount === 1
          ? HttpResponse.json({ code: 0, message: 'ok', data: task })
          : HttpResponse.json({
              code: 6017,
              message: '短剧剧集同步任务不存在',
              data: null,
            })
      }),
    )

    await expect(requestDramaContentSync(8)).resolves.toEqual(task)
    await expect(requestDramaContentBatchSync([8, 9])).resolves.toEqual(
      expect.objectContaining({ requestedCount: 2, skippedCount: 1 }),
    )
    await expect(
      requestAllDramaContentSync({
        providerId: 1,
        language: 'ENGLISH',
        missingOnly: false,
      }),
    ).resolves.toEqual(expect.objectContaining({ requestedCount: 24 }))
    await expect(getDramaContentSyncStatus(8)).resolves.toEqual(task)
    await expect(getDramaContentSyncStatus(8)).resolves.toBeNull()

    expect(singleRequested).toBe(true)
    expect(batchBody).toEqual({ dramaIds: [8, 9] })
    expect(allBody).toEqual({
      providerId: 1,
      language: 'ENGLISH',
      missingOnly: false,
    })
  })

  it('uses the approved message when a single content task is already running', async () => {
    server.use(
      http.post('/api/admin/drama/catalog/8/contents/sync', () =>
        HttpResponse.json({
          code: 6016,
          message: '短剧剧集同步任务正在执行',
          data: null,
        }),
      ),
    )

    await expect(requestDramaContentSync(8)).rejects.toThrow(
      '该短剧的剧集同步任务正在执行',
    )
  })
})
