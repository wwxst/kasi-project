import { httpClient } from '../../shared/api/httpClient'
import type {
  ApiResponse,
  DramaDetail,
  DramaContentResource,
  DramaDownloadTask,
  DramaPage,
  DramaPageQuery,
} from './types'

function unwrap<T>(response: { data: ApiResponse<T> }) {
  if (response.data.code !== 0 || response.data.data === null) {
    throw new Error(response.data.message || '请求失败')
  }
  return response.data.data
}

export async function getPublishedDramas(query: DramaPageQuery) {
  const response = await httpClient.get<ApiResponse<DramaPage>>(
    '/api/user/promotion/dramas',
    { params: query },
  )
  return unwrap(response)
}

export async function getPublishedDramaDetail(id: number) {
  const response = await httpClient.get<ApiResponse<DramaDetail>>(
    `/api/user/promotion/dramas/${id}`,
  )
  return unwrap(response)
}

export async function getPublishedDramaFreeContent(
  id: number,
  refresh = false,
) {
  const response = await httpClient.get<ApiResponse<DramaContentResource[]>>(
    `/api/user/promotion/dramas/${id}/free-content`,
    refresh ? { params: { refresh: true } } : undefined,
  )
  return unwrap(response)
}

export async function createDramaDownloadTask(
  dramaId: number,
  contentIds: number[],
) {
  const response = await httpClient.post<ApiResponse<DramaDownloadTask>>(
    `/api/user/promotion/dramas/${dramaId}/downloads`,
    { contentIds },
  )
  return unwrap(response)
}

export async function getDramaDownloadTask(taskId: number) {
  const response = await httpClient.get<ApiResponse<DramaDownloadTask>>(
    `/api/user/promotion/downloads/${taskId}`,
  )
  return unwrap(response)
}

export async function downloadDramaTaskFile(taskId: number) {
  const response = await httpClient.get<Blob>(
    `/api/user/promotion/downloads/${taskId}/file`,
    { responseType: 'blob' },
  )
  return response.data
}
