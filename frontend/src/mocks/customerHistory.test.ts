import { describe, expect, it } from 'vitest'
import { gateway } from './mockGateway'
import type { TicketDraft } from '../domain/types'

describe('customer history and resolution notes', () => {
  it('keeps each resolution after reopening and isolates history by phone', async () => {
    const draft: TicketDraft = { customerName: 'History customer', customerPhone: '98044331', deviceType: 'PHONE', manufacturer: 'Apple', deviceModel: 'iPhone', newDeviceModel: '', operatingSystem: 'IOS', category: 'E-post', description: 'Får ikke e-post.' }
    const first = await gateway.createTicket(draft, 1)
    await expect(gateway.setStatus(first.id, 'CLOSED', 1, first.version, '  ')).rejects.toThrow('avslutningsnotat')
    await expect(gateway.setStatus(first.id, 'CLOSED', 1, first.version, 'a'.repeat(4001))).rejects.toThrow()
    expect(await gateway.getTicket(first.id)).toEqual(first)
    const closed = await gateway.setStatus(first.id, 'CLOSED', 1, first.version, '  Oppdaterte kontoen.  ')
    const reopened = await gateway.setStatus(first.id, 'IN_PROGRESS', 1, closed.version)
    expect(reopened.resolutionNote).toBe('Oppdaterte kontoen.')
    await expect(gateway.setStatus(first.id, 'CLOSED', 1, closed.version, 'Stale note')).rejects.toThrow('endret')
    await expect(gateway.setStatus(first.id, 'CLOSED', 1, reopened.version)).rejects.toThrow('avslutningsnotat')
    const closedAgain = await gateway.setStatus(first.id, 'CLOSED', 1, reopened.version, 'Byttet innstillinger.')
    expect(closedAgain.history.filter(event => event.eventType === 'RESOLUTION').map(event => event.summary)).toEqual(['Oppdaterte kontoen.', 'Byttet innstillinger.'])
    const second = await gateway.createTicket({ ...draft, customerPhone: '+47 980 44 331', deviceModel: 'iPad' }, 1)
    await gateway.createTicket({ ...draft, customerPhone: '98044332', description: '98044331' }, 1)
    const history = await gateway.customerHistory('0047 980 44 331')
    expect(history.map(item => item.id)).toEqual([second.id, first.id])
    expect(history[1].resolutionNote).toBe('Byttet innstillinger.')
    expect(history[1].history).toEqual([])
    expect((await gateway.customerHistory('98044331', 1, 1)).map(item => item.id)).toEqual([first.id])
    expect((await gateway.customerHistory('98044331', 0, 10, second.id)).map(item => item.id)).toEqual([first.id])
    expect(await gateway.customerHistory('9804433')).toEqual([])
  })
})
