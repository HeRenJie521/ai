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
  NSelect,
  NSpace,
  NSpin,
  NTag,
  NText,
  useDialog,
  useMessage,
} from 'naive-ui'
import {
  adminCreateApiDefinition,
  adminDeleteApiDefinition,
  adminListApiDefinitions,
  adminUpdateApiDefinition,
  type ApiDefinitionRow,
  type ApiDefinitionSavePayload,
} from '@/api/adminApiDefinitions'

const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const rows = ref<ApiDefinitionRow[]>([])

// ---- 新建 / 编辑 ----
const showModal = ref(false)
const editId = ref<number | null>(null)
const form = ref<ApiDefinitionSavePayload>({
  systemName: '',
  requestUrl: '',
  httpMethod: 'POST',
  contentType: 'application/json',
  remark: '',
})

const httpMethodOptions = [
  { label: 'GET', value: 'GET' },
  { label: 'POST', value: 'POST' },
  { label: 'PUT', value: 'PUT' },
  { label: 'DELETE', value: 'DELETE' },
  { label: 'PATCH', value: 'PATCH' },
]

const contentTypeOptions = [
  { label: 'application/json', value: 'application/json' },
  { label: 'application/x-www-form-urlencoded', value: 'application/x-www-form-urlencoded' },
]

async function load() {
  loading.value = true
  try {
    rows.value = await adminListApiDefinitions()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editId.value = null
  form.value = {
    systemName: '',
    requestUrl: '',
    httpMethod: 'POST',
    contentType: 'application/json',
    remark: '',
  }
  showModal.value = true
}

function openEdit(row: ApiDefinitionRow) {
  editId.value = row.id
  form.value = {
    systemName: row.systemName,
    requestUrl: row.requestUrl,
    httpMethod: row.httpMethod,
    contentType: row.contentType,
    remark: row.remark ?? '',
  }
  showModal.value = true
}

async function handleSave() {
  try {
    const payload: ApiDefinitionSavePayload = {
      systemName: form.value.systemName.trim(),
      requestUrl: form.value.requestUrl.trim(),
      httpMethod: form.value.httpMethod || 'POST',
      contentType: form.value.contentType,
      remark: form.value.remark?.trim() || undefined,
    }
    if (editId.value) {
      await adminUpdateApiDefinition(editId.value, payload)
      message.success('已更新')
    } else {
      await adminCreateApiDefinition(payload)
      message.success('已创建')
    }
    showModal.value = false
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '保存失败')
  }
}

function confirmDelete(row: ApiDefinitionRow) {
  dialog.warning({
    title: '确认删除',
    content: `确定删除接口"${row.systemName}"？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await adminDeleteApiDefinition(row.id)
        message.success('已删除')
        await load()
      } catch (e: unknown) {
        const err = e as { response?: { data?: { message?: string } }; message?: string }
        message.error(err.response?.data?.message || err.message || '删除失败')
      }
    },
  })
}

const columns: DataTableColumns<ApiDefinitionRow> = [
  {
    title: '系统名称',
    key: 'systemName',
    render: (row) => h(NText, { code: true }, { default: () => row.systemName }),
  },
  {
    title: '接口请求路径',
    key: 'requestUrl',
    ellipsis: { tooltip: true },
  },
  {
    title: '请求方式',
    key: 'httpMethod',
    width: 90,
    render: (row) => h(NTag, { size: 'small', type: 'info' }, { default: () => row.httpMethod }),
  },
  {
    title: '参数格式',
    key: 'contentType',
    width: 180,
    render: (row) => h(NTag, { size: 'small', type: 'info' }, { default: () => row.contentType }),
  },
  {
    title: '备注',
    key: 'remark',
    ellipsis: { tooltip: true },
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
    <NCard :bordered="false" class="card" title="接口管理">
      <template #header-extra>
        <NButton type="primary" @click="openCreate">+ 新建接口</NButton>
      </template>
      <NSpin :show="loading">
        <NDataTable :columns="columns" :data="rows" :bordered="false" size="small" />
      </NSpin>
    </NCard>

    <NModal v-model:show="showModal" preset="card" :title="editId ? '编辑接口' : '新建接口'" style="width:680px" :mask-closable="false">
      <NForm :model="form" label-placement="left" label-width="110px">
        <NFormItem label="系统名称" required>
          <NInput v-model:value="form.systemName" placeholder="如：请假系统" />
        </NFormItem>
        <NFormItem label="接口请求路径" required>
          <NInput v-model:value="form.requestUrl" placeholder="完整的 URL，如 https://api.example.com/leave" />
        </NFormItem>
        <NFormItem label="请求方式" required>
          <NSelect v-model:value="form.httpMethod" :options="httpMethodOptions" style="width:120px" />
        </NFormItem>
        <NFormItem label="参数格式" required>
          <NSelect v-model:value="form.contentType" :options="contentTypeOptions" style="width:280px" />
        </NFormItem>
        <NFormItem label="备注">
          <NInput
            v-model:value="form.remark"
            type="textarea"
            :rows="3"
            placeholder="可选，对接口的补充说明"
          />
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
