<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  NAvatar,
  NButton,
  NCard,
  NEmpty,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NList,
  NListItem,
  NModal,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSlider,
  NSpace,
  NSpin,
  NSwitch,
  NTag,
  NText,
  NTooltip,
  useMessage,
} from 'naive-ui'
import { ChevronDownOutline, ChevronUpOutline, HelpCircleOutline } from '@vicons/ionicons5'
import {
  adminCreateLlm,
  adminGetLlm,
  adminListLlm,
  adminUpdateLlm,
  type LlmAdminRow,
  type LlmCreatePayload,
  type LlmUpdatePayload,
} from '@/api/adminLlm'
import { useAuthStore } from '@/stores/auth'
import ConversationsView from './ConversationsView.vue'
import ApiKeysView from './ApiKeysView.vue'
import AiAppsView from './AiAppsView.vue'
import ToolsView from './ToolsView.vue'
import ContextFieldsView from './ContextFieldsView.vue'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const auth = useAuthStore()

// 根据路由路径确定当前页面
const currentPage = computed<'llm' | 'conversations' | 'api-keys' | 'ai-apps' | 'tools' | 'context-fields'>(() => {
  const path = route.path
  if (path === '/settings/conversations') {
    return 'conversations'
  } else if (path === '/settings/api-keys') {
    return 'api-keys'
  } else if (path === '/settings/ai-apps') {
    return 'ai-apps'
  } else if (path === '/settings/tools') {
    return 'tools'
  } else if (path === '/settings/context-fields') {
    return 'context-fields'
  } else {
    return 'llm'
  }
})

const rows = ref<LlmAdminRow[]>([])
const loading = ref(false)

const showMainModal = ref(false)
const editingId = ref<number | null>(null)
const form = ref({
  displayName: '',
  apiKey: '',
  baseUrl: '',
  enabled: true,
  sortOrder: null as number | null,
})

/** 模型配置：逻辑名即上游 model id + 三种能力（保存时 upstreamModel 与键一致） */
interface ModelRow {
  logicalName: string
  textGeneration: boolean
  deepThinking: boolean
  vision: boolean
  contextWindowK: number | null
}

const showModelsModal = ref(false)
const modelsProviderId = ref<number | null>(null)
const modelRows = ref<ModelRow[]>([
  {
    logicalName: '',
    textGeneration: true,
    deepThinking: false,
    vision: false,
    contextWindowK: null,
  },
])
const modelDefaultMode = ref<string>('')

interface InferenceUiState {
  useTemperature: boolean
  temperature: number
  useTopP: boolean
  topP: number
  useMaxTokens: boolean
  maxTokens: number
  /** 是否写入 supportsThinkingApi，覆盖网关自动识别（百炼等自定义域名时用） */
  useSupportsThinkingApi: boolean
  supportsThinkingApi: boolean
  useThinking: boolean
  thinkingMode: boolean
  useResponseFormat: boolean
  responseFormat: 'TEXT' | 'JSON_OBJECT'
}

function defaultInferenceUi(): InferenceUiState {
  return {
    useTemperature: true,
    temperature: 0.7,
    useTopP: false,
    topP: 0.95,
    useMaxTokens: false,
    maxTokens: 4096,
    useSupportsThinkingApi: false,
    supportsThinkingApi: true,
    useThinking: false,
    thinkingMode: false,
    useResponseFormat: false,
    responseFormat: 'TEXT',
  }
}

const showParamsModal = ref(false)
const paramsProviderId = ref<number | null>(null)
const inf = ref<InferenceUiState>(defaultInferenceUi())

const responseFormatOptions = [
  { label: '普通文本', value: 'TEXT' as const },
  { label: 'JSON 模式', value: 'JSON_OBJECT' as const },
]

function modeCount(raw: string): number {
  try {
    const o = JSON.parse(raw || '{}') as Record<string, unknown>
    return Object.keys(o).length
  } catch {
    return 0
  }
}

function inferLegacyDeepThinking(provCode: string, baseUrl: string): boolean {
  const c = (provCode || '').toUpperCase()
  if (c === 'DEEPSEEK' || c.includes('DEEPSEEK')) {
    return true
  }
  if (c.includes('BAILIAN') || c.includes('DASHSCOPE')) {
    return true
  }
  if (c.includes('CODINGPLAN') || c.includes('CODING_PLAN')) {
    return true
  }
  const u = (baseUrl || '').toLowerCase()
  if (u.includes('deepseek') || u.includes('dashscope') || u.includes('bailian')) {
    return true
  }
  return u.includes('aliyuncs.com') && u.includes('compatible-mode')
}

