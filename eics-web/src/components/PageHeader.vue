<template>
  <div class="page-header">
    <h2>{{ title }}</h2>
    <span class="subtitle" v-if="subtitle">{{ subtitle }}</span>
    <slot />
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' }
})
</script>

<style scoped>
.page-header { display:flex; align-items:center; gap:12px; margin-bottom:20px; flex-wrap:wrap }
.page-header h2 { font-size:18px; font-weight:600; color:#303133; margin:0 }
.subtitle { color:#909399; font-size:13px }
</style>
