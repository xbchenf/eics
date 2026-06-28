<template>
  <div class="search-bar">
    <el-input v-model="keyword" :placeholder="placeholder" clearable style="width:220px"
              @keyup.enter="search" @clear="search" />
    <el-select v-if="filters && filters.length" v-model="filterVal" :placeholder="filterPlaceholder"
               clearable style="width:140px" @change="search">
      <el-option v-for="f in filters" :key="f.value" :label="f.label" :value="f.value" />
    </el-select>
    <el-button type="primary" @click="search" :icon="'Search'">搜索</el-button>
    <slot />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  placeholder: { type: String, default: '搜索...' },
  filterPlaceholder: { type: String, default: '全部' },
  filters: { type: Array, default: () => [] },
  modelValue: { type: String, default: '' },
  modelFilter: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'update:modelFilter', 'search'])
const keyword = ref(props.modelValue)
const filterVal = ref(props.modelFilter)

watch(() => props.modelValue, v => { keyword.value = v })
watch(() => props.modelFilter, v => { filterVal.value = v })

function search() {
  emit('update:modelValue', keyword.value)
  emit('update:modelFilter', filterVal.value)
  emit('search', { keyword: keyword.value, filter: filterVal.value })
}
</script>

<style scoped>
.search-bar { display:flex; gap:10px; align-items:center; flex-wrap:wrap }
</style>