function tokensToContextWindowK(tokens: unknown): number | null {
  if (typeof tokens !== 'number' || !Number.isFinite(tokens) || tokens <= 0) {
    return null
  }
  return Math.round(tokens / 1024)
}

function contextWindowKToTokens(contextWindowK: number | null | undefined): number | null {
  if (typeof contextWindowK !== 'number' || !Number.isFinite(contextWindowK) || contextWindowK <= 0) {
    return null
  }
  return Math.round(contextWindowK * 1024)
}

function updateContextWindowK(row: ModelRow, value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    row.contextWindowK = null
    return
  }
  const digitsOnly = trimmed.replace(/[^\d]/g, '')
  if (!digitsOnly) {
    row.contextWindowK = null
    return
  }
  row.contextWindowK = Number.parseInt(digitsOnly, 10)
}

function parseModesToRows(raw: string, provCode: string, baseUrl: string): ModelRow[] {
  const legacyDeep = inferLegacyDeepThinking(provCode, baseUrl)
  try {
    const o = JSON.parse(raw || '{}') as Record<string, unknown>
    const keys = Object.keys(o)
    if (!keys.length) {
      return [emptyModelRow()]
    }
    const rows: ModelRow[] = []
    for (const k of keys) {
      const key = k.trim()
      if (!key) {
        continue
      }
      const v = o[k]
      if (typeof v === 'string') {
        rows.push({
          logicalName: key,
          textGeneration: true,
          deepThinking: legacyDeep,
          vision: false,
          contextWindowK: null,
        })
      } else if (v && typeof v === 'object') {
        const obj = v as Record<string, unknown>
        rows.push({
          logicalName: key,
          textGeneration: obj.textGeneration !== false,
          deepThinking: obj.deepThinking === true,
          vision: obj.vision === true,
          contextWindowK: tokensToContextWindowK(obj.contextWindow),
        })
      }
    }
    return rows.length ? rows : [emptyModelRow()]
  } catch {
    return [emptyModelRow()]
  }
}

function emptyModelRow(): ModelRow {
  return {
    logicalName: '',
    textGeneration: true,
    deepThinking: false,
    vision: false,
    contextWindowK: null,
  }
}

/** 保存为 modes_json 对象值结构（含能力字段） */
function buildModesJsonFromRows(rows: ModelRow[]): string {
  const o: Record<string, Record<string, unknown>> = {}
  for (const r of rows) {
    const name = r.logicalName.trim()
    if (!name) {
      continue
    }
    o[name] = {
      upstreamModel: name,
      textGeneration: r.textGeneration,
      deepThinking: r.deepThinking,
      vision: r.vision,
    }
    const contextWindow = contextWindowKToTokens(r.contextWindowK)
    if (contextWindow != null) {
      o[name].contextWindow = contextWindow
    }
  }
  return JSON.stringify(o)
}

function parseInferenceUi(raw: string | null | undefined): InferenceUiState {
  const d = defaultInferenceUi()
  if (!raw?.trim()) {
    return d
  }
  try {
    const j = JSON.parse(raw) as Record<string, unknown>
    if (j.temperature != null && typeof j.temperature === 'number') {
      d.useTemperature = true
      d.temperature = j.temperature
    } else {
      d.useTemperature = false
    }
    if (j.topP != null && typeof j.topP === 'number') {
      d.useTopP = true
      d.topP = j.topP
    } else {
      d.useTopP = false
    }
    if (j.maxTokens != null && typeof j.maxTokens === 'number') {
      d.useMaxTokens = true
      d.maxTokens = j.maxTokens
    } else {
      d.useMaxTokens = false
    }
    if (j.supportsThinkingApi != null && typeof j.supportsThinkingApi === 'boolean') {
      d.useSupportsThinkingApi = true
      d.supportsThinkingApi = j.supportsThinkingApi
    } else {
      d.useSupportsThinkingApi = false
    }
    if (j.thinkingMode != null && typeof j.thinkingMode === 'boolean') {
      d.useThinking = true
      d.thinkingMode = j.thinkingMode
    } else {
      d.useThinking = false
    }
    if (j.responseFormat != null) {
      const rf = String(j.responseFormat).toUpperCase()
      d.useResponseFormat = true
      d.responseFormat = rf.includes('JSON') ? 'JSON_OBJECT' : 'TEXT'
    } else {
      d.useResponseFormat = false
    }
    return d
  } catch {
    return defaultInferenceUi()
  }
}

