import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { gateway } from '../gateway'
import type { Employee } from '../domain/types'

export const useAppStore = defineStore('app', () => {
  const employees = ref<Employee[]>([])
  const currentEmployeeId = ref<number | null>(null)
  const initialized = ref(false)
  const error = ref('')
  const currentEmployee = computed(() => employees.value.find(employee => employee.id === currentEmployeeId.value) ?? null)
  const activeEmployees = computed(() => employees.value.filter(employee => employee.active))

  async function initialize() {
    if (initialized.value) return
    try {
      const data = await gateway.bootstrap()
      employees.value = data.employees
      currentEmployeeId.value = data.currentEmployeeId ?? data.employees.find(employee => employee.active)?.id ?? null
      initialized.value = true
    } catch (cause) { error.value = cause instanceof Error ? cause.message : 'Kunne ikke starte programmet.' }
  }

  async function selectEmployee(id: number) {
    currentEmployeeId.value = id
    await gateway.setCurrentEmployee(id)
  }

  async function refreshEmployees() { employees.value = await gateway.listEmployees() }
  return { employees, activeEmployees, currentEmployeeId, currentEmployee, initialized, error, initialize, selectEmployee, refreshEmployees }
})
