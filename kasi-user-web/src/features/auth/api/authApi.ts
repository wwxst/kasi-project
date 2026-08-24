import { apiRequest } from '../../../shared/api/httpClient'
import type {
  PasswordVerificationResponse,
  RegisterRequest,
  UserLoginRequest,
  UserLoginResponse,
} from './authTypes'

export function loginUser(
  request: UserLoginRequest,
): Promise<UserLoginResponse | undefined> {
  return apiRequest<UserLoginResponse>({
    method: 'POST',
    url: '/api/user/auth/login',
    data: request,
  })
}

export function sendRegistrationCode(target: string) {
  return apiRequest<void>({
    method: 'POST',
    url: '/api/user/auth/register/code',
    data: { target },
  })
}

export function registerUser(request: RegisterRequest) {
  return apiRequest<void>({
    method: 'POST',
    url: '/api/user/auth/register',
    data: request,
  })
}

export function sendForgotPasswordCode(target: string) {
  return apiRequest<void>({
    method: 'POST',
    url: '/api/user/auth/password/forgot/code',
    data: { target },
  })
}

export function verifyForgotPasswordCode(target: string, code: string) {
  return apiRequest<PasswordVerificationResponse>({
    method: 'POST',
    url: '/api/user/auth/password/forgot/verify',
    data: { target, code },
  })
}

export function resetPassword(
  resetToken: string,
  newPassword: string,
  confirmPassword: string,
) {
  return apiRequest<void>({
    method: 'POST',
    url: '/api/user/auth/password/reset',
    data: { resetToken, newPassword, confirmPassword },
  })
}
