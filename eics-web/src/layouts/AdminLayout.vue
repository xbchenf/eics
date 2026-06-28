<template>
  <div class="admin-layout">
    <!-- Sidebar -->
    <div class="sidebar">
      <div class="logo">EICS<span>智能客服</span></div>
      <div class="nav-menu">
        <router-link v-for="item in navItems" :key="item.path"
          :to="item.path" class="nav-item"
          :class="{ active: route.path.startsWith(item.path) }">
          <span class="nav-icon">{{ item.icon }}</span>
          {{ item.label }}
        </router-link>
      </div>
      <div class="sidebar-footer">EICS v1.0.0</div>
    </div>

    <!-- Main -->
    <div class="main">
      <div class="topbar">
        <span class="breadcrumb">{{ currentTitle }}</span>
        <div class="user-info">
          <span v-if="slaAlertCount > 0" class="sla-badge" @click="showSlaAlerts" title="SLA 超时告警">
            🔔 {{ slaAlertCount }}
          </span>
          <span class="online-dot" :class="online ? 'on' : 'off'" />
          <span>{{ authStore.agentName }}</span>
          <StatusTag :status="authStore.agentRole === 'ADMIN' ? 'ADMIN' : 'AGENT_ROLE'"
                     :label="authStore.agentRole" />
          <el-button type="danger" size="small" @click="handleLogout">退出登录</el-button>
        </div>
      </div>
      <div class="content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { agentOnline, agentOffline } from '../api'
import { ElNotification } from 'element-plus'
import StatusTag from '../components/StatusTag.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const online = ref(false)
const slaAlertCount = ref(0)
let notifyWs = null

function connectNotifyWs() {
  const token = localStorage.getItem('eics_token')
  if (!token) return
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const url = `${proto}//${window.location.host}/ws/chat/admin-notify?token=${token}`
  notifyWs = new WebSocket(url)
  notifyWs.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      if (data.type === 'sla_alert') {
        slaAlertCount.value = data.totalOverdue || 0
        if (data.newCount > 0) {
          const first = (data.orders || [])[0]
          ElNotification({
            title: '🔴 SLA 超时告警',
            message: `共 ${data.totalOverdue} 个工单超时${first ? '，如 ' + first.issueType + ' ' + first.priority : ''}`,
            type: 'error',
            duration: 6000,
            position: 'bottom-right'
          })
        }
      }
    } catch (_) {}
  }
  notifyWs.onclose = () => { setTimeout(connectNotifyWs, 10000) }
}

function showSlaAlerts() {
  router.push('/admin/orders')
}

onMounted(async () => {
  try { await agentOnline(); online.value = true } catch (_) {}
  connectNotifyWs()
})
onUnmounted(() => {
  if (notifyWs) { notifyWs.close(); notifyWs = null }
})

const navItems = [
  { path: '/admin/dashboard', icon: '📊', label: '工作台总览' },
  { path: '/admin/sessions', icon: '💬', label: '会话管理' },
  { path: '/admin/orders', icon: '📋', label: '工单管理' },
  { path: '/admin/knowledge', icon: '📚', label: '知识库管理' },
  { path: '/admin/settings', icon: '⚙️', label: '系统设置' }
]

const currentTitle = computed(() => {
  const item = navItems.find(n => route.path.startsWith(n.path))
  return item ? item.label : '管理后台'
})

function handleLogout() {
  agentOffline().catch(() => {})
  if (notifyWs) { notifyWs.close(); notifyWs = null }
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.admin-layout { display:flex; height:100vh }
.sidebar {
  width:220px; background:#001529; color:#fff; display:flex; flex-direction:column; flex-shrink:0;
  user-select:none;
}
.logo { padding:20px; font-size:17px; font-weight:700; border-bottom:1px solid rgba(255,255,255,.08) }
.logo span { color:#409eff; margin-left:2px }
.nav-menu { flex:1; padding-top:8px }
.nav-item {
  display:flex; align-items:center; gap:10px; padding:13px 24px; font-size:14px;
  color:rgba(255,255,255,.6); text-decoration:none; border-left:3px solid transparent;
  transition:all .2s;
}
.nav-item:hover { color:#fff; background:rgba(255,255,255,.05) }
.nav-item.active, .nav-item.router-link-exact-active {
  color:#fff; background:#409eff; border-left-color:#66b1ff;
}
.nav-icon { font-size:17px; width:22px; text-align:center }
.sidebar-footer { padding:14px 20px; font-size:11px; color:rgba(255,255,255,.3); border-top:1px solid rgba(255,255,255,.08) }

.main { flex:1; display:flex; flex-direction:column; overflow:hidden }
.topbar {
  height:56px; background:#fff; border-bottom:1px solid #e4e7ed;
  display:flex; align-items:center; justify-content:space-between; padding:0 24px; flex-shrink:0;
}
.breadcrumb { font-size:14px; color:#606266; font-weight:500 }
.user-info { display:flex; align-items:center; gap:10px; font-size:14px }
.online-dot { width:8px; height:8px; border-radius:50% }
.online-dot.on { background:#67c23a }
.online-dot.off { background:#c0c4cc }
.sla-badge { display:flex; align-items:center; gap:2px; background:#fef0f0; color:#f56c6c; padding:3px 10px; border-radius:12px; font-size:12px; cursor:pointer; font-weight:600; animation:pulse 2s infinite }
@keyframes pulse { 0%,100% { opacity:1 } 50% { opacity:.6 } }
.content { flex:1; overflow-y:auto; padding:20px 24px; background:#f0f2f5 }
</style>
