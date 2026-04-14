<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  NAvatar,
  NButton,
  NDrawer,
  NDropdown,
  NEllipsis,
  NIcon,
  NInput,
  NSelect,
  NSwitch,
  NText,
  NTooltip,
  useDialog,
  useMessage,
  type DropdownOption,
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
  TrashOutline,
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
import { listLlmProviders, type LlmProviderOption } from '@/api/llmProviders'
import { chatStreamFetch, type ChatRequestBody } from '@/api/chat'
import { uploadChatFile } from '@/api/upload'
import { renderChatMarkdown } from '@/utils/chatMarkdown'

type PendingAttachment = { id: string; file: File; preview?: string }

let attachSeq = 0

const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()

function onMarkdownAreaClick(ev: MouseEvent) {
  const t = ev.target as HTMLElement | null
  if (!t?.closest('.md-copy-btn')) {
    return
  }
  ev.preventDefault()
  const block = t.closest('.md-code-block')
  const codeEl = block?.querySelector('pre.md-code-pre code') as HTMLElement | null
  const text = codeEl?.textContent ?? ''
  if (!text) {
    message.warning('无可复制内容')
    return
  }
  void navigator.clipboard.writeText(text).then(
    () => {
      message.success('已复制')
    },
    () => {
      message.error('复制失败，请检查浏览器权限')
    },
  )
}

const userMenuOptions: DropdownOption[] = [
  { label: '系统设置', key: 'settings' },
  { type: 'divider', key: 'd1' },
  { label: '退出登录', key: 'logout' },
]

const settingsOpen = ref(false)

const isMobile = ref(false)
const drawerOpen = ref(false)
const msgScrollRef = ref<HTMLElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
/** 待发送：选择或粘贴的文件；发送前经 /api/file/upload 换 URL */
const pendingAttachments = ref<PendingAttachment[]>([])

const conversations = ref<ConversationItem[]>([])
const sessionId = ref<string | null>(null)
const messages = ref<ChatMessage[]>([])
const input = ref('')
const sending = ref(false)
/** 当前流式请求的取消句柄；停止时 abort 以断开 SSE，后端随之关闭上游 */
const streamAbort = ref<AbortController | null>(null)

const providers = ref<LlmProviderOption[]>([])
const providerCode = ref<string | null>(null)
/** 对应库表 modes_json 的键（逻辑名），作为 ChatRequestBody.mode 传给后端 */
const modeKey = ref<string | null>(null)
/** 是否收集并展示思考流（与请求 thinkingMode 一致；关闭时忽略 delta.reasoning_content） */
const thinkingModeOn = ref(false)

const providerOptions = computed(() =>
  providers.value.map((p) => ({ label: p.displayName || p.code, value: p.code })),
)

/** 会话已有消息时锁定模型选择，切换须开启新会话 */
const modelLocked = computed(() => messages.value.length > 0)

const currentProvider = computed(() =>
  providers.value.find((p) => p.code === providerCode.value) ?? null,
)

const currentModeCapability = computed(() => {
  const p = currentProvider.value
  const k = modeKey.value?.trim()
  if (!p?.modeCapabilities || !k) {
    return null
  }
  const caps = p.modeCapabilities
  if (caps[k]) {
    return caps[k]
  }
  const found = Object.keys(caps).find((x) => x.toLowerCase() === k.toLowerCase())
  return found ? caps[found] : null
})

/**
 * 是否允许待传/粘贴图片：modes_json 里未配置 modeCapabilities 时视为兼容旧数据，不拦截图片。
 */
const currentVisionSupported = computed(() => {
  const p = currentProvider.value
  if (!p?.modeCapabilities || Object.keys(p.modeCapabilities).length === 0) {
    return true
  }
  return currentModeCapability.value?.vision === true
})

function isImageFile(file: File): boolean {
  if (file.type.startsWith('image/')) {
    return true
  }
  const n = file.name.toLowerCase()
  return /\.(png|jpe?g|gif|webp|bmp|heic|heif)$/i.test(n)
}

