<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import type { DataTableColumns } from 'naive-ui'
import {
  NButton,
  NCard,
  NDataTable,
  NDrawer,
  NDrawerContent,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NSpace,
  NSpin,
  NTag,
  NTabs,
  NTabPane,
  NText,
  NSelect,
  NSwitch,
  useDialog,
  useMessage,
} from 'naive-ui'
import {
  adminCreateAiApp,
  adminDeleteAiApp,
  adminListAiApps,
  adminUpdateAiApp,
  adminAiAppUsage,
  adminAiAppSessionMessages,
  type AiAppRow,
  type AiAppUsage,
  type RecentTurnRow,
} from '@/api/adminAiApps'
import {
  adminListTools,
  adminGetAppToolBindings,
  adminSaveAppToolBindings,
  type AiToolRow,
  type AppToolBindingInput,
} from '@/api/adminTools'
import { adminListLlmModels, type LlmModelAdminRow } from '@/api/adminLlmModel'
import type { ChatMessage } from '@/api/conversations'
import { useAuthStore } from '@/stores/auth'
import { renderChatMarkdown } from '@/utils/chatMarkdown'
import {
  adminCreateContextField,
  adminDeleteContextField,
  adminListContextFields,
  adminTestContextField,
  adminUpdateContextField,
  type ContextFieldRow,
} from '@/api/adminContextFields'

const message = useMessage()
const dialog = useDialog()
const authStore = useAuthStore()

const loading = ref(false)
const rows = ref<AiAppRow[]>([])

// ==================== 用户数据管理（原接口上下文配置） ====================
const showContextDrawer = ref(false)
const showContextForm = ref(false)
const contextEditId = ref<number | null>(null)
const contextForm = ref<ContextFieldRow>({
  id: 0, fieldKey: '', label: '', fieldType: 'String', parseExpression: null, description: null, enabled: true, createdAt: null,
})

// 解析路径分段（用于分层级输入框）
const parseExpressionSegments = ref<string[]>([])

function initParseExpressionSegments() {
  const expr = contextForm.value.parseExpression
  if (!expr) {
    parseExpressionSegments.value = []
  } else {
    parseExpressionSegments.value = expr.split('.').filter(s => s.trim() !== '')
  }
}

function updateParseExpression() {
  contextForm.value.parseExpression = parseExpressionSegments.value.filter(s => s.trim() !== '').join('.')
}

function addParseExpressionSegment() {
  parseExpressionSegments.value.push('')
}

function removeParseExpressionSegment() {
  parseExpressionSegments.value.pop()
  updateParseExpression()
}

const fieldTypeOptions = [
  { label: 'String', value: 'String' }, { label: 'Number', value: 'Number' },
  { label: 'Boolean', value: 'Boolean' }, { label: 'Object', value: 'Object' },
  { label: 'Array', value: 'Array' },
]

const contextFields = ref<ContextFieldRow[]>([])

const contextColumns: DataTableColumns<ContextFieldRow> = [
  {
    title: '字段 Key', key: 'fieldKey', width: 150,
    render: (row) => h(NText, { code: true, style: 'font-size:12px' }, { default: () => row.fieldKey }),
  },
  {
    title: '显示名', key: 'label', width: 120,
    render: (row) => h('span', { style: 'font-size:12px' }, { default: () => row.label }),
  },
  {
    title: '字段类型', key: 'fieldType', width: 100,
    render: (row) => h(NTag, { size: 'small', type: 'info' }, { default: () => row.fieldType || 'String' }),
  },
  {
    title: '解析逻辑', key: 'parseExpression', ellipsis: { tooltip: true },
    render: (row) => h('span', { style: 'font-size:11px; color:#666; font-family:monospace' }, { default: () => row.parseExpression || '-' }),
  },
  {
    title: '状态', key: 'enabled', width: 70,
    render: (row) => h(NTag, { size: 'small', type: row.enabled ? 'success' : 'default' }, { default: () => (row.enabled ? '启用' : '禁用') }),
  },
  {
    title: '操作', key: 'actions', width: 220, fixed: 'right',
    render: (row) => h(NSpace, { size: 4, wrap: false }, {
      default: () => [
        h(NButton, { size: 'small', onClick: () => openContextEdit(row) }, { default: () => '编辑' }),
        h(NButton, { size: 'small', type: 'info', ghost: true, onClick: () => openContextTest(row) }, { default: () => '测试运行' }),
        h(NButton, { size: 'small', type: 'error', onClick: () => handleContextDelete(row) }, { default: () => '删除' }),
      ],
    }),
  },
]

// 上下文字段测试
const showCtxTestModal = ref(false)
const ctxTestField = ref<ContextFieldRow | null>(null)
const ctxTestRunning = ref(false)
const ctxTestFound = ref<boolean | null>(null)
const ctxTestValue = ref<string | null>(null)
const ctxTestError = ref<string | null>(null)
const ctxTestExpression = ref('')

function openContextTest(row: ContextFieldRow) {
  ctxTestField.value = row
  ctxTestFound.value = null
  ctxTestValue.value = null
  ctxTestError.value = null
  ctxTestExpression.value = row.parseExpression || ''
  showCtxTestModal.value = true
}

async function runContextTest() {
  if (!ctxTestField.value) return
  ctxTestRunning.value = true
  ctxTestFound.value = null
  ctxTestValue.value = null
  ctxTestError.value = null
  try {
    const res = await adminTestContextField(ctxTestField.value.id)
    ctxTestFound.value = res.found
    ctxTestValue.value = res.value
    ctxTestExpression.value = res.expression
    if (res.error) ctxTestError.value = res.error
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    ctxTestError.value = err.response?.data?.message || err.message || '请求失败'
  } finally {
    ctxTestRunning.value = false
  }
}

function openContextDrawer() {
  showContextDrawer.value = true
  showContextForm.value = false
  void loadContextFields()
}