function buildInferenceJson(p: InferenceUiState): string | null {
  const o: Record<string, unknown> = {}
  if (p.useTemperature) {
    o.temperature = p.temperature
  }
  if (p.useTopP) {
    o.topP = p.topP
  }
  if (p.useMaxTokens) {
    o.maxTokens = Math.round(p.maxTokens)
  }
  if (p.useSupportsThinkingApi) {
    o.supportsThinkingApi = p.supportsThinkingApi
  }
  if (p.useThinking) {
    o.thinkingMode = p.thinkingMode
  }
  if (p.useResponseFormat && p.responseFormat === 'JSON_OBJECT') {
    o.responseFormat = 'JSON_OBJECT'
  }
  if (Object.keys(o).length === 0) {
    return null
  }
  return JSON.stringify(o)
}

const defaultModeSelectOptions = computed(() =>
  modelRows.value
    .map((r) => r.logicalName.trim())
    .filter(Boolean)
    .map((k) => ({ label: k, value: k })),
)

async function load() {
  loading.value = true
  try {
    rows.value = await adminListLlm()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } }; message?: string }
    message.error(err.response?.data?.error || err.message || '加载大模型列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function resetMainForm() {
  editingId.value = null
  form.value = {
    displayName: '',
    apiKey: '',
    baseUrl: '',
    enabled: true,
    sortOrder: null,
  }
}

function openCreate() {
  resetMainForm()
  showMainModal.value = true
}

async function openEditMain(row: LlmAdminRow) {
  editingId.value = row.id
  try {
    const detail = await adminGetLlm(row.id)
    form.value = {
      displayName: detail.displayName,
      apiKey: detail.apiKey || '',
      baseUrl: detail.baseUrl,
      enabled: detail.enabled,
      sortOrder: detail.sortOrder,
    }
    showMainModal.value = true
  } catch {
    message.error('加载详情失败')
  }
}

async function saveMain() {
  const name = form.value.displayName.trim()
  const baseUrl = form.value.baseUrl.trim()
  if (!name) {
    message.warning('请填写大模型名称')
    return
  }
  if (!baseUrl) {
    message.warning('请填写 Base URL')
    return
  }
  try {
    if (editingId.value == null) {
      const payload: LlmCreatePayload = {
        displayName: name,
        baseUrl,
        apiKey: form.value.apiKey,
        enabled: form.value.enabled,
      }
      if (form.value.sortOrder != null && !Number.isNaN(Number(form.value.sortOrder))) {
        payload.sortOrder = Number(form.value.sortOrder)
      }
      await adminCreateLlm(payload)
      message.success('已创建，请通过「模型配置」「参数配置」完善模型列表与推理默认值')
    } else {
      const payload: LlmUpdatePayload = {
        displayName: name,
        baseUrl,
        enabled: form.value.enabled,
        sortOrder:
          form.value.sortOrder != null && !Number.isNaN(Number(form.value.sortOrder))
            ? Number(form.value.sortOrder)
            : 0,
      }
      if (form.value.apiKey.trim()) {
        payload.apiKey = form.value.apiKey
      }
      await adminUpdateLlm(editingId.value, payload)
      message.success('已保存')
    }
    showMainModal.value = false
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } } }
    message.error(err.response?.data?.error || '保存失败')
  }
}

function openModels(row: LlmAdminRow) {
  modelsProviderId.value = row.id
  const parsed = parseModesToRows(row.modesJson, row.code, row.baseUrl)
  modelRows.value = parsed.length ? parsed : [emptyModelRow()]
  modelDefaultMode.value = row.defaultMode || ''
  showModelsModal.value = true
}

function addModeRow() {
  modelRows.value = [...modelRows.value, emptyModelRow()]
}

function removeModeRow(index: number) {
  modelRows.value = modelRows.value.filter((_, i) => i !== index)
  if (!modelRows.value.length) {
    modelRows.value = [emptyModelRow()]
  }
}

function moveModeRowUp(index: number) {
  if (index <= 0) {
    return
  }
  const arr = [...modelRows.value]
  const prev = arr[index - 1]
  const cur = arr[index]
  if (prev == null || cur == null) {
    return
  }
  arr[index - 1] = cur
  arr[index] = prev
  modelRows.value = arr
}

