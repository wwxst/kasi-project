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

  const ProTable = ({
    actionRef,
    columns,
    request,
    toolBarRender,
    rowSelection,
    onSubmit,
    onReset,
    pagination,
  }: any) => {
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
        <button
          data-testid="mock-search-submit"
          onClick={() => onSubmit?.({ title: 'Reborn' })}
        >
          查询
        </button>
        <button data-testid="mock-search-reset" onClick={() => onReset?.()}>
          重置
        </button>
        <button
          data-testid="mock-page-change"
          onClick={() => pagination?.onChange?.(2, 20)}
        >
          下一页
        </button>
        <button
          data-testid="mock-select-first-100"
          onClick={() => {
            const selected = rows.slice(0, 100)
            rowSelection?.onChange?.(
              selected.map((row) => row.id),
              selected,
            )
          }}
        >
          选择前 100 部
        </button>
        {rows.map((row) => (
          <div key={row.id} data-testid={`mock-row-${row.id}`}>
            {rowSelection ? (
              <input
                type="checkbox"
                aria-label={`选择短剧 ${row.id}`}
                checked={rowSelection.selectedRowKeys.includes(row.id)}
                disabled={rowSelection.getCheckboxProps?.(row)?.disabled}
                onChange={() => {
                  const checked = rowSelection.selectedRowKeys.includes(row.id)
                  const keys = checked
                    ? rowSelection.selectedRowKeys.filter(
                        (key: number) => key !== row.id,
                      )
                    : [...rowSelection.selectedRowKeys, row.id]
                  rowSelection.onChange(
                    keys,
                    rows.filter((item) => keys.includes(item.id)),
                  )
                }}
              />
            ) : null}
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
  capabilities: [
    'FULL_DRAMA_SYNC',
    'INCREMENTAL_DRAMA_SYNC',
    'FREE_CONTENT_PREVIEW',
  ],
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
  originalTitle: 'Hippocratic Romance',
  titleZh: '重生之恋',
  coverUrl: 'https://example.com/cover.jpg',
  labelNames: ['甜宠', '先婚后爱', '逆袭', '都市'],
  language: 'ENGLISH',
  dramaType: 'ROMANCE',
  categoryName: '爱情',
  remoteShowStatus: '1',
  localStatus: 'PUBLISHED',
  remoteCreatedAt: '2026-08-19T09:15:00',
  remoteUpdatedAt: '2026-08-20T10:30:00',
  lastSeenAt: '2026-08-21T09:00:00',
  updatedAt: '2026-08-21T09:00:00',
}

const draftDrama = {
  ...publishedDrama,
  id: 9,
  externalDramaId: 'book-1002',
  title: 'Hidden Heiress',
  titleZh: null,
  originalTitle: null,
  remoteShowStatus: '0',
  localStatus: 'DRAFT',
}

function contentTask(
  dramaId: number,
  status: 'REQUESTED' | 'RUNNING' | 'SUCCESS' | 'FAILED',
) {
  return {
    id: 50 + dramaId,
    dramaId,
    status,
    requestedAt: '2026-08-29T08:00:00',
    nextRunAt:
      status === 'SUCCESS' || status === 'FAILED'
        ? null
        : '2026-08-29T08:00:03',
    retryCount: 0,
    totalFetched: 0,
    insertedCount: 0,
    updatedCount: 0,
    lastErrorCode: null,
    lastErrorMessage: null,
  }
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
  it('renders compact drama information with title fallback and limited tags', async () => {
    useCatalogHandlers()

    renderPage()

    const publishedInfo = within(await screen.findByTestId('drama-info-8'))
    const titleRow = publishedInfo.getByTestId('drama-info-title-row')
    expect(within(titleRow).getByText('重生之恋')).toBeInTheDocument()
    expect(within(titleRow).queryByText('爱情')).not.toBeInTheDocument()
    expect(
      within(publishedInfo.getByTestId('drama-info-subtitle-row')).getByText(
        'Hippocratic Romance',
      ),
    ).toBeInTheDocument()
    const tagsRow = publishedInfo.getByTestId('drama-info-tags-row')
    expect(within(tagsRow).getByText('甜宠')).toBeInTheDocument()
    expect(within(tagsRow).getByText('先婚后爱')).toBeInTheDocument()
    expect(within(tagsRow).getByText('逆袭')).toBeInTheDocument()
    expect(within(tagsRow).getByText('都市')).toBeInTheDocument()
    expect(within(tagsRow).queryByText('+2')).not.toBeInTheDocument()

    const fallbackInfo = within(screen.getByTestId('drama-info-9'))
    expect(fallbackInfo.getByText('Hidden Heiress')).toBeInTheDocument()
  })

  it('shows readable remote availability labels instead of raw values', async () => {
    useCatalogHandlers()

    renderPage()

    expect(
      within(await screen.findByTestId('mock-row-8')).getByText('在线'),
    ).toBeInTheDocument()
    expect(
      within(await screen.findByTestId('mock-row-9')).getByText('已下架'),
    ).toBeInTheDocument()
  })

  it('shows the remote publish time in the catalog row', async () => {
    useCatalogHandlers()

    renderPage()

    expect(
      within(await screen.findByTestId('mock-row-8')).getByText(
        '2026-08-19 09:15',
      ),
    ).toBeInTheDocument()
  })

  it('shows Chinese title and category as separate catalog fields', async () => {
    useCatalogHandlers()

    renderPage()

    const row = await screen.findByTestId('mock-row-8')
    expect(within(row).getByText('重生之恋')).toBeInTheDocument()
    expect(within(row).getByTestId('drama-category-8')).toHaveTextContent(
      '爱情',
    )
    expect(within(row).queryByText('book-1001')).not.toBeInTheDocument()
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
      http.get('/api/admin/drama/catalog/8/contents/sync/status', () =>
        HttpResponse.json({
          code: 6017,
          message: '短剧剧集同步任务不存在',
          data: null,
        }),
      ),
    )
    const user = userEvent.setup()

    renderPage()

    const row = await screen.findByTestId('mock-row-8')
    expect(within(row).getByText('Hippocratic Romance')).toBeInTheDocument()
    expect(within(row).getByText('GoodShort')).toBeInTheDocument()
    expect(within(row).getByText('英语')).toBeInTheDocument()
    expect(requests.getListRequestUrl()?.searchParams.get('page')).toBe('1')
    expect(requests.getListRequestUrl()?.searchParams.get('size')).toBe('20')

    await user.click(screen.getByTestId('drama-detail-8'))

    const drawer = await screen.findByTestId('drama-detail-drawer')
    expect(screen.getByText('短剧详情')).toBeInTheDocument()
    expect(within(drawer).getByText('book-1001')).toBeInTheDocument()
    expect(
      within(drawer).getByText('A second chance changes everything.'),
    ).toBeInTheDocument()
    expect(
      within(drawer).getByText(publishedDrama.labelNames[0]),
    ).toBeInTheDocument()
    expect(within(drawer).getByText('The Return')).toBeInTheDocument()
    expect(within(drawer).getByText('The Choice')).toBeInTheDocument()
    expect(within(drawer).getByText('免费')).toBeInTheDocument()
    expect(within(drawer).getByText('本地创建时间')).toBeInTheDocument()
    expect(within(drawer).getByText('2026-08-20 10:35')).toBeInTheDocument()
    expect(within(drawer).getByText('2026-08-19 09:15')).toBeInTheDocument()
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
    await screen.findByText('Hippocratic Romance')

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

  it('submits an incremental sync task with backend default languages and opens its status', async () => {
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
    expect(
      within(modal).getByText('留空时同步全部支持语言'),
    ).toBeInTheDocument()

    await user.click(within(modal).getByTestId('drama-sync-submit'))

    await waitFor(() =>
      expect(syncBody).toEqual({
        providerId: 1,
        syncType: 'INCREMENTAL',
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
    fireEvent.error(
      within(row).getByRole('img', { name: publishedDrama.titleZh }),
    )

    expect(
      within(row).getByTestId('drama-cover-fallback-8'),
    ).toBeInTheDocument()
  })

  it('confirms and submits a single free content sync task', async () => {
    useCatalogHandlers()
    let requestedId: string | undefined
    server.use(
      http.post('/api/admin/drama/catalog/:id/contents/sync', ({ params }) => {
        requestedId = String(params.id)
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: contentTask(8, 'REQUESTED'),
        })
      }),
    )
    const user = userEvent.setup()
    renderPage()

    const row = await screen.findByTestId('mock-row-8')
    await user.click(within(row).getByTestId('drama-content-sync-8'))
    expect(
      await screen.findByText('当前操作只同步 GoodShort 免费剧集。'),
    ).toBeInTheDocument()
    await user.click(await screen.findByTestId('drama-content-sync-confirm-8'))

    await waitFor(() => expect(requestedId).toBe('8'))
    expect(
      await screen.findByText('免费剧集同步任务已提交'),
    ).toBeInTheDocument()
  })

  it('submits selected dramas, reports counts, and clears selection only on success', async () => {
    useCatalogHandlers()
    let body: unknown
    server.use(
      http.post(
        '/api/admin/drama/catalog/contents/sync',
        async ({ request }) => {
          body = await request.json()
          return HttpResponse.json({
            code: 0,
            message: 'ok',
            data: {
              requestedCount: 2,
              queuedCount: 1,
              skippedCount: 1,
              invalidCount: 0,
              tasks: [],
            },
          })
        },
      ),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByTestId('mock-row-8')

    expect(
      screen.getByRole('button', { name: '同步所选剧集（0）' }),
    ).toBeDisabled()
    await user.click(screen.getByRole('checkbox', { name: '选择短剧 8' }))
    await user.click(screen.getByRole('checkbox', { name: '选择短剧 9' }))
    await user.click(screen.getByRole('button', { name: '同步所选剧集（2）' }))
    await user.click(await screen.findByTestId('drama-content-batch-confirm'))

    await waitFor(() => expect(body).toEqual({ dramaIds: [8, 9] }))
    expect(
      await screen.findByText(
        '请求 2 部，排队 1 部，运行中跳过 1 部，无效 0 部',
      ),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: '同步所选剧集（0）' }),
    ).toBeDisabled()
  })

  it('keeps selected dramas when batch submission fails', async () => {
    useCatalogHandlers()
    server.use(
      http.post('/api/admin/drama/catalog/contents/sync', () =>
        HttpResponse.json({ code: 6008, message: '短剧不存在', data: null }),
      ),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByTestId('mock-row-8')

    await user.click(screen.getByRole('checkbox', { name: '选择短剧 8' }))
    await user.click(screen.getByRole('checkbox', { name: '选择短剧 9' }))
    await user.click(screen.getByRole('button', { name: '同步所选剧集（2）' }))
    await user.click(await screen.findByTestId('drama-content-batch-confirm'))

    expect(await screen.findByText('短剧不存在')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: '同步所选剧集（2）' }),
    ).toBeEnabled()
  })

  it('limits selected dramas to one hundred', async () => {
    const dramas = Array.from({ length: 101 }, (_, index) => ({
      ...publishedDrama,
      id: index + 1,
      externalDramaId: `book-${index + 1}`,
      title: `Drama ${index + 1}`,
      titleZh: null,
      originalTitle: null,
    }))
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [provider] }),
      ),
      http.get('/api/admin/drama/catalog', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: { list: dramas, page: 1, size: 101, total: 101 },
        }),
      ),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByTestId('mock-row-101')

    await user.click(screen.getByTestId('mock-select-first-100'))
    expect(
      screen.getByRole('button', { name: '同步所选剧集（100）' }),
    ).toBeEnabled()
    expect(
      screen.getByRole('checkbox', { name: '选择短剧 101' }),
    ).toBeDisabled()
  })

  it('clears selected dramas after search, reset, and page changes', async () => {
    useCatalogHandlers()
    const user = userEvent.setup()
    renderPage()
    await screen.findByTestId('mock-row-8')

    for (const trigger of [
      'mock-search-submit',
      'mock-search-reset',
      'mock-page-change',
    ]) {
      await user.click(screen.getByRole('checkbox', { name: '选择短剧 8' }))
      expect(
        screen.getByRole('button', { name: '同步所选剧集（1）' }),
      ).toBeEnabled()
      await user.click(screen.getByTestId(trigger))
      expect(
        screen.getByRole('button', { name: '同步所选剧集（0）' }),
      ).toBeDisabled()
    }
  })

  it('opens the separate free content sync modal', async () => {
    useCatalogHandlers()
    const user = userEvent.setup()
    renderPage()
    await screen.findByTestId('mock-row-8')

    await user.click(screen.getByTestId('drama-content-sync-all'))
    const modal = await screen.findByTestId('drama-content-sync-modal')
    expect(within(modal).getByText('GoodShort')).toBeInTheDocument()
    expect(within(modal).getByText('同步全部在线短剧')).toBeInTheDocument()
    expect(screen.queryByTestId('drama-sync-modal')).not.toBeInTheDocument()
  })

  it('refreshes the open detail after its content task succeeds', async () => {
    useCatalogHandlers()
    let detailRequests = 0
    let statusRequests = 0
    server.use(
      http.get('/api/admin/drama/catalog/8', () => {
        detailRequests += 1
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: {
            ...publishedDrama,
            description: null,
            createdAt: '2026-08-20T10:35:00',
            contents:
              detailRequests === 1
                ? []
                : [
                    {
                      id: 88,
                      externalContentId: 'episode-new',
                      sequenceNo: 1,
                      title: 'New Episode',
                      free: true,
                      durationSeconds: 90,
                      remoteUpdatedAt: '2026-08-29T08:01:00',
                    },
                  ],
          },
        })
      }),
      http.get('/api/admin/drama/catalog/8/contents/sync/status', () => {
        statusRequests += 1
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: contentTask(8, 'SUCCESS'),
        })
      }),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByTestId('mock-row-8')

    await user.click(screen.getByTestId('drama-detail-8'))
    expect(await screen.findByText('New Episode')).toBeInTheDocument()
    expect(detailRequests).toBe(2)
    expect(statusRequests).toBe(1)
  })

  it('refreshes current detail status after a row sync and stops it on close', async () => {
    useCatalogHandlers()
    let statusRequests = 0
    server.use(
      http.get('/api/admin/drama/catalog/8', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: {
            ...publishedDrama,
            description: null,
            createdAt: null,
            contents: [],
          },
        }),
      ),
      http.get('/api/admin/drama/catalog/8/contents/sync/status', () => {
        statusRequests += 1
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: contentTask(8, 'REQUESTED'),
        })
      }),
      http.post('/api/admin/drama/catalog/8/contents/sync', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: contentTask(8, 'REQUESTED'),
        }),
      ),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByTestId('mock-row-8')

    await user.click(screen.getByTestId('drama-detail-8'))
    await screen.findByText('等待执行')
    await user.click(screen.getByTestId('drama-content-sync-8'))
    await user.click(await screen.findByTestId('drama-content-sync-confirm-8'))
    await waitFor(() => expect(statusRequests).toBe(2))
    await user.click(screen.getByRole('button', { name: 'Close' }))
    await waitFor(() =>
      expect(
        screen.queryByTestId('drama-content-sync-section'),
      ).not.toBeInTheDocument(),
    )
  })
})