/** 当前模型不支持视觉时移除待传图片并提示 */
function stripPendingImagesIfNoVision(showToast: boolean) {
  if (currentVisionSupported.value) {
    return
  }
  const kept: PendingAttachment[] = []
  let removed = false
  for (const p of pendingAttachments.value) {
    if (isImageFile(p.file)) {
      if (p.preview) {
        URL.revokeObjectURL(p.preview)
      }
      removed = true
    } else {
      kept.push(p)
    }
  }
  if (removed) {
    pendingAttachments.value = kept
    if (showToast) {
      message.warning('当前模型不支持视觉，已移除待发送的图片')
    }
  }
}

watch(currentVisionSupported, (supported) => {
  if (!supported) {
    stripPendingImagesIfNoVision(true)
  }
})

/** 当前 mode 在配置中开启深度思考，且提供方支持 thinking API 时显示开关 */
const showDeepThinkingSwitch = computed(() => {
  if (!currentProvider.value?.supportsThinking) {
    return false
  }
  const c = currentModeCapability.value
  if (!c) {
    return true
  }
  return c.deepThinking === true
})

watch(showDeepThinkingSwitch, (show) => {
  if (!show) {
    thinkingModeOn.value = false
  }
})

/** 下拉展示去掉末尾「(…)」；提交仍用完整 mode key */
function modeLabelForDisplay(key: string): string {
  const t = key.replace(/\s*\([^)]*\)\s*$/u, '').trim()
  return t || key
}

function fmtCtxWindow(tokens: number): string {
  if (tokens >= 900000) return Math.round(tokens / 1000000) + 'M'
  return Math.round(tokens / 1024) + 'K'
}

const modeOptions = computed(() => {
  const m = currentProvider.value?.modes
  const caps = currentProvider.value?.modeCapabilities
  if (!m || typeof m !== 'object') {
    return []
  }
  return Object.keys(m).map((k) => {
    const cw = caps?.[k]?.contextWindow
    const base = modeLabelForDisplay(k)
    const label = cw ? `${base} · ${fmtCtxWindow(cw)}` : base
    return { label, value: k }
  })
})

function pickDefaultModeKey(p: LlmProviderOption): string | null {
  const raw = p.modes
  if (!raw || typeof raw !== 'object') {
    return null
  }
  const keys = Object.keys(raw)
  if (!keys.length) {
    return null
  }
  const d = (p.defaultMode || '').trim()
  if (d) {
    const exact = keys.find((k) => k === d)
    if (exact) {
      return exact
    }
    const ci = keys.find((k) => k.toLowerCase() === d.toLowerCase())
    if (ci) {
      return ci
    }
  }
  return keys[0]
}

function syncModeKeyForProvider() {
  const p = currentProvider.value
  if (!p) {
    modeKey.value = null
    return
  }
  const picked = pickDefaultModeKey(p)
  modeKey.value = picked ?? (p.defaultMode?.trim() || null)
}

/**
 * 切换「提供方」时调用：深度思考开关始终回到关闭（不跟随系统参数里的默认 thinking，避免一进对话就是开）。
 * 在同一提供方内只换「模型」不会调用本函数，保留用户当前开关。
 */
function resetThinkingSwitchForProviderChange() {
  thinkingModeOn.value = false
}

/**
 * 开启思考时：流式阶段在仅有思考、尚无正文前不渲染下方回复气泡，避免空白占位；
 * 一旦有正文或思考关闭，行为与原先一致。
 */
function showAssistantReplyBubble(m: ChatMessage, index: number): boolean {
  if (!thinkingModeOn.value) {
    return true
  }
  if ((m.content ?? '').trim().length > 0) {
    return true
  }
  const streamingThis =
    sending.value && index === messages.value.length - 1 && m.role === 'assistant'
  if (streamingThis) {
    return false
  }
  return true
}

function checkMobile() {
  isMobile.value = window.matchMedia('(max-width: 768px)').matches
}

async function scrollMessagesToBottom() {
  await nextTick()
  const el = msgScrollRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

/** 流式输出期间调用：仅当用户未主动上滑时才跟随滚动 */
async function scrollToStreamBottom() {
  await nextTick()
  const el = msgScrollRef.value
  if (!el) return
  const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  if (distFromBottom < 120) {
    el.scrollTop = el.scrollHeight
  }
}

watch(
  () =>
    messages.value
      .map(
        (m) =>
          `${m.role}:${(m.content ?? '').length}:${(m.reasoningContent ?? '').length}:${(m.fileUrls ?? []).join(',')}`,
      )
      .join('|'),
  () => {
    void scrollMessagesToBottom()
  },
)

onMounted(async () => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  try {
    await refreshConversations()
  } catch {
    message.error('加载会话列表失败，请确认已登录且后端可用')
  }
  try {
    providers.value = await listLlmProviders()
    if (providers.value.length) {
      providerCode.value = providers.value[0].code
      syncModeKeyForProvider()
      resetThinkingSwitchForProviderChange()
    }
  } catch {
    message.error('加载模型列表失败')
  }
  await scrollMessagesToBottom()
})

