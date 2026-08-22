import { httpClient } from '../../api/http'
import type { ApiResponse } from '../../api/types'
import { unwrapApiResponse } from '../management/managementTypes'
import type {
  CommissionRule,
  CommissionRuleRequest,
} from './commissionRuleTypes'

const basePath = '/api/admin/drama/providers'

export async function listCommissionRules(
  providerId: number,
): Promise<CommissionRule[]> {
  const response = await httpClient.get<ApiResponse<CommissionRule[]>>(
    `${basePath}/${providerId}/commission-rules`,
  )
  return unwrapApiResponse(response.data)
}

export async function createCommissionRule(
  providerId: number,
  request: CommissionRuleRequest,
): Promise<CommissionRule> {
  const response = await httpClient.post<ApiResponse<CommissionRule>>(
    `${basePath}/${providerId}/commission-rules`,
    request,
  )
  return unwrapApiResponse(response.data)
}

export async function updateCommissionRule(
  providerId: number,
  ruleId: number,
  request: CommissionRuleRequest,
): Promise<CommissionRule> {
  const response = await httpClient.put<ApiResponse<CommissionRule>>(
    `${basePath}/${providerId}/commission-rules/${ruleId}`,
    request,
  )
  return unwrapApiResponse(response.data)
}
