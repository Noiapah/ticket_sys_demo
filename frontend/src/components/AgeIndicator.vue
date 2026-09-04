<script setup lang="ts">
import { computed } from 'vue'
import { ageLabel, ageLevel } from '../domain/format'
import type { Ticket } from '../domain/types'

const props = defineProps<{ ticket: Ticket; now: Date }>()
const level = computed(() => ageLevel(props.ticket, props.now))
</script>

<template>
  <span :class="['age', `age--${level}`]" :aria-label="`Saksalder ${ageLabel(ticket.createdAt, now)}, ${level === 'red' ? 'krever oppmerksomhet' : level === 'yellow' ? 'har ventet en stund' : 'nylig opprettet'}`">
    <span class="age-dot" aria-hidden="true"></span>
    <strong>{{ ageLabel(ticket.createdAt, now) }}</strong>
    <small v-if="ticket.urgent">Haster</small>
  </span>
</template>

