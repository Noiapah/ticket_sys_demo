import { seedEmployees } from '../data/employees'
import { seedTickets } from '../data/tickets'
import { normalizePhone, normalizeText } from '../domain/format'
import { statusLabels, type Employee, type HistoryEvent, type ReportFilter, type ReportSummary, type TemporaryCredential, type Ticket, type TicketDraft, type TicketPatch, type TicketStatus } from '../domain/types'
import type { TicketGateway, TicketQuery } from './TicketGateway'

let employees = structuredClone(seedEmployees)
let tickets = structuredClone(seedTickets)
let currentEmployeeId = 1
let nextTicketId = Math.max(...tickets.map(ticket => ticket.id)) + 1
let nextEventId = 100
let nextCommentId = 100
const secrets = new Map<number, TemporaryCredential[]>()
const copy = <T>(value: T): T => structuredClone(value)
const employee = (id: number) => employees.find(item => item.id === id) ?? (() => { throw new Error('Fant ikke den ansatte.') })()
const ticket = (id: number) => tickets.find(item => item.id === id) ?? (() => { throw new Error('Fant ikke saken.') })()

function ensureWritable(item: Ticket, version: number) {
  if (item.status === 'CLOSED') throw new Error('Åpne saken igjen før du gjør endringer.')
  if (item.version !== version) throw new Error('Saken er endret. Last den inn på nytt.')
}

function history(item: Ticket, actorId: number, eventType: string, summary: string): HistoryEvent {
  const actor = employee(actorId)
  const event = { id: nextEventId++, actorEmployeeId: actor.id, actorName: actor.name, eventType, summary, createdAt: new Date().toISOString() }
  item.history.push(event)
  item.updatedAt = event.createdAt
  item.version++
  return event
}

function filtered(query: TicketQuery = {}) {
  const needle = normalizeText(query.query ?? '')
  return tickets.filter(item => {
    if (query.scope === 'active' && item.status === 'CLOSED') return false
    if (query.scope === 'closed' && item.status !== 'CLOSED') return false
    if (query.employeeId && item.assignedToId !== query.employeeId) return false
    if (query.category && item.category !== query.category) return false
    if (!needle) return true
    const haystack = normalizeText([item.id, item.customerName, item.customerPhone, item.customerPhoneNormalized, item.deviceModel, item.description].join(' '))
    return needle.split(' ').every(token => haystack.includes(token))
  })
}

