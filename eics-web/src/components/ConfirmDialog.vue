<template>
  <el-dialog v-model="visible" :title="title" width="400px" :close-on-click-modal="false" center>
    <p style="text-align:center;font-size:14px;color:#606266">{{ message }}</p>
    <template #footer>
      <el-button @click="onCancel">取消</el-button>
      <el-button :type="confirmType" :loading="loading" @click="onConfirm">
        {{ confirmText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '确认操作' },
  message: { type: String, default: '确定要执行此操作吗？' },
  confirmText: { type: String, default: '确定' },
  confirmType: { type: String, default: 'danger' }
})

const emit = defineEmits(['update:modelValue', 'confirm', 'cancel'])
const visible = ref(false)
const loading = ref(false)

watch(() => props.modelValue, v => { visible.value = v })
watch(visible, v => { if (!v) emit('update:modelValue', false) })

function onConfirm() { emit('confirm') }
function onCancel() { visible.value = false; emit('cancel') }

defineExpose({ setLoading: (v) => { loading.value = v } })
</script>
