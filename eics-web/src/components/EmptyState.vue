<template>
  <div class="empty-state">
    <div class="empty-icon">{{ icon }}</div>
    <p class="empty-text">{{ description }}</p>
    <el-button v-if="actionText" :type="actionType" @click="$emit('action')">
      {{ actionText }}
    </el-button>
  </div>
</template>

<script setup>
defineProps({
  icon: { type: String, default: '📭' },
  description: { type: String, default: '暂无数据' },
  actionText: { type: String, default: '' },
  actionType: { type: String, default: 'primary' }
})

defineEmits(['action'])
</script>

<style scoped>
.empty-state { text-align:center; padding:60px 20px }
.empty-icon { font-size:48px; margin-bottom:12px }
.empty-text { color:#909399; font-size:14px; margin-bottom:16px }
</style>
