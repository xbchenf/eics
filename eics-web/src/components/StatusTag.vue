<template>
  <el-tag :type="tagType" :size="size" :effect="effect">
    {{ displayLabel }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String, required: true },
  label: { type: String, default: '' },
  size: { type: String, default: 'small' },
  effect: { type: String, default: 'light' }
})

const CHINESE = {
  BOT: '机器人', WAITING: '等待接入', AGENT: '人工接待中', CLOSED: '已关闭',
  PENDING: '待处理', PROCESSING: '处理中', RESOLVED: '已解决',
  READY: '就绪', PARSING: '解析中', FAILED: '解析失败',
  ACTIVE: '正常', DISABLED: '已禁用', ADMIN: '管理员',
  UP: '运行中', DOWN: '异常', CHECKING: '检测中',
  ONLINE: '在线', OFFLINE: '离线',
  // file types
  TXT: 'TXT', PDF: 'PDF', DOCX: 'DOCX', MD: 'MD', UNKNOWN: '未知'
}

const tagType = computed(() => {
  const map = {
    BOT: 'info', WAITING: 'warning', AGENT: 'success', CLOSED: 'info',
    PENDING: 'danger', PROCESSING: 'warning', RESOLVED: 'success',
    READY: 'success', PARSING: 'warning', FAILED: 'danger',
    ACTIVE: 'success', DISABLED: 'info',
    UP: 'success', DOWN: 'danger', CHECKING: 'warning'
  }
  return map[props.status] || 'info'
})

const displayLabel = computed(() => {
  return props.label || CHINESE[props.status] || props.status
})
</script>
