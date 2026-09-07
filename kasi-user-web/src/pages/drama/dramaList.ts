export function formatDramaDate(value: string | null) {
  if (!value) return '暂无'
  return value.replace('T', ' ').slice(0, 16)
}
