<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AgeIndicator from '../components/AgeIndicator.vue'
import DeviceAutocomplete from '../components/DeviceAutocomplete.vue'
import CustomerHistory from '../components/CustomerHistory.vue'
import { categories, TRANSFER_CATEGORY } from '../data/categories'
import { dateTime, formatPhone, timeOnly } from '../domain/format'
import { statusLabels, type OperatingSystem, type Ticket, type TicketStatus } from '../domain/types'
import { gateway } from '../gateway'
import { useAppStore } from '../stores/app'
import { SecretExposure } from '../domain/secretExposure'

const route = useRoute(); const app = useAppStore()
const item = ref<Ticket | null>(null); const loading = ref(true); const busy = ref(false); const error = ref(''); const comment = ref(''); const editing = ref(false); const otherModel = ref(false)
const edit = reactive({ customerName: '', customerPhone: '', deviceModel: '', newDeviceModel: '', manufacturer: '', operatingSystem: 'OTHER' as OperatingSystem, category: '', description: '' })
const exposure = reactive(new SecretExposure())
const credentials = computed(() => exposure.credentials)
const credentialForm = exposure.values
const credentialExists = (key: string) => credentials.value.some(value => value.key === key)
const accountLabel = computed(() => item.value?.operatingSystem === 'IOS' ? 'Apple-konto' : item.value?.operatingSystem === 'ANDROID' ? 'Google-konto' : 'Konto')
const codeLabel = computed(() => item.value?.operatingSystem === 'IOS' || item.value?.operatingSystem === 'ANDROID' ? 'Skjermkode' : 'Enhetskode')
const active = computed(() => item.value?.status !== 'CLOSED')
const closing = ref(false)
const resolutionNote = ref('')

function apply(ticket: Ticket) { item.value = ticket; Object.assign(edit, { customerName: ticket.customerName, customerPhone: ticket.customerPhone, deviceModel: ticket.deviceModel, newDeviceModel: ticket.newDeviceModel, manufacturer: ticket.manufacturer, operatingSystem: ticket.operatingSystem, category: ticket.category, description: ticket.description }) }
let ticketLoad = 0
async function load() { const generation = ++ticketLoad; exposure.clear(); loading.value = true; try { const ticket = await gateway.getTicket(Number(route.params.id)); if (generation === ticketLoad) apply(ticket) } catch (cause) { if (generation === ticketLoad) error.value = message(cause) } finally { if (generation === ticketLoad) loading.value = false } }
async function revealCredentials() {
  if (!item.value || exposure.busy) return
  const generation = exposure.begin()
  try { exposure.accept(generation, await gateway.getTemporaryInfo(item.value.id)) }
  catch (cause) { exposure.failed(generation); error.value = message(cause) }
}
const message = (cause: unknown) => cause instanceof Error ? cause.message : 'Noe gikk galt.'
async function action(operation: (ticket: Ticket) => Promise<Ticket>) { if (!item.value) return; busy.value = true; error.value = ''; try { apply(await operation(item.value)) } catch (cause) { error.value = message(cause) } finally { busy.value = false } }
async function setStatus(status: TicketStatus) { if (!item.value || !app.currentEmployeeId) return; await action(ticket => gateway.setStatus(ticket.id, status, app.currentEmployeeId!, ticket.version)) }
async function closeTicket() {
  if (!item.value || !app.currentEmployeeId || busy.value || !resolutionNote.value.trim()) return
  await action(ticket => gateway.setStatus(ticket.id, 'CLOSED', app.currentEmployeeId!, ticket.version, resolutionNote.value))
  if (item.value.status === 'CLOSED') { closing.value = false; resolutionNote.value = '' }
}
async function assign(employeeId: number) { if (!item.value || !app.currentEmployeeId) return; await action(ticket => gateway.assign(ticket.id, employeeId, app.currentEmployeeId!, ticket.version)) }
async function toggleUrgent() { if (!item.value || !app.currentEmployeeId) return; await action(ticket => gateway.setUrgent(ticket.id, !ticket.urgent, app.currentEmployeeId!, ticket.version)) }
async function addComment() { if (!item.value || !app.currentEmployeeId || !comment.value.trim()) return; const text = comment.value; comment.value = ''; await action(ticket => gateway.addComment(ticket.id, text, app.currentEmployeeId!, ticket.version)) }
async function saveEdit() { if (!item.value || !app.currentEmployeeId) return; await action(ticket => gateway.updateTicket(ticket.id, { ...edit, deviceType: ticket.deviceType, version: ticket.version }, app.currentEmployeeId!)); editing.value = false }
async function saveCredentials() {
  exposure.expire()
  if (!item.value || !exposure.loaded || exposure.busy) return
  const existing = new Map(credentials.value.map(value => [value.key, value.value]))
  const changed = [{ key: 'account', label: accountLabel.value, value: credentialForm.account }, { key: 'code', label: codeLabel.value, value: credentialForm.code }, { key: 'simPin', label: 'SIM-PIN', value: credentialForm.simPin }, { key: 'temporaryPassword', label: 'Midlertidig passord', value: credentialForm.temporaryPassword }].filter(value => existing.has(value.key) ? existing.get(value.key) !== value.value.trim() : Boolean(value.value.trim()))
  if (!changed.length) return
  const generation = exposure.begin()
  try { exposure.accept(generation, await gateway.saveTemporaryInfo(item.value.id, changed)) }
  catch (cause) { exposure.failed(generation); error.value = message(cause) }
}
async function clearCredential(key?: string) {
  if (!item.value) return
  exposure.clear()
  try { await gateway.clearTemporaryInfo(item.value.id, key) } catch (cause) { error.value = message(cause) }
}
const clearCredentials = () => clearCredential()
const clearExposure = () => exposure.clear()
const checkExposure = () => exposure.expire()
const recordActivity = () => exposure.activity()
const onVisibility = () => document.hidden ? exposure.clear() : exposure.expire()
let secretTimer: number
watch(() => app.currentEmployeeId, clearExposure, { flush: 'sync' })
watch(() => route.params.id, load)
watch(() => route.params.id, () => { closing.value = false; resolutionNote.value = ''; editing.value = false })
onMounted(() => {
  void load()
  secretTimer = window.setInterval(checkExposure, 1000)
  window.addEventListener('focus', checkExposure)
  window.addEventListener('blur', clearExposure)
  window.addEventListener('session-ended', clearExposure)
  window.addEventListener('pointerdown', recordActivity)
  window.addEventListener('keydown', recordActivity)
  document.addEventListener('visibilitychange', onVisibility)
})
onBeforeUnmount(() => {
  ticketLoad++; exposure.clear(); window.clearInterval(secretTimer)
  window.removeEventListener('focus', checkExposure)
  window.removeEventListener('blur', clearExposure)
  window.removeEventListener('session-ended', clearExposure)
  window.removeEventListener('pointerdown', recordActivity)
  window.removeEventListener('keydown', recordActivity)
  document.removeEventListener('visibilitychange', onVisibility)
})
</script>

