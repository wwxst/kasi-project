import { httpClient } from '../../api/http'
import type { ApiResponse } from '../../api/types'
import { unwrapApiResponse } from '../management/managementTypes'
import type {
  PromotionProject,
  PromotionProjectPage,
  PromotionProjectQuery,
  SavePromotionProjectRequest,
} from './promotionProjectTypes'

const basePath = '/api/admin/promotion/projects'

export async function listPromotionProjects(
  query: PromotionProjectQuery,
): Promise<PromotionProjectPage> {
  const response = await httpClient.get<ApiResponse<PromotionProjectPage>>(
    basePath,
    { params: query },
  )
  return unwrapApiResponse(response.data)
}

export async function createPromotionProject(
  request: SavePromotionProjectRequest,
): Promise<PromotionProject> {
  const response = await httpClient.post<ApiResponse<PromotionProject>>(
    basePath,
    toFormData(request),
  )
  return unwrapApiResponse(response.data)
}

export async function updatePromotionProject(
  id: number,
  request: SavePromotionProjectRequest,
): Promise<PromotionProject> {
  const response = await httpClient.put<ApiResponse<PromotionProject>>(
    `${basePath}/${id}`,
    toFormData(request),
  )
  return unwrapApiResponse(response.data)
}

export async function deletePromotionProject(id: number): Promise<void> {
  const response = await httpClient.delete<ApiResponse<null>>(
    `${basePath}/${id}`,
  )
  unwrapApiResponse(response.data)
}

function toFormData(request: SavePromotionProjectRequest) {
  const formData = new FormData()
  formData.append('name', request.name.trim())
  formData.append('projectDocumentUrl', request.projectDocumentUrl.trim())
  formData.append('status', request.status)
  formData.append('sortOrder', String(request.sortOrder))
  if (request.coverFile) formData.append('coverFile', request.coverFile)
  return formData
}
