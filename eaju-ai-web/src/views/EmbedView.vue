<script setup lang="ts">
/**
 * EmbedView — 嵌入网站聊天页（WEB_EMBED 集成）
 *
 * iframe URL 格式（由集成方后端生成）：
 *   https://chat.example.com/embed?iid=42&uid=13800000000&token=emb_xxxxxxxxxxxxxxxx
 *
 * 语音 JSBridge 协议（Native App → WebView postMessage）：
 *   { type: 'VOICE_PERMISSION_DENIED' }         麦克风权限被拒绝
 *   { type: 'VOICE_RESULT', text: '...', isFinal: true }  识别结果（isFinal=true 时结束录音）
 *   { type: 'VOICE_STOPPED' }                   原生侧主动停止录音
 *
 * WebView → Native App postMessage（可通过 window.parent.postMessage 或 AppBridge）：
 *   { type: 'START_VOICE_RECOGNITION' }
 *   { type: 'STOP_VOICE_RECOGNITION' }
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { embedLoginApi, appEmbedLoginApi } from '@/api/auth'
import { listLlmProviders, type LlmProviderOption } from '@/api/llmProviders'
import { getWelcomeConfigByApp, type WelcomeConfig } from '@/api/welcome'
import {
  deleteConversation,
  listConversations,
  loadConversationMessages,
  type ChatMessage,
  type ConversationItem,
} from '@/api/conversations'
import { chatStreamFetch, type StreamDelta } from '@/api/chat'
import { renderChatMarkdown } from '@/utils/chatMarkdown'

const route = useRoute()
const authStore = useAuthStore()

// ---- 状态 ----
type EmbedStatus = 'loading' | 'ready' | 'error'
const status = ref<EmbedStatus>('loading')
const errorMsg = ref('')

// 集成参数（挂载后填充）
let iid = 0       // WEB_EMBED 集成 ID
let aid = 0       // 应用管理嵌入 AppID
const integrationName = ref('AI 助手')

// ---- 用户上下文（用于测试展示） ----
const userContext = ref<Record<string, unknown> | null>(null)
const showUserContext = ref(false)

// ---- 辅助函数：检查是否为错误状态 ----
const isErrorStatus = () => status.value === 'error'

// ---- 聊天核心 ----
const providers = ref<LlmProviderOption[]>([])
const selectedProvider = ref('')
const selectedMode = ref('')
const integrationDefaultModel = ref<string | null>(null)
const messages = ref<ChatMessage[]>([])
const input = ref('')
const sending = ref(false)
const sessionId = ref<string | null>(null)
const thinkingOn = ref(false)
const msgScrollRef = ref<HTMLElement | null>(null)

// ---- 会话列表 ----
const conversations = ref<ConversationItem[]>([])
const showSidebar = ref(false)
const deletingSessionId = ref<string | null>(null)

// ---- 开场引导配置 ----
const welcomeConfig = ref<WelcomeConfig | null>(null)
const welcomeLoaded = ref(false)
const suggestionsSending = ref(false)

// ---- 平台检测 ----
const isMobile = /Android|iPhone|iPad|iPod|HarmonyOS|HMOS/i.test(navigator.userAgent)

// ---- 语音录音 ----
type Platform = 'android' | 'ios' | 'harmony' | 'wechat' | 'web'
const isRecording = ref(false)
const voiceHint = ref('')
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let recognition: any = null
let mediaRecorder: MediaRecorder | null = null
let voiceBridgeCleanup: (() => void) | null = null

/** 兼容非安全上下文（HTTP IP 访问）的 UUID v4 生成 */
function generateUUID(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

// ---- localStorage 持久化 ----
function sessionKey() { return aid ? `embed_app_sid_${aid}` : `embed_sid_${iid}` }
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

const currentModeCapability = computed(() => {
  const p = currentProvider.value
  if (!p?.modeCapabilities || !selectedMode.value) return null
  return p.modeCapabilities[selectedMode.value] || null
})

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
  aid = Number(params.aid ?? 0)
  iid = Number(params.iid ?? 0)
  // phone 为主，兼容旧版 uid 参数
  const phone = String(params.phone ?? params.uid ?? '')
  const username = params.username ? String(params.username) : undefined

  // 保留参数之外的所有 URL 参数自动作为 extraContext 透传
  const RESERVED_KEYS = new Set(['aid', 'iid', 'phone', 'uid', 'username', 'token'])
  const extraContext: Record<string, string> = {}
  for (const [key, val] of Object.entries(params)) {
    if (!RESERVED_KEYS.has(key) && val != null) {
      extraContext[key] = String(val)
    }
  }

  if (aid) {
    // 应用管理嵌入方式：通过 aid + phone 登录，无需 token
    if (!phone) {
      status.value = 'error'
      errorMsg.value = '缺少必要的嵌入参数（aid / phone）'
      return
    }
    try {
      const result = await appEmbedLoginApi({
        appId: aid,
        userId: phone,
        username,
        extraContext: Object.keys(extraContext).length > 0 ? extraContext : undefined,
      })
      authStore.setFromLogin(result)
      if (result.defaultModel) integrationDefaultModel.value = result.defaultModel
      if (result.integrationName) integrationName.value = result.integrationName
      // 保存用户上下文用于测试展示
      if (result.userContext && Object.keys(result.userContext).length > 0) {
        userContext.value = result.userContext
      }
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } }; message?: string }
      status.value = 'error'
      errorMsg.value = err.response?.data?.message || err.message || '应用登录失败'
      // 登录失败时，页面上的功能均不可用
      return
    }
  } else if (iid) {
    // WEB_EMBED 集成方式：通过 iid + phone + token 登录
    const token = String(params.token ?? '')
    if (!phone || !token) {
      status.value = 'error'
      errorMsg.value = '缺少必要的嵌入参数（iid / phone / token）'
      return
    }
    try {
      const result = await embedLoginApi({ integrationId: iid, userId: phone, token, username })
      authStore.setFromLogin(result)
      if (result.defaultModel) integrationDefaultModel.value = result.defaultModel
      if (result.integrationName) integrationName.value = result.integrationName
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } }; message?: string }
      status.value = 'error'
      errorMsg.value = err.response?.data?.message || err.message || 'SSO 登录失败'
      return
    }
  } else {
    status.value = 'error'
    errorMsg.value = '缺少必要的嵌入参数（aid 或 iid）'
    return
  }

  try {
    providers.value = await listLlmProviders()
    selectDefaultModel()
  } catch { /* 忽略 */ }

  const savedSid = restoreSessionId()
  if (savedSid) {
    try {
      const msgs = await loadConversationMessages(savedSid)
      sessionId.value = savedSid
      messages.value = msgs
    } catch {
      clearSessionId()
    }
  }

  try {
    conversations.value = await listConversations()
  } catch { /* 忽略 */ }

  // 始终加载开场引导配置（永远显示在第一个位置）
  if (aid) {
    try {
      welcomeConfig.value = await getWelcomeConfigByApp(aid)
      welcomeLoaded.value = true
    } catch { /* 忽略，不显示错误 */ }
  }

  status.value = 'ready'
  await scrollToBottom()
})

