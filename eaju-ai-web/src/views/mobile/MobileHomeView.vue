<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { mobileLoginApi } from '@/api/mobileAuth'
import { listMobileApps, type MobileAiApp } from '@/api/mobileApps'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

type PageStatus = 'loading' | 'ready' | 'error'
const status = ref<PageStatus>('loading')
const errorMsg = ref('')
const apps = ref<MobileAiApp[]>([])
const appsLoaded = ref(false)
const phone = ref('')

const LS_MOBILE_PHONE = 'eaju_mobile_phone'

onMounted(async () => {
  const phoneParam = String(route.query.phone ?? '')
  if (!phoneParam) {
    status.value = 'error'
    errorMsg.value = '缺少手机号参数（?phone=xxx）'
    return
  }
  phone.value = phoneParam

  const cachedPhone = localStorage.getItem(LS_MOBILE_PHONE)
  const needLogin = !authStore.isLoggedIn || cachedPhone !== phoneParam

  if (needLogin) {
    try {
      const result = await mobileLoginApi(phoneParam)
      authStore.setFromLogin(result)
      localStorage.setItem(LS_MOBILE_PHONE, phoneParam)
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } }; message?: string }
      status.value = 'error'
      errorMsg.value = err.response?.data?.message || err.message || '登录失败'
      return
    }
  }

  try {
    apps.value = await listMobileApps()
  } catch { /* 忽略 */ } finally {
    appsLoaded.value = true
  }

  status.value = 'ready'
})

function openApp(app: MobileAiApp) {
  router.push({ name: 'mobile-chat', query: { appId: app.id } })
}

function startNewChat() {
  router.push({ name: 'mobile-chat' })
}

function getAppInitial(name: string): string {
  return name.charAt(0).toUpperCase()
}
</script>

<template>
  <div class="mobile-home">
    <!-- 加载中 -->
    <div v-if="status === 'loading'" class="center-state">
      <div class="spinner"></div>
      <p>登录中...</p>
    </div>

    <!-- 错误 -->
    <div v-else-if="status === 'error'" class="center-state error">
      <p>{{ errorMsg }}</p>
    </div>

    <!-- 应用列表 -->
    <div v-else class="list-container">
      <!-- 加载中 -->
      <div v-if="!appsLoaded" class="center-state">
        <div class="spinner"></div>
        <p>加载应用中...</p>
      </div>

      <!-- 空状态 -->
      <div v-else-if="apps.length === 0" class="center-state">
        <p>暂无可用应用</p>
      </div>

      <!-- 应用列表 -->
      <div v-else class="apps-list">
        <div
          v-for="app in apps"
          :key="app.id"
          class="app-item"
          @click="openApp(app)"
        >
          <div class="app-avatar">{{ getAppInitial(app.name) }}</div>
          <div class="app-info">
            <span class="app-name">{{ app.name }}</span>
            <span v-if="app.modelDisplayName" class="app-model">{{ app.modelDisplayName }}</span>
            <span v-if="app.welcomeText" class="app-desc">{{ app.welcomeText }}</span>
          </div>
          <svg class="item-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="9 18 15 12 9 6"/>
          </svg>
        </div>
      </div>
    </div>

    <!-- 新会话 FAB -->
    <button v-if="status === 'ready'" class="fab" @click="startNewChat" title="新会话">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
        <line x1="12" y1="5" x2="12" y2="19"/>
        <line x1="5" y1="12" x2="19" y2="12"/>
      </svg>
    </button>
  </div>
</template>

<style scoped>
.mobile-home {
  display: flex;
  flex-direction: column;
  height: 100dvh;
  background: #f5f5f5;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif;
  position: relative;
}

.list-container {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 80px;
}

.apps-list {
  flex: 1;
  overflow-y: auto;
}

.app-item {
  display: flex;
  align-items: center;
  padding: 14px 16px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  gap: 12px;
  min-height: 72px;
  -webkit-tap-highlight-color: transparent;
  transition: background 0.15s;
}

.app-item:active {
  background: #f0f0f0;
}

.app-avatar {
  width: 50px;
  height: 50px;
  border-radius: 14px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  font-size: 22px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.app-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.app-name {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-model {
  font-size: 12px;
  color: #667eea;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-desc {
  font-size: 13px;
  color: #888;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-arrow {
  width: 18px;
  height: 18px;
  color: #bbb;
  flex-shrink: 0;
}

.center-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #666;
  font-size: 15px;
}

.center-state.error {
  color: #ff4d4f;
}

.spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #e0e0e0;
  border-top-color: #667eea;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.fab {
  position: fixed;
  right: 20px;
  bottom: calc(20px + env(safe-area-inset-bottom));
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  border: none;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.5);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  transition: transform 0.15s, box-shadow 0.15s;
  z-index: 20;
}

.fab:active {
  transform: scale(0.93);
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.4);
}

.fab svg {
  width: 26px;
  height: 26px;
}
</style>