watch(providerCode, () => {
  syncModeKeyForProvider()
  resetThinkingSwitchForProviderChange()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
  for (const p of pendingAttachments.value) {
    if (p.preview) {
      URL.revokeObjectURL(p.preview)
    }
  }
})

async function refreshConversations() {
  conversations.value = await listConversations()
}

async function onNewChat() {
  for (const p of pendingAttachments.value) {
    if (p.preview) {
      URL.revokeObjectURL(p.preview)
    }
  }
  pendingAttachments.value = []
  const c = await createConversation()
  await refreshConversations()
  sessionId.value = c.sessionId
  messages.value = []
  if (isMobile.value) {
    drawerOpen.value = false
  }
}

/** 根据会话列表项恢复该会话曾使用的模型提供方与 mode */
function applyConversationModelPrefs(c: ConversationItem) {
  const pc = c.lastProviderCode?.trim()
  const mk = c.lastModeKey?.trim()
  if (!pc || !providers.value.some((p) => p.code === pc)) {
    return
  }
  const switchingProvider = providerCode.value !== pc
  providerCode.value = pc
  void nextTick(() => {
    const keys = modeOptions.value.map((o) => o.value)
    if (mk && keys.includes(mk)) {
      modeKey.value = mk
      return
    }
    if (switchingProvider) {
      syncModeKeyForProvider()
    }
  })
}

async function selectConv(c: ConversationItem) {
  for (const p of pendingAttachments.value) {
    if (p.preview) {
      URL.revokeObjectURL(p.preview)
    }
  }
  pendingAttachments.value = []
  sessionId.value = c.sessionId
  try {
    messages.value = await loadConversationMessages(c.sessionId)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载会话消息失败')
    messages.value = []
  }
  applyConversationModelPrefs(c)
  if (isMobile.value) {
    drawerOpen.value = false
  }
}

function confirmDelete(c: ConversationItem, e?: Event) {
  e?.stopPropagation()
  dialog.warning({
    title: '删除会话',
    content: '是否删除此会话，删除不可撤销',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await deleteConversation(c.sessionId)
      message.success('已删除')
      if (sessionId.value === c.sessionId) {
        sessionId.value = null
        messages.value = []
      }
      await refreshConversations()
    },
  })
}

async function ensureSession(): Promise<string> {
  if (sessionId.value) {
    return sessionId.value
  }
  const c = await createConversation()
  await refreshConversations()
  sessionId.value = c.sessionId
  return c.sessionId
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
  if (imageFiles.length > 0 && !currentVisionSupported.value) {
    message.warning('当前模型不支持视觉')
    if (nonImageFiles.length === 0) {
      return
    }
  }
  for (const file of nonImageFiles) {
    const id = `${++attachSeq}-${file.name}-${file.size}`
    pendingAttachments.value.push({ id, file })
  }
  if (!currentVisionSupported.value) {
    return
  }
  for (const file of imageFiles) {
    const id = `${++attachSeq}-${file.name}-${file.size}`
    const preview = URL.createObjectURL(file)
    pendingAttachments.value.push({ id, file, preview })
  }
}

function onFileInputChange(ev: Event) {
  const el = ev.target as HTMLInputElement
  const list = el.files
  if (!list?.length) {
    return
  }
  addPendingFiles(Array.from(list))
  el.value = ''
}

function removePendingAttachment(index: number) {
  const p = pendingAttachments.value[index]
  if (p?.preview) {
    URL.revokeObjectURL(p.preview)
  }
  pendingAttachments.value.splice(index, 1)
}

function onComposerPaste(ev: ClipboardEvent) {
  const dt = ev.clipboardData
  if (!dt) {
    return
  }
  const files: File[] = []
  for (let i = 0; i < dt.items.length; i++) {
    const it = dt.items[i]
    if (it.kind === 'file') {
      const f = it.getAsFile()
      if (f) {
        files.push(f)
      }
    }
  }
  if (!files.length) {
    return
  }
  ev.preventDefault()
  addPendingFiles(files)
}

