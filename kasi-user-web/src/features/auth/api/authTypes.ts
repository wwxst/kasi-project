export interface UserLoginRequest {
  account: string
  password: string
}

export interface UserLoginResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: UserLoginInfo
}

export interface UserLoginInfo {
  userNo: string
  nickname: string | null
  mobile: string | null
  email: string | null
  avatarUrl: string | null
}

export interface RegisterRequest {
  account: string
  verificationCode: string
  password: string
  confirmPassword: string
}

export interface PasswordVerificationResponse {
  resetToken: string
  expiresIn: number
}
