import { createRouter, createWebHistory } from 'vue-router'
import { TOKEN_KEY } from '@/api/client'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      children: [
        { path: '', redirect: '/workflows' },
        { path: 'workflows', name: 'workflows', component: () => import('@/views/WorkflowListView.vue') },
        { path: 'workflows/new', name: 'workflow-new', component: () => import('@/views/WorkflowEditView.vue') },
        { path: 'workflows/:id/edit', name: 'workflow-edit', component: () => import('@/views/WorkflowEditView.vue') },
        { path: 'executions', name: 'executions', component: () => import('@/views/ExecutionListView.vue') },
        { path: 'executions/:id', name: 'execution-detail', component: () => import('@/views/ExecutionDetailView.vue') },
        { path: 'notifications', name: 'notifications', component: () => import('@/views/NotificationView.vue') },
      ],
    },
    // 未匹配的路径回到列表页，避免用户停在空白页
    { path: '/:pathMatch(.*)*', redirect: '/workflows' },
  ],
})

/**
 * 前端路由守卫只负责「没登录就别进业务页」，
 * 真正的权限校验始终在后端——隐藏按钮不等于阻止请求。
 */
router.beforeEach((to) => {
  const hasToken = !!localStorage.getItem(TOKEN_KEY)
  if (!to.meta.public && !hasToken) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && hasToken) {
    return { path: '/workflows' }
  }
  return true
})

export default router
