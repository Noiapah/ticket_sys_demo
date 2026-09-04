import type { Ticket } from '../domain/types'

const now = Date.now()
const ago = (minutes: number) => new Date(now - minutes * 60_000).toISOString()

export const seedTickets: Ticket[] = [
  {
    id: 184, version: 1, customerName: 'Ola Hansen', customerPhone: '991 23 456', customerPhoneNormalized: '+4799123456',
    deviceType: 'PHONE', manufacturer: 'Apple', deviceModel: 'iPhone 15 Pro', operatingSystem: 'IOS',
    category: 'Dataoverføring / sikkerhetskopi / oppsett', description: 'Overfør data fra gammel Samsung til ny iPhone.',
    createdById: 1, createdByName: 'Emma', assignedToId: 1, assignedToName: 'Emma', status: 'IN_PROGRESS', urgent: false,
    createdAt: ago(12), updatedAt: ago(12), comments: [],
    history: [
      { id: 1, actorEmployeeId: 1, actorName: 'Emma', eventType: 'CREATED', summary: 'Saken ble opprettet', createdAt: ago(12) },
      { id: 2, actorEmployeeId: 1, actorName: 'Emma', eventType: 'STATUS', summary: 'Status satt til Pågår', createdAt: ago(12) }
    ]
  },
  {
    id: 185, version: 1, customerName: 'Kari Olsen', customerPhone: '980 44 221', customerPhoneNormalized: '+4798044221',
    deviceType: 'PHONE', manufacturer: 'Samsung', deviceModel: 'Samsung Galaxy S24', operatingSystem: 'ANDROID',
    category: 'Konto / brukernavn / passord', description: 'Kommer ikke inn på Google-konto.',
    createdById: 2, createdByName: 'Daniel', assignedToId: 2, assignedToName: 'Daniel', status: 'WAITING', urgent: false,
    createdAt: ago(43), updatedAt: ago(8), comments: [{ id: 1, employeeId: 2, employeeName: 'Daniel', text: 'Kunden leter etter gjenopprettingskode.', createdAt: ago(8) }],
    history: [{ id: 3, actorEmployeeId: 2, actorName: 'Daniel', eventType: 'CREATED', summary: 'Saken ble opprettet', createdAt: ago(43) }]
  },
  {
    id: 186, version: 1, customerName: 'Per Nilsen', customerPhone: '412 09 876', customerPhoneNormalized: '+4741209876',
    deviceType: 'PHONE', manufacturer: 'Google', deviceModel: 'Google Pixel 9', operatingSystem: 'ANDROID',
    category: 'App-problemer', description: 'BankID stopper under aktivering.',
    createdById: 1, createdByName: 'Emma', assignedToId: 1, assignedToName: 'Emma', status: 'ESCALATED', urgent: true,
    createdAt: ago(78), updatedAt: ago(3), comments: [],
    history: [{ id: 4, actorEmployeeId: 1, actorName: 'Emma', eventType: 'URGENT', summary: 'Markert som haster', createdAt: ago(3) }]
  },
  {
    id: 176, version: 2, customerName: 'Lise Berg', customerPhone: '930 11 202', customerPhoneNormalized: '+4793011202',
    deviceType: 'PHONE', manufacturer: 'Doro', deviceModel: 'Doro Smartphone', operatingSystem: 'ANDROID',
    category: 'Systemproblemer', description: 'Varslinger var slått av.',
    createdById: 4, createdByName: 'Sofie', assignedToId: 4, assignedToName: 'Sofie', status: 'CLOSED', urgent: false,
    createdAt: ago(2880), updatedAt: ago(2820), closedAt: ago(2820), comments: [],
    history: [{ id: 5, actorEmployeeId: 4, actorName: 'Sofie', eventType: 'CLOSED', summary: 'Saken ble lukket', createdAt: ago(2820) }]
  }
]

