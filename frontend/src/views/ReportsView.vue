<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import DatePicker from '../components/DatePicker.vue'
import { categories } from '../data/categories'
import type { ReportSummary } from '../domain/types'
import { gateway } from '../gateway'
import { useAppStore } from '../stores/app'

const app = useAppStore(); const today = new Date(); const first = new Date(today.getFullYear(), today.getMonth(), 1)
const iso = (date: Date) => date.toISOString().slice(0, 10)
const filter = reactive({ from: iso(first), to: iso(today), employeeId: undefined as number | undefined, category: '' })
const summary = ref<ReportSummary | null>(null); const summaryLoading = ref(false); const downloadLoading = ref(false); const message = ref('')
const desktopAvailable = Boolean(window.desktop)
const asBase64 = (blob: Blob) => new Promise<string>((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(String(reader.result).split(',', 2)[1] ?? ''); reader.onerror = () => reject(reader.error); reader.readAsDataURL(blob) })
async function generate(withDelay = false) {
  if (summaryLoading.value) return
  summaryLoading.value = true; message.value = ''
  const reportFilter = { ...filter }
  try {
    if (withDelay) {
      const delayMs = 300 + Math.floor(Math.random() * 301)
      await new Promise<void>(resolve => window.setTimeout(resolve, delayMs))
    }
    summary.value = await gateway.reportSummary(reportFilter)
  }
  catch (cause) { message.value = cause instanceof Error ? cause.message : 'Kunne ikke lage rapporten.' }
  finally { summaryLoading.value = false }
}
async function downloadReport() {
  downloadLoading.value = true; message.value = ''
  try {
    const blob = await gateway.exportReport(filter)
    const fileName = `telefonhjelp-${filter.from}-${filter.to}.xlsx`
    if (window.desktop) {
      const savedTo = window.desktop.saveDownload(fileName, await asBase64(blob))
      message.value = `Rapporten er lagret i Nedlastinger: ${savedTo}`
    } else {
      const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = fileName; link.click(); URL.revokeObjectURL(url)
      message.value = 'Rapporten er lastet ned.'
    }
  } catch (cause) { message.value = cause instanceof Error ? cause.message : 'Kunne ikke laste ned rapporten.' }
  finally { downloadLoading.value = false }
}
async function backup() { try { const path = window.desktop?.chooseBackupPath(); if (window.desktop && !path) return; message.value = await gateway.backup(path) } catch (cause) { message.value = cause instanceof Error ? cause.message : 'Kunne ikke sikkerhetskopiere.' } }
async function restore() { try { const path = window.desktop?.chooseRestorePath(); if (!path || !window.confirm('Gjenoppretting erstatter alle vanlige data og sletter midlertidig informasjon. Fortsette?')) return; const result = await gateway.restore(path); message.value = result.restarting ? 'Gjenoppretter data og starter programmet på nytt …' : result.message } catch (cause) { message.value = cause instanceof Error ? cause.message : 'Kunne ikke gjenopprette sikkerhetskopien.' } }
onMounted(generate)
</script>
<template><section class="workspace-header"><div><p class="eyebrow">Innsikt</p><h1>Rapporter</h1><p>Se aktivitet og behandlingstid, og last ned en detaljert Excel-rapport uten kundeopplysninger.</p></div><div class="button-row"><button class="button button--ghost" @click="backup">Sikkerhetskopier data</button><button v-if="desktopAvailable" class="button button--ghost" @click="restore">Gjenopprett</button></div></section><section class="card report-filter"><DatePicker v-model="filter.from" label="Fra" :max="filter.to" /><DatePicker v-model="filter.to" label="Til" :min="filter.from" /><label>Ansatt<select v-model="filter.employeeId"><option :value="undefined">Alle</option><option v-for="employee in app.employees" :key="employee.id" :value="employee.id">{{ employee.name }}</option></select></label><label>Kategori<select v-model="filter.category"><option value="">Alle</option><option v-for="category in categories" :key="category">{{ category }}</option></select></label><button class="button button--primary" :disabled="summaryLoading" @click="generate(true)">{{ summaryLoading ? 'Beregner…' : 'Vis rapport' }}</button></section><div v-if="message" class="alert alert--info">{{ message }}</div><section v-if="summary" class="metric-grid"><article><small>Opprettet</small><strong>{{ summary.created }}</strong></article><article><small>Lukket</small><strong>{{ summary.closed }}</strong></article><article><small>Åpne</small><strong>{{ summary.open }}</strong></article><article><small>Gj.snitt</small><strong>{{ summary.averageMinutes }} min</strong></article><article><small>Innen 30 min</small><strong>{{ summary.within30Percent }} %</strong></article><article><small>Innen 60 min</small><strong>{{ summary.within60Percent }} %</strong></article><article><small>Over 60 min</small><strong>{{ summary.over60Percent }} %</strong></article><article><small>Haster / eskalert</small><strong>{{ summary.urgent }} / {{ summary.escalated }}</strong></article></section><div class="report-actions"><button class="button button--dark button--large" :disabled="downloadLoading" @click="downloadReport">{{ downloadLoading ? 'Laster ned…' : 'Last ned Excel-rapport' }}</button><small>Navn, telefonnumre, beskrivelser, kommentarer og midlertidig informasjon tas aldri med.</small></div></template>
