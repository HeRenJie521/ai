<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  NAvatar,
  NButton,
  NDrawer,
  NEllipsis,
  NIcon,
  NInput,
  NSelect,
  NSwitch,
  NText,
  NTooltip,
  useMessage,
  type DropdownOption,
  NDropdown,
} from 'naive-ui'
import {
  AddOutline,
  AttachOutline,
  ChevronForwardOutline,
  DocumentTextOutline,
  KeyOutline,
  MenuOutline,
  ServerOutline,
  SettingsOutline,
  StopCircleOutline,
} from '@vicons/ionicons5'
import { useAuthStore } from '@/stores/auth'
import {
  createConversation,
  deleteConversation,
  listConversations,
  loadConversationMessages,
  type ChatMessage,
  type ConversationItem,
} from '@/api/conversations'
import { chatStreamFetch, type ChatRequestBody } from '@/api/chat'
import { uploadChatFile } from '@/api/upload'
import { renderChatMarkdown } from '@/utils/chatMarkdown'
import { listAiApps, type AiAppOption } from '@/api/aiApps'
import { getWelcomeConfigByApp, type WelcomeConfig } from '@/api/welcome'

type PendingAttachment = { id: string; file: File; preview?: string }

let attachSeq = 0

const router = useRouter()
const message = useMessage()
const auth = useAuthStore()

function onMarkdownAreaClick(ev: MouseEvent) {
  const t = ev.target as HTMLElement | null
  if (!t?.closest('.md-copy-btn')) return
  ev.preventDefault()
  const block = t.closest('.md-code-block')
  const codeEl = block?.querySelector('pre.md-code-pre code') as HTMLElement | null
  const text = codeEl?.textContent ?? ''
  if (!text) { message.warning('无可复制内容'); return }
  void navigator.clipboard.writeText(text).then(
    () => message.success('已复制'),
    () => message.error('复制失败，请检查浏览器权限'),
  )
}

const userMenuOptions = computed<DropdownOption[]>(() => {
  if (auth.isAdmin) {
    return [
      { label: '系统设置', key: 'settings' },
      { type: 'divider', key: 'd1' },
      { label: '退出登录', key: 'logout' },
    ]
  }
  return [{ label: '退出登录', key: 'logout' }]
})

const settingsOpen = ref(false)
const isMobile = ref(false)
const drawerOpen = ref(false)
const msgScrollRef = ref<HTMLElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const pendingAttachments = ref<PendingAttachment[]>([])

const conversations = ref<ConversationItem[]>([])
const sessionId = ref<string | null>(null)
const messages = ref<ChatMessage[]>([])
const input = ref('')
const sending = ref(false)
const streamAbort = ref<AbortController | null>(null)
const deletingSessionId = ref<string | null>(null)
/** 是否已选中/开始一个会话（用于锁定 Agent 选择） */
const conversationPicked = ref(false)

// ---- Agent 相关 ----
const agents = ref<AiAppOption[]>([])
const selectedAgentId = ref<number | null>(null)
const thinkingModeOn = ref(false)
const welcomeConfig = ref<WelcomeConfig | null>(null)
const suggestionsSending = ref(false)

const agentOptions = computed(() =>
  agents.value.map((a) => ({ label: a.name, value: a.id })),
)

const currentAgent = computed(() =>
  agents.value.find((a) => a.id === selectedAgentId.value) ?? null,
)

/** 选中会话或已开始对话后锁定 Agent 选择 */
const agentLocked = computed(() => conversationPicked.value || messages.value.length > 0)

/** 当前 Agent 的模型是否支持深度思考 */
const showDeepThinkingSwitch = computed(() => currentAgent.value?.deepThinking === true)

watch(showDeepThinkingSwitch, (show) => {
  if (!show) thinkingModeOn.value = false
})

/** 当前 Agent 的模型是否支持图片 */
const visionSupported = computed(() => currentAgent.value?.vision === true)

watch(visionSupported, (supported) => {
  if (!supported) stripPendingImagesIfNoVision(true)
})

/** 切换 Agent 时加载开场白 */
watch(selectedAgentId, async (agentId) => {
  welcomeConfig.value = null
  if (agentId == null) return
  try {
    welcomeConfig.value = await getWelcomeConfigByApp(agentId)
  } catch { /* 忽略 */ }
})

function isImageFile(file: File): boolean {
  if (file.type.startsWith('image/')) return true
  return /\.(png|jpe?g|gif|webp|bmp|heic|heif)$/i.test(file.name.toLowerCase())
}

function stripPendingImagesIfNoVision(showToast: boolean) {
  if (visionSupported.value) return
  const kept: PendingAttachment[] = []
  let removed = false
  for (const p of pendingAttachments.value) {
    if (isImageFile(p.file)) {
      if (p.preview) URL.revokeObjectURL(p.preview)
      removed = true
    } else {
      kept.push(p)
    }
  }
  if (removed) {
    pendingAttachments.value = kept
    if (showToast) message.warning('当前模型不支持视觉，已移除待发送的图片')
  }
}

function showAssistantReplyBubble(m: ChatMessage, index: number): boolean {
  if (!thinkingModeOn.value) return true
  if ((m.content ?? '').trim().length > 0) return true
  const streamingThis = sending.value && index === messages.value.length - 1 && m.role === 'assistant'
  if (streamingThis) return false
  return true
}

function checkMobile() {
  isMobile.value = window.matchMedia('(max-width: 768px)').matches
}

