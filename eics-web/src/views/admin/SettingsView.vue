<template>
  <div class="settings">
    <!-- 坐席管理 -->
    <div class="panel">
      <div class="panel-header">
        <span>用户管理</span>
        <el-button type="primary" size="small" @click="showAddDialog = true">+ 新增用户</el-button>
      </div>
      <DataTable :columns="agentColumns" :data="agents" :loading="loading" empty-text="暂无用户" :total="0">
        <template #col-status="{ row }">
          <StatusTag :status="row.status" />
        </template>
        <template #col-role="{ row }">
          <el-select :model-value="row.role" size="small" style="width:90px" @change="(v) => handleRoleChange(row, v)">
            <el-option label="USER" value="USER" />
            <el-option label="AGENT" value="AGENT" />
            <el-option label="ADMIN" value="ADMIN" />
          </el-select>
        </template>
        <template #actions="{ row }">
          <el-button v-if="row.status==='ACTIVE'" type="warning" size="small" @click="handleToggle(row)">禁用</el-button>
          <el-button v-else type="success" size="small" @click="handleToggle(row)">启用</el-button>
        </template>
      </DataTable>
    </div>

    <!-- 快捷回复管理 -->
    <div class="panel" style="margin-top:20px">
      <div class="panel-header">
        <span>快捷回复管理</span>
        <el-button type="primary" size="small" @click="showQrDialog = true">+ 新增</el-button>
      </div>
      <DataTable :columns="qrColumns" :data="quickReplies" :loading="qrLoading" empty-text="暂无快捷回复" :total="0">
        <template #col-content="{ row }">
          <span :title="row.content">{{ row.content?.substring(0, 30) }}{{ row.content?.length > 30 ? '...' : '' }}</span>
        </template>
        <template #actions="{ row }">
          <el-button v-if="row.agentId" type="danger" size="small" @click="handleDeleteQr(row)">删除</el-button>
          <span v-else style="color:#909399;font-size:12px">公用</span>
        </template>
      </DataTable>

      <!-- 新增弹窗 -->
      <el-dialog v-model="showQrDialog" title="新增快捷回复" width="450px">
        <el-form label-width="70px">
          <el-form-item label="标题" required><el-input v-model="qrForm.title" placeholder="按钮上显示的文字" /></el-form-item>
          <el-form-item label="内容" required><el-input v-model="qrForm.content" type="textarea" :rows="3" placeholder="点击后发送的完整内容" /></el-form-item>
          <el-form-item label="分组"><el-input v-model="qrForm.category" placeholder="如：问候/技术/结束语" /></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showQrDialog = false">取消</el-button>
          <el-button type="primary" :loading="qrSaving" @click="handleAddQr">确定</el-button>
        </template>
      </el-dialog>
    </div>

    <!-- 服务状态 -->
    <div class="panel" style="margin-top:20px">
      <div class="panel-header">服务健康状态</div>
      <DataTable :columns="healthColumns" :data="services" :total="0">
        <template #col-status="{ row }">
          <StatusTag :status="row.status === 'UP' ? 'READY' : 'FAILED'" :label="row.status === 'UP' ? '运行中' : '异常'" />
        </template>
      </DataTable>
    </div>

    <!-- 新增坐席弹窗 -->
    <el-dialog v-model="showAddDialog" title="新增坐席" width="400px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名" required><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码" required><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width:100%">
            <el-option label="普通用户 (USER)" value="USER" />
            <el-option label="坐席 (AGENT)" value="AGENT" />
            <el-option label="管理员 (ADMIN)" value="ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { healthCheck } from '../../api'
import api from '../../api'
import DataTable from '../../components/DataTable.vue'
import StatusTag from '../../components/StatusTag.vue'
import { ElMessage } from 'element-plus'

const agents = ref([])
const loading = ref(false)
const showAddDialog = ref(false)
const creating = ref(false)
const form = reactive({ username: '', password: '', name: '', role: 'AGENT' })

