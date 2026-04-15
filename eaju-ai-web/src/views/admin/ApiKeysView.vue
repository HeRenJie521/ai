<script setup lang="ts">
import { h, onMounted, ref, watch, computed } from 'vue'
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
  NRadioGroup,
  NRadio,
  NSelect,
  NSpace,
  NSpin,
  NSwitch,
  NTag,
  NText,
  NTabs,
  NTabPane,
  useDialog,
  useMessage,
} from 'naive-ui'
import {
  adminApiKeySessionMessages,
  adminApiKeyUsage,
  adminCreateApiKey,
  adminDeleteApiKey,
  adminListApiKeys,
  adminPatchApiKey,
  type ApiKeyRow,
  type ApiKeyUsage,
  type RecentTurnRow,
} from '@/api/adminApiKeys'
import { listLlmProviders, type LlmProviderOption } from '@/api/llmProviders'
import type { ChatMessage } from '@/api/conversations'

const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const rows = ref<ApiKeyRow[]>([])

// ---- 新建集成 ----
const showCreate = ref(false)
const createName = ref('')
const createType = ref<1 | 2>(1)
const createDefaultModel = ref<string | null>(null)
const createAllowedOrigins = ref('')

// 可选模型列表（用于嵌入网站默认模型下拉）
const llmProviders = ref<LlmProviderOption[]>([])
const modelOptions = computed(() => {
  const opts: { label: string; value: string }[] = []
  for (const p of llmProviders.value) {
    if (p.modes && Object.keys(p.modes).length > 0) {
      // value 存储 mode key（逻辑名），传给后端 mode 参数
      for (const modeKey of Object.keys(p.modes)) {
        opts.push({ label: `${p.displayName} · ${modeKey}`, value: modeKey })
      }
    } else {
      opts.push({ label: p.displayName, value: p.code })
    }
  }
  return opts
})

// ---- 创建成功弹窗 ----
const secretModal = ref('')
const secretTitle = ref('')
const showSecretModal = ref(false)
const isEmbedModal = ref(false)
const embedBaseUrl = computed(() => {
  return window.location.origin + '/embed'
})

// ---- 编辑 ----
const editId = ref<number | null>(null)
const editName = ref('')
const editEnabled = ref(true)

// ---- 开场白配置弹窗 ----
const showWelcomeConfig = ref(false)
const welcomeConfigId = ref<number | null>(null)
const welcomeConfigName = ref('')
const welcomeText = ref('')
const suggestionItems = ref<string[]>([])
const newSuggestion = ref('')

// ---- 用量抽屉 ----
const usageOpen = ref(false)
const usageKeyId = ref<number | null>(null)
const usageKeyName = ref('')
const usageLoading = ref(false)
const usageData = ref<ApiKeyUsage | null>(null)

// ---- 消息查看 ----
const msgOpen = ref(false)
const msgLoading = ref(false)
const msgSessionId = ref('')
const msgList = ref<ChatMessage[]>([])

