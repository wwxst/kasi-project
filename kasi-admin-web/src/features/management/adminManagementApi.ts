import { httpClient } from '../../api/http'
import type { ApiResponse } from '../../api/types'
import {
  unwrapApiResponse,
  type AdminDetail,
  type AdminListItem,
  type CreateAdminRequest,
  type PageQuery,
  type PageResult,
  type ResetPasswordRequest,
  type UpdateAdminRequest,
  type UpdateStatusRequest,
} from './managementTypes'

const basePath = '/api/admin/management'

export async function listAdmins(
  query: PageQuery,
): Promise<PageResult<AdminListItem>> {
  const response = await httpClient.get<ApiResponse<PageResult<AdminListItem>>>(
    basePath,
    { params: query },
  )
  return unwrapApiResponse(response.data)
}

export async function getAdmin(id: number): Promise<AdminDetail> {
  const response = await httpClient.get<ApiResponse<AdminDetail>>(
    `${basePath}/${id}`,
  )
  return unwrapApiResponse(response.data)
}

export async function createAdmin(
  request: CreateAdminRequest,
): Promise<AdminDetail> {
  const response = await httpClient.post<ApiResponse<AdminDetail>>(
    basePath,
    request,
  )
  return unwrapApiResponse(response.data)
}

export async function updateAdmin(
  id: number,
  request: UpdateAdminRequest,
): Promise<AdminDetail> {
  const response = await httpClient.put<ApiResponse<AdminDetail>>(
    `${basePath}/${id}`,
    request,
  )
  return unwrapApiResponse(response.data)
}

export async function updateAdminStatus(
  id: number,
  request: UpdateStatusRequest,
): Promise<void> {
  const response = await httpClient.patch<ApiResponse<null>>(
    `${basePath}/${id}/status`,
    request,
  )
  unwrapApiResponse(response.data)
}

export async function resetAdminPassword(
  id: number,
  request: ResetPasswordRequest,
): Promise<void> {
  const response = await httpClient.put<ApiResponse<null>>(
    `${basePath}/${id}/password`,
    request,
  )
  unwrapApiResponse(response.data)
}

export async function uploadAdminAvatar(
  id: number,
  file: File,
): Promise<AdminDetail> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await httpClient.put<ApiResponse<AdminDetail>>(
    `${basePath}/${id}/avatar`,
    formData,
  )
  return unwrapApiResponse(response.data)
}

export async function removeAdmin(id: number): Promise<void> {
  const response = await httpClient.delete<ApiResponse<null>>(
    `${basePath}/${id}`,
  )
  unwrapApiResponse(response.data)
}