async function scrollMessagesToBottom() {
  await nextTick()
  const el = msgScrollRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function scrollToStreamBottom() {
  await nextTick()
  const el = msgScrollRef.value
  if (!el) return
  const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  if (distFromBottom < 120) el.scrollTop = el.scrollHeight
}

watch(
  () => messages.value
    .map((m) => `${m.role}:${(m.content ?? '').length}:${(m.reasoningContent ?? '').length}`)
    .join('|'),
  () => { void scrollMessagesToBottom() },
)

onMounted(async () => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  try {
    agents.value = await listAiApps()
  } catch {
    message.error('加载 Agent 列表失败')
  }
  try {
    conversations.value = await listConversations(null, true)
  } catch { /* 忽略 */ }
  await scrollMessagesToBottom()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
  for (const p of pendingAttachments.value) {
    if (p.preview) URL.revokeObjectURL(p.preview)
  }
})

async function refreshConversations() {
  try {
    conversations.value = await listConversations(null, true)
  } catch { /* 忽略 */ }
}

async function onNewChat() {
  for (const p of pendingAttachments.value) {
    if (p.preview) URL.revokeObjectURL(p.preview)
  }
  pendingAttachments.value = []
  sessionId.value = null
  messages.value = []
  input.value = ''
  thinkingModeOn.value = false
  conversationPicked.value = false
  deletingSessionId.value = null
  selectedAgentId.value = null
  welcomeConfig.value = null
  if (isMobile.value) drawerOpen.value = false
}

async function selectConv(c: ConversationItem) {
  for (const p of pendingAttachments.value) {
    if (p.preview) URL.revokeObjectURL(p.preview)
  }
  pendingAttachments.value = []
  sessionId.value = c.sessionId
  conversationPicked.value = true
  deletingSessionId.value = null

  // 恢复 Agent 选择：若会话关联 Agent 且与当前不同，切换并加载欢迎语
  const agentId = c.appId ?? null
  if (agentId !== selectedAgentId.value) {
    selectedAgentId.value = agentId
    // watch 会自动加载 welcomeConfig，但若 agentId 为 null 则手动清空
    if (agentId == null) welcomeConfig.value = null
  }

  try {
    messages.value = await loadConversationMessages(c.sessionId)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载会话消息失败')
    messages.value = []
  }
  if (isMobile.value) drawerOpen.value = false
}

function askDelete(c: ConversationItem, e: Event) {
  e.stopPropagation()
  deletingSessionId.value = c.sessionId
}

async function confirmDelete(sid: string) {
  try {
    await deleteConversation(sid)
    conversations.value = conversations.value.filter((c) => c.sessionId !== sid)
    if (sessionId.value === sid) {
      sessionId.value = null
      messages.value = []
      conversationPicked.value = false
    }
  } catch {
    message.error('删除失败')
  }
  deletingSessionId.value = null
}

async function ensureSession(): Promise<string> {
  if (sessionId.value) return sessionId.value
  const c = await createConversation(selectedAgentId.value)
  sessionId.value = c.sessionId
  conversationPicked.value = true
  await refreshConversations()
  return c.sessionId
}

/** 推荐问题点击 */
async function handleSuggestionClick(question: string) {
  if (suggestionsSending.value || sending.value) return
  if (!currentAgent.value) return
  suggestionsSending.value = true
  input.value = question
  try {
    await send()
  } finally {
    suggestionsSending.value = false
  }
}

function openFilePicker() {
  fileInputRef.value?.click()
}

function addPendingFiles(files: File[]) {
  const oversized = files.filter((f) => f.size > 5 * 1024 * 1024)
  if (oversized.length > 0) {
    message.error(`以下文件超过 5 MB 限制，已跳过：${oversized.map((f) => f.name).join('、')}`)
  }
  files = files.filter((f) => f.size <= 5 * 1024 * 1024)
  if (!files.length) return
  const imageFiles = files.filter((f) => isImageFile(f))
  const nonImageFiles = files.filter((f) => !isImageFile(f))
  if (imageFiles.length > 0 && !visionSupported.value) {
    message.warning('当前模型不支持视觉')
    if (nonImageFiles.length === 0) return
  }
  for (const file of nonImageFiles) {
    const id = `${++attachSeq}-${file.name}-${file.size}`
    pendingAttachments.value.push({ id, file })
  }
  if (!visionSupported.value) return
  for (const file of imageFiles) {
    const id = `${++attachSeq}-${file.name}-${file.size}`
    const preview = URL.createObjectURL(file)
    pendingAttachments.value.push({ id, file, preview })
  }
}

function onFileInputChange(ev: Event) {
  const el = ev.target as HTMLInputElement
  const list = el.files
  if (!list?.length) return
  addPendingFiles(Array.from(list))
  el.value = ''
}

function removePendingAttachment(index: number) {
  const p = pendingAttachments.value[index]
  if (p?.preview) URL.revokeObjectURL(p.preview)
  pendingAttachments.value.splice(index, 1)
}

function onComposerPaste(ev: ClipboardEvent) {
  const dt = ev.clipboardData
  if (!dt) return
  const files: File[] = []
  for (let i = 0; i < dt.items.length; i++) {
    const it = dt.items[i]
    if (it.kind === 'file') {
      const f = it.getAsFile()
      if (f) files.push(f)
    }
  }
  if (!files.length) return
  ev.preventDefault()
  addPendingFiles(files)
}

