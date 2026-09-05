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

  const ProTable = ({ columns, request }: any) => {
    const [rows, setRows] = React.useState<any[]>([])
    React.useEffect(() => {
      void request({ current: 1, pageSize: 20 }).then((result: any) => {
        setRows(result.data ?? [])
      })
    }, [request])
    return (
      <div data-testid="mock-pro-table">
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

import { MediaAccountFilingPage } from './MediaAccountFilingPage'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

const listItem = {
  id: 8,
  userNo: '123456789012',
  nickname: '测试用户',
  realName: '张三',
  mediaType: 'TIKTOK',
  externalAccountId: 'creator-1001',
  accountName: 'TikTok 运营号',
  providerId: 1,
  status: 1,
  filingStatus: 'FAILED',
  filingRemoteStatus: null,
  filingLastSubmittedAt: null,
  filingLastErrorMessage: '上报接口超时',
  updatedAt: '2026-08-18T10:00:00',
}

const detail = {
  id: 8,
  userNo: '123456789012',
  nickname: '测试用户',
  realName: '张三',
  mediaAccount: {
    id: 8,
    mediaType: 'TIKTOK',
    externalAccountId: 'creator-1001',
    accountName: 'TikTok 运营号',
    accountLink: 'https://www.tiktok.com/@creator-1001',
    status: 1,
    createdAt: '2026-08-18T09:00:00',
    updatedAt: '2026-08-18T10:00:00',
    filings: [
      {
        providerId: 1,
        providerName: 'GoodShort',
        status: 'FAILED',
        remoteStatus: null,
        externalFilingId: null,
        filingTime: null,
        operateTime: null,
        nextActionAt: null,
        lastSubmittedAt: null,
        lastQueriedAt: null,
        lastErrorCode: 'REMOTE_TRANSIENT',
        lastErrorMessage: '上报接口超时',
      },
      {
        providerId: 2,
        providerName: 'Other Provider',
        status: 'FAILED',
        remoteStatus: '2',
        externalFilingId: null,
        filingTime: '2026-08-18T10:00:00',
        operateTime: '2026-08-18T10:10:00',
        nextActionAt: null,
        lastSubmittedAt: '2026-08-18T10:00:00',
        lastQueriedAt: '2026-08-18T10:10:00',
        lastErrorCode: null,
        lastErrorMessage: null,
      },
    ],
  },
}

describe('MediaAccountFilingPage', () => {
  it('shows details and retry without add or delete actions', async () => {
    let retryCalled = false
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: [
            { id: 1, providerName: 'GoodShort', providerCode: 'GOODSHORT' },
            {
              id: 2,
              providerName: 'Other Provider',
              providerCode: 'OTHER',
            },
          ],
        }),
      ),
      http.get('/api/admin/promotion/media-accounts', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: { list: [listItem], page: 1, size: 20, total: 1 },
        }),
      ),
      http.get('/api/admin/promotion/media-accounts/8', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: detail }),
      ),
      http.post('/api/admin/promotion/media-accounts/8/filings/1/retry', () => {
        retryCalled = true
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: { ...detail.mediaAccount.filings[0], status: 'PENDING' },
        })
      }),
    )

    const user = userEvent.setup()
    render(
      <AntdApp>
        <MediaAccountFilingPage />
      </AntdApp>,
    )

    expect(await screen.findByText('媒体账号报备')).toBeInTheDocument()
    expect(await screen.findByText('TikTok 运营号')).toBeInTheDocument()
    expect(screen.queryByText('新增')).not.toBeInTheDocument()
    expect(screen.queryByText('删除')).not.toBeInTheDocument()

    await user.click(screen.getByTestId('media-account-detail-8'))
    const drawer = await screen.findByText('媒体账号详情')
    expect(drawer).toBeInTheDocument()
    const drawerElement = screen
      .getByText('媒体账号详情')
      .closest('.ant-drawer') as HTMLElement
    expect(within(drawerElement).getByText('提交失败')).toBeInTheDocument()
    expect(within(drawerElement).getByText('已拒绝')).toBeInTheDocument()
    expect(
      await screen.findByText('失败原因：上报接口超时'),
    ).toBeInTheDocument()

    const retryButton = screen.getByRole('button', { name: '重新提交' })
    expect(screen.getAllByRole('button', { name: '重新提交' })).toHaveLength(1)
    await user.click(retryButton)
    await waitFor(() => expect(retryCalled).toBe(true))
    expect(within(drawerElement).getByText('已拒绝')).toBeInTheDocument()
  })
})
