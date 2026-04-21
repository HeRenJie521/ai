<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import type { DataTableColumns } from 'naive-ui'
import {
  NButton,
  NCard,
  NDataTable,
  NDivider,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  NSwitch,
  NTag,
  NText,
  useDialog,
  useMessage,
} from 'naive-ui'
import {
  adminCreateTool,
  adminDeleteTool,
  adminListTools,
  adminTestTool,
  adminUpdateTool,
  type AiToolRow,
} from '@/api/adminTools'
import {
  adminCreateApiDefinition,
  adminDeleteApiDefinition,
  adminListApiDefinitions,
  adminUpdateApiDefinition,
  type ApiDefinitionRow,
  type ApiDefinitionSavePayload,
} from '@/api/adminApiDefinitions'
import {
  adminListContextFields,
  type ContextFieldRow,
} from '@/api/adminContextFields'
import ParamNodeEditor, { type ParamNode } from '@/components/ParamNodeEditor.vue'
import ResponseParamEditor, { type ResponseParam } from '@/components/ResponseParamEditor.vue'

const message = useMessage()
const dialog = useDialog()

// ==================== 数据 ====================
const loading = ref(false)
const rows = ref<AiToolRow[]>([])
const apiDefinitions = ref<ApiDefinitionRow[]>([])
const contextFields = ref<ContextFieldRow[]>([])

// ToolParam / ChildParam 统一用 ParamNode（来自 ParamNodeEditor.vue，支持无限嵌套）
// ResponseParam 来自 ResponseParamEditor.vue

// ==================== 出参来源（入参管理 → 出参传递选项） ====================
interface ResponseSource { toolName: string; paramName: string; paramLabel: string }
const responseSources = ref<ResponseSource[]>([])

// ==================== 系统API配置 ====================
const apiLoading = ref(false)
const apiEditId = ref<number | null>(null)
const showApiForm = ref(false)
const apiForm = ref<ApiDefinitionSavePayload>({
  systemName: '', requestUrl: '', httpMethod: 'POST',
  contentType: 'application/json', remark: '',
})

const httpMethodOptions = [
  { label: 'GET', value: 'GET' }, { label: 'POST', value: 'POST' },
  { label: 'PUT', value: 'PUT' }, { label: 'DELETE', value: 'DELETE' },
  { label: 'PATCH', value: 'PATCH' },
]
const contentTypeOptions = [
  { label: 'application/json', value: 'application/json' },
  { label: 'application/x-www-form-urlencoded', value: 'application/x-www-form-urlencoded' },
]
// const fieldTypeOptions = [
//   { label: 'String', value: 'String' }, { label: 'Number', value: 'Number' },
//   { label: 'Boolean', value: 'Boolean' }, { label: 'Object', value: 'Object' },
//   { label: 'Array', value: 'Array' },
// ]

const apiColumns: DataTableColumns<ApiDefinitionRow> = [
  {
    title: '系统名称', key: 'systemName', width: 140,
    render: (row) => h(NText, { strong: true }, { default: () => row.systemName }),
  },
  {
    title: '请求路径', key: 'requestUrl', ellipsis: { tooltip: true },
    render: (row) => h('span', { style: 'font-size:12px; font-family:monospace; color:#555' }, row.requestUrl),
  },
  {
    title: '方式', key: 'httpMethod', width: 65,
    render: (row) => h(NTag, { size: 'small', type: 'info' }, { default: () => row.httpMethod }),
  },
  {
    title: '备注', key: 'remark', width: 120, ellipsis: { tooltip: true },
  },
  {
    title: '操作', key: 'actions', width: 120,
    render: (row) =>
      h(NSpace, { size: 'small' }, {
        default: () => [
          h(NButton, { size: 'small', onClick: () => openApiEditInDrawer(row) }, { default: () => '编辑' }),
          h(NButton, { size: 'small', type: 'error', onClick: () => confirmApiDelete(row) }, { default: () => '删除' }),
        ],
      }),
  },
]

