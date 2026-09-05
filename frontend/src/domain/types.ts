export type TicketStatus = 'IN_PROGRESS' | 'WAITING' | 'ESCALATED' | 'CLOSED'
export type DeviceType = 'PHONE' | 'TABLET' | 'SMARTWATCH' | 'COMPUTER' | 'OTHER'
export type OperatingSystem = 'IOS' | 'ANDROID' | 'OTHER'

export interface Employee {
  id: number
  name: string
  active: boolean
}

export interface CustomerMatch {
  id: number
  name: string
  phoneNormalized: string
  previousTickets: number
}

export interface Device {
  manufacturer: string
  family: string
  model: string
  aliases: string[]
  operatingSystem: OperatingSystem
  type: DeviceType
}

export interface Comment {
  id: number
  employeeId: number
  employeeName: string
  text: string
  createdAt: string
}

export interface HistoryEvent {
  id: number
  actorEmployeeId: number
  actorName: string
  eventType: string
  summary: string
  createdAt: string
}

export interface Ticket {
  id: number
  version: number
  customerName: string
  customerPhone: string
  customerPhoneNormalized: string
  deviceType: DeviceType
  manufacturer: string
  deviceModel: string
  newDeviceModel: string
  operatingSystem: OperatingSystem
  category: string
  description: string
  createdById: number
  createdByName: string
  assignedToId: number
  assignedToName: string
  status: TicketStatus
  urgent: boolean
  createdAt: string
  updatedAt: string
  closedAt?: string | null
  comments: Comment[]
  history: HistoryEvent[]
}

export interface TicketDraft {
  customerName: string
  customerPhone: string
  deviceType: DeviceType
  manufacturer: string
  deviceModel: string
  newDeviceModel: string
  operatingSystem: OperatingSystem
  category: string
  description: string
}

export interface TicketPatch extends Partial<TicketDraft> {
  version: number
}

export interface TemporaryCredential {
  key: string
  label: string
  value: string
  expiresAt: string
}

export interface ReportFilter {
  from: string
  to: string
  employeeId?: number
  category?: string
}

export interface ReportSummary {
  created: number
  closed: number
  open: number
  urgent: number
  escalated: number
  averageMinutes: number
  within30Percent: number
  within60Percent: number
  over60Percent: number
}

export const statusLabels: Record<TicketStatus, string> = {
  IN_PROGRESS: 'Pågår', WAITING: 'Venter', ESCALATED: 'Eskalert', CLOSED: 'Lukket'
}

export const deviceTypeLabels: Record<DeviceType, string> = {
  PHONE: 'Telefon', TABLET: 'Nettbrett', SMARTWATCH: 'Smartklokke', COMPUTER: 'Datamaskin', OTHER: 'Annet'
}
