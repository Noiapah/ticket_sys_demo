import { parsePhoneNumberFromString } from 'libphonenumber-js'
import type { Ticket } from './types'

export function normalizeText(value: string): string {
  return value.toLocaleLowerCase('nb-NO').normalize('NFKD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9+]+/g, ' ').trim()
}

export function normalizePhone(value: string): { normalized: string; valid: boolean } {
  const cleaned = value.trim().replace(/^00/, '+')
  const parsed = parsePhoneNumberFromString(cleaned, 'NO')
  if (parsed?.isValid()) return { normalized: parsed.number, valid: true }
  const digits = value.replace(/\D/g, '')
  return { normalized: digits, valid: false }
}

export function formatPhone(value: string): string {
  return parsePhoneNumberFromString(value, 'NO')?.formatNational() ?? value
}

export function ageMinutes(createdAt: string, now = new Date()): number {
  return Math.max(0, Math.floor((now.getTime() - new Date(createdAt).getTime()) / 60_000))
}

export function ageLabel(createdAt: string, now = new Date()): string {
  const minutes = ageMinutes(createdAt, now)
  if (minutes < 60) return `${minutes}m`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest ? `${hours}t ${rest}m` : `${hours}t`
}

export function ageLevel(ticket: Ticket, now = new Date()): 'green' | 'yellow' | 'red' {
  if (ticket.urgent) return 'red'
  const minutes = ageMinutes(ticket.createdAt, now)
  return minutes >= 60 ? 'red' : minutes >= 30 ? 'yellow' : 'green'
}

export const dateTime = new Intl.DateTimeFormat('nb-NO', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Europe/Oslo' })
export const timeOnly = new Intl.DateTimeFormat('nb-NO', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Oslo' })

