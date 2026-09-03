export type DramaLocalStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE'

export interface DramaListItem {
  id: number
  providerId: number | null
  providerName: string | null
  externalDramaId: string
  title: string | null
  originalTitle: string | null
  titleZh: string | null
  description: string | null
  coverUrl: string | null
  labelNames: string[]
  categoryName: string | null
  language: string | null
  remoteRank: number | null
  dramaType: string | null
  novelType: string | null
  novelSubType: number | null
  commissionScopes: string[]
  promotionDescription: string | null
  remoteShowStatus: string | null
  localStatus: DramaLocalStatus
  remoteCreatedAt: string | null
  remoteUpdatedAt: string | null
  lastSeenAt: string | null
  updatedAt: string | null
}

export interface DramaContent {
  id: number
  externalContentId: string | null
  sequenceNo: number
  title: string | null
  free: boolean
  durationSeconds: number | null
  remoteUpdatedAt: string | null
}

export interface DramaContentResource {
  id: number
  sequenceNo: number
  title: string | null
  free: boolean
  playUrl: string | null
  downloadUrl: string | null
}

export interface DramaDetail extends DramaListItem {
  contents: DramaContent[]
}

export interface DramaPage {
  list: DramaListItem[]
  page: number
  size: number
  total: number
}

export interface DramaPageQuery {
  page: number
  size: number
  title?: string
  language?: string
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T | null
}
