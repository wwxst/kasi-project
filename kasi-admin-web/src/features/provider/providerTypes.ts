export type ProviderCapability =
  | 'FULL_DRAMA_SYNC'
  | 'INCREMENTAL_DRAMA_SYNC'
  | 'FREE_CONTENT_PREVIEW'
  | 'ACCOUNT_FILING'
  | 'FILING_STATUS_QUERY'
  | 'PROMOTION_LINK'
  | 'PROMOTION_CODE'
  | 'TIKTOK_ANCHOR'
  | 'ORDER_SYNC'
  | 'ANALYTICS_SYNC'

export type FilingMediaType = 'FACEBOOK' | 'TIKTOK' | 'YOUTUBE' | 'INSTAGRAM'

export interface ProviderConnection {
  id: number
  connectionName: string
  mediaRootDomain?: string | null
  baseUrl: string | null
  partnerId: string | null
  currency: string
  status: number
  credentialConfigured: boolean
  apiFilingMediaTypes: FilingMediaType[]
  createdAt: string
  updatedAt: string
}

export interface DramaProvider {
  id: number
  providerCode: string
  providerName: string
  status: number
  capabilities: ProviderCapability[]
  connection: ProviderConnection | null
}

export interface UpsertProviderConnectionRequest {
  mediaRootDomain?: string
  baseUrl?: string
  partnerId?: string
  apiKey?: string
  status: number
  apiFilingMediaTypes: FilingMediaType[]
}

export interface ProviderConnectionTestResult {
  reachable: boolean
  message: string
  testedAt: string
}