function isImageHttpUrl(u: string): boolean {
  const path = (u.split('?')[0] ?? '').toLowerCase()
  return /\.(png|jpe?g|gif|webp|bmp)$/i.test(path)
}

function shortUrlDisplay(u: string): string {
  if (u.length <= 52) return u
  return `${u.slice(0, 40)}…${u.slice(-10)}`
}

function sameUserPayload(m: ChatMessage, text: string, urls: string[]): boolean {
  if (m.role !== 'user') return false
  if ((m.content ?? '').trim() !== text) return false
  const a = m.fileUrls ?? []
  if (a.length !== urls.length) return false
  return a.every((x, i) => x === urls[i])
}

function stopStreaming() {
  streamAbort.value?.abort()
}

function onComposerKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.isComposing) return
  if (event.shiftKey || event.ctrlKey || event.metaKey || event.altKey) return
  event.preventDefault()
  void send()
}

async function send() {
  const text = input.value.trim()
  if ((!text && !pendingAttachments.value.length) || sending.value) return
  const agent = currentAgent.value
  if (!agent) {
    message.warning('请选择 Agent 应用')
    return
  }
  if (!agent.providerCode || !agent.modeKey) {
    message.warning('所选 Agent 未配置模型，请联系管理员')
    return
  }
  sending.value = true
  const uploadedUrls: string[] = []
  try {
    for (const p of pendingAttachments.value) {
      uploadedUrls.push(await uploadChatFile(p.file))
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '文件上传失败')
    sending.value = false
    return
  }
  for (const p of pendingAttachments.value) {
    if (p.preview) URL.revokeObjectURL(p.preview)
  }
  pendingAttachments.value = []

  let sid: string
  try {
    sid = await ensureSession()
  } catch {
    message.error('创建会话失败')
    sending.value = false
    return
  }

  const userMsg: ChatMessage = {
    role: 'user',
    content: text,
    createdAt: new Date().toISOString(),
    ...(uploadedUrls.length ? { fileUrls: uploadedUrls } : {}),
  }
  messages.value = [...messages.value, userMsg]
  input.value = ''

  const body: ChatRequestBody = {
    provider: agent.providerCode,
    mode: agent.modeKey,
    sessionId: sid,
    messages: messages.value,
    stream: agent.streamOutput !== false,
    thinkingMode: thinkingModeOn.value,
    agentId: agent.id,
  }

  const ac = new AbortController()
  streamAbort.value = ac
  let streamSettled = false
  const settleStreamEnd = async () => {
    if (streamSettled) return
    streamSettled = true
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'assistant' && !last.createdAt) {
      last.createdAt = new Date().toISOString()
      messages.value = [...messages.value]
    }
    sending.value = false
    streamAbort.value = null
    await refreshConversations()
  }

  try {
    messages.value = [...messages.value, { role: 'assistant', content: '', reasoningContent: '' }]
    const idx = messages.value.length - 1
    await chatStreamFetch(
      auth.token,
      body,
      (delta) => {
        const m = messages.value[idx]
        if (m) {
          if (thinkingModeOn.value && delta.reasoning) {
            m.reasoningContent = (m.reasoningContent ?? '') + delta.reasoning
          }
          if (delta.content) m.content += delta.content
          messages.value = [...messages.value]
          void scrollToStreamBottom()
        }
      },
      async () => { await settleStreamEnd() },
      (err) => {
        message.error(err.message)
        messages.value = messages.value.slice(0, -1)
        void settleStreamEnd()
      },
      {
        signal: ac.signal,
        onAbort: async () => { await settleStreamEnd() },
      },
    )
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } }; message?: string }
    message.error(err.response?.data?.error || err.message || '发送失败')
    streamAbort.value = null
    sending.value = false
    messages.value = messages.value.filter((m) => !sameUserPayload(m, text, uploadedUrls))
  }
}

async function logout() {
  await auth.logoutRemote()
  router.push('/login')
}

