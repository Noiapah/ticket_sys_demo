<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from './stores/app'
import { gateway } from './gateway'

const app = useAppStore()
const route = useRoute()
const firstEmployeeName = ref('')
const firstRunError = ref('')
onMounted(app.initialize)

async function createFirstEmployee() {
  try {
    const employee = await gateway.addEmployee(firstEmployeeName.value)
    await app.refreshEmployees()
    await app.selectEmployee(employee.id)
  } catch (cause) { firstRunError.value = cause instanceof Error ? cause.message : 'Kunne ikke opprette den ansatte.' }
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink class="brand" to="/" aria-label="Gå til aktive saker">
        <span class="brand-mark">T</span>
        <span><strong>Telefonhjelp</strong><small>Butikkstøtte</small></span>
      </RouterLink>
      <nav aria-label="Hovedmeny">
        <RouterLink to="/">Saker</RouterLink>
        <RouterLink to="/rapporter">Rapporter</RouterLink>
      </nav>
      <div class="employee-switcher" v-if="app.initialized && app.employees.length">
        <label for="current-employee">Nåværende ansatt</label>
        <select id="current-employee" :value="app.currentEmployeeId ?? ''" @change="app.selectEmployee(Number(($event.target as HTMLSelectElement).value))">
          <option v-for="employee in app.activeEmployees" :key="employee.id" :value="employee.id">{{ employee.name }}</option>
        </select>
        <RouterLink class="settings-link" to="/ansatte">⚙ Ansattinnstillinger</RouterLink>
      </div>
    </header>

    <main :class="['page', { 'page--wide': route.path === '/' }]">
      <div v-if="app.error" class="alert alert--error">{{ app.error }}</div>
      <div v-else-if="!app.initialized" class="loading">Starter Telefonhjelp…</div>
      <section v-else-if="!app.employees.length" class="card form-card first-run">
        <p class="eyebrow">Første oppstart</p><h1>Hvem bruker Telefonhjelp?</h1><p>Opprett den første ansatte. Flere kan legges til senere.</p>
        <form class="inline-form" @submit.prevent="createFirstEmployee"><label class="grow">Navn<input v-model="firstEmployeeName" autofocus required placeholder="F.eks. Emma" /></label><button class="button button--primary">Kom i gang</button></form>
        <div v-if="firstRunError" class="alert alert--error">{{ firstRunError }}</div>
      </section>
      <RouterView v-else />
    </main>
  </div>
</template>
