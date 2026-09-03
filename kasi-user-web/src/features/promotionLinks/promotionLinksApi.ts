import { httpClient } from '../../shared/api/httpClient'
import type {
  ApiResponse,
  CreatePromotionLinksInput,
  PromotionLinkBatch,
  PromotionLinkPage,
} from './types'

function unwrap<T>(response: { data: ApiResponse<T> }) {
  if (response.data.code !== 0 || response.data.data === null) {
    throw new Error(response.data.message || '请求失败')
  }
  return response.data.data
}

export async function getPromotionLinks(page = 1, size = 20) {
  const response = await httpClient.get<ApiResponse<PromotionLinkPage>>(
    '/api/user/promotion/links',
    { params: { page, size } },
  )
  return unwrap(response)
}

export async function createPromotionLinks(
  input: Omit<CreatePromotionLinksInput, 'requestKey'> & {
    requestKey?: string
  },
) {
  const response = await httpClient.post<ApiResponse<PromotionLinkBatch>>(
    '/api/user/promotion/links',
    { ...input, requestKey: input.requestKey ?? crypto.randomUUID() },
  )
  return unwrap(response)
}
