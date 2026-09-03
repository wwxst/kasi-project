import { describe, expect, it } from 'vitest'
import type { MediaAccount } from '../../features/mediaAccounts/types'
import { filterAndPaginateMediaAccounts } from './mediaAccountList'

const accounts: MediaAccount[] = [
  {
    id: 1,
    mediaType: 'TIKTOK',
    externalAccountId: 'Creator-Alpha',
    accountName: 'Alpha',
    accountLink: null,
    status: 1,
    filings: [
      {
        providerName: 'GoodShort',
        status: 'APPROVED',
      } as MediaAccount['filings'][number],
    ],
  },
  {
    id: 2,
    mediaType: 'YOUTUBE',
    externalAccountId: 'channel-beta',
    accountName: 'Beta',
    accountLink: null,
    status: 0,
    filings: [
      {
        providerName: 'GoodShort',
        status: 'FAILED',
      } as MediaAccount['filings'][number],
    ],
  },
  {
    id: 3,
    mediaType: 'TIKTOK',
    externalAccountId: 'creator-gamma',
    accountName: 'Gamma',
    accountLink: null,
    status: 1,
    filings: [
      {
        providerName: 'GoodShort',
        status: 'PENDING',
      } as MediaAccount['filings'][number],
    ],
  },
]

describe('filterAndPaginateMediaAccounts', () => {
  it('filters by keyword and media type', () => {
    const result = filterAndPaginateMediaAccounts(
      accounts,
      {
        keyword: 'CREATOR',
        mediaType: 'TIKTOK',
      },
      1,
      10,
    )

    expect(result).toEqual({ items: [accounts[0], accounts[2]], total: 2 })
  })

  it('matches account name or id case-insensitively and slices the page', () => {
    const result = filterAndPaginateMediaAccounts(
      accounts,
      { keyword: 'a' },
      2,
      1,
    )

    expect(result).toEqual({ items: [accounts[1]], total: 3 })
  })

  it('returns an empty page for empty input', () => {
    expect(filterAndPaginateMediaAccounts([], { keyword: '' }, 1, 10)).toEqual({
      items: [],
      total: 0,
    })
  })

  it('keeps all accounts when no status filter is provided', () => {
    const accountWithOtherProvider = {
      ...accounts[0],
      id: 4,
      filings: [
        {
          providerName: 'OtherProvider',
          status: 'PENDING',
        } as MediaAccount['filings'][number],
      ],
    }

    expect(
      filterAndPaginateMediaAccounts(
        [...accounts, accountWithOtherProvider],
        { keyword: '' },
        1,
        10,
      ),
    ).toEqual({ items: [...accounts, accountWithOtherProvider], total: 4 })
  })
})
