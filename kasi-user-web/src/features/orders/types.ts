export type PromotionOrderStatus = 'UNPAID' | 'PAID' | 'REFUNDED'

export interface PromotionOrder {
  externalOrderId: string
  currency: string
  status: PromotionOrderStatus
  paidAt?: string | null
  trackingNo?: string | null
  commissionAmount?: number | null
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
  calculatedCommission: number
  reversedCommission: number
  netCommission: number
}
