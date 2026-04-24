<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { appEmbedLoginApi } from '@/api/auth'
import { listLlmProviders, type LlmProviderOption } from '@/api/llmProviders'
import { getWelcomeConfigByApp, type WelcomeConfig } from '@/api/welcome'
import {
  deleteConversation,
  listConversations,
  loadConversationMessages,
  type ChatMessage,
} from '@/api/conversations'
import { uploadChatFile } from '@/api/upload'
import { renderChatMarkdown } from '@/utils/chatMarkdown'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// ---- 模式 ----
// appMode: 进入了应用中心某个应用
// sessionMode: 从最近会话进入
// freeMode: 新会话
type ChatMode = 'loading' | 'app' | 'session' | 'free' | 'error'
const chatMode = ref<ChatMode>('loading')
const errorMsg = ref('')
const appId = ref(0)
const chatTitle = ref('AI 助手')

// ---- 聊天核心 ----
const providers = ref<LlmProviderOption[]>([])
const selectedProvider = ref('')
const selectedMode = ref('')
const messages = ref<ChatMessage[]>([])
const input = ref('')
const sending = ref(false)
const sessionId = ref<string | null>(null)
const thinkingOn = ref(false)
const msgScrollRef = ref<HTMLElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const pendingImages = ref<{ id: string; file: File; preview: string; url?: string }[]>([])
let attachSeq = 0

// ---- 是否已发送过消息（用于锁定模型选择） ----
const hasSentMessage = ref(false)

// ---- 开场引导 ----
const welcomeConfig = ref<WelcomeConfig | null>(null)

// ---- 会话删除确认 ----
const showDeleteConfirm = ref(false)

// ---- Provider 能力 ----
const currentProvider = computed(() =>
  providers.value.find(p => p.code === selectedProvider.value) ?? null,
)

const currentModeCapability = computed(() => {
  const p = currentProvider.value
  if (!p?.modeCapabilities || !selectedMode.value) return null
  return p.modeCapabilities[selectedMode.value] ?? null
})

const showThinkingToggle = computed(() => {
  if (chatMode.value === 'session') return false
  const p = currentProvider.value
  if (!p?.supportsThinking) return false
  const caps = p.modeCapabilities
  if (!caps || Object.keys(caps).length === 0) return true
  const cap = caps[selectedMode.value]
  if (!cap) return false
  return cap.deepThinking === true
})

watch(showThinkingToggle, show => { if (!show) thinkingOn.value = false })

const canSelectModel = computed(() =>
  chatMode.value === 'free' && !hasSentMessage.value,
)

const supportsVision = computed(() => {
  const cap = currentModeCapability.value
  if (!cap) return false
  return cap.vision === true
})

// Provider 选项（用于 select）
const providerOptions = computed(() =>
  providers.value.map(p => ({ label: p.displayName, value: p.code })),
)

const modeOptions = computed(() => {
  const p = currentProvider.value
  if (!p?.modes) return []
  return Object.keys(p.modes).map(k => ({ label: k, value: k }))
})

function generateUUID(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

// ---- 初始化 ----
onMounted(async () => {
  if (!authStore.isLoggedIn) {
    router.replace({ name: 'mobile-home' })
    return
  }

  const appIdParam = Number(route.query.appId ?? 0)
  const sessionIdParam = String(route.query.sessionId ?? '')

  // 加载 providers（所有模式都可能需要）
  try {
    providers.value = await listLlmProviders()
    selectDefaultProvider()
  } catch { /* 忽略 */ }

  if (appIdParam) {
    // 应用模式：用 phone 登录应用获取应用配置
    appId.value = appIdParam
    const phone = localStorage.getItem('eaju_mobile_phone') || authStore.userId
    try {
      const result = await appEmbedLoginApi({ appId: appIdParam, userId: phone })
      authStore.setFromLogin(result)
      if (result.integrationName) chatTitle.value = result.integrationName
      // 如果应用有默认模型，使用应用默认模型
      if (result.defaultModel) selectModelById(result.defaultModel)
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } }; message?: string }
      errorMsg.value = err.response?.data?.message || err.message || '应用登录失败'
      chatMode.value = 'error'
      return
    }

    try {
      welcomeConfig.value = await getWelcomeConfigByApp(appIdParam)
    } catch { /* 忽略 */ }

    chatMode.value = 'app'
  } else if (sessionIdParam) {
    // 继续会话模式
    sessionId.value = sessionIdParam
    try {
      const msgs = await loadConversationMessages(sessionIdParam)
      messages.value = msgs
      hasSentMessage.value = msgs.length > 0
      // 从会话中恢复 provider/mode
      const convs = await listConversations()
      const conv = convs.find(c => c.sessionId === sessionIdParam)
      if (conv?.lastProviderCode) {
        selectedProvider.value = conv.lastProviderCode
        if (conv.lastModeKey) selectedMode.value = conv.lastModeKey
      }
      if (conv?.title) chatTitle.value = conv.title
    } catch { /* 忽略 */ }
    chatMode.value = 'session'
    await scrollToBottom()
  } else {
    // 自由新会话模式
    chatMode.value = 'free'
  }
})

