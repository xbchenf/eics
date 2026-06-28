import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

// ==================== 请求拦截器 — 自动注入 Token ====================
api.interceptors.request.use(config => {
  const token = localStorage.getItem('eics_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ==================== 响应拦截器 — 401 自动跳转登录 ====================
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response && error.response.status === 401) {
      // 清除过期/无效的认证信息
      localStorage.removeItem('eics_token')
      localStorage.removeItem('eics_agent_id')
      localStorage.removeItem('eics_agent_name')
      localStorage.removeItem('eics_agent_role')
      // 不在登录页才跳转，避免死循环
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

// ==================== 导出 api 实例（供 LoginView 等直接使用） ====================
export default api

// RAG 问答（V2.0 对话引擎调用）
export function ragChat(question, sessionId) {
  return api.post('/rag/chat', { question, session_id: sessionId })
}

// 创建工单（公开接口，Rasa Action 调用）
export function createOrder(params) {
  return api.post('/order/create', params)
}

// 转人工（公开接口，Rasa Action 调用）
export function transferHuman(sessionId) {
  return api.post('/agent/transfer', { session_id: sessionId })
}

// 用户端 — 人工模式下发送消息（公开接口）
export function sendUserMessage(sessionId, content) {
  return api.post('/agent/user-message', { session_id: sessionId, content })
}

// 用户端 — 拉取会话消息（公开接口）
export function getSessionMessages(sessionId) {
  return api.get(`/agent/messages/${sessionId}`)
}

// ==================== 坐席接口（需要登录，agent_id 由后端从 Token 获取） ====================

// 获取待接入会话
export function getWaitingSessions() {
  return api.get('/agent/waiting')
}

// 接入会话
export function acceptSession(sessionId) {
  return api.post('/agent/accept', { session_id: sessionId })
}

// 发送消息
export function agentSend(sessionId, content) {
  return api.post('/agent/send', { session_id: sessionId, content })
}

// 关闭会话
export function closeSession(sessionId) {
  return api.post('/agent/close', { session_id: sessionId })
}

// ==================== 知识库文档管理（需登录） ====================

// 上传文档（FormData 提交，手动注入 Token）
export function uploadDoc(file) {
  const form = new FormData()
  form.append('file', file)
  const token = localStorage.getItem('eics_token')
  return fetch('/api/v1/doc/upload', {
    method: 'POST',
    body: form,
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  }).then(r => r.json())
}

// 文档列表（支持关键词搜索、状态筛选、分类筛选）
export function getDocList(page = 1, size = 20, status, keyword, category) {
  return api.get('/doc/list', { params: { page, size, status, keyword, category } })
}

// 删除文档
export function deleteDoc(id) {
  return api.delete(`/doc/${id}`)
}

// ==================== 工单管理（需登录） ====================

// 工单列表（支持关键词搜索）
export function getOrderList(page = 1, size = 20, status, keyword) {
  return api.get('/order/list', { params: { page, size, status, keyword } })
}

// 工单详情
export function getOrderDetail(orderId) {
  return api.get(`/order/${orderId}`)
}

// 认领工单
export function assignOrder(orderId) {
  return api.put(`/order/${orderId}/assign`)
}

// 解决工单
export function resolveOrder(orderId) {
  return api.put(`/order/${orderId}/resolve`)
}

// ==================== 坐席在线状态 ====================

// 上线
export function agentOnline() {
  return api.put('/agent/online')
}

// 下线
export function agentOffline() {
  return api.put('/agent/offline')
}

// 在线列表
export function getOnlineAgents() {
  return api.get('/agent/online')
}

// ==================== 会话管理（需登录） ====================

// 会话列表
export function getSessionList(page = 1, size = 20, status) {
  return api.get('/session/list', { params: { page, size, status } })
}

// 会话详情
export function getSessionDetail(id) {
  return api.get(`/session/${id}`)
}

// ==================== 满意度评价 ====================

// 提交评价
export function submitSatisfaction(sessionId, rating, comment) {
  return api.post('/satisfaction', { sessionId, rating, comment })
}

// 满意度统计
export function getSatisfactionStats() {
  return api.get('/satisfaction/stats')
}

// 待评价列表（用户端）
export function getPendingSatisfaction() {
  return api.get('/satisfaction/pending')
}

// 我的评价记录（坐席端）
export function getMyRatings(page = 1, size = 20) {
  return api.get('/satisfaction/my', { params: { page, size } })
}

// 健康检查
export function healthCheck() {
  return api.get('/health')
}
