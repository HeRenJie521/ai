<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  NButton,
  NCard,
  NDataTable,
  NEmpty,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  NSwitch,
  NTag,
  NTooltip,
  useDialog,
  useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { HelpCircleOutline } from '@vicons/ionicons5'
import {
  adminCreateLlm,
  adminDeleteLlm,
  adminGetLlm,
  adminListLlm,
  adminUpdateLlm,
  type LlmAdminRow,
  type LlmCreatePayload,
  type LlmUpdatePayload,
} from '@/api/adminLlm'
import {
  adminCreateLlmModel,
  adminDeleteLlmModel,
  adminListLlmModels,
  adminUpdateLlmModel,
  type LlmModelAdminRow,
  type LlmModelSavePayload,
} from '@/api/adminLlmModel'
import { useAuthStore } from '@/stores/auth'
import ConversationsView from './ConversationsView.vue'
import ApiKeysView from './ApiKeysView.vue'
import AiAppsView from './AiAppsView.vue'
import ToolsView from './ToolsView.vue'
import AdminAccountsView from './AdminAccountsView.vue'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()

const currentPage = computed<'llm' | 'conversations' | 'api-keys' | 'ai-apps' | 'tools' | 'admin-accounts'>(() => {
  const path = route.path
  if (path === '/settings/conversations') return 'conversations'
  if (path === '/settings/api-keys') return 'api-keys'
  if (path === '/settings/ai-apps') return 'ai-apps'
  if (path === '/settings/tools') return 'tools'
  if (path === '/settings/admin-accounts') return 'admin-accounts'
  return 'llm'
})

// ---- Provider list ----
const rows = ref<LlmAdminRow[]>([])
const loading = ref(false)
const modelCountMap = ref<Map<number, number>>(new Map())

// ---- Provider basic config modal ----
const showMainModal = ref(false)
const showProviderConfigModal = ref(false)
const editingId = ref<number | null>(null)
const form = ref({
  displayName: '',
  apiKey: '',
  baseUrl: '',
  enabled: true,
  sortOrder: null as number | null,
  forceTemperature: null as number | null,
  thinkingParamStyle: 'openai' as string,
  jsonModeSystemHint: false,
  stripToolCallIndex: false,
})

const thinkingParamStyleOptions = [
  { label: 'OpenAI（标准）', value: 'openai' },
  { label: 'DashScope（阿里云）', value: 'dashscope' },
]

// ---- Model management modal ----
const showModelsModal = ref(false)
const modelsProvider = ref<LlmAdminRow | null>(null)
const modelRows = ref<LlmModelAdminRow[]>([])
const modelsLoading = ref(false)

// ---- Model edit modal ----
const showModelEditModal = ref(false)
const showModelParamModal = ref(false)
const modelEditId = ref<number | null>(null)
const currentModelName = ref('')
const modelForm = ref(defaultModelForm())

interface ModelFormState {
  name: string
  textGeneration: boolean
  deepThinking: boolean
  vision: boolean
  streamOutput: boolean
  toolCall: boolean
  forceThinkingEnabled: boolean
  temperature: number | null
  maxTokens: number | null
  topP: number | null
  contextWindow: number | null
  responseFormat: string | null
  sortOrder: number | null
  enabled: boolean
}

function defaultModelForm(): ModelFormState {
  return {
    name: '',
    textGeneration: true,
    deepThinking: false,
    vision: false,
    streamOutput: true,
    toolCall: false,
    forceThinkingEnabled: false,
    temperature: null,
    maxTokens: null,
    topP: null,
    contextWindow: null,
    responseFormat: null,
    sortOrder: null,
    enabled: true,
  }
}

const modelColumns = computed<DataTableColumns<LlmModelAdminRow>>(() => [
  { title: '模型名称', key: 'name', minWidth: 160 },
  {
    title: '能力',
    key: 'caps',
    minWidth: 220,
    render: (row) => {
      const caps: string[] = []
      if (row.textGeneration) caps.push('文本')
      if (row.deepThinking) caps.push('思考')
      if (row.vision) caps.push('视觉')
      if (row.streamOutput) caps.push('流式')
      if (row.toolCall) caps.push('工具')
      return caps.join(' · ') || '—'
    },
  },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    fixed: 'right' as const,
    render: (row) =>
      h(NSpace, { size: 6, wrap: false }, {
        default: () => [
          h(NButton, { size: 'small', ghost: true, onClick: () => openEditModel(row) }, { default: () => '模型设置' }),
          h(NButton, { size: 'small', type: 'primary', ghost: true, onClick: () => openModelParams(row) }, { default: () => '参数设置' }),
          h(NButton, { size: 'small', type: 'error', ghost: true, onClick: () => confirmDeleteModel(row) }, { default: () => '删除' }),
        ],
      }),
  },
])

