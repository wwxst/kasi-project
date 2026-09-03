import type { DramaListItem } from '../../features/dramas/types'
import type { DramaFilters } from './components/SearchForm'

export function filterDramas(
  dramas: DramaListItem[],
  filters: DramaFilters,
): DramaListItem[] {
  const title = filters.title.toLocaleLowerCase()
  return dramas.filter((drama) => {
    const matchesTitle =
      title.length === 0 ||
      (drama.titleZh ?? '').toLocaleLowerCase().includes(title) ||
      (drama.title ?? '').toLocaleLowerCase().includes(title) ||
      drama.externalDramaId.toLocaleLowerCase().includes(title)
    const matchesLanguage =
      filters.language === undefined || drama.language === filters.language
    return matchesTitle && matchesLanguage
  })
}

export function formatDramaDate(value: string | null) {
  if (!value) return '暂无'
  return value.replace('T', ' ').slice(0, 16)
}