function openContextCreate() {
  contextEditId.value = null
  contextForm.value = { id: 0, fieldKey: '', label: '', fieldType: 'String', parseExpression: null, description: null, enabled: true, createdAt: null }
  parseExpressionSegments.value = []
  showContextForm.value = true
}

function openContextEdit(row: ContextFieldRow) {
  contextEditId.value = row.id
  contextForm.value = { ...row }
  initParseExpressionSegments()
  showContextForm.value = true
}

async function handleContextSave() {
  if (!contextForm.value.fieldKey.trim()) { message.warning('请填写字段 Key'); return }
  if (!contextForm.value.label.trim()) { message.warning('请填写显示名'); return }
  try {
    updateParseExpression()
    const payload = {
      fieldKey: contextForm.value.fieldKey.trim(),
      label: contextForm.value.label.trim(),
      fieldType: contextForm.value.fieldType || 'String',
      parseExpression: contextForm.value.parseExpression?.trim() || undefined,
      description: contextForm.value.description?.trim() || undefined,
      enabled: contextForm.value.enabled,
    }
    if (contextEditId.value && contextEditId.value > 0) {
      await adminUpdateContextField(contextEditId.value, payload)
      message.success('已更新')
    } else {
      await adminCreateContextField(payload)
      message.success('已创建')
    }
    showContextForm.value = false
    await loadContextFields()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '保存失败')
  }
}

async function handleContextDelete(row: ContextFieldRow) {
  try {
    await adminDeleteContextField(row.id)
    message.success('已删除')
    await loadContextFields()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '删除失败')
  }
}

async function loadContextFields() {
  try {
    const fieldsRes = await adminListContextFields()
    contextFields.value = fieldsRes
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '加载失败')
  }
}

// ---- 模型选项 ----
const llmModels = ref<LlmModelAdminRow[]>([])
const modelOptions = computed(() =>
  llmModels.value.map((m) => ({
    label: `${m.providerDisplayName} · ${m.name}`,
    value: m.id,
  })),
)

// ---- 新建应用 ----
const showCreate = ref(false)
const createForm = ref(defaultForm())

// ---- 编辑应用 ----
const editId = ref<number | null>(null)
const editForm = ref(defaultForm())

// ---- 嵌入网站 ----
const showEmbed = ref(false)
const embedRow = ref<AiAppRow | null>(null)
const embedActiveTab = ref('pc')

// ---- Prompt 配置 ----
const showPrompt = ref(false)
const promptRow = ref<AiAppRow | null>(null)
const promptForm = ref({
  systemRole: '',
  systemTask: '',
  systemConstraints: '',
})

// ---- 用量抽屉 ----
const usageOpen = ref(false)
const usageAppId = ref<number | null>(null)
const usageAppName = ref('')
const usageLoading = ref(false)
const usageData = ref<AiAppUsage | null>(null)

// ---- 会话消息弹窗 ----
const msgOpen = ref(false)
const msgSessionId = ref('')
const msgLoading = ref(false)
const msgList = ref<ChatMessage[]>([])

// ---- 工具配置 ----
const allTools = ref<AiToolRow[]>([])
const showToolConfigModal = ref(false)
const toolConfigAppId = ref<number | null>(null)
const toolConfigAppname = ref('')
const toolConfigList = ref<ToolConfigItem[]>([])
const toolConfigLoading = ref(false)
const toolConfigSelectedToolId = ref<number | null>(null)

interface ToolConfigItem {
  toolId: number
  toolName: string
  toolLabel: string
  toolDescription: string
  callStrategy: string | null
}

const toolConfigToolOptions = computed(() => {
  // 过滤掉已添加的工具
  const addedIds = new Set(toolConfigList.value.map(item => item.toolId))
  return allTools.value
    .filter(t => !addedIds.has(t.id))
    .map(t => ({ label: `${t.label}（${t.name}）`, value: t.id }))
})

async function openToolConfig(r: AiAppRow) {
  toolConfigAppId.value = r.id
  toolConfigAppname.value = r.name
  toolConfigList.value = []
  toolConfigSelectedToolId.value = null
  showToolConfigModal.value = true
  toolConfigLoading.value = true
  try {
    // 获取所有工具
    allTools.value = await adminListTools()
    // 获取已绑定的工具及策略
    const boundTools = await adminGetAppToolBindings(r.id)
    // 转换为列表
    toolConfigList.value = boundTools.map(b => ({
      toolId: b.toolId,
      toolName: b.toolName,
      toolLabel: b.toolLabel,
      toolDescription: b.toolDescription,
      callStrategy: b.callStrategy,
    }))
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || '加载工具列表失败')
  } finally {
    toolConfigLoading.value = false
  }
}

function addToolConfig() {
  if (!toolConfigSelectedToolId.value) {
    message.warning('请选择工具')
    return
  }
  // 检查是否已添加
  if (toolConfigList.value.some(item => item.toolId === toolConfigSelectedToolId.value)) {
    message.warning('该工具已添加')
    return
  }
  const tool = allTools.value.find(t => t.id === toolConfigSelectedToolId.value)
  if (!tool) return
  toolConfigList.value.push({
    toolId: tool.id,
    toolName: tool.name,
    toolLabel: tool.label,
    toolDescription: tool.description,
    callStrategy: null,
  })
  toolConfigSelectedToolId.value = null
}

function removeToolConfig(index: number) {
  toolConfigList.value.splice(index, 1)
}

async function saveToolConfig() {
  if (!toolConfigAppId.value) return
  try {
    const bindings: AppToolBindingInput[] = toolConfigList.value.map(item => ({
      toolId: item.toolId,
      callStrategy: item.callStrategy,
    }))
    await adminSaveAppToolBindings(toolConfigAppId.value, bindings)
    message.success('已保存')
    showToolConfigModal.value = false
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || '保存失败')
  }
}

async function loadAllTools() {
  try {
    allTools.value = await adminListTools()
  } catch { /* 忽略 */ }
}

// ---- 推荐问题（新建/编辑共用辅助状态）----
const newSuggestionCreate = ref('')
const newSuggestionEdit = ref('')
const suggestionItemsCreate = ref<string[]>([])
const suggestionItemsEdit = ref<string[]>([])