function moveModeRowDown(index: number) {
  if (index >= modelRows.value.length - 1) {
    return
  }
  const arr = [...modelRows.value]
  const cur = arr[index]
  const next = arr[index + 1]
  if (cur == null || next == null) {
    return
  }
  arr[index] = next
  arr[index + 1] = cur
  modelRows.value = arr
}

async function saveModels() {
  if (modelsProviderId.value == null) {
    return
  }
  const cleaned = modelRows.value.map((r) => r.logicalName.trim()).filter(Boolean)
  if (!cleaned.length) {
    message.warning('请至少添加一个模型')
    return
  }
  const unique = [...new Set(cleaned)]
  if (unique.length !== cleaned.length) {
    message.warning('逻辑模型名不能重复')
    return
  }
  const mj = buildModesJsonFromRows(modelRows.value)
  const dm = modelDefaultMode.value.trim()
  if (!dm) {
    message.warning('请选择或填写默认模型（须为已添加的模型之一）')
    return
  }
  if (!unique.includes(dm)) {
    message.warning('默认模型必须是列表中的某一个')
    return
  }
  try {
    await adminUpdateLlm(modelsProviderId.value, {
      defaultMode: dm,
      modesJson: mj,
    })
    message.success('模型配置已保存')
    showModelsModal.value = false
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } } }
    message.error(err.response?.data?.error || '保存失败')
  }
}

function openParams(row: LlmAdminRow) {
  paramsProviderId.value = row.id
  inf.value = parseInferenceUi(row.inferenceDefaultsJson)
  showParamsModal.value = true
}

async function saveParams() {
  if (paramsProviderId.value == null) {
    return
  }
  try {
    const json = buildInferenceJson(inf.value)
    await adminUpdateLlm(paramsProviderId.value, {
      inferenceDefaultsJson: json,
    })
    message.success('参数配置已保存')
    showParamsModal.value = false
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } } }
    message.error(err.response?.data?.error || '保存失败')
  }
}

function back() {
  router.push('/chat')
}

function goTo(page: 'llm' | 'conversations' | 'api-keys' | 'ai-apps' | 'tools' | 'context-fields') {
  router.push(`/settings/${page}`)
}

function avatarLetter(row: LlmAdminRow): string {
  const s = (row.displayName || '?').trim()
  return s.slice(0, 1).toUpperCase()
}
</script>

