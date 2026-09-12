import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { exportPromotionOrders } from './promotionOrderApi'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('promotionOrderApi', () => {
  it('exports XLSX with the current order filters', async () => {
    let requestUrl: URL | undefined
    server.use(
      http.get('/api/admin/promotion/orders/export.xlsx', ({ request }) => {
        requestUrl = new URL(request.url)
        return new HttpResponse(new Blob(['xlsx']))
      }),
    )

    await exportPromotionOrders({
      page: 4,
      size: 1,
      providerId: 7,
      status: 'REFUNDED',
      attributionStatus: 'ATTRIBUTED',
      startDate: '2026-09-01T00:00:00',
      endDate: '2026-10-01T00:00:00',
    })

    expect(requestUrl?.pathname).toBe('/api/admin/promotion/orders/export.xlsx')
    expect(requestUrl?.searchParams.get('providerId')).toBe('7')
    expect(requestUrl?.searchParams.get('status')).toBe('REFUNDED')
    expect(requestUrl?.searchParams.get('attributionStatus')).toBe('ATTRIBUTED')
  })
})
