export type MediaType = 'TIKTOK' | 'FACEBOOK' | 'YOUTUBE' | 'INSTAGRAM'

export type FilingStatus = 'PENDING' | 'APPROVED' | 'FAILED'

export interface MediaFiling {
  providerId: number
  providerName?: string | null
  status: FilingStatus
  remoteStatus?: string | null
  externalFilingId?: string | null
  filingTime?: string | null
  operateTime?: string | null
  lastSubmittedAt?: string | null
  lastQueriedAt?: string | null
  nextActionAt?: string | null
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
}

export interface MediaAccount {
  id: number
  mediaType: MediaType
  externalAccountId: string
  accountName: string | null
  accountLink: string | null
  status: number
  filings: MediaFiling[]
}

export interface MediaAccountDetail extends MediaAccount {
  createdAt: string
  updatedAt: string
}

export interface CreateMediaAccountRequest {
  mediaType: MediaType
  externalAccountId: string
  accountName?: string
  accountLink?: string
}

export interface UpdateMediaAccountRequest {
  mediaType: MediaType
  externalAccountId: string
  accountName?: string
  accountLink?: string
}