<template>
  <div class="settings-page">
    <div class="settings-sidebar">
      <div class="sidebar-header">
        <n-button text @click="back" class="back-btn">← 返回对话</n-button>
      </div>
      <div class="sidebar-menu">
        <div v-if="auth.isAdmin" class="menu-item" :class="{ active: currentPage === 'llm' }" @click="goTo('llm')">
          <span class="menu-icon">⚙</span>
          <span class="menu-label">模型管理</span>
        </div>
        <div v-if="auth.isAdmin" class="menu-item" :class="{ active: currentPage === 'conversations' }" @click="goTo('conversations')">
          <span class="menu-icon">💬</span>
          <span class="menu-label">会话管理</span>
        </div>
        <div v-if="auth.isAdmin" class="menu-item" :class="{ active: currentPage === 'ai-apps' }" @click="goTo('ai-apps')">
          <span class="menu-icon">🤖</span>
          <span class="menu-label">应用管理</span>
        </div>
        <div v-if="auth.isAdmin" class="menu-item" :class="{ active: currentPage === 'api-keys' }" @click="goTo('api-keys')">
          <span class="menu-icon">🔑</span>
          <span class="menu-label">API Key 管理</span>
        </div>
        <div v-if="auth.isAdmin" class="menu-item" :class="{ active: currentPage === 'tools' }" @click="goTo('tools')">
          <span class="menu-icon">🔧</span>
          <span class="menu-label">接口管理</span>
        </div>
        <div v-if="auth.isAdmin" class="menu-item" :class="{ active: currentPage === 'context-fields' }" @click="goTo('context-fields')">
          <span class="menu-icon">📋</span>
          <span class="menu-label">用户数据管理</span>
        </div>
      </div>
    </div>
    <div class="settings-content">
      <div v-if="currentPage === 'llm'" class="settings-content-inner">
        <div class="page-inner">
          <n-card :bordered="false" class="card" title="模型管理">
            <template #header-extra>
              <n-button type="primary" @click="openCreate">+ 新增大模型</n-button>
            </template>
        <n-spin :show="loading" class="list-spin">
          <n-empty
            v-if="!loading && !rows.length"
            description="暂无记录。数据库中的提供方会显示在此处；也可点击「新增大模型」添加。"
          />
          <n-list v-else-if="rows.length" bordered class="llm-list">
            <n-list-item v-for="row in rows" :key="row.id" class="llm-item">
              <template #prefix>
                <n-avatar round size="medium" class="llm-avatar">{{ avatarLetter(row) }}</n-avatar>
              </template>
              <div class="llm-item-main">
                <div class="llm-item-title">{{ row.displayName }}</div>
                <div class="llm-item-line">
                  <span class="llm-item-k">Base URL</span>
                  <span class="llm-item-v url" :title="row.baseUrl">{{ row.baseUrl }}</span>
                </div>
                <div class="llm-item-line">
                  <span class="llm-item-k">模型</span>
                  <span class="llm-item-v">{{ modeCount(row.modesJson) }} 个</span>
                </div>
                <div class="llm-item-line">
                  <span class="llm-item-k">默认模型</span>
                  <span class="llm-item-v">{{ row.defaultMode || '—' }}</span>
                </div>
              </div>
              <template #suffix>
                <n-space :size="8" align="center" justify="end" wrap class="row-actions">
                  <n-tag :bordered="false" :type="row.enabled ? 'success' : 'default'" size="small">
                    {{ row.enabled ? '已启用' : '已停用' }}
                  </n-tag>
                  <n-button size="small" ghost class="btn-basic-config" @click="openEditMain(row)">
                    基础配置
                  </n-button>
                  <n-button size="small" type="info" ghost @click="openModels(row)">模型配置</n-button>
                  <n-button size="small" type="primary" ghost strong class="btn-params" @click="openParams(row)">
                    参数配置
                  </n-button>
                </n-space>
              </template>
            </n-list-item>
          </n-list>
          <div v-else class="list-loading-gap" />
        </n-spin>
      </n-card>
        </div>
      </div>

      <n-modal
      v-model:show="showMainModal"
      preset="card"
      :title="editingId == null ? '新增大模型' : '基础配置'"
      style="width: min(520px, 96vw)"
      :mask-closable="false"
    >
      <div class="sys-settings-main-panel">
        <n-text depth="3" class="main-config-hint">
          填写名称、连接信息、启用与排序。系统将按名称自动生成对话里的 provider 标识；模型列表与推理默认值请使用「模型配置」「参数配置」。
        </n-text>
        <n-form label-placement="top" label-width="auto">
          <n-form-item label="大模型名称（显示名称）">
            <n-input v-model:value="form.displayName" placeholder="如 月之暗面 Kimi" />
          </n-form-item>
          <n-form-item label="API Key" :description="editingId != null ? '留空表示不修改已保存的 Key' : undefined">
            <n-input v-model:value="form.apiKey" type="password" show-password-on="click" placeholder="sk-..." />
          </n-form-item>
          <n-form-item label="Base URL">
            <n-input v-model:value="form.baseUrl" placeholder="https://api.example.com/v1" />
          </n-form-item>
          <n-form-item label="启用该提供方">
            <n-switch v-model:value="form.enabled" />
          </n-form-item>
          <n-form-item
            label="排序（数字越小越靠前）"
            :description="editingId == null ? '留空则自动排在列表末尾' : undefined"
          >
            <n-input-number v-model:value="form.sortOrder" :min="0" clearable placeholder="自动" class="form-sort" />
          </n-form-item>
        </n-form>
      </div>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showMainModal = false">取消</n-button>
          <n-button type="primary" @click="saveMain">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showModelsModal"
      preset="card"
      title="模型配置"
      style="width: min(760px, 98vw)"
      :mask-closable="false"
    >
      <n-text depth="3" style="display: block; margin-bottom: 12px; font-size: 13px">
        每行填写一个模型标识（与上游 API 的 model id 相同，也作为对话里 mode 下拉的逻辑名），并配置三种能力；**由上至下的顺序**即对话中模型列表顺序。上下文按 K 回显与编辑，保存时自动换算为 token 数写入。保存后 JSON 中 upstreamModel 与该名称一致。
      </n-text>
      <n-form label-placement="top">
        <n-form-item label="默认模型（须为下方列表中的某一个）">
          <n-select
            v-model:value="modelDefaultMode"
            filterable
            tag
            placeholder="选择或输入逻辑名"
            :options="defaultModeSelectOptions"
          />
        </n-form-item>
        <div class="mode-table-head">
          <span class="mode-col-name">模型</span>
          <span class="mode-col-cap">文本生成</span>
          <span class="mode-col-cap">深度思考</span>
          <span class="mode-col-cap">视觉理解</span>
          <span class="mode-col-ctx">上下文（K）</span>
          <span class="mode-col-order">顺序</span>
          <span class="mode-col-act" />
        </div>
        <div v-for="(row, idx) in modelRows" :key="idx" class="mode-row">
          <n-input v-model:value="row.logicalName" placeholder="与上游 model id 一致，如 qwen-plus" class="mode-in-name" />
          <div class="mode-cap-cell">
            <n-switch v-model:value="row.textGeneration" size="small" />
          </div>
          <div class="mode-cap-cell">
            <n-switch v-model:value="row.deepThinking" size="small" />
          </div>
          <div class="mode-cap-cell">
            <n-switch v-model:value="row.vision" size="small" />
          </div>
          <div class="mode-ctx-cell">
            <n-input
              :value="row.contextWindowK == null ? '' : String(row.contextWindowK)"
              inputmode="numeric"
              placeholder="如 128"
              class="mode-in-ctx"
              @update:value="(value) => updateContextWindowK(row, value)"
            />
          </div>
          <div class="mode-order-cell">
            <n-button
              quaternary
              circle
              size="tiny"
              :disabled="idx === 0"
              title="上移"
              @click="moveModeRowUp(idx)"
            >
              <template #icon>
                <n-icon :component="ChevronUpOutline" />
              </template>
            </n-button>
            <n-button
              quaternary
              circle
              size="tiny"
              :disabled="idx === modelRows.length - 1"
              title="下移"
              @click="moveModeRowDown(idx)"
            >
              <template #icon>
                <n-icon :component="ChevronDownOutline" />
              </template>
            </n-button>
          </div>
          <n-button quaternary size="small" class="mode-del" @click="removeModeRow(idx)">删除</n-button>
        </div>
        <n-button dashed block style="margin-top: 10px" @click="addModeRow">+ 新增模型</n-button>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showModelsModal = false">取消</n-button>
          <n-button type="primary" @click="saveModels">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showParamsModal"
      preset="card"
      title="参数配置"
      style="width: min(560px, 96vw)"
      :mask-closable="false"
    >
      <n-text depth="3" style="display: block; margin-bottom: 16px; font-size: 13px">
        左侧开关表示是否将该参数写入默认推理配置；关闭时不会出现在 JSON 中（使用上游默认）。保存时自动生成 inference JSON。
      </n-text>

      <div class="param-section-title">参数</div>

      <div class="param-row">
        <n-switch v-model:value="inf.useTemperature" />
        <div class="param-label">
          温度
          <n-tooltip trigger="hover">
            <template #trigger>
              <n-icon :component="HelpCircleOutline" class="param-help" />
            </template>
            采样温度，越高越随机。部分厂商仅支持固定值。
          </n-tooltip>
        </div>
        <div class="param-ctl">
          <n-slider
            v-model:value="inf.temperature"
            :min="0"
            :max="2"
            :step="0.01"
            :disabled="!inf.useTemperature"
            class="param-slider"
          />
          <n-input-number
            v-model:value="inf.temperature"
            :min="0"
            :max="2"
            :step="0.01"
            size="small"
            :disabled="!inf.useTemperature"
            class="param-num"
          />
        </div>
      </div>

      <div class="param-row">
        <n-switch v-model:value="inf.useTopP" />
        <div class="param-label">
          Top P
          <n-tooltip trigger="hover">
            <template #trigger>
              <n-icon :component="HelpCircleOutline" class="param-help" />
            </template>
            核采样阈值。
          </n-tooltip>
        </div>
        <div class="param-ctl">
          <n-slider
            v-model:value="inf.topP"
            :min="0"
            :max="1"
            :step="0.01"
            :disabled="!inf.useTopP"
            class="param-slider"
          />
          <n-input-number
            v-model:value="inf.topP"
            :min="0"
            :max="1"
            :step="0.01"
            size="small"
            :disabled="!inf.useTopP"
            class="param-num"
          />
        </div>
      </div>

      <div class="param-row">
        <n-switch v-model:value="inf.useMaxTokens" />
        <div class="param-label">
          最大输出 token
          <n-tooltip trigger="hover">
            <template #trigger>
              <n-icon :component="HelpCircleOutline" class="param-help" />
            </template>
            对应 OpenAI 的 max_tokens。
          </n-tooltip>
        </div>
        <div class="param-ctl">
          <n-slider
            v-model:value="inf.maxTokens"
            :min="256"
            :max="32768"
            :step="64"
            :disabled="!inf.useMaxTokens"
            class="param-slider"
          />
          <n-input-number
            v-model:value="inf.maxTokens"
            :min="1"
            :max="200000"
            :step="1"
            size="small"
            :disabled="!inf.useMaxTokens"
            class="param-num"
          />
        </div>
      </div>

      <div class="param-row">
        <n-switch v-model:value="inf.useSupportsThinkingApi" />
        <div class="param-label">
          Thinking API 兼容
          <n-tooltip trigger="hover">
            <template #trigger>
              <n-icon :component="HelpCircleOutline" class="param-help" />
            </template>
            开启后写入 supportsThinkingApi，强制认为该提供方可下发 thinking 请求（自定义域名、非 DashScope 子域时与「深度思考」开关显示有关）。关闭则完全按系统自动识别。
          </n-tooltip>
        </div>
        <div class="param-ctl">
          <n-radio-group v-model:value="inf.supportsThinkingApi" size="small" :disabled="!inf.useSupportsThinkingApi">
            <n-radio-button :value="true">启用</n-radio-button>
            <n-radio-button :value="false">禁用</n-radio-button>
          </n-radio-group>
        </div>
      </div>

      <div class="param-row">
        <n-switch v-model:value="inf.useThinking" />
        <div class="param-label">
          深度思考
          <n-tooltip trigger="hover">
            <template #trigger>
              <n-icon :component="HelpCircleOutline" class="param-help" />
            </template>
            默认是否在对话中打开「深度思考」；仍受模型配置里「深度思考」能力约束。
          </n-tooltip>
        </div>
        <div class="param-ctl">
          <n-radio-group v-model:value="inf.thinkingMode" size="small" :disabled="!inf.useThinking">
            <n-radio-button :value="true">True</n-radio-button>
            <n-radio-button :value="false">False</n-radio-button>
          </n-radio-group>
        </div>
      </div>

      <div class="param-row">
        <n-switch v-model:value="inf.useResponseFormat" />
        <div class="param-label">
          回复格式
          <n-tooltip trigger="hover">
            <template #trigger>
              <n-icon :component="HelpCircleOutline" class="param-help" />
            </template>
            JSON 模式会要求模型输出合法 JSON（OpenAI response_format）。
          </n-tooltip>
        </div>
        <div class="param-ctl">
          <n-select
            v-model:value="inf.responseFormat"
            :options="responseFormatOptions"
            placeholder="请选择"
            :disabled="!inf.useResponseFormat"
            style="width: 200px"
          />
        </div>
      </div>

      <template #footer>
        <n-space justify="end">
          <n-button @click="showParamsModal = false">取消</n-button>
          <n-button type="primary" @click="saveParams">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 会话管理 -->
    <div v-if="currentPage === 'conversations'" class="settings-content-inner-full">
      <ConversationsView />
    </div>

    <!-- AI 应用管理 -->
    <div v-if="currentPage === 'ai-apps'" class="settings-content-inner-full">
      <AiAppsView />
    </div>

    <!-- 集成管理 -->
    <div v-if="currentPage === 'api-keys'" class="settings-content-inner-full">
      <ApiKeysView />
    </div>

    <!-- 接口管理 -->
    <div v-if="currentPage === 'tools'" class="settings-content-inner-full">
      <ToolsView />
    </div>

    <!-- 用户数据管理 -->
    <div v-if="currentPage === 'context-fields'" class="settings-content-inner-full">
      <ContextFieldsView />
    </div>
  </div>
