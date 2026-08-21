import { httpClient } from '../../api/http'
import type { ApiResponse } from '../../api/types'
import { unwrapApiResponse } from '../management/managementTypes'
import type {
  DramaProvider,
  ProviderConnection,
  ProviderConnectionTestResult,
  UpsertProviderConnectionRequest,
} from './providerTypes'

export {
  createCommissionRule,
  deleteCommissionRule,
  endCommissionRule,
  listCommissionRules,
  updateCommissionRule,
} from './commissionRuleApi'

const basePath = '/api/admin/drama/providers'

export async function listProviders(): Promise<DramaProvider[]> {
  const response = await httpClient.get<ApiResponse<DramaProvider[]>>(basePath)
  return unwrapApiResponse(response.data)
}

export async function upsertProviderConnection(
  providerId: number,
  request: UpsertProviderConnectionRequest,
): Promise<ProviderConnection> {
  const response = await httpClient.put<ApiResponse<ProviderConnection>>(
    `${basePath}/${providerId}/connection`,
    request,
  )
  return unwrapApiResponse(response.data)
}

export async function testProviderConnection(
  providerId: number,
): Promise<ProviderConnectionTestResult> {
  const response = await httpClient.post<
    ApiResponse<ProviderConnectionTestResult>
  >(`${basePath}/${providerId}/connection/test`)
  return unwrapApiResponse(response.data)
}