onUnmounted(() => {
  pendingImages.value.forEach(img => URL.revokeObjectURL(img.preview))
})

function selectDefaultProvider() {
  if (providers.value.length === 0) return
  const first = providers.value[0]
  selectedProvider.value = first.code
  selectedMode.value = first.defaultMode ?? Object.keys(first.modes ?? {})[0] ?? ''
  if (currentProvider.value?.defaultThinkingMode) {
    thinkingOn.value = true
  }
}

function selectModelById(modelId: string) {
  for (const p of providers.value) {
    if (p.modes) {
      for (const [modeKey, upstreamId] of Object.entries(p.modes)) {
        if (modeKey === modelId || upstreamId === modelId) {
          selectedProvider.value = p.code
          selectedMode.value = modeKey
          return
        }
      }
    }
  }
}

function onProviderChange(code: string) {
  selectedProvider.value = code
  const p = providers.value.find(pp => pp.code === code)
  if (p) {
    selectedMode.value = p.defaultMode ?? Object.keys(p.modes ?? {})[0] ?? ''
    thinkingOn.value = p.defaultThinkingMode ?? false
  }
}

// ---- 图片 ----
function onFileChange(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (!files) return
  for (const file of Array.from(files)) {
    if (!file.type.startsWith('image/')) continue
    const id = `img-${++attachSeq}`
    pendingImages.value.push({ id, file, preview: URL.createObjectURL(file) })
  }
  if (fileInputRef.value) fileInputRef.value.value = ''
}

function removeImage(id: string) {
  const idx = pendingImages.value.findIndex(i => i.id === id)
  if (idx >= 0) {
    URL.revokeObjectURL(pendingImages.value[idx].preview)
    pendingImages.value.splice(idx, 1)
  }
}

