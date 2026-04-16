<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
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
  NText,
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
import type { ChatMessage } from '@/api/conversations'
import { renderChatMarkdown } from '@/utils/chatMarkdown'

const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const rows = ref<ApiKeyRow[]>([])

// ---- 新建 API Key ----
const showCreate = ref(false)
const createName = ref('')

// ---- 创建成功弹窗 ----
const secretModal = ref('')
const showSecretModal = ref(false)

// ---- 编辑 ----
const editId = ref<number | null>(null)
const editName = ref('')
const editEnabled = ref(true)

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

onMounted(() => {
  void load()
})

function openCreate() {
  createName.value = ''
  showCreate.value = true
}

async function submitCreate() {
  const name = createName.value.trim()
  if (!name) {
    message.warning('请填写名称')
    return
  }
  try {
    const created = await adminCreateApiKey({ name, type: 1 })
    showCreate.value = false
    secretModal.value = created.plainSecret ?? ''
    showSecretModal.value = true
    message.success('已创建')
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '创建失败')
  }
}

function openEdit(r: ApiKeyRow) {
  editId.value = r.id
  editName.value = r.name
  editEnabled.value = r.enabled
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
    title: '删除 API Key',
    content: `确定删除「${r.name}」？相关用量记录保留，但该 Key 将立即失效。`,
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

async function copyText(text: string) {
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(text)
      message.success('已复制')
      return
    }
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
    width: 180,
    ellipsis: { tooltip: true },
  },
  {
    title: '凭证',
    key: 'credential',
    width: 320,
    render: (r) => {
      const text = r.secretPrefix ?? '—'
      if (text === '—') return h('span', { style: 'color:#bbb' }, '—')
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
    width: 280,
    render: (r) =>
      h(NSpace, { size: 8, wrap: false }, () => [
        h(NButton, { size: 'small', onClick: () => openUsage(r) }, { default: () => '用量' }),
        h(NButton, { size: 'small', onClick: () => openEdit(r) }, { default: () => '编辑' }),
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
    { title: '会话ID', key: 'sessionId', width: 100, render: (r) => r.sessionId.slice(0, 8) + '…' },
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
          { size: 'tiny', type: 'primary', ghost: true, onClick: () => void openSessionMessages(keyId, r.sessionId) },
          { default: () => '查看消息' },
        ),
    },
  ]
}
</script>

<template>
  <div class="inner">
    <n-card :bordered="false" class="card" title="API Key 管理">
      <template #header-extra>
        <n-button type="primary" @click="openCreate">+ 新建 API Key</n-button>
      </template>
      <n-data-table :columns="columns" :data="rows" :loading="loading" :row-key="(r: ApiKeyRow) => r.id" />
    </n-card>
  </div>

  <!-- ============ 新建 API Key 弹窗 ============ -->
  <n-modal
    v-model:show="showCreate"
    preset="card"
    title="新建 API Key"
    style="width: min(420px, 96vw)"
    :mask-closable="false"
  >
    <n-form label-placement="top">
      <n-form-item label="名称（便于识别用途）">
        <n-input
          v-model:value="createName"
          placeholder="如 生产环境 / 合作方 A"
          @keyup.enter="submitCreate"
        />
      </n-form-item>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="showCreate = false">取消</n-button>
        <n-button type="primary" @click="submitCreate">创建</n-button>
      </n-space>
    </template>
  </n-modal>

  <!-- ============ 密钥弹窗（仅显示一次）============ -->
  <n-modal
    v-model:show="showSecretModal"
    preset="card"
    title="请立即保存 API Key"
    style="width: min(520px, 96vw)"
  >
    <n-text depth="3" style="display: block; margin-bottom: 12px">
      完整密钥仅显示一次，请复制到安全位置。调用时在请求头加入：<code>X-API-Key: …</code>
    </n-text>
    <n-input type="textarea" :value="secretModal" readonly :autosize="{ minRows: 2, maxRows: 4 }" />
    <template #footer>
      <n-space justify="end">
        <n-button type="primary" @click="void copyText(secretModal)">复制</n-button>
        <n-button @click="showSecretModal = false">关闭</n-button>
      </n-space>
    </template>
  </n-modal>

  <!-- ============ 编辑弹窗 ============ -->
  <n-modal
    :show="editId != null"
    preset="card"
    title="编辑 API Key"
    style="width: min(480px, 96vw)"
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
            <div v-if="m.role === 'assistant'" class="msg-md" v-html="renderChatMarkdown(m.content || '')" />
            <pre v-else class="msg-body">{{ m.content || '' }}</pre>
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
.msg-md {
  font-size: 13px; line-height: 1.6; color: #111827; word-break: break-word;
}
.msg-md :deep(p) { margin: 0.35em 0; }
.msg-md :deep(p:first-child) { margin-top: 0; }
.msg-md :deep(p:last-child) { margin-bottom: 0; }
.msg-md :deep(ul), .msg-md :deep(ol) { margin: 0.4em 0; padding-left: 1.3em; }
.msg-md :deep(h1), .msg-md :deep(h2), .msg-md :deep(h3), .msg-md :deep(h4) {
  margin: 0.55em 0 0.3em; font-weight: 600; line-height: 1.3;
}
.msg-md :deep(blockquote) {
  margin: 0.4em 0; padding: 0.3em 0 0.3em 0.8em;
  border-left: 3px solid #d1d5db; color: #4b5563;
}
.msg-md :deep(a) { color: #2563eb; text-decoration: underline; }
.msg-md :deep(p > code), .msg-md :deep(li > code), .msg-md :deep(td > code) {
  background: #f3f4f6; padding: 0.1em 0.35em; border-radius: 4px;
  font-family: ui-monospace, monospace; font-size: 0.88em;
}
.msg-md :deep(.md-code-block) {
  margin: 0.6em 0; border: 1px solid #e5e7eb; border-radius: 8px;
  overflow: hidden; background: #fff;
}
.msg-md :deep(.md-code-head) {
  display: flex; align-items: center; justify-content: space-between;
  gap: 8px; padding: 4px 10px; background: #f3f4f6;
  border-bottom: 1px solid #e5e7eb; font-size: 11px;
}
.msg-md :deep(.md-code-lang) { color: #6b7280; font-family: ui-monospace, monospace; }
.msg-md :deep(.md-copy-btn) {
  padding: 2px 7px; font-size: 11px; color: #374151;
  background: #fff; border: 1px solid #d1d5db; border-radius: 4px; cursor: pointer;
}
.msg-md :deep(.md-copy-btn:hover) { background: #f9fafb; }
.msg-md :deep(.md-code-pre) {
  margin: 0; padding: 8px 12px; overflow-x: auto;
  font-size: 12px; line-height: 1.5; background: #fafafa;
}
.msg-md :deep(.md-code-pre code) { font-family: ui-monospace, monospace; }
.msg-md :deep(table) { border-collapse: collapse; margin: 0.5em 0; font-size: 12px; width: 100%; }
.msg-md :deep(th), .msg-md :deep(td) { border: 1px solid #e5e7eb; padding: 0.3em 0.5em; }
.msg-md :deep(th) { background: #f9fafb; }
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
</style>