function openApiCreate() {
  apiEditId.value = null
  apiForm.value = { systemName: '', requestUrl: '', httpMethod: 'POST', contentType: 'application/json', remark: '' }
  showApiForm.value = true
}

function openApiEditInDrawer(row?: ApiDefinitionRow) {
  if (row) {
    apiEditId.value = row.id
    apiForm.value = { systemName: row.systemName, requestUrl: row.requestUrl, httpMethod: row.httpMethod, contentType: row.contentType, remark: row.remark ?? '' }
  } else {
    apiEditId.value = null
    apiForm.value = { systemName: '', requestUrl: '', httpMethod: 'POST', contentType: 'application/json', remark: '' }
  }
  showApiForm.value = true
}

// ==================== 系统API配置 Drawer ====================
const showApiDrawer = ref(false)

function openApiModal() {
  showApiDrawer.value = true
  showApiForm.value = false
}

async function handleApiSave() {
  if (!apiForm.value.systemName.trim()) { message.warning('请填写系统名称'); return }
  if (!apiForm.value.requestUrl.trim()) { message.warning('请填写请求路径'); return }
  try {
    const payload = { ...apiForm.value, systemName: apiForm.value.systemName.trim(), requestUrl: apiForm.value.requestUrl.trim(), remark: apiForm.value.remark?.trim() || undefined }
    if (apiEditId.value) {
      await adminUpdateApiDefinition(apiEditId.value, payload)
      message.success('已更新')
    } else {
      await adminCreateApiDefinition(payload)
      message.success('已创建')
    }
    showApiForm.value = false
    await loadAll()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '保存失败')
  }
}

function confirmApiDelete(row: ApiDefinitionRow) {
  dialog.warning({
    title: '确认删除', content: `确定删除接口"${row.systemName}"？`,
    positiveText: '删除', negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await adminDeleteApiDefinition(row.id)
        message.success('已删除')
        await loadAll()
      } catch (e: unknown) {
        const err = e as { response?: { data?: { message?: string } }; message?: string }
        message.error(err.response?.data?.message || err.message || '删除失败')
      }
    },
  })
}

// ==================== 新建 / 编辑接口 ====================
const showModal = ref(false)
const editId = ref<number | null>(null)
const selectedApiDefId = ref<number | null>(null)
const form = ref({
  name: '', label: '', description: '',
  paramsSchemaJson: '{"type":"object","properties":{},"required":[]}',
  enabled: true, httpMethod: 'POST', url: '', contentType: 'application/json',
})

const apiDefOptions = computed(() =>
  apiDefinitions.value.map((api) => ({ label: api.systemName, value: api.id }))
)
const contextFieldOptions = computed(() =>
  contextFields.value.filter((f) => f.enabled)
    .map((f) => ({ label: f.label, value: f.fieldKey }))
)

function onApiDefChange(id: number | null) {
  if (!id) return
  const def = apiDefinitions.value.find((d) => d.id === id)
  if (def) { form.value.httpMethod = def.httpMethod; form.value.url = def.requestUrl; form.value.contentType = def.contentType }
}

async function loadAll() {
  loading.value = true
  try {
    const [toolsRes, defsRes, fieldsRes] = await Promise.all([adminListTools(), adminListApiDefinitions(), adminListContextFields()])
    rows.value = toolsRes; apiDefinitions.value = defsRes; contextFields.value = fieldsRes
    collectResponseSources()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '加载失败')
  } finally { loading.value = false }
}

function openCreate() {
  editId.value = null; selectedApiDefId.value = null
  form.value = { name: '', label: '', description: '', paramsSchemaJson: '{"type":"object","properties":{},"required":[]}', enabled: true, httpMethod: 'POST', url: '', contentType: 'application/json' }
  showModal.value = true
}