export const mockGateway: TicketGateway = {
  async bootstrap() { return { employees: copy(employees), currentEmployeeId } },
  async setCurrentEmployee(id) { currentEmployeeId = id },
  async matchCustomer(phone) { const normalized = normalizePhone(phone).normalized; const matches = tickets.filter(item => item.customerPhoneNormalized === normalized); return matches.length ? { id: matches[0].id, name: matches[0].customerName, phoneNormalized: normalized, previousTickets: matches.length } : null },
  async listTickets(query) { return copy(filtered(query)) },
  async getTicket(id) { return copy(ticket(id)) },
  async createTicket(draft: TicketDraft, actorId: number) {
    const actor = employee(actorId)
    const phone = normalizePhone(draft.customerPhone)
    const now = new Date().toISOString()
    const item: Ticket = { ...draft, id: nextTicketId++, version: 0, customerPhoneNormalized: phone.normalized, createdById: actor.id, createdByName: actor.name, assignedToId: actor.id, assignedToName: actor.name, status: 'IN_PROGRESS', urgent: false, createdAt: now, updatedAt: now, comments: [], history: [] }
    tickets.push(item)
    history(item, actorId, 'CREATED', 'Saken ble opprettet')
    history(item, actorId, 'STATUS', 'Status satt til Pågår')
    return copy(item)
  },
  async updateTicket(id: number, patch: TicketPatch, actorId: number) {
    const item = ticket(id); ensureWritable(item, patch.version)
    const labels: Array<[keyof TicketDraft, string]> = [['customerName', 'Kundenavn'], ['customerPhone', 'Telefonnummer'], ['deviceModel', 'Enhet'], ['category', 'Kategori'], ['description', 'Problem']]
    for (const [key, label] of labels) {
      if (patch[key] !== undefined && patch[key] !== item[key]) {
        const old = String(item[key]); (item as unknown as Record<string, unknown>)[key] = patch[key]
        history(item, actorId, 'EDITED', `${label} endret: ${old} → ${patch[key]}`)
      }
    }
    if (patch.customerPhone) item.customerPhoneNormalized = normalizePhone(patch.customerPhone).normalized
    return copy(item)
  },
  async addComment(id, text, actorId, version) {
    const item = ticket(id); ensureWritable(item, version); const actor = employee(actorId)
    item.comments.push({ id: nextCommentId++, employeeId: actor.id, employeeName: actor.name, text: text.trim(), createdAt: new Date().toISOString() })
    history(item, actorId, 'COMMENT', 'Kommentar lagt til')
    return copy(item)
  },
  async assign(id, employeeId, actorId, version) {
    const item = ticket(id); ensureWritable(item, version); const target = employee(employeeId)
    if (!target.active) throw new Error('Kan ikke tildele til en deaktivert ansatt.')
    const old = item.assignedToName; item.assignedToId = target.id; item.assignedToName = target.name
    history(item, actorId, 'ASSIGNED', `Tildelt endret: ${old} → ${target.name}`)
    return copy(item)
  },
  async setStatus(id, status, actorId, version) {
    const item = ticket(id)
    if (item.version !== version) throw new Error('Saken er endret. Last den inn på nytt.')
    if (item.status === 'CLOSED' && status !== 'IN_PROGRESS') throw new Error('En lukket sak kan bare åpnes igjen.')
    const old = item.status; item.status = status; item.closedAt = status === 'CLOSED' ? new Date().toISOString() : null
    history(item, actorId, status === 'CLOSED' ? 'CLOSED' : old === 'CLOSED' ? 'REOPENED' : 'STATUS', `${statusLabels[old]} → ${statusLabels[status]}`)
    return copy(item)
  },
  async setUrgent(id, urgent, actorId, version) {
    const item = ticket(id); ensureWritable(item, version); item.urgent = urgent
    history(item, actorId, 'URGENT', urgent ? 'Markert som haster' : 'Haster-markering fjernet')
    return copy(item)
  },
  async listEmployees() { return copy(employees) },
  async addEmployee(name) { const item = { id: Math.max(0, ...employees.map(e => e.id)) + 1, name: name.trim(), active: true }; employees.push(item); return copy(item) },
  async updateEmployee(id, changes) { const item = employee(id); Object.assign(item, changes); return copy(item) },
  async getTemporaryInfo(ticketId) {
    const now = Date.now(); const values = (secrets.get(ticketId) ?? []).filter(value => new Date(value.expiresAt).getTime() > now); secrets.set(ticketId, values); return copy(values)
  },
  async saveTemporaryInfo(ticketId, values) {
    const expiresAt = new Date(Date.now() + 24 * 60 * 60_000).toISOString()
    const saved = new Map((secrets.get(ticketId) ?? []).map(item => [item.key, item]))
    values.forEach(item => item.value.trim() ? saved.set(item.key, { ...item, value: item.value.trim(), expiresAt }) : saved.delete(item.key))
    secrets.set(ticketId, [...saved.values()]); return copy([...saved.values()])
  },
  async clearTemporaryInfo(ticketId, key) {
    if (!key) secrets.delete(ticketId); else secrets.set(ticketId, (secrets.get(ticketId) ?? []).filter(item => item.key !== key))
  },
  async reportSummary(filter: ReportFilter): Promise<ReportSummary> {
    const from = new Date(`${filter.from}T00:00:00`).getTime(); const to = new Date(`${filter.to}T23:59:59`).getTime()
    const cohort = tickets.filter(item => { const time = new Date(item.createdAt).getTime(); return time >= from && time <= to && (!filter.category || item.category === filter.category) })
    const closed = cohort.filter(item => item.closedAt)
    const durations = closed.map(item => (new Date(item.closedAt!).getTime() - new Date(item.createdAt).getTime()) / 60_000)
    const percentage = (predicate: (minutes: number) => boolean) => durations.length ? Math.round(durations.filter(predicate).length / durations.length * 100) : 0
    return { created: cohort.length, closed: closed.length, open: cohort.filter(item => item.status !== 'CLOSED').length, urgent: cohort.filter(item => item.urgent || item.history.some(event => event.eventType === 'URGENT' && event.summary.includes('Markert'))).length, escalated: cohort.filter(item => item.history.some(event => event.summary.includes('Eskalert'))).length, averageMinutes: durations.length ? Math.round(durations.reduce((a, b) => a + b, 0) / durations.length) : 0, within30Percent: percentage(m => m <= 30), within60Percent: percentage(m => m <= 60), over60Percent: percentage(m => m > 60) }
  },
  async exportReport() { return new Blob(['Rapporteksport krever skrivebordsversjonen.'], { type: 'text/plain' }) },
  async backup() { return 'Minnedata kan ikke sikkerhetskopieres.' },
  async restore() { return { message: 'Sikkerhetskopier kan bare gjenopprettes i skrivebordsversjonen.', restarting: false } }
}
