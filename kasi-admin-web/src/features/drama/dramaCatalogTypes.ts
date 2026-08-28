export type DramaLocalStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE'

export type DramaSyncType = 'FULL' | 'INCREMENTAL'

export type DramaSyncStatus =
  'IDLE' | 'REQUESTED' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface DramaCatalogPageQuery {
  page: number
  size: number
  providerId?: number
  title?: string
  language?: string
  remoteShowStatus?: string
  localStatus?: DramaLocalStatus
}

export interface DramaCatalogListItem {
  id: number
  externalDramaId: string
  title: string | null
  originalTitle: string | null
  titleZh: string | null
  coverUrl: string | null
  labelNames: string[] | null
  language: string | null
  dramaType: string | null
  categoryName: string | null
  remoteShowStatus: string | null
  localStatus: DramaLocalStatus
  remoteCreatedAt: string | null
  remoteUpdatedAt: string | null
  lastSeenAt: string | null
  updatedAt: string | null
}

export interface DramaCatalogPage {
  list: DramaCatalogListItem[]
  page: number
  size: number
  total: number
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

export interface DramaCatalogDetail extends DramaCatalogListItem {
  description: string | null
  createdAt: string | null
  contents: DramaContent[]
}

export interface DramaSyncTask {
  id: number
  syncType: DramaSyncType
  language: string
  status: DramaSyncStatus
  pageNo: number
  updateTime: number | null
  totalFetched: number
  totalUpserted: number
  insertedCount: number
  updatedCount: number
  skippedCount: number
  errorCount: number
  lastSuccessAt: string | null
  lastErrorCode: string | null
  lastErrorMessage: string | null
}

export interface RequestDramaSync {
  providerId: number
  syncType: DramaSyncType
  languages?: string[]
}

export interface UpdateDramaLocalStatusRequest {
  localStatus: DramaLocalStatus
}