import { h } from 'vue'

const providerColumns = computed<DataTableColumns<LlmAdminRow>>(() => [
  {
    title: '模型提供商',
    key: 'displayName',
    minWidth: 100,
    render: (row) => h('div', { style: 'font-weight: 600' }, { default: () => row.displayName }),
  },
  {
    title: 'Base URL',
    key: 'baseUrl',
    minWidth: 200,
    ellipsis: { tooltip: true },
  },
  {
    title: '模型数量',
    key: 'modelCount',
    width: 80,
    align: 'center' as const,
    render: (row) => {
      const count = modelCountMap.value.get(row.id) || 0
      return h(NTag, { size: 'small', type: count > 0 ? 'info' : 'default' }, { default: () => String(count) })
    },
  },
  {
    title: '排序',
    key: 'sortOrder',
    width: 60,
    align: 'center' as const,
  },
  {
    title: '状态',
    key: 'enabled',
    width: 60,
    align: 'center' as const,
    render: (row) => h(NTag, { size: 'small', type: row.enabled ? 'success' : 'default' }, { default: () => row.enabled ? '启用' : '停用' }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 320,
    align: 'center' as const,
    fixed: 'right' as const,
    render: (row) =>
      h(NSpace, { size: 6, wrap: false }, {
        default: () => [
          h(NButton, { size: 'small', ghost: true, onClick: () => openEditMain(row) }, { default: () => '基础配置' }),
          h(NButton, { size: 'small', type: 'info', ghost: true, onClick: () => openProviderConfig(row) }, { default: () => '提供方配置' }),
          h(NButton, { size: 'small', type: 'primary', ghost: true, onClick: () => openModels(row) }, { default: () => '模型配置' }),
          h(NButton, { size: 'small', type: 'error', ghost: true, onClick: () => confirmDeleteProvider(row) }, { default: () => '删除' }),
        ],
      }),
  },
])

async function load() {
  loading.value = true
  try {
    rows.value = await adminListLlm()
    // 加载每个提供商的模型数量
    for (const row of rows.value) {
      try {
        const models = await adminListLlmModels(row.id)
        modelCountMap.value.set(row.id, models.length)
      } catch {
        modelCountMap.value.set(row.id, 0)
      }
    }
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
    forceTemperature: null,
    thinkingParamStyle: 'openai',
    jsonModeSystemHint: false,
    stripToolCallIndex: false,
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
      forceTemperature: detail.forceTemperature ?? null,
      thinkingParamStyle: detail.thinkingParamStyle || 'openai',
      jsonModeSystemHint: detail.jsonModeSystemHint ?? false,
      stripToolCallIndex: detail.stripToolCallIndex ?? false,
    }
    showMainModal.value = true
  } catch {
    message.error('加载详情失败')
  }
}

async function saveMain() {
  const name = form.value.displayName.trim()
  const baseUrl = form.value.baseUrl.trim()
  if (!name) { message.warning('请填写大模型名称'); return }
  if (!baseUrl) { message.warning('请填写 Base URL'); return }
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
      message.success('已创建，请通过「提供方配置」配置行为标志')
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
      if (form.value.apiKey.trim()) payload.apiKey = form.value.apiKey
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

function openProviderConfig(row: LlmAdminRow) {
  modelsProvider.value = row
  editingId.value = row.id
  form.value = {
    displayName: row.displayName,
    apiKey: '',
    baseUrl: row.baseUrl,
    enabled: row.enabled,
    sortOrder: row.sortOrder,
    forceTemperature: row.forceTemperature ?? null,
    thinkingParamStyle: row.thinkingParamStyle || 'openai',
    jsonModeSystemHint: row.jsonModeSystemHint ?? false,
    stripToolCallIndex: row.stripToolCallIndex ?? false,
  }
  showProviderConfigModal.value = true
}

async function saveProviderConfig() {
  const providerId = modelsProvider.value?.id
  if (providerId == null) return
  try {
    const payload: LlmUpdatePayload = {
      displayName: form.value.displayName,
      baseUrl: form.value.baseUrl,
      enabled: form.value.enabled,
      sortOrder: form.value.sortOrder != null && !Number.isNaN(Number(form.value.sortOrder))
        ? Number(form.value.sortOrder) : 0,
      forceTemperature: form.value.forceTemperature,
      thinkingParamStyle: form.value.thinkingParamStyle || null,
      jsonModeSystemHint: form.value.jsonModeSystemHint,
      stripToolCallIndex: form.value.stripToolCallIndex,
    }
    if (form.value.apiKey.trim()) payload.apiKey = form.value.apiKey
    await adminUpdateLlm(providerId, payload)
    message.success('已保存')
    showProviderConfigModal.value = false
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } }; message?: string }
    message.error(err.response?.data?.error || err.message || '保存失败')
  }
}

