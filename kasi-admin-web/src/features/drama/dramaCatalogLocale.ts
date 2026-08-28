export const dramaLanguageLabels: Record<string, string> = {
  ENGLISH: '英语',
  SPANISH: '西班牙语',
  PORTUGUESE: '葡萄牙语',
  DEUTSCH: '德语',
  FRENCH: '法语',
  BAHASA_INDONESIA: '印尼语',
  KOREAN: '韩语',
  ARAB: '阿拉伯语',
  THAI: '泰语',
  JAPANESE: '日语',
  TRADITIONAL_CHINESE: '中文（繁体）',
  POLISH: '波兰语',
  TURKISH: '土耳其语',
}

export function formatDramaLanguage(value: string | null | undefined) {
  if (!value) return '-'
  return dramaLanguageLabels[value] ?? value
}