function isImageHttpUrl(u: string): boolean {
  const path = (u.split('?')[0] ?? '').toLowerCase()
  return /\.(png|jpe?g|gif|webp|bmp)$/i.test(path)
}

function shortUrlDisplay(u: string): string {
  if (u.length <= 52) {
    return u
  }
  return `${u.slice(0, 40)}…${u.slice(-10)}`
}

function sameUserPayload(m: ChatMessage, text: string, urls: string[]): boolean {
  if (m.role !== 'user') {
    return false
  }
  if ((m.content ?? '').trim() !== text) {
    return false
  }
  const a = m.fileUrls ?? []
  if (a.length !== urls.length) {
    return false
  }
  return a.every((x, i) => x === urls[i])
}

function stopStreaming() {
  streamAbort.value?.abort()
}

function onComposerKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.isComposing) {
    return
  }
  if (event.shiftKey || event.ctrlKey || event.metaKey || event.altKey) {
    return
  }
  event.preventDefault()
  void send()
}

async function send() {
  const text = input.value.trim()
  if ((!text && !pendingAttachments.value.length) || sending.value) {
    return
  }
  if (!providerCode.value) {
    message.warning('请选择模型提供方')
    return
  }
  if (!modeKey.value) {
    message.warning('请选择具体模型（mode）')
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
    if (p.preview) {
      URL.revokeObjectURL(p.preview)
    }
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
    provider: providerCode.value,
    mode: modeKey.value,
    sessionId: sid,
    messages: messages.value,
    stream: true,
    thinkingMode: thinkingModeOn.value,
  }

  const ac = new AbortController()
  streamAbort.value = ac
  let streamSettled = false
  const settleStreamEnd = async () => {
    if (streamSettled) {
      return
    }
    streamSettled = true
    // 给助手消息打上完成时间戳
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
          if (delta.content) {
            m.content += delta.content
          }
          messages.value = [...messages.value]
          void scrollToStreamBottom()
        }
      },
      async () => {
        await settleStreamEnd()
      },
      (err) => {
        message.error(err.message)
        messages.value = messages.value.slice(0, -1)
        void settleStreamEnd()
      },
      {
        signal: ac.signal,
        onAbort: async () => {
          await settleStreamEnd()
        },
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
  <!-- 不用 Naive NLayout：其内部滚动容器会破坏纵向 flex，导致输入框悬在中间、底部留白 -->
  <div class="layout-root">

    <!-- 系统设置面板 -->
    <Transition name="settings-slide">
      <div v-if="settingsOpen" class="settings-overlay">
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
                <span class="settings-nav-label">API Key 管理</span>
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
        <!-- 点击空白处关闭 -->
        <div class="settings-backdrop" @click="settingsOpen = false" />
      </div>
    </Transition>

    <aside v-if="!isMobile" class="sidebar ds-sidebar">
      <div class="brand">
        <span class="brand-mark">智蚁</span>
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
          @click="selectConv(c)"
        >
          <n-ellipsis style="flex: 1">{{ c.title }}</n-ellipsis>
          <n-button quaternary size="tiny" @click="(e) => confirmDelete(c, e)">
            <template #icon>
              <n-icon :component="TrashOutline" />
            </template>
          </n-button>
        </div>
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
                <n-button quaternary size="tiny" class="user-foot-more" aria-label="菜单"><template #icon><n-icon :component="SettingsOutline" /></template></n-button>
              </n-dropdown>
            </div>
          </div>
        </div>
      </div>
    </aside>

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

        <div v-if="!providers.length" class="banner-warn">
          <n-text depth="2">
            暂无可用大模型提供方。请点击左下角「...」→「系统设置」新增提供方并填写 API Key。
          </n-text>
        </div>

        <div ref="msgScrollRef" class="msg-scroll">
          <div class="msg-scroll-inner">
            <div v-if="!messages.length" class="empty-hint">
              <p>开始与 AI 对话</p>
              <p class="sub">左侧选择历史会话，或点击「开启新对话」</p>
            </div>
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
              placeholder="输入消息或粘贴截图；Enter 发送，组合键回车换行"
              :autosize="{ minRows: 2, maxRows: 6 }"
              class="composer-textarea"
              @paste="onComposerPaste"
              @keydown="onComposerKeydown"
            />
            <div class="composer-toolbar">
              <div class="composer-toolbar-left composer-model-picks">
                <n-tooltip :disabled="!modelLocked" placement="top">
                  <template #trigger>
                    <n-select
                      v-model:value="providerCode"
                      :options="providerOptions"
                      placeholder="提供方"
                      class="tb-select tb-provider"
                      size="small"
                      filterable
                      :consistent-menu-width="false"
                      :disabled="modelLocked"
                      :status="!providerCode && !providers.length ? 'warning' : undefined"
                    />
                  </template>
                  已开始会话，切换模型请开启新对话
                </n-tooltip>
                <n-tooltip :disabled="!modelLocked" placement="top">
                  <template #trigger>
                    <n-select
                      v-model:value="modeKey"
                      :options="modeOptions"
                      placeholder="模型"
                      class="tb-select tb-mode"
                      size="small"
                      filterable
                      :disabled="modelLocked || !modeOptions.length"
                      :consistent-menu-width="false"
                    />
                  </template>
                  已开始会话，切换模型请开启新对话
                </n-tooltip>
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

    <n-drawer v-model:show="drawerOpen" :width="288" placement="left" class="drawer-side">
      <div class="brand drawer-brand">
        <span class="brand-mark">智蚁</span>
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
          @click="selectConv(c)"
        >
          <n-ellipsis style="flex: 1">{{ c.title }}</n-ellipsis>
          <n-button quaternary size="tiny" @click="(e) => confirmDelete(c, e)">
            <template #icon>
              <n-icon :component="TrashOutline" />
            </template>
          </n-button>
        </div>
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
                <n-button quaternary size="tiny" class="user-foot-more" aria-label="菜单"><template #icon><n-icon :component="SettingsOutline" /></template></n-button>
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
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.02em;
  border-bottom: 1px solid #e5e5e5;
}
.drawer-brand {
  margin: -4px -4px 0;
}
.brand-mark {
  color: #111827;
}
.brand-sub {
  color: #2563eb;
  margin-left: 4px;
}
.side-head {
  padding: 12px;
  border-bottom: 1px solid #e5e5e5;
}
.banner-warn {
  flex-shrink: 0;
  padding: 8px 16px;
  background: #fffbeb;
  border-bottom: 1px solid #fde68a;
  font-size: 13px;
}
.side-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}
.conv-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 10px 12px;
  margin: 0 8px 4px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  color: #4b5563;
}
.conv-item:hover {
  background: #ececf0;
}
.conv-item.active {
  background: #e0e7ff;
  color: #111827;
}
.side-foot {
  padding: 12px;
  border-top: 1px solid #e5e5e5;
}
.user-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.user-foot-avatar {
  flex-shrink: 0;
}
.user-foot-main {
  flex: 1;
  min-width: 0;
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-right: 44px;
}
.user-foot-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  min-height: 24px;
}
.user-foot-name {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 500;
  color: #111827;
}
.user-foot-menu {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
}
.api-docs-link {
  font-size: 13px;
  color: #6b7280;
  cursor: pointer;
  user-select: none;
}
.api-docs-link:hover {
  color: #111827;
}
.user-foot-more {
  flex-shrink: 0;
  min-width: 40px;
  min-height: 40px;
  padding: 0;
  font-size: 22px;
  line-height: 1;
  color: #6b7280;
  display: flex;
  align-items: center;
  justify-content: center;
}
.user-foot-more :deep(.n-icon) {
  font-size: 24px;
}
.user-foot-more:hover {
  color: #111827;
}
.top-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid #e5e5e5;
  background: #ffffff;
}
.msg-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 20px 16px 16px;
  -webkit-overflow-scrolling: touch;
}
.msg-scroll-inner {
  max-width: 880px;
  width: 100%;
  margin: 0 auto;
}
.empty-hint {
  text-align: center;
  color: #9ca3af;
  margin-top: 15vh;
}
.empty-hint .sub {
  font-size: 13px;
  margin-top: 8px;
}
.msg-row {
  display: flex;
  margin-bottom: 16px;
}
.msg-row.user {
  justify-content: flex-end;
}
.msg-row.assistant {
  justify-content: flex-start;
}
.msg-bubble-col {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  max-width: 85%;
  gap: 4px;
}
.assistant-stack {
  max-width: 85%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
}
.msg-time {
  font-size: 11px;
  color: #9ca3af;
  padding: 0 2px;
  white-space: nowrap;
}
.msg-time--right {
  text-align: right;
}
.thinking-details {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #f9fafb;
  padding: 8px 10px;
}
.thinking-summary {
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  color: #6b7280;
  user-select: none;
}
.thinking-pre {
  margin-top: 8px;
  font-size: 13px;
  color: #4b5563;
}
.thinking-md-wrap {
  margin-top: 8px;
  font-size: 13px;
  color: #4b5563;
  line-height: 1.55;
}
.bubble {
  max-width: 100%;
  padding: 10px 14px;
}
.msg-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.55;
  color: #111827;
}
.msg-md-placeholder {
  min-height: 1em;
  color: transparent;
}
.msg-md {
  font-size: 14px;
  line-height: 1.55;
  color: #111827;
  word-break: break-word;
}
.msg-md :deep(p) {
  margin: 0.4em 0;
}
.msg-md :deep(p:first-child) {
  margin-top: 0;
}
.msg-md :deep(p:last-child) {
  margin-bottom: 0;
}
.msg-md :deep(ul),
.msg-md :deep(ol) {
  margin: 0.45em 0;
  padding-left: 1.35em;
}
.msg-md :deep(h1),
.msg-md :deep(h2),
.msg-md :deep(h3),
.msg-md :deep(h4) {
  margin: 0.65em 0 0.35em;
  font-weight: 600;
  line-height: 1.3;
}
.msg-md :deep(h1) {
  font-size: 1.25em;
}
.msg-md :deep(h2) {
  font-size: 1.15em;
}
.msg-md :deep(h3),
.msg-md :deep(h4) {
  font-size: 1.05em;
}
.msg-md :deep(blockquote) {
  margin: 0.5em 0;
  padding: 0.35em 0 0.35em 0.85em;
  border-left: 3px solid #d1d5db;
  color: #4b5563;
}
.msg-md :deep(a) {
  color: #2563eb;
  text-decoration: underline;
}
.msg-md :deep(p > code),
.msg-md :deep(li > code),
.msg-md :deep(td > code) {
  background: #f3f4f6;
  padding: 0.12em 0.38em;
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
  font-size: 0.9em;
}
.msg-md :deep(.md-code-block) {
  margin: 0.75em 0;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  overflow: hidden;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05);
}
.msg-md :deep(.md-code-head) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 10px;
  background: #f3f4f6;
  border-bottom: 1px solid #e5e7eb;
  font-size: 12px;
}
.msg-md :deep(.md-code-lang) {
  color: #6b7280;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  text-transform: lowercase;
}
.msg-md :deep(.md-copy-btn) {
  flex-shrink: 0;
  margin: 0;
  padding: 3px 10px;
  font-size: 12px;
  line-height: 1.35;
  color: #374151;
  background: #ffffff;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  cursor: pointer;
}
.msg-md :deep(.md-copy-btn:hover) {
  background: #f9fafb;
  border-color: #9ca3af;
}
.msg-md :deep(.md-code-pre) {
  margin: 0;
  padding: 12px 14px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.5;
  background: #fafafa;
}
.msg-md :deep(.md-code-pre code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
}
.msg-md :deep(table) {
  border-collapse: collapse;
  margin: 0.6em 0;
  font-size: 0.95em;
}
.msg-md :deep(th),
.msg-md :deep(td) {
  border: 1px solid #e5e7eb;
  padding: 0.35em 0.6em;
}
.msg-md :deep(th) {
  background: #f9fafb;
}
.composer {
  flex: 0 0 auto;
  width: 100%;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom, 0px));
  background: #ffffff;
  border-top: 1px solid #e5e5e5;
  box-sizing: border-box;
}
.composer-box {
  max-width: 880px;
  width: 100%;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 10px 12px 8px;
  background: #fafafa;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}
.composer-pending-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 4px 10px;
  margin-bottom: 2px;
  border-bottom: 1px dashed #e5e7eb;
}
.composer-pending-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 28px 4px 4px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  max-width: 100%;
}
.composer-pending-thumb {
  display: block;
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: 6px;
}
.composer-pending-name {
  font-size: 12px;
  color: #4b5563;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 4px 4px 4px 8px;
}
.user-msg-files {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.user-img-wrap {
  display: block;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  max-width: min(220px, 70vw);
}
.user-msg-img {
  display: block;
  width: 100%;
  height: auto;
  max-height: 200px;
  object-fit: contain;
  vertical-align: middle;
}
.user-file-link {
  font-size: 12px;
  color: #2563eb;
  word-break: break-all;
  text-decoration: underline;
  max-width: 100%;
}
.composer-textarea :deep(.n-input-wrapper) {
  padding: 4px 4px 8px;
}
/* 默认约两行；随内容增高，最高 6 行（maxRows），超出在框内滚动 */
.composer-textarea :deep(textarea.n-input__textarea-el) {
  min-height: 0;
  overflow-y: auto;
  line-height: 1.55;
  font-size: 15px;
}
.composer-attach-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 4px 8px;
}
.composer-attach-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 2px 8px;
  font-size: 12px;
  color: #374151;
  background: #eef2ff;
  border: 1px solid #e0e7ff;
  border-radius: 999px;
}
.composer-attach-remove {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  margin: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #6b7280;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}
.composer-attach-remove:hover {
  color: #111827;
  background: rgba(0, 0, 0, 0.06);
}
.composer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 4px 4px;
  margin-top: 2px;
  border-top: 1px solid #ececf1;
}
.composer-toolbar-left {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 0 1 auto;
}
.composer-model-picks {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.composer-model-picks :deep(.n-base-selection) {
  min-height: 30px;
  font-size: 12px;
  border-radius: 8px;
}
.composer-model-picks :deep(.n-base-selection__border) {
  border-radius: 8px;
}
.composer-model-picks :deep(.n-base-selection-label) {
  font-size: 12px;
}
.composer-model-picks :deep(.n-base-selection__placeholder) {
  font-size: 12px;
}
.thinking-switch {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 6px;
  padding: 0 2px 0 4px;
}
.thinking-switch-label {
  font-size: 12px;
  color: #6b7280;
  user-select: none;
  white-space: nowrap;
}
.composer-toolbar-right {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 6px;
}
.tb-select.tb-provider {
  width: 118px;
}
.tb-select.tb-mode {
  width: 156px;
  min-width: 96px;
  max-width: 168px;
}
.composer-file-input {
  position: fixed;
  left: -9999px;
  top: 0;
  width: 1px;
  height: 1px;
  opacity: 0;
}
@media (max-width: 560px) {
  .tb-select.tb-provider {
    width: 108px;
    min-width: 100px;
  }
  .tb-select.tb-mode {
    width: min(148px, 38vw);
    max-width: 156px;
    min-width: 92px;
  }
}

/* ── 系统设置面板 ─────────────────────────────────────────── */
.settings-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: stretch;
}
.settings-panel {
  width: 268px;
  flex-shrink: 0;
  background: #ffffff;
  box-shadow: 2px 0 16px rgba(15, 23, 42, 0.12);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.settings-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 12px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.settings-panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}
.settings-nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 10px 8px;
  gap: 2px;
  overflow-y: auto;
}
.settings-nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.12s;
}
.settings-nav-item:hover {
  background: #f3f4f6;
}
.settings-nav-icon {
  flex-shrink: 0;
  font-size: 20px;
  color: #4b5563;
}
.settings-nav-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.settings-nav-label {
  font-size: 14px;
  font-weight: 500;
  color: #111827;
}
.settings-nav-sub {
  font-size: 12px;
  color: #9ca3af;
}
.settings-nav-arrow {
  flex-shrink: 0;
  font-size: 16px;
  color: #d1d5db;
}
.settings-backdrop {
  flex: 1;
  background: rgba(15, 23, 42, 0.3);
  cursor: pointer;
}

/* 滑入动画 */
.settings-slide-enter-active,
.settings-slide-leave-active {
  transition: opacity 0.2s ease;
}
.settings-slide-enter-active .settings-panel,
.settings-slide-leave-active .settings-panel {
  transition: transform 0.22s ease;
}
.settings-slide-enter-from,
.settings-slide-leave-to {
  opacity: 0;
}
.settings-slide-enter-from .settings-panel,
.settings-slide-leave-to .settings-panel {
  transform: translateX(-100%);
}
</style>
