import { describe, expect, it } from 'vitest'
import { searchDevices } from './deviceSearch'

describe('device aliases', () => {
  it('finds iPhone models through ip', () => expect(searchDevices('ip 15 pro', 'PHONE').map(x => x.model)).toContain('iPhone 15 Pro'))
  it('finds Samsung models through sm', () => expect(searchDevices('sm s24', 'PHONE').map(x => x.model)).toContain('Samsung Galaxy S24'))
  it('does not suggest phone models for computers', () => expect(searchDevices('ip', 'COMPUTER')).toEqual([]))
})
