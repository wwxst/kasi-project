const MOBILE_PATTERN = /^1[3-9]\d{9}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function isPhoneOrEmail(value: string) {
  const normalized = value.trim()
  return (
    MOBILE_PATTERN.test(normalized) ||
    (normalized.length <= 128 && EMAIL_PATTERN.test(normalized))
  )
}

export function isVerificationCode(value: string) {
  return /^\d{6}$/.test(value)
}
