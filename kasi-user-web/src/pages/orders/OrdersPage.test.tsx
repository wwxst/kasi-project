import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { appRoutes } from '../../app/routes'
import { fetchPromotionOrders } from '../../features/orders/ordersApi'

vi.mock('../../features/orders/ordersApi', () => ({
  fetchPromotionOrders: vi.fn(),
}))

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function renderOrderRoute() {
  const route = appRoutes.find((item) => item.path === '/workspace/orders')
  if (!route) throw new Error('订单路由缺失')
  const Component = route.element

  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <Component title={route.title} />
    </QueryClientProvider>,
  )
}

describe('OrdersPage', () => {
  it('shows the current users monthly orders with localized statuses', async () => {
    const month = new Date().toISOString().slice(0, 7)
    vi.mocked(fetchPromotionOrders).mockResolvedValueOnce({
      list: [
        {
          externalOrderId: 'GS-202608-001',
          currency: 'USD',
          status: 'PAID',
          paidAt: '2026-08-30T12:34:56',
          trackingNo: 'tracking-001',
          commissionAmount: 4.79,
        },
        {
          externalOrderId: 'GS-202608-002',
          currency: 'USD',
          status: 'UNPAID',
          paidAt: null,
          trackingNo: 'tracking-002',
          commissionAmount: null,
        },
      ],
      page: 1,
      size: 20,
      total: 1,
    })

    renderOrderRoute()

    expect(await screen.findByText('GS-202608-001')).toBeTruthy()
    expect(fetchPromotionOrders).toHaveBeenCalledWith(month, 1, 20)
    expect(screen.getByText('已支付')).toBeTruthy()
    expect(screen.getByText('未支付')).toBeTruthy()
    expect(screen.queryByText('已计算')).toBeNull()
    expect(screen.queryByText('佣金状态')).toBeNull()
    expect(screen.queryByText('$19.98')).toBeNull()
    expect(screen.getByText('$4.79')).toBeTruthy()
    expect(screen.getByText('我的收益')).toBeTruthy()
    expect(screen.getByText('2026-08-30 12:34:56')).toBeTruthy()
    expect(screen.getByText('tracking-001')).toBeTruthy()
  })

  it('requests the selected server page', async () => {
    const user = userEvent.setup()
    const month = new Date().toISOString().slice(0, 7)
    vi.mocked(fetchPromotionOrders)
      .mockResolvedValueOnce({
        list: [],
        page: 1,
        size: 20,
        total: 21,
      })
      .mockResolvedValueOnce({
        list: [],
        page: 2,
        size: 20,
        total: 21,
      })

    renderOrderRoute()

    await waitFor(() =>
      expect(fetchPromotionOrders).toHaveBeenCalledWith(month, 1, 20),
    )
    await user.click(await screen.findByText('2'))
    await waitFor(() =>
      expect(fetchPromotionOrders).toHaveBeenLastCalledWith(month, 2, 20),
    )
  })
})