function openEdit(row: AiToolRow) {
  editId.value = row.id
  const matched = apiDefinitions.value.find((api) => api.requestUrl === row.url && api.httpMethod === row.httpMethod)
  selectedApiDefId.value = matched ? matched.id : null
  form.value = { name: row.name, label: row.label || '', description: row.description, paramsSchemaJson: row.paramsSchemaJson, enabled: row.enabled, httpMethod: row.httpMethod, url: row.url, contentType: row.contentType ?? 'application/json' }
  showModal.value = true
}

async function handleSave() {
  if (!form.value.name.trim()) { message.warning('请填写工具名称'); return }
  if (!form.value.label.trim()) { message.warning('请填写显示名称'); return }
  if (!form.value.description.trim()) { message.warning('请填写功能描述'); return }
  if (!form.value.url.trim()) { message.warning('请选择业务系统'); return }
  try {
    const payload = { name: form.value.name.trim(), label: form.value.label.trim(), description: form.value.description.trim(), httpMethod: form.value.httpMethod, url: form.value.url, contentType: form.value.contentType, paramsSchemaJson: form.value.paramsSchemaJson.trim(), enabled: form.value.enabled }
    if (editId.value) { await adminUpdateTool(editId.value, payload); message.success('已更新') }
    else { await adminCreateTool(payload); message.success('已创建') }
    showModal.value = false; await loadAll()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '保存失败')
  }
}

// ==================== 入参管理 ====================
const showParamsModal = ref(false)
const paramsToolId = ref<number | null>(null)
const paramsToolName = ref('')
const paramsToolContentType = ref('application/json')
const params = ref<ParamNode[]>([])
const paramsSaving = ref(false)

/** 递归将旧格式参数节点迁移为新 ParamNode（兼容 valueType/value 旧字段） */
function migrateParamNode(p: any): ParamNode {
  const hasChildren = Array.isArray(p.children) && p.children.length > 0
  const oldValueType = p.valueType as string | undefined
  const isOldObject = oldValueType === 'object'
  return {
    key: p.key || '',
    fieldType: p.fieldType || (isOldObject ? 'Object' : 'String'),
    valueSource: p.valueSource || (isOldObject ? 'static' : oldValueType === 'context' ? 'context' : 'static'),
    sourceValue: p.sourceValue ?? p.value ?? '',
    fieldKey: p.fieldKey || '',
    children: hasChildren ? p.children.map(migrateParamNode) : [],
  }
}

function openParams(row: AiToolRow) {
  paramsToolId.value = row.id
  paramsToolName.value = row.label || row.name
  paramsToolContentType.value = row.contentType ?? 'application/json'
  try {
    const parsed = row.dataParamsJson ? JSON.parse(row.dataParamsJson) : []
    params.value = (parsed as any[]).map(migrateParamNode)
  } catch { params.value = [] }
  collectResponseSources()
  showParamsModal.value = true
}
/** 递归校验参数树，返回第一个错误信息或 null */
function validateParamNodes(nodes: ParamNode[], path = ''): string | null {
  for (const p of nodes) {
    const fullKey = path ? `${path}.${p.key}` : p.key
    if (!p.key.trim()) return `"${path || '根节点'}" 下有参数名未填`
    if (p.fieldType !== 'Object' && p.fieldType !== 'Array') {
      if (p.valueSource === 'static' && !p.sourceValue.trim()) return `参数 "${fullKey}" 的静态值不能为空`
      if (p.valueSource === 'context' && !p.fieldKey) return `参数 "${fullKey}" 未选择用户数据字段`
      // 'llm' 和 'response' 无需额外校验
    } else {
      const childErr = validateParamNodes(p.children, fullKey)
      if (childErr) return childErr
    }
  }
  return null
}

const FIELD_TYPE_MAP: Record<string, string> = {
  String: 'string', Number: 'number', Boolean: 'boolean', Array: 'array', Object: 'object',
}