async function load() {
  loading.value = true
  try {
    rows.value = await adminListApiKeys()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadLlmProviders() {
  try {
    llmProviders.value = await listLlmProviders()
  } catch { /* 忽略 */ }
}

onMounted(() => {
  void load()
  void loadLlmProviders()
})

watch(showSecretModal, (v) => {
  if (!v) secretModal.value = ''
})

function openCreate() {
  createName.value = ''
  createType.value = 1
  createDefaultModel.value = null
  createAllowedOrigins.value = ''
  showCreate.value = true
}

async function submitCreate() {
  const name = createName.value.trim()
  if (!name) {
    message.warning('请填写名称')
    return
  }
  if (createType.value === 2 && !createDefaultModel.value) {
    message.warning('嵌入网站集成必须选择默认模型')
    return
  }
  try {
    const created = await adminCreateApiKey({
      name,
      type: createType.value,
      defaultModel: createType.value === 2 ? createDefaultModel.value ?? undefined : undefined,
      allowedOrigins: createAllowedOrigins.value.trim() || undefined,
    })
    showCreate.value = false

    if (createType.value === 2) {
      // 嵌入网站：展示嵌入密钥和嵌入代码（密钥仅创建时可见）
      isEmbedModal.value = true
      secretTitle.value = '嵌入配置已生成'
      secretModal.value = created.plainSecret ?? ''
      createdIntegrationId.value = created.id
      showSecretModal.value = true
    } else {
      // API Key：展示密钥
      isEmbedModal.value = false
      secretTitle.value = '请立即保存集成密钥'
      secretModal.value = created.plainSecret ?? ''
      showSecretModal.value = true
    }
    message.success('已创建')
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '创建失败')
  }
}

// 当前弹窗展示的集成 ID（用于生成嵌入代码）
const createdIntegrationId = ref<number | null>(null)
const embedCodePc = computed(() => {
  const id = createdIntegrationId.value
  if (!id) return ''
  // Token 仅在创建时可见，已创建的集成显示占位符提示用户替换
  const token = secretModal.value || '{凭证-创建时自动填入}'
  return `<iframe
  src="${embedBaseUrl.value}?iid=${id}&uid={手机号}&username={姓名}&token=${token}"
  width="100%"
  height="100%"
  style="height: 100vh; border:none; border-radius:12px; box-shadow:0 4px 24px rgba(0,0,0,.1);"
  allow="clipboard-write">
</iframe>`
})

const embedCodeMobile = computed(() => {
  const id = createdIntegrationId.value
  if (!id) return ''
  const token = secretModal.value || '{凭证-创建时自动填入}'
  return `<iframe
  src="${embedBaseUrl.value}?iid=${id}&uid={手机号}&username={姓名}&token=${token}"
  width="100%"
  style="height: 100svh; border:none; display:block;"
  allow="clipboard-write">
</iframe>`
})

async function copyText(text: string) {
  try {
    // 优先使用现代 Clipboard API
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(text)
      message.success('已复制')
      return
    }
    // 降级方案：使用传统 execCommand（兼容 HTTP 环境）
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}

function closeSecretReveal() {
  showSecretModal.value = false
  createdIntegrationId.value = null
}

function openEdit(r: ApiKeyRow) {
  editId.value = r.id
  editName.value = r.name
  editEnabled.value = r.enabled
}

/** 查看已有嵌入网站集成的嵌入代码（不展示 Token，Token 只在创建时显示一次） */
function openEmbedCode(r: ApiKeyRow) {
  createdIntegrationId.value = r.id
  isEmbedModal.value = true
  secretTitle.value = `嵌入方式 · ${r.name}`
  secretModal.value = '' // Token 已不可见，只展示代码和签名说明
  showSecretModal.value = true
}

async function submitEdit() {
  const id = editId.value
  if (id == null) return
  const name = editName.value.trim()
  if (!name) {
    message.warning('请填写名称')
    return
  }
  try {
    await adminPatchApiKey(id, { name, enabled: editEnabled.value })
    message.success('已保存')
    editId.value = null
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '保存失败')
  }
}

// ---- 开场白配置管理 ----
function openWelcomeConfig(r: ApiKeyRow) {
  welcomeConfigId.value = r.id
  welcomeConfigName.value = r.name
  welcomeText.value = r.welcomeText || ''
  suggestionItems.value = r.suggestions ? JSON.parse(r.suggestions) : []
  newSuggestion.value = ''
  showWelcomeConfig.value = true
}

async function saveWelcomeConfig() {
  const id = welcomeConfigId.value
  if (id == null) return

  const suggestionsJson = suggestionItems.value.length > 0 ? JSON.stringify(suggestionItems.value) : null

  try {
    await adminPatchApiKey(id, {
      welcomeText: welcomeText.value || undefined,
      suggestions: suggestionsJson || undefined,
    })
    message.success('已保存')
    showWelcomeConfig.value = false
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '保存失败')
  }
}

function addSuggestion() {
  const text = newSuggestion.value.trim()
  if (!text) {
    message.warning('请输入推荐问题')
    return
  }
  if (suggestionItems.value.length >= 10) {
    message.warning('最多添加 10 个推荐问题')
    return
  }
  suggestionItems.value.push(text)
  newSuggestion.value = ''
}

function removeSuggestion(index: number) {
  suggestionItems.value.splice(index, 1)
}

function handleSuggestionKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    e.preventDefault()
    addSuggestion()
  }
}

async function toggleEnabled(r: ApiKeyRow) {
  try {
    await adminPatchApiKey(r.id, { enabled: !r.enabled })
    message.success('已更新')
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '更新失败')
  }
}

