export type MediaType = 'TIKTOK' | 'YOUTUBE' | 'FACEBOOK' | 'INSTAGRAM'
export type LinkVariant = 'LANDING' | 'ONELINK'
export type PromotionLinkStatus = 'PENDING' | 'SUCCESS' | 'FAILED'

/**
 * 推广任务列表行：一个 GoodShort 口令一行，
 * LANDING/ONELINK 两个变体共用同一份转化日报。
 * analyticsConflict 为 true 时媒体无法确定，媒体和转化指标均为空。
 */
export interface PromotionLink {
  id: number
  providerId: number
  providerName: string | null
  dramaId: number
  dramaTitle: string | null
  campaignName: string | null
  mediaType: MediaType | null
  externalCode: string
  landingUrl: string | null
  oneLinkUrl: string | null
  analyticsConflict: boolean
  clickCount: number | null
  attributedUserCount: number | null
  newRegisteredUserCount: number | null
  newPaidUserCount: number | null
  newMemberUserCount: number | null
  paidUserCount: number | null
  orderCount: number | null
  createdAt: string
}

/** 生成推广任务时平台返回的单个链接变体记录。 */
export interface PromotionLinkVariant {
  id: number
  mediaType: MediaType
  linkVariant: LinkVariant
  externalCode: string | null
  shareUrl: string | null
  status: PromotionLinkStatus
  lastErrorCode: string | null
  lastErrorMessage: string | null
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
  links: PromotionLinkVariant[]
  complete: boolean
}

export interface CreatePromotionLinksInput {
  providerId: number
  dramaId: number
  mediaTypes: MediaType[]
  requestKey: string
  campaignName?: string
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T | null
}