</div>
</template>

<style scoped>
.settings-page {
  display: flex;
  min-height: 100vh;
  background: #f5f5f5;
}
.settings-sidebar {
  width: 180px;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e5e7eb;
}
.back-btn {
  font-size: 14px;
}
.sidebar-menu {
  padding: 8px;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  color: #4b5563;
  transition: all 0.2s;
}
.menu-item:hover {
  background: #f3f4f6;
}
.menu-item.active {
  background: #e0e7ff;
  color: #4f46e5;
  font-weight: 500;
}
.menu-icon {
  font-size: 16px;
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.menu-label {
  flex: 1;
}
.settings-content {
  flex: 1;
  overflow: auto;
}
.settings-content-inner {
  padding: 20px;
  min-height: 100%;
}
.settings-content-inner .page-inner {
  max-width: 100%;
  margin: 0 auto;
}
.settings-content-inner-full {
  padding: 20px;
  min-height: 100%;
}
.settings-content-inner-full > * {
  max-width: 100%;
  margin: 0 auto;
}
.page {
  flex: 1;
  min-height: 0;
  min-height: 100vh;
  min-height: 100dvh;
  overflow: auto;
  padding: 20px;
  padding-bottom: calc(24px + env(safe-area-inset-bottom, 0px));
  background: #f9fafb;
}
.page-inner {
  max-width: 760px;
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
.page-title {
  font-size: 18px;
}
.card {
  background: #ffffff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 12px;
}
.list-spin {
  min-height: 120px;
}
.list-loading-gap {
  min-height: 100px;
}
.llm-list {
  border-radius: 8px;
  margin-top: 4px;
}
.llm-item :deep(.n-list-item__main) {
  min-width: 0;
}
.llm-avatar {
  flex-shrink: 0;
}
.llm-item-main {
  min-width: 0;
  padding-right: 8px;
}
.llm-item-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 8px;
}
.llm-item-line {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 10px;
  font-size: 13px;
  line-height: 1.5;
  margin-top: 4px;
  word-break: break-all;
}
.llm-item-k {
  flex: 0 0 auto;
  min-width: 4.5em;
  font-size: 12px;
  font-weight: 500;
  color: #6b7280;
}
.llm-item-v {
  flex: 1 1 auto;
  min-width: 0;
  color: #374151;
}
.llm-item-v.url {
  color: #4f46e2;
}
.main-config-hint {
  display: block;
  margin-bottom: 14px;
  font-size: 13px;
  line-height: 1.55;
}
.row-actions {
  max-width: 420px;
}
.btn-basic-config {
  color: #dc2626 !important;
}
.btn-basic-config :deep(.n-button__border) {
  border: 1px solid #dc2626 !important;
}
.btn-basic-config:hover {
  color: #b91c1c !important;
  background: rgba(220, 38, 38, 0.06) !important;
}
.btn-basic-config:hover :deep(.n-button__border) {
  border-color: #b91c1c !important;
}
.btn-params {
  font-weight: 600;
}
.btn-params :deep(.n-button__border) {
  border-width: 1.5px;
}
.btn-basic-config :deep(.n-button__border) {
  border-width: 1.5px;
}
.mode-table-head {
  display: grid;
  grid-template-columns: minmax(140px, 1.4fr) repeat(3, 88px) 112px 76px 52px;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 6px;
  padding: 0 4px;
  align-items: center;
}
.mode-col-name {
  grid-column: span 1;
}
.mode-col-cap {
  text-align: center;
}
.mode-col-ctx {
  text-align: center;
}
.mode-col-order {
  text-align: center;
}
.mode-col-act {
  min-width: 52px;
}
.mode-row {
  display: grid;
  grid-template-columns: minmax(140px, 1.4fr) repeat(3, 88px) 112px 76px 52px;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.mode-in-name {
  min-width: 0;
}
.mode-cap-cell {
  display: flex;
  justify-content: center;
  align-items: center;
}
.mode-ctx-cell {
  display: flex;
  justify-content: center;
  align-items: center;
}
.mode-in-ctx {
  width: 68px;
}
.mode-in-ctx :deep(.n-input__input-el) {
  text-align: center;
}
.mode-order-cell {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 2px;
}
.mode-del {
  justify-self: end;
}
.param-section-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 12px;
}
.param-row {
  display: grid;
  grid-template-columns: auto minmax(100px, 140px) 1fr;
  gap: 10px 14px;
  align-items: center;
  margin-bottom: 18px;
}
.param-label {
  font-size: 14px;
  color: #374151;
  display: flex;
  align-items: center;
  gap: 4px;
}
.param-help {
  font-size: 16px;
  color: #9ca3af;
  cursor: help;
}
.param-ctl {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  min-width: 0;
}
.param-slider {
  flex: 1;
  min-width: 120px;
  max-width: 260px;
}
.param-num {
  width: 110px;
}
.form-sort {
  max-width: 220px;
}
</style>

<style>
/* 基础配置在 n-modal（Teleport）内，scoped 可能不命中弹层；单独全局类 */
.sys-settings-main-panel {
  box-sizing: border-box;
  border: 2px solid #374151 !important;
  border-radius: 10px;
  padding: 16px 18px;
  background: #f3f4f6 !important;
  box-shadow:
    0 0 0 1px rgba(17, 24, 39, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}
</style>
