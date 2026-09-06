import { describe, expect, it } from 'vitest'
import { seedEmployees } from './employees'
import { seedTickets } from './tickets'

describe('report demo data', () => {
  it('contains employees with inactive accounts', () => {
    expect(seedEmployees).toHaveLength(8)
    expect(seedEmployees.filter(employee => employee.active)).toHaveLength(6)
    expect(seedEmployees.filter(employee => !employee.active)).toHaveLength(2)
  })

  it('contains 140 tickets with useful status variation', () => {
    expect(seedTickets).toHaveLength(140)
    expect(seedTickets.filter(ticket => ticket.status === 'CLOSED')).toHaveLength(84)
    expect(seedTickets.filter(ticket => ticket.status === 'IN_PROGRESS')).toHaveLength(28)
    expect(seedTickets.filter(ticket => ticket.status === 'WAITING')).toHaveLength(14)
    expect(seedTickets.filter(ticket => ticket.status === 'ESCALATED')).toHaveLength(14)
    expect(seedTickets.some(ticket => ticket.urgent)).toBe(true)
    expect(seedTickets.some(ticket => ticket.comments.length > 0)).toBe(true)
  })
})
