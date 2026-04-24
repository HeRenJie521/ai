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
      meta: { auth: true, admin: true },
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
      component: () => import('@/views/admin/SystemSettingsView.vue'),
      meta: { auth: true, admin: true },
    },
    {
      path: '/settings/ai-apps',
      name: 'settings-ai-apps',
      component: () => import('@/views/admin/SystemSettingsView.vue'),
      meta: { auth: true, admin: true },
    },
    {
      path: '/settings/conversations',
      name: 'settings-conversations',
      component: () => import('@/views/admin/SystemSettingsView.vue'),
      meta: { auth: true, admin: true },
    },
    {
      path: '/settings/tools',
      name: 'settings-tools',
      component: () => import('@/views/admin/SystemSettingsView.vue'),
      meta: { auth: true, admin: true },
    },
    {
      path: '/settings/admin-accounts',
      name: 'settings-admin-accounts',
      component: () => import('@/views/admin/AdminAccountsView.vue'),
      meta: { auth: true, admin: true },
    },
    {
      path: '/settings',
      redirect: '/settings/llm',
    },
    {
      // 嵌入网站路由：通过 ?iid=&uid=&ts=&sign= 参数完成 SSO 后展示聊天界面
      path: '/embed',
      name: 'embed',
      component: () => import('@/views/EmbedView.vue'),
      meta: { embed: true },
    },
    {
      // 移动端入口：?phone=手机号 → DMS 免密登录后展示列表
      path: '/mobile',
      name: 'mobile-home',
      component: () => import('@/views/mobile/MobileHomeView.vue'),
      meta: { mobile: true },
    },
    {
      path: '/mobile/apps',
      name: 'mobile-apps',
      component: () => import('@/views/mobile/MobileAppsView.vue'),
      meta: { mobile: true },
    },
    {
      path: '/mobile/chat',
      name: 'mobile-chat',
      component: () => import('@/views/mobile/MobileChatView.vue'),
      meta: { mobile: true },
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
