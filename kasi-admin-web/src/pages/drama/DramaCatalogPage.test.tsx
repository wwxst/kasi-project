import {
  cleanup,
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
})
