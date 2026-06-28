<template>
  <div class="data-table">
    <div class="table-toolbar" v-if="$slots.toolbar">
      <slot name="toolbar" />
    </div>

    <el-table :data="data" v-loading="loading" stripe border :empty-text="''" @row-click="onRowClick">
      <el-table-column v-for="col in columns" :key="col.prop"
        :prop="col.prop" :label="col.label" :width="col.width"
        :min-width="col.minWidth" :align="col.align || 'left'"
        :show-overflow-tooltip="col.tooltip !== false">
        <template #default="{ row }">
          <slot :name="'col-' + col.prop" :row="row" :value="row[col.prop]">
            <StatusTag v-if="col.tag" :status="row[col.prop]" />
            <span v-else-if="col.truncate" :title="row[col.prop]">
              {{ truncate(row[col.prop], col.truncate) }}
            </span>
            <span v-else>{{ row[col.prop] }}</span>
          </slot>
        </template>
      </el-table-column>
      <el-table-column v-if="$slots.actions || actionsWidth" label="操作" :width="actionsWidth || 160" align="center" fixed="right">
        <template #default="{ row }">
          <slot name="actions" :row="row" />
        </template>
      </el-table-column>
    </el-table>

    <EmptyState v-if="!loading && data.length === 0" :description="emptyText" />

    <div class="table-footer" v-if="total > pageSize">
      <el-pagination background layout="prev, pager, next, total"
        :total="total" :page-size="pageSize" :current-page="currentPage"
        @current-change="$emit('page-change', $event)" />
    </div>
  </div>
</template>

<script setup>
import StatusTag from './StatusTag.vue'
import EmptyState from './EmptyState.vue'

defineProps({
  columns: { type: Array, required: true },
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageSize: { type: Number, default: 20 },
  currentPage: { type: Number, default: 1 },
  emptyText: { type: String, default: '暂无数据' },
  actionsWidth: { type: [Number, String], default: 0 },
  clickable: { type: Boolean, default: false }
})

const emit = defineEmits(['page-change', 'row-click'])

function truncate(text, len) {
  if (!text) return ''
  return text.length > len ? text.substring(0, len) + '...' : text
}

function onRowClick(row) { emit('row-click', row) }
</script>

<style scoped>
.data-table { background:#fff; border-radius:8px; overflow:hidden }
.table-toolbar { padding:16px 20px; border-bottom:1px solid #ebeef5 }
.table-footer { display:flex; justify-content:center; padding:16px 0 }
</style>