// ---- Model management ----
async function openModels(row: LlmAdminRow) {
  modelsProvider.value = row
  showModelsModal.value = true
  await loadModels(row.id)
}

async function loadModels(providerId: number) {
  modelsLoading.value = true
  try {
    modelRows.value = await adminListLlmModels(providerId)
  } catch (e: unknown) {
    const err = e as { message?: string }
    message.error(err.message || '加载模型列表失败')
  } finally {
    modelsLoading.value = false
  }
}

function openCreateModel() {
  modelEditId.value = null
  modelForm.value = defaultModelForm()
  showModelEditModal.value = true
}

function openEditModel(row: LlmModelAdminRow) {
  modelEditId.value = row.id
  modelForm.value = {
    name: row.name,
    textGeneration: row.textGeneration,
    deepThinking: row.deepThinking,
    vision: row.vision,
    streamOutput: row.streamOutput,
    toolCall: row.toolCall,
    forceThinkingEnabled: row.forceThinkingEnabled,
    temperature: row.temperature,
    maxTokens: row.maxTokens,
    topP: row.topP,
    contextWindow: row.contextWindow,
    responseFormat: row.responseFormat,
    sortOrder: row.sortOrder,
    enabled: row.enabled,
  }
  showModelEditModal.value = true
}

function openModelParams(row: LlmModelAdminRow) {
  modelEditId.value = row.id
  currentModelName.value = row.name
  modelForm.value = {
    name: row.name,
    textGeneration: row.textGeneration,
    deepThinking: row.deepThinking,
    vision: row.vision,
    streamOutput: row.streamOutput,
    toolCall: row.toolCall,
    forceThinkingEnabled: row.forceThinkingEnabled,
    temperature: row.temperature,
    maxTokens: row.maxTokens,
    topP: row.topP,
    contextWindow: row.contextWindow,
    responseFormat: row.responseFormat,
    sortOrder: row.sortOrder,
    enabled: row.enabled,
  }
  showModelParamModal.value = true
}

async function saveModelParams() {
  const providerId = modelsProvider.value?.id
  if (providerId == null) return
  const payload: LlmModelSavePayload = {
    providerId,
    name: modelForm.value.name,
    upstreamModelId: modelForm.value.name,
    textGeneration: true,
    deepThinking: modelForm.value.deepThinking,
    vision: modelForm.value.vision,
    streamOutput: modelForm.value.streamOutput,
    toolCall: modelForm.value.toolCall,
    forceThinkingEnabled: modelForm.value.forceThinkingEnabled,
    temperature: modelForm.value.temperature ?? undefined,
    maxTokens: modelForm.value.maxTokens ?? undefined,
    topP: modelForm.value.topP ?? undefined,
    contextWindow: modelForm.value.contextWindow ?? undefined,
    responseFormat: modelForm.value.responseFormat ?? undefined,
    sortOrder: modelForm.value.sortOrder ?? undefined,
    enabled: modelForm.value.enabled,
  }
  try {
    if (modelEditId.value == null) {
      await adminCreateLlmModel(payload)
      message.success('已创建')
    } else {
      await adminUpdateLlmModel(modelEditId.value, payload)
      message.success('已保存')
    }
    showModelParamModal.value = false
    await loadModels(providerId)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } }; message?: string }
    message.error(err.response?.data?.error || err.message || '保存失败')
  }
}

