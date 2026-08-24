import { apiRequest, httpClient } from '../../../shared/api/httpClient'
import type {
  PromotionMonthlyCommission,
  PromotionOrderPage,
} from './promotionOrderTypes'

function requireData<T>(value: T | undefined): T {
  if (value === undefined) throw new Error('佣金数据缺失')
  return value
}

export async function fetchPromotionOrders(
  month: string,
): Promise<PromotionOrderPage> {
  return requireData(
    await apiRequest<PromotionOrderPage>({
      method: 'GET',
      url: '/api/user/promotion/orders',
      params: { month },
    }),
  )
}

export async function fetchMonthlyCommission(
  month: string,
): Promise<PromotionMonthlyCommission> {
  return requireData(
    await apiRequest<PromotionMonthlyCommission>({
      method: 'GET',
      url: '/api/user/promotion/orders/monthly',
      params: { month },
    }),
  )
}

export async function downloadPromotionOrders(month: string) {
  const response = await httpClient.get<Blob>(
    '/api/user/promotion/orders/export.csv',
    { params: { month }, responseType: 'blob' },
  )
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `promotion-income-${month}.csv`
  anchor.click()
  URL.revokeObjectURL(url)
}
