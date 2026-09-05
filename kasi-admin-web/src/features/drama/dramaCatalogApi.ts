import { httpClient } from '../../api/http'
import type { ApiResponse } from '../../api/types'
import { unwrapApiResponse } from '../management/managementTypes'
import type {
  DramaCatalogDetail,
  DramaLanguageOption,
  DramaCatalogPage,
  DramaCatalogPageQuery,
  DramaContentSyncBatchResult,
  DramaContentSyncTask,
  DramaContentSyncRecordDetail,
  DramaSyncRecord,
  DramaSyncRecordDetail,
  DramaSyncTask,
  RequestAllDramaContentSync,
  RequestDramaSync,
  UpdateDramaLocalStatusRequest,
} from './dramaCatalogTypes'

const basePath = '/api/admin/drama/catalog'

export async function listDramaLanguageOptions(): Promise<
  DramaLanguageOption[]
> {
  const response = await httpClient.get<ApiResponse<DramaLanguageOption[]>>(
    '/api/drama/languages',
  )
  return unwrapApiResponse(response.data)
}

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

export async function listDramaSyncRecords(
  providerId: number,
): Promise<DramaSyncRecord[]> {
  const response = await httpClient.get<ApiResponse<DramaSyncRecord[]>>(
    `${basePath}/sync/records`,
    { params: { providerId } },
  )
  return unwrapApiResponse(response.data)
}

export async function getDramaSyncRecordDetails(
  providerId: number,
  runId: string,
): Promise<DramaSyncRecordDetail[]> {
  const response = await httpClient.get<ApiResponse<DramaSyncRecordDetail[]>>(
    `${basePath}/sync/records/${runId}`,
    { params: { providerId } },
  )
  return unwrapApiResponse(response.data)
}

export async function listDramaContentSyncRecords(
  providerId: number,
): Promise<DramaSyncRecord[]> {
  const response = await httpClient.get<ApiResponse<DramaSyncRecord[]>>(
    `${basePath}/contents/sync/records`,
    { params: { providerId } },
  )
  return unwrapApiResponse(response.data)
}

export async function getDramaContentSyncRecordDetails(
  providerId: number,
  runId: string,
): Promise<DramaContentSyncRecordDetail[]> {
  const response = await httpClient.get<
    ApiResponse<DramaContentSyncRecordDetail[]>
  >(`${basePath}/contents/sync/records/${runId}`, {
    params: { providerId },
  })
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

export async function requestDramaContentSync(
  id: number,
): Promise<DramaContentSyncTask> {
  const response = await httpClient.post<ApiResponse<DramaContentSyncTask>>(
    `${basePath}/${id}/contents/sync`,
  )
  if (response.data.code === 6016) {
    throw new Error('该短剧的剧集同步任务正在执行')
  }
  return unwrapApiResponse(response.data)
}

export async function requestDramaContentBatchSync(
  dramaIds: number[],
): Promise<DramaContentSyncBatchResult> {
  const response = await httpClient.post<
    ApiResponse<DramaContentSyncBatchResult>
  >(`${basePath}/contents/sync`, { dramaIds })
  return unwrapApiResponse(response.data)
}

export async function requestAllDramaContentSync(
  request: RequestAllDramaContentSync,
): Promise<DramaContentSyncBatchResult> {
  const response = await httpClient.post<
    ApiResponse<DramaContentSyncBatchResult>
  >(`${basePath}/contents/sync/all`, request)
  return unwrapApiResponse(response.data)
}

export async function getDramaContentSyncStatus(
  id: number,
): Promise<DramaContentSyncTask | null> {
  const response = await httpClient.get<ApiResponse<DramaContentSyncTask>>(
    `${basePath}/${id}/contents/sync/status`,
  )
  if (response.data.code === 6017) return null
  return unwrapApiResponse(response.data)
}
