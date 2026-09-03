export type MediaType = 'TIKTOK' | 'YOUTUBE' | 'FACEBOOK' | 'INSTAGRAM'
export type LinkVariant = 'LANDING' | 'ONELINK'
export type PromotionLinkStatus = 'PENDING' | 'SUCCESS' | 'FAILED'

export interface PromotionLink {
  id: number
  providerId: number
  providerName: string | null
  dramaId: number
  dramaTitle: string | null
  batchNo: string
  requestKey: string
  mediaType: MediaType
  linkVariant: LinkVariant
  campaignName: string | null
  trackingNo: string | null
  externalCode: string | null
  shareUrl: string | null
  customParams: string | null
  status: PromotionLinkStatus
  lastErrorCode: string | null
  lastErrorMessage: string | null
  createdAt: string
  updatedAt: string
}

export interface PromotionLinkPage {
  list: PromotionLink[]
  page: number
  size: number
  total: number
}

export interface PromotionLinkBatch {
  batchNo: string | null
  requestKey: string
  links: PromotionLink[]
  complete: boolean
}

export interface CreatePromotionLinksInput {
  providerId: number
  dramaId: number
  mediaTypes: MediaType[]
  linkVariant?: LinkVariant
  requestKey: string
  campaignName?: string
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T | null
}
