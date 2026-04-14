<script setup lang="ts">
/**
 * EmbedView — 嵌入网站聊天页（WEB_EMBED 集成）
 *
 * iframe URL 格式（由集成方后端生成）：
 *   https://chat.example.com/embed?iid=42&uid=13800000000&token=emb_xxxxxxxxxxxxxxxx
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { embedLoginApi } from '@/api/auth'
import { listLlmProviders, type LlmProviderOption } from '@/api/llmProviders'
import {
  listConversations,
  loadConversationMessages,
  type ChatMessage,
  type ConversationItem,
} from '@/api/conversations'
import { renderChatMarkdown } from '@/utils/chatMarkdown'

const route = useRoute()
const authStore = useAuthStore()

// ---- 状态 ----
type EmbedStatus = 'loading' | 'ready' | 'error'
const status = ref<EmbedStatus>('loading')
const errorMsg = ref('')

// 集成参数（挂载后填充）
let iid = 0

// ---- 聊天核心 ----
const providers = ref<LlmProviderOption[]>([])
const selectedProvider = ref('')
const selectedMode = ref('')
const integrationDefaultModel = ref<string | null>(null)
const messages = ref<ChatMessage[]>([])
const input = ref('')
const sending = ref(false)
const sessionId = ref<string | null>(null)
const thinkingOn = ref(false)      // Issue 5: 默认关闭
const msgScrollRef = ref<HTMLElement | null>(null)

// ---- 会话列表 ----
const conversations = ref<ConversationItem[]>([])
const showSidebar = ref(false)

// ---- localStorage 持久化 ----
function sessionKey() { return `embed_sid_${iid}` }
function saveSessionId(sid: string) {
  try { localStorage.setItem(sessionKey(), sid) } catch { /* ignore */ }
}
function restoreSessionId(): string | null {
  try { return localStorage.getItem(sessionKey()) } catch { return null }
}
function clearSessionId() {
  try { localStorage.removeItem(sessionKey()) } catch { /* ignore */ }
}

// ---- Provider 能力 ----
const currentProvider = computed(() =>
  providers.value.find(p => p.code === selectedProvider.value) ?? null,
)

const showThinkingToggle = computed(() => {
  const p = currentProvider.value
  if (!p?.supportsThinking) return false
  const caps = p.modeCapabilities
  if (!caps || Object.keys(caps).length === 0) return true
  const cap = caps[selectedMode.value]
  if (!cap) return false
  return cap.deepThinking === true
})

watch(showThinkingToggle, show => { if (!show) thinkingOn.value = false })

// ---- 初始化 ----
onMounted(async () => {
  const params = route.query
  iid = Number(params.iid)
  const uid = String(params.uid ?? '')
  const token = String(params.token ?? '')

  if (!iid || !uid || !token) {
    status.value = 'error'
    errorMsg.value = '缺少必要的嵌入参数（iid / uid / token）'
    return
  }

  // SSO 静默登录
  try {
    const result = await embedLoginApi({ integrationId: iid, userId: uid, token })
    authStore.setFromLogin(result)
    if (result.defaultModel) integrationDefaultModel.value = result.defaultModel
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    status.value = 'error'
    errorMsg.value = err.response?.data?.message || err.message || 'SSO 登录失败'
    return
  }

  // 加载模型列表
  try {
    providers.value = await listLlmProviders()
    selectDefaultModel()
  } catch { /* 忽略 */ }

  // 恢复上次会话（Issue 1: 同一用户不重新生成 session）
  const savedSid = restoreSessionId()
  if (savedSid) {
    try {
      const msgs = await loadConversationMessages(savedSid)
      sessionId.value = savedSid
      messages.value = msgs
    } catch {
      // 会话已失效，清除记录
      clearSessionId()
    }
  }

  // 加载会话列表
  try {
    conversations.value = await listConversations()
  } catch { /* 忽略 */ }

  status.value = 'ready'
  await scrollToBottom()
})

function selectDefaultModel() {
  const defaultModel = integrationDefaultModel.value
  if (defaultModel && providers.value.length > 0) {
    for (const p of providers.value) {
      if (p.modes && Object.keys(p.modes).includes(defaultModel)) {
        selectedProvider.value = p.code
        selectedMode.value = defaultModel
        return
      }
      if (p.modes) {
        for (const [modeKey, modelId] of Object.entries(p.modes)) {
          if (modelId === defaultModel) {
            selectedProvider.value = p.code
            selectedMode.value = modeKey
            return
          }
        }
      }
    }
  }
  if (providers.value.length > 0) {
    const first = providers.value[0]
    selectedProvider.value = first.code
    selectedMode.value = first.defaultMode ?? Object.keys(first.modes ?? {})[0] ?? ''
  }
}

