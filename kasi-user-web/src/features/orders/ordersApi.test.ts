import { afterEach, describe, expect, it, vi } from 'vitest'
import { httpClient } from '../../shared/api/httpClient'
import { fetchPromotionOrders } from './ordersApi'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('ordersApi', () => {
  it('forwards the selected month and server pagination', async () => {
    const get = vi.spyOn(httpClient, 'get').mockResolvedValueOnce({
      data: {
        code: 0,
        message: '成功',
        data: { list: [], page: 2, size: 50, total: 0 },
      },
    })

    await fetchPromotionOrders('2026-08', 2, 50)

    expect(get).toHaveBeenCalledWith('/api/user/promotion/orders', {
      params: { month: '2026-08', page: 2, size: 50 },
    })
  })
})
