<template>
  <div class="dashboard">
    <!-- 欢迎横幅 -->
    <div class="dash-hero">
      <div class="hero-left">
        <div class="hero-title">👋 工作台</div>
        <div class="hero-sub">{{ greeting }}，以下是今日系统运行概览</div>
      </div>
      <div class="hero-clock">{{ now }}</div>
    </div>

    <!-- KPI 卡片 -->
    <div class="kpi-row">
      <div class="kpi-card" style="--c:#409eff">
        <div class="kpi-icon">💬</div>
        <div class="kpi-body">
          <div class="kpi-num">{{ stats.todaySessions }}</div>
          <div class="kpi-label">今日会话</div>
        </div>
      </div>
      <div class="kpi-card" style="--c:#e6a23c">
        <div class="kpi-icon">📋</div>
        <div class="kpi-body">
          <div class="kpi-num">{{ stats.pendingOrders }}<span v-if="stats.overdueOrders>0" class="kpi-alert">/{{ stats.overdueOrders }}超时</span></div>
          <div class="kpi-label">待处理工单</div>
        </div>
      </div>
      <div class="kpi-card" style="--c:#67c23a">
        <div class="kpi-icon">👤</div>
        <div class="kpi-body">
          <div class="kpi-num">{{ stats.onlineAgents }}</div>
          <div class="kpi-label">在线坐席</div>
        </div>
      </div>
      <div class="kpi-card" style="--c:#909399">
        <div class="kpi-icon">📚</div>
        <div class="kpi-body">
          <div class="kpi-num">{{ stats.totalDocuments }}</div>
          <div class="kpi-label">知识库文档</div>
        </div>
      </div>
      <div class="kpi-card" style="--c:#f56c6c">
        <div class="kpi-icon">⭐</div>
        <div class="kpi-body">
          <div class="kpi-num">{{ stats.avgSatisfaction }}<span class="kpi-unit">/5</span></div>
          <div class="kpi-label">满意度 ({{ stats.satisfactionCount }}条)</div>
        </div>
      </div>
      <div class="kpi-card" style="--c:#9b59b6">
        <div class="kpi-icon">📊</div>
        <div class="kpi-body">
          <div class="kpi-num">{{ stats.todayOrders }}</div>
          <div class="kpi-label">今日工单</div>
        </div>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="chart-row">
      <!-- 优先级分布 -->
      <div class="chart-panel">
        <div class="chart-title">📌 待处理工单 · 优先级分布</div>
        <div class="bar-chart">
          <div v-for="item in priorityBars" :key="item.label" class="bar-row">
            <span class="bar-label">{{ item.label }}</span>
            <div class="bar-track">
              <div class="bar-fill" :style="{ width: item.pct + '%', background: item.color }"></div>
            </div>
            <span class="bar-num">{{ item.count }}</span>
          </div>
        </div>
      </div>

      <!-- 7 日趋势 -->
      <div class="chart-panel">
        <div class="chart-title">📈 近 7 日趋势</div>
        <div class="trend-chart">
          <div class="trend-row">
            <div class="trend-legend"><span class="dot" style="background:#409eff"></span> 会话</div>
            <div class="trend-legend"><span class="dot" style="background:#67c23a"></span> 工单</div>
          </div>
          <div class="trend-bars">
            <div v-for="(label, i) in stats.weekLabels" :key="i" class="trend-col">
              <div class="trend-bar-wrap">
                <div class="trend-bar bar-blue" :style="{ height: barHeight(stats.weeklySessions[i]) }" :title="'会话: '+stats.weeklySessions[i]"></div>
                <div class="trend-bar bar-green" :style="{ height: barHeight(stats.weeklyOrders[i]) }" :title="'工单: '+stats.weeklyOrders[i]"></div>
              </div>
              <div class="trend-label">{{ label }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 双列表格 -->
    <div class="panel-row">
      <div class="panel">
        <div class="panel-header">
          <span>📋 最近会话</span>
          <router-link to="/admin/sessions" class="panel-link">全部 →</router-link>
        </div>
        <DataTable :columns="sessionColumns" :data="recentSessions" :loading="loading"
                   empty-text="暂无会话" :page-size="6" :total="0">
          <template #col-userId="{ row }">
            {{ row.userName || row.userId || '匿名' }}
          </template>
        </DataTable>
      </div>
      <div class="panel">
        <div class="panel-header">
          <span>📋 最近工单</span>
          <router-link to="/admin/orders" class="panel-link">全部 →</router-link>
        </div>
        <DataTable :columns="orderColumns" :data="recentOrders" :loading="loading"
                   empty-text="暂无工单" :page-size="6" :total="0">
          <template #col-status="{ row }">
            <StatusTag :status="row.status" />
          </template>
          <template #col-priority="{ row }">
            <span :style="{ color: priorityColor(row.priority), fontWeight:600 }">{{ row.priority }}</span>
          </template>
        </DataTable>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { getSessionList, getOrderList } from '../../api'
import DataTable from '../../components/DataTable.vue'
import StatusTag from '../../components/StatusTag.vue'
import api from '../../api'

const loading = ref(false)
const recentSessions = ref([])
const recentOrders = ref([])
const now = ref('')
const stats = reactive({
  todaySessions: 0, todayOrders: 0, pendingOrders: 0, overdueOrders: 0,
  onlineAgents: 0, totalDocuments: 0, avgSatisfaction: 0, satisfactionCount: 0,
  priorityDist: {}, weekLabels: [], weeklySessions: [], weeklyOrders: []
})
let timer = null
let clockTimer = null

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 12) return '早上好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const priorityBars = computed(() => {
  const colors = { P0: '#f56c6c', P1: '#e6a23c', P2: '#409eff', P3: '#909399' }
  const dist = stats.priorityDist || {}
  const max = Math.max(1, ...Object.values(dist))
  return Object.entries(colors).map(([label, color]) => ({
    label, color, count: dist[label] || 0, pct: Math.round(((dist[label] || 0) / max) * 100)
  }))
})

