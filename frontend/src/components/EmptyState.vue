<script setup lang="ts">
defineProps<{ title: string; text: string }>()
</script>
<template><div class="empty-state"><span>✓</span><h2>{{ title }}</h2><p>{{ text }}</p></div></template>

