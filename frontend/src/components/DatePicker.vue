<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  modelValue: string
  label: string
  min?: string
  max?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const root = ref<HTMLElement | null>(null)
const open = ref(false)
const today = new Date()

function parseIso(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function formatIso(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const visibleMonth = ref(new Date(parseIso(props.modelValue).getFullYear(), parseIso(props.modelValue).getMonth(), 1))
const displayValue = computed(() => new Intl.DateTimeFormat('nb-NO', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(parseIso(props.modelValue)))
const monthTitle = computed(() => {
  const value = new Intl.DateTimeFormat('nb-NO', { month: 'long', year: 'numeric' }).format(visibleMonth.value)
  return value.charAt(0).toUpperCase() + value.slice(1)
})
const days = computed(() => {
  const first = new Date(visibleMonth.value.getFullYear(), visibleMonth.value.getMonth(), 1)
  const mondayOffset = (first.getDay() + 6) % 7
  const gridStart = new Date(first)
  gridStart.setDate(first.getDate() - mondayOffset)
  return Array.from({ length: 42 }, (_, offset) => {
    const date = new Date(gridStart)
    date.setDate(gridStart.getDate() + offset)
    const value = formatIso(date)
    return {
      value,
      label: date.getDate(),
      outside: date.getMonth() !== visibleMonth.value.getMonth(),
      selected: value === props.modelValue,
      today: value === formatIso(today),
      disabled: Boolean((props.min && value < props.min) || (props.max && value > props.max))
    }
  })
})

watch(() => props.modelValue, value => {
  const selected = parseIso(value)
  visibleMonth.value = new Date(selected.getFullYear(), selected.getMonth(), 1)
})

function toggle() {
  open.value = !open.value
  if (open.value) {
    const selected = parseIso(props.modelValue)
    visibleMonth.value = new Date(selected.getFullYear(), selected.getMonth(), 1)
  }
}

function changeMonth(offset: number) {
  visibleMonth.value = new Date(visibleMonth.value.getFullYear(), visibleMonth.value.getMonth() + offset, 1)
}

function choose(value: string, disabled: boolean) {
  if (disabled) return
  emit('update:modelValue', value)
  open.value = false
}

function chooseToday() {
  const value = formatIso(today)
  if ((props.min && value < props.min) || (props.max && value > props.max)) return
  choose(value, false)
}

function closeOnOutsideClick(event: MouseEvent) {
  if (root.value && !root.value.contains(event.target as Node)) open.value = false
}

onMounted(() => document.addEventListener('mousedown', closeOnOutsideClick))
onBeforeUnmount(() => document.removeEventListener('mousedown', closeOnOutsideClick))
</script>

<template>
  <div ref="root" class="date-picker" @keydown.esc="open = false">
    <span class="date-picker__label">{{ label }}</span>
    <button class="date-picker__field" type="button" :aria-expanded="open" aria-haspopup="dialog" @click="toggle">
      <span>{{ displayValue }}</span>
      <span aria-hidden="true">▦</span>
    </button>
    <div v-if="open" class="date-picker__dropdown" role="dialog" :aria-label="`${label}: velg dato`">
      <header class="date-picker__header">
        <button type="button" aria-label="Forrige måned" @click="changeMonth(-1)">‹</button>
        <strong>{{ monthTitle }}</strong>
        <button type="button" aria-label="Neste måned" @click="changeMonth(1)">›</button>
      </header>
      <div class="date-picker__weekdays" aria-hidden="true">
        <span v-for="weekday in ['Ma', 'Ti', 'On', 'To', 'Fr', 'Lø', 'Sø']" :key="weekday">{{ weekday }}</span>
      </div>
      <div class="date-picker__days">
        <button
          v-for="day in days"
          :key="day.value"
          type="button"
          :class="{ outside: day.outside, selected: day.selected, today: day.today }"
          :disabled="day.disabled"
          :aria-label="day.value"
          :aria-pressed="day.selected"
          @click="choose(day.value, day.disabled)"
        >{{ day.label }}</button>
      </div>
      <button class="date-picker__today" type="button" @click="chooseToday">I dag</button>
    </div>
  </div>
</template>
