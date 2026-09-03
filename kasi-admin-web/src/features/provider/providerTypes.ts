export type ProviderCapability =
  | 'FULL_DRAMA_SYNC'
  | 'INCREMENTAL_DRAMA_SYNC'
  | 'FREE_CONTENT_PREVIEW'
  | 'SINGLE_DOWNLOAD'
  | 'BATCH_DOWNLOAD'
  | 'ACCOUNT_FILING'
  | 'FILING_STATUS_QUERY'
  | 'PROMOTION_LINK'
  | 'PROMOTION_CODE'
  | 'TIKTOK_ANCHOR'
  | 'ORDER_SYNC'
  | 'ANALYTICS_SYNC'

export interface ProviderConnection {
  id: number
  connectionName: string
  mediaRootDomain?: string | null
  baseUrl: string | null
  partnerId: string | null
  currency: string
  status: number
  credentialConfigured: boolean
  filingMode?: 'API' | 'MANUAL'
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
  filingMode: 'API' | 'MANUAL'
}

export interface ProviderConnectionTestResult {
  reachable: boolean
  message: string
  testedAt: string
}
