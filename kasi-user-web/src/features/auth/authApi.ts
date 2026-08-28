import { httpClient } from '../../shared/api/httpClient'
import type { ApiResponse, CurrentUser, LoginResult } from './types'

function unwrap<T>(response: { data: ApiResponse<T> }) {
  if (response.data.code !== 0 || response.data.data === null) {
    throw new Error(response.data.message || '请求失败')
  }
  return response.data.data
}

export async function loginUser(account: string, password: string) {
  const response = await httpClient.post<ApiResponse<LoginResult>>(
    '/api/user/auth/login',
    { account, password },
  )
  return unwrap(response)
}

export async function getCurrentUser() {
  const response =
    await httpClient.get<ApiResponse<CurrentUser>>('/api/user/auth/me')
  return unwrap(response)
}
