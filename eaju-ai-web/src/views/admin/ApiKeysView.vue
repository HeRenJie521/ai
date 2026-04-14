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
  return `<iframe
  src="${embedBaseUrl.value}?iid=${id}&uid={用户手机号}&token={嵌入凭证}"
  width="100%"
  height="700px"
  style="border:none; border-radius:12px; box-shadow:0 4px 24px rgba(0,0,0,.1);"
  allow="clipboard-write"
></iframe>`
})

const embedCodeMobile = computed(() => {
  const id = createdIntegrationId.value
  if (!id) return ''
  return `<iframe
  src="${embedBaseUrl.value}?iid=${id}&uid={用户手机号}&token={嵌入凭证}"
  width="100%"
  height="100svh"
  style="border:none; display:block;"
  allow="clipboard-write"
></iframe>`
})

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
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
    minWidth: 200,
    render: (r) => {
      // API Key / WEB_EMBED：均展示 secretPrefix（前20位）
      const text = r.secretPrefix ?? '—'

      if (text === '—') return h('span', { style: 'color:#bbb' }, '—')

      return h('span', { class: 'credential-cell' }, [
        h('span', { class: 'credential-text', title: text }, text),
        h(
          'button',
          {
            class: 'copy-icon-btn',
            title: '复制',
            onClick: (e: MouseEvent) => {
              e.stopPropagation()
              void navigator.clipboard.writeText(text).then(
                () => message.success('已复制'),
                () => message.error('复制失败'),
              )
            },
          },
          [
            h(
              'svg',
              { width: 14, height: 14, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' },
              [
                h('rect', { x: '9', y: '9', width: '13', height: '13', rx: '2', ry: '2' }),
                h('path', { d: 'M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1' }),
              ],
            ),
          ],
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
    width: 400,
    render: (r) =>
      h(NSpace, { size: 8, wrap: false }, () => [
        h(NButton, { size: 'small', onClick: () => openUsage(r) }, { default: () => '用量' }),
        h(NButton, { size: 'small', onClick: () => openEdit(r) }, { default: () => '编辑' }),
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

function turnColumns(keyId: number): DataTableColumns<RecentTurnRow> {
  return [
    { title: '用户ID', key: 'userId', width: 120, ellipsis: { tooltip: true }, render: (r) => r.userId ?? '—' },
    { title: '模型', key: 'model', width: 130, ellipsis: { tooltip: true } },
    {
      title: 'Token',
      key: 'totalTokens',
      width: 70,
      render: (r) => (r.totalTokens != null ? String(r.totalTokens) : '—'),
    },
    {
      title: '会话ID',
      key: 'sessionId',
      width: 90,
      render: (r) => (r.sessionId ? r.sessionId.slice(0, 8) + '…' : '—'),
    },
    { title: '时间', key: 'createdAt', width: 152, render: (r) => fmtTime(r.createdAt) },
    {
      title: '',
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
      <n-form-item label="启用">
        <n-switch v-model:value="editEnabled" />
      </n-form-item>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="editId = null">取消</n-button>
        <n-button type="primary" @click="submitEdit">保存</n-button>
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
    <n-tabs type="line" animated>
      <!-- Tab 1: 嵌入密钥 -->
      <n-tab-pane name="token" tab="嵌入密钥">
        <template v-if="secretModal">
          <n-input
            type="textarea"
            :value="secretModal"
            readonly
            :autosize="{ minRows: 2, maxRows: 4 }"
            style="font-family: monospace"
          />
          <n-space style="margin-top: 8px">
            <n-button type="primary" @click="void copyText(secretModal)">复制嵌入密钥</n-button>
          </n-space>
        </template>
        <template v-else>
          <div class="token-hidden-tip">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            <span>嵌入密钥仅在创建时显示一次，若已遗失请删除后重新创建。</span>
          </div>
        </template>
      </n-tab-pane>

      <!-- Tab 2: PC 端代码 -->
      <n-tab-pane name="pc" tab="PC 端">
        <n-input
          type="textarea"
          :value="embedCodePc"
          readonly
          :autosize="{ minRows: 7, maxRows: 12 }"
          style="font-family: monospace; font-size: 12px"
        />
        <n-space style="margin-top: 8px">
          <n-button @click="void copyText(embedCodePc)">复制</n-button>
        </n-space>
      </n-tab-pane>

      <!-- Tab 3: 移动端代码 -->
      <n-tab-pane name="mobile" tab="移动端">
        <n-input
          type="textarea"
          :value="embedCodeMobile"
          readonly
          :autosize="{ minRows: 7, maxRows: 12 }"
          style="font-family: monospace; font-size: 12px"
        />
        <n-space style="margin-top: 8px">
          <n-button @click="void copyText(embedCodeMobile)">复制</n-button>
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
                :columns="turnColumns(usageKeyId)"
                :data="usageData.recentTurns"
                :max-height="360"
                :scroll-x="540"
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

/* 凭证列：文本 + 复制图标 */
.credential-cell {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  max-width: 100%;
}
.credential-text {
  font-family: ui-monospace, 'Cascadia Code', monospace;
  font-size: 12px;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: calc(100% - 34px);
}
.copy-icon-btn {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 1px solid #e5e7eb;
  background: #f9fafb;
  color: #6b7280;
  cursor: pointer;
  border-radius: 6px;
  transition: color 0.15s, background 0.15s, border-color 0.15s;
}
.copy-icon-btn:hover {
  color: #2563eb;
  background: #eff6ff;
  border-color: #bfdbfe;
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
</style>
