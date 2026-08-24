export interface CurrentUser {
  userNo: string
  nickname: string | null
  realName: string | null
  mobile: string | null
  email: string | null
  avatarUrl: string | null
  status: number
  lastLoginAt: string | null
  lastLoginIp: string | null
  createdAt: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}