/** 遍历参数树，收集所有 valueSource=llm 的参数，构建 paramsSchemaJson */
function buildParamsSchema(nodes: ParamNode[]): string {
  const properties: Record<string, { type: string; description?: string }> = {}
  function walk(list: ParamNode[]) {
    for (const node of list) {
      if (node.valueSource === 'llm' && node.key.trim()) {
        const entry: { type: string; description?: string } = { type: FIELD_TYPE_MAP[node.fieldType] ?? 'string' }
        if (node.sourceValue.trim()) entry.description = node.sourceValue.trim()
        properties[node.key.trim()] = entry
      }
      if ((node.fieldType === 'Object' || node.fieldType === 'Array') && node.children.length) {
        walk(node.children)
      }
    }
  }
  walk(nodes)
  return JSON.stringify({ type: 'object', properties, required: [] })
}

async function saveParams() {
  const err = validateParamNodes(params.value)
  if (err) { message.warning(err); return }
  if (!paramsToolId.value) return
  paramsSaving.value = true
  try {
    const dataParamsJson = params.value.length > 0 ? JSON.stringify(params.value) : undefined
    const paramsSchemaJson = buildParamsSchema(params.value)
    await adminUpdateTool(paramsToolId.value, { dataParamsJson, paramsSchemaJson })
    message.success('参数已保存'); showParamsModal.value = false; await loadAll()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '保存失败')
  } finally { paramsSaving.value = false }
}

/** 递归展开出参树，收集所有叶路径，供入参"出参传递"选择 */
function flattenRespParams(nodes: ResponseParam[], toolName: string, prefix: string, out: ResponseSource[]) {
  for (const p of nodes) {
    const fullPath = prefix ? `${prefix}.${p.key}` : p.key
    out.push({ toolName, paramName: fullPath, paramLabel: p.label || p.key })
    if (p.children?.length) flattenRespParams(p.children, toolName, fullPath, out)
  }
}

function collectResponseSources() {
  const sources: ResponseSource[] = []
  rows.value.forEach(tool => {
    if (tool.responseParamsJson) {
      try {
        const rps = JSON.parse(tool.responseParamsJson) as ResponseParam[]
        flattenRespParams(rps, tool.label || tool.name, '', sources)
      } catch {}
    }
  })
  responseSources.value = sources
}

// ==================== 出参管理 ====================
const showRespModal = ref(false)
const respToolId = ref<number | null>(null)
const respToolName = ref('')
const respSaving = ref(false)
const respParams = ref<ResponseParam[]>([])

function openResp(row: AiToolRow) {
  respToolId.value = row.id
  respToolName.value = row.label || row.name
  try { respParams.value = row.responseParamsJson ? JSON.parse(row.responseParamsJson) : [] } catch { respParams.value = [] }
  showRespModal.value = true
}

async function saveResp() {
  // 简单校验
  for (const p of respParams.value) {
    if (!p.key.trim()) { message.warning('参数名不能为空'); return }
  }
  if (!respToolId.value) return
  respSaving.value = true
  try {
    const responseParamsJson = respParams.value.length > 0 ? JSON.stringify(respParams.value) : undefined
    await adminUpdateTool(respToolId.value, { responseParamsJson })
    message.success('出参已保存'); showRespModal.value = false; await loadAll()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '保存失败')
  } finally { respSaving.value = false }
}

function addRootParam() {
  params.value.push({ key: '', fieldType: 'String', valueSource: 'static', sourceValue: '', fieldKey: '', children: [] })
}

function addRespL1() {
  respParams.value.push({ key: '', fieldType: 'String', label: '', description: '', children: [] })
}

// ==================== 测试工具 ====================
const showTestModal = ref(false)
const testTool = ref<AiToolRow | null>(null)
// context 类型参数的测试输入值，key = fieldKey
const testContextInputs = ref<Record<string, string>>({})
// apikey 类型参数的测试输入值，key = fieldKey
const testApiKeyInputs = ref<Record<string, string>>({})
const testRunning = ref(false)
const testRequestBody = ref('')
const testResult = ref('')
const testElapsed = ref<number | null>(null)