// ---- 新会话 ----
function newConversation() {
  messages.value = []
  sessionId.value = null
  thinkingOn.value = false
  clearSessionId()
  showSidebar.value = false
}

// ---- 切换历史会话 ----
async function selectConversation(conv: ConversationItem) {
  if (conv.sessionId === sessionId.value) {
    showSidebar.value = false
    return
  }
  messages.value = []
  sessionId.value = conv.sessionId
  saveSessionId(conv.sessionId)
  showSidebar.value = false
  try {
    const msgs = await loadConversationMessages(conv.sessionId)
    messages.value = msgs
  } catch { /* 忽略 */ }
  await scrollToBottom()
}

// ---- 发送消息 ----
async function sendMessage() {
  const text = input.value.trim()
  if (!text || sending.value) return

  // Issue 1 fix: 客户端生成 sessionId，避免服务端每次重新生成
  const isNewSession = !sessionId.value
  if (isNewSession) {
    sessionId.value = crypto.randomUUID()
    saveSessionId(sessionId.value)
  }

  messages.value.push({ role: 'user', content: text })
  input.value = ''
  sending.value = true

  const assistantIdx = messages.value.length
  messages.value.push({ role: 'assistant', content: '', reasoningContent: '' })

  try {
    const payload: Record<string, unknown> = {
      provider: selectedProvider.value,
      messages: messages.value.slice(0, assistantIdx),
      stream: true,
      sessionId: sessionId.value,
      thinkingMode: thinkingOn.value,   // 明确传 false 时后端会向上游发 thinking:{type:"disabled"}
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
    // 新会话首次发送后刷新会话列表
    if (isNewSession) {
      try { conversations.value = await listConversations() } catch { /* 忽略 */ }
    }
  } catch (e: unknown) {
    const err = e as { message?: string }
    messages.value[assistantIdx].content = `错误：${err.message ?? '未知错误'}`
  } finally {
    sending.value = false
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    void sendMessage()
  }
}

async function scrollToBottom() {
  await nextTick()
  const el = msgScrollRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function scrollToStreamBottom() {
  await nextTick()
  const el = msgScrollRef.value
  if (!el) return
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 120) {
    el.scrollTop = el.scrollHeight
  }
}

function fmtTime(val: string | null | undefined): string {
  if (!val) return ''
  return val.replace('T', ' ').slice(0, 16)
}

// 代码块复制按钮处理
function onMdAreaClick(ev: MouseEvent) {
  const t = ev.target as HTMLElement | null
  if (!t?.closest('.md-copy-btn')) return
  ev.preventDefault()
  const block = t.closest('.md-code-block')
  const codeEl = block?.querySelector('pre.md-code-pre code') as HTMLElement | null
  const text = codeEl?.textContent ?? ''
  if (text) {
    void navigator.clipboard.writeText(text).then(
      () => { t.textContent = '已复制'; setTimeout(() => { t.textContent = '复制' }, 1500) },
      () => {},
    )
  }
}

const providerLabel = computed(() => {
  const p = providers.value.find(p => p.code === selectedProvider.value)
  if (!p) return selectedMode.value || selectedProvider.value
  return selectedMode.value ? `${p.displayName} · ${selectedMode.value}` : p.displayName
})
</script>

<template>
  <div class="embed-root">

    <!-- 加载中 -->
    <div v-if="status === 'loading'" class="state-center">
      <div class="spinner" />
      <p class="state-hint">正在初始化...</p>
    </div>

    <!-- 错误 -->
    <div v-else-if="status === 'error'" class="state-center state-error">
      <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" />
      </svg>
      <p>{{ errorMsg }}</p>
    </div>

    <!-- 聊天界面 -->
    <template v-else>

      <!-- 会话列表侧边栏（遮罩） -->
      <div v-if="showSidebar" class="sidebar-overlay" @click="showSidebar = false" />
      <aside :class="['sidebar', { 'sidebar--open': showSidebar }]">
        <div class="sidebar-header">
          <span class="sidebar-title">历史会话</span>
          <button class="icon-btn" title="关闭" @click="showSidebar = false">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="sidebar-list">
          <button
            v-for="conv in conversations"
            :key="conv.sessionId"
            :class="['conv-item', { 'conv-item--active': conv.sessionId === sessionId }]"
            @click="selectConversation(conv)"
          >
            <span class="conv-title">{{ conv.title || '无标题' }}</span>
            <span class="conv-time">{{ fmtTime(conv.lastMessageAt) }}</span>
          </button>
          <p v-if="conversations.length === 0" class="sidebar-empty">暂无历史会话</p>
        </div>
      </aside>

      <!-- 顶部栏 -->
      <header class="topbar">
        <button class="icon-btn" title="历史会话" @click="showSidebar = true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
          </svg>
        </button>
        <span class="topbar-logo">AI 助手</span>
        <span v-if="selectedMode" class="model-tag">{{ providerLabel }}</span>
        <button class="icon-btn new-btn" title="新会话" @click="newConversation">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
        </button>
      </header>

      <!-- 消息区域 -->
      <div ref="msgScrollRef" class="msg-list" @click="onMdAreaClick">
        <div v-if="messages.length === 0" class="msg-empty">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" stroke-width="1.2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          <p>有什么可以帮助您的吗？</p>
        </div>

        <template v-for="(msg, i) in messages" :key="i">
          <!-- 用户消息 -->
          <div v-if="msg.role === 'user'" class="msg msg--user">
            <div class="bubble bubble--user">
              <span style="white-space: pre-wrap; word-break: break-word;">{{ msg.content }}</span>
            </div>
          </div>

          <!-- 助手消息 -->
          <div v-else class="msg msg--ai">
            <div class="bubble bubble--ai">
              <!-- 思考过程 -->
              <details v-if="msg.reasoningContent" class="thinking-block">
                <summary class="thinking-summary">思考过程</summary>
                <pre class="thinking-body">{{ msg.reasoningContent }}</pre>
              </details>
              <!-- 打字动画（流式输出开始前） -->
              <span v-if="!msg.content && sending && i === messages.length - 1" class="typing">
                <span /><span /><span />
              </span>
              <!-- Markdown 渲染（Issue 2） -->
              <div
                v-else
                class="msg-md"
                v-html="renderChatMarkdown(msg.content)"
              />
            </div>
          </div>
        </template>
      </div>

      <!-- 输入区域（Issue 3: 与 ChatView 风格一致） -->
      <footer class="composer">
        <div class="composer-box">
          <textarea
            v-model="input"
            class="composer-textarea"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行..."
            rows="2"
            :disabled="sending"
            @keydown="handleKeydown"
          />
          <div class="composer-toolbar">
            <!-- 深度思考开关（Issue 5: 默认关闭） -->
            <div v-if="showThinkingToggle" class="thinking-toggle">
              <span class="thinking-label">深度思考</span>
              <button
                :class="['toggle-btn', { 'toggle-btn--on': thinkingOn }]"
                @click="thinkingOn = !thinkingOn"
              >
                <span class="toggle-knob" />
              </button>
            </div>
            <div style="flex: 1" />
            <!-- 发送按钮 -->
            <button
              class="send-btn"
              :disabled="sending || !input.trim()"
              @click="sendMessage"
            >
              <div v-if="sending" class="btn-spinner" />
              <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
              </svg>
              <span>{{ sending ? '发送中' : '发送' }}</span>
            </button>
          </div>
        </div>
      </footer>

    </template>
  </div>
</template>

<style scoped>
* { box-sizing: border-box; }

/* ===== 根容器 ===== */
.embed-root {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  background: #f5f6f8;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif;
  overflow: hidden;
  position: relative;
}

/* ===== 加载 / 错误 ===== */
.state-center {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: #6b7280;
  font-size: 14px;
}
.state-error { color: #ef4444; }
.spinner {
  width: 30px; height: 30px;
  border: 3px solid #e5e7eb;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.state-hint { font-size: 13px; color: #9ca3af; }

/* ===== 侧边栏 ===== */
.sidebar-overlay {
  position: fixed; inset: 0; z-index: 40;
  background: rgba(0, 0, 0, 0.3);
}
.sidebar {
  position: fixed; top: 0; left: 0; bottom: 0; z-index: 50;
  width: 260px;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex; flex-direction: column;
  transform: translateX(-100%);
  transition: transform 0.22s ease;
}
.sidebar--open { transform: translateX(0); }

.sidebar-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #f0f0f0;
}
.sidebar-title { font-weight: 600; font-size: 14px; color: #111827; }
.sidebar-list {
  flex: 1; overflow-y: auto; padding: 8px 0;
}
.conv-item {
  width: 100%; text-align: left;
  display: flex; flex-direction: column; gap: 2px;
  padding: 10px 16px;
  border: none; background: none; cursor: pointer;
  transition: background 0.12s;
}
.conv-item:hover { background: #f5f6f8; }
.conv-item--active { background: #eff6ff; }
.conv-title {
  font-size: 13px; color: #111827;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.conv-time { font-size: 11px; color: #9ca3af; }
.sidebar-empty { padding: 24px 16px; font-size: 13px; color: #9ca3af; text-align: center; }

/* ===== 顶部栏 ===== */
.topbar {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.topbar-logo { font-weight: 700; font-size: 15px; color: #111827; flex: 1; }
.model-tag {
  font-size: 11px; color: #6b7280;
  background: #f3f4f6; padding: 2px 8px; border-radius: 99px;
  max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.icon-btn {
  display: flex; align-items: center; justify-content: center;
  width: 32px; height: 32px; flex-shrink: 0;
  border: none; background: none; color: #6b7280;
  cursor: pointer; border-radius: 8px;
  transition: background 0.12s, color 0.12s;
}
.icon-btn:hover { background: #f3f4f6; color: #111827; }
.new-btn { color: #3b82f6; }
.new-btn:hover { background: #eff6ff; color: #2563eb; }

/* ===== 消息列表 ===== */
.msg-list {
  flex: 1; overflow-y: auto;
  padding: 16px 12px;
  display: flex; flex-direction: column; gap: 10px;
}
.msg-empty {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 10px; color: #9ca3af; font-size: 14px;
  padding: 40px 0;
}
.msg { display: flex; }
.msg--user { justify-content: flex-end; }
.msg--ai  { justify-content: flex-start; }
.bubble {
  max-width: 82%;
  padding: 9px 13px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.6;
}
.bubble--user {
  background: #2563eb; color: #fff;
  border-bottom-right-radius: 4px;
}
.bubble--ai {
  background: #fff; color: #111827;
  border: 1px solid #e5e7eb;
  border-bottom-left-radius: 4px;
}

/* 思考过程 */
.thinking-block {
  margin-bottom: 8px;
  border: 1px solid #e5e7eb; border-radius: 8px;
  background: #f9fafb; overflow: hidden;
}
.thinking-summary {
  padding: 6px 10px; cursor: pointer;
  font-size: 12px; font-weight: 600; color: #6b7280;
  user-select: none; list-style: none;
}
.thinking-summary::-webkit-details-marker { display: none; }
.thinking-summary::before { content: '▶ '; font-size: 10px; }
details[open] .thinking-summary::before { content: '▼ '; }
.thinking-body {
  padding: 8px 12px 12px; margin: 0;
  font-size: 12px; color: #6b7280;
  white-space: pre-wrap; line-height: 1.6;
  border-top: 1px solid #e5e7eb;
  font-family: inherit;
}

/* 打字动画 */
.typing {
  display: inline-flex; gap: 4px; align-items: center; padding: 4px 0;
}
.typing span {
  width: 6px; height: 6px; background: #9ca3af;
  border-radius: 50%; animation: blink 1.2s ease-in-out infinite;
}
.typing span:nth-child(2) { animation-delay: 0.2s; }
.typing span:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink { 0%,80%,100% { opacity: 0.25; } 40% { opacity: 1; } }

/* ===== 输入区域（Issue 3: 与 ChatView 风格一致） ===== */
.composer {
  flex-shrink: 0;
  padding: 10px 12px;
  background: #f5f6f8;
}
.composer-box {
  background: #fff;
  border: 1.5px solid #e5e7eb;
  border-radius: 14px;
  overflow: hidden;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.composer-box:focus-within {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}
.composer-textarea {
  width: 100%; resize: none;
  border: none; outline: none;
  padding: 12px 14px 8px;
  font-size: 14px; font-family: inherit; line-height: 1.55;
  color: #111827; background: transparent;
  min-height: 52px; max-height: 160px;
  overflow-y: auto;
}
.composer-textarea::placeholder { color: #9ca3af; }
.composer-textarea:disabled { opacity: 0.6; cursor: not-allowed; }
.composer-toolbar {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 10px 10px;
}

/* 深度思考开关 */
.thinking-toggle {
  display: flex; align-items: center; gap: 6px;
}
.thinking-label {
  font-size: 12px; color: #6b7280; user-select: none;
}
.toggle-btn {
  position: relative; width: 32px; height: 18px;
  border: none; padding: 0; cursor: pointer;
  border-radius: 9px; background: #d1d5db;
  transition: background 0.2s;
  flex-shrink: 0;
}
.toggle-btn--on { background: #3b82f6; }
.toggle-knob {
  position: absolute; top: 2px; left: 2px;
  width: 14px; height: 14px;
  border-radius: 50%; background: #fff;
  transition: left 0.2s;
  box-shadow: 0 1px 3px rgba(0,0,0,.15);
}
.toggle-btn--on .toggle-knob { left: 16px; }

/* 发送按钮 */
.send-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 16px; border: none; border-radius: 8px;
  background: #2563eb; color: #fff;
  font-size: 13px; font-weight: 500; cursor: pointer;
  transition: background 0.15s;
  flex-shrink: 0;
}
.send-btn:hover:not(:disabled) { background: #1d4ed8; }
.send-btn:disabled { background: #93c5fd; cursor: not-allowed; }
.btn-spinner {
  width: 14px; height: 14px;
  border: 2px solid rgba(255,255,255,0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  flex-shrink: 0;
}

/* ===== Markdown 渲染（Issue 2） ===== */
.msg-md { word-break: break-word; line-height: 1.65; font-size: 14px; }
.msg-md :deep(p) { margin: 0 0 0.55em; }
.msg-md :deep(p:last-child) { margin-bottom: 0; }
.msg-md :deep(ul), .msg-md :deep(ol) { margin: 0.4em 0 0.4em 1.2em; padding: 0; }
.msg-md :deep(li) { margin: 0.15em 0; }
.msg-md :deep(h1), .msg-md :deep(h2) { font-size: 1.1em; font-weight: 700; margin: 0.6em 0 0.3em; }
.msg-md :deep(h3), .msg-md :deep(h4) { font-size: 1.0em; font-weight: 600; margin: 0.5em 0 0.25em; }
.msg-md :deep(blockquote) {
  margin: 0.5em 0; padding: 0.3em 0 0.3em 0.8em;
  border-left: 3px solid #d1d5db; color: #4b5563;
}
.msg-md :deep(a) { color: #2563eb; text-decoration: underline; }
.msg-md :deep(p > code), .msg-md :deep(li > code), .msg-md :deep(td > code) {
  background: #f3f4f6; padding: 0.1em 0.35em; border-radius: 4px;
  font-family: ui-monospace, monospace; font-size: 0.88em;
}
.msg-md :deep(.md-code-block) {
  margin: 0.65em 0; border: 1px solid #e5e7eb; border-radius: 10px;
  overflow: hidden; background: #fff; box-shadow: 0 1px 2px rgba(15,23,42,.05);
}
.msg-md :deep(.md-code-head) {
  display: flex; align-items: center; justify-content: space-between;
  gap: 8px; padding: 5px 10px; background: #f3f4f6;
  border-bottom: 1px solid #e5e7eb; font-size: 12px;
}
.msg-md :deep(.md-code-lang) {
  color: #6b7280; font-family: ui-monospace, monospace;
}
.msg-md :deep(.md-copy-btn) {
  flex-shrink: 0; margin: 0; padding: 2px 8px;
  font-size: 12px; color: #374151; background: #fff;
  border: 1px solid #d1d5db; border-radius: 5px; cursor: pointer;
}
.msg-md :deep(.md-copy-btn:hover) { background: #f9fafb; border-color: #9ca3af; }
.msg-md :deep(.md-code-pre) {
  margin: 0; padding: 10px 12px; overflow-x: auto;
  font-size: 12px; line-height: 1.5; background: #fafafa;
}
.msg-md :deep(.md-code-pre code) { font-family: ui-monospace, monospace; }
.msg-md :deep(table) { border-collapse: collapse; margin: 0.5em 0; font-size: 0.92em; width: 100%; }
.msg-md :deep(th), .msg-md :deep(td) { border: 1px solid #e5e7eb; padding: 0.3em 0.55em; }
.msg-md :deep(th) { background: #f9fafb; }

/* ===== 移动端适配 ===== */
@media (max-width: 640px) {
  .topbar { padding: 8px 10px; }
  .msg-list { padding: 12px 8px; }
  .bubble { max-width: 88%; font-size: 14px; }
  .composer { padding: 8px; }
  .send-btn span { display: none; }
  .send-btn { padding: 6px 12px; }
}
</style>
