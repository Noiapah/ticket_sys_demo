import type { Ticket } from '../domain/types'
import { categories, TRANSFER_CATEGORY } from '../data/categories'
import { seedEmployees } from './employees'

const now = Date.now()
const ago = (minutes: number) => new Date(now - minutes * 60_000).toISOString()

const firstNames = ['Ola', 'Kari', 'Per', 'Lise', 'Anne', 'Jon', 'Mina', 'Emil', 'Sara', 'Arne']
const lastNames = ['Hansen', 'Olsen', 'Nilsen', 'Berg', 'Johansen', 'Larsen', 'Solheim']
const descriptions = [
  'Trenger hjelp til å overføre innhold og kontrollere sikkerhetskopien.',
  'Telefonen mister forbindelsen til trådløst nettverk.',
  'Kunden kommer ikke inn på kontoen sin.',
  'E-post synkroniseres ikke på den nye enheten.',
  'Mistenkelig popup vises når nettleseren åpnes.',
  'Appen avsluttes under oppstart.',
  'Varslinger og lyd virker ikke som forventet.',
  'Generell veiledning og kontroll av innstillinger.'
]
const devices: Array<Pick<Ticket, 'deviceType' | 'manufacturer' | 'deviceModel' | 'operatingSystem'>> = [
  { deviceType: 'PHONE', manufacturer: 'Apple', deviceModel: 'iPhone 16', operatingSystem: 'IOS' },
  { deviceType: 'PHONE', manufacturer: 'Samsung', deviceModel: 'Samsung Galaxy S25', operatingSystem: 'ANDROID' },
  { deviceType: 'PHONE', manufacturer: 'Google', deviceModel: 'Google Pixel 9', operatingSystem: 'ANDROID' },
  { deviceType: 'PHONE', manufacturer: 'Doro', deviceModel: 'Doro Smartphone', operatingSystem: 'ANDROID' },
  { deviceType: 'TABLET', manufacturer: 'Apple', deviceModel: 'iPad Air', operatingSystem: 'IOS' },
  { deviceType: 'TABLET', manufacturer: 'Samsung', deviceModel: 'Samsung Galaxy Tab S10', operatingSystem: 'ANDROID' },
  { deviceType: 'SMARTWATCH', manufacturer: 'Apple', deviceModel: 'Apple Watch Series 10', operatingSystem: 'IOS' },
  { deviceType: 'COMPUTER', manufacturer: 'Lenovo', deviceModel: 'Lenovo IdeaPad', operatingSystem: 'OTHER' }
]
const statuses: Ticket['status'][] = ['CLOSED', 'CLOSED', 'CLOSED', 'CLOSED', 'CLOSED', 'CLOSED', 'IN_PROGRESS', 'IN_PROGRESS', 'WAITING', 'ESCALATED']
const resolutionMinutes = [18, 45, 80, 150]

function phoneDisplay(number: string) {
  return `${number.slice(0, 2)} ${number.slice(2, 4)} ${number.slice(4, 6)} ${number.slice(6, 8)}`
}

export const seedTickets: Ticket[] = Array.from({ length: 140 }, (_, offset) => {
  const sequence = offset + 1
  const id = 1000 + sequence
  const customerNumber = (offset % 70) + 1
  const localPhone = String(45_000_000 + customerNumber)
  const createdMinutesAgo = (141 - sequence) * 35
  const status = statuses[offset % statuses.length]
  const duration = resolutionMinutes[offset % resolutionMinutes.length]
  const category = categories[offset % categories.length]
  const creator = seedEmployees[offset % seedEmployees.length]
  const assignee = seedEmployees[(offset + 2) % seedEmployees.length]
  const urgent = sequence % 11 === 0
  const actionMinutesAgo = status === 'CLOSED' ? createdMinutesAgo - duration : createdMinutesAgo - 10
  const history = [
    { id: id * 10, actorEmployeeId: creator.id, actorName: creator.name, eventType: 'CREATED', summary: 'Saken ble opprettet', createdAt: ago(createdMinutesAgo) },
    { id: id * 10 + 1, actorEmployeeId: assignee.id, actorName: assignee.name, eventType: status === 'CLOSED' ? 'CLOSED' : 'STATUS', summary: status === 'CLOSED' ? 'Saken ble lukket' : status === 'ESCALATED' ? 'Status satt til Eskalert' : status === 'WAITING' ? 'Status satt til Venter' : 'Status satt til Pågår', createdAt: ago(actionMinutesAgo) }
  ]
  if (urgent) history.push({ id: id * 10 + 2, actorEmployeeId: creator.id, actorName: creator.name, eventType: 'URGENT', summary: 'Markert som haster', createdAt: ago(actionMinutesAgo) })

  return {
    id,
    version: history.length,
    customerName: `${firstNames[offset % firstNames.length]} ${lastNames[Math.floor(offset / firstNames.length) % lastNames.length]}`,
    customerPhone: phoneDisplay(localPhone),
    customerPhoneNormalized: `+47${localPhone}`,
    ...devices[offset % devices.length],
    newDeviceModel: category === TRANSFER_CATEGORY ? 'iPhone 17' : '',
    category,
    description: descriptions[offset % descriptions.length],
    createdById: creator.id,
    createdByName: creator.name,
    assignedToId: assignee.id,
    assignedToName: assignee.name,
    status,
    urgent,
    createdAt: ago(createdMinutesAgo),
    updatedAt: ago(actionMinutesAgo),
    closedAt: status === 'CLOSED' ? ago(actionMinutesAgo) : null,
    comments: sequence % 4 === 0 ? [{ id: id, employeeId: assignee.id, employeeName: assignee.name, text: 'Kunden er oppdatert. Saken følges opp som avtalt.', createdAt: ago(createdMinutesAgo - 8) }] : [],
    history
  }
})
