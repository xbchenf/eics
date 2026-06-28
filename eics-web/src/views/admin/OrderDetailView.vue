<template>
  <div class="page">
    <PageHeader title="工单详情">
      <el-button @click="router.back()">返回列表</el-button>
    </PageHeader>
    <div class="detail-card">
      <div class="detail-grid">
        <div class="detail-item"><label>工单号</label><code>{{ order.id }}</code></div>
        <div class="detail-item"><label>电话</label><span>{{ maskPhone(order.phone) }}</span></div>
        <div class="detail-item"><label>优先级</label>
          <el-select v-model="order.priority" size="small" style="width:100px" @change="handlePriorityChange">
            <el-option label="🔴 P0 紧急" value="P0" />
            <el-option label="🟠 P1 高" value="P1" />
            <el-option label="🔵 P2 中" value="P2" />
            <el-option label="⚪ P3 低" value="P3" />
          </el-select>
        </div>
        <div class="detail-item"><label>SLA 截止</label><span>{{ order.slaDeadline || '-' }}</span></div>
        <div class="detail-item"><label>问题类型</label><span>{{ order.issueType }}</span></div>
        <div class="detail-item"><label>设备编号</label><span>{{ order.deviceId || '-' }}</span></div>
        <div class="detail-item"><label>状态</label><StatusTag :status="order.status" /></div>
        <div class="detail-item"><label>坐席</label><span>{{ order.agentId || '未分配' }}</span></div>
        <div class="detail-item"><label>创建时间</label><span>{{ order.createTime }}</span></div>
        <div class="detail-item"><label>解决时间</label><span>{{ order.resolveTime || '-' }}</span></div>
        <div class="detail-item full"><label>故障描述</label><p>{{ order.faultDescription }}</p></div>
      </div>
      <div class="actions">
        <el-button v-if="order.status==='PENDING'" type="primary" @click="handleAssign">认领工单</el-button>
        <el-button v-if="order.status==='PROCESSING'" type="success" @click="handleResolve">标记解决</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getOrderDetail } from '../../api'
import api from '../../api'
import PageHeader from '../../components/PageHeader.vue'
import StatusTag from '../../components/StatusTag.vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const orderId = router.currentRoute.value.params.id
const order = ref({})

function maskPhone(p) { return p ? p.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2') : '-' }

async function load() {
  try {
    const res = await getOrderDetail(orderId)
    order.value = res.data?.data || {}
  } catch (e) { ElMessage.error('加载失败') }
}

async function handlePriorityChange(newPriority) {
  try {
    await api.put(`/order/${orderId}/priority`, { priority: newPriority })
    ElMessage.success('优先级已更新')
  } catch (_) { ElMessage.error('更新失败') }
}

async function handleAssign() {
  await api.put(`/order/${orderId}/assign`)
  ElMessage.success('已认领')
  load()
}
async function handleResolve() {
  await api.put(`/order/${orderId}/resolve`)
  ElMessage.success('已解决')
  load()
}

onMounted(load)
</script>

<style scoped>
.detail-card { background:#fff; border-radius:8px; padding:24px; box-shadow:0 1px 3px rgba(0,0,0,.05) }
.detail-grid { display:grid; grid-template-columns:1fr 1fr; gap:16px; margin-bottom:20px }
.detail-item label { display:block; font-size:12px; color:#909399; margin-bottom:4px }
.detail-item span, .detail-item p { font-size:14px; color:#303133 }
.detail-item code { font-size:12px; color:#409eff; background:#f5f7fa; padding:2px 6px; border-radius:3px }
.detail-item.full { grid-column:1/-1 }
.detail-item.full p { background:#fafafa; padding:12px; border-radius:6px; line-height:1.6 }
.actions { display:flex; gap:10px; border-top:1px solid #ebeef5; padding-top:16px }
</style>
