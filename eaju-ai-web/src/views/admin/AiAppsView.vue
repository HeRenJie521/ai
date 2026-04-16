<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import type { DataTableColumns } from 'naive-ui'
import {
  NButton,
  NCard,
  NDataTable,
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
  useDialog,
  useMessage,
} from 'naive-ui'
import {
  adminCreateAiApp,
  adminDeleteAiApp,
  adminListAiApps,
  adminUpdateAiApp,
  type AiAppRow,
} from '@/api/adminAiApps'
import { listLlmProviders, type LlmProviderOption } from '@/api/llmProviders'

const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const rows = ref<AiAppRow[]>([])

// ---- 模型选项 ----
const llmProviders = ref<LlmProviderOption[]>([])
const modelOptions = computed(() => {
  const opts: { label: string; value: string }[] = []
  for (const p of llmProviders.value) {
    if (p.modes && Object.keys(p.modes).length > 0) {
      for (const modeKey of Object.keys(p.modes)) {
        opts.push({ label: `${p.displayName} · ${modeKey}`, value: modeKey })
      }
    } else {
      opts.push({ label: p.displayName, value: p.code })
    }
  }
  return opts
})

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

// ---- 推荐问题（新建/编辑共用辅助状态）----
const newSuggestionCreate = ref('')
const newSuggestionEdit = ref('')
const suggestionItemsCreate = ref<string[]>([])
const suggestionItemsEdit = ref<string[]>([])

function defaultForm() {
  return {
    name: '',
    modelId: null as string | null,
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

async function loadLlmProviders() {
  try {
    llmProviders.value = await listLlmProviders()
  } catch { /* 忽略 */ }
}

onMounted(() => {
  void load()
  void loadLlmProviders()
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
      modelId: createForm.value.modelId || undefined,
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
    modelId: r.modelId,
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
      modelId: editForm.value.modelId || undefined,
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
  const id = promptRow.value?.id
  if (id == null) return
  try {
    await adminUpdateAiApp(id, {
      name: promptRow.value.name,
      modelId: promptRow.value.modelId || undefined,
      welcomeText: promptRow.value.welcomeText || undefined,
      suggestions: promptRow.value.suggestions || '',
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
  const params = new URLSearchParams()
  params.set('aid', String(aid))
  return `${embedOrigin.value}/embed?${params.toString()}`
})

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

const columns: DataTableColumns<AiAppRow> = [
  {
    title: '应用名称',
    key: 'name',
    width: 160,
    ellipsis: { tooltip: true },
  },
  {
    title: '默认模型',
    key: 'modelId',
    width: 180,
    ellipsis: { tooltip: true },
    render: (r) => r.modelId ?? h('span', { style: 'color:#bbb' }, '—'),
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
    width: 260,
    render: (r) =>
      h(NSpace, { size: 8, wrap: false, justify: 'center' }, () => [
        h(NButton, { size: 'small', onClick: () => openEdit(r) }, { default: () => '编辑' }),
        h(
          NButton,
          { size: 'small', type: 'info', ghost: true, onClick: () => openPrompt(r) },
          { default: () => 'Prompt' },
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
    <header class="toolbar">
      <n-text strong class="page-title">应用管理</n-text>
      <n-space :size="12" wrap>
        <n-button type="primary" @click="openCreate">新建应用</n-button>
      </n-space>
    </header>

    <n-card :bordered="false" class="card" title="应用列表">
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
    style="width: min(640px, 96vw)"
    :mask-closable="false"
  >
    <n-form label-placement="top">
      <n-form-item label="应用名称" required>
        <n-input v-model:value="createForm.name" placeholder="如 官网客服助手" />
      </n-form-item>

      <n-form-item label="默认对话模型">
        <n-select
          v-model:value="createForm.modelId"
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
    style="width: min(640px, 96vw)"
    :mask-closable="false"
    @update:show="(v: boolean) => { if (!v) editId = null }"
  >
    <n-form v-if="editId != null" label-placement="top">
      <n-form-item label="应用名称" required>
        <n-input v-model:value="editForm.name" />
      </n-form-item>

      <n-form-item label="默认对话模型">
        <n-select
          v-model:value="editForm.modelId"
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
  padding: 16px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
  border: 1px dashed #d1d5db;
  border-radius: 8px;
}
.embed-code-block {
  position: relative;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 8px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.embed-code {
  margin: 0;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  color: #1f2937;
}
.embed-hint {
  font-size: 12px;
  color: #6b7280;
  margin: 0;
}

/* 表格标题居中 */
:deep(.n-data-table th.n-data-table-th) {
  text-align: center;
}

/* 表格内容居中 */
:deep(.n-data-table td.n-data-table-td) {
  text-align: center;
}
</style>
