import { httpClient } from '../../shared/api/httpClient'
import type { ApiResponse } from '../../shared/api/types'
import type { PromotionMonthlyCommission, PromotionOrderPage } from './types'

function unwrap<T>(response: { data: ApiResponse<T> }): T {
  if (response.data.code !== 0 || response.data.data === undefined) {
    throw new Error(response.data.message || '请求失败')
  }
  return response.data.data
}

export async function fetchPromotionOrders(
  month: string,
  page = 1,
  size = 20,
): Promise<PromotionOrderPage> {
  const response = await httpClient.get<ApiResponse<PromotionOrderPage>>(
    '/api/user/promotion/orders',
    { params: { month, page, size } },
  )
  return unwrap(response)
}

export async function fetchMonthlyCommission(
  month: string,
): Promise<PromotionMonthlyCommission> {
  const response = await httpClient.get<
    ApiResponse<PromotionMonthlyCommission>
  >('/api/user/promotion/orders/monthly', { params: { month } })
  return unwrap(response)
}
