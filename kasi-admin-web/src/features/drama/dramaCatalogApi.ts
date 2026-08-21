import { httpClient } from '../../api/http'
import type { ApiResponse } from '../../api/types'
import { unwrapApiResponse } from '../management/managementTypes'
import type {
  DramaCatalogDetail,
  DramaCatalogPage,
  DramaCatalogPageQuery,
  DramaSyncTask,
  RequestDramaSync,
  UpdateDramaLocalStatusRequest,
} from './dramaCatalogTypes'

const basePath = '/api/admin/drama/catalog'

export async function listDramaCatalog(
  query: DramaCatalogPageQuery,
): Promise<DramaCatalogPage> {
  const response = await httpClient.get<ApiResponse<DramaCatalogPage>>(
    basePath,
    { params: query },
  )
  return unwrapApiResponse(response.data)
}

export async function getDramaCatalogDetail(
  id: number,
): Promise<DramaCatalogDetail> {
  const response = await httpClient.get<ApiResponse<DramaCatalogDetail>>(
    `${basePath}/${id}`,
  )
  return unwrapApiResponse(response.data)
}

export async function requestDramaCatalogSync(
  request: RequestDramaSync,
): Promise<DramaSyncTask[]> {
  const response = await httpClient.post<ApiResponse<DramaSyncTask[]>>(
    `${basePath}/sync`,
    request,
  )
  return unwrapApiResponse(response.data)
}

export async function listDramaSyncStatuses(
  providerId: number,
): Promise<DramaSyncTask[]> {
  const response = await httpClient.get<ApiResponse<DramaSyncTask[]>>(
    `${basePath}/sync/status`,
    { params: { providerId } },
  )
  return unwrapApiResponse(response.data)
}

export async function updateDramaLocalStatus(
  id: number,
  request: UpdateDramaLocalStatusRequest,
): Promise<DramaCatalogDetail> {
  const response = await httpClient.patch<ApiResponse<DramaCatalogDetail>>(
    `${basePath}/${id}/status`,
    request,
  )
  return unwrapApiResponse(response.data)
}
