import { httpClient } from '../../shared/api/httpClient'
import type {
  CreateMediaAccountInput,
  MediaAccount,
  MediaAccountApiResponse,
  MediaFiling,
} from './types'

function unwrap<T>(response: { data: MediaAccountApiResponse<T> }) {
  if (response.data.code !== 0 || response.data.data === null) {
    throw new Error(response.data.message || '请求失败')
  }
  return response.data.data
}

export async function getMediaAccounts() {
  const response = await httpClient.get<
    MediaAccountApiResponse<MediaAccount[]>
  >('/api/user/promotion/media-accounts')
  return unwrap(response)
}

export async function createMediaAccount(input: CreateMediaAccountInput) {
  const response = await httpClient.post<MediaAccountApiResponse<MediaAccount>>(
    '/api/user/promotion/media-accounts',
    input,
  )
  return unwrap(response)
}

export async function submitMediaFiling(accountId: number, providerId: number) {
  const response = await httpClient.post<MediaAccountApiResponse<MediaFiling>>(
    `/api/user/promotion/media-accounts/${accountId}/filings/${providerId}`,
  )
  return unwrap(response)
}
