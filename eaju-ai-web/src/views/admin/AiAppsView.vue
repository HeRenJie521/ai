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
  NInputNumber,
  NModal,
  NSpace,
  NSpin,
  NTag,
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

// ---- 推荐问题（新建/编辑共用辅助状态）----
const newSuggestionCreate = ref('')
const newSuggestionEdit = ref('')
const suggestionItemsCreate = ref<string[]>([])
const suggestionItemsEdit = ref<string[]>([])

function defaultForm() {
  return {
    name: '',
    modelId: null as string | null,
    temperature: null as number | null,
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
    await adminCreateAiApp({
      name,
      modelId: createForm.value.modelId || undefined,
      temperature: createForm.value.temperature ?? undefined,
      welcomeText: createForm.value.welcomeText || undefined,
      suggestions: suggestionsJson,
      systemRole: createForm.value.systemRole || undefined,
      systemTask: createForm.value.systemTask || undefined,
      systemConstraints: createForm.value.systemConstraints || undefined,
    })
    showCreate.value = false
    message.success('已创建')
    await load()
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
    temperature: r.temperature,
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
      temperature: editForm.value.temperature ?? undefined,
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
    title: '删除 AI 应用',
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
    title: '温度',
    key: 'temperature',
    width: 80,
    render: (r) => r.temperature != null ? String(r.temperature) : '—',
  },
  {
    title: '开场白',
    key: 'welcomeText',
    width: 120,
    ellipsis: { tooltip: true },
    render: (r) =>
      r.welcomeText
        ? h(NTag, { size: 'small', bordered: false, type: 'success' }, () => '已配置')
        : h('span', { style: 'color:#bbb' }, '未配置'),
  },
  {
    title: '系统提示词',
    key: 'systemRole',
    width: 120,
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
    width: 140,
    render: (r) =>
      h(NSpace, { size: 8, wrap: false }, () => [
        h(NButton, { size: 'small', onClick: () => openEdit(r) }, { default: () => '编辑' }),
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
      <n-text strong class="page-title">AI 应用管理</n-text>
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
    title="新建 AI 应用"
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

      <n-form-item label="采样温度（可选，留空使用模型默认值）">
        <n-input-number
          v-model:value="createForm.temperature"
          :min="0"
          :max="2"
          :step="0.1"
          :precision="2"
          placeholder="如 0.7"
          style="width: 180px"
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

      <n-form-item label="角色设定（可选）">
        <n-input
          v-model:value="createForm.systemRole"
          type="textarea"
          placeholder="例如：你是一名专业的客服助手，熟悉公司产品与服务。"
          :autosize="{ minRows: 2, maxRows: 5 }"
        />
      </n-form-item>

      <n-form-item label="任务指令（可选）">
        <n-input
          v-model:value="createForm.systemTask"
          type="textarea"
          placeholder="例如：帮助用户解决售前、售后问题，引导用户下单。"
          :autosize="{ minRows: 2, maxRows: 5 }"
        />
      </n-form-item>

      <n-form-item label="限制条件（可选）">
        <n-input
          v-model:value="createForm.systemConstraints"
          type="textarea"
          placeholder="例如：不得讨论竞争对手；回答简洁，不超过 200 字。"
          :autosize="{ minRows: 2, maxRows: 5 }"
        />
        <template #feedback>三个提示词字段均为空时不下发 system prompt</template>
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
    title="编辑 AI 应用"
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

      <n-form-item label="采样温度（可选，留空使用模型默认值）">
        <n-input-number
          v-model:value="editForm.temperature"
          :min="0"
          :max="2"
          :step="0.1"
          :precision="2"
          placeholder="如 0.7"
          style="width: 180px"
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

      <n-form-item label="角色设定（可选）">
        <n-input
          v-model:value="editForm.systemRole"
          type="textarea"
          placeholder="例如：你是一名专业的客服助手，熟悉公司产品与服务。"
          :autosize="{ minRows: 2, maxRows: 5 }"
        />
      </n-form-item>

      <n-form-item label="任务指令（可选）">
        <n-input
          v-model:value="editForm.systemTask"
          type="textarea"
          placeholder="例如：帮助用户解决售前、售后问题，引导用户下单。"
          :autosize="{ minRows: 2, maxRows: 5 }"
        />
      </n-form-item>

      <n-form-item label="限制条件（可选）">
        <n-input
          v-model:value="editForm.systemConstraints"
          type="textarea"
          placeholder="例如：不得讨论竞争对手；回答简洁，不超过 200 字。"
          :autosize="{ minRows: 2, maxRows: 5 }"
        />
        <template #feedback>三个提示词字段均为空时不下发 system prompt</template>
      </n-form-item>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="editId = null">取消</n-button>
        <n-button type="primary" @click="submitEdit">保存</n-button>
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
</style>