function confirmDelete(r: ApiKeyRow) {
  dialog.warning({
    title: '删除集成',
    content: `确定删除「${r.name}」？相关用量记录保留，但集成将立即失效。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await adminDeleteApiKey(r.id)
        message.success('已删除')
        await load()
      } catch (e: unknown) {
        const err = e as { response?: { data?: { message?: string } }; message?: string }
        message.error(err.response?.data?.message || err.message || '删除失败')
      }
    },
  })
}

async function openUsage(r: ApiKeyRow) {
  usageKeyId.value = r.id
  usageKeyName.value = r.name
  usageData.value = null
  usageOpen.value = true
  usageLoading.value = true
  try {
    usageData.value = await adminApiKeyUsage(r.id)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '加载用量失败')
  } finally {
    usageLoading.value = false
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

async function openSessionMessages(keyId: number, sessionId: string) {
  msgSessionId.value = sessionId
  msgList.value = []
  msgOpen.value = true
  msgLoading.value = true
  try {
    msgList.value = await adminApiKeySessionMessages(keyId, sessionId)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '加载消息失败')
  } finally {
    msgLoading.value = false
  }
}

function fmtTime(val: string | null): string {
  if (!val) return '—'
  const d = new Date(val)
  if (isNaN(d.getTime())) return val
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const columns: DataTableColumns<ApiKeyRow> = [
  {
    title: '名称',
    key: 'name',
    width: 150,
    ellipsis: { tooltip: true },
  },
  {
    title: '类型',
    key: 'type',
    width: 90,
    render: (r) =>
      h(
        NTag,
        { size: 'small', bordered: false, type: r.type === 2 ? 'info' : 'default' },
        () => (r.type === 2 ? '嵌入网站' : 'API Key'),
      ),
  },
  {
    title: '凭证',
    key: 'credential',
    width: 320,
    render: (r) => {
      const text = r.secretPrefix ?? '—'

      if (text === '—') return h('span', { style: 'color:#bbb' }, '—')

      // 隐藏中间部分，只显示前8位和后4位
      const maskedText = text.length > 12 ? `${text.slice(0, 8)}••••••••${text.slice(-4)}` : text

      return h('div', { style: 'display:flex; align-items:center;' }, [
        h('span', { style: 'font-family:monospace; font-size:13px; white-space:nowrap;', title: text }, maskedText),
        h(
          NButton,
          {
            size: 'small',
            type: 'primary',
            ghost: true,
            style: 'margin-left: 12px; flex-shrink: 0;',
            onClick: (e: MouseEvent) => {
              e.stopPropagation()
              void navigator.clipboard.writeText(text).then(
                () => message.success('已复制'),
                () => message.error('复制失败'),
              )
            },
          },
          { default: () => '复制' },
        ),
      ])
    },
  },
  {
    title: '默认模型',
    key: 'defaultModel',
    width: 150,
    ellipsis: { tooltip: true },
    render: (r) => r.defaultModel ?? '—',
  },
  {
    title: '状态',
    key: 'enabled',
    width: 72,
    render: (r) =>
      h(NTag, { size: 'small', bordered: false, type: r.enabled ? 'success' : 'default' }, () =>
        r.enabled ? '启用' : '停用',
      ),
  },
  {
    title: '创建时间',
    key: 'createdAt',
    width: 160,
    render: (r) => fmtTime(r.createdAt),
  },
  {
    title: '操作',
    key: 'actions',
    width: 480,
    render: (r) =>
      h(NSpace, { size: 8, wrap: false }, () => [
        h(NButton, { size: 'small', onClick: () => openUsage(r) }, { default: () => '用量' }),
        h(NButton, { size: 'small', onClick: () => openEdit(r) }, { default: () => '编辑' }),
        ...(r.type === 2
          ? [h(
              NButton,
              { size: 'small', type: 'primary', ghost: true, onClick: () => openWelcomeConfig(r) },
              { default: () => '开场白' },
            )]
          : []),
        ...(r.type === 2
          ? [h(
              NButton,
              { size: 'small', type: 'info', ghost: true, onClick: () => openEmbedCode(r) },
              { default: () => '嵌入方式' },
            )]
          : []),
        h(
          NButton,
          { size: 'small', type: r.enabled ? 'warning' : 'success', ghost: true, onClick: () => toggleEnabled(r) },
          { default: () => (r.enabled ? '停用' : '启用') },
        ),
        h(
          NButton,
          { size: 'small', type: 'error', ghost: true, onClick: () => confirmDelete(r) },
          { default: () => '删除' },
        ),
      ]),
  },
]

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

function sessionColumns(keyId: number): DataTableColumns<SessionGroup> {
  return [
    { title: '用户ID', key: 'userId', width: 130, ellipsis: { tooltip: true }, render: (r) => r.userId ?? '—' },
    {
      title: '会话ID',
      key: 'sessionId',
      width: 100,
      render: (r) => r.sessionId.slice(0, 8) + '…',
    },
    { title: '次数', key: 'turnCount', width: 60 },
    { title: '模型', key: 'models', width: 140, ellipsis: { tooltip: true } },
    { title: 'Token', key: 'totalTokens', width: 80 },
    { title: '最近时间', key: 'lastAt', width: 152, render: (r) => fmtTime(r.lastAt) },
    {
      title: '操作',
      key: 'open',
      width: 88,
      fixed: 'right',
      render: (r) =>
        h(
          NButton,
          {
            size: 'tiny',
            type: 'primary',
            ghost: true,
            onClick: () => void openSessionMessages(keyId, r.sessionId),
          },
          { default: () => '查看消息' },
        ),
    },
  ]
}
</script>

<template>
  <div class="inner">
    <header class="toolbar">
      <n-text strong class="page-title">集成管理</n-text>
      <n-space :size="12" wrap>
        <n-button type="primary" @click="openCreate">新建集成</n-button>
      </n-space>
    </header>

    <n-card :bordered="false" class="card" title="集成列表">
      <n-data-table :columns="columns" :data="rows" :loading="loading" :row-key="(r: ApiKeyRow) => r.id" />
    </n-card>
  </div>

  <!-- ============ 新建集成弹窗 ============ -->
  <n-modal
    v-model:show="showCreate"
    preset="card"
    title="新建集成"
    style="width: min(480px, 96vw)"
    :mask-closable="false"
  >
    <n-form label-placement="top">
      <!-- 集成类型 -->
      <n-form-item label="集成类型">
        <n-radio-group v-model:value="createType">
          <n-space>
            <n-radio :value="1">API Key（直接调用 REST API）</n-radio>
            <n-radio :value="2">嵌入网站（iframe 聊天窗口）</n-radio>
          </n-space>
        </n-radio-group>
      </n-form-item>

      <!-- 名称 -->
      <n-form-item label="名称（便于识别用途）">
        <n-input
          v-model:value="createName"
          placeholder="如 官网嵌入 / 合作方 A"
          @keyup.enter="createType === 1 ? submitCreate() : undefined"
        />
      </n-form-item>

      <!-- WEB_EMBED 专属 -->
      <template v-if="createType === 2">
        <n-form-item label="默认对话模型" required>
          <n-select
            v-model:value="createDefaultModel"
            :options="modelOptions"
            placeholder="请选择默认模型"
            filterable
          />
        </n-form-item>
        <n-form-item label="允许嵌入的来源域名（可选，逗号分隔）">
          <n-input
            v-model:value="createAllowedOrigins"
            placeholder="如 https://example.com, https://partner.com"
          />
          <template #feedback>为空则不限制来源</template>
        </n-form-item>
      </template>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="showCreate = false">取消</n-button>
        <n-button type="primary" @click="submitCreate">创建</n-button>
      </n-space>
    </template>
  </n-modal>

  <!-- ============ 编辑弹窗 ============ -->
  <n-modal
    :show="editId != null"
    preset="card"
    title="编辑集成"
    style="width: min(420px, 96vw)"
    :mask-closable="false"
    @update:show="(v: boolean) => { if (!v) editId = null }"
  >
    <n-form v-if="editId != null" label-placement="top">
      <n-form-item label="名称">
        <n-input v-model:value="editName" />
      </n-form-item>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="editId = null">取消</n-button>
        <n-button type="primary" @click="submitEdit">保存</n-button>
      </n-space>
    </template>
  </n-modal>

  <!-- ============ 开场白配置弹窗 ============ -->
  <n-modal
    v-model:show="showWelcomeConfig"
    preset="card"
    :title="`开场白配置 · ${welcomeConfigName}`"
    style="width: min(600px, 96vw)"
    :mask-closable="false"
  >
    <n-form label-placement="top">
      <n-form-item label="开场白文本（可选）">
        <n-input
          v-model:value="welcomeText"
          type="textarea"
          placeholder="例如：你好，我是 AI 助手，有什么可以帮助您的吗？"
          :autosize="{ minRows: 3, maxRows: 6 }"
        />
        <template #feedback>
          用户打开聊天页面且无对话记录时显示
        </template>
      </n-form-item>
      <n-form-item label="推荐问题（最多 10 个）">
        <div style="width: 100%; display: flex; flex-direction: column; gap: 12px;">
          <!-- 已添加的问题列表 -->
          <div v-if="suggestionItems.length > 0" class="suggestion-list">
            <div
              v-for="(item, index) in suggestionItems"
              :key="index"
              class="suggestion-item"
            >
              <span class="suggestion-text">{{ item }}</span>
              <n-button
                size="tiny"
                type="error"
                ghost
                @click="removeSuggestion(index)"
              >
                删除
              </n-button>
            </div>
          </div>
          <div v-else class="suggestion-empty">
            暂无推荐问题，请添加
          </div>
          <!-- 添加问题输入框 -->
          <div style="display: flex; gap: 8px; width: 100%;">
            <n-input
              v-model:value="newSuggestion"
              style="flex: 1;"
              placeholder="输入推荐问题，按 Enter 添加"
              @keydown="handleSuggestionKeydown"
            />
            <n-button type="primary" @click="addSuggestion">
              添加
            </n-button>
          </div>
        </div>
        <template #feedback>
          用户点击推荐问题后自动发送，按 Enter 快速添加
        </template>
      </n-form-item>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="showWelcomeConfig = false">取消</n-button>
        <n-button type="primary" @click="saveWelcomeConfig">保存</n-button>
      </n-space>
    </template>
  </n-modal>
  <!-- ============ API Key 密钥弹窗 ============ -->
  <n-modal
    v-if="!isEmbedModal"
    v-model:show="showSecretModal"
    preset="card"
    :title="secretTitle"
    style="width: min(520px, 96vw)"
  >
    <n-text depth="3" style="display: block; margin-bottom: 12px">
      完整集成密钥仅显示一次，请复制到安全位置。请求开放接口时在请求头加入：
      <code>X-API-Key: …</code>
    </n-text>
    <n-input type="textarea" :value="secretModal" readonly :autosize="{ minRows: 3, maxRows: 8 }" />
    <template #footer>
      <n-space justify="end">
        <n-button type="primary" @click="void copyText(secretModal)">复制密钥</n-button>
        <n-button @click="closeSecretReveal">关闭</n-button>
      </n-space>
    </template>
  </n-modal>

  <!-- ============ WEB_EMBED 嵌入配置弹窗 ============ -->
  <n-modal
    v-if="isEmbedModal"
    v-model:show="showSecretModal"
    preset="card"
    :title="secretTitle"
    style="width: min(680px, 96vw)"
    :on-after-leave="closeSecretReveal"
  >
    <n-alert type="warning" :bordered="false" style="margin-bottom: 16px">
      <template v-if="secretModal">
        嵌入代码已自动生成，<code>{手机号}</code> 需要替换为实际用户手机号。
      </template>
      <template v-else>
        凭证仅在创建时可见，请将下方代码中的 <code>{凭证-创建时自动填入}</code> 替换为创建时生成的完整凭证。
      </template>
    </n-alert>

    <n-tabs type="line" animated>
      <!-- Tab 1: PC 端代码 -->
      <n-tab-pane name="pc" tab="PC 端">
        <n-input
          type="textarea"
          :value="embedCodePc"
          readonly
          :autosize="{ minRows: 7, maxRows: 12 }"
          style="font-family: monospace; font-size: 12px"
        />
        <n-space style="margin-top: 8px">
          <n-button type="primary" @click="void copyText(embedCodePc)">复制</n-button>
        </n-space>
      </n-tab-pane>

      <!-- Tab 2: 移动端代码 -->
      <n-tab-pane name="mobile" tab="移动端">
        <n-input
          type="textarea"
          :value="embedCodeMobile"
          readonly
          :autosize="{ minRows: 7, maxRows: 12 }"
          style="font-family: monospace; font-size: 12px"
        />
        <n-space style="margin-top: 8px">
          <n-button type="primary" @click="void copyText(embedCodeMobile)">复制</n-button>
        </n-space>
      </n-tab-pane>
    </n-tabs>

    <template #footer>
      <n-space justify="end">
        <n-button @click="showSecretModal = false">关闭</n-button>
      </n-space>
    </template>
  </n-modal>

  <!-- ============ 用量抽屉 ============ -->
  <n-drawer v-model:show="usageOpen" :width="760" placement="right">
    <n-drawer-content :title="`用量 · ${usageKeyName}`" closable>
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
            <div v-if="usageKeyId != null">
              <n-text strong style="display: block; margin-bottom: 8px">调用记录</n-text>
              <n-data-table
                :columns="sessionColumns(usageKeyId)"
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
            <pre class="msg-body">{{ m.content || '' }}</pre>
            <div v-if="m.createdAt" class="msg-time">{{ fmtTime(m.createdAt) }}</div>
          </div>
        </div>
      </div>
    </n-spin>
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

/* 凭证列：文本 + 复制按钮 */
.credential-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.credential-text {
  font-family: ui-monospace, 'Cascadia Code', 'SF Mono', Consolas, monospace;
  font-size: 13px;
  color: #374151;
  letter-spacing: 0.5px;
  /* 固定宽度，确保按钮位置固定 */
  width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-shrink: 0;
}

/* 密钥不可见提示 */
.token-hidden-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 8px;
  color: #92400e;
  font-size: 13px;
}


/* 消息查看 */
.msg-preview {
  max-height: 70vh;
  overflow-y: auto;
  padding: 4px 0;
  font-size: 13px;
}
.msg-row { display: flex; margin-bottom: 12px; }
.msg-row--left  { justify-content: flex-start; }
.msg-row--right { justify-content: flex-end; }
.msg-bubble {
  max-width: 75%;
  padding: 8px 12px;
  border-radius: 14px;
}
.msg-bubble--ai   { background: #f4f4f5; border-bottom-left-radius: 4px; }
.msg-bubble--user { background: #e8f4fd; border-bottom-right-radius: 4px; }
.msg-role-label { font-size: 11px; color: #999; margin-bottom: 4px; }
.msg-thinking {
  margin-bottom: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
  padding: 6px 10px;
}
.msg-thinking-summary {
  cursor: pointer; font-size: 12px; font-weight: 600; color: #6b7280;
  user-select: none; list-style: none;
}
.msg-thinking-summary::-webkit-details-marker { display: none; }
.msg-thinking-summary::before { content: '▶ '; font-size: 10px; }
details[open] .msg-thinking-summary::before { content: '▼ '; }
.msg-thinking-body { margin-top: 6px; font-size: 12px; color: #6b7280; }
.msg-body {
  margin: 0; white-space: pre-wrap; word-break: break-word;
  font-family: inherit; line-height: 1.5;
}
.msg-time { margin-top: 4px; font-size: 11px; color: #aaa; text-align: right; }
code { font-size: 12px; }
.msg-modal-header {
  display: flex; align-items: center; gap: 10px; flex-wrap: nowrap; min-width: 0;
}
.msg-modal-title { font-weight: 600; white-space: nowrap; }
.msg-modal-session {
  font-size: 12px; color: #999; font-family: ui-monospace, monospace;
  word-break: break-all; min-width: 0;
}

/* 开场白配置弹窗 - 推荐问题列表 */
.suggestion-list {
  max-height: 300px;
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
  padding: 8px 12px;
  margin-bottom: 6px;
  background: #fff;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
}
.suggestion-item:last-child {
  margin-bottom: 0;
}
.suggestion-text {
  flex: 1;
  font-size: 13px;
  color: #1f2937;
  margin-right: 12px;
  word-break: break-word;
}
.suggestion-empty {
  padding: 24px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
  border: 1px dashed #d1d5db;
  border-radius: 8px;
  margin-bottom: 8px;
}
</style>
