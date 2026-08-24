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
import { CommissionRulePage } from './CommissionRulePage'

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
      realName: '管理员',
      mobile: null,
      email: null,
      avatarUrl: null,
      isSuperAdmin: 1,
    },
  }),
)

const providers = [
  {
    id: 1,
    providerCode: 'GOODSHORT',
    providerName: 'GoodShort',
    status: 1,
    capabilities: [],
    connection: null,
  },
  {
    id: 2,
    providerCode: 'OTHER',
    providerName: 'Other',
    status: 1,
    capabilities: [],
    connection: null,
  },
]
const rule = {
  id: 7,
  providerId: 1,
  channelFeeRate: 30,
  principalFeeRate: 0,
  principalCommissionRate: 80,
  downstreamFeeRate: 0,
  downstreamCommissionRate: 70,
}

function renderPage() {
  return render(
    <AntdApp>
      <CommissionRulePage />
    </AntdApp>,
  )
}

describe('CommissionRulePage', () => {
  it('shows platform rows and unconfigured providers', async () => {
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: providers }),
      ),
      http.get('/api/admin/drama/providers/1/commission-rules', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [rule] }),
      ),
      http.get('/api/admin/drama/providers/2/commission-rules', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [] }),
      ),
    )
    renderPage()
    expect(
      await screen.findByText(/GoodShort（GOODSHORT）/),
    ).toBeInTheDocument()
    expect(screen.getByText(/Other（OTHER）/)).toBeInTheDocument()
    expect(screen.getByText('未配置')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '编辑' })).toBeInTheDocument()
  })

  it('saves a new default rule with five rates', async () => {
    let body: unknown
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: providers }),
      ),
      http.get('/api/admin/drama/providers/1/commission-rules', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [rule] }),
      ),
      http.get('/api/admin/drama/providers/2/commission-rules', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [] }),
      ),
      http.post(
        '/api/admin/drama/providers/2/commission-rules',
        async ({ request }) => {
          body = await request.json()
          return HttpResponse.json({
            code: 0,
            message: 'ok',
            data: { ...rule, id: 8, providerId: 2 },
          })
        },
      ),
    )
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: '设置' }))
    fireEvent.click(screen.getByRole('button', { name: 'OK' }))
    await waitFor(() =>
      expect(body).toEqual({
        channelFeeRate: 0,
        principalFeeRate: 0,
        principalCommissionRate: 0,
        downstreamFeeRate: 0,
        downstreamCommissionRate: 0,
      }),
    )
  })

  it('hides write actions for ordinary administrators', async () => {
    useAuthStore.setState({
      admin: { ...useAuthStore.getState().admin!, isSuperAdmin: 0 },
    })
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: providers }),
      ),
      http.get('/api/admin/drama/providers/1/commission-rules', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [rule] }),
      ),
      http.get('/api/admin/drama/providers/2/commission-rules', () =>
        HttpResponse.json({ code: 0, message: 'ok', data: [] }),
      ),
    )
    renderPage()
    expect(
      await screen.findByText(/GoodShort（GOODSHORT）/),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: '编辑' }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: '设置默认规则' }),
    ).not.toBeInTheDocument()
  })
})
