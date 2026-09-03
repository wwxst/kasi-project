export type FilingMode = 'API' | 'MANUAL'

export interface ProviderFilingMode {
  providerId: number
  providerName: string
  filingMode: FilingMode
  connectionConfigured: boolean
}
