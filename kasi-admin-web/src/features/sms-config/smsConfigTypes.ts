export interface SmsConfig {
  configured: boolean
  accessKeyIdConfigured: boolean
  accessKeySecretConfigured: boolean
  signName: string | null
  registerTemplateCode: string | null
  loginTemplateCode: string | null
  resetPasswordTemplateCode: string | null
  enabled: boolean
  smtpHost: string | null
  smtpPort: number | null
  smtpUsername: string | null
  smtpPasswordConfigured: boolean
  smtpFromAddress: string | null
  emailEnabled: boolean
  updatedAt: string | null
}
export interface UpdateSmsConfigRequest {
  accessKeyId?: string
  accessKeySecret?: string
  signName: string
  registerTemplateCode: string
  loginTemplateCode: string
  resetPasswordTemplateCode: string
  enabled: boolean
  smtpHost?: string
  smtpPort?: number
  smtpUsername?: string
  smtpPassword?: string
  smtpFromAddress?: string
  emailEnabled: boolean
}