onUnmounted(() => {
  stopVoice()
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
  // 新会话时重新加载开场引导
  if (aid) {
    getWelcomeConfigByApp(aid).then(config => {
      welcomeConfig.value = config
    }).catch(() => { /* 忽略 */ })
  }
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
  deletingSessionId.value = null
  try {
    const msgs = await loadConversationMessages(conv.sessionId)
    messages.value = msgs
  } catch { /* 忽略 */ }
  await scrollToBottom()
}

// ---- 删除会话 ----
function askDeleteConversation(conv: ConversationItem, e: Event) {
  e.stopPropagation()
  deletingSessionId.value = conv.sessionId
}

async function confirmDelete(sid: string) {
  try {
    await deleteConversation(sid)
    conversations.value = conversations.value.filter(c => c.sessionId !== sid)
    if (sessionId.value === sid) {
      messages.value = []
      sessionId.value = null
      clearSessionId()
    }
  } catch { /* 忽略 */ }
  deletingSessionId.value = null
}

// ---- 发送消息 ----
async function sendMessage(text?: string) {
  const msgText = text ?? input.value.trim()
  
  if (!msgText || sending.value) {
    return
  }
  
  // 检查是否有选中的 provider
  if (!selectedProvider.value) {
    console.error('No provider selected')
    return
  }

  const isNewSession = !sessionId.value
  if (isNewSession) {
    sessionId.value = generateUUID()
    saveSessionId(sessionId.value)
  }

  messages.value.push({ role: 'user', content: msgText })
  if (!text) {
    input.value = ''
  }
  sending.value = true

  const assistantIdx = messages.value.length
  messages.value.push({ role: 'assistant', content: '', reasoningContent: '' })

  const payload = {
    provider: selectedProvider.value,
    messages: messages.value.slice(0, assistantIdx),
    stream: currentModeCapability.value?.streamOutput !== false,
    sessionId: sessionId.value ?? undefined,
    thinkingMode: thinkingOn.value,
    ...(selectedMode.value ? { mode: selectedMode.value } : {}),
  }

  try {
    await chatStreamFetch(
      authStore.token,
      payload,
      (delta: StreamDelta) => {
        if (delta.content) {
          messages.value[assistantIdx].content += delta.content
        }
        if (thinkingOn.value && delta.reasoning) {
          messages.value[assistantIdx].reasoningContent =
            (messages.value[assistantIdx].reasoningContent ?? '') + delta.reasoning
        }
        void scrollToStreamBottom()
      },
      () => {
        // onDone
        if (isNewSession) {
          listConversations().then(list => { conversations.value = list }).catch(() => {})
        }
      },
      (e: Error) => {
        messages.value[assistantIdx].content = `错误：${e.message}`
      },
    )
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

// ---- 推荐问题点击 ----
function handleSuggestionClick(question: string, e: MouseEvent) {
  if (suggestionsSending.value || sending.value) {
    return
  }
  ;(e.currentTarget as HTMLElement).blur()
  suggestionsSending.value = true
  void sendMessage(question).finally(() => {
    suggestionsSending.value = false
  })
}

// ---- 语音录音 ----
function detectPlatform(): Platform {
  const ua = navigator.userAgent.toLowerCase()
  if (ua.includes('micromessenger')) return 'wechat'
  if (ua.includes('harmonyos') || ua.includes('hmos')) return 'harmony'
  if (/(iphone|ipad|ipod)/.test(ua)) return 'ios'
  if (/android/.test(ua)) return 'android'
  return 'web'
}

/** 通过 JSBridge/postMessage 与 Native App 通信的语音录音 */
function startVoiceViaBridge(platform: Platform) {
  isRecording.value = true
  voiceHint.value = '正在录音...'

  const handler = (e: MessageEvent) => {
    let data: Record<string, unknown>
    try { data = typeof e.data === 'string' ? JSON.parse(e.data) : e.data } catch { return }
    if (!data || typeof data !== 'object') return

    if (data.type === 'VOICE_PERMISSION_DENIED') {
      isRecording.value = false
      voiceHint.value = '麦克风权限已拒绝'
      setTimeout(() => { voiceHint.value = '' }, 2500)
      cleanup()
    } else if (data.type === 'VOICE_RESULT' && data.text) {
      // 累加到输入框（interim 结果可能触发多次）
      if (data.isFinal) {
        input.value = (input.value + String(data.text)).trim()
        isRecording.value = false
        voiceHint.value = ''
        cleanup()
      } else {
        // 暂时展示 interim 结果
        voiceHint.value = String(data.text)
      }
    } else if (data.type === 'VOICE_STOPPED') {
      isRecording.value = false
      voiceHint.value = ''
      cleanup()
    }
  }

  function cleanup() {
    window.removeEventListener('message', handler)
    voiceBridgeCleanup = null
  }
  voiceBridgeCleanup = cleanup
  window.addEventListener('message', handler)

  // 发送开始录音指令
  const msg = JSON.stringify({ type: 'START_VOICE_RECOGNITION' })
  if (platform === 'wechat') {
    // 小程序 web-view 通过 parent.postMessage 传给小程序侧
    window.parent?.postMessage(msg, '*')
  } else if (platform === 'ios') {
    // iOS WKWebView JSBridge
    const win = window as unknown as { webkit?: { messageHandlers?: { AppBridge?: { postMessage: (v: unknown) => void } } } }
    win.webkit?.messageHandlers?.AppBridge?.postMessage({ action: 'startVoice' })
  } else {
    // Android / HarmonyOS AppBridge
    const win = window as unknown as { AppBridge?: { startVoiceRecognition?: () => void } }
    win.AppBridge?.startVoiceRecognition?.()
  }
}

/** Web Speech API (Chrome / Edge / Android Chrome 均支持) */
function startWebSpeechRecognition() {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const w = window as any
  const SpeechRec = w.SpeechRecognition ?? w.webkitSpeechRecognition
  if (!SpeechRec) return false

  recognition = new SpeechRec()
  recognition.lang = 'zh-CN'
  recognition.continuous = false
  recognition.interimResults = true

  recognition.onstart = () => { isRecording.value = true; voiceHint.value = '正在聆听...' }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  recognition.onresult = (e: any) => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const transcript = Array.from(e.results as any[])
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      .map((r: any) => r[0].transcript)
      .join('')
    if (e.results[e.results.length - 1]?.isFinal) {
      input.value = transcript
      voiceHint.value = ''
    } else {
      voiceHint.value = transcript
    }
  }
  recognition.onend = () => { isRecording.value = false; voiceHint.value = '' }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  recognition.onerror = (e: any) => {
    isRecording.value = false
    voiceHint.value = e.error === 'not-allowed' ? '麦克风权限被拒绝' : '语音识别失败'
    setTimeout(() => { voiceHint.value = '' }, 2500)
  }
  recognition.start()
  return true
}

/** 是否在安全上下文（HTTPS 或 localhost）中运行 */
function isSecureContext(): boolean {
  return window.isSecureContext === true ||
    location.protocol === 'https:' ||
    location.hostname === 'localhost' ||
    location.hostname === '127.0.0.1'
}

/** MediaRecorder 兜底（无 STT，仅录音提示） */
async function startMediaRecorderFallback() {
  // getUserMedia 在非安全上下文（HTTP + IP）下不可用
  if (!isSecureContext() || !navigator.mediaDevices?.getUserMedia) {
    voiceHint.value = '语音需要 HTTPS 或在 App 内使用'
    setTimeout(() => { voiceHint.value = '' }, 3000)
    return
  }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    isRecording.value = true
    voiceHint.value = '录音中，再次点击停止'
    const chunks: BlobPart[] = []
    mediaRecorder = new MediaRecorder(stream)
    mediaRecorder.ondataavailable = (e) => chunks.push(e.data)
    mediaRecorder.onstop = () => {
      stream.getTracks().forEach(t => t.stop())
      isRecording.value = false
      voiceHint.value = ''
    }
    mediaRecorder.start()
  } catch (e: unknown) {
    const err = e as { name?: string }
    isRecording.value = false
    voiceHint.value = err?.name === 'NotAllowedError' ? '麦克风权限被拒绝' : '无法访问麦克风'
    setTimeout(() => { voiceHint.value = '' }, 2500)
  }
}

async function toggleVoice() {
  if (isRecording.value) {
    stopVoice()
    return
  }
  const platform = detectPlatform()

  // 微信小程序 webview
  if (platform === 'wechat') {
    startVoiceViaBridge('wechat')
    return
  }
  // iOS WKWebView Bridge
  const win = window as unknown as {
    webkit?: { messageHandlers?: { AppBridge?: unknown } }
    AppBridge?: unknown
  }
  if (platform === 'ios' && win.webkit?.messageHandlers?.AppBridge) {
    startVoiceViaBridge('ios')
    return
  }
  // Android / HarmonyOS Bridge
  if ((platform === 'android' || platform === 'harmony') && win.AppBridge) {
    startVoiceViaBridge(platform)
    return
  }
  // Web Speech API（优先）
  if (startWebSpeechRecognition()) return
  // MediaRecorder 兜底
  await startMediaRecorderFallback()
}

function stopVoice() {
  if (recognition) {
    try { recognition.stop() } catch { /* ignore */ }
    recognition = null
  }
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    try { mediaRecorder.stop() } catch { /* ignore */ }
    mediaRecorder = null
  }
  if (voiceBridgeCleanup) {
    const platform = detectPlatform()
    const msg = JSON.stringify({ type: 'STOP_VOICE_RECOGNITION' })
    if (platform === 'wechat') {
      window.parent?.postMessage(msg, '*')
    } else if (platform === 'ios') {
      const w = window as unknown as { webkit?: { messageHandlers?: { AppBridge?: { postMessage: (v: unknown) => void } } } }
      w.webkit?.messageHandlers?.AppBridge?.postMessage({ action: 'stopVoice' })
    } else {
      const w = window as unknown as { AppBridge?: { stopVoiceRecognition?: () => void } }
      w.AppBridge?.stopVoiceRecognition?.()
    }
    voiceBridgeCleanup()
  }
  isRecording.value = false
  voiceHint.value = ''
}

