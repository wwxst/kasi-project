import type { PromotionProjectStatus } from '../promotionProject/promotionProjectTypes'

export interface PromotionProjectType {
  id: number
  code: string
  name: string
  status: PromotionProjectStatus
  sortOrder: number
  createdAt: string | null
  updatedAt: string | null
}

export interface PromotionProjectTypeFormValues {
  code: string
  name: string
  status: PromotionProjectStatus
  sortOrder: number
}
