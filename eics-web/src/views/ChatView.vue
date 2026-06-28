<template>
  <div class="chat-container">
    <!-- 顶栏 -->
    <div class="chat-topbar">
      <div class="top-left">
        <span class="logo">EICS<span>智能客服</span></span>
      </div>
      <div class="top-right" v-if="isLoggedIn">
        <el-button size="small" text @click="showOrders = true; loadMyOrders(); loadPendingForOrders()">
          📝 我的工单
          <span v-if="pendingCount > 0" class="pending-badge">{{ pendingCount }}</span>
        </el-button>
        <span class="user-name">{{ userName }}</span>
        <span class="user-avatar">{{ userName?.charAt(0) || 'U' }}</span>
        <el-button size="small" text @click="handleLogout" style="color:#909399">退出</el-button>
      </div>
    </div>

    <!-- 状态栏 -->
    <div :class="['status-bar', statusTagType === 'warning' ? 'agent' : '']">
      <span>{{ statusText }}</span>
    </div>

    <!-- 消息列表 -->
    <div class="chat-messages" ref="msgBox">
      <div v-if="messages.length === 0" class="welcome-card">
        <div class="welcome-icon">💬</div>
        <div class="welcome-title" v-if="isLoggedIn">您好，{{ userName }}</div>
        <div class="welcome-desc">我是 EICS 智能助手，可以帮您解答问题、提交工单</div>
      </div>
      <div v-for="(msg, i) in messages" :key="i"
           :class="['message', msg.sender === 'user' ? 'message-user' : 'message-bot']">
        <!-- 普通文本消息 -->
        <template v-if="msg.type !== 'satisfaction'">
          <div class="message-bubble">
            {{ msg.text }}
            <div v-if="msg.buttons && msg.buttons.length" class="msg-buttons">
              <button v-for="(b, j) in msg.buttons" :key="j"
                      class="quick-reply-btn" @click="handleButtonClick(b.payload)">{{ b.title }}</button>
            </div>
          </div>
          <div class="message-time">{{ msg.time }}</div>
        </template>
        <!-- 满意度评价卡片 -->
        <div v-else class="satisfaction-card">
          <div v-if="msg.submitted" class="sat-submitted">
            ✅ 感谢您的评价！<br/>
            <span class="sat-result">评分：{{ '★'.repeat(msg.rating) }}{{ '☆'.repeat(5 - msg.rating) }} {{ msg.rating }}分</span>
            <div v-if="msg.comment" class="sat-comment">「{{ msg.comment }}」</div>
          </div>
          <div v-else class="sat-form">
            <div class="sat-title">⭐ 请为本次服务评分</div>
            <div class="sat-stars">
              <span v-for="s in 5" :key="s"
                    :class="['sat-star', { active: s <= msg.hoverRating }]"
                    @click="msg.rating = s"
                    @mouseenter="msg.hoverRating = s"
                    @mouseleave="msg.hoverRating = msg.rating">{{ s <= msg.hoverRating ? '★' : '☆' }}</span>
            </div>
            <textarea v-model="msg.comment" class="sat-textarea" placeholder="说说您的感受...（选填）" maxlength="500" rows="2" />
            <el-button type="primary" size="small" :disabled="!msg.rating" @click="submitRating(i, msg)">提交评价</el-button>
          </div>
        </div>
      </div>
      <div v-if="sending" class="message message-user"><div class="message-bubble sending">{{ inputText || '...' }}</div></div>
      <div v-if="receiving" class="message message-bot"><div class="message-bubble typing">正在输入...</div></div>
      <div v-if="reconnecting" class="reconnect-bar">⚠ 连接断开，正在重连... ({{ reconnectAttempt }}/3)</div>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input">
      <el-input v-model="inputText" placeholder="输入您的问题..."
                @keyup.enter="sendMessage" :disabled="sending || reconnecting">
        <template #append>
          <el-button @click="sendMessage" :disabled="!inputText.trim() || sending || reconnecting" :loading="sending">发送</el-button>
        </template>
      </el-input>
      <div class="quick-actions">
        <el-button size="small" @click="quickSend('转人工')">🔁 转人工</el-button>
        <el-button size="small" @click="quickSend('提交工单')">📋 提交工单</el-button>
      </div>
    </div>

    <!-- 我的工单弹窗 -->
    <el-dialog v-model="showOrders" title="我的工单" width="600px">
      <!-- 待评价区域 -->
      <div v-if="pendingSatisfactions.length > 0" class="pending-section">
        <div class="pending-title">⭐ 待评价服务 ({{ pendingSatisfactions.length }})</div>
        <div v-for="p in pendingSatisfactions" :key="p.sessionId" class="pending-card">
          <span class="pending-session">{{ p.sessionId?.substring(0, 20) }}...</span>
          <span class="pending-time">{{ p.closeTime }}</span>
          <div class="sat-stars-small">
            <span v-for="s in 5" :key="s"
                  :class="['sat-star-sm', { active: s <= p.hoverRating }]"
                  @click="p.rating = s"
                  @mouseenter="p.hoverRating = s"
                  @mouseleave="p.hoverRating = p.rating">{{ s <= p.hoverRating ? '★' : '☆' }}</span>
            <el-button size="small" type="primary" :disabled="!p.rating" @click="submitPendingRating(p)">提交</el-button>
          </div>
        </div>
      </div>
      <div v-if="myOrders.length === 0 && pendingSatisfactions.length === 0" style="text-align:center;padding:40px;color:#909399">暂无工单记录</div>
      <div v-for="o in myOrders" :key="o.id" class="order-card">
        <div class="order-header">
          <span class="order-type">{{ o.issueType || '其他' }}</span>
          <StatusTag :status="o.status" />
        </div>
        <div class="order-desc">{{ o.faultDescription }}</div>
        <div class="order-footer">
          <span class="order-id">工单号: {{ o.id?.substring(0, 16) }}...</span>
          <span class="order-time">{{ o.createTime }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import StatusTag from '../components/StatusTag.vue'
import api from '../api'
import { submitSatisfaction, getPendingSatisfaction } from '../api'

const router = useRouter()
const showOrders = ref(false)

const isLoggedIn = computed(() => !!localStorage.getItem('eics_token'))
const userName = computed(() => localStorage.getItem('eics_agent_name') || '')
const loginId = localStorage.getItem('eics_agent_id')
const userId = loginId || Math.random().toString(36).substring(2, 10)
const WS_PROTOCOL = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
const WS_URL = `${WS_PROTOCOL}//${window.location.host}/ws/chat`

// 动态会话 ID：user-{userId}-{timestamp}
let senderId = null

const messages = ref([])
const inputText = ref('')
const sending = ref(false)
const receiving = ref(false)
const reconnecting = ref(false)
const reconnectAttempt = ref(0)
const msgBox = ref(null)
const statusText = ref('🤖 AI 机器人接待中')
const statusTagType = ref('success')
let ws = null
let reconnectTimer = null
const MAX_RECONNECT = 3

const myOrders = ref([])
const pendingSatisfactions = ref([])
const pendingCount = ref(0)

/** 决定当前使用的会话 ID：上次有未关闭会话则续用，否则生成新 ID */
async function resolveSessionId() {
  const stored = localStorage.getItem('eics_active_session')
  if (stored && isLoggedIn.value) {
    try {
      const res = await api.get(`/session/${stored}`)
      const s = res.data?.data
      if (s && s.status !== 'CLOSED') {
        // 上次会话还在进行中（BOT/WAITING/AGENT），续用
        return stored
      }
    } catch (_) { /* 会话不存在，创建新的 */ }
  }
  // 生成新会话
  const newId = `user-${userId}-${Date.now()}`
  localStorage.setItem('eics_active_session', newId)
  return newId
}

function addMessage(text, sender, buttons) {
  messages.value.push({ text, sender, time: new Date().toLocaleTimeString(), buttons: buttons || [] })
}
function handleButtonClick(payload) { inputText.value = payload; sendMessage() }
function scrollToBottom() { nextTick(() => { if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight }) }

/** 加载历史消息 */
async function loadHistory() {
  try {
    const res = await api.get(`/session/${senderId}/messages`)
    const list = res.data?.data?.messages || []
    list.forEach(m => {
      const sender = m.sender_type === 'USER' ? 'user' : 'bot'
      messages.value.push({ text: m.content, sender, time: m.timestamp ? new Date(m.timestamp).toLocaleTimeString() : '' })
    })
    if (list.length > 0) scrollToBottom()
  } catch (_) {}
}

function connectWebSocket() {
  reconnecting.value = true
  ws = new WebSocket(`${WS_URL}/${senderId}`)
  ws.onopen = () => { reconnecting.value = false; reconnectAttempt.value = 0 }
  ws.onmessage = (event) => {
    receiving.value = false
    try {
      const data = JSON.parse(event.data)
      if (data.type === 'session_closed') {
        addMessage(data.content || '人工服务已结束。', 'bot')
        statusText.value = '🤖 AI 机器人接待中'; statusTagType.value = 'success'
      } else if (data.type === 'satisfaction') {
        messages.value.push({
          type: 'satisfaction',
          sessionId: data.sessionId,
          rating: 0, hoverRating: 0, comment: '', submitted: false,
          time: new Date().toLocaleTimeString()
        })
      } else if (data.type === 'error') {
        addMessage(data.content || '发送失败，请稍后重试', 'bot')
      } else if (data.type === 'message') {
        addMessage(data.content, 'bot', data.buttons)
        if ((data.content || '').includes('转接人工坐席')) { statusText.value = '🧑 等待坐席接入中'; statusTagType.value = 'warning' }
      }
      scrollToBottom()
    } catch (e) {}
  }
  ws.onerror = () => {}
  ws.onclose = () => {
    ws = null; reconnecting.value = false
    if (reconnectAttempt.value < MAX_RECONNECT) {
      reconnectAttempt.value++
      reconnectTimer = setTimeout(connectWebSocket, Math.pow(2, reconnectAttempt.value - 1) * 1000)
    }
  }
}

/** 发送前检查：如果当前会话已关闭，自动创建新会话 */
async function ensureActiveSession() {
  if (!senderId) return
  try {
    const res = await api.get(`/session/${senderId}`)
    const s = res.data?.data
    if (s && s.status === 'CLOSED') {
      // 已关闭 → 开新会话
      const newId = `user-${userId}-${Date.now()}`
      localStorage.setItem('eics_active_session', newId)
      // 断开旧的，连新的
      if (ws) { ws.close(); ws = null }
      clearTimeout(reconnectTimer)
      senderId = newId
      messages.value = []
      connectWebSocket()
    }
  } catch (_) {}
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text) return
  await ensureActiveSession()
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    // 重连中，稍后重试
    setTimeout(() => sendMessage(), 500)
    return
  }
  addMessage(text, 'user'); sending.value = true; receiving.value = true
  try { ws.send(JSON.stringify({ sender_type: 'USER', content: text })) } catch (e) { addMessage('发送失败，请稍后重试', 'bot') }
  sending.value = false; inputText.value = ''; scrollToBottom()
}

