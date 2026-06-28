<template>
  <div class="workspace">
    <!-- 左侧栏 -->
    <div class="side-panel">
      <!-- 待接入队列 -->
      <div class="section waiting-section">
        <div class="section-title">
          🟢 待接入
          <el-badge v-if="waitingList.length" :value="waitingList.length" class="badge" />
        </div>
        <EmptyState v-if="waitingList.length===0" icon="🎉" description="暂无等待" />
        <div v-for="s in waitingList" :key="s.id || s.session_id" class="waiting-item"
             :class="{ active: activeTab === (s.id || s.session_id) }" @click="handleAccept(s)">
          <div class="wi-user">{{ userLabel(s) }}</div>
          <div class="wi-time">{{ s.createTime }}</div>
          <el-button size="small" type="primary" class="wi-btn">接入</el-button>
        </div>
      </div>

      <!-- 我的会话 -->
      <div class="section active-section">
        <div class="section-title">💬 我的会话</div>
        <EmptyState v-if="mySessions.length===0" icon="💬" description="暂无进行中会话" />
        <div v-for="s in mySessions" :key="s.id || s.session_id" class="session-tab"
             :class="{ active: activeTab === (s.id || s.session_id) }" @click="switchSession(s)">
          <div class="st-user">{{ userLabel(s) }}</div>
          <el-button size="small" type="danger" text @click.stop="handleClose(s)">✕</el-button>
        </div>
      </div>
    </div>

    <!-- 右侧聊天区 -->
    <div class="chat-panel" v-if="activeTab && currentSession">
      <div class="chat-top">
        <span>{{ userLabel(currentSession) }} · {{ currentSession.id?.substring(0,12) }}...</span>
        <StatusTag :status="currentSession.status" />
      </div>

      <div class="chat-body" ref="chatBody">
        <EmptyState v-if="messages.length===0" description="暂无消息" />
        <div v-for="(m,i) in messages" :key="i" :class="['msg', m.senderType==='AGENT'?'msg-me':'msg-other']">
          <div class="msg-bubble">{{ m.content }}</div>
          <div class="msg-meta">{{ m.senderType }} · {{ m.timestamp }}</div>
        </div>
        <div v-if="sending" class="msg msg-me"><div class="msg-bubble sending">发送中...</div></div>
      </div>

      <div class="quick-replies" v-if="quickReplies.length && currentSession.status === 'AGENT'">
        <span v-for="qr in quickReplies" :key="qr.id" class="qr-btn"
              @click="sendQuickReply(qr.content)">{{ qr.title }}</span>
      </div>
      <div class="chat-input" v-if="currentSession.status === 'AGENT'">
        <el-input v-model="replyText" placeholder="输入回复..." @keyup.enter="sendReply" />
        <el-button type="primary" @click="sendReply" :disabled="!replyText.trim()">发送</el-button>
        <el-button type="danger" @click="handleClose(currentSession)">结束会话</el-button>
      </div>
      <div class="chat-input closed-bar" v-else>
        <span>会话已关闭</span>
      </div>
    </div>

    <div class="chat-panel empty" v-else>
      <EmptyState icon="💬" description="选择一个会话开始接待" />
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { getWaitingSessions, acceptSession, getSessionMessages, agentSend, closeSession, getSessionList } from '../../api'
import api from '../../api'
import StatusTag from '../../components/StatusTag.vue'
import EmptyState from '../../components/EmptyState.vue'
import { ElMessage } from 'element-plus'

function userLabel(s) {
  return s.userName || s.userId || s.user_id || (s.id ? s.id.substring(0, 14) + '...' : '匿名')
}

const WS_URL = `${window.location.protocol==='https:'?'wss:':'ws:'}//${window.location.host}/ws/chat`

const waitingList = ref([])
const mySessions = ref([])
const activeTab = ref(null)
const currentSession = ref(null)
const messages = ref([])
const replyText = ref('')
const sending = ref(false)
const quickReplies = ref([])
const chatBody = ref(null)
let ws = null
let pollTimer = null

async function loadQuickReplies() {
  try {
    const res = await api.get('/agent/quick-replies')
    quickReplies.value = res.data?.data || []
  } catch (_) {}
}

function sendQuickReply(content) {
  replyText.value = content
  sendReply()
}

function scrollDown() {
  nextTick(() => {
    if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight
  })
}

async function loadWaiting() {
  try {
    const res = await getWaitingSessions()
    waitingList.value = res.data?.data?.sessions || []
  } catch (e) { /* ignore */ }
}

async function loadMySessions() {
  try {
    const res = await getSessionList(1, 50, 'AGENT')
    mySessions.value = res.data?.data?.records || []
  } catch (e) { /* ignore */ }
}

async function handleAccept(session) {
  try {
    await acceptSession(session.id || session.session_id)
    ElMessage.success('已接入')
    await loadWaiting()
    await loadMySessions()
    switchSession(session)
  } catch (e) { ElMessage.error('接入失败') }
}

