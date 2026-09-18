<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { gateway } from '../gateway'
import { dateTime } from '../domain/format'
import { statusLabels, type Ticket } from '../domain/types'

const props = defineProps<{ phone: string; excludeTicketId?: number; label?: string }>()
const expanded = ref(false)
const loading = ref(false)
const error = ref('')
const tickets = ref<Ticket[]>([])
const page = ref(0)
const pageSize = 10
let generation = 0

async function load() {
  const request = ++generation
  loading.value = true; error.value = ''; tickets.value = []
  try {
    const result = await gateway.customerHistory(props.phone, page.value, pageSize, props.excludeTicketId)
    if (request === generation) tickets.value = result
  } catch (cause) {
    if (request === generation) error.value = cause instanceof Error ? cause.message : 'Kunne ikke hente kundehistorikken.'
  } finally { if (request === generation) loading.value = false }
}

function toggle() {
  expanded.value = !expanded.value
  if (expanded.value) { page.value = 0; void load() }
  else { generation++; loading.value = false; tickets.value = [] }
}
function changePage(offset: number) { page.value += offset; void load() }
watch(() => [props.phone, props.excludeTicketId], () => {
  generation++; expanded.value = false; tickets.value = []; error.value = ''; loading.value = false; page.value = 0
})
onBeforeUnmount(() => generation++)
</script>

<template>
  <div class="customer-history">
    <button type="button" class="text-button" :aria-expanded="expanded" @click="toggle">{{ expanded ? 'Skjul kundehistorikk' : label || 'Vis kundens tidligere saker' }}</button>
    <section v-if="expanded" class="customer-history__content" aria-label="Kundens tidligere saker" :aria-busy="loading">
      <p v-if="loading" role="status">Henter kundehistorikk…</p>
      <div v-else-if="error" class="alert alert--error" role="alert">{{ error }} <button type="button" class="text-button" @click="load">Prøv igjen</button></div>
      <template v-else>
        <p v-if="!tickets.length" class="muted">Ingen flere tidligere saker.</p>
        <article v-for="ticket in tickets" :key="ticket.id" class="customer-history__ticket">
          <header><strong>Sak #{{ ticket.id }} · {{ ticket.deviceModel }}</strong><span :class="['status', `status--${ticket.status.toLowerCase()}`]">{{ statusLabels[ticket.status] }}</span></header>
          <small>{{ dateTime.format(new Date(ticket.createdAt)) }} · {{ ticket.category }} · {{ ticket.assignedToName }}</small>
          <p v-if="ticket.newDeviceModel">Ny enhet: {{ ticket.newDeviceModel }}</p>
          <p class="note-text">{{ ticket.description }}</p>
          <p v-if="ticket.resolutionNote" class="note-text"><strong>{{ ticket.status === 'CLOSED' ? 'Avslutningsnotat' : 'Siste avslutningsnotat' }}:</strong> {{ ticket.resolutionNote }}</p>
          <p v-else-if="ticket.status === 'CLOSED'" class="muted">Ingen avslutningsnotat registrert.</p>
        </article>
      </template>
      <div v-if="!error && (page > 0 || tickets.length === pageSize)" class="sensitive-actions" aria-label="Sider med kundehistorikk">
        <button type="button" class="button" :disabled="loading || page === 0" @click="changePage(-1)">Forrige</button>
        <span>Side {{ page + 1 }}</span>
        <button type="button" class="button" :disabled="loading || tickets.length < pageSize" @click="changePage(1)">Neste</button>
      </div>
    </section>
  </div>
</template>
