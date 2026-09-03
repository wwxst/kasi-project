export type MediaType = 'TIKTOK' | 'FACEBOOK' | 'YOUTUBE' | 'INSTAGRAM'

export type FilingStatus = 'PENDING' | 'APPROVED' | 'FAILED'

export interface MediaFiling {
  providerId: number | null
  providerName: string | null
  status: FilingStatus
  remoteStatus: string | null
  externalFilingId: string | null
  filingTime: string | null
  operateTime: string | null
  operateBy: string | null
  lastSubmittedAt: string | null
  lastQueriedAt: string | null
  nextActionAt: string | null
  lastErrorCode: string | null
  lastErrorMessage: string | null
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

export interface MediaAccountApiResponse<T> {
  code: number
  message: string
  data: T | null
}

export interface CreateMediaAccountInput {
  mediaType: MediaType
  externalAccountId: string
  accountName: string
  accountLink: string
}
