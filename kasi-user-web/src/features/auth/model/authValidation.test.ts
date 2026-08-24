import { describe, expect, it } from 'vitest'
import { isPhoneOrEmail, isVerificationCode } from './authValidation'

describe('authentication validation', () => {
  it('accepts a mainland mobile number or a normal email address', () => {
    expect(isPhoneOrEmail('13800138000')).toBe(true)
    expect(isPhoneOrEmail('User@example.com')).toBe(true)
  })

  it('rejects malformed account values', () => {
    expect(isPhoneOrEmail('12800138000')).toBe(false)
    expect(isPhoneOrEmail('user@invalid')).toBe(false)
    expect(isPhoneOrEmail('plain-account')).toBe(false)
  })

  it('accepts only a six-digit verification code', () => {
    expect(isVerificationCode('123456')).toBe(true)
    expect(isVerificationCode('12345')).toBe(false)
    expect(isVerificationCode('12345a')).toBe(false)
  })
})