interface FlatContextParam { label: string; fieldKey: string }

/** 递归收集所有 valueSource=context 的参数，供测试运行填值 */
function walkContextParams(nodes: ParamNode[], path: string, out: FlatContextParam[]) {
  for (const p of nodes) {
    const fullPath = path ? `${path}.${p.key}` : p.key
    if (p.valueSource === 'context' && p.fieldKey) {
      out.push({ label: fullPath, fieldKey: p.fieldKey })
    }
    if (p.children?.length) walkContextParams(p.children, fullPath, out)
  }
}

/** 递归收集所有 valueSource=apikey 的参数，供测试运行填值 */
function walkApiKeyParams(nodes: ParamNode[], path: string, out: FlatContextParam[]) {
  for (const p of nodes) {
    const fullPath = path ? `${path}.${p.key}` : p.key
    if (p.valueSource === 'apikey' && p.fieldKey) {
      // 同一 fieldKey 只收集一次
      if (!out.some(o => o.fieldKey === p.fieldKey)) {
        out.push({ label: `${fullPath}（${p.fieldKey}）`, fieldKey: p.fieldKey })
      }
    }
    if (p.children?.length) walkApiKeyParams(p.children, fullPath, out)
  }
}

function collectContextParams(row: AiToolRow): FlatContextParam[] {
  if (!row.dataParamsJson) return []
  try {
    const items = JSON.parse(row.dataParamsJson) as ParamNode[]
    const result: FlatContextParam[] = []
    walkContextParams(items, '', result)
    return result
  } catch { return [] }
}

function collectApiKeyParams(row: AiToolRow): FlatContextParam[] {
  if (!row.dataParamsJson) return []
  try {
    const items = JSON.parse(row.dataParamsJson) as ParamNode[]
    const result: FlatContextParam[] = []
    walkApiKeyParams(items, '', result)
    return result
  } catch { return [] }
}

function openTest(row: AiToolRow) {
  testTool.value = row
  testRequestBody.value = ''
  testResult.value = ''
  testElapsed.value = null
  // 预填默认空值
  const ctxParams = collectContextParams(row)
  const inputs: Record<string, string> = {}
  for (const p of ctxParams) inputs[p.fieldKey] = ''
  testContextInputs.value = inputs

  const apiKeyParams = collectApiKeyParams(row)
  const apiKeyInputs: Record<string, string> = {}
  for (const p of apiKeyParams) apiKeyInputs[p.fieldKey] = ''
  testApiKeyInputs.value = apiKeyInputs

  showTestModal.value = true
}

async function runTest() {
  if (!testTool.value) return
  testRunning.value = true
  testRequestBody.value = ''
  testResult.value = ''
  testElapsed.value = null
  try {
    const extendedParams = Object.keys(testApiKeyInputs.value).length > 0
      ? testApiKeyInputs.value : undefined
    const { result, elapsedMs, requestBody } = await adminTestTool(
      testTool.value.id, testContextInputs.value, undefined, extendedParams)
    // 尝试格式化入参 JSON
    if (requestBody) {
      try { testRequestBody.value = JSON.stringify(JSON.parse(requestBody), null, 2) }
      catch { testRequestBody.value = requestBody }
    }
    // 尝试格式化响应 JSON（后端已根据出参配置过滤）
    try { testResult.value = JSON.stringify(JSON.parse(result), null, 2) }
    catch { testResult.value = result }
    testElapsed.value = elapsedMs
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    testResult.value = `请求失败：${err.response?.data?.message || err.message || '未知错误'}`
  } finally {
    testRunning.value = false
  }
}

// ==================== 删除工具 ====================
function confirmDelete(row: AiToolRow) {
  dialog.warning({
    title: '确认删除', content: `确定删除工具"${row.label}"？所有应用的绑定关系也会一并清除。`,
    positiveText: '删除', negativeText: '取消',
    onPositiveClick: async () => {
      try { await adminDeleteTool(row.id); message.success('已删除'); await loadAll() } catch (e: unknown) {
        const err = e as { response?: { data?: { message?: string } }; message?: string }
        message.error(err.response?.data?.message || err.message || '删除失败')
      }
    },
  })
}

