import type { MediaAccount, MediaType } from '../api/mediaAccountTypes'

const mediaTypeLabels: Record<MediaType, string> = {
  TIKTOK: 'TikTok',
  FACEBOOK: 'Facebook',
  YOUTUBE: 'YouTube',
  INSTAGRAM: 'Instagram',
}

const filingLabels = {
  PENDING: { label: '审核中', theme: 'warning' as const },
  APPROVED: { label: '已加白', theme: 'success' as const },
  FAILED: { label: '已失败', theme: 'danger' as const },
}

export function getFilingView(account: MediaAccount) {
  const filing =
    account.filings.find((item) => item.status === 'FAILED') ??
    account.filings.find((item) => item.status === 'PENDING') ??
    account.filings.find((item) => item.status === 'APPROVED')
  if (!filing)
    return { status: 'PENDING' as const, filing: null, ...filingLabels.PENDING }
  return { status: filing.status, filing, ...filingLabels[filing.status] }
}

export function isIdentityEditable(account: MediaAccount) {
  return !account.filings.some((filing) => filing.status === 'APPROVED')
}

export function formatMediaType(mediaType: MediaType) {
  return mediaTypeLabels[mediaType]
}

export function formatDateTime(value: string | null | undefined) {
  if (!value) return '暂无记录'
  return value.replace('T', ' ').slice(0, 19)
}
