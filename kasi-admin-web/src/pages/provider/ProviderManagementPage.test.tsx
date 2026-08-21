import React from 'react'
import {
  afterAll,
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import { App as AntdApp } from 'antd'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { useAuthStore } from '../../features/auth/authStore'
import { ProviderManagementPage } from './ProviderManagementPage'

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({
    title,
    content,
    children,
    ...rest
  }: React.PropsWithChildren<{
    title?: React.ReactNode
    content?: React.ReactNode
  }>) => (
    <section {...rest}>
      <h1>{title}</h1>
      <p>{content}</p>
      {children}
    </section>
  ),
}))

const server = setupServer()

const configuredProvider = {
  id: 1,
  providerCode: 'GOODSHORT',
  providerName: 'GoodShort',
  status: 1,
  capabilities: ['ACCOUNT_FILING'],
  connection: {
    id: 2,
    connectionName: 'GoodShort',
    baseUrl: 'https://api.novelopen.com/creek',
    partnerId: 'partner-1',
    currency: 'USD',
    status: 1,
    filingMode: 'API',
    credentialConfigured: true,
    createdAt: '2026-08-18T10:00:00',
    updatedAt: '2026-08-18T10:00:00',
  },
}

const unconfiguredProvider = { ...configuredProvider, connection: null }

const commissionRules = [
  {
    id: 7,
    providerId: 1,
    channelFeeRate: 30,
    principalFeeRate: 0,
    principalCommissionRate: 80,
    downstreamFeeRate: 0,
    downstreamCommissionRate: 70,
    effectiveFrom: '2099-01-01T00:00:00',
    effectiveTo: null,
    status: 'PENDING',
  },
  {
    id: 8,
    providerId: 1,
    channelFeeRate: 20,
    principalFeeRate: 5,
    principalCommissionRate: 75,
    downstreamFeeRate: 2,
    downstreamCommissionRate: 60,
    effectiveFrom: '2026-01-01T00:00:00',
    effectiveTo: '2026-06-01T00:00:00',
    status: 'ENDED',
  },
]

beforeAll(() => server.listen())
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

