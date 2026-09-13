import { httpClient } from '../../api/http'
import type { ApiResponse } from '../../api/types'
import { unwrapApiResponse } from '../management/managementTypes'
import type {
  PromotionProjectType,
  PromotionProjectTypeFormValues,
} from './promotionProjectTypeTypes'

const basePath = '/api/admin/promotion/project-types'

export async function listPromotionProjectTypes(): Promise<
  PromotionProjectType[]
> {
  const response =
    await httpClient.get<ApiResponse<PromotionProjectType[]>>(basePath)
  return unwrapApiResponse(response.data)
}

export async function createPromotionProjectType(
  request: PromotionProjectTypeFormValues,
): Promise<PromotionProjectType> {
  const response = await httpClient.post<ApiResponse<PromotionProjectType>>(
    basePath,
    request,
  )
  return unwrapApiResponse(response.data)
}

export async function updatePromotionProjectType(
  id: number,
  request: PromotionProjectTypeFormValues,
): Promise<PromotionProjectType> {
  const response = await httpClient.put<ApiResponse<PromotionProjectType>>(
    `${basePath}/${id}`,
    request,
  )
  return unwrapApiResponse(response.data)
}

export async function deletePromotionProjectType(id: number): Promise<void> {
  const response = await httpClient.delete<ApiResponse<null>>(
    `${basePath}/${id}`,
  )
  unwrapApiResponse(response.data)
}
