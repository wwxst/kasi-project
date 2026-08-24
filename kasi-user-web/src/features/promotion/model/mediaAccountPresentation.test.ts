import { describe, expect, it } from 'vitest'
import type { MediaAccount } from '../api/mediaAccountTypes'
import {
  formatMediaType,
  getFilingView,
  isIdentityEditable,
} from './mediaAccountPresentation'

const account = (status?: string): MediaAccount => ({
  id: 1,
  mediaType: 'TIKTOK',
  externalAccountId: 'creator-1',
  accountName: 'Creator 1',
  accountLink: null,
  status: 1,
  filings: status
    ? [{ providerId: 1, providerName: 'GoodShort', status: status as never }]
    : [],
})

describe('media account presentation', () => {
  it.each([
    ['PENDING', '审核中'],
    ['APPROVED', '已加白'],
    ['FAILED', '已失败'],
  ])('maps %s to %s', (status, label) => {
    expect(getFilingView(account(status)).label).toBe(label)
  })

  it('maps an account without a filing to the pending state', () => {
    expect(getFilingView(account()).label).toBe('审核中')
  })

  it('only allows identity changes before approval', () => {
    expect(isIdentityEditable(account('PENDING'))).toBe(true)
    expect(isIdentityEditable(account('FAILED'))).toBe(true)
    expect(isIdentityEditable(account('APPROVED'))).toBe(false)
  })

  it('formats media platform names for the table', () => {
    expect(formatMediaType('TIKTOK')).toBe('TikTok')
    expect(formatMediaType('INSTAGRAM')).toBe('Instagram')
  })
})