// ---- 发送 ----
async function sendMessage(text?: string) {
  const msgText = text ?? input.value.trim()
  if ((!msgText && pendingImages.value.length === 0) || sending.value) return
  if (!selectedProvider.value) return

  const isNewSession = !sessionId.value
  if (isNewSession) sessionId.value = generateUUID()

  // 上传图片
  const fileUrls: string[] = []
  if (pendingImages.value.length > 0) {
    for (const img of pendingImages.value) {
      try {
        const url = await uploadChatFile(img.file)
        fileUrls.push(url)
      } catch { /* 忽略单张失败 */ }
    }
    pendingImages.value.forEach(i => URL.revokeObjectURL(i.preview))
    pendingImages.value = []
  }

  const userMsg: ChatMessage = { role: 'user', content: msgText }
  if (fileUrls.length > 0) userMsg.fileUrls = fileUrls
  messages.value.push(userMsg)
  if (!text) input.value = ''
  sending.value = true
  hasSentMessage.value = true

  const assistantIdx = messages.value.length
  messages.value.push({ role: 'assistant', content: '', reasoningContent: '' })
  await scrollToBottom()

  try {
    const payload: Record<string, unknown> = {
      provider: selectedProvider.value,
      messages: messages.value.slice(0, assistantIdx).map(m => ({
        role: m.role,
        content: m.content,
        ...(m.fileUrls?.length ? { fileUrls: m.fileUrls } : {}),
      })),
      stream: currentModeCapability.value?.streamOutput !== false,
      sessionId: sessionId.value,
      thinkingMode: thinkingOn.value,
    }
    if (selectedMode.value) payload.mode = selectedMode.value

    const resp = await fetch('/api/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authStore.token}`,
      },
      body: JSON.stringify(payload),
    })

    if (!resp.ok || !resp.body) {
      messages.value[assistantIdx].content = '请求失败，请重试'
      return
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''
      for (const line of lines) {
        if (!line.startsWith('data:')) continue
        const raw = line.slice(5).trim()
        if (raw === '[DONE]') continue
        try {
          const chunk = JSON.parse(raw)
          const delta = chunk?.choices?.[0]?.delta?.content ?? ''
          const reasoning = chunk?.choices?.[0]?.delta?.reasoning_content ?? ''
          messages.value[assistantIdx].content += delta
          if (thinkingOn.value && reasoning) {
            messages.value[assistantIdx].reasoningContent =
              (messages.value[assistantIdx].reasoningContent ?? '') + reasoning
          }
          await scrollToStreamBottom()
        } catch { /* skip */ }
      }
    }
  } catch (e: unknown) {
    const err = e as { message?: string }
    messages.value[assistantIdx].content = `错误：${err.message ?? '未知错误'}`
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

async function scrollToBottom() {
  await nextTick()
  if (msgScrollRef.value) {
    msgScrollRef.value.scrollTop = msgScrollRef.value.scrollHeight
  }
}

let scrollDebounce: ReturnType<typeof setTimeout> | null = null
async function scrollToStreamBottom() {
  if (scrollDebounce) return
  scrollDebounce = setTimeout(async () => {
    scrollDebounce = null
    await nextTick()
    if (msgScrollRef.value) {
      const el = msgScrollRef.value
      const isNearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 120
      if (isNearBottom) el.scrollTop = el.scrollHeight
    }
  }, 50)
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    void sendMessage()
  }
}

// ---- 推荐问题 ----
function handleSuggestionClick(question: string) {
  if (sending.value) return
  void sendMessage(question)
}

// ---- 删除会话 ----
async function confirmDeleteConversation() {
  showDeleteConfirm.value = false
  if (!sessionId.value) { router.back(); return }
  try {
    await deleteConversation(sessionId.value)
  } catch { /* 忽略 */ }
  router.back()
}

function stopSending() {
  // 无流式中断支持时仅标记
  sending.value = false
}

function goBack() {
  router.back()
}

// ---- markdown copy ----
function onMsgAreaClick(ev: MouseEvent) {
  const t = ev.target as HTMLElement | null
  if (!t?.closest('.md-copy-btn')) return
  ev.preventDefault()
  const block = t.closest('.md-code-block')
  const codeEl = block?.querySelector('pre.md-code-pre code') as HTMLElement | null
  const text = codeEl?.textContent ?? ''
  if (text) void navigator.clipboard.writeText(text).catch(() => {})
}
</script>

