import { describe, expect, it } from 'vitest'
import { ageLevel, normalizePhone } from './format'
import type { Ticket } from './types'

const ticket = (createdAt: string, urgent = false) => ({ createdAt, urgent } as Ticket)

describe('ticket age', () => {
  const now = new Date('2026-09-04T12:00:00Z')
  it('changes at exact boundaries and urgent overrides', () => {
    expect(ageLevel(ticket('2026-09-04T11:31:00Z'), now)).toBe('green')
    expect(ageLevel(ticket('2026-09-04T11:30:00Z'), now)).toBe('yellow')
    expect(ageLevel(ticket('2026-09-04T11:00:00Z'), now)).toBe('red')
    expect(ageLevel(ticket('2026-09-04T11:59:00Z', true), now)).toBe('red')
  })
})

describe('Norwegian phone normalization', () => {
  it.each(['99 12 34 56', '99123456', '+47 991 23 456', '0047 991 23 456'])('matches %s', input => {
    expect(normalizePhone(input).normalized).toBe('+4799123456')
  })
})

