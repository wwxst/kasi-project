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
  createdAt: string | null
}

export interface LoginUser {
  userNo: string
  nickname: string | null
  mobile: string | null
  email: string | null
  avatarUrl: string | null
}

export interface LoginResult {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: LoginUser
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T | null
}
