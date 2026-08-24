export type PromotionOrderStatus = 'UNPAID' | 'PAID' | 'REFUNDED' | 'UNKNOWN'
export type PromotionCommissionStatus =
  'CALCULATED' | 'REVERSED' | 'NOT_APPLICABLE' | 'ERROR'

export interface PromotionOrder {
  id: number
  externalOrderId: string
  orderAmount: number
  currency: string
  status: PromotionOrderStatus
  paidAt?: string | null
  trackingNo?: string | null
  commissionAmount?: number | null
  commissionStatus?: PromotionCommissionStatus | null
}

export interface PromotionOrderPage {
  list: PromotionOrder[]
  page: number
  size: number
  total: number
}

export interface PromotionMonthlyCommission {
  month: string
  paidOrderCount: number
  grossOrderAmount: number
  calculatedCommission: number
  reversedCommission: number
  netCommission: number
}
