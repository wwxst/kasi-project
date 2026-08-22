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
import { cleanup, render, screen } from '@testing-library/react'
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
  }: React.PropsWithChildren<{
    title?: React.ReactNode
    content?: React.ReactNode
  }>) => (
    <section>
      <h1>{title}</h1>
      <p>{content}</p>
      {children}
    </section>
  ),
}))

const server = setupServer()
beforeAll(() => server.listen())
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())
beforeEach(() =>
  useAuthStore.setState({
    accessToken: 'token',
    admin: {
      id: 1,
      username: 'admin',
      realName: 'admin',
      mobile: null,
      email: null,
      avatarUrl: null,
      isSuperAdmin: 1,
    },
  }),
)

const provider = {
  id: 1,
  providerCode: 'GOODSHORT',
  providerName: 'GoodShort',
  status: 1,
  capabilities: ['ACCOUNT_FILING'],
  connection: {
    id: 2,
    connectionName: 'GoodShort',
    baseUrl: 'https://api.test',
    partnerId: 'partner',
    currency: 'USD',
    status: 1,
    filingMode: 'API',
    credentialConfigured: true,
    createdAt: '2026-08-18T10:00:00',
    updatedAt: '2026-08-18T10:00:00',
  },
}

describe('ProviderManagementPage', () => {
  it('renders the API configuration without an embedded commission section', async () => {
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [provider] }),
      ),
    )
    render(
      <AntdApp>
        <ProviderManagementPage />
      </AntdApp>,
    )
    expect(await screen.findByText('GoodShort API 接入')).toBeInTheDocument()
    expect(screen.queryByText('分佣规则')).not.toBeInTheDocument()
  })

  it('keeps API configuration read-only for ordinary administrators', async () => {
    useAuthStore.setState({
      admin: { ...useAuthStore.getState().admin!, isSuperAdmin: 0 },
    })
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [provider] }),
      ),
    )
    render(
      <AntdApp>
        <ProviderManagementPage />
      </AntdApp>,
    )
    expect(await screen.findByText('GoodShort API 接入')).toBeInTheDocument()
    expect(screen.getByLabelText('接口 URL')).toBeDisabled()
  })
})