function paramCount(row: AiToolRow): number {
  if (!row.dataParamsJson) return 0
  try { return (JSON.parse(row.dataParamsJson) as unknown[]).length } catch { return 0 }
}

const columns: DataTableColumns<AiToolRow> = [
  {
    title: '接口名称', key: 'name', width: 180, align: 'center', titleAlign: 'center',
    render: (row) => h('div', { style: 'font-size:12px; line-height:1.6' }, [
      h('div', { style: 'font-weight:600; color:#333' }, row.label || row.name),
      h('div', { style: 'font-size:11px; color:#999' }, `(${row.name})`),
    ]),
  },
  {
    title: '业务系统', key: 'url', width: 140, align: 'center', titleAlign: 'center',
    render: (row) => {
      const apiDef = apiDefinitions.value.find(d => d.requestUrl === row.url && d.httpMethod === row.httpMethod)
      return h('span', { style: 'font-size:12px; color:#555' }, apiDef ? apiDef.systemName : (row.url || '-'))
    },
  },
  {
    title: '参数格式', key: 'contentType', width: 80, align: 'center', titleAlign: 'center',
    render: (row) => h(NTag, { size: 'tiny', type: row.contentType === 'application/json' ? 'info' : 'default' }, { default: () => (row.contentType === 'application/json' ? 'JSON' : 'Form') }),
  },
  {
    title: '参数配置', key: 'params', align: 'center', titleAlign: 'center',
    render: (row) => {
      const count = paramCount(row)
      if (count === 0) return h('span', { style: 'color:#bbb; font-size:12px' }, '未配置')
      try {
        const items = JSON.parse(row.dataParamsJson!) as Array<{ key: string; valueType?: string }>
        return h('div', { style: 'font-size:12px; color:#555; line-height:1.6' },
          items.map((p, i) => h('div', { key: i, style: 'margin-bottom:4px' }, [
            h(NTag, { size: 'tiny', type: p.valueType === 'object' ? 'warning' : p.valueType === 'context' ? 'success' : 'default' }, { default: () => p.key }),
          ]))
        )
      } catch { return h(NTag, { size: 'small', type: 'success' }, { default: () => `${count} 个` }) }
    },
  },
  {
    title: '功能描述', key: 'description', align: 'center', titleAlign: 'center', ellipsis: { tooltip: true },
    render: (row) => h('span', { style: 'font-size:12px; color:#555' }, row.description),
  },
  {
    title: '操作', key: 'actions', width: 330, align: 'center', titleAlign: 'center', fixed: 'right',
    render: (row) =>
      h(NSpace, { size: 4, wrap: false, justify: 'center' }, {
        default: () => [
          h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => '编辑' }),
          h(NButton, { size: 'small', type: 'primary', ghost: true, onClick: () => openParams(row) }, { default: () => '入参管理' }),
          h(NButton, { size: 'small', type: 'warning', ghost: true, onClick: () => openResp(row) }, { default: () => '出参管理' }),
          h(NButton, { size: 'small', type: 'info', ghost: true, onClick: () => openTest(row) }, { default: () => '测试运行' }),
          h(NButton, { size: 'small', type: 'error', onClick: () => confirmDelete(row) }, { default: () => '删除' }),
        ],
      }),
  },
]

onMounted(() => { void loadAll() })
</script>