function priorityColor(p) {
  return { P0: '#f56c6c', P1: '#e6a23c', P2: '#409eff', P3: '#909399' }[p] || '#909399'
}

function barHeight(v) {
  const max = Math.max(1, ...stats.weeklySessions, ...stats.weeklyOrders)
  return Math.max(4, Math.round((v / max) * 100)) + '%'
}

const sessionColumns = [
  { prop: 'userId', label: '用户', minWidth: '100' },
  { prop: 'status', label: '状态', width: '90', tag: true },
  { prop: 'createTime', label: '时间', minWidth: '130' }
]

const orderColumns = [
  { prop: 'id', label: '工单号', width: '120', truncate: 14 },
  { prop: 'issueType', label: '类型', width: '80' },
  { prop: 'priority', label: '优先级', width: '65' },
  { prop: 'status', label: '状态', width: '80' },
  { prop: 'createTime', label: '时间', minWidth: '110' }
]

async function loadData() {
  loading.value = true
  try {
    const [dashRes, sRes, oRes] = await Promise.all([
      api.get('/dashboard').catch(() => ({ data: { data: {} } })),
      getSessionList(1, 6),
      getOrderList(1, 6)
    ])
    const d = dashRes.data?.data || {}
    Object.assign(stats, {
      todaySessions: d.todaySessions || 0, todayOrders: d.todayOrders || 0,
      pendingOrders: d.pendingOrders || 0, overdueOrders: d.overdueOrders || 0,
      onlineAgents: d.onlineAgents || 0, totalDocuments: d.totalDocuments || 0,
      avgSatisfaction: d.avgSatisfaction || 0, satisfactionCount: d.satisfactionCount || 0,
      priorityDist: d.priorityDist || {}, weekLabels: d.weekLabels || [],
      weeklySessions: d.weeklySessions || [], weeklyOrders: d.weeklyOrders || []
    })
    recentSessions.value = sRes.data?.data?.records || []
    recentOrders.value = oRes.data?.data?.records || []
  } catch (e) { /* ignore */ }
  loading.value = false
}

function updateClock() {
  now.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => { loadData(); updateClock(); timer = setInterval(loadData, 30000); clockTimer = setInterval(updateClock, 1000) })
