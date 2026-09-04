import type { ReportFilter, TicketDraft, TicketPatch, TicketStatus } from '../domain/types'
import type { TicketGateway, TicketQuery } from './TicketGateway'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, { ...init, headers: { 'Content-Type': 'application/json', ...init?.headers } })
  if (!response.ok) {
    const body = await response.json().catch(() => ({ message: `Feil ${response.status}` }))
    throw new Error(body.message ?? 'En ukjent feil oppstod.')
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

const params = (value: object) => {
  const result = new URLSearchParams()
  Object.entries(value).forEach(([key, item]) => item !== undefined && item !== '' && result.set(key, String(item)))
  return result.toString()
}

export const httpGateway: TicketGateway = {
  bootstrap: () => request('/bootstrap'),
  setCurrentEmployee: employeeId => request('/settings/current-employee', { method: 'PUT', body: JSON.stringify({ employeeId }) }),
  async matchCustomer(phone) {
    const response = await fetch(`/api/customers/match?${params({ phone })}`)
    if (response.status === 204) return null
    if (!response.ok) throw new Error('Kunne ikke slå opp kunden.')
    return response.json()
  },
  listTickets: (query: TicketQuery = {}) => request(`/tickets?${params(query)}`),
  getTicket: id => request(`/tickets/${id}`),
  createTicket: (draft: TicketDraft, actorId: number) => request('/tickets', { method: 'POST', body: JSON.stringify({ ...draft, actorId }) }),
  updateTicket: (id: number, patch: TicketPatch, actorId: number) => request(`/tickets/${id}`, { method: 'PATCH', body: JSON.stringify({ ...patch, actorId }) }),
  addComment: (id, text, actorId, version) => request(`/tickets/${id}/comments`, { method: 'POST', body: JSON.stringify({ text, actorId, version }) }),
  assign: (id, employeeId, actorId, version) => request(`/tickets/${id}/assignment`, { method: 'POST', body: JSON.stringify({ employeeId, actorId, version }) }),
  setStatus: (id, status: TicketStatus, actorId, version) => request(`/tickets/${id}/status`, { method: 'POST', body: JSON.stringify({ status, actorId, version }) }),
  setUrgent: (id, urgent, actorId, version) => request(`/tickets/${id}/urgent`, { method: 'POST', body: JSON.stringify({ urgent, actorId, version }) }),
  listEmployees: () => request('/employees'),
  addEmployee: name => request('/employees', { method: 'POST', body: JSON.stringify({ name }) }),
  updateEmployee: (id, changes) => request(`/employees/${id}`, { method: 'PATCH', body: JSON.stringify(changes) }),
  getTemporaryInfo: ticketId => request(`/tickets/${ticketId}/temporary-info`),
  saveTemporaryInfo: (ticketId, values) => request(`/tickets/${ticketId}/temporary-info`, { method: 'PUT', body: JSON.stringify({ values }) }),
  clearTemporaryInfo: (ticketId, key) => request(`/tickets/${ticketId}/temporary-info${key ? `/${encodeURIComponent(key)}` : ''}`, { method: 'DELETE' }),
  reportSummary: filter => request(`/reports/summary?${params(filter)}`),
  async exportReport(filter: ReportFilter) {
    const response = await fetch(`/api/reports/xlsx?${params(filter)}`)
    if (!response.ok) throw new Error('Kunne ikke lage Excel-rapporten.')
    return response.blob()
  },
  backup: async destination => (await request<{ message: string }>('/maintenance/backup', { method: 'POST', body: JSON.stringify({ path: destination }) })).message,
  restore: source => request('/maintenance/restore', { method: 'POST', body: JSON.stringify({ path: source }) })
}