beforeEach(() => {
  useAuthStore.setState({
    accessToken: 'test-token',
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

function renderPage() {
  return render(
    <AntdApp>
      <ProviderManagementPage />
    </AntdApp>,
  )
}

describe('ProviderManagementPage', () => {
  it('shows a read-only API form to an ordinary administrator', async () => {
    useAuthStore.setState({
      admin: { ...useAuthStore.getState().admin!, isSuperAdmin: 0 },
    })
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: [configuredProvider],
        }),
      ),
    )

    renderPage()

    expect(await screen.findByText('GoodShort API 接入')).toBeInTheDocument()
    expect(screen.getByLabelText('接口 URL')).toBeDisabled()
    expect(screen.getByLabelText('PID')).toBeDisabled()
    expect(
      screen.queryByRole('button', { name: /提\s*交/ }),
    ).not.toBeInTheDocument()
  })

  it('submits only url pid status when retaining an existing key', async () => {
    let requestBody: Record<string, unknown> | undefined
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: [configuredProvider],
        }),
      ),
      http.put(
        '/api/admin/drama/providers/1/connection',
        async ({ request }) => {
          requestBody = (await request.json()) as Record<string, unknown>
          return HttpResponse.json({
            code: 0,
            message: '保存成功',
            data: configuredProvider.connection,
          })
        },
      ),
    )

    renderPage()
    await screen.findByText('GoodShort API 接入')
    expect(screen.getByLabelText('接口 URL')).toHaveValue(
      'https://api.novelopen.com/creek',
    )
    expect(screen.getByLabelText('KEY')).toHaveValue('')
    expect(screen.getByLabelText('API 自动报备')).toBeChecked()
    expect(document.querySelector('.ant-radio-wrapper')).toBeInTheDocument()
    expect(
      document.querySelector('.ant-radio-button-wrapper'),
    ).not.toBeInTheDocument()
    fireEvent.click(
      screen.getByLabelText('API 自动报备').parentElement as HTMLElement,
    )
    fireEvent.click(screen.getByRole('button', { name: /提\s*交/ }))

    await waitFor(() => expect(requestBody).toBeDefined())
    expect(requestBody).toEqual({
      baseUrl: 'https://api.novelopen.com/creek',
      partnerId: 'partner-1',
      status: 1,
      filingMode: 'API',
    })
  })

  it('requires url pid and key on the first setup', async () => {
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: [unconfiguredProvider],
        }),
      ),
    )

    renderPage()
    await screen.findByText('GoodShort API 接入')
    fireEvent.click(screen.getByRole('button', { name: /提\s*交/ }))

    expect(await screen.findByText('请输入接口 URL')).toBeInTheDocument()
    expect(screen.getByText('请输入 PID')).toBeInTheDocument()
    expect(screen.getByText('请输入 KEY')).toBeInTheDocument()
  })

  it('saves an unconfigured provider in manual filing mode without API credentials', async () => {
    let requestBody: Record<string, unknown> | undefined
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: [unconfiguredProvider],
        }),
      ),
      http.put(
        '/api/admin/drama/providers/1/connection',
        async ({ request }) => {
          requestBody = (await request.json()) as Record<string, unknown>
          return HttpResponse.json({
            code: 0,
            message: '保存成功',
            data: {
              ...configuredProvider.connection,
              baseUrl: null,
              partnerId: null,
              credentialConfigured: false,
              filingMode: 'MANUAL',
            },
          })
        },
      ),
    )

    renderPage()
    await screen.findByText('GoodShort API 接入')
    fireEvent.click(
      screen.getByLabelText('人工报备').parentElement as HTMLElement,
    )
    expect(screen.getByLabelText('人工报备')).toBeChecked()
    expect(screen.queryByLabelText('接口 URL')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('PID')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('KEY')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /提\s*交/ }))

    await waitFor(() =>
      expect(requestBody).toEqual({
        status: 1,
        filingMode: 'MANUAL',
      }),
    )
  })

  it('shows the connection test result for a saved enabled config', async () => {
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: [configuredProvider],
        }),
      ),
      http.post('/api/admin/drama/providers/1/connection/test', () =>
        HttpResponse.json({
          code: 0,
          message: '连接成功',
          data: {
            reachable: true,
            message: '连接成功',
            testedAt: '2026-08-18T10:00:00Z',
          },
        }),
      ),
    )

    renderPage()
    await screen.findByText('GoodShort API 接入')
    fireEvent.click(screen.getByRole('button', { name: '连接测试' }))

    expect(await screen.findByText(/GoodShort：连接成功/)).toBeInTheDocument()
    expect(screen.getByText('连接可达')).toBeInTheDocument()
  })

  it('loads platform commission rules and exposes super administrator actions', async () => {
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: [configuredProvider],
        }),
      ),
      http.get('/api/admin/drama/providers/1/commission-rules', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: commissionRules,
        }),
      ),
    )

    renderPage()

    expect(await screen.findByText('分佣规则')).toBeInTheDocument()
    expect((await screen.findAllByText('30%')).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: '新增规则' })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: '编辑' })).toHaveLength(1)
    expect(screen.getByText('已结束')).toBeInTheDocument()
  })

  it('keeps commission rules readable but hides write actions for ordinary administrators', async () => {
    useAuthStore.setState({
      admin: { ...useAuthStore.getState().admin!, isSuperAdmin: 0 },
    })
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: [configuredProvider],
        }),
      ),
      http.get('/api/admin/drama/providers/1/commission-rules', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: commissionRules,
        }),
      ),
    )

    renderPage()

    expect(await screen.findByText('分佣规则')).toBeInTheDocument()
    expect(await screen.findByText('30%')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: '新增规则' }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: '编辑' }),
    ).not.toBeInTheDocument()
  })

  it('creates a commission rule from the embedded provider configuration form', async () => {
    let requestBody: Record<string, unknown> | undefined
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({
          code: 0,
          message: '查询成功',
          data: [configuredProvider],
        }),
      ),
      http.get('/api/admin/drama/providers/1/commission-rules', () =>
        HttpResponse.json({ code: 0, message: '查询成功', data: [] }),
      ),
      http.post(
        '/api/admin/drama/providers/1/commission-rules',
        async ({ request }) => {
          requestBody = (await request.json()) as Record<string, unknown>
          return HttpResponse.json({
            code: 0,
            message: '创建成功',
            data: commissionRules[0],
          })
        },
      ),
    )

    renderPage()
    await screen.findByText('分佣规则')
    fireEvent.click(screen.getByRole('button', { name: '新增规则' }))
    fireEvent.change(screen.getByLabelText('渠道费率'), {
      target: { value: '30' },
    })
    fireEvent.change(screen.getByLabelText('甲方手续费率'), {
      target: { value: '0' },
    })
    fireEvent.change(screen.getByLabelText('甲方分佣比例'), {
      target: { value: '80' },
    })
    fireEvent.change(screen.getByLabelText('我方手续费率'), {
      target: { value: '0' },
    })
    fireEvent.change(screen.getByLabelText('下游分佣比例'), {
      target: { value: '70' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'OK' }))

    await waitFor(() => expect(requestBody).toBeDefined())
    expect(requestBody).toMatchObject({
      channelFeeRate: 30,
      principalFeeRate: 0,
      principalCommissionRate: 80,
      downstreamFeeRate: 0,
      downstreamCommissionRate: 70,
      effectiveTo: null,
    })
  })
})