<template>
  <div class="mobile-chat">
    <!-- 顶部导航 -->
    <header class="mobile-header">
      <button class="back-btn" @click="goBack">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <polyline points="15 18 9 12 15 6"/>
        </svg>
      </button>
      <span class="header-title">{{ chatTitle }}</span>
      <button
        v-if="sessionId && chatMode !== 'loading'"
        class="header-action-btn"
        @click="showDeleteConfirm = true"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="3 6 5 6 21 6"/>
          <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
          <path d="M10 11v6M14 11v6"/>
          <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
        </svg>
      </button>
      <div v-else class="header-placeholder"></div>
    </header>

    <!-- 错误 -->
    <div v-if="chatMode === 'error'" class="center-state error">
      <p>{{ errorMsg }}</p>
      <button class="retry-btn" @click="goBack">返回</button>
    </div>

    <!-- 加载中 -->
    <div v-else-if="chatMode === 'loading'" class="center-state">
      <div class="spinner"></div>
    </div>

    <!-- 聊天主体 -->
    <template v-else>
      <!-- 消息列表 -->
      <div ref="msgScrollRef" class="messages-area" @click="onMsgAreaClick">
        <!-- 开场欢迎（应用模式） -->
        <template v-if="chatMode === 'app' && welcomeConfig && messages.length === 0">
          <div v-if="welcomeConfig.welcomeText" class="welcome-msg">
            {{ welcomeConfig.welcomeText }}
          </div>
          <div v-if="welcomeConfig.suggestions?.length" class="suggestions">
            <button
              v-for="s in welcomeConfig.suggestions"
              :key="s"
              class="suggestion-btn"
              :disabled="sending"
              @click="handleSuggestionClick(s)"
            >{{ s }}</button>
          </div>
        </template>

        <!-- 空自由会话提示 -->
        <div v-if="chatMode === 'free' && messages.length === 0" class="empty-chat-hint">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          <p>开始新的对话</p>
        </div>

        <!-- 消息气泡 -->
        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          class="msg-row"
          :class="msg.role === 'user' ? 'user-row' : 'ai-row'"
        >
          <!-- AI 头像 -->
          <div v-if="msg.role === 'assistant'" class="ai-avatar">AI</div>

          <div class="msg-bubble" :class="msg.role === 'user' ? 'user-bubble' : 'ai-bubble'">
            <!-- 思考过程 -->
            <div v-if="msg.reasoningContent" class="reasoning-block">
              <div class="reasoning-label">思考过程</div>
              <!-- eslint-disable-next-line vue/no-v-html -->
              <div class="reasoning-text" v-html="renderChatMarkdown(msg.reasoningContent)"></div>
            </div>

            <!-- 图片 -->
            <div v-if="msg.fileUrls?.length" class="msg-images">
              <img v-for="url in msg.fileUrls" :key="url" :src="url" class="msg-img" />
            </div>

            <!-- 正文 -->
            <div v-if="msg.role === 'user'" class="user-text">{{ msg.content }}</div>
            <!-- eslint-disable-next-line vue/no-v-html -->
            <div v-else class="ai-text" v-html="renderChatMarkdown(msg.content)"></div>

            <!-- AI 流式光标 -->
            <span v-if="msg.role === 'assistant' && sending && idx === messages.length - 1 && !msg.content" class="typing-cursor"></span>
          </div>
        </div>
      </div>

      <!-- 图片预览区 -->
      <div v-if="pendingImages.length > 0" class="pending-images">
        <div v-for="img in pendingImages" :key="img.id" class="pending-img-wrap">
          <img :src="img.preview" class="pending-img" />
          <button class="remove-img-btn" @click="removeImage(img.id)">×</button>
        </div>
      </div>

      <!-- 模型选择器（仅自由新会话且未发送消息时显示） -->
      <div v-if="canSelectModel && providers.length > 0" class="model-selector">
        <div class="selector-row">
          <label class="selector-label">模型</label>
          <div class="selector-controls">
            <select
              :value="selectedProvider"
              class="mobile-select"
              @change="onProviderChange(($event.target as HTMLSelectElement).value)"
            >
              <option v-for="opt in providerOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
            <select
              v-if="modeOptions.length > 1"
              :value="selectedMode"
              class="mobile-select"
              @change="selectedMode = ($event.target as HTMLSelectElement).value"
            >
              <option v-for="opt in modeOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
          </div>
        </div>
        <div v-if="showThinkingToggle" class="selector-row">
          <label class="selector-label">深度思考</label>
          <label class="toggle-switch">
            <input v-model="thinkingOn" type="checkbox" />
            <span class="toggle-slider"></span>
          </label>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <!-- 图片按钮（仅自由模式或应用模式支持视觉时显示） -->
        <button
          v-if="chatMode !== 'session' && supportsVision"
          class="input-icon-btn"
          @click="fileInputRef?.click()"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2"/>
            <circle cx="8.5" cy="8.5" r="1.5"/>
            <polyline points="21 15 16 10 5 21"/>
          </svg>
        </button>
        <input ref="fileInputRef" type="file" accept="image/*" multiple class="hidden-file" @change="onFileChange" />

        <textarea
          v-model="input"
          class="msg-input"
          placeholder="输入消息..."
          rows="1"
          :disabled="sending"
          @keydown="handleKeydown"
        ></textarea>

        <button
          v-if="sending"
          class="send-btn stop-btn"
          @click="stopSending"
        >
          <svg viewBox="0 0 24 24" fill="currentColor">
            <rect x="6" y="6" width="12" height="12" rx="2"/>
          </svg>
        </button>
        <button
          v-else
          class="send-btn"
          :disabled="!input.trim() && pendingImages.length === 0"
          @click="sendMessage()"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <line x1="22" y1="2" x2="11" y2="13"/>
            <polygon points="22 2 15 22 11 13 2 9 22 2"/>
          </svg>
        </button>
      </div>
    </template>

    <!-- 删除确认对话框 -->
    <div v-if="showDeleteConfirm" class="dialog-overlay" @click.self="showDeleteConfirm = false">
      <div class="dialog">
        <h3 class="dialog-title">删除会话</h3>
        <p class="dialog-msg">确认删除此会话？删除后不可恢复。</p>
        <div class="dialog-actions">
          <button class="dialog-cancel" @click="showDeleteConfirm = false">取消</button>
          <button class="dialog-confirm" @click="confirmDeleteConversation">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mobile-chat {
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
  flex-shrink: 0;
}

