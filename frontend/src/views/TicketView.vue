<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AgeIndicator from '../components/AgeIndicator.vue'
import DeviceAutocomplete from '../components/DeviceAutocomplete.vue'
import { categories } from '../data/categories'
import { dateTime, formatPhone, timeOnly } from '../domain/format'
import { statusLabels, type OperatingSystem, type TemporaryCredential, type Ticket, type TicketStatus } from '../domain/types'
import { gateway } from '../gateway'
import { useAppStore } from '../stores/app'

const route = useRoute(); const router = useRouter(); const app = useAppStore()
const item = ref<Ticket | null>(null); const loading = ref(true); const busy = ref(false); const error = ref(''); const comment = ref(''); const editing = ref(false); const otherModel = ref(false)
const edit = reactive({ customerName: '', customerPhone: '', deviceModel: '', manufacturer: '', operatingSystem: 'OTHER' as OperatingSystem, category: '', description: '' })
const credentials = ref<TemporaryCredential[]>([])
const credentialForm = reactive<Record<string, string>>({ account: '', code: '', simPin: '', temporaryPassword: '' })
const credentialExists = (key: string) => credentials.value.some(value => value.key === key)
const accountLabel = computed(() => item.value?.operatingSystem === 'IOS' ? 'Apple-konto' : item.value?.operatingSystem === 'ANDROID' ? 'Google-konto' : 'Konto')
const codeLabel = computed(() => item.value?.operatingSystem === 'IOS' || item.value?.operatingSystem === 'ANDROID' ? 'Skjermkode' : 'Enhetskode')
const active = computed(() => item.value?.status !== 'CLOSED')

