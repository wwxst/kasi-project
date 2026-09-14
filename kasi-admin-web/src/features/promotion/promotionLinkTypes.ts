export interface PromotionLinkQuery {
  page: number
  size: number
  userNo?: string
  providerId?: number
  externalCode?: string
  trackingNo?: string
}

/**
 * 管理端推广任务行：一个 GoodShort 口令一行，
 * LANDING/ONELINK 共用一份转化日报；跨媒体共用口令时媒体和转化指标为空。
 */
export interface AdminPromotionLink {
  id: number
  userNo: string
  nickname: string | null
  realName: string | null
  providerId: number
  providerName: string | null
  dramaId: number
  dramaTitle: string | null
  campaignName: string | null
  mediaType: string | null
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
  createdAt: string | null
}

export interface PromotionLinkPage {
  list: AdminPromotionLink[]
  page: number
  size: number
  total: number
}

export interface PromotionAnalyticalReportSyncRequest {
  providerId: number
  startDate: string
  endDate: string
}

export interface PromotionAnalyticalReportSyncResult {
  fetchedCount: number
  upsertedCount: number
}
