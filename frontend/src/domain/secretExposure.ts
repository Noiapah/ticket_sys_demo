import type { TemporaryCredential } from './types'

export class SecretExposure {
  readonly values: Record<string, string> = { account: '', code: '', simPin: '', temporaryPassword: '' }
  credentials: TemporaryCredential[] = []
  loaded = false
  showValues = false
  busy = false
  private generation = 0
  private lastActivity = Date.now()

  begin(): number { this.activity(); this.busy = true; return ++this.generation }
  activity(): void { this.expire(); this.lastActivity = Date.now() }
  accept(generation: number, values: TemporaryCredential[]): void {
    this.expire()
    if (generation !== this.generation) return
    this.clearValues()
    this.credentials = values.filter(value => Object.prototype.hasOwnProperty.call(this.values, value.key) && Date.parse(value.expiresAt) > Date.now())
    this.credentials.forEach(value => this.values[value.key] = value.value)
    this.loaded = true
    this.busy = false
  }
  failed(generation: number): void { if (generation === this.generation) this.busy = false }
  clear(): void {
    this.generation++
    this.clearValues()
    this.credentials = []
    this.loaded = false
    this.showValues = false
    this.busy = false
  }
  expire(): void {
    if (Date.now() - this.lastActivity >= 5 * 60_000) { this.clear(); return }
    this.credentials = this.credentials.filter(value => {
      if (Date.parse(value.expiresAt) > Date.now()) return true
      this.values[value.key] = ''
      return false
    })
  }
  private clearValues(): void { Object.keys(this.values).forEach(key => this.values[key] = '') }
}
