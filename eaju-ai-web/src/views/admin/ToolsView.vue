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
  NSelect,
  NSpace,
  NSpin,
  NSwitch,
  NText,
  useDialog,
  useMessage,
} from 'naive-ui'
import {
  adminCreateTool,
  adminDeleteTool,
  adminListTools,
  adminUpdateTool,
  type AiToolRow,
  type AiToolSavePayload,
} from '@/api/adminTools'
import {
  adminListApiDefinitions,
  type ApiDefinitionRow,
} from '@/api/adminApiDefinitions'

const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const rows = ref<AiToolRow[]>([])
const apiDefinitions = ref<ApiDefinitionRow[]>([])
const apiDefinitionLoading = ref(false)

// ---- 新建 / 编辑 ----
const showModal = ref(false)
const editId = ref<number | null>(null)
const selectedApiDefinitionId = ref<number | null>(null)
const form = ref<AiToolSavePayload>({
  name: '',
  description: '',
  httpMethod: 'POST',
  url: '',
  headersJson: '',
  bodyTemplate: '',
  contentType: 'application/json',
  paramsSchemaJson: '{"type":"object","properties":{},"required":[]}',
  enabled: true,
})

// 接口定义下拉选项
const apiDefinitionOptions = computed(() => {
  return apiDefinitions.value.map((api) => ({
    label: `${api.systemName} - ${api.httpMethod} - ${api.requestUrl}`,
    value: api.id,
    httpMethod: api.httpMethod,
    url: api.requestUrl,
    contentType: api.contentType,
  }))
})

// 当选择接口时，自动填充 httpMethod、url、contentType
function onApiDefinitionChange(apiDefId: number | null) {
  if (apiDefId) {
    const selected = apiDefinitions.value.find((api) => api.id === apiDefId)
    if (selected) {
      form.value.httpMethod = selected.httpMethod
      form.value.url = selected.requestUrl
      form.value.contentType = selected.contentType
    }
  }
}

async function loadApiDefinitions() {
  apiDefinitionLoading.value = true
  try {
    apiDefinitions.value = await adminListApiDefinitions()
  } catch (e: unknown) {
    // 静默失败，不影响主流程
  } finally {
    apiDefinitionLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    rows.value = await adminListTools()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editId.value = null
  selectedApiDefinitionId.value = null
  form.value = {
    name: '',
    description: '',
    httpMethod: 'POST',
    url: '',
    headersJson: '',
    bodyTemplate: '',
    contentType: 'application/json',
    paramsSchemaJson: '{"type":"object","properties":{},"required":[]}',
    enabled: true,
  }
  showModal.value = true
}

function openEdit(row: AiToolRow) {
  editId.value = row.id
  form.value = {
    name: row.name,
    description: row.description,
    httpMethod: row.httpMethod,
    url: row.url,
    headersJson: row.headersJson ?? '',
    bodyTemplate: row.bodyTemplate ?? '',
    contentType: row.contentType ?? 'application/json',
    paramsSchemaJson: row.paramsSchemaJson,
    enabled: row.enabled,
  }
  // 尝试匹配已保存的 URL 对应的接口定义
  const matched = apiDefinitions.value.find((api) => api.requestUrl === row.url && api.httpMethod === row.httpMethod)
  selectedApiDefinitionId.value = matched ? matched.id : null
  showModal.value = true
}

async function handleSave() {
  try {
    const payload: AiToolSavePayload = {
      name: form.value.name.trim(),
      description: form.value.description.trim(),
      httpMethod: form.value.httpMethod || 'POST',
      url: form.value.url.trim(),
      headersJson: form.value.headersJson?.trim() || undefined,
      bodyTemplate: form.value.bodyTemplate?.trim() || undefined,
      contentType: form.value.contentType || 'application/json',
      paramsSchemaJson: form.value.paramsSchemaJson.trim(),
      enabled: form.value.enabled,
    }
    if (editId.value) {
      await adminUpdateTool(editId.value, payload)
      message.success('已更新')
    } else {
      await adminCreateTool(payload)
      message.success('已创建')
    }
    showModal.value = false
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '保存失败')
  }
}

function confirmDelete(row: AiToolRow) {
  dialog.warning({
    title: '确认删除',
    content: `确定删除工具"${row.label}"？所有应用的绑定关系也会一并清除。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await adminDeleteTool(row.id)
        message.success('已删除')
        await load()
      } catch (e: unknown) {
        const err = e as { response?: { data?: { message?: string } }; message?: string }
        message.error(err.response?.data?.message || err.message || '删除失败')
      }
    },
  })
}

const columns: DataTableColumns<AiToolRow> = [
  {
    title: '名称 (name)',
    key: 'name',
    render: (row) => h(NText, { code: true }, { default: () => row.name }),
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
  void loadApiDefinitions()
  void load()
})
</script>

<template>
  <div>
    <NCard :bordered="false" class="card" title="AI 工具管理">
      <template #header-extra>
        <NButton type="primary" @click="openCreate">+ 新建工具</NButton>
      </template>
      <NSpin :show="loading">
        <NDataTable :columns="columns" :data="rows" :bordered="false" size="small" />
      </NSpin>
    </NCard>

    <NModal v-model:show="showModal" preset="card" :title="editId ? '编辑工具' : '新建工具'" style="width:680px" :mask-closable="false">
      <NForm :model="form" label-placement="left" label-width="110px">
        <NFormItem label="工具名称" required>
          <NInput v-model:value="form.name" placeholder="英文，如 query_leave_balance" :disabled="!!editId" />
        </NFormItem>
        <NFormItem label="功能描述" required>
          <NInput
            v-model:value="form.description"
            type="textarea"
            :rows="2"
            placeholder="LLM 据此决定何时调用此工具"
          />
        </NFormItem>
        <NFormItem label="接口选择" required>
          <NSelect
            v-model:value="selectedApiDefinitionId"
            :options="apiDefinitionOptions"
            placeholder="选择已配置的接口"
            clearable
            style="width:100%"
            @update:value="onApiDefinitionChange"
          />
        </NFormItem>
        <NFormItem label="请求头 (JSON)">
          <NInput
            v-model:value="form.headersJson"
            type="textarea"
            :rows="3"
            placeholder='{"Authorization": "Bearer {{token}}"}'
          />
        </NFormItem>
        <NFormItem label="请求体模板">
          <NInput
            v-model:value="form.bodyTemplate"
            type="textarea"
            :rows="3"
            placeholder="留空则自动将 LLM 工具参数序列化为 JSON body"
          />
        </NFormItem>
        <NFormItem label="参数 Schema" required>
          <NInput
            v-model:value="form.paramsSchemaJson"
            type="textarea"
            :rows="5"
            placeholder='{"type":"object","properties":{"query":{"type":"string","description":"关键词"}},"required":["query"]}'
          />
        </NFormItem>
        <NFormItem label="状态">
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
