export function resolveApiAssetUrl(value: string | null | undefined) {
  if (!value || /^(?:https?:|data:|blob:)/i.test(value))
    return value ?? undefined

  const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
  if (!baseUrl) return value
  return `${baseUrl}${value.startsWith('/') ? value : `/${value}`}`
}