const services = ref([
  { name: 'Java Backend', port: 8080, status: 'CHECKING' },
  { name: 'MySQL', port: 3306, status: 'CHECKING' },
  { name: 'Redis', port: 6379, status: 'CHECKING' },
  { name: 'Milvus', port: 19530, status: 'CHECKING' },
  { name: 'MinIO', port: 9000, status: 'CHECKING' }
])

const agentColumns = [
  { prop: 'username', label: '用户名', minWidth: '100' },
  { prop: 'name', label: '姓名', minWidth: '100' },
  { prop: 'role', label: '角色', width: '90' },
  { prop: 'status', label: '状态', width: '90' },
  { prop: 'createTime', label: '创建时间', minWidth: '140' }
]

// ==================== 快捷回复管理 ====================
const quickReplies = ref([])
const qrLoading = ref(false)
const showQrDialog = ref(false)
const qrSaving = ref(false)
const qrForm = reactive({ title: '', content: '', category: '' })

const qrColumns = [
  { prop: 'title', label: '标题', minWidth: '100' },
  { prop: 'content', label: '内容预览', minWidth: '200' },
  { prop: 'category', label: '分组', width: '90' }
]

async function loadQuickReplies() {
  qrLoading.value = true
  try { const res = await api.get('/agent/quick-replies'); quickReplies.value = res.data?.data || [] } catch (_) {}
  qrLoading.value = false
}

async function handleAddQr() {
  if (!qrForm.title || !qrForm.content) { ElMessage.warning('标题和内容不能为空'); return }
  qrSaving.value = true
  try {
    await api.post('/agent/quick-replies', { ...qrForm })
    ElMessage.success('已添加')
    showQrDialog.value = false
    Object.assign(qrForm, { title: '', content: '', category: '' })
    loadQuickReplies()
  } catch (_) { ElMessage.error('添加失败') }
  qrSaving.value = false
}

async function handleDeleteQr(row) {
  try { await api.delete(`/agent/quick-replies/${row.id}`); ElMessage.success('已删除'); loadQuickReplies() } catch (_) { ElMessage.error('删除失败') }
}

const healthColumns = [
  { prop: 'name', label: '服务', minWidth: '120' },
  { prop: 'port', label: '端口', width: '80' },
  { prop: 'status', label: '状态', width: '100' }
]

async function loadAgents() {
  loading.value = true
  try {
    const res = await api.get('/agent/list')
    agents.value = res.data?.data?.agents || []
  } catch (e) { ElMessage.error('加载失败') }
  loading.value = false
}

async function handleCreate() {
  if (!form.username || !form.password) { ElMessage.warning('用户名和密码不能为空'); return }
  creating.value = true
  try {
    await api.post('/agent/create', { ...form })
    ElMessage.success('创建成功')
    showAddDialog.value = false
    Object.assign(form, { username: '', password: '', name: '', role: 'AGENT' })
    loadAgents()
  } catch (e) { ElMessage.error(e.response?.data?.message || '创建失败') }
  creating.value = false
}

async function handleToggle(row) {
  try {
    await api.put(`/agent/${row.id}/status`)
    ElMessage.success(row.status === 'ACTIVE' ? '已禁用' : '已启用')
    loadAgents()
  } catch (e) { ElMessage.error('操作失败') }
}

async function handleRoleChange(row, newRole) {
  try {
    await api.put(`/agent/${row.id}/role`, { role: newRole })
    ElMessage.success('角色已更新')
    loadAgents()
  } catch (e) { ElMessage.error('更新失败') }
}

onMounted(async () => {
  loadAgents()
  loadQuickReplies()
  try { await healthCheck(); services.value.forEach(s => s.status = 'UP') } catch (_) {
    services.value.forEach(s => s.status = 'DOWN')
  }
})
</script>

<style scoped>
.panel { background:#fff; border-radius:8px; box-shadow:0 1px 3px rgba(0,0,0,.05); overflow:hidden }
.panel-header { padding:14px 20px; font-size:15px; font-weight:600; border-bottom:1px solid #ebeef5; display:flex; justify-content:space-between; align-items:center }
</style>
