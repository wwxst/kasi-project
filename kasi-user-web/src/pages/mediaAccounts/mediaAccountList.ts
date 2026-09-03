import type {
  MediaAccount,
  MediaFiling,
} from '../../features/mediaAccounts/types'
import type { MediaAccountFilters } from './components/SearchForm'

export interface MediaAccountPageResult {
  items: MediaAccount[]
  total: number
}

export function getGoodShortFiling(
  account: MediaAccount,
): MediaFiling | undefined {
  return account.filings.find(
    (filing) => filing.providerName?.trim().toLocaleLowerCase() === 'goodshort',
  )
}

export function filterAndPaginateMediaAccounts(
  accounts: MediaAccount[],
  filters: MediaAccountFilters,
  page: number,
  pageSize: number,
): MediaAccountPageResult {
  const keyword = filters.keyword.trim().toLocaleLowerCase()
  const filtered = accounts.filter((account) => {
    const matchesKeyword =
      keyword.length === 0 ||
      account.externalAccountId.toLocaleLowerCase().includes(keyword) ||
      (account.accountName ?? '').toLocaleLowerCase().includes(keyword)
    const matchesMediaType =
      filters.mediaType === undefined || account.mediaType === filters.mediaType
    return matchesKeyword && matchesMediaType
  })

  const safePage = Math.max(1, page)
  const safePageSize = Math.max(1, pageSize)
  const start = (safePage - 1) * safePageSize
  return {
    items: filtered.slice(start, start + safePageSize),
    total: filtered.length,
  }
}