function fmtMsgTime(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const hhmm = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  if (sameDay) return hhmm
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${hhmm}`
}

function onUserMenuSelect(key: string) {
  if (key === 'settings') {
    if (!auth.isAdmin) return
    router.push('/settings/llm')
  } else if (key === 'logout') {
    void logout()
  }
}

function goSettingsPage(page: 'llm' | 'api-keys' | 'api-docs') {
  settingsOpen.value = false
  drawerOpen.value = false
  router.push(`/settings/${page}`)
}
</script>

<template>
  <div class="layout-root">

    <Transition name="settings-slide">
      <div v-if="settingsOpen && auth.isAdmin" class="settings-overlay">
        <div class="settings-panel">
          <div class="settings-panel-head">
            <span class="settings-panel-title">系统设置</span>
            <n-button quaternary size="tiny" @click="settingsOpen = false">✕</n-button>
          </div>
          <nav class="settings-nav">
            <div class="settings-nav-item" @click="goSettingsPage('llm')">
              <n-icon class="settings-nav-icon" :component="ServerOutline" />
              <div class="settings-nav-text">
                <span class="settings-nav-label">模型管理</span>
                <span class="settings-nav-sub">已配置的大模型</span>
              </div>
              <n-icon class="settings-nav-arrow" :component="ChevronForwardOutline" />
            </div>
            <div class="settings-nav-item" @click="goSettingsPage('api-keys')">
              <n-icon class="settings-nav-icon" :component="KeyOutline" />
              <div class="settings-nav-text">
                <span class="settings-nav-label">集成管理</span>
              </div>
              <n-icon class="settings-nav-arrow" :component="ChevronForwardOutline" />
            </div>
            <div class="settings-nav-item" @click="goSettingsPage('api-docs')">
              <n-icon class="settings-nav-icon" :component="DocumentTextOutline" />
              <div class="settings-nav-text">
                <span class="settings-nav-label">API 文档</span>
              </div>
              <n-icon class="settings-nav-arrow" :component="ChevronForwardOutline" />
            </div>
          </nav>
        </div>
        <div class="settings-backdrop" @click="settingsOpen = false" />
      </div>
    </Transition>

    <!-- ===== 左侧会话列表 ===== -->
    <aside v-if="!isMobile" class="sidebar ds-sidebar">
      <div class="brand">
        <span class="brand-mark">Agent专家</span>
        <span class="brand-sub">AI</span>
      </div>
      <div class="side-head">
        <n-button type="primary" ghost block @click="onNewChat">
          <template #icon>
            <n-icon :component="AddOutline" />
          </template>
          开启新对话
        </n-button>
      </div>
      <div class="side-scroll">
        <div
          v-for="c in conversations"
          :key="c.sessionId"
          class="conv-item"
          :class="{ active: c.sessionId === sessionId }"
        >
          <template v-if="deletingSessionId === c.sessionId">
            <span class="conv-del-confirm">确认删除？</span>
            <div class="conv-del-actions">
              <button class="conv-del-yes" @click="confirmDelete(c.sessionId)">删除</button>
              <button class="conv-del-no" @click="deletingSessionId = null">取消</button>
            </div>
          </template>
          <template v-else>
            <div class="conv-item-main" @click="selectConv(c)">
              <n-ellipsis class="conv-title">{{ c.title || '无标题' }}</n-ellipsis>
            </div>
            <button class="conv-del-btn" title="删除会话" @click="askDelete(c, $event)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6l-1 14H6L5 6"/>
                <path d="M10 11v6M14 11v6"/>
                <path d="M9 6V4h6v2"/>
              </svg>
            </button>
          </template>
        </div>
        <p v-if="conversations.length === 0" class="side-empty">暂无历史会话</p>
      </div>
      <div class="side-foot">
        <div class="user-foot">
          <n-avatar round class="user-foot-avatar">{{ auth.username?.slice(0, 1).toUpperCase() }}</n-avatar>
          <div class="user-foot-main">
            <div class="user-foot-row">
              <n-ellipsis class="user-foot-name" :tooltip="false">{{ auth.username || '用户' }}</n-ellipsis>
            </div>
            <span class="api-docs-link" @click="router.push('/settings/api-docs')">API文档</span>
            <div class="user-foot-menu">
              <n-dropdown trigger="click" :options="userMenuOptions" @select="onUserMenuSelect">
                <n-button quaternary size="tiny" class="user-foot-more" aria-label="菜单">
                  <template #icon><n-icon :component="SettingsOutline" /></template>
                </n-button>
              </n-dropdown>
            </div>
          </div>
        </div>
      </div>
    </aside>

    <!-- ===== 主内容区 ===== -->
    <div class="main-column ds-main">
      <header class="top-bar">
        <n-button v-if="isMobile" quaternary circle @click="drawerOpen = true">
          <template #icon>
            <n-icon :component="MenuOutline" />
          </template>
        </n-button>
        <n-text strong style="font-size: 16px">对话</n-text>
        <div style="flex: 1" />
      </header>

      <div v-if="!agents.length" class="banner-warn">
        <n-text depth="2">
          暂无可用 Agent。请点击左下角「...」→「系统设置」新建 Agent 并配置模型。
        </n-text>
      </div>

      <div ref="msgScrollRef" class="msg-scroll">
        <div class="msg-scroll-inner">
          <!-- 开场白 + 推荐问题（选择 Agent 后始终固定在顶部） -->
          <div v-if="welcomeConfig && welcomeConfig.welcomeText" class="welcome-block">
            <div class="welcome-text">{{ welcomeConfig.welcomeText }}</div>
            <div v-if="welcomeConfig.suggestions?.length" class="suggestions-list">
              <button
                v-for="(s, i) in welcomeConfig.suggestions"
                :key="i"
                class="suggestion-btn"
                :disabled="sending || suggestionsSending"
                @click="handleSuggestionClick(s)"
              >
                {{ s }}
              </button>
            </div>
          </div>

          <!-- 空状态（未选 Agent 且无消息时） -->
          <div v-if="!messages.length && !welcomeConfig" class="empty-hint">
            <p>{{ selectedAgentId ? '开始与 AI 对话' : '请先选择 Agent 应用' }}</p>
            <p class="sub">左侧选择历史会话，或点击「开启新对话」</p>
          </div>

          <!-- 消息列表 -->
          <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
            <template v-if="m.role === 'assistant'">
              <div class="assistant-stack">
                <details v-if="m.reasoningContent" class="thinking-details" open>
                  <summary class="thinking-summary">思考过程</summary>
                  <div
                    class="thinking-md-wrap msg-md"
                    @click="onMarkdownAreaClick"
                    v-html="renderChatMarkdown(m.reasoningContent || '')"
                  />
                </details>
                <div
                  v-if="showAssistantReplyBubble(m, i)"
                  class="bubble ds-bubble-ai"
                  @click="onMarkdownAreaClick"
                >
                  <div
                    v-if="(m.content ?? '').trim()"
                    class="msg-md"
                    v-html="renderChatMarkdown(m.content || '')"
                  />
                  <pre v-else class="msg-pre msg-md-placeholder">&nbsp;</pre>
                </div>
                <div v-if="m.createdAt" class="msg-time">{{ fmtMsgTime(m.createdAt) }}</div>
              </div>
            </template>
            <template v-else>
              <div class="msg-bubble-col">
                <div class="bubble ds-bubble-user">
                  <div v-if="m.fileUrls?.length" class="user-msg-files">
                    <template v-for="(u, ui) in m.fileUrls" :key="`${u}-${ui}`">
                      <a
                        v-if="isImageHttpUrl(u)"
                        :href="u"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="user-img-wrap"
                      >
                        <img :src="u" alt="" class="user-msg-img" loading="lazy" />
                      </a>
                      <a
                        v-else
                        :href="u"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="user-file-link"
                      >
                        {{ shortUrlDisplay(u) }}
                      </a>
                    </template>
                  </div>
                  <pre v-if="(m.content ?? '').trim()" class="msg-pre">{{ m.content }}</pre>
                </div>
                <div v-if="m.createdAt" class="msg-time msg-time--right">{{ fmtMsgTime(m.createdAt) }}</div>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- ===== 输入区 ===== -->
      <div class="composer">
        <div class="composer-box">
          <div v-if="pendingAttachments.length" class="composer-pending-bar">
            <div
              v-for="(p, i) in pendingAttachments"
              :key="p.id"
              class="composer-pending-item"
            >
              <img v-if="p.preview" :src="p.preview" alt="" class="composer-pending-thumb" />
              <span v-else class="composer-pending-name">{{ p.file.name }}</span>
              <button
                type="button"
                class="composer-attach-remove"
                aria-label="移除"
                @click="removePendingAttachment(i)"
              >
                ×
              </button>
            </div>
          </div>
          <n-input
            v-model:value="input"
            type="textarea"
            :bordered="false"
            placeholder="请输入你的问题"
            :autosize="{ minRows: 2, maxRows: 6 }"
            class="composer-textarea"
            @paste="onComposerPaste"
            @keydown="onComposerKeydown"
          />
          <div class="composer-toolbar">
            <div class="composer-toolbar-left composer-agent-picks">
              <!-- Agent 选择器 -->
              <n-tooltip :disabled="!agentLocked" placement="top">
                <template #trigger>
                  <n-select
                    v-model:value="selectedAgentId"
                    :options="agentOptions"
                    placeholder="选择Agent应用"
                    class="tb-select tb-agent"
                    size="small"
                    filterable
                    :consistent-menu-width="false"
                    :disabled="agentLocked"
                    :status="!selectedAgentId && agents.length ? 'warning' : undefined"
                  />
                </template>
                已开始会话，切换 Agent 请开启新对话
              </n-tooltip>
              <!-- 当前 Agent 的模型信息（只读） -->
              <span v-if="currentAgent?.modelDisplayName" class="agent-model-badge">
                {{ currentAgent.modelDisplayName }}
              </span>
              <span v-else-if="selectedAgentId" class="agent-model-badge agent-model-none">
                未配置模型
              </span>
              <!-- 深度思考开关 -->
              <div
                v-if="showDeepThinkingSwitch"
                class="thinking-switch"
                title="关闭时不展示思考过程，且请求不带 thinking 参数"
              >
                <span class="thinking-switch-label">深度思考</span>
                <n-switch v-model:value="thinkingModeOn" size="small" />
              </div>
            </div>
            <div class="composer-toolbar-right">
              <input
                ref="fileInputRef"
                type="file"
                class="composer-file-input"
                multiple
                accept="image/*,.pdf,.doc,.docx,.txt,.zip,.rar"
                @change="onFileInputChange"
              />
              <n-button quaternary circle size="small" title="选择文件" @click="openFilePicker">
                <template #icon>
                  <n-icon :component="AttachOutline" />
                </template>
              </n-button>
              <n-button
                size="small"
                :type="sending ? 'warning' : 'primary'"
                :secondary="sending"
                :disabled="!sending && !input.trim() && !pendingAttachments.length"
                @click="sending ? stopStreaming() : send()"
              >
                <template v-if="sending" #icon>
                  <n-icon :component="StopCircleOutline" />
                </template>
                {{ sending ? '停止' : '发送' }}
              </n-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 移动端抽屉 ===== -->
    <n-drawer v-model:show="drawerOpen" :width="288" placement="left" class="drawer-side">
      <div class="brand drawer-brand">
        <span class="brand-mark">Agent专家</span>
        <span class="brand-sub">AI</span>
      </div>
      <div class="side-head">
        <n-button type="primary" ghost block @click="onNewChat">
          <template #icon>
            <n-icon :component="AddOutline" />
          </template>
          开启新对话
        </n-button>
      </div>
      <div class="side-scroll">
        <div
          v-for="c in conversations"
          :key="c.sessionId"
          class="conv-item"
          :class="{ active: c.sessionId === sessionId }"
        >
          <template v-if="deletingSessionId === c.sessionId">
            <span class="conv-del-confirm">确认删除？</span>
            <div class="conv-del-actions">
              <button class="conv-del-yes" @click="confirmDelete(c.sessionId)">删除</button>
              <button class="conv-del-no" @click="deletingSessionId = null">取消</button>
            </div>
          </template>
          <template v-else>
            <div class="conv-item-main" @click="selectConv(c)">
              <n-ellipsis class="conv-title">{{ c.title || '无标题' }}</n-ellipsis>
            </div>
            <button class="conv-del-btn" title="删除会话" @click="askDelete(c, $event)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6l-1 14H6L5 6"/>
                <path d="M10 11v6M14 11v6"/>
                <path d="M9 6V4h6v2"/>
              </svg>
            </button>
          </template>
        </div>
        <p v-if="conversations.length === 0" class="side-empty">暂无历史会话</p>
      </div>
      <div class="side-foot">
        <div class="user-foot">
          <n-avatar round class="user-foot-avatar">{{ auth.username?.slice(0, 1).toUpperCase() }}</n-avatar>
          <div class="user-foot-main">
            <div class="user-foot-row">
              <n-ellipsis class="user-foot-name" :tooltip="false">{{ auth.username || '用户' }}</n-ellipsis>
            </div>
            <div class="user-foot-menu">
              <n-dropdown trigger="click" :options="userMenuOptions" @select="onUserMenuSelect">
                <n-button quaternary size="tiny" class="user-foot-more" aria-label="菜单">
                  <template #icon><n-icon :component="SettingsOutline" /></template>
                </n-button>
              </n-dropdown>
            </div>
          </div>
        </div>
      </div>
    </n-drawer>
  </div>
</template>

<style scoped>
.layout-root {
  display: flex;
  flex: 1;
  width: 100%;
  height: 100vh;
  height: 100dvh;
  max-height: 100dvh;
  overflow: hidden;
  box-sizing: border-box;
  background: #ffffff;
}
.sidebar {
  width: 268px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  box-sizing: border-box;
}
.main-column {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.brand {
  padding: 14px 16px 8px;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 0.02em;
  border-bottom: 1px solid #e5e5e5;
}
.drawer-brand { margin: -4px -4px 0; }
.brand-mark { color: #111827; }
.brand-sub { color: #2563eb; margin-left: 4px; }
.side-head { padding: 12px; border-bottom: 1px solid #e5e5e5; }
.banner-warn {
  flex-shrink: 0;
  padding: 8px 16px;
  background: #fffbeb;
  border-bottom: 1px solid #fde68a;
  font-size: 13px;
}
.side-scroll { flex: 1; overflow-y: auto; padding: 8px 0; }
.side-empty { text-align: center; font-size: 13px; color: #9ca3af; padding: 20px 0; }
.conv-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  margin: 0 8px 2px;
  border-radius: 8px;
  cursor: default;
  font-size: 15px;
  color: #4b5563;
}
.conv-item:hover { background: #ececf0; }
.conv-item.active { background: #e0e7ff; color: #111827; }
.conv-item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  cursor: pointer;
}
.conv-title { font-size: 14px; }
.conv-model { font-size: 11px; color: #9ca3af; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.conv-del-confirm { flex: 1; font-size: 12px; color: #ef4444; font-weight: 500; }
.conv-del-actions { display: flex; gap: 6px; flex-shrink: 0; }
.conv-del-yes {
  font-size: 12px; padding: 2px 8px; border-radius: 5px; border: none; cursor: pointer;
  background: #ef4444; color: #fff;
}
.conv-del-yes:hover { background: #dc2626; }
.conv-del-no {
  font-size: 12px; padding: 2px 8px; border-radius: 5px; border: 1px solid #d1d5db; cursor: pointer;
  background: #fff; color: #374151;
}
.conv-del-no:hover { background: #f3f4f6; }
.conv-del-btn {
  flex-shrink: 0; display: flex; align-items: center; justify-content: center;
  width: 24px; height: 24px; border: none; background: transparent;
  color: #9ca3af; cursor: pointer; border-radius: 4px; padding: 0; opacity: 0;
  transition: opacity 0.15s, color 0.15s;
}
.conv-item:hover .conv-del-btn { opacity: 1; }
.conv-del-btn:hover { color: #ef4444; background: #fee2e2; }
.side-foot { padding: 12px; border-top: 1px solid #e5e5e5; }
.user-foot { display: flex; align-items: center; gap: 10px; min-width: 0; }
.user-foot-avatar { flex-shrink: 0; }
.user-foot-main {
  flex: 1; min-width: 0; position: relative; display: flex;
  flex-direction: column; gap: 4px; padding-right: 44px;
}
.user-foot-row { display: flex; align-items: center; gap: 6px; min-width: 0; min-height: 24px; }
.user-foot-name { flex: 1; min-width: 0; font-size: 15px; font-weight: 500; color: #111827; }
.user-foot-menu {
  position: absolute; right: 0; top: 50%; transform: translateY(-50%);
  display: flex; align-items: center;
}
.api-docs-link { font-size: 13px; color: #6b7280; cursor: pointer; user-select: none; }
.api-docs-link:hover { color: #111827; }
.user-foot-more {
  flex-shrink: 0; min-width: 40px; min-height: 40px; padding: 0; font-size: 22px;
  line-height: 1; color: #6b7280; display: flex; align-items: center; justify-content: center;
}
.user-foot-more :deep(.n-icon) { font-size: 24px; }
.user-foot-more:hover { color: #111827; }
.top-bar {
  flex-shrink: 0; display: flex; align-items: center; gap: 12px;
  padding: 12px 16px; border-bottom: 1px solid #e5e5e5; background: #ffffff; font-size: 16px;
}
.msg-scroll {
  flex: 1 1 auto; min-height: 0; overflow-y: auto; overflow-x: hidden;
  padding: 20px 16px 16px; -webkit-overflow-scrolling: touch;
}
.msg-scroll-inner { max-width: 880px; width: 100%; margin: 0 auto; }

/* 开场白 */
.welcome-block {
  margin-bottom: 24px;
  padding: 16px 20px;
  background: #f0f4ff;
  border: 1px solid #d4e0ff;
  border-radius: 12px;
  max-width: 480px;
}
.welcome-text {
  font-size: 15px;
  line-height: 1.65;
  color: #1f2937;
  white-space: pre-wrap;
  word-break: break-word;
}
.suggestions-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
}
.suggestion-btn {
  display: block;
  width: 100%;
  padding: 8px 14px;
  font-size: 13px;
  color: #2563eb;
  background: #ffffff;
  border: 1px solid #93c5fd;
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
  transition: background 0.12s, border-color 0.12s;
  word-break: break-word;
}
.suggestion-btn:hover:not(:disabled) { background: #eff6ff; border-color: #2563eb; }
.suggestion-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.empty-hint { text-align: center; color: #9ca3af; margin-top: 15vh; font-size: 16px; }
.empty-hint .sub { font-size: 14px; margin-top: 8px; }
.msg-row { display: flex; margin-bottom: 16px; }
.msg-row.user { justify-content: flex-end; }
.msg-row.assistant { justify-content: flex-start; }
.msg-bubble-col { display: flex; flex-direction: column; align-items: flex-end; max-width: 85%; gap: 4px; }
.assistant-stack { max-width: 85%; min-width: 0; display: flex; flex-direction: column; align-items: stretch; gap: 8px; }
.msg-time { font-size: 12px; color: #9ca3af; padding: 0 2px; white-space: nowrap; }
.msg-time--right { text-align: right; }
.thinking-details { border: 1px solid #e5e7eb; border-radius: 10px; background: #f9fafb; padding: 10px 12px; }
.thinking-summary { cursor: pointer; font-size: 14px; font-weight: 600; color: #6b7280; user-select: none; }
.thinking-md-wrap { margin-top: 8px; font-size: 14px; color: #4b5563; line-height: 1.6; }
.bubble { max-width: 100%; padding: 10px 14px; }
.msg-pre { margin: 0; white-space: pre-wrap; word-break: break-word; font-family: inherit; font-size: 15px; line-height: 1.6; color: #111827; }
.msg-md-placeholder { min-height: 1em; color: transparent; }
.msg-md { font-size: 15px; line-height: 1.6; color: #111827; word-break: break-word; }
.msg-md :deep(p) { margin: 0.4em 0; }
.msg-md :deep(p:first-child) { margin-top: 0; }
.msg-md :deep(p:last-child) { margin-bottom: 0; }
.msg-md :deep(ul), .msg-md :deep(ol) { margin: 0.45em 0; padding-left: 1.35em; }
.msg-md :deep(h1), .msg-md :deep(h2), .msg-md :deep(h3), .msg-md :deep(h4) { margin: 0.65em 0 0.35em; font-weight: 600; line-height: 1.3; }
.msg-md :deep(h1) { font-size: 1.35em; }
.msg-md :deep(h2) { font-size: 1.25em; }
.msg-md :deep(h3), .msg-md :deep(h4) { font-size: 1.15em; }
.msg-md :deep(blockquote) { margin: 0.5em 0; padding: 0.35em 0 0.35em 0.85em; border-left: 3px solid #d1d5db; color: #4b5563; font-size: 15px; }
.msg-md :deep(a) { color: #2563eb; text-decoration: underline; }
.msg-md :deep(p > code), .msg-md :deep(li > code), .msg-md :deep(td > code) {
  background: #f3f4f6; padding: 0.12em 0.38em; border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace; font-size: 0.95em;
}
.msg-md :deep(.md-code-block) { margin: 0.75em 0; border: 1px solid #e5e7eb; border-radius: 10px; overflow: hidden; background: #ffffff; box-shadow: 0 1px 2px rgba(15,23,42,0.05); }
.msg-md :deep(.md-code-head) { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 6px 10px; background: #f3f4f6; border-bottom: 1px solid #e5e7eb; font-size: 12px; }
.msg-md :deep(.md-code-lang) { color: #6b7280; font-family: ui-monospace, monospace; text-transform: lowercase; }
.msg-md :deep(.md-copy-btn) { flex-shrink: 0; margin: 0; padding: 3px 10px; font-size: 12px; line-height: 1.35; color: #374151; background: #ffffff; border: 1px solid #d1d5db; border-radius: 6px; cursor: pointer; }
.msg-md :deep(.md-copy-btn:hover) { background: #f9fafb; border-color: #9ca3af; }
.msg-md :deep(.md-code-pre) { margin: 0; padding: 12px 14px; overflow-x: auto; font-size: 14px; line-height: 1.5; background: #fafafa; }
.msg-md :deep(.md-code-pre code) { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace; }
.msg-md :deep(table) { border-collapse: collapse; margin: 0.6em 0; font-size: 14px; }
.msg-md :deep(th), .msg-md :deep(td) { border: 1px solid #e5e7eb; padding: 0.35em 0.6em; }
.msg-md :deep(th) { background: #f9fafb; }
.user-msg-files { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
.user-img-wrap { display: block; border-radius: 8px; overflow: hidden; border: 1px solid #e5e7eb; max-width: min(220px, 70vw); }
.user-msg-img { display: block; width: 100%; height: auto; max-height: 200px; object-fit: contain; vertical-align: middle; }
.user-file-link { font-size: 12px; color: #2563eb; word-break: break-all; text-decoration: underline; max-width: 100%; }
.composer {
  flex: 0 0 auto; width: 100%;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom, 0px));
  background: #ffffff; border-top: 1px solid #e5e5e5; box-sizing: border-box;
}
.composer-box {
  max-width: 880px; width: 100%; margin: 0 auto;
  display: flex; flex-direction: column; gap: 0;
  padding: 10px 12px 8px; background: #fafafa;
  border: 1px solid #e5e7eb; border-radius: 12px;
  box-shadow: 0 1px 2px rgba(15,23,42,0.06);
}
.composer-pending-bar { display: flex; flex-wrap: wrap; gap: 8px; padding: 0 4px 10px; margin-bottom: 2px; border-bottom: 1px dashed #e5e7eb; }
.composer-pending-item { position: relative; display: inline-flex; align-items: center; gap: 6px; padding: 4px 28px 4px 4px; background: #ffffff; border: 1px solid #e5e7eb; border-radius: 8px; max-width: 100%; }
.composer-pending-thumb { display: block; width: 72px; height: 72px; object-fit: cover; border-radius: 6px; }
.composer-pending-name { font-size: 12px; color: #4b5563; max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; padding: 4px 4px 4px 8px; }
.composer-attach-remove { display: flex; align-items: center; justify-content: center; width: 18px; height: 18px; padding: 0; margin: 0; border: none; border-radius: 50%; background: transparent; color: #6b7280; font-size: 16px; line-height: 1; cursor: pointer; }
.composer-attach-remove:hover { color: #111827; background: rgba(0,0,0,0.06); }
.composer-textarea :deep(.n-input-wrapper) { padding: 4px 4px 8px; }
.composer-textarea :deep(textarea.n-input__textarea-el) { min-height: 0; overflow-y: auto; line-height: 1.6; font-size: 16px; }
.composer-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  gap: 10px; padding: 8px 4px 4px; margin-top: 2px; border-top: 1px solid #ececf1;
}
.composer-toolbar-left { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; min-width: 0; flex: 0 1 auto; }
.composer-agent-picks { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.composer-agent-picks :deep(.n-base-selection) { min-height: 30px; font-size: 12px; border-radius: 8px; }
.composer-agent-picks :deep(.n-base-selection__border) { border-radius: 8px; }
.composer-agent-picks :deep(.n-base-selection-label) { font-size: 12px; }
.composer-agent-picks :deep(.n-base-selection__placeholder) { font-size: 12px; }
.agent-model-badge {
  display: inline-flex; align-items: center; padding: 2px 8px;
  background: #f0f4ff; border: 1px solid #d4e0ff; border-radius: 6px;
  font-size: 12px; color: #2563eb; white-space: nowrap; max-width: 200px;
  overflow: hidden; text-overflow: ellipsis;
}
.agent-model-none { color: #9ca3af; background: #f9fafb; border-color: #e5e7eb; }
.thinking-switch { display: flex; flex-shrink: 0; align-items: center; gap: 6px; padding: 0 2px 0 4px; }
.thinking-switch-label { font-size: 12px; color: #6b7280; user-select: none; white-space: nowrap; }
.composer-toolbar-right { display: flex; flex-shrink: 0; align-items: center; gap: 6px; }
.tb-select.tb-agent { width: 140px; min-width: 100px; }
.composer-file-input { position: fixed; left: -9999px; top: 0; width: 1px; height: 1px; opacity: 0; }
@media (max-width: 560px) {
  .tb-select.tb-agent { width: 120px; min-width: 90px; }
  .agent-model-badge { max-width: 130px; }
}

/* 设置面板 */
.settings-overlay { position: fixed; inset: 0; z-index: 1000; display: flex; align-items: stretch; }
.settings-panel { width: 268px; flex-shrink: 0; background: #ffffff; box-shadow: 2px 0 16px rgba(15,23,42,0.12); display: flex; flex-direction: column; overflow: hidden; }
.settings-panel-head { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px 12px; border-bottom: 1px solid #e5e7eb; flex-shrink: 0; }
.settings-panel-title { font-size: 15px; font-weight: 600; color: #111827; }
.settings-nav { flex: 1; display: flex; flex-direction: column; padding: 10px 8px; gap: 2px; overflow-y: auto; }
.settings-nav-item { display: flex; align-items: center; gap: 12px; padding: 11px 12px; border-radius: 10px; cursor: pointer; transition: background 0.12s; }
.settings-nav-item:hover { background: #f3f4f6; }
.settings-nav-icon { flex-shrink: 0; font-size: 20px; color: #4b5563; }
.settings-nav-text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.settings-nav-label { font-size: 14px; font-weight: 500; color: #111827; }
.settings-nav-sub { font-size: 12px; color: #9ca3af; }
.settings-nav-arrow { flex-shrink: 0; font-size: 16px; color: #d1d5db; }
.settings-backdrop { flex: 1; background: rgba(15,23,42,0.3); cursor: pointer; }
.settings-slide-enter-active, .settings-slide-leave-active { transition: opacity 0.2s ease; }
.settings-slide-enter-active .settings-panel, .settings-slide-leave-active .settings-panel { transition: transform 0.22s ease; }
.settings-slide-enter-from, .settings-slide-leave-to { opacity: 0; }
.settings-slide-enter-from .settings-panel, .settings-slide-leave-to .settings-panel { transform: translateX(-100%); }
</style>
