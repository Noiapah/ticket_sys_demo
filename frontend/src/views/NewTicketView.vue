<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import DeviceAutocomplete from '../components/DeviceAutocomplete.vue'
import { categories } from '../data/categories'
import { deviceTypeLabels, type CustomerMatch, type DeviceType, type OperatingSystem, type TicketDraft } from '../domain/types'
import { normalizePhone } from '../domain/format'
import { gateway } from '../gateway'
import { useAppStore } from '../stores/app'

const router = useRouter()
const app = useAppStore()
const otherModel = ref(false)
const submitting = ref(false)
const error = ref('')
const customerMatch = ref<CustomerMatch | null>(null)
let customerTimer: number
const draft = reactive<TicketDraft>({ customerName: '', customerPhone: '', deviceType: 'PHONE', manufacturer: '', deviceModel: '', operatingSystem: 'OTHER', category: categories[0], description: '' })
const phoneWarning = computed(() => draft.customerPhone && !normalizePhone(draft.customerPhone).valid ? 'Nummeret lagres, men kunne ikke gjenkjennes som et gyldig telefonnummer.' : '')
const deviceTypes: DeviceType[] = ['PHONE', 'TABLET', 'SMARTWATCH', 'COMPUTER', 'OTHER']

watch(() => draft.customerPhone, () => {
  window.clearTimeout(customerTimer); customerMatch.value = null
  if (draft.customerPhone.replace(/\D/g, '').length < 7) return
  customerTimer = window.setTimeout(async () => {
    try { customerMatch.value = await gateway.matchCustomer(draft.customerPhone); if (customerMatch.value && !draft.customerName.trim()) draft.customerName = customerMatch.value.name }
    catch { customerMatch.value = null }
  }, 250)
})
onBeforeUnmount(() => window.clearTimeout(customerTimer))

function selectType(type: DeviceType) {
  draft.deviceType = type; draft.deviceModel = ''; draft.manufacturer = ''; draft.operatingSystem = 'OTHER'; otherModel.value = type !== 'PHONE'
}

function selectDevice(value: { model: string; manufacturer: string; operatingSystem: OperatingSystem }) { Object.assign(draft, { deviceModel: value.model, manufacturer: value.manufacturer, operatingSystem: value.operatingSystem }) }

async function submit() {
  if (!app.currentEmployeeId) return
  submitting.value = true; error.value = ''
  try { const ticket = await gateway.createTicket({ ...draft }, app.currentEmployeeId); await router.push(`/sak/${ticket.id}`) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'Kunne ikke opprette saken.' }
  finally { submitting.value = false }
}
</script>

<template>
  <RouterLink class="back-link" to="/">← Aktive saker</RouterLink>
  <section class="form-card card">
    <div class="form-heading"><div><p class="eyebrow">Rask registrering</p><h1>Ny supportsak</h1><p>Det viktigste først. Detaljer kan legges til senere.</p></div><span class="employee-pill">Opprettes av {{ app.currentEmployee?.name }}</span></div>
    <form @submit.prevent="submit">
      <fieldset><legend>1. Enhetstype</legend><div class="choice-grid"><button v-for="type in deviceTypes" :key="type" type="button" :class="['choice', { active: draft.deviceType === type }]" @click="selectType(type)">{{ deviceTypeLabels[type] }}</button></div></fieldset>
      <fieldset><legend>2. Enhet</legend><DeviceAutocomplete v-model="draft.deviceModel" v-model:other="otherModel" :type="draft.deviceType" @select="selectDevice" /></fieldset>
      <fieldset><legend>3. Kunde</legend><div class="two-columns"><label>Telefonnummer<input v-model="draft.customerPhone" inputmode="tel" autocomplete="tel" placeholder="991 23 456" required /><small v-if="phoneWarning" class="warning-text">{{ phoneWarning }}</small></label><label>Navn<input v-model="draft.customerName" autocomplete="name" placeholder="Kundens navn" required /></label></div><div v-if="customerMatch" class="alert alert--info">Kjent kunde: {{ customerMatch.name }} · {{ customerMatch.previousTickets }} tidligere {{ customerMatch.previousTickets === 1 ? 'sak' : 'saker' }}</div></fieldset>
      <fieldset><legend>4. Problem</legend><label>Kategori<select v-model="draft.category"><option v-for="category in categories" :key="category">{{ category }}</option></select></label><label>Kort beskrivelse<textarea v-model="draft.description" rows="3" placeholder="F.eks. kommer ikke inn på Apple-konto" maxlength="500" required></textarea><small>{{ draft.description.length }}/500</small></label></fieldset>
      <div v-if="error" class="alert alert--error">{{ error }}</div>
      <div class="form-actions"><RouterLink class="button button--ghost" to="/">Avbryt</RouterLink><button class="button button--primary button--large" :disabled="submitting">{{ submitting ? 'Oppretter…' : 'Opprett sak' }}</button></div>
    </form>
  </section>
</template>