.back-btn,
.header-action-btn {
  background: none;
  border: none;
  padding: 4px;
  cursor: pointer;
  color: #667eea;
  display: flex;
  align-items: center;
  -webkit-tap-highlight-color: transparent;
}

.back-btn svg,
.header-action-btn svg {
  width: 24px;
  height: 24px;
}

.header-action-btn {
  color: #ff4d4f;
}

.header-title {
  flex: 1;
  text-align: center;
  font-size: 17px;
  font-weight: 600;
  color: #1a1a1a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding: 0 8px;
}

.header-placeholder {
  width: 32px;
}

/* ---- Messages ---- */
.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  -webkit-overflow-scrolling: touch;
}

.welcome-msg {
  background: #fff;
  border-radius: 16px;
  padding: 14px 16px;
  font-size: 15px;
  color: #333;
  line-height: 1.6;
  align-self: flex-start;
  max-width: 88%;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-self: flex-start;
}

.suggestion-btn {
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 20px;
  padding: 8px 14px;
  font-size: 14px;
  color: #555;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  transition: background 0.15s;
}

.suggestion-btn:active:not(:disabled) {
  background: #f0f0f0;
}

.empty-chat-hint {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #ccc;
}

.empty-chat-hint svg {
  width: 48px;
  height: 48px;
}

.empty-chat-hint p {
  font-size: 15px;
}

.msg-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.user-row {
  flex-direction: row-reverse;
}

.ai-row {
  flex-direction: row;
}

.ai-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  align-self: flex-end;
}

.msg-bubble {
  max-width: 80%;
  border-radius: 18px;
  padding: 10px 14px;
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
}

