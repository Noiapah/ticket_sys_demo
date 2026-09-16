import { afterEach, describe, expect, it, vi } from 'vitest'
import { SecretExposure } from './secretExposure'

afterEach(() => vi.useRealTimers())
describe('temporary secret exposure', () => {
  const secret = (expiresAt: number) => ({ key: 'code', label: 'Code', value: '1234', expiresAt: new Date(expiresAt).toISOString() })

  it('starts empty and masked, accepts only a requested response, and clears at expiry', () => {
    vi.useFakeTimers(); vi.setSystemTime(0)
    const exposure = new SecretExposure()
    expect(exposure.loaded).toBe(false)
    expect(exposure.values.code).toBe('')
    exposure.accept(exposure.begin(), [secret(1000)])
    expect(exposure.showValues).toBe(false)
    expect(exposure.values.code).toBe('1234')
    vi.setSystemTime(1000); exposure.expire()
    expect(exposure.values.code).toBe('')
    expect(exposure.credentials).toEqual([])
  })

  it('ignores in-flight responses after a session/employee/route change or blur', () => {
    const exposure = new SecretExposure()
    const generation = exposure.begin()
    exposure.clear()
    exposure.accept(generation, [secret(Date.now() + 1000)])
    expect(exposure.loaded).toBe(false)
    expect(exposure.values.code).toBe('')
  })

  it('clears saved and unsaved values on inactivity, including delayed responses', () => {
    vi.useFakeTimers(); vi.setSystemTime(0)
    const exposure = new SecretExposure()
    exposure.accept(exposure.begin(), [secret(86400000)])
    exposure.values.account = 'unsaved account'
    const pending = exposure.begin()
    vi.setSystemTime(300000); exposure.activity()
    exposure.accept(pending, [secret(86400000)])
    expect(exposure.loaded).toBe(false)
    expect(Object.values(exposure.values).every(value => value === '')).toBe(true)
  })

  it('rejects expired and unexpected fields in a reveal response', () => {
    vi.useFakeTimers(); vi.setSystemTime(500)
    const exposure = new SecretExposure()
    exposure.accept(exposure.begin(), [secret(400), { ...secret(1000), key: '__proto__' }])
    expect(exposure.credentials).toEqual([])
  })
})
