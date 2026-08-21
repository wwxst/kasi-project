export interface AdminLoginRequest {
  account: string
  password: string
}

export interface AdminInfo {
  id: number
  username: string
  realName: string
  mobile: string | null
  email: string | null
  avatarUrl: string | null
  isSuperAdmin: number
}

export interface AdminLoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  admin: AdminInfo
}

export interface UpdateAdminProfileRequest {
  username: string
  realName: string
  mobile?: string | null
  email?: string | null
}

export interface ChangeAdminPasswordRequest {
  newPassword: string
  confirmPassword: string
}
