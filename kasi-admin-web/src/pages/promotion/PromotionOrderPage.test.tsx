import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App as AntdApp } from 'antd'
import { HttpResponse, http } from 'msw'
import { setupServer } from 'msw/node'
import React from 'react'
import {
  afterAll,
  afterEach,
  beforeAll,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

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
import { PromotionOrderPage } from './PromotionOrderPage'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

describe('PromotionOrderPage', () => {
  it('lists orders and manually synchronizes a selected provider window', async () => {
    let syncBody: unknown
    const orderRequestUrls: string[] = []
    server.use(
      http.get('/api/admin/drama/providers', () =>
        HttpResponse.json({
          code: 0,
          message: 'ok',
          data: [
            {
              id: 1,
              providerCode: 'GOODSHORT',
              providerName: 'GoodShort',
              status: 1,
              capabilities: ['ORDER_SYNC'],
              connection: { id: 11, currency: 'USD', status: 1 },
            },
          ],
        }),
      ),
      http.get('/api/admin/promotion/orders', ({ request }) => {
        orderRequestUrls.push(request.url)
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: {
            list: [
              {
                id: 7,
                providerId: 1,
                externalOrderId: 'order-7',
                orderAmount: 19.98,
                currency: 'USD',
                status: 'PAID',
                paidAt: '2025-07-01T10:00:00',
                trackingNo: 'tracking-7',
                attributionStatus: 'ATTRIBUTED',
                commissionAmount: 9.59,
                commissionStatus: 'CALCULATED',
                lastSyncedAt: '2026-08-24T12:00:00',
              },
            ],
            page: 1,
            size: 20,
            total: 1,
          },
        })
      }),
      http.post('/api/admin/promotion/orders/sync', async ({ request }) => {
        syncBody = await request.json()
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: {
            fetchedCount: 5,
            insertedCount: 3,
            updatedCount: 2,
            unattributedCount: 1,
          },
        })
      }),
    )

    const user = userEvent.setup()
    render(
      <AntdApp>
        <PromotionOrderPage />
      </AntdApp>,
    )

    expect(
      await screen.findByRole('heading', { name: '推广订单' }),
    ).toBeInTheDocument()
    expect(await screen.findByText('order-7')).toBeInTheDocument()
    expect(screen.getByText('tracking-7')).toBeInTheDocument()
    expect(screen.getByText('$19.98')).toBeInTheDocument()
    expect(screen.getByText('$9.59')).toBeInTheDocument()

    await user.type(
      screen.getByLabelText('支付开始时间'),
      '2025-07-01 00:00:00',
    )
    await user.type(
      screen.getByLabelText('支付结束时间'),
      '2025-07-01 23:59:59',
    )
    await user.click(screen.getByRole('button', { name: '查询' }))
    await waitFor(() => expect(orderRequestUrls).toHaveLength(2))
    const filterUrl = new URL(orderRequestUrls[1])
    expect(filterUrl.searchParams.get('startDate')).toBe('2025-07-01T00:00:00')
    expect(filterUrl.searchParams.get('endDate')).toBe('2025-07-01T23:59:59')

    await user.click(screen.getByRole('button', { name: '手动同步' }))
    const dialog = (await screen.findByText('手动同步订单')).closest(
      '.ant-modal',
    ) as HTMLElement
    expect(within(dialog).getByText('GoodShort')).toBeInTheDocument()
    await user.type(
      within(dialog).getByLabelText('同步开始时间'),
      '2025-07-01 00:00:00',
    )
    await user.type(
      within(dialog).getByLabelText('同步结束时间'),
      '2025-07-01 23:59:59',
    )
    await user.click(within(dialog).getByRole('button', { name: '开始同步' }))

    await waitFor(() =>
      expect(syncBody).toEqual({
        providerId: 1,
        startDate: '2025-07-01T00:00:00',
        endDate: '2025-07-01T23:59:59',
      }),
    )
    expect(await screen.findByText(/获取 5 条/)).toBeInTheDocument()
  })
})
