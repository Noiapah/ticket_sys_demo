<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AgeIndicator from '../components/AgeIndicator.vue'
import EmptyState from '../components/EmptyState.vue'
import { gateway } from '../gateway'
import { statusLabels, type Ticket } from '../domain/types'
import { formatPhone } from '../domain/format'

const router = useRouter()
const tab = ref<'active' | 'closed'>('active')
const query = ref('')
const tickets = ref<Ticket[]>([])
const loading = ref(true)
const error = ref('')
const now = ref(new Date())
let timer: number
let searchTimer: number

const sorted = computed(() => [...tickets.value].sort((a, b) => Number(b.urgent) - Number(a.urgent) || new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()))

async function load() {
  loading.value = true; error.value = ''
  try { tickets.value = await gateway.listTickets({ scope: tab.value, query: query.value }) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'Kunne ikke hente saker.' }
  finally { loading.value = false }
}

watch(tab, load)
watch(query, () => { window.clearTimeout(searchTimer); searchTimer = window.setTimeout(load, 180) })
onMounted(() => { load(); timer = window.setInterval(() => now.value = new Date(), 60_000) })
onBeforeUnmount(() => { window.clearInterval(timer); window.clearTimeout(searchTimer) })
</script>

<template>
  <section class="workspace-header">
    <div><p class="eyebrow">Arbeidskø</p><h1>{{ tab === 'active' ? 'Aktive saker' : 'Tidligere saker' }}</h1><p>Få oversikt, finn kunden og fortsett der dere slapp.</p></div>
    <RouterLink class="button button--primary button--large" to="/ny"><span>+</span> Ny sak</RouterLink>
  </section>

  <section class="toolbar card">
    <div class="tabs" role="tablist">
      <button :class="{ active: tab === 'active' }" @click="tab = 'active'">Aktive saker</button>
      <button :class="{ active: tab === 'closed' }" @click="tab = 'closed'">Tidligere saker</button>
    </div>
    <label class="search"><span aria-hidden="true">⌕</span><input v-model="query" placeholder="Søk på telefon, navn, saksnummer, enhet eller problem…" aria-label="Søk i saker" /></label>
  </section>

  <div v-if="error" class="alert alert--error">{{ error }}</div>
  <div v-if="loading" class="loading">Henter saker…</div>
  <EmptyState v-else-if="!sorted.length" :title="query ? 'Ingen treff' : tab === 'active' ? 'Køen er tom' : 'Ingen tidligere saker'" :text="query ? 'Prøv et annet søkeord.' : 'Nye supportsaker vises her.'" />
  <div v-else class="ticket-table card">
    <table>
      <thead><tr><th>Alder</th><th>Kunde</th><th>Enhet</th><th>Kategori</th><th>Tildelt</th><th>Status</th><th><span class="sr-only">Åpne</span></th></tr></thead>
      <tbody>
        <tr v-for="ticket in sorted" :key="ticket.id" :class="{ urgent: ticket.urgent }" @dblclick="router.push(`/sak/${ticket.id}`)">
          <td><AgeIndicator v-if="ticket.status !== 'CLOSED'" :ticket="ticket" :now="now" /><span v-else class="muted">—</span></td>
          <td><strong>{{ ticket.customerName }}</strong><small>{{ formatPhone(ticket.customerPhoneNormalized) }}</small></td>
          <td><strong>{{ ticket.deviceModel }}</strong><small>Sak #{{ ticket.id }}</small></td>
          <td>{{ ticket.category }}</td><td>{{ ticket.assignedToName }}</td><td><span :class="['status', `status--${ticket.status.toLowerCase()}`]">{{ statusLabels[ticket.status] }}</span></td>
          <td><RouterLink class="icon-button" :to="`/sak/${ticket.id}`" :aria-label="`Åpne sak ${ticket.id}`">→</RouterLink></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