function defaultForm() {
  return {
    name: '',
    llmModelId: null as number | null,
    welcomeText: '',
    systemRole: '',
    systemTask: '',
    systemConstraints: '',
  }
}

async function load() {
  loading.value = true
  try {
    rows.value = await adminListAiApps()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadLlmModels() {
  try {
    llmModels.value = await adminListLlmModels()
  } catch { /* 忽略 */ }
}

onMounted(() => {
  void load()
  void loadLlmModels()
  void loadAllTools()
  void loadContextFields()
})

// ---------- 新建 ----------
function openCreate() {
  createForm.value = defaultForm()
  suggestionItemsCreate.value = []
  newSuggestionCreate.value = ''
  showCreate.value = true
}

async function submitCreate() {
  const name = createForm.value.name.trim()
  if (!name) {
    message.warning('请填写应用名称')
    return
  }
  const suggestionsJson =
    suggestionItemsCreate.value.length > 0
      ? JSON.stringify(suggestionItemsCreate.value)
      : undefined
  try {
    const created = await adminCreateAiApp({
      name,
      llmModelId: createForm.value.llmModelId != null ? createForm.value.llmModelId : undefined,
      welcomeText: createForm.value.welcomeText || undefined,
      suggestions: suggestionsJson,
      systemRole: undefined,
      systemTask: undefined,
      systemConstraints: undefined,
    })
    showCreate.value = false
    message.success('已创建，是否立即配置 Prompt？')
    await load()
    // 打开 Prompt 配置弹窗
    openPrompt(created)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '创建失败')
  }
}

// ---------- 编辑 ----------
function openEdit(r: AiAppRow) {
  editId.value = r.id
  editForm.value = {
    name: r.name,
    llmModelId: r.llmModelId,
    welcomeText: r.welcomeText || '',
    systemRole: r.systemRole || '',
    systemTask: r.systemTask || '',
    systemConstraints: r.systemConstraints || '',
  }
  suggestionItemsEdit.value = r.suggestions ? JSON.parse(r.suggestions) : []
  newSuggestionEdit.value = ''
}

async function submitEdit() {
  const id = editId.value
  if (id == null) return
  const name = editForm.value.name.trim()
  if (!name) {
    message.warning('请填写应用名称')
    return
  }
  const suggestionsJson =
    suggestionItemsEdit.value.length > 0
      ? JSON.stringify(suggestionItemsEdit.value)
      : ''
  try {
    await adminUpdateAiApp(id, {
      name,
      llmModelId: editForm.value.llmModelId != null ? editForm.value.llmModelId : undefined,
      welcomeText: editForm.value.welcomeText,
      suggestions: suggestionsJson,
      systemRole: editForm.value.systemRole,
      systemTask: editForm.value.systemTask,
      systemConstraints: editForm.value.systemConstraints,
    })
    editId.value = null
    message.success('已保存')
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '保存失败')
  }
}