async function saveModel() {
  const providerId = modelsProvider.value?.id
  if (providerId == null) return
  const name = modelForm.value.name.trim()
  if (!name) { message.warning('请填写模型名称'); return }

  const payload: LlmModelSavePayload = {
    providerId,
    name,
    upstreamModelId: name,
    textGeneration: true,
    deepThinking: modelForm.value.deepThinking,
    vision: modelForm.value.vision,
    streamOutput: modelForm.value.streamOutput,
    toolCall: modelForm.value.toolCall,
    forceThinkingEnabled: modelForm.value.forceThinkingEnabled,
    temperature: modelForm.value.temperature ?? undefined,
    maxTokens: modelForm.value.maxTokens ?? undefined,
    topP: modelForm.value.topP ?? undefined,
    contextWindow: modelForm.value.contextWindow ?? undefined,
    responseFormat: modelForm.value.responseFormat ?? undefined,
    sortOrder: modelForm.value.sortOrder ?? undefined,
    enabled: modelForm.value.enabled,
  }

  try {
    if (modelEditId.value == null) {
      await adminCreateLlmModel(payload)
      message.success('已创建')
    } else {
      await adminUpdateLlmModel(modelEditId.value, payload)
      message.success('已保存')
    }
    showModelEditModal.value = false
    await loadModels(providerId)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } }; message?: string }
    message.error(err.response?.data?.error || err.message || '保存失败')
  }
}

function confirmDeleteModel(row: LlmModelAdminRow) {
  const providerId = modelsProvider.value?.id
  if (providerId == null) return
  dialog.warning({
    title: '删除模型',
    content: `确定删除模型「${row.name}」？该操作不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await adminDeleteLlmModel(row.id)
        message.success('已删除')
        await loadModels(providerId)
      } catch (e: unknown) {
        const err = e as { response?: { data?: { error?: string } }; message?: string }
        message.error(err.response?.data?.error || err.message || '删除失败')
      }
    },
  })
}

function confirmDeleteProvider(row: LlmAdminRow) {
  dialog.warning({
    title: '删除提供商',
    content: `确定删除提供商「${row.displayName}」？该提供商下的所有模型也将被删除。该操作不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await adminDeleteLlm(row.id)
        message.success('已删除')
        await load()
      } catch (e: unknown) {
        const err = e as { response?: { data?: { error?: string } }; message?: string }
        message.error(err.response?.data?.error || err.message || '删除失败')
      }
    },
  })
}

function back() {
  router.push('/chat')
}

