<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { mobileLoginApi } from '@/api/mobileAuth'
import { listConversations, type ConversationItem } from '@/api/conversations'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

type PageStatus = 'loading' | 'ready' | 'error'
const status = ref<PageStatus>('loading')
const errorMsg = ref('')
const conversations = ref<ConversationItem[]>([])
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
    conversations.value = await listConversations()
  } catch { /* 忽略 */ }

  status.value = 'ready'
})

function goToApps() {
  router.push({ name: 'mobile-apps' })
}

function goToConversation(conv: ConversationItem) {
  router.push({ name: 'mobile-chat', query: { sessionId: conv.sessionId } })
}

function startNewChat() {
  router.push({ name: 'mobile-chat' })
}

function formatTime(t: string | null): string {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const diffMs = now.getTime() - d.getTime()
  const diffDays = Math.floor(diffMs / 86400000)
  if (diffDays === 0) return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  if (diffDays === 1) return '昨天'
  if (diffDays < 7) return `${diffDays}天前`
  return d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
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

    <!-- 主列表 -->
    <div v-else class="list-container">
      <!-- 应用中心固定第一项 -->
      <div class="list-section-title">功能</div>
      <div class="list-item app-center-item" @click="goToApps">
        <div class="item-icon app-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="7" height="7" rx="1"/>
            <rect x="14" y="3" width="7" height="7" rx="1"/>
            <rect x="3" y="14" width="7" height="7" rx="1"/>
            <rect x="14" y="14" width="7" height="7" rx="1"/>
          </svg>
        </div>
        <div class="item-content">
          <span class="item-title">应用中心</span>
          <span class="item-sub">浏览所有 AI 应用</span>
        </div>
        <svg class="item-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="9 18 15 12 9 6"/>
        </svg>
      </div>

      <!-- 最近会话 -->
      <template v-if="conversations.length > 0">
        <div class="list-section-title">最近会话</div>
        <div
          v-for="conv in conversations"
          :key="conv.sessionId"
          class="list-item"
          @click="goToConversation(conv)"
        >
          <div class="item-icon chat-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
          </div>
          <div class="item-content">
            <span class="item-title">{{ conv.title || '新会话' }}</span>
            <span class="item-sub">{{ conv.lastModelDisplayName || '默认模型' }}</span>
          </div>
          <span class="item-time">{{ formatTime(conv.lastMessageAt) }}</span>
        </div>
      </template>
      <div v-else class="empty-hint">暂无最近会话</div>
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

.list-section-title {
  padding: 16px 16px 8px;
  font-size: 13px;
  color: #888;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.list-item {
  display: flex;
  align-items: center;
  padding: 14px 16px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  gap: 12px;
  -webkit-tap-highlight-color: transparent;
  transition: background 0.15s;
  min-height: 64px;
}

.list-item:active {
  background: #f0f0f0;
}

.item-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.app-icon {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
}

.chat-icon {
  background: #e8f4fd;
  color: #1890ff;
}

.item-icon svg {
  width: 22px;
  height: 22px;
}

.item-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.item-title {
  font-size: 16px;
  font-weight: 500;
  color: #1a1a1a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-sub {
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

.item-time {
  font-size: 12px;
  color: #aaa;
  flex-shrink: 0;
}

.app-center-item .item-title {
  font-size: 17px;
  font-weight: 600;
}

.empty-hint {
  text-align: center;
  color: #bbb;
  font-size: 14px;
  padding: 32px 16px;
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