.user-bubble {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.ai-bubble {
  background: #fff;
  color: #1a1a1a;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}

.user-text {
  white-space: pre-wrap;
}

.ai-text :deep(p) { margin: 0 0 8px; }
.ai-text :deep(p:last-child) { margin-bottom: 0; }
.ai-text :deep(pre) {
  background: #f6f8fa;
  border-radius: 8px;
  padding: 10px;
  overflow-x: auto;
  font-size: 13px;
}
.ai-text :deep(code) { font-size: 13px; }
.ai-text :deep(ul), .ai-text :deep(ol) { padding-left: 20px; }

.reasoning-block {
  background: #f8f0ff;
  border-left: 3px solid #b39ddb;
  border-radius: 8px;
  padding: 8px 10px;
  margin-bottom: 8px;
}

.reasoning-label {
  font-size: 11px;
  color: #9c72c4;
  font-weight: 600;
  margin-bottom: 4px;
}

.reasoning-text {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
}

.msg-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 6px;
}

.msg-img {
  max-width: 160px;
  max-height: 160px;
  border-radius: 8px;
  object-fit: cover;
}

.typing-cursor {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #667eea;
  margin-left: 4px;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* ---- Pending images ---- */
.pending-images {
  display: flex;
  gap: 8px;
  padding: 8px 12px 0;
  overflow-x: auto;
  background: #fff;
  border-top: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.pending-img-wrap {
  position: relative;
  flex-shrink: 0;
}

.pending-img {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 8px;
}

.remove-img-btn {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(0,0,0,0.6);
  color: #fff;
  border: none;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

/* ---- Model selector ---- */
.model-selector {
  background: #fff;
  border-top: 1px solid #f0f0f0;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-shrink: 0;
}

.selector-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.selector-label {
  font-size: 13px;
  color: #666;
  flex-shrink: 0;
  min-width: 48px;
}

.selector-controls {
  display: flex;
  gap: 8px;
  flex: 1;
  flex-wrap: wrap;
}

.mobile-select {
  flex: 1;
  min-width: 0;
  padding: 6px 10px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  background: #f8f8f8;
  color: #333;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6'%3E%3Cpath d='M0 0l5 6 5-6z' fill='%23888'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 10px center;
  padding-right: 28px;
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 26px;
  cursor: pointer;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  inset: 0;
  background: #ccc;
  border-radius: 13px;
  transition: background 0.2s;
}

.toggle-slider::before {
  content: '';
  position: absolute;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #fff;
  top: 3px;
  left: 3px;
  transition: transform 0.2s;
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}

.toggle-switch input:checked + .toggle-slider {
  background: #667eea;
}

.toggle-switch input:checked + .toggle-slider::before {
  transform: translateX(18px);
}

/* ---- Input area ---- */
.input-area {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 10px 12px;
  padding-bottom: calc(10px + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1px solid #e8e8e8;
  flex-shrink: 0;
}

.hidden-file {
  display: none;
}

.input-icon-btn {
  width: 40px;
  height: 40px;
  border: none;
  background: #f0f0f0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #555;
  flex-shrink: 0;
  -webkit-tap-highlight-color: transparent;
}

.input-icon-btn svg {
  width: 20px;
  height: 20px;
}

.msg-input {
  flex: 1;
  border: 1px solid #e0e0e0;
  border-radius: 20px;
  padding: 10px 14px;
  font-size: 15px;
  font-family: inherit;
  resize: none;
  outline: none;
  background: #f8f8f8;
  color: #1a1a1a;
  min-height: 40px;
  max-height: 120px;
  overflow-y: auto;
  line-height: 1.4;
  transition: border-color 0.15s;
}

.msg-input:focus {
  border-color: #667eea;
  background: #fff;
}

.msg-input:disabled {
  opacity: 0.6;
}

.send-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  -webkit-tap-highlight-color: transparent;
  transition: opacity 0.15s;
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: default;
}

.send-btn svg {
  width: 18px;
  height: 18px;
}

.stop-btn {
  background: #ff4d4f;
}

/* ---- Center states ---- */
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

.retry-btn {
  padding: 10px 24px;
  border: 1px solid #ff4d4f;
  border-radius: 20px;
  background: none;
  color: #ff4d4f;
  font-size: 15px;
  cursor: pointer;
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

/* ---- Delete dialog ---- */
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  z-index: 100;
  padding-bottom: env(safe-area-inset-bottom);
}

.dialog {
  background: #fff;
  border-radius: 20px 20px 0 0;
  padding: 24px 20px 20px;
  width: 100%;
  max-width: 480px;
}

.dialog-title {
  font-size: 17px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 10px;
}

.dialog-msg {
  font-size: 15px;
  color: #666;
  margin: 0 0 24px;
}

.dialog-actions {
  display: flex;
  gap: 12px;
}

.dialog-cancel,
.dialog-confirm {
  flex: 1;
  padding: 12px;
  border-radius: 12px;
  border: none;
  font-size: 16px;
  cursor: pointer;
  font-weight: 500;
}

.dialog-cancel {
  background: #f0f0f0;
  color: #333;
}

.dialog-confirm {
  background: #ff4d4f;
  color: #fff;
}
</style>
