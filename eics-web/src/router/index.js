import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Chat',
    component: () => import('../views/ChatView.vue')
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/RegisterView.vue')
  },
  {
    path: '/admin',
    component: () => import('../layouts/AdminLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/admin/DashboardView.vue')
      },
      {
        path: 'sessions',
        name: 'SessionList',
        component: () => import('../views/admin/SessionListView.vue')
      },
      {
        path: 'orders',
        name: 'OrderList',
        component: () => import('../views/admin/OrderListView.vue')
      },
      {
        path: 'orders/:id',
        name: 'OrderDetail',
        component: () => import('../views/admin/OrderDetailView.vue')
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('../views/admin/KnowledgeView.vue')
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('../views/admin/SettingsView.vue')
      }
    ]
  },
  // Legacy redirect
  { path: '/agent', redirect: '/admin/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('eics_token')
  const role = localStorage.getItem('eics_agent_role')

  // 需要登录的页面
  if (to.meta.requiresAuth && !token) { next('/login'); return }
  if (to.path === '/' && !token) { next('/login'); return }

  // 已登录 → /login 和 /register 按角色跳转
  if ((to.path === '/login' || to.path === '/register') && token) {
    next(role === 'USER' ? '/' : '/admin/dashboard')
    return
  }

  // AGENT/ADMIN → 不要进 /，跳去后台
  if (to.path === '/' && token && role !== 'USER') {
    next('/admin/dashboard')
    return
  }

  next()
})

export default router