function quickSend(text) { inputText.value = text; sendMessage() }

async function loadMyOrders() {
  try { const res = await api.get('/order/my?page=1&size=50'); myOrders.value = res.data?.data?.records || [] } catch (_) {}
}

async function submitRating(index, msg) {
  if (!msg.rating || msg.submitted) return
  try {
    const res = await submitSatisfaction(msg.sessionId, msg.rating, msg.comment)
    if (res.data?.code === 200) {
      msg.submitted = true
    }
  } catch (_) {}
}

async function checkPendingSatisfaction() {
  if (!isLoggedIn.value) return
  try {
    const res = await getPendingSatisfaction()
    pendingCount.value = (res.data?.data || []).length
  } catch (_) { pendingCount.value = 0 }
}

async function loadPendingForOrders() {
  if (!isLoggedIn.value) return
  try {
    const res = await getPendingSatisfaction()
    pendingSatisfactions.value = (res.data?.data || []).map(item => ({
      sessionId: item.sessionId,
      closeTime: item.closeTime ? new Date(item.closeTime).toLocaleString() : '',
      rating: 0, hoverRating: 0, submitted: false
    }))
  } catch (_) { pendingSatisfactions.value = [] }
}

async function submitPendingRating(p) {
  if (!p.rating || p.submitted) return
  try {
    const res = await submitSatisfaction(p.sessionId, p.rating, '')
    if (res.data?.code === 200) {
      p.submitted = true
      pendingCount.value = Math.max(0, pendingCount.value - 1)
      setTimeout(() => {
        pendingSatisfactions.value = pendingSatisfactions.value.filter(x => x !== p)
      }, 1500)
    }
  } catch (_) {}
}

