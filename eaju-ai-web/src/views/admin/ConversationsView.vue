<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NButton,
  NCard,
  NDataTable,
  NInput,
  NSelect,
  NPagination,
  NSpin,
  NText,
  NTag,
  NModal,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NList,
  NListItem,
  NIcon,
  useMessage,
} from 'naive-ui'
import { SearchOutline } from '@vicons/ionicons5'
import {
  adminListConversations,
  adminGetConversationDetail,
  adminGetConversationMessages,
  type ConversationAdminRow,
  type ConversationDetail,
} from '@/api/adminConversations'
import { adminListApiKeys, type ApiKeyRow } from '@/api/adminApiKeys'
import { adminListAiApps, type AiAppRow } from '@/api/adminAiApps'
import { renderChatMarkdown } from '@/utils/chatMarkdown'

const message = useMessage()

const loading = ref(false)
const rows = ref<ConversationAdminRow[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const queryUserId = ref('')
const queryApiKeyId = ref<number | null>(null)
const queryAppId = ref<number | null>(null)
const apiKeys = ref<ApiKeyRow[]>([])
const aiApps = ref<AiAppRow[]>([])

// 详情弹窗
const showDetailModal = ref(false)
const detailLoading = ref(false)
const detailData = ref<ConversationDetail | null>(null)
const messages = ref<{ role: string; content: string; reasoningContent?: string; createdAt?: string }[]>([])

function integrationTypeLabel(type: ApiKeyRow['type']): string {
  return type === 2 ? '嵌入网站' : 'API Key'
}

onMounted(async () => {
  await Promise.all([loadApiKeys(), loadAiApps()])
  await loadData()
})

async function loadApiKeys() {
  apiKeys.value = await adminListApiKeys()
}

async function loadAiApps() {
  try {
    aiApps.value = await adminListAiApps()
  } catch { /* 忽略 */ }
}

async function loadData() {
  loading.value = true
  try {
    const res = await adminListConversations(
      page.value - 1,
      pageSize.value,
      queryUserId.value || undefined,
      queryApiKeyId.value || undefined,
      queryAppId.value || undefined
    )
    rows.value = res.content
    total.value = res.totalElements
  } catch (e) {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  loadData()
}

function onPageChange(p: number) {
  page.value = p
  loadData()
}

function onPageSizeChange(s: number) {
  pageSize.value = s
  page.value = 1
  loadData()
}

async function showDetail(row: ConversationAdminRow) {
  showDetailModal.value = true
  detailLoading.value = true
  detailData.value = null
  messages.value = []
  try {
    detailData.value = await adminGetConversationDetail(row.sessionId)
    const msgs = await adminGetConversationMessages(row.sessionId)
    messages.value = msgs.map((m) => ({
      role: m.role,
      content: m.content || '',
      reasoningContent: m.reasoningContent || '',
      createdAt: m.createdAt ?? undefined,
    }))
  } catch (e) {
    message.error('加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

function formatTokens(n: number): string {
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return String(n)
}

const columns = [
  {
    title: '用户',
    key: 'userId',
    width: 160,
    align: 'center' as const,
  },
  {
    title: '会话标题',
    key: 'title',
    width: 240,
    ellipsis: { tooltip: true },
  },
  {
    title: '会话类型',
    key: 'type',
    width: 100,
    align: 'center' as const,
    render: (row: ConversationAdminRow) => {
      const isApp = row.type === 'APP' || row.type === 'EMBED'
      const tagType = row.type === 'API_KEY' ? 'warning' : isApp ? 'info' : 'success'
      const label = row.type === 'API_KEY' ? 'API Key' : isApp ? '应用' : 'Chat'
      return h(NTag, { type: tagType, size: 'small' }, { default: () => label })
    },
  },
  {
    title: '模型',
    key: 'lastModeKey',
    width: 180,
    align: 'center' as const,
    ellipsis: { tooltip: true },
    render: (row: ConversationAdminRow) => {
      if (!row.lastModeKey) return '—'
      // 如果有提供商显示名称，显示为「厂家·模型」格式
      if (row.lastProviderDisplayName) {
        return `${row.lastProviderDisplayName} · ${row.lastModeKey}`
      }
      return row.lastModeKey
    },
  },
  {
    title: '轮次',
    key: 'turnCount',
    width: 80,
    align: 'center' as const,
  },
  {
    title: 'Token',
    key: 'totalTokens',
    width: 80,
    align: 'center' as const,
    render: (row: ConversationAdminRow) => formatTokens(row.totalTokens),
  },
  {
    title: '状态',
    key: 'deletedAt',
    width: 80,
    align: 'center' as const,
    render: (row: ConversationAdminRow) =>
      row.deletedAt ? '已删除' : '正常',
  },
  {
    title: '最后消息',
    key: 'lastMessageAt',
    width: 180,
    align: 'center' as const,
    render: (row: ConversationAdminRow) =>
      row.lastMessageAt ? row.lastMessageAt.replace('T', ' ').slice(0, 19) : '-',
  },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    align: 'center' as const,
    render: (row: ConversationAdminRow) =>
      h(
        NButton,
        { size: 'small', quaternary: true, onClick: () => showDetail(row) },
        { default: () => '查看' }
      ),
  },
]
</script>

<template>
  <div class="page-inner">
    <n-card :bordered="false" class="card" title="会话管理">
        <div class="search-bar">
          <n-input
            v-model:value="queryUserId"
            placeholder="按手机号查询"
            clearable
            style="width: 200px"
            @keyup.enter="search"
          />
          <n-select
            v-model:value="queryApiKeyId"
            :options="apiKeys.map(k => ({ label: `${k.name}（${integrationTypeLabel(k.type)}）`, value: k.id }))"
            placeholder="按 API Key 筛选"
            clearable
            style="width: 160px"
            @update:value="() => { queryAppId = null; search() }"
          />
          <n-select
            v-model:value="queryAppId"
            :options="aiApps.map(a => ({ label: a.name, value: a.id }))"
            placeholder="按应用筛选"
            clearable
            style="width: 180px"
            @update:value="() => { queryApiKeyId = null; search() }"
          />
          <n-button type="primary" @click="search">
            <template #icon>
              <n-icon :component="SearchOutline" />
            </template>
            查询
          </n-button>
        </div>

        <n-spin :show="loading">
          <n-empty v-if="!loading && !rows.length" description="暂无会话记录" />
          <n-data-table
            v-else
            :columns="columns"
            :data="rows"
            :bordered="false"
            :single-line="false"
          />
        </n-spin>

        <div v-if="total > 0" class="pagination-wrap">
          <n-pagination
            v-model:page="page"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :item-count="total"
            show-size-picker
            @update:page="onPageChange"
            @update:page-size="onPageSizeChange"
          />
        </div>
      </n-card>
    </div>

    <n-modal
      v-model:show="showDetailModal"
      preset="card"
      title="会话详情"
      style="width: min(800px, 96vw)"
      :mask-closable="true"
    >
      <n-spin :show="detailLoading">
        <template v-if="detailData">
          <n-descriptions label-placement="left" :column="2" bordered>
            <n-descriptions-item label="会话ID">
              <n-text code>{{ detailData.sessionId }}</n-text>
            </n-descriptions-item>
            <n-descriptions-item label="用户">{{ detailData.userId }}</n-descriptions-item>
            <n-descriptions-item label="标题">{{ detailData.title }}</n-descriptions-item>
            <n-descriptions-item label="API Key">{{ detailData.apiKeyName || '-' }}</n-descriptions-item>
            <n-descriptions-item label="模型">{{ detailData.lastModeKey || '-' }}</n-descriptions-item>
            <n-descriptions-item label="状态">
              <n-tag :type="detailData.deletedAt ? 'error' : 'success'">
                {{ detailData.deletedAt ? '已删除' : '正常' }}
              </n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="创建时间">{{ detailData.createdAt?.replace('T', ' ').slice(0, 19) || '-' }}</n-descriptions-item>
            <n-descriptions-item label="最后消息">{{ detailData.lastMessageAt?.replace('T', ' ').slice(0, 19) || '-' }}</n-descriptions-item>
          </n-descriptions>

          <div v-if="detailData.usage" class="usage-section">
            <n-text strong>Token 用量</n-text>
            <n-descriptions :column="3" bordered size="small" style="margin-top: 8px">
              <n-descriptions-item label="Prompt">{{ formatTokens(detailData.usage.promptTokens) }}</n-descriptions-item>
              <n-descriptions-item label="Completion">{{ formatTokens(detailData.usage.completionTokens) }}</n-descriptions-item>
              <n-descriptions-item label="总计">{{ formatTokens(detailData.usage.totalTokens) }}</n-descriptions-item>
            </n-descriptions>
          </div>

          <div v-if="false" class="model-section">
            <n-text strong>各模型用量</n-text>
            <n-list bordered size="small" style="margin-top: 8px">
              <n-list-item v-for="m in detailData?.byModel" :key="m.model">
                <span>{{ m.model }}</span>
                <span style="float: right">
                  {{ m.turnCount }} 轮 · {{ formatTokens(m.totalTokens) }} tokens
                </span>
              </n-list-item>
            </n-list>
          </div>

          <div v-if="messages.length" class="messages-section">
            <n-text strong>对话记录</n-text>
            <div class="messages-list">
              <div
                v-for="(msg, idx) in messages"
                :key="idx"
                class="message-item"
                :class="msg.role"
              >
                <div class="message-bubble">
                  <div class="message-role">{{ msg.role === 'user' ? '提问' : 'AI回复' }}</div>
                  <details v-if="msg.role === 'assistant' && msg.reasoningContent" class="thinking-details">
                    <summary class="thinking-summary">思考过程</summary>
                    <pre class="thinking-content">{{ msg.reasoningContent }}</pre>
                  </details>
                  <div v-if="msg.role === 'assistant'" class="msg-md" v-html="renderChatMarkdown(msg.content)" />
                  <div v-else class="message-content">{{ msg.content }}</div>
                  <div v-if="msg.createdAt" class="message-time">{{ msg.createdAt.replace('T', ' ').slice(0, 19) }}</div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </n-spin>
    </n-modal>
</template>

<style scoped>
.conversations-page {
  padding: 20px;
  min-height: 100vh;
  background: #f5f5f5;
}
.page-inner {
  max-width: 100%;
  margin: 0 auto;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  font-size: 20px;
}
.card {
  margin-bottom: 20px;
}
.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.usage-section,
.model-section,
.messages-section {
  margin-top: 20px;
}
.messages-list {
  margin-top: 12px;
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #eee;
  border-radius: 4px;
  padding: 12px;
}
.message-item {
  display: flex;
  margin-bottom: 16px;
}
.message-item.user {
  justify-content: flex-end;
}
.message-item.assistant {
  justify-content: flex-start;
}
.message-item.user .message-bubble {
  background: #e8f4fd;
  border-radius: 12px 12px 4px 12px;
}
.message-item.assistant .message-bubble {
  background: #f0f0f0;
  border-radius: 12px 12px 12px 4px;
}
.message-bubble {
  max-width: 80%;
  padding: 10px 14px;
}
.message-role {
  font-weight: 500;
  color: #666;
  margin-bottom: 4px;
  font-size: 12px;
}
.message-content {
  white-space: pre-wrap;
  word-break: break-word;
}
.msg-md {
  font-size: 14px; line-height: 1.6; color: #111827; word-break: break-word;
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
.message-time {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
  text-align: right;
}
.thinking-details {
  margin-bottom: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f9fafb;
}
.thinking-summary {
  padding: 6px 10px;
  cursor: pointer;
  font-size: 12px;
  color: #6b7280;
  font-weight: 500;
}
.thinking-content {
  padding: 8px 12px 12px;
  font-size: 13px;
  color: #4b5563;
  white-space: pre-wrap;
  line-height: 1.6;
  border-top: 1px solid #e5e7eb;
}
</style>

<style scoped>
.conversations-page {
  min-height: 100%;
}
</style>
