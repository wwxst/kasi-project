export type DramaLocalStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE'

export interface PromotionDramaQuery {
  title?: string
  providerId?: number
  language?: string
  dramaType?: string
  localStatus?: DramaLocalStatus
  page?: number
  size?: number
}

export interface PromotionDrama {
  id: number
  providerId: number
  providerName?: string | null
  externalDramaId: string
  title: string
  originalTitle?: string | null
  description?: string | null
  coverUrl?: string | null
  language?: string | null
  dramaType?: string | null
  commissionScopes?: Array<'ORDER' | 'AD'>
  promotionDescription?: string | null
  remoteUpdatedAt?: string | null
  remoteShowStatus?: string | null
  localStatus: DramaLocalStatus
  updatedAt?: string | null
}

export interface PromotionDramaPage {
  list: PromotionDrama[]
  page: number
  size: number
  total: number
}
