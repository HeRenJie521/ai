import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guest: true },
    },
    {
      path: '/',
      redirect: '/chat',
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('@/views/ChatView.vue'),
      meta: { auth: true },
    },
    {
      path: '/settings/llm',
      name: 'settings-llm',
      component: () => import('@/views/admin/SystemSettingsView.vue'),
      meta: { auth: true },
    },
    {
      path: '/settings/api-docs',
      name: 'settings-api-docs',
      component: () => import('@/views/admin/ApiDocsView.vue'),
      meta: { auth: true },
    },
    {
      path: '/settings/api-keys',
      name: 'settings-api-keys',
      redirect: '/settings/llm?tab=api-keys',
    },
    {
      path: '/settings/conversations',
      name: 'settings-conversations',
      redirect: '/settings/llm?tab=conversations',
    },
    {
      path: '/settings',
      redirect: '/settings/llm',
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.auth && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.admin && !auth.isAdmin) {
    return { name: 'chat' }
  }
  if (to.meta.guest && auth.isLoggedIn && to.name === 'login') {
    return { name: 'chat' }
  }
  return true
})

export default router