async function switchSession(session) {
  activeTab.value = session.id || session.session_id
  currentSession.value = session
  messages.value = []
  // Load history
  try {
    const res = await getSessionMessages(session.id || session.session_id)
    messages.value = res.data?.data?.messages || []
    scrollDown()
  } catch (e) { /* ignore */ }
  // Connect WebSocket
  if (ws) { ws.close(); ws = null }
  const token = localStorage.getItem('eics_token') || ''
  ws = new WebSocket(`${WS_URL}/${session.id || session.session_id}?token=${encodeURIComponent(token)}`)
  ws.onmessage = (e) => {
    try {
      const d = JSON.parse(e.data)
      if (d.type === 'message' && d.sender_type !== 'AGENT') {
        messages.value.push({ content: d.content, senderType: d.sender_type || 'USER', timestamp: new Date().toLocaleTimeString() })
        scrollDown()
      }
    } catch (_) { /* ignore */ }
  }
}

async function sendReply() {
  const text = replyText.value.trim()
  if (!text || !currentSession.value || !ws || ws.readyState !== WebSocket.OPEN) return
  ws.send(JSON.stringify({ sender_type: 'AGENT', content: text }))
  messages.value.push({ content: text, senderType: 'AGENT', timestamp: new Date().toLocaleTimeString() })
  replyText.value = ''
  scrollDown()
}

async function handleClose(session) {
  try {
    await closeSession(session.id || session.session_id)
    ElMessage.success('已结束')
    if (ws) { ws.close(); ws = null }
    activeTab.value = null
    currentSession.value = null
    messages.value = []
    await loadWaiting()
    await loadMySessions()
  } catch (e) { ElMessage.error('操作失败') }
}

onMounted(() => {
  loadWaiting()
  loadMySessions()
  loadQuickReplies()
  pollTimer = setInterval(() => { loadWaiting(); loadMySessions() }, 3000)
})
onUnmounted(() => {
  if (ws) { ws.close(); ws = null }
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.workspace { display:flex; height:calc(100vh - 96px); gap:0 }
/* Side panel */
.side-panel {
  width:280px; background:#fff; border-radius:8px; display:flex; flex-direction:column;
  box-shadow:0 1px 3px rgba(0,0,0,.05); overflow:hidden; flex-shrink:0;
}
.section { overflow-y:auto; padding:12px }
.waiting-section { flex:1; border-bottom:1px solid #ebeef5 }
.active-section { flex:1 }
.section-title { font-size:14px; font-weight:600; padding:4px 0 8px; display:flex; align-items:center; gap:8px }
.badge { }
.waiting-item {
  padding:10px; border:1px solid #ebeef5; border-radius:6px; margin-bottom:6px;
  cursor:pointer; display:flex; flex-direction:column; gap:4px; transition:all .15s;
}
.waiting-item:hover { border-color:#409eff; background:#ecf5ff }
.waiting-item.active { border-color:#409eff; background:#ecf5ff }
.wi-user { font-size:13px; font-weight:500 }
.wi-time { font-size:11px; color:#c0c4cc }
.wi-btn { align-self:flex-end; margin-top:2px }

.session-tab {
  padding:10px; border:1px solid #ebeef5; border-radius:6px; margin-bottom:6px;
  cursor:pointer; display:flex; justify-content:space-between; align-items:center;
  transition:all .15s;
}
.session-tab:hover { border-color:#409eff; background:#ecf5ff }
.session-tab.active { border-color:#409eff; background:#ecf5ff }
.st-user { font-size:13px; font-weight:500 }

/* Chat panel */
.chat-panel {
  flex:1; margin-left:12px; background:#fff; border-radius:8px;
  box-shadow:0 1px 3px rgba(0,0,0,.05); display:flex; flex-direction:column; overflow:hidden;
}
.chat-panel.empty { justify-content:center; align-items:center }
.chat-top {
  padding:12px 16px; border-bottom:1px solid #ebeef5; font-size:14px;
  display:flex; justify-content:space-between; align-items:center;
}
.chat-body { flex:1; overflow-y:auto; padding:16px }
.msg { margin-bottom:12px }
.msg-me { text-align:right }
.msg-other { text-align:left }
.msg-bubble {
  display:inline-block; max-width:65%; padding:8px 14px; border-radius:10px; font-size:13px; word-break:break-word;
}
.msg-me .msg-bubble { background:#409eff; color:#fff }
.msg-other .msg-bubble { background:#fff; color:#303133; border:1px solid #e4e7ed }
.sending { opacity:.5 }
.msg-meta { font-size:11px; color:#c0c4cc; margin-top:2px }
.chat-input {
  padding:12px 16px; border-top:1px solid #ebeef5; display:flex; gap:8px; align-items:center;
}
.chat-input .el-input { flex:1 }
.closed-bar { justify-content:center; color:#909399; font-size:13px }
.quick-replies { padding:6px 16px; display:flex; gap:6px; flex-wrap:wrap }
.qr-btn {
  padding:3px 12px; border:1px solid #c6e2ff; border-radius:12px; background:#ecf5ff;
  color:#409eff; cursor:pointer; font-size:12px; transition:all .15s; white-space:nowrap;
}
.qr-btn:hover { background:#409eff; color:#fff }
</style>
