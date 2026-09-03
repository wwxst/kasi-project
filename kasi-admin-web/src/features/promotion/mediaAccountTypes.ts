export type MediaType = 'FACEBOOK' | 'TIKTOK' | 'YOUTUBE' | 'INSTAGRAM'

export type FilingStatus = 'PENDING' | 'APPROVED' | 'FAILED'

export interface MediaAccountPageQuery {
  page: number
  size: number
  userNo?: string
  mediaType?: MediaType
  accountStatus?: number
  providerId?: number
  filingStatus?: FilingStatus
}

export interface MediaAccountPageResult<T> {
  list: T[]
  page: number
  size: number
  total: number
}

export interface AdminMediaAccountListItem {
  id: number
  userNo: string
  nickname: string | null
  realName: string | null
  mediaType: MediaType
  externalAccountId: string
  accountName: string | null
  providerId: number | null
  status: number
  filingStatus: FilingStatus | null
  updatedAt: string | null
}

export interface MediaFiling {
  providerId: number | null
  providerName: string | null
  status: FilingStatus
  remoteStatus: string | null
  externalFilingId: string | null
  filingTime: string | null
  operateTime: string | null
  nextActionAt: string | null
  lastSubmittedAt: string | null
  lastQueriedAt: string | null
  lastErrorCode: string | null
  lastErrorMessage: string | null
}

export interface MediaAccountDetail {
  id: number
  mediaType: MediaType
  externalAccountId: string
  accountName: string | null
  accountLink: string | null
  status: number
  createdAt: string | null
  updatedAt: string | null
  filings: MediaFiling[]
}

export interface AdminMediaAccountDetail {
  id: number
  userNo: string
  nickname: string | null
  realName: string | null
  mediaAccount: MediaAccountDetail
}

export interface AdminUpdateMediaAccountRequest {
  mediaType: MediaType
  externalAccountId: string
  accountName?: string
  accountLink?: string
  status: number
}

export interface DramaProviderOption {
  id: number
  providerName: string
  providerCode: string
}
