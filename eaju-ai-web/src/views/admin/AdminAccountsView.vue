<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  NButton,
  NCard,
  NDataTable,
  NEmpty,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NSpace,
  NSpin,
  NSwitch,
  NTag,
  useDialog,
  useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import {
  adminListAdminAccounts,
  adminCreateAdminAccount,
  adminUpdateAdminAccount,
  adminDeleteAdminAccount,
  type AdminAccountRow,
  type AdminAccountSavePayload,
} from '@/api/adminAccount'

interface Props {
  isEmbedded?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isEmbedded: false,
})

const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const rows = ref<AdminAccountRow[]>([])
const loading = ref(false)

// ---- 管理员管理 modal ----
const showMainModal = ref(false)
const editingId = ref<number | null>(null)
const form = ref({
  phone: '',
  name: '',
  enabled: true,
})

const columns = computed<DataTableColumns<AdminAccountRow>>(() => [
  {
    title: '手机号',
    key: 'phone',
    minWidth: 120,
  },
  {
    title: '姓名',
    key: 'name',
    minWidth: 100,
  },
  {
    title: '状态',
    key: 'enabled',
    width: 80,
    align: 'center' as const,
    render: (row) =>
      h(NTag, { size: 'small', type: row.enabled ? 'success' : 'default' }, { default: () => row.enabled ? '启用' : '停用' }),
  },
  {
    title: '创建时间',
    key: 'createdAt',
    minWidth: 160,
    render: (row) => formatDate(row.createdAt),
  },
  {
    title: '更新时间',
    key: 'updatedAt',
    minWidth: 160,
    render: (row) => formatDate(row.updatedAt),
  },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    fixed: 'right' as const,
    render: (row) =>
      h(NSpace, { size: 6, wrap: false }, {
        default: () => [
          h(NButton, { size: 'small', ghost: true, onClick: () => openEdit(row) }, { default: () => '编辑' }),
          h(NButton, { size: 'small', type: 'error', ghost: true, onClick: () => confirmDelete(row) }, { default: () => '删除' }),
        ],
      }),
  },
])

async function load() {
  loading.value = true
  try {
    rows.value = await adminListAdminAccounts()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } }; message?: string }
    message.error(err.response?.data?.error || err.message || '加载管理员列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

// 监听嵌入模式变化，确保在嵌入模式下也能正确加载数据
watch(() => props.isEmbedded, () => {
  if (props.isEmbedded) {
    load()
  }
}, { immediate: true })

function resetForm() {
  editingId.value = null
  form.value = {
    phone: '',
    name: '',
    enabled: true,
  }
}

function openCreate() {
  resetForm()
  showMainModal.value = true
}

async function openEdit(row: AdminAccountRow) {
  editingId.value = row.id
  form.value = {
    phone: row.phone,
    name: row.name,
    enabled: row.enabled,
  }
  showMainModal.value = true
}

async function saveMain() {
  const phone = form.value.phone.trim()
  const name = form.value.name.trim()

  if (!phone) {
    message.warning('请填写手机号')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(phone)) {
    message.warning('手机号格式不正确')
    return
  }
  if (!name) {
    message.warning('请填写姓名')
    return
  }

  const payload: AdminAccountSavePayload = {
    phone,
    name,
    enabled: form.value.enabled,
  }

  try {
    if (editingId.value == null) {
      await adminCreateAdminAccount(payload)
      message.success('已创建')
    } else {
      await adminUpdateAdminAccount(editingId.value, payload)
      message.success('已保存')
    }
    showMainModal.value = false
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } }; message?: string }
    message.error(err.response?.data?.error || err.message || '保存失败')
  }
}

