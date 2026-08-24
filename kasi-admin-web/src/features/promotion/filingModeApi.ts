import { httpClient } from '../../api/http'
import type { ApiResponse } from '../../api/types'
import { unwrapApiResponse } from '../management/managementTypes'
import type { DramaProvider } from '../provider/providerTypes'
import type { FilingMode, ProviderFilingMode } from './filingModeTypes'

const basePath = '/api/admin/drama/providers'

export async function listProviderFilingModes(): Promise<ProviderFilingMode[]> {
  const providersResponse =
    await httpClient.get<ApiResponse<DramaProvider[]>>(basePath)
  const providers = unwrapApiResponse(providersResponse.data)
  return Promise.all(
    providers.map(async (provider) => {
      if (!provider.connection) {
        return {
          providerId: provider.id,
          providerName: provider.providerName,
          filingMode: 'API' as const,
          connectionConfigured: false,
        }
      }
      const response = await httpClient.get<ApiResponse<ProviderFilingMode>>(
        `${basePath}/${provider.id}/filing-mode`,
      )
      return { ...unwrapApiResponse(response.data), connectionConfigured: true }
    }),
  )
}

export async function updateProviderFilingMode(
  providerId: number,
  filingMode: FilingMode,
): Promise<ProviderFilingMode> {
  const response = await httpClient.put<ApiResponse<ProviderFilingMode>>(
    `${basePath}/${providerId}/filing-mode`,
    { filingMode },
  )
  return unwrapApiResponse(response.data)
}
