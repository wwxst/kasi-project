export type PromotionOrderStatus = 'UNPAID' | 'PAID' | 'REFUNDED' | 'UNKNOWN'

export type PromotionAttributionStatus = 'ATTRIBUTED' | 'UNATTRIBUTED'

export type PromotionCommissionStatus =
  'CALCULATED' | 'REVERSED' | 'NOT_APPLICABLE' | 'ERROR'

export interface PromotionOrderQuery {
  page: number
  size: number
  providerId?: number
  status?: PromotionOrderStatus
  attributionStatus?: PromotionAttributionStatus
  startDate?: string
  endDate?: string
}

export interface PromotionOrder {
  id: number
  providerId: number
  externalOrderId: string
  externalDramaId: string | null
  searchCode: string | null
  channelCode: string | null
  orderAmount: number
  currency: string
  status: PromotionOrderStatus
  paidAt: string | null
  customParams: string | null
  trackingNo: string | null
  userId: number | null
  mediaAccountId: number | null
  dramaId: number | null
  attributionStatus: PromotionAttributionStatus
  commissionAmount: number | null
  commissionStatus: PromotionCommissionStatus
  lastSyncedAt: string | null
}

export interface PromotionOrderPage {
  list: PromotionOrder[]
  page: number
  size: number
  total: number
}

export interface PromotionOrderSyncRequest {
  providerId: number
  startDate: string
  endDate: string
}

export interface PromotionOrderSyncResult {
  fetchedCount: number
  insertedCount: number
  updatedCount: number
  unattributedCount: number
}
