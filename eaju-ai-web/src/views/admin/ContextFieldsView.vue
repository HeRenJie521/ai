<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
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
  NSwitch,
  NTag,
  useDialog,
  useMessage,
} from 'naive-ui'
import {
  adminCreateContextField,
  adminDeleteContextField,
  adminListContextFields,
  adminUpdateContextField,
  type ContextFieldRow,
  type ContextFieldSavePayload,
} from '@/api/adminContextFields'

const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const rows = ref<ContextFieldRow[]>([])

// ---- 新建 / 编辑 ----
const showModal = ref(false)
const editId = ref<number | null>(null)
const form = ref<ContextFieldSavePayload & { enabled: boolean }>({
  fieldKey: '',
  label: '',
  description: '',
  enabled: true,
})

async function load() {
  loading.value = true
  try {
    rows.value = await adminListContextFields()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editId.value = null
  form.value = { fieldKey: '', label: '', description: '', enabled: true }
  showModal.value = true
}

function openEdit(row: ContextFieldRow) {
  editId.value = row.id
  form.value = {
    fieldKey: row.fieldKey,
    label: row.label,
    description: row.description ?? '',
    enabled: row.enabled,
  }
  showModal.value = true
}

async function handleSave() {
  try {
    if (editId.value) {
      await adminUpdateContextField(editId.value, {
        fieldKey: form.value.fieldKey.trim(),
        label: form.value.label.trim(),
        description: form.value.description?.trim() || undefined,
        enabled: form.value.enabled,
      })
      message.success('已更新')
    } else {
      await adminCreateContextField({
        fieldKey: form.value.fieldKey.trim(),
        label: form.value.label.trim(),
        description: form.value.description?.trim() || undefined,
      })
      message.success('已创建')
    }
    showModal.value = false
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '保存失败')
  }
}

function confirmDelete(row: ContextFieldRow) {
  dialog.warning({
    title: '确认删除',
    content: `确定删除上下文字段"${row.label}"？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await adminDeleteContextField(row.id)
        message.success('已删除')
        await load()
      } catch (e: unknown) {
        const err = e as { response?: { data?: { message?: string } }; message?: string }
        message.error(err.response?.data?.message || err.message || '删除失败')
      }
    },
  })
}

const columns: DataTableColumns<ContextFieldRow> = [
  {
    title: 'Key',
    key: 'fieldKey',
    render: (row) =>
      h('span', { style: 'font-family:monospace' }, row.fieldKey),
  },
  { title: '显示名', key: 'label' },
  { title: '说明', key: 'description' },
  {
    title: '状态',
    key: 'enabled',
    width: 80,
    render: (row) =>
      h(NTag, { size: 'small', type: row.enabled ? 'success' : 'default' }, {
        default: () => (row.enabled ? '启用' : '禁用'),
      }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 140,
    render: (row) =>
      h(NSpace, { size: 'small' }, {
        default: () => [
          h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => '编辑' }),
          h(NButton, { size: 'small', type: 'error', onClick: () => confirmDelete(row) }, { default: () => '删除' }),
        ],
      }),
  },
]

onMounted(() => {
  void load()
})
</script>

<template>
  <div>
    <NCard :bordered="false" class="card" title="用户上下文字段">
      <template #header-extra>
        <NButton type="primary" @click="openCreate">+ 新建字段</NButton>
      </template>
      <div style="color:#888; font-size:12px; margin-bottom:12px;">
        配置允许从登录请求中提取的字段 key（白名单），可用于工具调用中的 &#123;&#123;var&#125;&#125; 变量替换。
      </div>
      <NSpin :show="loading">
        <NDataTable :columns="columns" :data="rows" :bordered="false" size="small" />
      </NSpin>
    </NCard>

    <NModal v-model:show="showModal" preset="card" :title="editId ? '编辑字段' : '新建字段'" style="width:480px" :mask-closable="false">
      <NForm :model="form" label-placement="left" label-width="80px">
        <NFormItem label="字段 Key" required>
          <NInput v-model:value="form.fieldKey" placeholder="英文，如 department" :disabled="!!editId" />
        </NFormItem>
        <NFormItem label="显示名" required>
          <NInput v-model:value="form.label" placeholder="如：部门" />
        </NFormItem>
        <NFormItem label="说明">
          <NInput v-model:value="form.description" type="textarea" :rows="2" placeholder="可选" />
        </NFormItem>
        <NFormItem v-if="editId" label="状态">
          <NSwitch v-model:value="form.enabled" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showModal = false">取消</NButton>
          <NButton type="primary" @click="handleSave">保存</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
