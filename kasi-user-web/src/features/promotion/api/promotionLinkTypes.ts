export type PromotionLinkStatus = 'PENDING' | 'SUCCESS' | 'FAILED'

export type PromotionLinkLandingType = 'DEFAULT' | 'ONELINK'

export interface CreatePromotionLinkRequest {
  providerId: number
  dramaId: number
  mediaAccountId: number
  requestKey: string
  campaignName?: string
  landingType: PromotionLinkLandingType
}

export interface PromotionLink {
  id: number
  providerId: number
  providerName?: string | null
  dramaId: number
  dramaTitle?: string | null
  mediaAccountId: number
  mediaType?: string | null
  mediaAccountName?: string | null
  campaignName?: string | null
  trackingNo?: string | null
  externalCode?: string | null
  shareUrl?: string | null
  customParams?: string | null
  landingType: PromotionLinkLandingType
  status: PromotionLinkStatus
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface PromotionLinkPage {
  list: PromotionLink[]
  page: number
  size: number
  total: number
}
