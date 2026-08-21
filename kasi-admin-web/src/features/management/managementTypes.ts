import type { ApiResponse } from '../../api/types'

export interface PageQuery {
  page: number
  size: number
  keyword?: string
}

export interface PageResult<T> {
  list: T[]
  page: number
  size: number
  total: number
}

export interface AdminListItem {
  id: number
  username: string
  realName: string
  mobile: string | null
  email: string | null
  avatarUrl: string | null
  departmentId: number | null
  status: number
  isSuperAdmin: number
  lastLoginAt: string | null
  createdAt: string
}

export interface AdminDetail extends AdminListItem {
  lastLoginIp: string | null
  passwordChangedAt: string | null
  remark: string | null
  createdBy: number | null
  updatedBy: number | null
  updatedAt: string | null
}

export interface CreateAdminRequest {
  username: string
  password: string
  confirmPassword: string
  realName: string
  mobile?: string
  email?: string
  departmentId?: number
  remark?: string
}

export type UpdateAdminRequest = Omit<
  CreateAdminRequest,
  'password' | 'confirmPassword'
>

export interface UserListItem {
  id: number
  userNo: string
  nickname: string
  realName: string | null
  mobile: string | null
  email: string | null
  avatarUrl: string | null
  status: number
  registerSource: string | null
  lastLoginAt: string | null
  createdAt: string
}

export interface UserDetail extends UserListItem {
  lastLoginIp: string | null
  remark: string | null
  updatedAt: string | null
}

export interface CreateUserRequest {
  mobile?: string
  email?: string
  nickname: string
  realName?: string
  avatarUrl?: string
  remark?: string
  password: string
  confirmPassword: string
}

export type UpdateUserRequest = Omit<
  CreateUserRequest,
  'password' | 'confirmPassword'
>

export interface UpdateStatusRequest {
  status: number
}

export interface ResetPasswordRequest {
  newPassword: string
  confirmPassword: string
}

export function unwrapApiResponse<T>(response: ApiResponse<T>): T {
  if (response.code !== 0 || response.data === undefined) {
    throw new Error(response.message || '请求失败')
  }

  return response.data
}
