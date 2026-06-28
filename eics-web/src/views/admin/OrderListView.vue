<template>
  <div class="page">
    <PageHeader title="工单管理" subtitle="查看和处理所有工单" />
    <div class="panel">
      <div class="panel-header">工单列表</div>
      <SearchBar placeholder="搜索工单..." filter-placeholder="全部状态"
        :filters="statusFilters" v-model="keyword" v-model:filter="statusFilter"
        @search="(payload) => { keyword = payload.keyword; statusFilter = payload.filter; onSearch() }" />
      <DataTable :columns="columns" :data="orders" :loading="loading"
        :total="total" :page-size="pageSize" :current-page="page"
        empty-text="暂无工单" @page-change="onPageChange">
        <template #col-id="{ row }">
          <span style="font-family:monospace;font-size:12px">{{ row.id?.substring(0,14) }}...</span>
        </template>
        <template #col-phone="{ row }">
          {{ maskPhone(row.phone) }}
        </template>
        <template #col-faultDescription="{ row }">
          {{ row.faultDescription?.substring(0,30) }}{{ row.faultDescription?.length > 30 ? '...' : '' }}
        </template>
        <template #col-priority="{ row }">
          <span :style="{ color: priorityColor(row.priority), fontWeight: (row.priority==='P0'||row.priority==='P1')?'700':'400' }">
            {{ row.priority || 'P2' }}
          </span>
        </template>
        <template #col-slaDeadline="{ row }">
          <span :style="{ color: slaOverdue(row) ? '#f56c6c' : '#909399' }">
            {{ slaCountdown(row) }}
          </span>
        </template>
        <template #col-status="{ row }">
          <StatusTag :status="row.status" />
        </template>
        <template #actions="{ row }">
          <el-button v-if="row.status==='PENDING'" type="primary" size="small" @click="handleAssign(row)">认领</el-button>
          <el-button v-if="row.status==='PROCESSING'" type="success" size="small" @click="handleResolve(row)">解决</el-button>
          <el-button size="small" @click="handleView(row)">查看</el-button>
        </template>
      </DataTable>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getOrderList } from '../../api'
import api from '../../api'
import PageHeader from '../../components/PageHeader.vue'
import SearchBar from '../../components/SearchBar.vue'
import DataTable from '../../components/DataTable.vue'
import StatusTag from '../../components/StatusTag.vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const orders = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const statusFilter = ref('')

const statusFilters = [
  { label: '待处理', value: 'PENDING' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已解决', value: 'RESOLVED' },
  { label: '已关闭', value: 'CLOSED' }
]

const columns = [
  { prop: 'priority', label: '优先级', width: '75' },
  { prop: 'id', label: '工单号', minWidth: '120' },
  { prop: 'phone', label: '电话', width: '110' },
  { prop: 'issueType', label: '类型', width: '80' },
  { prop: 'faultDescription', label: '故障描述', minWidth: '140' },
  { prop: 'slaDeadline', label: 'SLA', width: '110' },
  { prop: 'status', label: '状态', width: '85' },
  { prop: 'createTime', label: '时间', minWidth: '120' }
]

function maskPhone(p) { return p ? p.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2') : '-' }

function priorityColor(p) { return { P0: '#f56c6c', P1: '#e6a23c', P2: '#409eff', P3: '#909399' }[p] || '#909399' }

function slaCountdown(row) {
  if (!row.slaDeadline || row.status === 'RESOLVED' || row.status === 'CLOSED') return '-'
  const diff = new Date(row.slaDeadline) - new Date()
  if (diff < 0) return '超时 ' + Math.abs(Math.floor(diff / 60000)) + ' 分钟'
  const h = Math.floor(diff / 3600000), m = Math.floor((diff % 3600000) / 60000)
  return h > 0 ? `剩 ${h}h${m}m` : `剩 ${m} 分钟`
}

function slaOverdue(row) {
  if (!row.slaDeadline || row.status !== 'PENDING') return false
  return new Date(row.slaDeadline) < new Date()
}

async function load() {
  loading.value = true
  try {
    const res = await getOrderList(page.value, pageSize.value, statusFilter.value || undefined, keyword.value || undefined)
    orders.value = res.data?.data?.records || []
    total.value = res.data?.data?.total || 0
  } catch (e) { ElMessage.error('加载失败') }
  loading.value = false
}

function onSearch() { page.value = 1; load() }
function onPageChange(p) { page.value = p; load() }
async function handleAssign(row) {
  try {
    await api.put(`/order/${row.id}/assign`)
    ElMessage.success('已认领')
    load()
  } catch (e) { ElMessage.error('认领失败') }
}
async function handleResolve(row) {
  try {
    await api.put(`/order/${row.id}/resolve`)
    ElMessage.success('已解决')
    load()
  } catch (e) { ElMessage.error('操作失败') }
}
function handleView(row) { router.push(`/admin/orders/${row.id}`) }

onMounted(load)
</script>

<style scoped>
.page { }
.panel { background:#fff; border-radius:8px; box-shadow:0 1px 3px rgba(0,0,0,.05); overflow:hidden }
.panel-header { padding:14px 20px; font-size:15px; font-weight:600; border-bottom:1px solid #ebeef5 }
</style>
