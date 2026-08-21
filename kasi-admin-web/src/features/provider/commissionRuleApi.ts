import { httpClient } from '../../api/http'
import type { ApiResponse } from '../../api/types'
import { unwrapApiResponse } from '../management/managementTypes'
import type {
  CommissionRule,
  CommissionRuleRequest,
  EndCommissionRuleRequest,
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

export async function endCommissionRule(
  providerId: number,
  ruleId: number,
  request: EndCommissionRuleRequest,
): Promise<CommissionRule> {
  const response = await httpClient.patch<ApiResponse<CommissionRule>>(
    `${basePath}/${providerId}/commission-rules/${ruleId}/end-time`,
    request,
  )
  return unwrapApiResponse(response.data)
}

export async function deleteCommissionRule(
  providerId: number,
  ruleId: number,
): Promise<void> {
  const response = await httpClient.delete<ApiResponse<null>>(
    `${basePath}/${providerId}/commission-rules/${ruleId}`,
  )
  unwrapApiResponse(response.data)
}
