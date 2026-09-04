import type { CustomerMatch, Employee, ReportFilter, ReportSummary, TemporaryCredential, Ticket, TicketDraft, TicketPatch, TicketStatus } from '../domain/types'

export interface TicketQuery {
  scope?: 'active' | 'closed' | 'all'
  query?: string
  employeeId?: number
  category?: string
  from?: string
  to?: string
}

export interface TicketGateway {
  bootstrap(): Promise<{ employees: Employee[]; currentEmployeeId: number | null }>
  setCurrentEmployee(employeeId: number): Promise<void>
  matchCustomer(phone: string): Promise<CustomerMatch | null>
  listTickets(query?: TicketQuery): Promise<Ticket[]>
  getTicket(id: number): Promise<Ticket>
  createTicket(draft: TicketDraft, actorId: number): Promise<Ticket>
  updateTicket(id: number, patch: TicketPatch, actorId: number): Promise<Ticket>
  addComment(id: number, text: string, actorId: number, version: number): Promise<Ticket>
  assign(id: number, employeeId: number, actorId: number, version: number): Promise<Ticket>
  setStatus(id: number, status: TicketStatus, actorId: number, version: number): Promise<Ticket>
  setUrgent(id: number, urgent: boolean, actorId: number, version: number): Promise<Ticket>
  listEmployees(): Promise<Employee[]>
  addEmployee(name: string): Promise<Employee>
  updateEmployee(id: number, changes: { name?: string; active?: boolean }): Promise<Employee>
  getTemporaryInfo(ticketId: number): Promise<TemporaryCredential[]>
  saveTemporaryInfo(ticketId: number, values: Array<{ key: string; label: string; value: string }>): Promise<TemporaryCredential[]>
  clearTemporaryInfo(ticketId: number, key?: string): Promise<void>
  reportSummary(filter: ReportFilter): Promise<ReportSummary>
  exportReport(filter: ReportFilter): Promise<Blob>
  backup(destination?: string): Promise<string>
  restore(source: string): Promise<{ message: string; restarting: boolean }>
}
