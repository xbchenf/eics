<template>
  <div class="page">
    <PageHeader title="会话详情">
      <el-button @click="router.back()">返回列表</el-button>
    </PageHeader>

    <div class="detail-layout">
      <!-- 会话信息 -->
      <div class="info-card">
        <h4>会话信息</h4>
        <div class="info-row"><span>会话ID</span><code>{{ session.id }}</code></div>
        <div class="info-row"><span>用户</span><span>{{ session.userId || '匿名' }}</span></div>
        <div class="info-row"><span>状态</span><StatusTag :status="session.status" /></div>
        <div class="info-row"><span>坐席</span><span>{{ session.agentId || '-' }}</span></div>
        <div class="info-row"><span>创建时间</span><span>{{ session.createTime }}</span></div>
        <div class="info-row"><span>关闭时间</span><span>{{ session.closeTime || '-' }}</span></div>
        <div style="margin-top:12px">
          <el-button v-if="session.status==='WAITING'" type="primary" @click="handleAccept">接入会话</el-button>
          <el-button v-if="session.status==='AGENT'" type="danger" @click="handleClose">关闭会话</el-button>
        </div>
      </div>

      <!-- 聊天记录 -->
      <div class="chat-card">
        <h4>聊天记录</h4>
        <div class="chat-timeline" ref="timeline">
          <LoadingSkeleton v-if="loading" :rows="4" />
          <EmptyState v-else-if="messages.length===0" description="暂无消息" />
          <div v-for="(m,i) in messages" :key="i" :class="['msg', msgClass(m.senderType)]">
            <div class="msg-sender">{{ m.senderType }}</div>
            <div class="msg-bubble">{{ m.content }}</div>
            <div class="msg-time">{{ m.timestamp }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getSessionDetail, getSessionMessages, acceptSession, closeSession } from '../../api'
import PageHeader from '../../components/PageHeader.vue'
import StatusTag from '../../components/StatusTag.vue'
import EmptyState from '../../components/EmptyState.vue'
import LoadingSkeleton from '../../components/LoadingSkeleton.vue'
import { ElMessage } from 'element-plus'

const props = defineProps({ id: String })  // vue-router passes :id as prop
const router = useRouter()
const sessionId = router.currentRoute.value.params.id
const session = ref({})
const messages = ref([])
const loading = ref(false)

function msgClass(type) {
  return type === 'BOT' ? 'msg-bot' : type === 'AGENT' ? 'msg-agent' : 'msg-user'
}

async function load() {
  loading.value = true
  try {
    const [sRes, mRes] = await Promise.all([
      getSessionDetail(sessionId),
      getSessionMessages(sessionId)
    ])
    session.value = sRes.data?.data || {}
    messages.value = mRes.data?.data?.messages || []
  } catch (e) { /* ignore */ }
  loading.value = false
}

async function handleAccept() {
  await acceptSession(sessionId)
  ElMessage.success('已接入')
  load()
}
async function handleClose() {
  await closeSession(sessionId)
  ElMessage.success('已关闭')
  load()
}

onMounted(load)
</script>

<style scoped>
.detail-layout { display:grid; grid-template-columns:280px 1fr; gap:16px }
.info-card, .chat-card { background:#fff; border-radius:8px; padding:20px; box-shadow:0 1px 3px rgba(0,0,0,.05) }
.info-card h4, .chat-card h4 { margin:0 0 16px; font-size:15px; color:#303133 }
.info-row { display:flex; justify-content:space-between; padding:8px 0; border-bottom:1px solid #f5f5f5; font-size:13px }
.info-row span:first-child { color:#909399 }
.info-row code { font-size:11px; color:#409eff; background:#f5f7fa; padding:2px 6px; border-radius:3px }
.chat-timeline { max-height:60vh; overflow-y:auto }
.msg { margin-bottom:12px }
.msg-sender { font-size:11px; color:#909399; margin-bottom:2px }
.msg-bubble { display:inline-block; max-width:70%; padding:8px 14px; border-radius:10px; font-size:13px; word-break:break-word }
.msg-bot .msg-bubble { background:#ecf5ff; color:#303133; border:1px solid #d9ecff }
.msg-agent .msg-bubble { background:#409eff; color:#fff }
.msg-user .msg-bubble { background:#fff; border:1px solid #e4e7ed }
.msg-agent { text-align:right }
.msg-time { font-size:11px; color:#c0c4cc; margin-top:2px }
</style>
