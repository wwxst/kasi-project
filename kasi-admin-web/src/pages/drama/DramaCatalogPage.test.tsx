import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App as AntdApp } from 'antd'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import {
  afterAll,
  afterEach,
  beforeAll,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

vi.mock('@ant-design/pro-components', async () => {
  const React = await vi.importActual<typeof import('react')>('react')

  const PageContainer = ({ title, content, children, ...rest }: any) => (
    <section {...rest}>
      <h1>{title}</h1>
      <p>{content}</p>
      {children}
    </section>
  )

  const ProTable = ({ actionRef, columns, request, toolBarRender }: any) => {
    const [rows, setRows] = React.useState<any[]>([])
    const load = React.useCallback(async () => {
      const result = await request({ current: 1, pageSize: 20 })
      setRows(result.data ?? [])
    }, [request])

    React.useEffect(() => {
      void load()
    }, [load])

    React.useEffect(() => {
      if (actionRef) actionRef.current = { reload: load }
    }, [actionRef, load])

    return (
      <div data-testid="mock-pro-table">
        <div>
          {typeof toolBarRender === 'function' ? toolBarRender() : null}
        </div>
        {rows.map((row) => (
          <div key={row.id} data-testid={`mock-row-${row.id}`}>
            {columns
              .filter((column: any) => !column.hideInTable)
              .map((column: any, index: number) => {
                const value = column.dataIndex
                  ? row[column.dataIndex]
                  : undefined
                const rendered = column.render
                  ? column.render(value, row, index, undefined)
                  : column.renderText
                    ? column.renderText(value, row, index, undefined)
                    : value
                return (
                  <span key={column.title?.toString() ?? index}>
                    {rendered}
                  </span>
                )
              })}
          </div>
        ))}
      </div>
    )
  }

  return { PageContainer, ProTable }
})

import { DramaCatalogPage } from './DramaCatalogPage'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

const provider = {
  id: 1,
  providerCode: 'GOODSHORT',
  providerName: 'GoodShort',
  status: 1,
  capabilities: ['FULL_DRAMA_SYNC', 'INCREMENTAL_DRAMA_SYNC'],
  connection: {
    id: 11,
    connectionName: 'GoodShort production',
    baseUrl: 'https://example.com',
    partnerId: 'pid',
    currency: 'USD',
    status: 1,
    credentialConfigured: true,
    createdAt: '2026-08-20T08:00:00',
    updatedAt: '2026-08-20T08:00:00',
  },
}

const publishedDrama = {
  id: 8,
  externalDramaId: 'book-1001',
  title: 'Reborn to Love',
  originalTitle: 'Reborn to Love',
  coverUrl: 'https://example.com/cover.jpg',
  language: 'ENGLISH',
  dramaType: 'ROMANCE',
  remoteShowStatus: '1',
  localStatus: 'PUBLISHED',
  remoteUpdatedAt: '2026-08-20T10:30:00',
  lastSeenAt: '2026-08-21T09:00:00',
  updatedAt: '2026-08-21T09:00:00',
}

const draftDrama = {
  ...publishedDrama,
  id: 9,
  externalDramaId: 'book-1002',
  title: 'Hidden Heiress',
  originalTitle: null,
  remoteShowStatus: '0',
  localStatus: 'DRAFT',
}

function renderPage() {
  return render(
    <AntdApp>
      <DramaCatalogPage />
    </AntdApp>,
  )
}

function useCatalogHandlers() {
  let listRequestUrl: URL | undefined
  let listRequestCount = 0
  server.use(
    http.get('/api/admin/drama/providers', () =>
      HttpResponse.json({ code: 0, message: 'ok', data: [provider] }),
    ),
    http.get('/api/admin/drama/catalog', ({ request }) => {
      listRequestUrl = new URL(request.url)
      listRequestCount += 1
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: {
          list: [publishedDrama, draftDrama],
          page: 1,
          size: 20,
          total: 2,
        },
      })
    }),
  )
  return {
    getListRequestUrl: () => listRequestUrl,
    getListRequestCount: () => listRequestCount,
  }
}

