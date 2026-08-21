import { httpClient } from '../../api/http'
import type { ApiResponse } from '../../api/types'
import type {
  AdminInfo,
  AdminLoginRequest,
  AdminLoginResponse,
  ChangeAdminPasswordRequest,
  UpdateAdminProfileRequest,
} from './authTypes'

export async function loginAdmin(
  request: AdminLoginRequest,
): Promise<AdminLoginResponse> {
  const response = await httpClient.post<ApiResponse<AdminLoginResponse>>(
    '/api/admin/auth/login',
    request,
  )

  if (response.data.code !== 0 || !response.data.data) {
    throw new Error(response.data.message || '登录失败')
  }

  return response.data.data
}

export async function updateAdminProfile(
  request: UpdateAdminProfileRequest,
): Promise<AdminInfo> {
  const response = await httpClient.put<ApiResponse<AdminInfo>>(
    '/api/admin/auth/profile',
    request,
  )
  return unwrapAuthResponse(response.data)
}

export async function uploadCurrentAdminAvatar(file: File): Promise<AdminInfo> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await httpClient.put<ApiResponse<AdminInfo>>(
    '/api/admin/auth/avatar',
    formData,
  )
  return unwrapAuthResponse(response.data)
}

export async function changeAdminPassword(
  request: ChangeAdminPasswordRequest,
): Promise<void> {
  const response = await httpClient.put<ApiResponse<null>>(
    '/api/admin/auth/password',
    request,
  )
  unwrapAuthResponse(response.data)
}

function unwrapAuthResponse<T>(response: ApiResponse<T>): T {
  if (response.code !== 0 || response.data === undefined) {
    throw new Error(response.message || '请求失败')
  }
  return response.data
}
