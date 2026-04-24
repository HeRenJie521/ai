<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { listMobileApps, type MobileAiApp } from '@/api/mobileApps'

const router = useRouter()
const authStore = useAuthStore()

const apps = ref<MobileAiApp[]>([])
const loading = ref(true)
const errorMsg = ref('')

onMounted(async () => {
  if (!authStore.isLoggedIn) {
    router.replace({ name: 'mobile-home' })
    return
  }
  try {
    apps.value = await listMobileApps()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    errorMsg.value = err.response?.data?.message || err.message || '加载失败'
  } finally {
    loading.value = false
  }
})

function goBack() {
  router.back()
}

function openApp(app: MobileAiApp) {
  router.push({ name: 'mobile-chat', query: { appId: app.id } })
}

function getAppInitial(name: string): string {
  return name.charAt(0).toUpperCase()
}
</script>

<template>
  <div class="mobile-apps">
    <!-- 顶部导航 -->
    <header class="mobile-header">
      <button class="back-btn" @click="goBack">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <polyline points="15 18 9 12 15 6"/>
        </svg>
      </button>
      <span class="header-title">应用中心</span>
      <div class="header-placeholder"></div>
    </header>

    <!-- 加载中 -->
    <div v-if="loading" class="center-state">
      <div class="spinner"></div>
      <p>加载中...</p>
    </div>

    <!-- 错误 -->
    <div v-else-if="errorMsg" class="center-state error">
      <p>{{ errorMsg }}</p>
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
</template>

<style scoped>
.mobile-apps {
  display: flex;
  flex-direction: column;
  height: 100dvh;
  background: #f5f5f5;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif;
}

.mobile-header {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  padding-top: calc(12px + env(safe-area-inset-top));
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  position: sticky;
  top: 0;
  z-index: 10;
}

.back-btn {
  background: none;
  border: none;
  padding: 4px;
  cursor: pointer;
  color: #667eea;
  display: flex;
  align-items: center;
  -webkit-tap-highlight-color: transparent;
}

.back-btn svg {
  width: 24px;
  height: 24px;
}

.header-title {
  flex: 1;
  text-align: center;
  font-size: 17px;
  font-weight: 600;
  color: #1a1a1a;
}

.header-placeholder {
  width: 32px;
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
</style>
