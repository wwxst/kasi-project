export type PromotionTaskStatus = 'PENDING' | 'SUCCESS' | 'FAILED'
export type PromotionMediaType = 'TIKTOK' | 'FACEBOOK' | 'YOUTUBE' | 'INSTAGRAM'

export interface PromotionTask {
  id: number
  taskName: string
  mediaType: PromotionMediaType
  providerName?: string | null
  dramaTitle?: string | null
  trackingNo?: string | null
  externalCode?: string | null
  directUrl?: string | null
  status: PromotionTaskStatus
  lastErrorMessage?: string | null
  codeSearchCount: number
  directClickCount: number
  appClickCount: number
  leadCount: number
  orderAmount: string | number
  orderCount: number
  adAmount: string | number
  createdAt?: string | null
}

export interface PromotionTaskPage {
  list: PromotionTask[]
  page: number
  size: number
  total: number
}

export interface CreatePromotionTaskRequest {
  providerId: number
  dramaId: number
  taskName: string
  requestKey: string
  mediaTypes: PromotionMediaType[]
}
