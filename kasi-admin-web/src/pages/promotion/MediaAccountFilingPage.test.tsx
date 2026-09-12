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

  const ProTable = ({
    actionRef,
    columns,
    request,
    rowKey,
    rowSelection,
    toolBarRender,
  }: any) => {
    const [rows, setRows] = React.useState<any[]>([])
    const currentParamsRef = React.useRef({
      current: 3,
      pageSize: 1,
      userNo: '583104726918',
      mediaType: 'TIKTOK',
      accountStatus: 1,
      providerId: 7,
      filingMethod: 'MANUAL',
      filingStatus: 'REJECTED',
    })

    const loadRows = React.useCallback(
      async (params = currentParamsRef.current) => {
        currentParamsRef.current = params
        const result = await request(params)
        setRows(result.data ?? [])
      },
      [request],
    )

    React.useEffect(() => {
      actionRef.current = { reload: loadRows }
      void loadRows()
      return () => {
        actionRef.current = undefined
      }
    }, [actionRef, loadRows])

    return (
      <div data-testid="mock-pro-table">
        <div data-testid="mock-toolbar">
          {typeof toolBarRender === 'function' ? toolBarRender() : null}
          <button
            type="button"
            onClick={() =>
              void loadRows({
                ...currentParamsRef.current,
                current: currentParamsRef.current.current + 1,
              })
            }
          >
            模拟下一页
          </button>
          <output data-testid="mock-selected-row-keys">
            {(rowSelection?.selectedRowKeys ?? []).join(',')}
          </output>
        </div>
        {columns
          .filter((column: any) => column.valueEnum)
          .map((column: any) => (
            <div
              key={`filter-${column.dataIndex}`}
              data-testid={`filter-${column.dataIndex}`}
            >
              {Object.values(column.valueEnum).map((option: any) => (
                <span key={option.text}>{option.text}</span>
              ))}
            </div>
          ))}
        {rows.map((row) => {
          const key = typeof rowKey === 'function' ? rowKey(row) : row[rowKey]
          const selectedRowKeys = rowSelection?.selectedRowKeys ?? []
          const checked = selectedRowKeys.includes(key)
          return (
            <div key={row.id} data-testid={`mock-row-${row.id}`}>
              {rowSelection ? (
                <input
                  type="checkbox"
                  aria-label={`选择账号 ${row.id}`}
                  checked={checked}
                  disabled={rowSelection.getCheckboxProps?.(row)?.disabled}
                  onChange={(event) => {
                    const nextKeys = event.target.checked
                      ? [...selectedRowKeys, key]
                      : selectedRowKeys.filter(
                          (selectedKey: React.Key) => selectedKey !== key,
                        )
                    rowSelection.onChange?.(
                      nextKeys,
                      rows.filter((candidate) => {
                        const candidateKey =
                          typeof rowKey === 'function'
                            ? rowKey(candidate)
                            : candidate[rowKey]
                        return nextKeys.includes(candidateKey)
                      }),
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
          )
        })}
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
  filingMethod: 'API',
  filingStatus: 'SUBMIT_FAILED',
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
        filingMethod: 'API',
        status: 'SUBMIT_FAILED',
        remoteStatus: null,
        externalFilingId: null,
        filingTime: null,
        operateTime: null,
        nextActionAt: null,
        lastSubmittedAt: null,
        lastQueriedAt: null,
        lastErrorCode: 'SUBMIT_CONFIRMED_NOT_RECEIVED',
        lastErrorMessage: '上报接口超时',
        retryAllowed: true,
        submissionResolutionAllowed: false,
      },
      {
        providerId: 2,
        providerName: 'Other Provider',
        filingMethod: 'API',
        status: 'REJECTED',
        remoteStatus: '2',
        externalFilingId: null,
        filingTime: '2026-08-18T10:00:00',
        operateTime: '2026-08-18T10:10:00',
        nextActionAt: null,
        lastSubmittedAt: '2026-08-18T10:00:00',
        lastQueriedAt: '2026-08-18T10:10:00',
        lastErrorCode: null,
        lastErrorMessage: null,
        retryAllowed: false,
        submissionResolutionAllowed: false,
      },
    ],
  },
}

describe('MediaAccountFilingPage', () => {
  it('exports Excel with the current filing filters and filename', async () => {
    let exportUrl: URL | undefined
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [] }),
      ),
      http.get('/api/admin/promotion/media-accounts', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: { list: [], page: 3, size: 1, total: 0 },
        }),
      ),
      http.get(
        '/api/admin/promotion/media-accounts/export.xlsx',
        ({ request }) => {
          exportUrl = new URL(request.url)
          return new HttpResponse(new Blob(['xlsx']))
        },
      ),
    )
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:media-account-filings'),
    })
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn(),
    })
    const anchorClick = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined)
    const user = userEvent.setup()
    render(
      <AntdApp>
        <MediaAccountFilingPage />
      </AntdApp>,
    )

    await user.click(await screen.findByRole('button', { name: '导出 Excel' }))
    await waitFor(() => expect(exportUrl).toBeDefined())
    expect(exportUrl?.searchParams.get('userNo')).toBe('583104726918')
    expect(exportUrl?.searchParams.get('providerId')).toBe('7')
    expect(exportUrl?.searchParams.get('filingMethod')).toBe('MANUAL')
    expect(exportUrl?.searchParams.get('filingStatus')).toBe('REJECTED')
    const download = anchorClick.mock.instances.at(-1) as HTMLAnchorElement
    expect(download.download).toBe('media-account-filings.xlsx')
  })

  it('shows details and lets an administrator retry failed API filings', async () => {
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

    expect(await screen.findByText('TikTok 运营号')).toBeInTheDocument()
    const statusFilter = screen.getByTestId('filter-filingStatus')
    for (const label of ['待提交', '审核中', '已加白', '未通过', '提交失败']) {
      expect(within(statusFilter).getByText(label)).toBeInTheDocument()
    }
    const methodFilter = screen.getByTestId('filter-filingMethod')
    expect(within(methodFilter).getByText('API')).toBeInTheDocument()
    expect(within(methodFilter).getByText('人工')).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: '媒体账号报备' }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByText('查看推广用户媒体账号及短剧平台报备状态'),
    ).not.toBeInTheDocument()
    expect(screen.queryByText('新增')).not.toBeInTheDocument()
    expect(screen.queryByText('删除')).not.toBeInTheDocument()

    await user.click(screen.getByTestId('media-account-detail-8'))
    const drawer = await screen.findByText('媒体账号详情')
    expect(drawer).toBeInTheDocument()
    const drawerElement = screen
      .getByText('媒体账号详情')
      .closest('.ant-drawer') as HTMLElement
    expect(within(drawerElement).getByText('提交失败')).toBeInTheDocument()
    expect(within(drawerElement).getByText('未通过')).toBeInTheDocument()
    expect(
      await screen.findByText('失败原因：上报接口超时'),
    ).toBeInTheDocument()

    const retryButton = screen.getByRole('button', { name: '重新提交' })
    expect(screen.getAllByRole('button', { name: '重新提交' })).toHaveLength(1)
    await user.click(retryButton)
    await waitFor(() => expect(retryCalled).toBe(true))
    expect(within(drawerElement).getByText('未通过')).toBeInTheDocument()
    expect(
      within(drawerElement).queryByRole('button', { name: '删除' }),
    ).not.toBeInTheDocument()
  })

  it('filters manual filing outcomes by the current row status', async () => {
    const manualBodies: unknown[] = []
    let resolutionBody: unknown
    const actionDetail = {
      ...detail,
      mediaAccount: {
        ...detail.mediaAccount,
        filings: [
          {
            ...detail.mediaAccount.filings[0],
            providerId: 1,
            providerName: 'Manual Approved',
            filingMethod: 'MANUAL',
            status: 'APPROVED',
            lastErrorCode: null,
            lastErrorMessage: null,
            retryAllowed: false,
          },
          {
            ...detail.mediaAccount.filings[0],
            providerId: 2,
            providerName: 'Manual Rejected',
            filingMethod: 'MANUAL',
            status: 'REJECTED',
            lastErrorCode: null,
            lastErrorMessage: null,
            retryAllowed: false,
          },
          {
            ...detail.mediaAccount.filings[0],
            providerId: 3,
            providerName: 'Unknown API',
            filingMethod: 'API',
            status: 'SUBMIT_FAILED',
            lastErrorCode: 'SUBMIT_OUTCOME_UNKNOWN',
            lastErrorMessage: 'timeout',
            retryAllowed: false,
            submissionResolutionAllowed: true,
          },
          {
            ...detail.mediaAccount.filings[0],
            providerId: 4,
            providerName: 'Pending API',
            filingMethod: 'API',
            status: 'PENDING',
            lastErrorCode: null,
            lastErrorMessage: null,
            retryAllowed: false,
            submissionResolutionAllowed: false,
          },
        ],
      },
    }
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [] }),
      ),
      http.get('/api/admin/promotion/media-accounts', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: {
            list: [
              { ...listItem, filingMethod: 'MANUAL', filingStatus: 'APPROVED' },
              {
                ...listItem,
                id: 9,
                providerId: 2,
                filingMethod: 'MANUAL',
                filingStatus: 'REJECTED',
              },
              {
                ...listItem,
                id: 10,
                providerId: 3,
                filingMethod: 'MANUAL',
                filingStatus: 'PENDING',
              },
              {
                ...listItem,
                id: 11,
                providerId: 4,
                filingMethod: 'MANUAL',
                filingStatus: 'NOT_SUBMITTED',
              },
              {
                ...listItem,
                id: 12,
                providerId: 5,
                filingMethod: 'MANUAL',
                filingStatus: 'SUBMIT_FAILED',
              },
              {
                ...listItem,
                id: 13,
                providerId: 6,
                filingMethod: 'API',
                filingStatus: 'PENDING',
              },
            ],
            page: 1,
            size: 20,
            total: 6,
          },
        }),
      ),
      http.get('/api/admin/promotion/media-accounts/8', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: actionDetail }),
      ),
      http.patch(
        '/api/admin/promotion/media-accounts/8/filings/1/status',
        async ({ request }) => {
          manualBodies.push(await request.json())
          return HttpResponse.json({ code: 0, message: 'ok', data: {} })
        },
      ),
      http.patch(
        '/api/admin/promotion/media-accounts/9/filings/2/status',
        async ({ request }) => {
          manualBodies.push(await request.json())
          return HttpResponse.json({ code: 0, message: 'ok', data: {} })
        },
      ),
      http.post(
        '/api/admin/promotion/media-accounts/8/filings/3/submission-resolution',
        async ({ request }) => {
          resolutionBody = await request.json()
          return HttpResponse.json({ code: 0, message: 'ok', data: {} })
        },
      ),
    )

    const user = userEvent.setup()
    render(
      <AntdApp>
        <MediaAccountFilingPage />
      </AntdApp>,
    )
    const more = await screen.findByTestId('media-account-more-8')
    await user.click(more)
    expect
      .soft(screen.queryByRole('menuitem', { name: '报白通过' }))
      .not.toBeInTheDocument()
    expect(
      await screen.findByRole('menuitem', { name: '报白未通过' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('menuitem', { name: '标记已提交' }),
    ).not.toBeInTheDocument()

    await user.click(screen.getByRole('menuitem', { name: '报白未通过' }))
    await waitFor(() =>
      expect(manualBodies).toContainEqual({ status: 'REJECTED' }),
    )

    await user.click(screen.getByTestId('media-account-more-9'))
    const rejectedRowMenu = (await screen.findAllByRole('menu')).at(
      -1,
    ) as HTMLElement
    expect(
      within(rejectedRowMenu).getByRole('menuitem', { name: '报白通过' }),
    ).toBeInTheDocument()
    expect
      .soft(
        within(rejectedRowMenu).queryByRole('menuitem', {
          name: '报白未通过',
        }),
      )
      .not.toBeInTheDocument()
    await user.click(
      within(rejectedRowMenu).getByRole('menuitem', { name: '报白通过' }),
    )
    await waitFor(() =>
      expect(manualBodies).toContainEqual({ status: 'APPROVED' }),
    )

    for (const id of [10, 11, 12]) {
      await user.click(screen.getByTestId(`media-account-more-${id}`))
      const menu = (await screen.findAllByRole('menu')).at(-1) as HTMLElement
      expect(
        within(menu).getByRole('menuitem', { name: '报白通过' }),
      ).toBeInTheDocument()
      expect(
        within(menu).getByRole('menuitem', { name: '报白未通过' }),
      ).toBeInTheDocument()
    }

    await user.click(screen.getByTestId('media-account-more-13'))
    const apiRowMenu = (await screen.findAllByRole('menu')).at(
      -1,
    ) as HTMLElement
    expect(
      within(apiRowMenu).queryByRole('menuitem', { name: '报白通过' }),
    ).not.toBeInTheDocument()
    expect(
      within(apiRowMenu).queryByRole('menuitem', { name: '报白未通过' }),
    ).not.toBeInTheDocument()
    expect(
      within(apiRowMenu).getByRole('menuitem', { name: '删除' }),
    ).toBeInTheDocument()

    await user.click(await screen.findByTestId('media-account-detail-8'))
    const drawerElement = (await screen.findByText('媒体账号详情')).closest(
      '.ant-drawer',
    ) as HTMLElement
    expect(
      within(drawerElement).queryByRole('button', { name: '报白通过' }),
    ).not.toBeInTheDocument()
    expect(
      within(drawerElement).queryByRole('button', { name: '报白未通过' }),
    ).not.toBeInTheDocument()
    const unknown = screen.getByTestId('filing-3')
    expect(
      within(unknown).getByRole('button', { name: '确认已收到' }),
    ).toBeInTheDocument()
    expect(
      within(unknown).getByRole('button', { name: '确认未收到' }),
    ).toBeInTheDocument()
    expect(
      within(screen.getByTestId('filing-4')).queryByRole('button'),
    ).not.toBeInTheDocument()

    await user.click(
      within(unknown).getByRole('button', { name: '确认已收到' }),
    )
    await waitFor(() =>
      expect(resolutionBody).toEqual({ resolution: 'RECEIVED' }),
    )
  })

  it('selects only manual filings and bulk approves actionable rows', async () => {
    const requests: Array<{
      accountId: string
      providerId: string
      body: unknown
    }> = []
    const rows = [
      { ...listItem, filingMethod: 'MANUAL', filingStatus: 'APPROVED' },
      {
        ...listItem,
        id: 9,
        providerId: 2,
        filingMethod: 'MANUAL',
        filingStatus: 'REJECTED',
      },
      {
        ...listItem,
        id: 10,
        providerId: 3,
        filingMethod: 'MANUAL',
        filingStatus: 'PENDING',
      },
      {
        ...listItem,
        id: 13,
        providerId: 6,
        filingMethod: 'API',
        filingStatus: 'PENDING',
      },
      {
        ...listItem,
        id: 14,
        providerId: null,
        filingMethod: 'MANUAL',
        filingStatus: 'PENDING',
      },
    ]
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [] }),
      ),
      http.get('/api/admin/promotion/media-accounts', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: { list: rows, page: 1, size: 20, total: rows.length },
        }),
      ),
      http.patch(
        '/api/admin/promotion/media-accounts/:accountId/filings/:providerId/status',
        async ({ params, request }) => {
          requests.push({
            accountId: String(params.accountId),
            providerId: String(params.providerId),
            body: await request.json(),
          })
          return HttpResponse.json({ code: 0, message: 'ok', data: {} })
        },
      ),
    )

    const user = userEvent.setup()
    render(
      <AntdApp>
        <MediaAccountFilingPage />
      </AntdApp>,
    )

    const approveButton = screen.getByRole('button', { name: '批量通过' })
    const rejectButton = screen.getByRole('button', { name: '批量未通过' })
    expect(approveButton).toBeDisabled()
    expect(rejectButton).toBeDisabled()
    expect(
      await screen.findByRole('checkbox', { name: '选择账号 8' }),
    ).toBeEnabled()
    expect(screen.getByRole('checkbox', { name: '选择账号 13' })).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: '选择账号 14' })).toBeDisabled()

    await user.click(screen.getByRole('checkbox', { name: '选择账号 8' }))
    await user.click(screen.getByRole('checkbox', { name: '选择账号 9' }))
    await user.click(screen.getByRole('checkbox', { name: '选择账号 10' }))
    expect(approveButton).toBeEnabled()
    expect(rejectButton).toBeEnabled()

    await user.click(approveButton)
    const approveDialog = await screen.findByRole('dialog', {
      name: '确认将 2 条记录设为报白通过？',
    })
    await user.click(
      within(approveDialog).getByRole('button', { name: /确\s*认/ }),
    )

    await waitFor(() => expect(requests).toHaveLength(2))
    expect(requests).toEqual([
      { accountId: '9', providerId: '2', body: { status: 'APPROVED' } },
      { accountId: '10', providerId: '3', body: { status: 'APPROVED' } },
    ])
    expect(await screen.findByText('已批量更新 2 条记录')).toBeInTheDocument()
    await waitFor(() => {
      expect(
        screen.getByRole('checkbox', { name: '选择账号 8' }),
      ).not.toBeChecked()
      expect(
        screen.getByRole('checkbox', { name: '选择账号 9' }),
      ).not.toBeChecked()
      expect(
        screen.getByRole('checkbox', { name: '选择账号 10' }),
      ).not.toBeChecked()
    })
  })

  it('keeps only failed rows selected after a partial bulk rejection', async () => {
    const requests: string[] = []
    const rows = [
      { ...listItem, filingMethod: 'MANUAL', filingStatus: 'APPROVED' },
      {
        ...listItem,
        id: 10,
        providerId: 3,
        filingMethod: 'MANUAL',
        filingStatus: 'PENDING',
      },
    ]
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [] }),
      ),
      http.get('/api/admin/promotion/media-accounts', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: { list: rows, page: 1, size: 20, total: rows.length },
        }),
      ),
      http.patch(
        '/api/admin/promotion/media-accounts/:accountId/filings/:providerId/status',
        ({ params }) => {
          const accountId = String(params.accountId)
          requests.push(accountId)
          return accountId === '10'
            ? HttpResponse.json(
                { code: 1000, message: '状态已变化', data: null },
                { status: 409 },
              )
            : HttpResponse.json({ code: 0, message: 'ok', data: {} })
        },
      ),
    )

    const user = userEvent.setup()
    render(
      <AntdApp>
        <MediaAccountFilingPage />
      </AntdApp>,
    )

    await user.click(
      await screen.findByRole('checkbox', { name: '选择账号 8' }),
    )
    await user.click(screen.getByRole('checkbox', { name: '选择账号 10' }))
    await user.click(screen.getByRole('button', { name: '批量未通过' }))
    const rejectDialog = await screen.findByRole('dialog', {
      name: '确认将 2 条记录设为报白未通过？',
    })
    await user.click(
      within(rejectDialog).getByRole('button', { name: /确\s*认/ }),
    )

    await waitFor(() => expect(requests).toEqual(['8', '10']))
    expect(
      await screen.findByText('批量更新完成：成功 1 条，失败 1 条'),
    ).toBeInTheDocument()
    await waitFor(() => {
      expect(
        screen.getByRole('checkbox', { name: '选择账号 8' }),
      ).not.toBeChecked()
      expect(
        screen.getByRole('checkbox', { name: '选择账号 10' }),
      ).toBeChecked()
    })
  })

  it('does not restore stale failed rows after the query changes during a batch', async () => {
    let patchStarted = false
    let finishPatch: (() => void) | undefined
    const patchGate = new Promise<void>((resolve) => {
      finishPatch = resolve
    })
    const firstPageRow = {
      ...listItem,
      filingMethod: 'MANUAL',
      filingStatus: 'APPROVED',
    }
    const nextPageRow = {
      ...listItem,
      id: 20,
      providerId: 2,
      filingMethod: 'MANUAL',
      filingStatus: 'PENDING',
    }
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [] }),
      ),
      http.get('/api/admin/promotion/media-accounts', ({ request }) => {
        const page = new URL(request.url).searchParams.get('page')
        const rows = page === '4' ? [nextPageRow] : [firstPageRow]
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: { list: rows, page: Number(page), size: 1, total: 2 },
        })
      }),
      http.patch(
        '/api/admin/promotion/media-accounts/8/filings/1/status',
        async () => {
          patchStarted = true
          await patchGate
          return HttpResponse.json(
            { code: 1000, message: '状态已变化', data: null },
            { status: 409 },
          )
        },
      ),
    )

    const user = userEvent.setup()
    render(
      <AntdApp>
        <MediaAccountFilingPage />
      </AntdApp>,
    )

    await user.click(
      await screen.findByRole('checkbox', { name: '选择账号 8' }),
    )
    await user.click(screen.getByRole('button', { name: '批量未通过' }))
    const rejectDialog = await screen.findByRole('dialog', {
      name: '确认将 1 条记录设为报白未通过？',
    })
    await user.click(
      within(rejectDialog).getByRole('button', { name: /确\s*认/ }),
    )
    await waitFor(() => expect(patchStarted).toBe(true))

    await user.click(screen.getByRole('button', { name: '模拟下一页' }))
    expect(
      await screen.findByRole('checkbox', { name: '选择账号 20' }),
    ).not.toBeChecked()
    expect(
      screen.queryByRole('checkbox', { name: '选择账号 8' }),
    ).not.toBeInTheDocument()
    expect(screen.getByTestId('mock-selected-row-keys')).toBeEmptyDOMElement()

    finishPatch?.()

    expect(
      await screen.findByText('批量更新完成：成功 0 条，失败 1 条'),
    ).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByTestId('mock-selected-row-keys')).toBeEmptyDOMElement()
      expect(screen.getByRole('button', { name: '批量通过' })).toBeDisabled()
      expect(screen.getByRole('button', { name: '批量未通过' })).toBeDisabled()
    })
  })

  it('offers local-only deletion for an API filing that is still pending', async () => {
    let deleteCalled = false
    const pendingDetail = {
      ...detail,
      mediaAccount: {
        ...detail.mediaAccount,
        filings: [
          {
            ...detail.mediaAccount.filings[0],
            filingMethod: 'API',
            status: 'PENDING',
            nextActionAt: '2026-08-18T11:00:00',
            lastErrorCode: null,
            lastErrorMessage: null,
            retryAllowed: false,
          },
        ],
      },
    }
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [] }),
      ),
      http.get('/api/admin/promotion/media-accounts', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: { list: [listItem], page: 1, size: 20, total: 1 },
        }),
      ),
      http.get('/api/admin/promotion/media-accounts/8', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: pendingDetail }),
      ),
      http.delete('/api/admin/promotion/media-accounts/8', () => {
        deleteCalled = true
        return HttpResponse.json({ code: 0, message: 'ok', data: null })
      }),
    )

    const user = userEvent.setup()
    render(
      <AntdApp>
        <MediaAccountFilingPage />
      </AntdApp>,
    )
    await user.click(await screen.findByTestId('media-account-more-8'))
    await user.click(await screen.findByRole('menuitem', { name: '删除' }))

    expect(
      await screen.findByText('仅删除本系统账号及报白记录，不会删除甲方记录。'),
    ).toBeInTheDocument()
    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /删\s*除/ }))
    await waitFor(() => expect(deleteCalled).toBe(true))
  })
})