describe('DramaCatalogPage', () => {
  it('shows readable remote availability labels instead of raw values', async () => {
    useCatalogHandlers()

    renderPage()

    expect(within(await screen.findByTestId('mock-row-8')).getByText('在线'))
      .toBeInTheDocument()
    expect(within(await screen.findByTestId('mock-row-9')).getByText('已下架'))
      .toBeInTheDocument()
  })

  it('loads the catalog and opens a detail drawer with episodes', async () => {
    const requests = useCatalogHandlers()
    server.use(
      http.get('/api/admin/drama/catalog/8', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: {
            ...publishedDrama,
            description: 'A second chance changes everything.',
            createdAt: '2026-08-20T10:35:00',
            contents: [
              {
                id: 81,
                externalContentId: 'episode-1',
                sequenceNo: 1,
                title: 'The Return',
                free: true,
                durationSeconds: 125,
                remoteUpdatedAt: '2026-08-20T10:30:00',
              },
              {
                id: 82,
                externalContentId: 'episode-2',
                sequenceNo: 2,
                title: 'The Choice',
                free: false,
                durationSeconds: 119,
                remoteUpdatedAt: '2026-08-20T10:30:00',
              },
            ],
          },
        }),
      ),
    )
    const user = userEvent.setup()

    renderPage()

    const row = await screen.findByTestId('mock-row-8')
    expect(within(row).getByText('Reborn to Love')).toBeInTheDocument()
    expect(within(row).getByText('GoodShort')).toBeInTheDocument()
    expect(requests.getListRequestUrl()?.searchParams.get('page')).toBe('1')
    expect(requests.getListRequestUrl()?.searchParams.get('size')).toBe('20')

    await user.click(screen.getByTestId('drama-detail-8'))

    const drawer = await screen.findByTestId('drama-detail-drawer')
    expect(screen.getByText('短剧详情')).toBeInTheDocument()
    expect(within(drawer).getByText('book-1001')).toBeInTheDocument()
    expect(
      within(drawer).getByText('A second chance changes everything.'),
    ).toBeInTheDocument()
    expect(within(drawer).getByText('The Return')).toBeInTheDocument()
    expect(within(drawer).getByText('The Choice')).toBeInTheDocument()
    expect(within(drawer).getByText('免费')).toBeInTheDocument()
    expect(within(drawer).getByText('本地创建时间')).toBeInTheDocument()
    expect(within(drawer).getByText('2026-08-20 10:35')).toBeInTheDocument()
  })

  it('confirms publish and offline status changes then reloads the table', async () => {
    const requests = useCatalogHandlers()
    const statusBodies: Record<number, unknown> = {}
    server.use(
      http.patch(
        '/api/admin/drama/catalog/:id/status',
        async ({ params, request }) => {
          const id = Number(params.id)
          statusBodies[id] = await request.json()
          const source = id === 8 ? publishedDrama : draftDrama
          return HttpResponse.json({
            code: 0,
            message: 'ok',
            data: {
              ...source,
              localStatus: id === 8 ? 'OFFLINE' : 'PUBLISHED',
              description: null,
              createdAt: '2026-08-20T10:35:00',
              contents: [],
            },
          })
        },
      ),
    )
    const user = userEvent.setup()

    renderPage()
    await screen.findByText('Reborn to Love')

    await user.click(screen.getByTestId('drama-status-8'))
    await user.click(await screen.findByTestId('drama-status-confirm-8'))
    await waitFor(() =>
      expect(statusBodies[8]).toEqual({ localStatus: 'OFFLINE' }),
    )

    await user.click(screen.getByTestId('drama-status-9'))
    await user.click(await screen.findByTestId('drama-status-confirm-9'))
    await waitFor(() =>
      expect(statusBodies[9]).toEqual({ localStatus: 'PUBLISHED' }),
    )
    await waitFor(() =>
      expect(requests.getListRequestCount()).toBeGreaterThan(2),
    )
  })

  it('submits an incremental English sync task and opens its status', async () => {
    useCatalogHandlers()
    let syncBody: unknown
    server.use(
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
              pageNo: 1,
              updateTime: null,
              totalFetched: 0,
              totalUpserted: 0,
              insertedCount: 0,
              updatedCount: 0,
              skippedCount: 0,
              errorCount: 0,
              lastSuccessAt: null,
              lastErrorCode: null,
              lastErrorMessage: null,
            },
          ],
        })
      }),
      http.get('/api/admin/drama/catalog/sync/status', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [] }),
      ),
    )
    const user = userEvent.setup()

    renderPage()
    await screen.findByTestId('mock-row-8')
    await user.click(screen.getByRole('button', { name: '同步目录' }))

    const modal = await screen.findByTestId('drama-sync-modal')
    expect(within(modal).getByText('GoodShort')).toBeInTheDocument()
    expect(within(modal).getByText('增量同步')).toBeInTheDocument()
    expect(within(modal).getByText('ENGLISH')).toBeInTheDocument()

    await user.click(within(modal).getByTestId('drama-sync-submit'))

    await waitFor(() =>
      expect(syncBody).toEqual({
        providerId: 1,
        syncType: 'INCREMENTAL',
        languages: ['ENGLISH'],
      }),
    )
    expect(await screen.findByText('同步任务已提交')).toBeInTheDocument()
    expect(
      await screen.findByTestId('drama-sync-status-drawer'),
    ).toBeInTheDocument()
  })

  it('shows sync progress, statistics and errors then refreshes manually', async () => {
    useCatalogHandlers()
    let statusRequestCount = 0
    let requestedProviderId: string | null = null
    server.use(
      http.get('/api/admin/drama/catalog/sync/status', ({ request }) => {
        statusRequestCount += 1
        requestedProviderId = new URL(request.url).searchParams.get(
          'providerId',
        )
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: [
            {
              id: 41,
              syncType: 'INCREMENTAL',
              language: 'ENGLISH',
              status: 'RUNNING',
              pageNo: 3,
              updateTime: 1787280000000,
              totalFetched: 120,
              totalUpserted: 118,
              insertedCount: 30,
              updatedCount: 88,
              skippedCount: 2,
              errorCount: 0,
              lastSuccessAt: '2026-08-21T08:30:00',
              lastErrorCode: null,
              lastErrorMessage: null,
            },
            {
              id: 42,
              syncType: 'FULL',
              language: 'ENGLISH',
              status: 'FAILED',
              pageNo: 6,
              updateTime: null,
              totalFetched: 500,
              totalUpserted: 490,
              insertedCount: 400,
              updatedCount: 90,
              skippedCount: 10,
              errorCount: 1,
              lastSuccessAt: '2026-08-20T18:00:00',
              lastErrorCode: 'PROVIDER_REMOTE_UNAVAILABLE',
              lastErrorMessage: 'GoodShort service temporarily unavailable',
            },
          ],
        })
      }),
    )
    const user = userEvent.setup()

    renderPage()
    await screen.findByTestId('mock-row-8')
    await user.click(screen.getByRole('button', { name: '同步状态' }))

    const drawer = await screen.findByTestId('drama-sync-status-drawer')
    expect(requestedProviderId).toBe('1')
    expect(within(drawer).getByText('运行中')).toBeInTheDocument()
    expect(within(drawer).getByText('同步失败')).toBeInTheDocument()
    expect(within(drawer).getByText('第 3 页')).toBeInTheDocument()
    expect(within(drawer).getByText('拉取 120')).toBeInTheDocument()
    expect(within(drawer).getByText('写入 118')).toBeInTheDocument()
    expect(within(drawer).getByText('新增 30')).toBeInTheDocument()
    expect(within(drawer).getByText('更新 88')).toBeInTheDocument()
    expect(within(drawer).getByText('跳过 2')).toBeInTheDocument()
    expect(within(drawer).getByText('异常 0')).toBeInTheDocument()
    expect(
      within(drawer).getByText('GoodShort service temporarily unavailable'),
    ).toBeInTheDocument()

    await user.click(within(drawer).getByTestId('drama-sync-status-refresh'))
    await waitFor(() => expect(statusRequestCount).toBe(2))
  })

  it('shows the backend error when sync submission fails', async () => {
    useCatalogHandlers()
    server.use(
      http.post('/api/admin/drama/catalog/sync', () =>
        HttpResponse.json({
          code: 6010,
          message: '当前语言已有同步任务执行中',
          data: null,
        }),
      ),
    )
    const user = userEvent.setup()

    renderPage()
    await screen.findByTestId('mock-row-8')
    await user.click(screen.getByRole('button', { name: '同步目录' }))
    const modal = await screen.findByTestId('drama-sync-modal')
    await user.click(within(modal).getByTestId('drama-sync-submit'))

    expect(
      await screen.findByText('当前语言已有同步任务执行中'),
    ).toBeInTheDocument()
    expect(modal).toBeVisible()
  })

  it('keeps disabled providers available for history but blocks new syncs', async () => {
    const disabledProvider = {
      ...provider,
      status: 0,
      connection: { ...provider.connection, status: 0 },
    }
    let statusProviderId: string | null = null
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: [disabledProvider],
        }),
      ),
      http.get('/api/admin/drama/catalog', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: {
            list: [publishedDrama],
            page: 1,
            size: 20,
            total: 1,
          },
        }),
      ),
      http.get('/api/admin/drama/catalog/sync/status', ({ request }) => {
        statusProviderId = new URL(request.url).searchParams.get('providerId')
        return HttpResponse.json({ code: 0, message: 'ok', data: [] })
      }),
    )
    const user = userEvent.setup()

    renderPage()

    const row = await screen.findByTestId('mock-row-8')
    expect(within(row).getByText('GoodShort')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '同步目录' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: '同步状态' }))
    const drawer = await screen.findByTestId('drama-sync-status-drawer')
    expect(within(drawer).getByText('GoodShort')).toBeInTheDocument()
    await waitFor(() => expect(statusProviderId).toBe('1'))
  })

  it('shows a stable placeholder when a cover image fails to load', async () => {
    useCatalogHandlers()

    renderPage()

    const row = await screen.findByTestId('mock-row-8')
    fireEvent.error(within(row).getByRole('img', { name: 'Reborn to Love' }))

    expect(
      within(row).getByTestId('drama-cover-fallback-8'),
    ).toBeInTheDocument()
  })
})
