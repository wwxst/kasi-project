import { httpClient } from '../../api/http'
import type { ApiResponse } from '../../api/types'
import { unwrapApiResponse } from '../management/managementTypes'
import type {
  AdminMediaAccountDetail,
  AdminMediaAccountListItem,
  DramaProviderOption,
  MediaAccountPageQuery,
  MediaAccountPageResult,
  MediaFiling,
} from './mediaAccountTypes'

const basePath = '/api/admin/promotion/media-accounts'

export async function listAdminMediaAccounts(
  query: MediaAccountPageQuery,
): Promise<MediaAccountPageResult<AdminMediaAccountListItem>> {
  const response = await httpClient.get<
    ApiResponse<MediaAccountPageResult<AdminMediaAccountListItem>>
  >(basePath, { params: query })
  return unwrapApiResponse(response.data)
}

export async function getAdminMediaAccount(
  id: number,
): Promise<AdminMediaAccountDetail> {
  const response = await httpClient.get<ApiResponse<AdminMediaAccountDetail>>(
    `${basePath}/${id}`,
  )
  return unwrapApiResponse(response.data)
}

export async function retryMediaFiling(
  id: number,
  providerId: number,
): Promise<MediaFiling> {
  const response = await httpClient.post<ApiResponse<MediaFiling>>(
    `${basePath}/${id}/filings/${providerId}/retry`,
  )
  return unwrapApiResponse(response.data)
}

export async function deleteAdminMediaAccount(id: number): Promise<void> {
  const response = await httpClient.delete<ApiResponse<null>>(
    `${basePath}/${id}`,
  )
  unwrapApiResponse(response.data)
}

export async function listDramaProviderOptions(): Promise<
  DramaProviderOption[]
> {
  const response = await httpClient.get<ApiResponse<DramaProviderOption[]>>(
    '/api/admin/drama/providers',
  )
  return unwrapApiResponse(response.data)
}
