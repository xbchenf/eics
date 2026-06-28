import { defineStore } from 'pinia'

/**
 * 认证状态管理 — 坐席登录/登出 + localStorage 持久化
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('eics_token') || '',
    agentId: localStorage.getItem('eics_agent_id') || null,
    agentName: localStorage.getItem('eics_agent_name') || '',
    agentRole: localStorage.getItem('eics_agent_role') || ''
  }),

  getters: {
    isLoggedIn: (state) => !!state.token && !!state.agentId,
    getToken: (state) => state.token
  },

  actions: {
    /** 登录成功 — 保存令牌和坐席信息 */
    login(token, agentId, name, role) {
      this.token = token
      this.agentId = agentId
      this.agentName = name
      this.agentRole = role
      localStorage.setItem('eics_token', token)
      localStorage.setItem('eics_agent_id', agentId)
      localStorage.setItem('eics_agent_name', name)
      localStorage.setItem('eics_agent_role', role)
    },

    /** 退出登录 — 清除所有认证信息 */
    logout() {
      this.token = ''
      this.agentId = null
      this.agentName = ''
      this.agentRole = ''
      localStorage.removeItem('eics_token')
      localStorage.removeItem('eics_agent_id')
      localStorage.removeItem('eics_agent_name')
      localStorage.removeItem('eics_agent_role')
    }
  }
})
