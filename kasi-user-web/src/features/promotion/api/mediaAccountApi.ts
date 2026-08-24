import { apiRequest } from '../../../shared/api/httpClient'
import type {
  CreateMediaAccountRequest,
  MediaAccount,
  MediaAccountDetail,
  MediaFiling,
  UpdateMediaAccountRequest,
} from './mediaAccountTypes'

const basePath = '/api/user/promotion/media-accounts'

function requireData<T>(data: T | undefined, message: string): T {
  if (data === undefined) throw new Error(message)
  return data
}

export async function fetchMediaAccounts(): Promise<MediaAccount[]> {
  const data = await apiRequest<MediaAccount[]>({
    method: 'GET',
    url: basePath,
  })
  return data ?? []
}

export async function fetchMediaAccount(
  id: number,
): Promise<MediaAccountDetail> {
  return requireData(
    await apiRequest<MediaAccountDetail>({
      method: 'GET',
      url: `${basePath}/${id}`,
    }),
    '媒体账号详情缺失',
  )
}

export async function createMediaAccount(
  request: CreateMediaAccountRequest,
): Promise<MediaAccountDetail> {
  return requireData(
    await apiRequest<MediaAccountDetail>({
      method: 'POST',
      url: basePath,
      data: request,
    }),
    '媒体账号创建结果缺失',
  )
}

export async function updateMediaAccount(
  id: number,
  request: UpdateMediaAccountRequest,
): Promise<MediaAccountDetail> {
  return requireData(
    await apiRequest<MediaAccountDetail>({
      method: 'PUT',
      url: `${basePath}/${id}`,
      data: request,
    }),
    '媒体账号更新结果缺失',
  )
}

export async function retryMediaAccountFiling(
  id: number,
  providerId: number,
): Promise<MediaFiling> {
  return requireData(
    await apiRequest<MediaFiling>({
      method: 'POST',
      url: `${basePath}/${id}/filings/${providerId}`,
    }),
    '报白任务结果缺失',
  )
}