function goTo(page: 'llm' | 'conversations' | 'api-keys' | 'ai-apps' | 'tools' | 'admin-accounts') {
  router.push(`/settings/${page}`)
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
        <div v-if="auth.isAdmin" class="menu-item" :class="{ active: currentPage === 'admin-accounts' }" @click="goTo('admin-accounts')">
          <span class="menu-icon">👤</span>
          <span class="menu-label">系统管理员</span>
        </div>
      </div>
    </div>
    <div class="settings-content">
      <div v-if="currentPage === 'llm'" class="settings-content-inner">
        <div class="page-inner">
          <n-card :bordered="false" class="card" title="模型管理">
            <template #header-extra>
              <n-button type="primary" @click="openCreate">+ 新增提供商</n-button>
            </template>
            <n-spin :show="loading">
              <n-empty v-if="!loading && !rows.length" description="暂无记录。" />
              <n-data-table
                v-else-if="rows.length"
                :columns="providerColumns"
                :data="rows"
                :bordered="true"
                size="small"
                :scroll-x="900"
              />
              <div v-else class="list-loading-gap" />
            </n-spin>
          </n-card>
        </div>
      </div>

      <!-- 提供商基础配置 modal -->
      <n-modal
        v-model:show="showMainModal"
        preset="card"
        :title="editingId == null ? '新增模型提供商' : '基础配置'"
        style="width: min(560px, 96vw)"
        :mask-closable="false"
      >
        <div class="sys-settings-main-panel">
          <n-form label-placement="top" label-width="auto">
            <n-form-item label="模型提供商">
              <n-input v-model:value="form.displayName" placeholder="如 月之暗面 Kimi" />
            </n-form-item>
            <n-form-item label="API Key" :description="editingId != null ? '留空表示不修改已保存的 Key' : undefined">
              <n-input v-model:value="form.apiKey" type="password" show-password-on="click" placeholder="sk-..." />
            </n-form-item>
            <n-form-item label="Base URL">
              <n-input v-model:value="form.baseUrl" placeholder="https://api.example.com/v1" />
            </n-form-item>
            <n-form-item label="排序（数字越小越靠前）" :description="editingId == null ? '留空则自动排在列表末尾' : undefined">
              <n-input-number v-model:value="form.sortOrder" :min="0" clearable placeholder="自动" class="form-sort" />
            </n-form-item>
            <n-form-item label="启用该提供方">
              <n-switch v-model:value="form.enabled" />
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

      <!-- 提供方配置 modal -->
      <n-modal
        v-model:show="showProviderConfigModal"
        preset="card"
        :title="`提供方配置 — ${modelsProvider?.displayName || ''}`"
        style="width: min(560px, 96vw)"
        :mask-closable="false"
      >
        <div class="sys-settings-main-panel">
          <n-form label-placement="top" label-width="auto">
            <n-form-item label="强制温度（force_temperature）" description="非空时强制覆盖所有请求的 temperature 值（如 Kimi 只接受 1.0）">
              <n-input-number v-model:value="form.forceTemperature" :min="0" :max="2" :step="0.01" clearable placeholder="不强制（留空）" class="form-sort" />
            </n-form-item>
            <n-form-item label="思考参数风格" description="openai=使用 thinking.type 参数；dashscope=使用 enable_thinking 参数">
              <n-select v-model:value="form.thinkingParamStyle" :options="thinkingParamStyleOptions" style="width: 200px" />
            </n-form-item>
            <n-form-item>
              <template #label>
                JSON 模式系统提示注入
                <n-tooltip trigger="hover">
                  <template #trigger>
                    <n-icon :component="HelpCircleOutline" style="margin-left:4px;cursor:help" />
                  </template>
                  开启后，当请求为 JSON_OBJECT 格式时，自动在 system message 中追加 JSON 提示（通义千问等兼容层需要）。
                </n-tooltip>
              </template>
              <n-switch v-model:value="form.jsonModeSystemHint" />
            </n-form-item>
            <n-form-item>
              <template #label>
                去除工具调用索引（strip_tool_call_index）
                <n-tooltip trigger="hover">
                  <template #trigger>
                    <n-icon :component="HelpCircleOutline" style="margin-left:4px;cursor:help" />
                  </template>
                  部分提供方的 tool_call 响应中 index 字段格式异常，开启后自动过滤。
                </n-tooltip>
              </template>
              <n-switch v-model:value="form.stripToolCallIndex" />
            </n-form-item>
          </n-form>
        </div>
        <template #footer>
          <n-space justify="end">
            <n-button @click="showProviderConfigModal = false">取消</n-button>
            <n-button type="primary" @click="saveProviderConfig">保存</n-button>
          </n-space>
        </template>
      </n-modal>

      <!-- 模型管理 modal -->
      <n-modal
        v-model:show="showModelsModal"
        preset="card"
        :title="`管理模型 — ${modelsProvider?.displayName || ''}`"
        style="width: min(900px, 98vw)"
        :mask-closable="false"
      >
        <n-space justify="end" style="margin-bottom: 12px">
          <n-button type="primary" @click="openCreateModel">+ 新增模型</n-button>
        </n-space>
        <n-spin :show="modelsLoading">
          <n-data-table
            :columns="modelColumns"
            :data="modelRows"
            :bordered="true"
            size="small"
            :scroll-x="700"
          />
        </n-spin>
        <template #footer>
          <n-space justify="end">
            <n-button @click="showModelsModal = false">关闭</n-button>
          </n-space>
        </template>
      </n-modal>

      <!-- 模型编辑 modal -->
      <n-modal
        v-model:show="showModelEditModal"
        preset="card"
        :title="modelEditId == null ? '新增模型' : '模型设置'"
        style="width: min(500px, 96vw)"
        :mask-closable="false"
      >
        <n-form label-placement="top" label-width="auto">
          <n-form-item label="模型名称（逻辑标识，对话里作为 mode 键）" required>
            <n-input v-model:value="modelForm.name" placeholder="如 qwen-plus" />
          </n-form-item>
          <n-form-item label="排序（数字越小越靠前）">
            <n-input-number
              v-model:value="modelForm.sortOrder"
              :min="0"
              placeholder="自动"
              clearable
              class="form-sort"
            />
          </n-form-item>

          <div class="model-cap-grid">
            <n-form-item label="文本生成">
              <n-switch :value="true" disabled />
            </n-form-item>
            <n-form-item label="深度思考">
              <n-switch v-model:value="modelForm.deepThinking" />
            </n-form-item>
            <n-form-item label="视觉理解">
              <n-switch v-model:value="modelForm.vision" />
            </n-form-item>
            <n-form-item label="流式输出">
              <n-switch v-model:value="modelForm.streamOutput" />
            </n-form-item>
            <n-form-item>
              <template #label>
                工具调用
                <n-tooltip trigger="hover">
                  <template #trigger>
                    <n-icon :component="HelpCircleOutline" style="margin-left:4px;cursor:help" />
                  </template>
                  开启后，绑定了工具的应用才可使用该模型进行工具调用。
                </n-tooltip>
              </template>
              <n-switch v-model:value="modelForm.toolCall" />
            </n-form-item>
            <n-form-item>
              <template #label>
                强制启用思考
                <n-tooltip trigger="hover">
                  <template #trigger>
                    <n-icon :component="HelpCircleOutline" style="margin-left:4px;cursor:help" />
                  </template>
                  对应旧版 MiniMax 强制思考逻辑，该模型每次请求都强制带 thinking 参数。
                </n-tooltip>
              </template>
              <n-switch v-model:value="modelForm.forceThinkingEnabled" />
            </n-form-item>
          </div>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="showModelEditModal = false">取消</n-button>
            <n-button type="primary" @click="saveModel">保存</n-button>
          </n-space>
        </template>
      </n-modal>

      <!-- 模型参数设置 modal -->
      <n-modal
        v-model:show="showModelParamModal"
        preset="card"
        :title="`参数设置 — ${currentModelName || ''}`"
        style="width: min(500px, 96vw)"
        :mask-closable="false"
      >
        <div class="sys-settings-main-panel">
          <n-form label-placement="top" label-width="auto">
            <n-form-item label="温度（temperature）" description="控制随机性，越高越随机，越低越确定">
              <n-input-number
                v-model:value="modelForm.temperature"
                :min="0" :max="2" :step="0.01"
                placeholder="不设置"
                class="form-sort"
              />
            </n-form-item>
            <n-form-item label="最大输出 token" description="模型单次回复的最大 token 数量">
              <n-input-number
                v-model:value="modelForm.maxTokens"
                :min="1" :max="200000" :step="1"
                placeholder="不设置"
                class="form-sort"
              />
            </n-form-item>
            <n-form-item label="Top P" description="核采样参数，控制多样性">
              <n-input-number
                v-model:value="modelForm.topP"
                :min="0" :max="1" :step="0.01"
                placeholder="不设置"
                class="form-sort"
              />
            </n-form-item>
            <n-form-item label="上下文窗口（token 数）" description="模型支持的上下文长度">
              <n-input-number
                v-model:value="modelForm.contextWindow"
                :min="0" :step="1024"
                placeholder="不设置"
                class="form-sort"
              />
            </n-form-item>
            <n-form-item label="回复格式">
              <n-select
                v-model:value="modelForm.responseFormat"
                :options="[
                  { label: '普通文本', value: 'TEXT' },
                  { label: 'JSON 模式', value: 'JSON_OBJECT' }
                ]"
                style="width: 200px"
              />
            </n-form-item>
            <n-form-item label="启用该模型">
              <n-switch v-model:value="modelForm.enabled" />
            </n-form-item>
          </n-form>
        </div>
        <template #footer>
          <n-space justify="end">
            <n-button @click="showModelParamModal = false">取消</n-button>
            <n-button type="primary" @click="saveModelParams">保存</n-button>
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
        <ToolsView is-embedded />
      </div>

      <!-- 系统管理员 -->
      <div v-if="currentPage === 'admin-accounts'" class="settings-content-inner-full">
        <AdminAccountsView is-embedded />
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
}
.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e5e7eb;
}
.back-btn {
  font-size: 14px;
}
.sidebar-menu {
  padding: 8px 0;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  transition: background 0.2s;
}
.menu-item:hover {
  background: #f3f4f6;
}
.menu-item.active {
  background: #eef2ff;
  border-right: 2px solid #6366f1;
}
.menu-icon {
  font-size: 18px;
  width: 20px;
  text-align: center;
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
.card {
  background: #ffffff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 12px;
}
.list-loading-gap {
  min-height: 100px;
}
.model-cap-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0 16px;
}
.form-sort {
  max-width: 220px;
}
</style>

<style>
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
