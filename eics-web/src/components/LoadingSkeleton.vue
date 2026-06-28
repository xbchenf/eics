<template>
  <div class="skeleton">
    <div v-for="i in rows" :key="i" class="skeleton-row">
      <div class="skeleton-bar" :style="{ width: widths[(i - 1) % widths.length] }" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  rows: { type: Number, default: 5 },
  widths: { type: Array, default: () => ['60%', '80%', '40%', '70%', '50%'] }
})
</script>

<style scoped>
.skeleton { padding:16px 0 }
.skeleton-row { margin-bottom:16px }
.skeleton-bar {
  height:14px; border-radius:4px;
  background:linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size:200% 100%;
  animation:shimmer 1.5s infinite;
}
@keyframes shimmer { 0%{background-position:200% 0} 100%{background-position:-200% 0} }
</style>
