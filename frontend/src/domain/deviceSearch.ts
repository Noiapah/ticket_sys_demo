import Fuse from 'fuse.js'
import { devices } from '../data/devices'
import { normalizeText } from './format'
import type { Device, DeviceType } from './types'

const expand = (query: string) => normalizeText(query).split(' ').map(token => token === 'ip' ? 'iphone' : token === 'sm' ? 'samsung' : token).join(' ')
const fuse = new Fuse(devices, { keys: ['model', 'manufacturer', 'family', 'aliases'], threshold: 0.34, ignoreLocation: true })

export function searchDevices(query: string, type: DeviceType, limit = 8): Device[] {
  if (type !== 'PHONE' || !query.trim()) return []
  const needle = expand(query)
  const ranked = devices
    .filter(device => device.type === type)
    .map(device => {
      const fields = [device.model, device.manufacturer, device.family, ...device.aliases].map(expand)
      const exact = fields.some(field => field === needle)
      const prefix = fields.some(field => field.startsWith(needle) || needle.split(' ').every(token => field.includes(token)))
      return { device, score: exact ? 0 : prefix ? 1 : 9 }
    })
    .filter(item => item.score < 9)
    .sort((a, b) => a.score - b.score || a.device.model.length - b.device.model.length)
    .map(item => item.device)
  const seen = new Set(ranked.map(device => device.model))
  const fuzzy = fuse.search(needle).map(result => result.item).filter(device => device.type === type && !seen.has(device.model))
  return [...ranked, ...fuzzy].slice(0, limit)
}