// ---------- 删除 ----------
function confirmDelete(r: AiAppRow) {
  dialog.warning({
    title: '删除应用',
    content: `确定删除「${r.name}」？已关联该应用的集成将失去 AI 能力配置。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await adminDeleteAiApp(r.id)
        message.success('已删除')
        await load()
      } catch (e: unknown) {
        const err = e as { response?: { data?: { message?: string } }; message?: string }
        message.error(err.response?.data?.message || err.message || '删除失败')
      }
    },
  })
}

// ---------- 嵌入网站 ----------
function openEmbed(r: AiAppRow) {
  embedRow.value = r
  embedActiveTab.value = 'pc'
  showEmbed.value = true
}

// ---------- 用量 ----------
async function openUsage(r: AiAppRow) {
  usageAppId.value = r.id
  usageAppName.value = r.name
  usageData.value = null
  usageOpen.value = true
  usageLoading.value = true
  try {
    usageData.value = await adminAiAppUsage(r.id)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '加载用量失败')
  } finally {
    usageLoading.value = false
  }
}

async function openSessionMessages(appId: number, sessionId: string) {
  msgSessionId.value = sessionId
  msgList.value = []
  msgOpen.value = true
  msgLoading.value = true
  try {
    msgList.value = await adminAiAppSessionMessages(appId, sessionId)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '加载消息失败')
  } finally {
    msgLoading.value = false
  }
}

async function copySessionId() {
  try {
    await navigator.clipboard.writeText(msgSessionId.value)
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}

// ---------- Prompt 配置 ----------
function openPrompt(r: AiAppRow) {
  promptRow.value = r
  promptForm.value = {
    systemRole: r.systemRole || '',
    systemTask: r.systemTask || '',
    systemConstraints: r.systemConstraints || '',
  }
  showPrompt.value = true
}

async function submitPrompt() {
  const row = promptRow.value
  const id = row?.id
  if (id == null || row == null) return
  try {
    await adminUpdateAiApp(id, {
      name: row.name,
      llmModelId: row.llmModelId ?? undefined,
      welcomeText: row.welcomeText || undefined,
      suggestions: row.suggestions || '',
      systemRole: promptForm.value.systemRole || undefined,
      systemTask: promptForm.value.systemTask || undefined,
      systemConstraints: promptForm.value.systemConstraints || undefined,
    })
    showPrompt.value = false
    message.success('已保存')
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '保存失败')
  }
}

const embedOrigin = computed(() => window.location.origin)

const embedUrl = computed(() => {
  const aid = embedRow.value?.id ?? ''
  // 只保留 aid 和 phone 参数，phone 使用占位符提示用户替换
  return `${embedOrigin.value}/embed?aid=${aid}&phone=请输入手机号`
})

function previewUrl(row: AiAppRow): string {
  const params = new URLSearchParams()
  params.set('aid', String(row.id))
  params.set('uid', authStore.userId || '')
  params.set('username', authStore.username || '')
  return `${window.location.origin}/embed?${params.toString()}`
}

const embedCodePc = computed(() => {
  return `<iframe
        src="${embedUrl.value}"
        width="100%"
        height="100%"
        style="height: 100vh; border:none; border-radius:12px; box-shadow:0 4px 24px rgba(0,0,0,.1);"
        allow="clipboard-write">
</iframe>`
})

const embedCodeMobile = computed(() => {
  return `<iframe
        src="${embedUrl.value}"
        width="100%"
        style="height: 100svh; border:none; display:block;"
        allow="clipboard-write">
</iframe>`
})

function copyCode(code: string) {
  navigator.clipboard.writeText(code).then(() => {
    message.success('已复制到剪贴板')
  }).catch(() => {
    message.error('复制失败，请手动复制')
  })
}

// ---------- 推荐问题辅助 ----------
function addSuggestion(items: string[], input: string, setter: (v: string) => void) {
  const text = input.trim()
  if (!text) { message.warning('请输入推荐问题'); return }
  if (items.length >= 10) { message.warning('最多添加 10 个推荐问题'); return }
  items.push(text)
  setter('')
}

function removeSuggestion(items: string[], index: number) {
  items.splice(index, 1)
}

function fmtTime(val: string | null): string {
  if (!val) return '—'
  const d = new Date(val)
  if (isNaN(d.getTime())) return val
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

interface SessionGroup {
  sessionId: string
  userId: string | null
  models: string
  turnCount: number
  totalTokens: number
  lastAt: string | null
}

function groupBySession(turns: RecentTurnRow[]): SessionGroup[] {
  const map = new Map<string, SessionGroup>()
  for (const t of turns) {
    const key = t.sessionId
    if (!map.has(key)) {
      map.set(key, {
        sessionId: key,
        userId: t.userId,
        models: t.model,
        turnCount: 1,
        totalTokens: t.totalTokens ?? 0,
        lastAt: t.createdAt,
      })
    } else {
      const g = map.get(key)!
      g.turnCount += 1
      g.totalTokens += t.totalTokens ?? 0
      if (!g.models.split('、').includes(t.model)) g.models += '、' + t.model
      if (t.createdAt && (!g.lastAt || t.createdAt > g.lastAt)) g.lastAt = t.createdAt
    }
  }
  return Array.from(map.values())
}

function sessionColumns(appId: number): DataTableColumns<SessionGroup> {
  return [
    { title: '用户ID', key: 'userId', width: 130, ellipsis: { tooltip: true }, render: (r) => r.userId ?? '—' },
    { title: '会话ID', key: 'sessionId', width: 100, render: (r) => r.sessionId.slice(0, 8) + '…' },
    { title: '次数', key: 'turnCount', width: 60 },
    { title: '模型', key: 'models', width: 140, ellipsis: { tooltip: true } },
    { title: 'Token', key: 'totalTokens', width: 80 },
    { title: '最近时间', key: 'lastAt', width: 152, render: (r) => fmtTime(r.lastAt) },
    {
      title: '操作',
      key: 'open',
      width: 88,
      fixed: 'right' as const,
      render: (r) =>
        h(
          NButton,
          { size: 'tiny', type: 'primary', ghost: true, onClick: () => void openSessionMessages(appId, r.sessionId) },
          { default: () => '查看消息' },
        ),
    },
  ]
}

const columns: DataTableColumns<AiAppRow> = [
  {
    title: '应用名称',
    key: 'name',
    width: 160,
    ellipsis: { tooltip: true },
  },
  {
    title: '默认模型',
    key: 'modelDisplayName',
    width: 180,
    ellipsis: { tooltip: true },
    render: (r) => r.modelDisplayName ?? h('span', { style: 'color:#bbb' }, '—'),
  },
  {
    title: '开场白',
    key: 'welcomeText',
    width: 100,
    render: (r) =>
      r.welcomeText
        ? h(NTag, { size: 'small', bordered: false, type: 'success' }, () => '已配置')
        : h('span', { style: 'color:#bbb' }, '未配置'),
  },
  {
    title: '系统提示词',
    key: 'systemRole',
    width: 100,
    render: (r) =>
      r.systemRole || r.systemTask || r.systemConstraints
        ? h(NTag, { size: 'small', bordered: false, type: 'info' }, () => '已配置')
        : h('span', { style: 'color:#bbb' }, '未配置'),
  },
  {
    title: '创建时间',
    key: 'createdAt',
    width: 152,
    render: (r) => fmtTime(r.createdAt),
  },
  {
    title: '操作',
    key: 'actions',
    width: 460,
    render: (r) =>
      h(NSpace, { size: 8, wrap: false, justify: 'center' }, () => [
        h(NButton, { size: 'small', onClick: () => openUsage(r) }, { default: () => '用量' }),
        h(NButton, { size: 'small', onClick: () => openEdit(r) }, { default: () => '编辑' }),
        h(
          NButton,
          { size: 'small', type: 'info', ghost: true, onClick: () => openPrompt(r) },
          { default: () => 'Prompt' },
        ),
        h(
          NButton,
          { size: 'small', type: 'warning', ghost: true, onClick: () => openToolConfig(r) },
          { default: () => '工具配置' },
        ),
        h(
          NButton,
          { size: 'small', type: 'success', ghost: true, onClick: () => window.open(previewUrl(r), '_blank') },
          { default: () => '预览' },
        ),
        h(
          NButton,
          { size: 'small', type: 'primary', ghost: true, onClick: () => openEmbed(r) },
          { default: () => '嵌入网站' },
        ),
        h(
          NButton,
          { size: 'small', type: 'error', ghost: true, onClick: () => confirmDelete(r) },
          { default: () => '删除' },
        ),
      ]),
  },
]
</script>

<template>
  <div class="inner">
    <n-card :bordered="false" class="card" title="应用管理">
      <template #header-extra>
        <n-space>
          <n-button @click="openContextDrawer">用户数据</n-button>
          <n-button type="primary" @click="openCreate">+ 新建应用</n-button>
        </n-space>
      </template>
      <n-spin :show="loading">
        <n-data-table
          :columns="columns"
          :data="rows"
          :loading="loading"
          :row-key="(r: AiAppRow) => r.id"
        />
      </n-spin>
    </n-card>
  </div>

  <!-- ============ 新建应用弹窗 ============ -->
  <n-modal
    v-model:show="showCreate"
    preset="card"
    title="新建应用"
    style="width: min(800px, 96vw)"
    :mask-closable="false"
  >
    <template #header>
      <div style="display:flex; align-items:center; justify-content:space-between; width:100%">
        <n-button size="small" type="info" ghost @click="openContextDrawer">用户数据</n-button>
        <span style="font-size:16px; font-weight:600">新建应用</span>
        <span style="width:60px"></span>
      </div>
    </template>
    <n-form label-placement="top">
      <n-form-item label="应用名称" required>
        <n-input v-model:value="createForm.name" placeholder="如 官网客服助手" />
      </n-form-item>

      <n-form-item label="默认对话模型">
        <n-select
          v-model:value="createForm.llmModelId"
          :options="modelOptions"
          placeholder="请选择默认模型（可选）"
          filterable
          clearable
        />
      </n-form-item>

      <n-form-item label="开场白文本（可选）">
        <n-input
          v-model:value="createForm.welcomeText"
          type="textarea"
          placeholder="例如：你好，我是 AI 助手，有什么可以帮助您的吗？"
          :autosize="{ minRows: 2, maxRows: 4 }"
        />
      </n-form-item>

      <n-form-item label="推荐问题（最多 10 个）">
        <div style="width: 100%; display: flex; flex-direction: column; gap: 8px;">
          <div v-if="suggestionItemsCreate.length > 0" class="suggestion-list">
            <div
              v-for="(item, idx) in suggestionItemsCreate"
              :key="idx"
              class="suggestion-item"
            >
              <span class="suggestion-text">{{ item }}</span>
              <n-button size="tiny" type="error" ghost @click="removeSuggestion(suggestionItemsCreate, idx)">删除</n-button>
            </div>
          </div>
          <div v-else class="suggestion-empty">暂无推荐问题</div>
          <div style="display: flex; gap: 8px;">
            <n-input
              v-model:value="newSuggestionCreate"
              style="flex: 1"
              placeholder="输入推荐问题，按 Enter 添加"
              @keydown.enter.prevent="addSuggestion(suggestionItemsCreate, newSuggestionCreate, v => newSuggestionCreate = v)"
            />
            <n-button type="primary" @click="addSuggestion(suggestionItemsCreate, newSuggestionCreate, v => newSuggestionCreate = v)">添加</n-button>
          </div>
        </div>
      </n-form-item>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="showCreate = false">取消</n-button>
        <n-button type="primary" @click="submitCreate">创建</n-button>
      </n-space>
    </template>
  </n-modal>

  <!-- ============ 编辑应用弹窗 ============ -->
  <n-modal
    :show="editId != null"
    preset="card"
    title="编辑应用"
    style="width: min(800px, 96vw)"
    :mask-closable="false"
    @update:show="(v: boolean) => { if (!v) editId = null }"
  >
    <template #header>
      <div style="display:flex; align-items:center; justify-content:space-between; width:100%">
        <n-button size="small" type="info" ghost @click="openContextDrawer">用户数据</n-button>
        <span style="font-size:16px; font-weight:600">编辑应用</span>
        <span style="width:60px"></span>
      </div>
    </template>
    <n-form v-if="editId != null" label-placement="top">
      <n-form-item label="应用名称" required>
        <n-input v-model:value="editForm.name" />
      </n-form-item>

      <n-form-item label="默认对话模型">
        <n-select
          v-model:value="editForm.llmModelId"
          :options="modelOptions"
          placeholder="请选择默认模型（可选）"
          filterable
          clearable
        />
      </n-form-item>

      <n-form-item label="开场白文本（可选）">
        <n-input
          v-model:value="editForm.welcomeText"
          type="textarea"
          placeholder="例如：你好，我是 AI 助手，有什么可以帮助您的吗？"
          :autosize="{ minRows: 2, maxRows: 4 }"
        />
      </n-form-item>

      <n-form-item label="推荐问题（最多 10 个）">
        <div style="width: 100%; display: flex; flex-direction: column; gap: 8px;">
          <div v-if="suggestionItemsEdit.length > 0" class="suggestion-list">
            <div
              v-for="(item, idx) in suggestionItemsEdit"
              :key="idx"
              class="suggestion-item"
            >
              <span class="suggestion-text">{{ item }}</span>
              <n-button size="tiny" type="error" ghost @click="removeSuggestion(suggestionItemsEdit, idx)">删除</n-button>
            </div>
          </div>
          <div v-else class="suggestion-empty">暂无推荐问题</div>
          <div style="display: flex; gap: 8px;">
            <n-input
              v-model:value="newSuggestionEdit"
              style="flex: 1"
              placeholder="输入推荐问题，按 Enter 添加"
              @keydown.enter.prevent="addSuggestion(suggestionItemsEdit, newSuggestionEdit, v => newSuggestionEdit = v)"
            />
            <n-button type="primary" @click="addSuggestion(suggestionItemsEdit, newSuggestionEdit, v => newSuggestionEdit = v)">添加</n-button>
          </div>
        </div>
      </n-form-item>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="editId = null">取消</n-button>
        <n-button type="primary" @click="submitEdit">保存</n-button>
      </n-space>
    </template>
  </n-modal>

  <!-- ============ 嵌入网站弹窗 ============ -->
  <n-modal
    v-model:show="showEmbed"
    preset="card"
    :title="`嵌入网站 — ${embedRow?.name ?? ''}`"
    style="width: min(680px, 96vw)"
    :mask-closable="true"
  >
    <n-tabs v-model:value="embedActiveTab" type="line" animated>
      <n-tab-pane name="pc" tab="嵌入 PC 端">
        <div class="embed-code-block">
          <pre class="embed-code">{{ embedCodePc }}</pre>
          <n-button size="small" type="primary" @click="copyCode(embedCodePc)">复制代码</n-button>
        </div>
        <p class="embed-hint">将以上代码粘贴到您网站的 HTML 中，即可在 PC 端展示聊天窗口。</p>
      </n-tab-pane>
      <n-tab-pane name="mobile" tab="嵌入移动端">
        <div class="embed-code-block">
          <pre class="embed-code">{{ embedCodeMobile }}</pre>
          <n-button size="small" type="primary" @click="copyCode(embedCodeMobile)">复制代码</n-button>
        </div>
        <p class="embed-hint">将以上代码粘贴到您移动端页面中，聊天窗口将自适应全屏展示。</p>
      </n-tab-pane>
    </n-tabs>

    <template #footer>
      <n-space justify="end">
        <n-button @click="showEmbed = false">关闭</n-button>
      </n-space>
    </template>
  </n-modal>

  <!-- ============ 用量抽屉 ============ -->
  <n-drawer v-model:show="usageOpen" :width="760" placement="right">
    <n-drawer-content :title="`用量 · ${usageAppName}`" closable>
      <n-spin :show="usageLoading">
        <template v-if="usageData">
          <n-space vertical :size="16">
            <n-text>
              次数 {{ usageData.turnCount }}；输入 {{ usageData.totalPromptTokens }}；输出
              {{ usageData.totalCompletionTokens }}；合计 {{ usageData.totalTokens }}
            </n-text>
            <div>
              <n-text strong style="display: block; margin-bottom: 8px">模型统计</n-text>
              <n-data-table
                :columns="[
                  { title: '模型', key: 'model' },
                  { title: '次数', key: 'turnCount', width: 88 },
                  { title: 'Token', key: 'totalTokens', width: 100 },
                ]"
                :data="usageData.byModel"
                :max-height="200"
                size="small"
              />
            </div>
            <div v-if="usageAppId != null">
              <n-text strong style="display: block; margin-bottom: 8px">调用记录</n-text>
              <n-data-table
                :columns="sessionColumns(usageAppId)"
                :data="groupBySession(usageData.recentTurns)"
                :max-height="360"
                :scroll-x="620"
                size="small"
                :single-line="false"
                style="white-space: nowrap"
              />
            </div>
          </n-space>
        </template>
      </n-spin>
    </n-drawer-content>
  </n-drawer>

  <!-- ============ 会话消息弹窗 ============ -->
  <n-modal v-model:show="msgOpen" preset="card" style="width: min(680px, 98vw)">
    <template #header>
      <div class="msg-modal-header">
        <span class="msg-modal-title">会话消息</span>
        <span class="msg-modal-session">{{ msgSessionId }}</span>
        <n-button size="tiny" @click="copySessionId">复制ID</n-button>
      </div>
    </template>
    <n-spin :show="msgLoading">
      <div class="msg-preview">
        <div
          v-for="(m, i) in msgList"
          :key="i"
          :class="['msg-row', m.role === 'user' ? 'msg-row--right' : 'msg-row--left']"
        >
          <div :class="['msg-bubble', m.role === 'user' ? 'msg-bubble--user' : 'msg-bubble--ai']">
            <div class="msg-role-label">{{ m.role === 'user' ? '提问' : 'AI回复' }}</div>
            <details v-if="m.role === 'assistant' && m.reasoningContent" class="msg-thinking">
              <summary class="msg-thinking-summary">思考过程</summary>
              <pre class="msg-body msg-thinking-body">{{ m.reasoningContent }}</pre>
            </details>
            <div v-if="m.role === 'assistant'" class="msg-md" v-html="renderChatMarkdown(m.content || '')" />
            <pre v-else class="msg-body">{{ m.content || '' }}</pre>
            <div v-if="m.createdAt" class="msg-time">{{ fmtTime(m.createdAt) }}</div>
          </div>
        </div>
      </div>
    </n-spin>
  </n-modal>

  <!-- ============ Prompt 配置弹窗 ============ -->
  <n-modal
    v-model:show="showPrompt"
    preset="card"
    :title="`Prompt 配置 — ${promptRow?.name ?? ''}`"
    style="width: min(640px, 96vw)"
    :mask-closable="false"
  >
    <n-form label-placement="top">
      <n-form-item label="角色设定">
        <n-input
          v-model:value="promptForm.systemRole"
          type="textarea"
          placeholder="例如：你是一名专业的客服助手，熟悉公司产品与服务。"
          :autosize="{ minRows: 3, maxRows: 6 }"
        />
      </n-form-item>

      <n-form-item label="任务指令">
        <n-input
          v-model:value="promptForm.systemTask"
          type="textarea"
          placeholder="例如：帮助用户解决售前、售后问题，引导用户下单。"
          :autosize="{ minRows: 3, maxRows: 6 }"
        />
      </n-form-item>

      <n-form-item label="限制条件">
        <n-input
          v-model:value="promptForm.systemConstraints"
          type="textarea"
          placeholder="例如：不得讨论竞争对手；回答简洁，不超过 200 字。"
          :autosize="{ minRows: 3, maxRows: 6 }"
        />
        <template #feedback>三个提示词字段均为空时不下发 system prompt</template>
      </n-form-item>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="showPrompt = false">取消</n-button>
        <n-button type="primary" @click="submitPrompt">保存</n-button>
      </n-space>
    </template>
  </n-modal>

  <!-- ============ 用户数据管理 Drawer ============ -->
  <n-drawer v-model:show="showContextDrawer" placement="right" :width="900">
    <n-drawer-content title="用户数据" :native-scrollbar="false" closable>
      <!-- 操作栏 -->
      <div style="display:flex; justify-content:flex-end; margin-bottom:12px">
        <n-button type="primary" @click="openContextCreate">+ 新建用户数据字段</n-button>
      </div>

      <!-- 列表 -->
      <n-data-table :columns="contextColumns" :data="contextFields" :bordered="false" size="small" />

      <!-- 新建/编辑表单 Modal -->
      <n-modal v-model:show="showContextForm" preset="card" :title="contextEditId ? '编辑用户数据字段' : '新建用户数据字段'" style="width:680px" :mask-closable="false">
        <n-form :model="contextForm" label-placement="left" label-width="110px">
          <n-form-item label="字段 Key" required>
            <n-input v-model:value="contextForm.fieldKey" placeholder="如：esusMobile" />
          </n-form-item>
          <n-form-item label="显示名" required>
            <n-input v-model:value="contextForm.label" placeholder="如：用户手机号" />
          </n-form-item>
          <n-form-item label="字段类型" required>
            <n-select v-model:value="contextForm.fieldType" :options="fieldTypeOptions" style="width:200px" />
          </n-form-item>
          <n-form-item label="解析逻辑">
            <div style="display:flex; flex-wrap:wrap; gap:6px; align-items:center">
              <template v-for="(_seg, index) in parseExpressionSegments" :key="index">
                <n-input v-model:value="parseExpressionSegments[index]" placeholder="路径段" style="width:140px" @blur="updateParseExpression" @keydown.enter.prevent="updateParseExpression" />
                <span v-if="index < parseExpressionSegments.length - 1" style="color:#999">.</span>
              </template>
              <n-button size="small" type="primary" @click="addParseExpressionSegment">+</n-button>
              <n-button v-if="parseExpressionSegments.length > 0" size="small" type="error" @click="removeParseExpressionSegment">-</n-button>
            </div>
          </n-form-item>
          <n-form-item label="说明">
            <n-input v-model:value="contextForm.description" type="textarea" :rows="3" placeholder="如：用户手机号，系统唯一" />
          </n-form-item>
          <n-form-item label="状态">
            <n-switch v-model:value="contextForm.enabled" />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="showContextForm = false">取消</n-button>
            <n-button type="primary" @click="handleContextSave">保存</n-button>
          </n-space>
        </template>
      </n-modal>

      <!-- 用户数据字段测试 Modal -->
      <n-modal v-model:show="showCtxTestModal" preset="card" :title="`测试运行 · ${ctxTestField?.label || ctxTestField?.fieldKey || ''}`" style="width:520px" :mask-closable="false">
        <template v-if="ctxTestField">
          <div style="font-size:12px; color:#888; margin-bottom:12px; line-height:1.8">
            <div>字段 Key：<span style="font-family:monospace; color:#333">{{ ctxTestField.fieldKey }}</span></div>
            <div style="display:flex; align-items:center; gap:6px; flex-wrap:wrap; margin-top:4px">
              <span>解析路径：</span>
              <template v-if="ctxTestField.parseExpression">
                <template v-for="(seg, i) in ctxTestField.parseExpression.split('.')" :key="i">
                  <span v-if="i > 0" style="color:#aaa">→</span>
                  <n-tag size="tiny" style="font-family:monospace">{{ seg }}</n-tag>
                </template>
              </template>
              <span v-else style="color:#bbb">未配置路径</span>
            </div>
          </div>
          <n-button type="primary" :loading="ctxTestRunning" style="width:100%; margin-bottom:14px" @click="runContextTest">
            {{ ctxTestRunning ? '解析中...' : '从当前登录数据中提取' }}
          </n-button>
          <template v-if="ctxTestFound !== null || ctxTestError">
            <n-divider title-placement="left" style="margin:0 0 10px">解析结果</n-divider>
            <div v-if="ctxTestError" style="background:#fff2f0; border:1px solid #ffccc7; border-radius:6px; padding:10px 14px; font-size:13px; color:#cf1322">
              {{ ctxTestError }}
            </div>
            <template v-else-if="ctxTestFound">
              <div style="background:#f6ffed; border:1px solid #b7eb8f; border-radius:6px; padding:10px 14px">
                <div style="font-size:12px; color:#52c41a; margin-bottom:6px; font-weight:600">✓ 解析成功</div>
                <div style="font-size:12px; color:#555; margin-bottom:4px">提取到的值：</div>
                <n-input :value="ctxTestValue ?? ''" type="textarea" :rows="4" readonly style="font-family:monospace; font-size:12px" />
              </div>
            </template>
            <div v-else style="background:#fffbe6; border:1px solid #ffe58f; border-radius:6px; padding:10px 14px; font-size:13px; color:#d46b08">
              ⚠ 路径 <span style="font-family:monospace">{{ ctxTestExpression }}</span> 在登录数据中未找到对应值，请检查路径是否正确
            </div>
          </template>
        </template>
        <template #footer>
          <n-space justify="end">
            <n-button @click="showCtxTestModal = false">关闭</n-button>
          </n-space>
        </template>
      </n-modal>
    </n-drawer-content>
  </n-drawer>

  <!-- ============ 工具配置弹窗 ============ -->
  <n-modal
    v-model:show="showToolConfigModal"
    preset="card"
    :title="`工具配置 — ${toolConfigAppname}`"
    style="width: min(800px, 96vw)"
    :mask-closable="false"
  >
    <n-spin :show="toolConfigLoading">
      <!-- 添加工具 -->
      <div style="display: flex; gap: 8px; margin-bottom: 16px">
        <n-select
          v-model:value="toolConfigSelectedToolId"
          :options="toolConfigToolOptions"
          placeholder="选择工具"
          style="flex: 1"
          filterable
        />
        <n-button type="primary" @click="addToolConfig">新增</n-button>
      </div>
      
      <!-- 已选工具列表 -->
      <div v-if="toolConfigList.length === 0" style="text-align:center; padding:40px 0; color:#999">
        暂无已配置的工具
      </div>
      
      <div v-else style="max-height:400px; overflow-y:auto; border:1px solid #e5e7eb; border-radius:8px; padding:8px; background:#fafafa">
        <div
          v-for="(item, idx) in toolConfigList"
          :key="item.toolId"
          style="display:flex; align-items:flex-start; gap:12px; padding:10px; border-bottom:1px solid #e5e7eb"
          :style="{ borderBottom: idx < toolConfigList.length - 1 ? '1px solid #e5e7eb' : 'none' }"
        >
          <div style="flex:1; min-width:0">
            <div style="display:flex; align-items:flex-start; gap:8px; margin-bottom:8px">
              <span style="font-size:13px; color:#666; white-space:nowrap; padding-top:2px">接口名称：</span>
              <div style="font-size:14px; font-weight:600; color:#333">
                {{ item.toolLabel }}
                <span style="font-size:11px; color:#999; margin-left:6px; font-weight:normal">({{ item.toolName }})</span>
              </div>
            </div>
            <div style="display:flex; align-items:flex-start; gap:8px">
              <span style="font-size:13px; color:#666; white-space:nowrap; padding-top:8px">调用策略：</span>
              <n-input
                v-model:value="item.callStrategy"
                type="textarea"
                placeholder="例如：当用户询问{{相关场景}}时调用此工具"
                :autosize="{ minRows: 2, maxRows: 3 }"
                style="font-size:13px; flex:1"
              />
            </div>
          </div>
          <n-button size="small" type="error" ghost @click="removeToolConfig(idx)">删除</n-button>
        </div>
      </div>
    </n-spin>
    
    <template #footer>
      <n-space justify="end">
        <n-button @click="showToolConfigModal = false">取消</n-button>
        <n-button type="primary" @click="saveToolConfig">保存</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<style scoped>
.inner {
  max-width: 100%;
  margin: 0 auto;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.page-title { font-size: 18px; }
.card {
  background: #ffffff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 12px;
}
.suggestion-list {
  max-height: 240px;
  overflow-y: auto;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px;
  background: #f9fafb;
}
.suggestion-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  margin-bottom: 4px;
  background: #fff;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
}
.suggestion-item:last-child { margin-bottom: 0; }
.suggestion-text {
  flex: 1;
  font-size: 13px;
  color: #1f2937;
  margin-right: 8px;
  word-break: break-word;
}
.suggestion-empty {
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
  padding: 20px 0;
}
/* 会话消息样式 */
.msg-preview {
  max-height: 500px;
  overflow-y: auto;
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
}
.msg-row {
  display: flex;
  margin-bottom: 16px;
}
.msg-row--right {
  justify-content: flex-end;
}
.msg-row--left {
  justify-content: flex-start;
}
.msg-bubble {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: 12px;
}
.msg-bubble--user {
  background: #e8f4fd;
  border-radius: 12px 12px 4px 12px;
}
.msg-bubble--ai {
  background: #f0f0f0;
  border-radius: 12px 12px 12px 4px;
}
.msg-role-label {
  font-weight: 500;
  color: #666;
  margin-bottom: 4px;
  font-size: 12px;
}
.msg-body {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.6;
  color: #111827;
}
.msg-thinking {
  margin-bottom: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f9fafb;
}
.msg-thinking-summary {
  padding: 6px 10px;
  cursor: pointer;
  font-size: 12px;
  color: #6b7280;
  font-weight: 500;
}
.msg-thinking-body {
  padding: 8px 12px 12px;
  font-size: 13px;
  color: #4b5563;
  white-space: pre-wrap;
  line-height: 1.6;
  border-top: 1px solid #e5e7eb;
}
.msg-time {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
  text-align: right;
}
.msg-md {
  font-size: 14px;
  line-height: 1.6;
  color: #111827;
  word-break: break-word;
}
.msg-md :deep(p) { margin: 0.35em 0; }
.msg-md :deep(p:first-child) { margin-top: 0; }
.msg-md :deep(p:last-child) { margin-bottom: 0; }
.msg-md :deep(ul), .msg-md :deep(ol) { margin: 0.4em 0; padding-left: 1.3em; }
.msg-md :deep(h1), .msg-md :deep(h2), .msg-md :deep(h3), .msg-md :deep(h4) {
  margin: 0.55em 0 0.3em;
  font-weight: 600;
  line-height: 1.3;
}
.msg-md :deep(blockquote) {
  margin: 0.4em 0;
  padding: 0.3em 0 0.3em 0.8em;
  border-left: 3px solid #d1d5db;
  color: #4b5563;
}
.msg-md :deep(a) { color: #2563eb; text-decoration: underline; }
.msg-md :deep(p > code), .msg-md :deep(li > code), .msg-md :deep(td > code) {
  background: #f3f4f6;
  padding: 0.1em 0.35em;
  border-radius: 4px;
  font-family: ui-monospace, monospace;
  font-size: 0.88em;
}
.msg-md :deep(.md-code-block) {
  margin: 0.6em 0;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}
.msg-md :deep(.md-code-head) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 10px;
  background: #f3f4f6;
  border-bottom: 1px solid #e5e7eb;
  font-size: 11px;
}
.msg-md :deep(.md-code-lang) { color: #6b7280; font-family: ui-monospace, monospace; }
.msg-md :deep(.md-copy-btn) {
  padding: 2px 7px;
  font-size: 11px;
  color: #374151;
  background: #fff;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  cursor: pointer;
}
.msg-md :deep(.md-copy-btn:hover) { background: #f9fafb; }
.msg-md :deep(.md-code-pre) {
  margin: 0;
  padding: 8px 12px;
  overflow-x: auto;
  font-size: 12px;
  line-height: 1.5;
  background: #fafafa;
}
.msg-md :deep(.md-code-pre code) { font-family: ui-monospace, monospace; }
.msg-md :deep(table) { border-collapse: collapse; margin: 0.5em 0; font-size: 12px; width: 100%; }
.msg-md :deep(th), .msg-md :deep(td) { border: 1px solid #e5e7eb; padding: 0.3em 0.5em; }
.msg-md :deep(th) { background: #f9fafb; }
/* 消息中的图片 */
.msg-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.msg-image {
  max-width: 200px;
  max-height: 200px;
  border-radius: 8px;
  cursor: pointer;
  object-fit: contain;
}
</style>
