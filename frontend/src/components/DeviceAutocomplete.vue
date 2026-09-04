<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { searchDevices } from '../domain/deviceSearch'
import type { Device, DeviceType, OperatingSystem } from '../domain/types'

const props = defineProps<{ type: DeviceType; modelValue: string; other: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:other': [value: boolean]
  select: [value: { model: string; manufacturer: string; operatingSystem: OperatingSystem }]
}>()
const focused = ref(false)
const activeIndex = ref(0)
const results = computed(() => searchDevices(props.modelValue, props.type))

watch(() => props.modelValue, () => activeIndex.value = 0)

watch(() => props.type, type => {
  if (type !== 'PHONE') emit('update:other', true)
})

function select(device: Device) {
  emit('update:modelValue', device.model)
  emit('select', { model: device.model, manufacturer: device.manufacturer, operatingSystem: device.operatingSystem })
  focused.value = false
}

function onKeydown(event: KeyboardEvent) {
  if (!focused.value || props.other || !results.value.length) return
  if (event.key === 'ArrowDown') { event.preventDefault(); activeIndex.value = (activeIndex.value + 1) % results.value.length }
  else if (event.key === 'ArrowUp') { event.preventDefault(); activeIndex.value = (activeIndex.value - 1 + results.value.length) % results.value.length }
  else if (event.key === 'Enter') { event.preventDefault(); select(results.value[activeIndex.value]) }
  else if (event.key === 'Escape') focused.value = false
}

function closeAfterClick() { window.setTimeout(() => focused.value = false, 100) }
</script>

<template>
  <div class="device-field">
    <div class="field-row">
      <label class="grow">Enhetsmodell
        <input :value="modelValue" :placeholder="other ? 'Skriv inn modell' : 'Søk, f.eks. ip 15 pro'" autocomplete="off" required role="combobox" aria-autocomplete="list" :aria-expanded="focused && !other" :aria-activedescendant="focused && results.length ? `device-option-${activeIndex}` : undefined"
          @focus="focused = true" @blur="closeAfterClick" @keydown="onKeydown" @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)" />
      </label>
      <label class="check other-check"><input type="checkbox" :checked="other" @change="emit('update:other', ($event.target as HTMLInputElement).checked)" /> Annen modell</label>
    </div>
    <ul v-if="focused && !other && results.length" class="autocomplete" role="listbox">
      <li v-for="(device, index) in results" :id="`device-option-${index}`" :key="device.model" role="option" :aria-selected="index === activeIndex">
        <button type="button" :class="{ active: index === activeIndex }" @mouseenter="activeIndex = index" @mousedown.prevent="select(device)"><strong>{{ device.model }}</strong><small>{{ device.family }}</small></button>
      </li>
    </ul>
  </div>
</template>
