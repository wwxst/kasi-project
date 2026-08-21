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
})