function apply(ticket: Ticket) { item.value = ticket; Object.assign(edit, { customerName: ticket.customerName, customerPhone: ticket.customerPhone, deviceModel: ticket.deviceModel, manufacturer: ticket.manufacturer, operatingSystem: ticket.operatingSystem, category: ticket.category, description: ticket.description }) }
async function load() { loading.value = true; try { apply(await gateway.getTicket(Number(route.params.id))); credentials.value = await gateway.getTemporaryInfo(Number(route.params.id)); credentials.value.forEach(value => credentialForm[value.key] = value.value) } catch (cause) { error.value = message(cause) } finally { loading.value = false } }
const message = (cause: unknown) => cause instanceof Error ? cause.message : 'Noe gikk galt.'
async function action(operation: (ticket: Ticket) => Promise<Ticket>) { if (!item.value) return; busy.value = true; error.value = ''; try { apply(await operation(item.value)) } catch (cause) { error.value = message(cause) } finally { busy.value = false } }
async function setStatus(status: TicketStatus) { if (!item.value || !app.currentEmployeeId) return; await action(ticket => gateway.setStatus(ticket.id, status, app.currentEmployeeId!, ticket.version)) }
async function assign(employeeId: number) { if (!item.value || !app.currentEmployeeId) return; await action(ticket => gateway.assign(ticket.id, employeeId, app.currentEmployeeId!, ticket.version)) }
async function toggleUrgent() { if (!item.value || !app.currentEmployeeId) return; await action(ticket => gateway.setUrgent(ticket.id, !ticket.urgent, app.currentEmployeeId!, ticket.version)) }
async function addComment() { if (!item.value || !app.currentEmployeeId || !comment.value.trim()) return; const text = comment.value; comment.value = ''; await action(ticket => gateway.addComment(ticket.id, text, app.currentEmployeeId!, ticket.version)) }
async function saveEdit() { if (!item.value || !app.currentEmployeeId) return; await action(ticket => gateway.updateTicket(ticket.id, { ...edit, deviceType: ticket.deviceType, version: ticket.version }, app.currentEmployeeId!)); editing.value = false }
async function saveCredentials() { if (!item.value) return; const existing = new Map(credentials.value.map(value => [value.key, value.value])); const changed = [{ key: 'account', label: accountLabel.value, value: credentialForm.account }, { key: 'code', label: codeLabel.value, value: credentialForm.code }, { key: 'simPin', label: 'SIM-PIN', value: credentialForm.simPin }, { key: 'temporaryPassword', label: 'Midlertidig passord', value: credentialForm.temporaryPassword }].filter(value => existing.has(value.key) ? existing.get(value.key) !== value.value.trim() : Boolean(value.value.trim())); if (!changed.length) return; try { credentials.value = await gateway.saveTemporaryInfo(item.value.id, changed) } catch (cause) { error.value = message(cause) } }
async function clearCredential(key: string) { if (!item.value) return; try { await gateway.clearTemporaryInfo(item.value.id, key); credentialForm[key] = ''; credentials.value = credentials.value.filter(value => value.key !== key) } catch (cause) { error.value = message(cause) } }
async function clearCredentials() { if (!item.value) return; await gateway.clearTemporaryInfo(item.value.id); Object.keys(credentialForm).forEach(key => credentialForm[key] = ''); credentials.value = [] }
onMounted(load)
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
        <section class="card section-card">
          <div class="section-heading"><h2>Saksinformasjon</h2><button v-if="active" class="text-button" @click="editing = !editing">{{ editing ? 'Avbryt' : 'Rediger' }}</button></div>
          <form v-if="editing" class="edit-form" @submit.prevent="saveEdit"><div class="two-columns"><label>Navn<input v-model="edit.customerName" required /></label><label>Telefon<input v-model="edit.customerPhone" required /></label></div><DeviceAutocomplete v-model="edit.deviceModel" v-model:other="otherModel" :type="item.deviceType" @select="value => Object.assign(edit, { deviceModel: value.model, manufacturer: value.manufacturer, operatingSystem: value.operatingSystem })" /><label>Kategori<select v-model="edit.category"><option v-for="category in categories" :key="category">{{ category }}</option></select></label><label>Problem<textarea v-model="edit.description" rows="3" required></textarea></label><button class="button button--primary" :disabled="busy">Lagre endringer</button></form>
          <dl v-else class="facts"><div><dt>Kategori</dt><dd>{{ item.category }}</dd></div><div><dt>Problem</dt><dd>{{ item.description }}</dd></div><div><dt>Opprettet</dt><dd>{{ dateTime.format(new Date(item.createdAt)) }} av {{ item.createdByName }}</dd></div></dl>
        </section>
        <section class="card section-card comments"><h2>Kommentarer</h2><div v-if="!item.comments.length" class="muted">Ingen kommentarer ennå.</div><article v-for="entry in item.comments" :key="entry.id"><header><strong>{{ entry.employeeName }}</strong><time>{{ dateTime.format(new Date(entry.createdAt)) }}</time></header><p>{{ entry.text }}</p></article><form v-if="active" class="comment-form" @submit.prevent="addComment"><label><span class="sr-only">Ny kommentar</span><textarea v-model="comment" rows="2" placeholder="Legg til informasjon…" required></textarea></label><button class="button button--primary" :disabled="busy || !comment.trim()">Legg til kommentar</button></form></section>
        <section class="card section-card"><h2>Historikk</h2><ol class="timeline"><li v-for="event in [...item.history].reverse()" :key="event.id"><time>{{ timeOnly.format(new Date(event.createdAt)) }}</time><span></span><div><strong>{{ event.summary }}</strong><small>{{ event.actorName }} · {{ dateTime.format(new Date(event.createdAt)) }}</small></div></li></ol></section>
      </div>
      <aside class="detail-side">
        <section class="card section-card"><h2>Behandling</h2><label>Tildelt til<select :value="item.assignedToId" :disabled="busy || !active" @change="assign(Number(($event.target as HTMLSelectElement).value))"><option v-for="employee in app.activeEmployees" :key="employee.id" :value="employee.id">{{ employee.name }}</option></select></label><label>Status<select :value="item.status" :disabled="busy || !active" @change="setStatus(($event.target as HTMLSelectElement).value as TicketStatus)"><option v-for="(label, status) in statusLabels" :key="status" :value="status" :disabled="status === 'CLOSED'">{{ label }}</option></select></label><button v-if="active" class="button button--danger button--block" :disabled="busy" @click="setStatus('CLOSED')">Lukk saken</button><button v-else class="button button--primary button--block" :disabled="busy" @click="setStatus('IN_PROGRESS')">Åpne saken igjen</button></section>
        <section class="card section-card sensitive"><div class="section-heading"><div><p class="eyebrow">Midlertidig</p><h2>Sensitiv informasjon</h2></div><span>ⓘ</span></div><p class="sensitive-note">Hvert felt slettes 24 timer etter siste lagring. Ikke skriv dette i kommentarer.</p><label><span class="sensitive-label">{{ accountLabel }}<button v-if="credentialExists('account')" class="text-button danger" @click.prevent="clearCredential('account')">Slett</button></span><input v-model="credentialForm.account" :disabled="!active" /></label><label><span class="sensitive-label">{{ codeLabel }}<button v-if="credentialExists('code')" class="text-button danger" @click.prevent="clearCredential('code')">Slett</button></span><input v-model="credentialForm.code" :disabled="!active" /></label><label><span class="sensitive-label">SIM-PIN<button v-if="credentialExists('simPin')" class="text-button danger" @click.prevent="clearCredential('simPin')">Slett</button></span><input v-model="credentialForm.simPin" :disabled="!active" /></label><label><span class="sensitive-label">Midlertidig passord<button v-if="credentialExists('temporaryPassword')" class="text-button danger" @click.prevent="clearCredential('temporaryPassword')">Slett</button></span><input v-model="credentialForm.temporaryPassword" :disabled="!active" /></label><div class="sensitive-actions"><button v-if="active" class="button button--dark" @click="saveCredentials">Lagre midlertidig</button><button v-if="credentials.length" class="text-button danger" @click="clearCredentials">Slett alt</button></div></section>
      </aside>
    </div>
  </template>
</template>