// ---- 滚动 ----
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
</script>

<template>
  <div class="embed-root">

    <!-- 加载中 -->
    <div v-if="status === 'loading'" class="state-center">
      <div class="spinner" />
      <p class="state-hint">正在初始化...</p>
    </div>

    <!-- 错误 -->
    <div v-else-if="isErrorStatus()" class="state-center state-error">
      <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" />
      </svg>
      <p>{{ errorMsg }}</p>
    </div>

    <!-- 聊天界面 -->
    <template v-else>

      <!-- 侧边栏遮罩 -->
      <div v-if="showSidebar" class="sidebar-overlay" @click="showSidebar = false" />

      <!-- 历史会话侧边栏 -->
      <aside :class="['sidebar', { 'sidebar--open': showSidebar }]">
        <div class="sidebar-header">
          <span class="sidebar-title">历史会话</span>
          <button class="icon-btn" title="关闭" @click="showSidebar = false">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>

        <!-- 新建会话按钮 -->
        <button class="new-conv-btn" @click="newConversation">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
            <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          新建会话
        </button>

        <div class="sidebar-list">
          <div
            v-for="conv in conversations"
            :key="conv.sessionId"
            :class="['conv-item', { 'conv-item--active': conv.sessionId === sessionId }]"
          >
            <!-- 确认删除状态 -->
            <template v-if="deletingSessionId === conv.sessionId">
              <span class="conv-del-confirm">确认删除？</span>
              <div class="conv-del-actions">
                <button class="conv-del-yes" @click="confirmDelete(conv.sessionId)">删除</button>
                <button class="conv-del-no" @click="deletingSessionId = null">取消</button>
              </div>
            </template>
            <!-- 正常展示 -->
            <template v-else>
              <div class="conv-main" @click="selectConversation(conv)">
                <span class="conv-title">{{ conv.title || '无标题' }}</span>
                <span class="conv-time">{{ fmtTime(conv.lastMessageAt) }}</span>
              </div>
              <button class="conv-del-btn" title="删除会话" @click="askDeleteConversation(conv, $event)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="3 6 5 6 21 6"/>
                  <path d="M19 6l-1 14H6L5 6"/>
                  <path d="M10 11v6M14 11v6"/>
                  <path d="M9 6V4h6v2"/>
                </svg>
              </button>
            </template>
          </div>
          <p v-if="conversations.length === 0" class="sidebar-empty">暂无历史会话</p>
        </div>
      </aside>

      <!-- 顶部栏：仅展示 集成名称 + 历史会话 入口 + 用户上下文测试 -->
      <header class="topbar">
        <button class="icon-btn" title="历史会话" @click="showSidebar = true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
          </svg>
        </button>
        <span class="topbar-logo">{{ integrationName }}</span>
        <!-- 用户上下文测试按钮（仅当有用户数据时显示） -->
        <button
          v-if="userContext && Object.keys(userContext).length > 0"
          class="icon-btn"
          :class="{ 'icon-btn--active': showUserContext }"
          title="用户上下文（测试）"
          @click="showUserContext = !showUserContext"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
        </button>
      </header>

      <!-- 用户上下文展示面板（测试用） -->
      <div v-if="showUserContext && userContext" class="user-context-panel">
        <div class="user-context-header">
          <span class="user-context-title">用户上下文数据（缓存于 Redis）</span>
          <button class="icon-btn" title="关闭" @click="showUserContext = false">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="user-context-list">
          <div v-for="(value, key) in userContext" :key="key" class="user-context-item">
            <span class="user-context-key">{{ key }}:</span>
            <span class="user-context-value">{{ typeof value === 'object' ? JSON.stringify(value) : String(value) }}</span>
          </div>
        </div>
      </div>

      <!-- 消息区域 -->
      <div ref="msgScrollRef" class="msg-list" @click="onMdAreaClick">
        
        <!-- 开场引导区域（永远显示在第一个位置） -->
        <div v-if="welcomeConfig && welcomeConfig.welcomeText" class="msg msg--ai">
          <div class="bubble bubble--ai">
            <p class="welcome-text">{{ welcomeConfig.welcomeText }}</p>
            <!-- 推荐问题列表 -->
            <div v-if="welcomeConfig.suggestions && welcomeConfig.suggestions.length > 0" class="suggestions-list">
              <button
                v-for="(suggestion, index) in welcomeConfig.suggestions"
                :key="index"
                :class="['suggestion-btn', { 'suggestion-btn--disabled': sending }]"
                :disabled="sending || suggestionsSending"
                @click="handleSuggestionClick(suggestion, $event)"
              >
                {{ suggestion }}
              </button>
            </div>
          </div>
        </div>

        <!-- 空状态（没有开场白时显示默认提示） -->
        <div v-else-if="messages.length === 0" class="msg-empty">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" stroke-width="1.2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          <p>有什么可以帮助您的吗？</p>
        </div>

        <template v-for="(msg, i) in messages" :key="i">
          <div v-if="msg.role === 'user'" class="msg msg--user">
            <div class="bubble bubble--user">
              <span style="white-space: pre-wrap; word-break: break-word;">{{ msg.content }}</span>
            </div>
          </div>
          <div v-else class="msg msg--ai">
            <div class="bubble bubble--ai">
              <details v-if="msg.reasoningContent" class="thinking-block">
                <summary class="thinking-summary">思考过程</summary>
                <pre class="thinking-body">{{ msg.reasoningContent }}</pre>
              </details>
              <span v-if="!msg.content && sending && i === messages.length - 1" class="typing">
                <span /><span /><span />
              </span>
              <div v-else class="msg-md" v-html="renderChatMarkdown(msg.content)" />
            </div>
          </div>
        </template>
      </div>

      <!-- 输入区域 -->
      <footer class="composer" :class="{ 'composer--disabled': isErrorStatus() }">
        <!-- 语音提示条 -->
        <div v-if="voiceHint" class="voice-hint">
          <span class="voice-hint-dot" :class="{ 'voice-hint-dot--pulse': isRecording }" />
          {{ voiceHint }}
        </div>
        <div class="composer-box">
          <textarea
            v-model="input"
            class="composer-textarea"
            :placeholder="isErrorStatus() ? '登录失败，功能不可用' : (isMobile ? '请输入内容...' : '输入消息，Enter 发送，Shift+Enter 换行...')"
            rows="2"
            :disabled="sending || isErrorStatus()"
            @keydown="handleKeydown"
          />
          <div class="composer-toolbar">
            <!-- 深度思考开关 -->
            <div v-if="showThinkingToggle" class="thinking-toggle">
              <span class="thinking-label">深度思考</span>
              <button
                :class="['toggle-btn', { 'toggle-btn--on': thinkingOn }]"
                :disabled="isErrorStatus()"
                @click="thinkingOn = !thinkingOn"
              >
                <span class="toggle-knob" />
              </button>
            </div>
            <div style="flex: 1" />
            <!-- 语音按钮 -->
            <button
              :class="['voice-btn', { 'voice-btn--recording': isRecording }]"
              :title="isRecording ? '停止录音' : '语音输入'"
              :disabled="sending || isErrorStatus()"
              @click="toggleVoice"
            >
              <svg v-if="!isRecording" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
                <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                <line x1="12" y1="19" x2="12" y2="23"/>
                <line x1="8" y1="23" x2="16" y2="23"/>
              </svg>
              <svg v-else width="17" height="17" viewBox="0 0 24 24" fill="currentColor">
                <rect x="6" y="6" width="12" height="12" rx="2"/>
              </svg>
            </button>
            <!-- 发送按钮 -->
            <button
              class="send-btn"
              :disabled="sending || !input.trim() || isErrorStatus()"
              @click="() => sendMessage()"
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
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 14px; color: #6b7280; font-size: 14px;
}
.state-error { color: #ef4444; }
.spinner {
  width: 30px; height: 30px;
  border: 3px solid #e5e7eb; border-top-color: #3b82f6;
  border-radius: 50%; animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.state-hint { font-size: 13px; color: #9ca3af; }

/* ===== 侧边栏 ===== */
.sidebar-overlay {
  position: fixed; inset: 0; z-index: 40;
  background: rgba(0,0,0,.3);
}
.sidebar {
  position: fixed; top: 0; left: 0; bottom: 0; z-index: 50;
  width: 260px; background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex; flex-direction: column;
  transform: translateX(-100%); transition: transform 0.22s ease;
}
.sidebar--open { transform: translateX(0); }

.sidebar-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px; border-bottom: 1px solid #f0f0f0; flex-shrink: 0;
}
.sidebar-title { font-weight: 600; font-size: 14px; color: #111827; }

/* 新建会话按钮（侧边栏顶部） */
.new-conv-btn {
  display: flex; align-items: center; gap: 7px;
  width: calc(100% - 24px); margin: 10px 12px 4px;
  padding: 9px 14px; border-radius: 10px;
  border: 1.5px dashed #93c5fd; background: #eff6ff;
  color: #2563eb; font-size: 13px; font-weight: 500;
  cursor: pointer; transition: background 0.12s, border-color 0.12s;
}
.new-conv-btn:hover { background: #dbeafe; border-color: #60a5fa; }

.sidebar-list { flex: 1; overflow-y: auto; padding: 4px 0 8px; }

.conv-item {
  display: flex; align-items: center;
  padding: 0 8px 0 4px; min-height: 52px;
  border-radius: 8px; margin: 0 6px 2px;
  cursor: pointer; transition: background 0.12s;
}
.conv-item:hover { background: #f5f6f8; }
.conv-item--active { background: #eff6ff; }

.conv-main {
  flex: 1; min-width: 0;
  display: flex; flex-direction: column; gap: 2px;
  padding: 10px 6px 10px 10px;
}
.conv-title {
  font-size: 13px; color: #111827;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.conv-time { font-size: 11px; color: #9ca3af; }

/* 会话删除按钮 */
.conv-del-btn {
  flex-shrink: 0; opacity: 0;
  width: 28px; height: 28px;
  border: none; background: none; cursor: pointer;
  border-radius: 6px; color: #9ca3af;
  display: flex; align-items: center; justify-content: center;
  transition: opacity 0.12s, background 0.12s, color 0.12s;
}
.conv-item:hover .conv-del-btn { opacity: 1; }
.conv-del-btn:hover { background: #fee2e2; color: #ef4444; }

/* 删除确认行 */
.conv-del-confirm {
  font-size: 12px; color: #ef4444; font-weight: 500;
  padding: 0 0 0 10px; flex: 1; user-select: none;
}
.conv-del-actions {
  display: flex; gap: 6px; padding: 8px 6px;
}
.conv-del-yes {
  padding: 4px 10px; border-radius: 6px;
  border: none; background: #ef4444; color: #fff;
  font-size: 12px; cursor: pointer;
}
.conv-del-yes:hover { background: #dc2626; }
.conv-del-no {
  padding: 4px 10px; border-radius: 6px;
  border: 1px solid #e5e7eb; background: #fff; color: #374151;
  font-size: 12px; cursor: pointer;
}
.conv-del-no:hover { background: #f3f4f6; }

.sidebar-empty { padding: 24px 16px; font-size: 13px; color: #9ca3af; text-align: center; }

/* ===== 开场引导区域 ===== */
/* 使用 AI 消息样式 */
.welcome-text {
  font-size: 14px;
  color: #1f2937;
  line-height: 1.5;
  margin: 0 0 12px 0;
  white-space: pre-wrap;
  word-break: break-word;
}
.suggestions-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.suggestion-btn {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 10px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
  color: #3b82f6;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  text-align: left;
  min-height: 40px;
  outline: none;
}
.suggestion-btn:hover:not(:disabled) {
  /* 无样式变化 */
}
.suggestion-btn:active:not(:disabled) {
  /* 无样式变化 */
}
.suggestion-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ===== 顶部栏 ===== */
.topbar {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; background: #fff;
  border-bottom: 1px solid #e5e7eb; flex-shrink: 0;
}
.topbar-logo { font-weight: 700; font-size: 15px; color: #111827; flex: 1; }

.icon-btn {
  display: flex; align-items: center; justify-content: center;
  width: 32px; height: 32px; flex-shrink: 0;
  border: none; background: none; color: #6b7280;
  cursor: pointer; border-radius: 8px;
  transition: background 0.12s, color 0.12s;
}
.icon-btn:hover { background: #f3f4f6; color: #111827; }
.icon-btn--active { background: #dbeafe; color: #2563eb; }

/* ===== 用户上下文面板 ===== */
.user-context-panel {
  background: #fff; border-bottom: 1px solid #e5e7eb;
  padding: 12px 16px;
}
.user-context-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 10px;
}
.user-context-title {
  font-weight: 600; font-size: 13px; color: #111827;
}
.user-context-list {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 8px;
}
.user-context-item {
  display: flex; flex-direction: column;
  padding: 8px 10px; background: #f9fafb;
  border-radius: 8px; font-size: 12px;
}
.user-context-key {
  font-weight: 600; color: #6b7280; margin-bottom: 4px;
}
.user-context-value {
  color: #111827; word-break: break-all;
}

/* ===== 消息列表 ===== */
.msg-list {
  flex: 1; overflow-y: auto;
  padding: 16px 12px;
  display: flex; flex-direction: column; gap: 10px;
}
.msg-empty {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 10px; color: #9ca3af; font-size: 14px; padding: 40px 0;
}
.msg { display: flex; }
.msg--user { justify-content: flex-end; }
.msg--ai  { justify-content: flex-start; }
.bubble {
  max-width: 82%; padding: 9px 13px; border-radius: 16px;
  font-size: 14px; line-height: 1.6;
}
.bubble--user {
  background: #2563eb; color: #fff; border-bottom-right-radius: 4px;
}
.bubble--ai {
  background: #fff; color: #111827;
  border: 1px solid #e5e7eb; border-bottom-left-radius: 4px;
}

/* 思考过程 */
.thinking-block {
  margin-bottom: 8px; border: 1px solid #e5e7eb; border-radius: 8px;
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
  padding: 8px 12px 12px; margin: 0; font-size: 12px; color: #6b7280;
  white-space: pre-wrap; line-height: 1.6; border-top: 1px solid #e5e7eb; font-family: inherit;
}

/* 打字动画 */
.typing { display: inline-flex; gap: 4px; align-items: center; padding: 4px 0; }
.typing span {
  width: 6px; height: 6px; background: #9ca3af;
  border-radius: 50%; animation: blink 1.2s ease-in-out infinite;
}
.typing span:nth-child(2) { animation-delay: 0.2s; }
.typing span:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink { 0%,80%,100% { opacity: 0.25; } 40% { opacity: 1; } }

/* ===== 输入区域 ===== */
.composer { flex-shrink: 0; padding: 10px 12px; background: #f5f6f8; }
.composer--disabled { opacity: 0.6; pointer-events: none; }

/* 语音提示条 */
.voice-hint {
  display: flex; align-items: center; gap: 7px;
  padding: 5px 12px 8px; font-size: 13px; color: #374151;
}
.voice-hint-dot {
  width: 8px; height: 8px; border-radius: 50%; background: #9ca3af; flex-shrink: 0;
}
.voice-hint-dot--pulse {
  background: #ef4444;
  animation: pulse-dot 1s ease-in-out infinite;
}
@keyframes pulse-dot { 0%,100% { opacity: 1; transform: scale(1); } 50% { opacity: 0.5; transform: scale(1.3); } }

.composer-box {
  background: #fff; border: 1.5px solid #e5e7eb; border-radius: 14px;
  overflow: hidden; transition: border-color 0.15s, box-shadow 0.15s;
}
.composer-box:focus-within {
  border-color: #93c5fd; box-shadow: 0 0 0 3px rgba(59,130,246,.1);
}
.composer-textarea {
  width: 100%; resize: none; border: none; outline: none;
  padding: 12px 14px 8px; font-size: 14px; font-family: inherit; line-height: 1.55;
  color: #111827; background: transparent;
  min-height: 52px; max-height: 160px; overflow-y: auto;
}
.composer-textarea::placeholder { color: #9ca3af; }
.composer-textarea:disabled { opacity: 0.6; cursor: not-allowed; }
.composer-toolbar {
  display: flex; align-items: center; gap: 8px; padding: 6px 10px 10px;
}

/* 深度思考开关 */
.thinking-toggle { display: flex; align-items: center; gap: 6px; }
.thinking-label { font-size: 12px; color: #6b7280; user-select: none; }
.toggle-btn {
  position: relative; width: 32px; height: 18px;
  border: none; padding: 0; cursor: pointer;
  border-radius: 9px; background: #d1d5db; transition: background 0.2s; flex-shrink: 0;
}
.toggle-btn--on { background: #3b82f6; }
.toggle-knob {
  position: absolute; top: 2px; left: 2px;
  width: 14px; height: 14px; border-radius: 50%; background: #fff;
  transition: left 0.2s; box-shadow: 0 1px 3px rgba(0,0,0,.15);
}
.toggle-btn--on .toggle-knob { left: 16px; }

/* 语音按钮 */
.voice-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 34px; height: 34px; border: none; border-radius: 50%;
  background: #f3f4f6; color: #4b5563; cursor: pointer; flex-shrink: 0;
  transition: background 0.15s, color 0.15s, box-shadow 0.15s;
}
.voice-btn:hover:not(:disabled) { background: #e5e7eb; color: #111827; }
.voice-btn--recording {
  background: #fee2e2; color: #ef4444;
  box-shadow: 0 0 0 3px rgba(239,68,68,.2);
  animation: voice-pulse 1.2s ease-in-out infinite;
}
.voice-btn:disabled { opacity: 0.4; cursor: not-allowed; }
@keyframes voice-pulse { 0%,100% { box-shadow: 0 0 0 3px rgba(239,68,68,.2); } 50% { box-shadow: 0 0 0 6px rgba(239,68,68,.08); } }

/* 发送按钮 */
.send-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 16px; border: none; border-radius: 8px;
  background: #2563eb; color: #fff;
  font-size: 13px; font-weight: 500; cursor: pointer;
  transition: background 0.15s; flex-shrink: 0;
}
.send-btn:hover:not(:disabled) { background: #1d4ed8; }
.send-btn:disabled { background: #93c5fd; cursor: not-allowed; }
.btn-spinner {
  width: 14px; height: 14px;
  border: 2px solid rgba(255,255,255,.4); border-top-color: #fff;
  border-radius: 50%; animation: spin 0.7s linear infinite; flex-shrink: 0;
}

/* ===== Markdown 渲染 ===== */
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
.msg-md :deep(.md-code-lang) { color: #6b7280; font-family: ui-monospace, monospace; }
.msg-md :deep(.md-copy-btn) {
  flex-shrink: 0; margin: 0; padding: 2px 8px; font-size: 12px;
  color: #374151; background: #fff; border: 1px solid #d1d5db;
  border-radius: 5px; cursor: pointer;
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
  .topbar-logo { font-size: 17px; }
  .msg-list { padding: 12px 8px; }
  .bubble { max-width: 90%; font-size: 16px; padding: 10px 14px; }
  .msg-md { font-size: 16px; }
  .composer { padding: 8px; }
  .composer-textarea { font-size: 16px; }
  .voice-hint { font-size: 15px; }
  .send-btn span { display: none; }
  .send-btn { padding: 6px 12px; font-size: 15px; }
  .sidebar-title { font-size: 16px; }
  .new-conv-btn { font-size: 15px; }
  .conv-title { font-size: 15px; }
  .conv-time { font-size: 12px; }
  .thinking-label { font-size: 14px; }
  .thinking-summary { font-size: 13px; }
  .thinking-body { font-size: 13px; }
  .sidebar-empty { font-size: 15px; }
  .conv-del-confirm { font-size: 13px; }
  .conv-del-yes, .conv-del-no { font-size: 13px; }
}
</style>