<template>
  <RouterLink class="back-link" to="/">← Aktive saker</RouterLink>
  <div v-if="loading" class="loading">Henter saken…</div><div v-else-if="error && !item" class="alert alert--error">{{ error }}</div>
  <template v-else-if="item">
    <section class="ticket-hero card" :class="{ urgent: item.urgent }">
      <div class="ticket-title"><AgeIndicator v-if="active" :ticket="item" :now="new Date()" /><div><p class="eyebrow">Sak #{{ item.id }}</p><h1>{{ item.customerName }}</h1><p>{{ formatPhone(item.customerPhoneNormalized) }} · {{ item.deviceModel }}</p></div></div>
      <button v-if="active" :class="['urgent-toggle', { active: item.urgent }]" :disabled="busy" @click="toggleUrgent"><span>●</span> {{ item.urgent ? 'Haster' : 'Marker som haster' }}</button>
    </section>
    <div v-if="error" class="alert alert--error">{{ error }}</div>
    <div class="detail-grid">
      <div class="detail-main">
        <section class="card section-card"><h2>Kundehistorikk</h2><CustomerHistory :phone="item.customerPhoneNormalized" :exclude-ticket-id="item.id" /></section>
        <section class="card section-card">
          <div class="section-heading"><h2>Saksinformasjon</h2><button v-if="active" class="text-button" @click="editing = !editing">{{ editing ? 'Avbryt' : 'Rediger' }}</button></div>
          <form v-if="editing" class="edit-form" @submit.prevent="saveEdit"><div class="two-columns"><label>Navn<input v-model="edit.customerName" required /></label><label>Telefon<input v-model="edit.customerPhone" required /></label></div><DeviceAutocomplete v-model="edit.deviceModel" v-model:other="otherModel" :type="item.deviceType" @select="value => Object.assign(edit, { deviceModel: value.model, manufacturer: value.manufacturer, operatingSystem: value.operatingSystem })" /><label>Kategori<select v-model="edit.category"><option v-for="category in categories" :key="category">{{ category }}</option></select></label><label v-if="edit.category === TRANSFER_CATEGORY">Enhetsmodell (ny enhet)<input v-model="edit.newDeviceModel" placeholder="F.eks. iPhone 16 Pro" /></label><label>Problem<textarea v-model="edit.description" rows="3" required></textarea></label><button class="button button--primary" :disabled="busy">Lagre endringer</button></form>
          <dl v-else class="facts"><div><dt>Kategori</dt><dd>{{ item.category }}</dd></div><div v-if="item.newDeviceModel"><dt>Ny enhet</dt><dd>{{ item.newDeviceModel }}</dd></div><div><dt>Problem</dt><dd>{{ item.description }}</dd></div><div><dt>Opprettet</dt><dd>{{ dateTime.format(new Date(item.createdAt)) }} av {{ item.createdByName }}</dd></div></dl>
        </section>
        <section v-if="item.resolutionNote || !active" class="card section-card"><h2>{{ active ? 'Siste avslutningsnotat' : 'Avslutningsnotat' }}</h2><p class="note-text">{{ item.resolutionNote || 'Ingen avslutningsnotat registrert.' }}</p></section>
        <section class="card section-card comments"><h2>Kommentarer</h2><div v-if="!item.comments.length" class="muted">Ingen kommentarer ennå.</div><article v-for="entry in item.comments" :key="entry.id"><header><strong>{{ entry.employeeName }}</strong><time>{{ dateTime.format(new Date(entry.createdAt)) }}</time></header><p>{{ entry.text }}</p></article><form v-if="active" class="comment-form" @submit.prevent="addComment"><label><span class="sr-only">Ny kommentar</span><textarea v-model="comment" rows="2" placeholder="Legg til informasjon…" required></textarea></label><button class="button button--primary" :disabled="busy || !comment.trim()">Legg til kommentar</button></form></section>
        <section class="card section-card"><h2>Historikk</h2><ol class="timeline"><li v-for="event in [...item.history].reverse()" :key="event.id"><time>{{ timeOnly.format(new Date(event.createdAt)) }}</time><span></span><div><strong class="note-text">{{ event.eventType === 'RESOLUTION' ? 'Avslutningsnotat: ' : '' }}{{ event.summary }}</strong><small>{{ event.actorName }} · {{ dateTime.format(new Date(event.createdAt)) }}</small></div></li></ol></section>
      </div>
      <aside class="detail-side">
        <section class="card section-card">
          <h2>Behandling</h2>
          <label>Tildelt til<select :value="item.assignedToId" :disabled="busy || !active" @change="assign(Number(($event.target as HTMLSelectElement).value))"><option v-for="employee in app.activeEmployees" :key="employee.id" :value="employee.id">{{ employee.name }}</option></select></label>
          <label>Status<select :value="item.status" :disabled="busy || !active" @change="setStatus(($event.target as HTMLSelectElement).value as TicketStatus)"><option v-for="(label, status) in statusLabels" :key="status" :value="status" :disabled="status === 'CLOSED'">{{ label }}</option></select></label>
          <template v-if="active">
            <button v-if="!closing" class="button button--danger button--block" :disabled="busy" @click="closing = true">Lukk saken</button>
            <form v-else class="resolution-form" @submit.prevent="closeTicket">
              <label>Avslutningsnotat<textarea v-model="resolutionNote" rows="4" maxlength="4000" required :disabled="busy" placeholder="Hva ble gjort, og hva ble resultatet?" /></label>
              <small>Beskriv løsningen eller hvorfor saken avsluttes. Ikke ta med passord eller koder.</small>
              <button class="button button--danger button--block" :disabled="busy || !resolutionNote.trim()">{{ busy ? 'Lagrer…' : 'Lagre og lukk saken' }}</button>
              <button type="button" class="text-button" :disabled="busy" @click="closing = false">Avbryt</button>
            </form>
          </template>
          <button v-else class="button button--primary button--block" :disabled="busy" @click="setStatus('IN_PROGRESS')">Åpne saken igjen</button>
        </section>
        <section class="card section-card sensitive">
          <div class="section-heading"><div><p class="eyebrow">Midlertidig</p><h2>Sensitiv informasjon</h2></div><span>ⓘ</span></div>
          <p class="sensitive-note">Hvert felt utløper 24 timer etter siste lagring. Ikke skriv dette i kommentarer.</p>
          <button v-if="!exposure.loaded" class="button button--dark" :disabled="exposure.busy" @click="revealCredentials">Åpne midlertidige felt</button>
          <template v-else>
            <div class="sensitive-actions"><button class="text-button" @click="exposure.showValues = !exposure.showValues">{{ exposure.showValues ? 'Masker verdier' : 'Vis verdier' }}</button><button class="text-button" @click="clearExposure">Skjul og tøm feltene</button></div>
            <label v-for="field in [{ key: 'account', label: accountLabel }, { key: 'code', label: codeLabel }, { key: 'simPin', label: 'SIM-PIN' }, { key: 'temporaryPassword', label: 'Midlertidig passord' }]" :key="field.key">
              <span class="sensitive-label">{{ field.label }}<button v-if="credentialExists(field.key)" class="text-button danger" :disabled="exposure.busy" @click.prevent="clearCredential(field.key)">Slett</button></span>
              <input v-model="credentialForm[field.key]" :type="exposure.showValues ? 'text' : 'password'" autocomplete="off" autocapitalize="off" :spellcheck="false" maxlength="1024" :disabled="!active || exposure.busy" />
            </label>
            <div class="sensitive-actions"><button v-if="active" class="button button--dark" :disabled="exposure.busy" @click="saveCredentials">Lagre midlertidig</button><button v-if="credentials.length" class="text-button danger" :disabled="exposure.busy" @click="clearCredentials">Slett alt</button></div>
          </template>
        </section>
      </aside>
    </div>
  </template>
</template>
