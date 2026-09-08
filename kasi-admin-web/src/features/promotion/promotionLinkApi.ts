import { httpClient } from '../../api/http'
import type { ApiResponse } from '../../api/types'
import { unwrapApiResponse } from '../management/managementTypes'
import type {
  PromotionAnalyticalReportSyncRequest,
  PromotionAnalyticalReportSyncResult,
  PromotionLinkPage,
  PromotionLinkQuery,
} from './promotionLinkTypes'

const basePath = '/api/admin/promotion/links'

export async function listPromotionLinks(
  query: PromotionLinkQuery,
): Promise<PromotionLinkPage> {
  const response = await httpClient.get<ApiResponse<PromotionLinkPage>>(
    basePath,
    { params: query },
  )
  return unwrapApiResponse(response.data)
}

export async function syncPromotionAnalyticalReports(
  request: PromotionAnalyticalReportSyncRequest,
): Promise<PromotionAnalyticalReportSyncResult> {
  const response = await httpClient.post<
    ApiResponse<PromotionAnalyticalReportSyncResult>
  >('/api/admin/promotion/analytical-reports/sync', request)
  return unwrapApiResponse(response.data)
}