onUnmounted(() => { clearInterval(timer); clearInterval(clockTimer) })
</script>

<style scoped>
.dashboard { padding:0 0 20px 0 }
/* 欢迎横幅 */
.dash-hero { background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%); border-radius:12px; padding:28px 32px; display:flex; justify-content:space-between; align-items:center; margin-bottom:20px; color:#fff }
.hero-title { font-size:22px; font-weight:700; margin-bottom:4px }
.hero-sub { font-size:13px; opacity:.7 }
.hero-clock { font-size:28px; font-weight:300; font-family:monospace; letter-spacing:2px }

/* KPI 卡片 */
.kpi-row { display:grid; grid-template-columns:repeat(6,1fr); gap:14px; margin-bottom:20px }
.kpi-card { background:#fff; border-radius:10px; padding:20px 18px; display:flex; align-items:center; gap:14px; box-shadow:0 2px 8px rgba(0,0,0,.04); border-top:3px solid var(--c); transition:transform .15s }
.kpi-card:hover { transform:translateY(-2px) }
.kpi-icon { width:44px; height:44px; border-radius:10px; display:flex; align-items:center; justify-content:center; font-size:22px; background:color-mix(in srgb, var(--c) 12%, transparent) }
.kpi-body { flex:1 }
.kpi-num { font-size:24px; font-weight:700; color:#303133; line-height:1.2 }
.kpi-label { font-size:12px; color:#909399; margin-top:2px }
.kpi-unit { font-size:13px; font-weight:400; color:#909399 }
.kpi-alert { font-size:12px; color:#f56c6c; margin-left:4px; font-weight:500 }

/* 图表区 */
.chart-row { display:grid; grid-template-columns:1fr 1fr; gap:16px; margin-bottom:20px }
.chart-panel { background:#fff; border-radius:10px; padding:20px 24px; box-shadow:0 2px 8px rgba(0,0,0,.04) }
.chart-title { font-size:14px; font-weight:600; color:#303133; margin-bottom:18px }

/* 横向柱状图 */
.bar-chart { display:flex; flex-direction:column; gap:12px }
.bar-row { display:flex; align-items:center; gap:10px }
.bar-label { width:28px; font-size:12px; font-weight:600; color:#606266; text-align:right }
.bar-track { flex:1; height:22px; background:#f5f7fa; border-radius:11px; overflow:hidden }
.bar-fill { height:100%; border-radius:11px; transition:width .5s ease; min-width:2px }
.bar-num { width:28px; font-size:12px; font-weight:600; color:#303133; text-align:left }

/* 7 日趋势 */
.trend-row { display:flex; gap:20px; margin-bottom:12px }
.trend-legend { font-size:12px; color:#909399; display:flex; align-items:center; gap:6px }
.dot { width:8px; height:8px; border-radius:50%; display:inline-block }
.trend-bars { display:flex; align-items:flex-end; gap:8px; height:120px; padding-top:8px }
.trend-col { flex:1; display:flex; flex-direction:column; align-items:center; height:100% }
.trend-bar-wrap { flex:1; width:100%; display:flex; align-items:flex-end; gap:2px; justify-content:center }
.trend-bar { width:13px; border-radius:4px 4px 0 0; min-height:4px; transition:height .4s ease }
.bar-blue { background:linear-gradient(180deg, #66b1ff, #409eff) }
.bar-green { background:linear-gradient(180deg, #85ce61, #67c23a) }
.trend-label { font-size:11px; color:#c0c4cc; margin-top:6px }

/* 列表面板 */
.panel-row { display:grid; grid-template-columns:1fr 1fr; gap:16px }
.panel { background:#fff; border-radius:10px; box-shadow:0 2px 8px rgba(0,0,0,.04); overflow:hidden }
.panel-header { padding:14px 20px; border-bottom:1px solid #ebeef5; font-size:14px; font-weight:600; display:flex; justify-content:space-between; align-items:center }
.panel-link { font-size:12px; color:#409eff; text-decoration:none; font-weight:400 }
</style>