function handleLogout() {
  ['eics_token','eics_agent_id','eics_agent_name','eics_agent_role','eics_active_session'].forEach(k => localStorage.removeItem(k))
  if (ws) { ws.close(); ws = null }
  router.push('/login')
}

onMounted(async () => {
  senderId = await resolveSessionId()
  await loadHistory()
  connectWebSocket()
  checkPendingSatisfaction()
})
onUnmounted(() => { if (ws) { ws.close(); ws = null }; if (reconnectTimer) clearTimeout(reconnectTimer) })
</script>

<style scoped>
.chat-container { max-width:720px; margin:0 auto; height:100vh; display:flex; flex-direction:column; background:#fff; box-shadow:0 0 20px rgba(0,0,0,.05) }
.chat-topbar { display:flex; justify-content:space-between; align-items:center; padding:12px 20px; border-bottom:1px solid #ebeef5; background:#fff; flex-shrink:0 }
.top-left .logo { font-size:16px; font-weight:700; color:#303133 }
.top-left .logo span { color:#409eff; margin-left:2px }
.top-right { display:flex; align-items:center; gap:10px }
.user-name { font-size:13px; color:#606266 }
.user-avatar { width:30px; height:30px; border-radius:50%; background:#409eff; color:#fff; display:flex; align-items:center; justify-content:center; font-size:13px; font-weight:600 }
.status-bar { text-align:center; padding:6px; font-size:12px; color:#909399; background:#fafafa; border-bottom:1px solid #ebeef5; flex-shrink:0 }
.status-bar.agent { color:#67c23a; background:#f0f9eb }
.welcome-card { text-align:center; padding:60px 20px }
.welcome-icon { font-size:48px; margin-bottom:12px }
.welcome-title { font-size:15px; color:#303133; margin-bottom:4px }
.welcome-desc { font-size:13px; color:#909399 }
.chat-messages { flex:1; overflow-y:auto; padding:20px; background:#fafafa }
.message { margin-bottom:16px; display:flex; flex-direction:column }
.message-user { align-items:flex-end }
.message-bot { align-items:flex-start }
.message-bubble { max-width:75%; padding:10px 16px; border-radius:12px; font-size:14px; line-height:1.6; white-space:pre-wrap; word-break:break-word }
.message-user .message-bubble { background:#409eff; color:#fff; border-bottom-right-radius:4px }
.message-bot .message-bubble { background:#fff; color:#303133; border:1px solid #e4e7ed; border-bottom-left-radius:4px }
.message-time { font-size:11px; color:#c0c4cc; margin-top:4px }
.sending { opacity:.6 }
.typing { color:#909399; font-style:italic }
.reconnect-bar { text-align:center; padding:8px; background:#fdf6ec; color:#e6a23c; border-radius:6px; font-size:13px; margin:8px 0 }
.msg-buttons { margin-top:8px; display:flex; flex-wrap:wrap; gap:6px }
.quick-reply-btn { padding:5px 14px; border:1px solid #409eff; background:#fff; color:#409eff; border-radius:16px; cursor:pointer; font-size:13px; transition:all .15s }
.quick-reply-btn:hover { background:#409eff; color:#fff }
.chat-input { padding:14px 20px; border-top:1px solid #ebeef5; background:#fff; flex-shrink:0 }
.quick-actions { margin-top:8px; display:flex; gap:8px }
.order-card { padding:14px; border-bottom:1px solid #ebeef5 }
.order-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:6px }
.order-type { font-size:14px; font-weight:500; color:#303133 }
.order-desc { font-size:13px; color:#606266; margin-bottom:6px }
.order-footer { display:flex; justify-content:space-between; font-size:11px; color:#c0c4cc }
/* 满意度评价卡片 */
.satisfaction-card { max-width:75%; background:#fff; border:1px solid #e4e7ed; border-radius:12px; border-bottom-left-radius:4px; padding:16px 20px }
.sat-form { display:flex; flex-direction:column; gap:10px }
.sat-title { font-size:14px; font-weight:500; color:#303133 }
.sat-stars { display:flex; gap:4px; font-size:28px; cursor:pointer; user-select:none }
.sat-star { color:#e4e7ed; transition:color .1s }
.sat-star.active { color:#f7ba2a }
.sat-textarea { width:100%; border:1px solid #dcdfe6; border-radius:6px; padding:6px 10px; font-size:13px; resize:none; box-sizing:border-box; font-family:inherit }
.sat-textarea:focus { outline:none; border-color:#409eff }
.sat-submitted { text-align:center; color:#67c23a; font-size:14px; line-height:1.8 }
.sat-result { color:#303133; font-size:13px }
.sat-comment { color:#909399; font-size:12px; margin-top:4px }
/* 待评价 badge */
.pending-badge { display:inline-block; background:#f56c6c; color:#fff; border-radius:10px; padding:0 6px; font-size:11px; line-height:18px; min-width:18px; text-align:center; margin-left:2px }
/* 待评价区域 */
.pending-section { margin-bottom:16px; border:1px solid #f7ba2a; border-radius:8px; padding:12px 16px; background:#fef9e7 }
.pending-title { font-size:14px; font-weight:600; color:#e6a23c; margin-bottom:10px }
.pending-card { display:flex; align-items:center; gap:12px; padding:8px 0; border-bottom:1px solid #faecd8; flex-wrap:wrap }
.pending-card:last-child { border-bottom:none }
.pending-session { font-size:12px; color:#909399 }
.pending-time { font-size:11px; color:#c0c4cc }
.sat-stars-small { display:flex; align-items:center; gap:2px; font-size:22px; cursor:pointer; user-select:none; margin-left:auto }
.sat-star-sm { color:#e4e7ed; transition:color .1s }
.sat-star-sm.active { color:#f7ba2a }
</style>
