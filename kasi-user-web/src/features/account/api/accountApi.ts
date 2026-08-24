import { useQuery } from '@tanstack/react-query'
import { apiRequest } from '../../../shared/api/httpClient'
import type { ChangePasswordRequest, CurrentUser } from './accountTypes'

export async function fetchCurrentUser(): Promise<CurrentUser> {
  const response = await apiRequest<CurrentUser>({
    method: 'GET',
    url: '/api/user/auth/me',
  })
  if (!response) {
    throw new Error('当前用户信息缺失')
  }
  return response
}

export function useCurrentUser(enabled = true) {
  return useQuery({
    queryKey: ['current-user'],
    queryFn: fetchCurrentUser,
    enabled,
    retry: false,
    staleTime: 30_000,
  })
}

export function changePassword(request: ChangePasswordRequest) {
  return apiRequest<void>({
    method: 'PUT',
    url: '/api/user/auth/password',
    data: request,
  })
}

export function logoutUser() {
  return apiRequest<void>({
    method: 'POST',
    url: '/api/user/auth/logout',
  })
}