<template>
  <div>
    <NCard :bordered="false" class="card" title="接口管理">
      <template #header-extra>
        <NSpace>
          <NButton @click="openApiModal">系统API配置</NButton>
          <NButton type="primary" @click="openCreate">+ 新建接口</NButton>
        </NSpace>
      </template>
      <NSpin :show="loading">
        <NDataTable :columns="columns" :data="rows" :bordered="false" size="small" />
      </NSpin>
    </NCard>

    <!-- ===== 新建 / 编辑接口 ===== -->
    <NModal v-model:show="showModal" preset="card" :title="editId ? '编辑接口' : '新建接口'" style="width:620px" :mask-closable="false">
      <NForm :model="form" label-placement="left" label-width="110px">
        <NFormItem label="显示名称" required>
          <NInput v-model:value="form.label" placeholder="中文，如 请假余额查询" />
        </NFormItem>
        <NFormItem label="接口名称" required>
          <NInput v-model:value="form.name" placeholder="英文，如 query_leave_balance" />
        </NFormItem>
        <NFormItem label="功能描述" required>
          <NInput v-model:value="form.description" type="textarea" :rows="3" placeholder="LLM 据此决定何时调用此接口" />
        </NFormItem>
        <NFormItem label="业务系统" required>
          <NSelect v-model:value="selectedApiDefId" :options="apiDefOptions" placeholder="选择业务系统（自动填充接口地址）" clearable style="width:100%" @update:value="onApiDefChange" />
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

    <!-- ===== 参数管理 ===== -->
    <NModal v-model:show="showParamsModal" preset="card" :title="`入参管理 · ${paramsToolName}`" style="width:960px" :mask-closable="false">

      <div style="max-height:65vh; overflow-y:auto; padding-right:4px">
        <ParamNodeEditor
          :nodes="params"
          :context-field-options="contextFieldOptions"
          :response-sources="[]"
        />
      </div>

      <template #footer>
        <NSpace justify="space-between">
          <NButton dashed @click="addRootParam">+ 添加参数</NButton>
          <NButton type="primary" :loading="paramsSaving" @click="saveParams">保存参数</NButton>
        </NSpace>
      </template>
    </NModal>

    <!-- ===== 出参管理 ===== -->
    <NModal v-model:show="showRespModal" preset="card" :title="`出参管理 · ${respToolName}`" style="width:800px" :mask-closable="false">

      <div style="max-height:65vh; overflow-y:auto; padding-right:4px">
        <ResponseParamEditor :nodes="respParams" />
      </div>

      <template #footer>
        <NSpace justify="space-between">
          <NButton dashed @click="addRespL1">+ 添加参数</NButton>
          <NButton type="primary" :loading="respSaving" @click="saveResp">保存</NButton>
        </NSpace>
      </template>
    </NModal>

    <!-- ===== 测试工具 ===== -->
    <NModal
      v-model:show="showTestModal"
      preset="card"
      :title="`测试 · ${testTool?.label || testTool?.name || ''}`"
      style="width:680px"
      :mask-closable="false"
    >
      <template v-if="testTool">
        <!-- 工具基本信息 -->
        <div style="font-size:12px; color:#888; margin-bottom:12px; line-height:1.8">
          <span style="margin-right:16px">接口：<NTag size="small">{{ testTool.httpMethod }}</NTag> {{ testTool.url }}</span>
        </div>

        <!-- context 参数输入区 -->
        <template v-if="collectContextParams(testTool).length > 0">
          <NDivider title-placement="left" style="margin:0 0 10px">用户数据参数（测试值）</NDivider>
          <NForm label-placement="left" label-width="160px" size="small">
            <NFormItem
              v-for="p in collectContextParams(testTool)"
              :key="p.fieldKey"
              :label="p.label"
            >
              <NInput
                v-model:value="testContextInputs[p.fieldKey]"
                :placeholder="`${p.fieldKey} 的测试值`"
              />
            </NFormItem>
          </NForm>
        </template>

        <!-- apikey 参数输入区 -->
        <template v-if="collectApiKeyParams(testTool).length > 0">
          <NDivider title-placement="left" style="margin:0 0 10px">APIKey 扩展参数（测试值）</NDivider>
          <NForm label-placement="left" label-width="200px" size="small">
            <NFormItem
              v-for="p in collectApiKeyParams(testTool)"
              :key="p.fieldKey"
              :label="p.label"
            >
              <NInput
                v-model:value="testApiKeyInputs[p.fieldKey]"
                :placeholder="`${p.fieldKey} 的测试值`"
              />
            </NFormItem>
          </NForm>
        </template>

        <div
          v-if="collectContextParams(testTool).length === 0 && collectApiKeyParams(testTool).length === 0"
          style="color:#aaa; font-size:12px; margin-bottom:12px"
        >
          该接口无需用户数据参数，直接点击"发起测试"即可
        </div>

        <!-- 发起测试 -->
        <NButton type="primary" :loading="testRunning" style="width:100%; margin-bottom:12px" @click="runTest">
          {{ testRunning ? '请求中...' : '发起测试' }}
        </NButton>

        <!-- 结果展示 -->
        <template v-if="testResult || testRequestBody || testElapsed !== null">
          <template v-if="testRequestBody">
            <NDivider title-placement="left" style="margin:0 0 8px">实际入参</NDivider>
            <NInput
              :value="testRequestBody"
              type="textarea"
              :rows="5"
              readonly
              style="font-family:monospace; font-size:12px; margin-bottom:12px"
            />
          </template>
          <NDivider title-placement="left" style="margin:0 0 8px">
            响应结果
            <span v-if="testElapsed !== null" style="font-size:11px; color:#aaa; font-weight:normal; margin-left:8px">
              耗时 {{ testElapsed }} ms
            </span>
          </NDivider>
          <NInput
            :value="testResult"
            type="textarea"
            :rows="10"
            readonly
            style="font-family:monospace; font-size:12px"
          />
        </template>
      </template>

      <template #footer>
        <NSpace justify="end">
          <NButton @click="showTestModal = false">关闭</NButton>
        </NSpace>
      </template>
    </NModal>

    <!-- ===== 系统 API 配置 Drawer ===== -->
    <NDrawer v-model:show="showApiDrawer" placement="right" :width="900">
      <NDrawerContent title="系统 API 配置" :native-scrollbar="false" closable>
        <!-- 操作栏 -->
        <div style="display:flex; justify-content:flex-end; margin-bottom:12px">
          <NButton type="primary" @click="openApiCreate">+ 新建接口</NButton>
        </div>

        <!-- 列表 -->
        <NSpin :show="apiLoading">
          <NDataTable :columns="apiColumns" :data="apiDefinitions" :bordered="false" size="small" />
        </NSpin>

        <!-- 新建/编辑表单 Modal -->
        <NModal v-model:show="showApiForm" preset="card" :title="apiEditId ? '编辑接口' : '新建接口'" style="width:680px" :mask-closable="false">
          <NForm :model="apiForm" label-placement="left" label-width="90px" size="small">
            <NFormItem label="系统名称" required>
              <NInput v-model:value="apiForm.systemName" placeholder="如：请假系统" />
            </NFormItem>
            <NFormItem label="请求路径" required>
              <NInput v-model:value="apiForm.requestUrl" placeholder="完整 URL，如 https://api.example.com/leave" />
            </NFormItem>
            <NFormItem label="请求方式" required>
              <NSelect v-model:value="apiForm.httpMethod" :options="httpMethodOptions" style="width:120px" />
            </NFormItem>
            <NFormItem label="参数格式" required>
              <NSelect v-model:value="apiForm.contentType" :options="contentTypeOptions" style="width:100%" />
            </NFormItem>
            <NFormItem label="备注">
              <NInput v-model:value="apiForm.remark" placeholder="可选说明" />
            </NFormItem>
          </NForm>
          <template #footer>
            <NSpace justify="end">
              <NButton size="small" @click="showApiForm = false">取消</NButton>
              <NButton size="small" type="primary" @click="handleApiSave">保存</NButton>
            </NSpace>
          </template>
        </NModal>
      </NDrawerContent>
    </NDrawer>
  </div>
</template>
