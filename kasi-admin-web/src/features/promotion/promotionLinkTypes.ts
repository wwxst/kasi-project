export interface PromotionLinkQuery {
  page: number
  size: number
  userNo?: string
  providerId?: number
  externalCode?: string
  trackingNo?: string
}

export interface AdminPromotionLink {
  id: number
  userNo: string
  nickname: string | null
  realName: string | null
  providerId: number
  providerName: string | null
  dramaId: number
  dramaTitle: string | null
  mediaType: string
  linkVariant: string
  campaignName: string | null
  trackingNo: string
  externalCode: string
  shareUrl: string
  clickCount: number
  attributedUserCount: number
  newRegisteredUserCount: number
  newPaidUserCount: number
  newMemberUserCount: number
  paidUserCount: number
  orderCount: number
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
