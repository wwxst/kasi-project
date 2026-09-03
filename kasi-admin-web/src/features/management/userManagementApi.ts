import { httpClient } from '../../api/http'
import type { ApiResponse } from '../../api/types'
import {
  unwrapApiResponse,
  type CreateUserRequest,
  type PageQuery,
  type PageResult,
  type ResetPasswordRequest,
  type UpdateStatusRequest,
  type UpdateUserRequest,
  type UserDetail,
  type UserListItem,
} from './managementTypes'

const basePath = '/api/user/management'

export async function listUsers(
  query: PageQuery,
): Promise<PageResult<UserListItem>> {
  const response = await httpClient.get<ApiResponse<PageResult<UserListItem>>>(
    basePath,
    { params: query },
  )
  return unwrapApiResponse(response.data)
}

export async function getUser(id: number): Promise<UserDetail> {
  const response = await httpClient.get<ApiResponse<UserDetail>>(
    `${basePath}/${id}`,
  )
  return unwrapApiResponse(response.data)
}

export async function createUser(
  request: CreateUserRequest,
): Promise<UserDetail> {
  const response = await httpClient.post<ApiResponse<UserDetail>>(
    basePath,
    request,
  )
  return unwrapApiResponse(response.data)
}

export async function updateUser(
  id: number,
  request: UpdateUserRequest,
): Promise<UserDetail> {
  const response = await httpClient.put<ApiResponse<UserDetail>>(
    `${basePath}/${id}`,
    request,
  )
  return unwrapApiResponse(response.data)
}

export async function updateUserStatus(
  id: number,
  request: UpdateStatusRequest,
): Promise<void> {
  const response = await httpClient.patch<ApiResponse<null>>(
    `${basePath}/${id}/status`,
    request,
  )
  unwrapApiResponse(response.data)
}

export async function resetUserPassword(
  id: number,
  request: ResetPasswordRequest,
): Promise<void> {
  const response = await httpClient.put<ApiResponse<null>>(
    `${basePath}/${id}/password`,
    request,
  )
  unwrapApiResponse(response.data)
}

export async function removeUser(id: number): Promise<void> {
  const response = await httpClient.delete<ApiResponse<null>>(
    `${basePath}/${id}`,
  )
  unwrapApiResponse(response.data)
}