function confirmDelete(row: AdminAccountRow) {
  dialog.warning({
    title: '删除管理员',
    content: `确定删除管理员「${row.name}（${row.phone}）」？该操作不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await adminDeleteAdminAccount(row.id)
        message.success('已删除')
        await load()
      } catch (e: unknown) {
        const err = e as { response?: { data?: { error?: string } }; message?: string }
        message.error(err.response?.data?.error || err.message || '删除失败')
      }
    },
  })
}

function formatDate(timestamp: number): string {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function back() {
  router.push('/chat')
}

function goTo(page: string) {
  router.push(`/settings/${page}`)
}
</script>

<template>
  <div v-if="!isEmbedded" class="settings-page">
    <div class="settings-sidebar">
      <div class="sidebar-header">
        <n-button text @click="back" class="back-btn">← 返回对话</n-button>
      </div>
      <div class="sidebar-menu">
        <div class="menu-item" @click="goTo('llm')">
          <span class="menu-icon">⚙</span>
          <span class="menu-label">模型管理</span>
        </div>
        <div class="menu-item" @click="goTo('conversations')">
          <span class="menu-icon">💬</span>
          <span class="menu-label">会话管理</span>
        </div>
        <div class="menu-item" @click="goTo('ai-apps')">
          <span class="menu-icon">🤖</span>
          <span class="menu-label">Agent应用</span>
        </div>
        <div class="menu-item" @click="goTo('api-keys')">
          <span class="menu-icon">🔑</span>
          <span class="menu-label">API Key 管理</span>
        </div>
        <div class="menu-item" @click="goTo('tools')">
          <span class="menu-icon">🔧</span>
          <span class="menu-label">接口管理</span>
        </div>
        <div class="menu-item active">
          <span class="menu-icon">👤</span>
          <span class="menu-label">系统管理员</span>
        </div>
      </div>
    </div>
    <div class="settings-content">
      <div class="settings-content-inner">
        <div class="page-inner">
          <n-card :bordered="false" class="card" title="系统管理员">
            <template #header-extra>
              <n-button type="primary" @click="openCreate">+ 新增管理员</n-button>
            </template>
            <n-spin :show="loading">
              <n-empty v-if="!loading && !rows.length" description="暂无记录。" />
              <n-data-table
                v-else-if="rows.length"
                :columns="columns"
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
    </div>
  </div>

  <!-- 嵌入模式：只显示内容 -->
  <div v-else class="embedded-content">
    <n-card :bordered="false" class="card" title="系统管理员">
      <template #header-extra>
        <n-button type="primary" @click="openCreate">+ 新增管理员</n-button>
      </template>
      <n-spin :show="loading">
        <n-empty v-if="!loading && !rows.length" description="暂无记录。" />
        <n-data-table
          v-else-if="rows.length"
          :columns="columns"
          :data="rows"
          :bordered="true"
          size="small"
          :scroll-x="900"
        />
        <div v-else class="list-loading-gap" />
      </n-spin>
    </n-card>
  </div>

  <!-- 管理员管理 modal -->
  <n-modal
    v-model:show="showMainModal"
    preset="card"
    :title="editingId == null ? '新增系统管理员' : '编辑系统管理员'"
    style="width: min(460px, 96vw)"
    :mask-closable="false"
  >
    <n-form label-placement="top" :show-feedback="false" style="display:flex; flex-direction:column; gap:18px; padding:4px 0 8px">
      <n-form-item label="手机号" required>
        <n-input
          v-model:value="form.phone"
          placeholder="请输入手机号"
          :disabled="editingId != null"
          size="medium"
        />
        <template v-if="editingId != null" #feedback>
          <span style="font-size:12px; color:#aaa">手机号不可修改</span>
        </template>
      </n-form-item>
      <n-form-item label="姓名" required>
        <n-input v-model:value="form.name" placeholder="请输入姓名" size="medium" />
      </n-form-item>
      <n-form-item label="账号状态">
        <div style="display:flex; align-items:center; gap:10px">
          <n-switch v-model:value="form.enabled" />
          <span style="font-size:13px; color:#666">
            {{ form.enabled ? '已启用' : '已停用' }}
          </span>
        </div>
        <template #feedback>
          <span style="font-size:12px; color:#aaa">停用后该手机号将无法以管理员身份登录</span>
        </template>
      </n-form-item>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="showMainModal = false">取消</n-button>
        <n-button type="primary" @click="saveMain">保存</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<style scoped>
/* 独立模式样式 */
.settings-page {
  display: flex;
  height: 100vh;
  background: #f5f7f9;
}

.settings-sidebar {
  width: 180px;
  background: #fff;
  border-right: 1px solid #e8eaed;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e8eaed;
}

.back-btn {
  font-size: 14px;
}

.sidebar-menu {
  flex: 1;
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

.page-inner {
  max-width: 100%;
  margin: 0 auto;
}

/* 嵌入模式样式 */
.embedded-content {
  padding: 0;
}

.card {
  background: #ffffff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 12px;
}

.sys-settings-main-panel {
  padding: 8px 0;
}

.form-sort {
  width: 120px;
}

.model-cap-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin: 8px 0;
}

.list-loading-gap {
  height: 100px;
}
</style>
