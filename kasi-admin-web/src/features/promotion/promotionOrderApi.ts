import { httpClient } from '../../api/http'
import type { ApiResponse } from '../../api/types'
import { unwrapApiResponse } from '../management/managementTypes'
import type {
  PromotionOrderPage,
  PromotionOrderQuery,
  PromotionOrderSyncRequest,
  PromotionOrderSyncResult,
} from './promotionOrderTypes'

const basePath = '/api/admin/promotion/orders'

export async function listPromotionOrders(
  query: PromotionOrderQuery,
): Promise<PromotionOrderPage> {
  const response = await httpClient.get<ApiResponse<PromotionOrderPage>>(
    basePath,
    { params: query },
  )
  return unwrapApiResponse(response.data)
}

export async function syncPromotionOrders(
  request: PromotionOrderSyncRequest,
): Promise<PromotionOrderSyncResult> {
  const response = await httpClient.post<ApiResponse<PromotionOrderSyncResult>>(
    `${basePath}/sync`,
    request,
  )
  return unwrapApiResponse(response.data)
}

export async function exportPromotionOrders(
  query: PromotionOrderQuery,
): Promise<Blob> {
  const response = await httpClient.get<Blob>(`${basePath}/export.csv`, {
    params: query,
    responseType: 'blob',
  })
  return response.data
}
