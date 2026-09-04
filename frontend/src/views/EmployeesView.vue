<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { gateway } from '../gateway'
import type { Employee } from '../domain/types'
import { useAppStore } from '../stores/app'

const app = useAppStore(); const employees = ref<Employee[]>([]); const newName = ref(''); const error = ref('')
async function load() { employees.value = await gateway.listEmployees() }
async function add() { if (!newName.value.trim()) return; try { await gateway.addEmployee(newName.value); newName.value = ''; await load(); await app.refreshEmployees() } catch (cause) { error.value = cause instanceof Error ? cause.message : 'Kunne ikke legge til ansatt.' } }
async function rename(employee: Employee) { const name = window.prompt('Nytt navn', employee.name)?.trim(); if (!name || name === employee.name) return; await gateway.updateEmployee(employee.id, { name }); await load(); await app.refreshEmployees() }
async function toggle(employee: Employee) { if (employee.id === app.currentEmployeeId && employee.active) { error.value = 'Velg en annen ansatt før denne deaktiveres.'; return } await gateway.updateEmployee(employee.id, { active: !employee.active }); await load(); await app.refreshEmployees() }
onMounted(load)
</script>
<template><RouterLink class="back-link" to="/">← Aktive saker</RouterLink><section class="card form-card"><p class="eyebrow">Innstillinger</p><h1>Ansatte</h1><p>Deaktiver ansatte i stedet for å slette dem, slik at historikken bevares.</p><form class="inline-form" @submit.prevent="add"><label class="grow">Navn<input v-model="newName" placeholder="Navn på ny ansatt" required /></label><button class="button button--primary">Legg til</button></form><div v-if="error" class="alert alert--error">{{ error }}</div><ul class="employee-list"><li v-for="employee in employees" :key="employee.id" :class="{ inactive: !employee.active }"><div><span class="avatar">{{ employee.name.charAt(0) }}</span><span><strong>{{ employee.name }}</strong><small>{{ employee.active ? 'Aktiv' : 'Deaktivert' }}</small></span></div><div><button class="text-button" @click="rename(employee)">Gi nytt navn</button><button class="text-button" :class="{ danger: employee.active }" @click="toggle(employee)">{{ employee.active ? 'Deaktiver' : 'Aktiver' }}</button></div></li></ul></section></template>

