import { httpClient } from '../../shared/api/httpClient'
import type {
  ApiResponse,
  ChangePasswordRequest,
  CurrentUser,
  LoginResult,
  RegisterRequest,
  ResetTokenResult,
  UpdateUserProfileRequest,
} from './types'

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

async function postVoid(path: string, data: unknown) {
  const response = await httpClient.post<ApiResponse<null>>(path, data)
  if (response.data.code !== 0) throw new Error(response.data.message)
  return
}
export const sendRegisterCode = (target: string) =>
  postVoid('/api/user/auth/register/code', { target })
export const registerUser = (request: RegisterRequest) =>
  postVoid('/api/user/auth/register', request)
export const sendLoginCode = (target: string) =>
  postVoid('/api/user/auth/login/code', { target })
export const loginUserWithCode = async (target: string, code: string) => {
  const r = await httpClient.post<ApiResponse<LoginResult>>(
    '/api/user/auth/login/code/verify',
    { target, code },
  )
  return unwrap(r)
}
export const sendForgotPasswordCode = (target: string) =>
  postVoid('/api/user/auth/password/forgot/code', { target })
export const verifyForgotPasswordCode = async (
  target: string,
  code: string,
) => {
  const r = await httpClient.post<ApiResponse<ResetTokenResult>>(
    '/api/user/auth/password/forgot/verify',
    { target, code },
  )
  return unwrap(r)
}
export const resetPassword = (
  resetToken: string,
  newPassword: string,
  confirmPassword: string,
) =>
  postVoid('/api/user/auth/password/reset', {
    resetToken,
    newPassword,
    confirmPassword,
  })

export async function changePassword(request: ChangePasswordRequest) {
  const response = await httpClient.put<ApiResponse<null>>(
    '/api/user/auth/password',
    request,
  )
  if (response.data.code !== 0) {
    throw new Error(response.data.message || '密码修改失败')
  }
}

export async function updateUserProfile(request: UpdateUserProfileRequest) {
  const response = await httpClient.put<ApiResponse<CurrentUser>>(
    '/api/user/auth/profile',
    request,
  )
  return unwrap(response)
}

export async function uploadUserAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await httpClient.put<ApiResponse<CurrentUser>>(
    '/api/user/auth/avatar',
    formData,
  )
  return unwrap(response)
}
