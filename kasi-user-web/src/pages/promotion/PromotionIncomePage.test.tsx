import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { HttpResponse, http } from 'msw'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PromotionIncomePage } from './PromotionIncomePage'
import { server } from '../../test/server'

describe('PromotionIncomePage', () => {
  it('shows monthly commission summary and own order details', async () => {
    server.use(
      http.get('/api/user/promotion/orders/monthly', () =>
        HttpResponse.json({
          code: 0,
          message: 'success',
          data: {
            month: '2025-07',
            paidOrderCount: 2,
            grossOrderAmount: 19.98,
            calculatedCommission: 9.58,
            reversedCommission: 4.79,
            netCommission: 4.79,
          },
        }),
      ),
      http.get('/api/user/promotion/orders', () =>
        HttpResponse.json({
          code: 0,
          message: 'success',
          data: {
            list: [
              {
                id: 1,
                externalOrderId: 'order-1',
                orderAmount: 9.99,
                currency: 'USD',
                status: 'PAID',
                commissionAmount: 4.79,
                commissionStatus: 'CALCULATED',
                paidAt: '2025-07-01T10:00:00',
              },
            ],
            page: 1,
            size: 20,
            total: 1,
          },
        }),
      ),
    )
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    render(
      <QueryClientProvider client={client}>
        <PromotionIncomePage />
      </QueryClientProvider>,
    )

    expect(
      await screen.findByRole('heading', { name: '\u4f63\u91d1\u660e\u7ec6' }),
    ).toBeInTheDocument()
    expect(await screen.findByText('$4.79')).toBeInTheDocument()
    expect(
      screen.getByText('\u51c0\u4f63\u91d1').closest('.account-overview-grid'),
    ).toHaveClass('promotion-income-summary')
    expect(screen.getByText('order-1')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: '\u5bfc\u51fa CSV' }),
    ).toBeInTheDocument()
  })
})
