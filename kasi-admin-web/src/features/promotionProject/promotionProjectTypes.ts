export type PromotionProjectStatus = 'ENABLED' | 'DISABLED'

export interface PromotionProject {
  id: number
  name: string
  coverImageUrl: string
  projectDocumentUrl: string
  status: PromotionProjectStatus
  sortOrder: number
  createdAt: string | null
  updatedAt: string | null
}

export interface PromotionProjectPage {
  list: PromotionProject[]
  page: number
  size: number
  total: number
}

export interface PromotionProjectQuery {
  page: number
  size: number
}

export interface PromotionProjectFormValues {
  name: string
  projectDocumentUrl: string
  status: PromotionProjectStatus
  sortOrder: number
}

export interface SavePromotionProjectRequest extends PromotionProjectFormValues {
  coverFile?: File
}
