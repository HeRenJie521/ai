<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import type { DataTableColumns } from 'naive-ui'
import {
  NButton,
  NCard,
  NDataTable,
  NDivider,
  NDrawer,
  NDrawerContent,
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
  adminCreateContextField,
  adminDeleteContextField,
  adminListContextFields,
  adminTestContextField,
  adminUpdateContextField,
  type ContextFieldRow,
} from '@/api/adminContextFields'

const message = useMessage()
const dialog = useDialog()

// ==================== 数据 ====================
const loading = ref(false)
const rows = ref<AiToolRow[]>([])
const apiDefinitions = ref<ApiDefinitionRow[]>([])
const contextFields = ref<ContextFieldRow[]>([])

// ==================== 接口上下文配置 Drawer ====================
const showContextDrawer = ref(false)
const showContextForm = ref(false)
const contextEditId = ref<number | null>(null)
const contextForm = ref<ContextFieldRow>({
  id: 0, fieldKey: '', label: '', fieldType: 'String', parseExpression: '', description: '', enabled: true, createdAt: null,
})

// ==================== 参数结构 ====================
interface ChildParam {
  key: string
  valueSource: 'static' | 'context' | 'response'
  sourceValue: string
  fieldKey: string
  fieldType?: string  // 子参数的数据类型（可选）
}
interface ToolParam {
  key: string
  fieldType: string  // 参数数据类型: String/Number/Boolean/Object/Array
  valueSource: 'static' | 'context' | 'response'  // 去掉 'object'
  sourceValue: string
  fieldKey: string
  children: ChildParam[]
}

// ==================== 出参数据 ====================
interface ResponseSource {
  toolName: string
  paramName: string
  paramLabel: string
}

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
const fieldTypeOptions = [
  { label: 'String', value: 'String' }, { label: 'Number', value: 'Number' },
  { label: 'Boolean', value: 'Boolean' }, { label: 'Object', value: 'Object' },
  { label: 'Array', value: 'Array' },
]

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

const contextColumns: DataTableColumns<ContextFieldRow> = [
  {
    title: '字段 Key', key: 'fieldKey', width: 150,
    render: (row) => h(NText, { code: true, style: 'font-size:12px' }, { default: () => row.fieldKey }),
  },
  {
    title: '显示名', key: 'label', width: 120,
    render: (row) => h('span', { style: 'font-size:12px' }, { default: () => row.label }),
  },
  {
    title: '字段类型', key: 'fieldType', width: 100,
    render: (row) => h(NTag, { size: 'small', type: 'info' }, { default: () => row.fieldType || 'String' }),
  },
  {
    title: '解析逻辑', key: 'parseExpression', ellipsis: { tooltip: true },
    render: (row) => h('span', { style: 'font-size:11px; color:#666; font-family:monospace' }, { default: () => row.parseExpression || '-' }),
  },
  {
    title: '状态', key: 'enabled', width: 70,
    render: (row) => h(NTag, { size: 'small', type: row.enabled ? 'success' : 'default' }, { default: () => (row.enabled ? '启用' : '禁用') }),
  },
  {
    title: '操作', key: 'actions', width: 220, fixed: 'right',
    render: (row) => h(NSpace, { size: 4, wrap: false }, {
      default: () => [
        h(NButton, { size: 'small', onClick: () => openContextEdit(row) }, { default: () => '编辑' }),
        h(NButton, { size: 'small', type: 'info', ghost: true, onClick: () => openContextTest(row) }, { default: () => '测试运行' }),
        h(NButton, { size: 'small', type: 'error', onClick: () => handleContextDelete(row) }, { default: () => '删除' }),
      ],
    }),
  },
]

// ==================== 上下文字段测试 ====================
const showCtxTestModal = ref(false)
const ctxTestField = ref<ContextFieldRow | null>(null)
const ctxTestRunning = ref(false)
const ctxTestFound = ref<boolean | null>(null)
const ctxTestValue = ref<string | null>(null)
const ctxTestError = ref<string | null>(null)
const ctxTestExpression = ref('')

function openContextTest(row: ContextFieldRow) {
  ctxTestField.value = row
  ctxTestFound.value = null
  ctxTestValue.value = null
  ctxTestError.value = null
  ctxTestExpression.value = row.parseExpression || ''
  showCtxTestModal.value = true
}

async function runContextTest() {
  if (!ctxTestField.value) return
  ctxTestRunning.value = true
  ctxTestFound.value = null
  ctxTestValue.value = null
  ctxTestError.value = null
  try {
    const res = await adminTestContextField(ctxTestField.value.id)
    ctxTestFound.value = res.found
    ctxTestValue.value = res.value
    ctxTestExpression.value = res.expression
    if (res.error) ctxTestError.value = res.error
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    ctxTestError.value = err.response?.data?.message || err.message || '请求失败'
  } finally {
    ctxTestRunning.value = false
  }
}

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

// ==================== 接口上下文配置方法 ====================
// @ts-ignore - 在模板中使用
function openContextDrawer() {
  showContextDrawer.value = true
  showContextForm.value = false
}

function openContextCreate() {
  contextEditId.value = null
  contextForm.value = { id: 0, fieldKey: '', label: '', fieldType: 'String', parseExpression: '', description: '', enabled: true, createdAt: null }
  showContextForm.value = true
}

function openContextEdit(row: ContextFieldRow) {
  contextEditId.value = row.id
  contextForm.value = { ...row }
  showContextForm.value = true
}

async function handleContextSave() {
  if (!contextForm.value.fieldKey.trim()) { message.warning('请填写字段 Key'); return }
  if (!contextForm.value.label.trim()) { message.warning('请填写显示名'); return }
  try {
    const payload = {
      fieldKey: contextForm.value.fieldKey.trim(),
      label: contextForm.value.label.trim(),
      fieldType: contextForm.value.fieldType || 'String',
      parseExpression: contextForm.value.parseExpression?.trim() || undefined,
      description: contextForm.value.description?.trim() || undefined,
      enabled: contextForm.value.enabled,
    }
    if (contextEditId.value && contextEditId.value > 0) {
      await adminUpdateContextField(contextEditId.value, payload)
      message.success('已更新')
    } else {
      await adminCreateContextField(payload)
      message.success('已创建')
    }
    showContextForm.value = false
    // 重新加载
    const fieldsRes = await adminListContextFields()
    contextFields.value = fieldsRes
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '保存失败')
  }
}

async function handleContextDelete(row: ContextFieldRow) {
  try {
    await adminDeleteContextField(row.id)
    message.success('已删除')
    // 重新加载
    const fieldsRes = await adminListContextFields()
    contextFields.value = fieldsRes
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '删除失败')
  }
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
  name: '', description: '',
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
  form.value = { name: '', description: '', paramsSchemaJson: '{"type":"object","properties":{},"required":[]}', enabled: true, httpMethod: 'POST', url: '', contentType: 'application/json' }
  showModal.value = true
}

function openEdit(row: AiToolRow) {
  editId.value = row.id
  const matched = apiDefinitions.value.find((api) => api.requestUrl === row.url && api.httpMethod === row.httpMethod)
  selectedApiDefId.value = matched ? matched.id : null
  form.value = { name: row.name, description: row.description, paramsSchemaJson: row.paramsSchemaJson, enabled: row.enabled, httpMethod: row.httpMethod, url: row.url, contentType: row.contentType ?? 'application/json' }
  showModal.value = true
}

async function handleSave() {
  if (!form.value.name.trim()) { message.warning('请填写工具名称'); return }
  if (!form.value.description.trim()) { message.warning('请填写功能描述'); return }
  if (!form.value.url.trim()) { message.warning('请选择业务系统'); return }
  try {
    const payload = { name: form.value.name.trim(), description: form.value.description.trim(), httpMethod: form.value.httpMethod, url: form.value.url, contentType: form.value.contentType, paramsSchemaJson: form.value.paramsSchemaJson.trim(), enabled: form.value.enabled }
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
const params = ref<ToolParam[]>([])
const paramsSaving = ref(false)

function openParams(row: AiToolRow) {
  paramsToolId.value = row.id; paramsToolName.value = row.label || row.name
  paramsToolContentType.value = row.contentType ?? 'application/json'
  try {
    const parsed = row.dataParamsJson ? JSON.parse(row.dataParamsJson) : []
    // 兼容旧数据
    params.value = parsed.map((p: any) => ({
      key: p.key,
      fieldType: p.fieldType || (p.paramType === 'array' ? 'Array' : 'String'),
      valueSource: p.valueSource || (p.valueType === 'object' ? 'static' : p.valueType === 'context' ? 'context' : 'static'),
      sourceValue: p.sourceValue || p.value || '',
      fieldKey: p.fieldKey || '',
      children: p.children?.map((c: any) => ({
        key: c.key,
        fieldType: c.fieldType || 'String',
        valueSource: c.valueSource || (c.valueType === 'context' ? 'context' : 'static'),
        sourceValue: c.sourceValue || c.value || '',
        fieldKey: c.fieldKey || '',
      })) || []
    }))
  } catch { params.value = [] }
  collectResponseSources()
  showParamsModal.value = true
}
function addRootParam() { params.value.push({ key: '', fieldType: 'String', valueSource: 'static', sourceValue: '', fieldKey: '', children: [] }) }
function removeRootParam(i: number) { params.value.splice(i, 1) }
function addChildParam(i: number) { if (!params.value[i].children) params.value[i].children = []; params.value[i].children.push({ key: '', valueSource: 'static', sourceValue: '', fieldKey: '' }) }
function removeChildParam(i: number, j: number) { params.value[i].children.splice(j, 1) }
function onRootValueSourceChange(param: ToolParam) { param.children = [] }

async function saveParams() {
  for (const p of params.value) {
    if (!p.key.trim()) { message.warning('参数名不能为空'); return }
    if (p.fieldType === 'Array') {
      // 数组参数需要至少有一个元素
      if (!p.children || p.children.length === 0) { message.warning(`参数 "${p.key}" 是数组类型，至少需要配置一个元素`); return }
      for (const child of p.children) {
        if (!child.key.trim()) { message.warning(`"${p.key}" 的数组元素名不能为空`); return }
        if (child.valueSource === 'static' && !child.sourceValue.trim()) { message.warning(`参数 "${p.key}" 的数组元素静态值不能为空`); return }
        if (child.valueSource === 'context' && !child.fieldKey) { message.warning(`参数 "${p.key}" 的数组元素未选择用户数据字段`); return }
        if (child.valueSource === 'response' && !child.sourceValue.trim()) { message.warning(`参数 "${p.key}" 的数组元素未选择出参字段`); return }
      }
    } else {
      // 单个值参数（String/Number/Boolean）
      if (p.valueSource === 'static' && !p.sourceValue.trim()) { message.warning(`参数 "${p.key}" 的静态值不能为空`); return }
      if (p.valueSource === 'context' && !p.fieldKey) { message.warning(`参数 "${p.key}" 未选择用户数据字段`); return }
      if (p.valueSource === 'response' && !p.sourceValue.trim()) { message.warning(`参数 "${p.key}" 未选择出参字段`); return }
      if (p.fieldType === 'Object') {
        for (const child of p.children) {
          if (!child.key.trim()) { message.warning(`"${p.key}" 的子参数名不能为空`); return }
          if (child.valueSource === 'static' && !child.sourceValue.trim()) { message.warning(`"${p.key}.${child.key}" 静态值不能为空`); return }
          if (child.valueSource === 'context' && !child.fieldKey) { message.warning(`"${p.key}.${child.key}" 未选择用户数据字段`); return }
          if (child.valueSource === 'response' && !child.sourceValue.trim()) { message.warning(`"${p.key}.${child.key}" 未选择出参字段`); return }
        }
      }
    }
  }
  if (!paramsToolId.value) return
  paramsSaving.value = true
  try {
    const dataParamsJson = params.value.length > 0 ? JSON.stringify(params.value) : undefined
    await adminUpdateTool(paramsToolId.value, { dataParamsJson })
    message.success('参数已保存'); showParamsModal.value = false; await loadAll()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '保存失败')
  } finally { paramsSaving.value = false }
}

// 收集所有工具的出参，用于入参的出参传递选项
function collectResponseSources() {
  const sources: ResponseSource[] = []
  rows.value.forEach(tool => {
    if (tool.responseParamsJson) {
      try {
        const params = JSON.parse(tool.responseParamsJson) as ResponseParam[]
        params.forEach(p => {
          sources.push({ toolName: tool.label || tool.name, paramName: p.key, paramLabel: p.label || p.key })
          if (p.children) {
            p.children.forEach(c => {
              sources.push({ toolName: tool.label || tool.name, paramName: `${p.key}.${c.key}`, paramLabel: c.label || c.key })
              if (c.children) {
                c.children.forEach(d => {
                  sources.push({ toolName: tool.label || tool.name, paramName: `${p.key}.${c.key}.${d.key}`, paramLabel: d.label || d.key })
                })
              }
            })
          }
        })
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

interface ResponseParam {
  key: string
  label: string
  fieldType: string
  description: string
  children: ResponseParam[]
}


function newRespParam(): ResponseParam {
  return { key: '', label: '', fieldType: 'String', description: '', children: [] }
}

function openResp(row: AiToolRow) {
  respToolId.value = row.id
  respToolName.value = row.label || row.name
  try { respParams.value = row.responseParamsJson ? JSON.parse(row.responseParamsJson) : [] } catch { respParams.value = [] }
  showRespModal.value = true
}

function addRespL1() { respParams.value.push(newRespParam()) }
function removeRespL1(i: number) { respParams.value.splice(i, 1) }
function addRespL2(i: number) { respParams.value[i].children.push(newRespParam()) }
function removeRespL2(i: number, j: number) { respParams.value[i].children.splice(j, 1) }
function addRespL3(i: number, j: number) { respParams.value[i].children[j].children.push(newRespParam()) }
function removeRespL3(i: number, j: number, k: number) { respParams.value[i].children[j].children.splice(k, 1) }

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

// ==================== 测试工具 ====================
const showTestModal = ref(false)
const testTool = ref<AiToolRow | null>(null)
// context 类型参数的测试输入值，key = fieldKey
const testContextInputs = ref<Record<string, string>>({})
const testRunning = ref(false)
const testResult = ref('')
const testElapsed = ref<number | null>(null)

interface FlatContextParam { label: string; fieldKey: string }

// 从 dataParamsJson 里收集所有 valueType=context 的参数（含子参数）
function collectContextParams(row: AiToolRow): FlatContextParam[] {
  if (!row.dataParamsJson) return []
  try {
    const items = JSON.parse(row.dataParamsJson) as ToolParam[]
    const result: FlatContextParam[] = []
    for (const p of items) {
      if (p.valueSource === 'context') {
        result.push({ label: p.key, fieldKey: p.fieldKey })
      } else if (p.fieldType === 'Object') {
        for (const c of p.children) {
          if (c.valueSource === 'context') {
            result.push({ label: `${p.key}.${c.key}`, fieldKey: c.fieldKey })
          }
        }
      }
    }
    return result
  } catch { return [] }
}

function openTest(row: AiToolRow) {
  testTool.value = row
  testResult.value = ''
  testElapsed.value = null
  // 预填默认空值
  const ctxParams = collectContextParams(row)
  const inputs: Record<string, string> = {}
  for (const p of ctxParams) inputs[p.fieldKey] = ''
  testContextInputs.value = inputs
  showTestModal.value = true
}

async function runTest() {
  if (!testTool.value) return
  testRunning.value = true
  testResult.value = ''
  testElapsed.value = null
  try {
    const { result, elapsedMs } = await adminTestTool(testTool.value.id, testContextInputs.value)
    // 尝试格式化 JSON
    try {
      testResult.value = JSON.stringify(JSON.parse(result), null, 2)
    } catch {
      testResult.value = result
    }
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
    title: '工具名称', key: 'name', width: 180, align: 'center', titleAlign: 'center',
    render: (row) => h(NText, { code: true, style: 'font-size:12px' }, { default: () => row.name }),
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
          <NButton @click="openContextDrawer">接口上下文配置</NButton>
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
        <NFormItem label="接口名称" required>
          <NInput v-model:value="form.name" placeholder="英文，如 query_leave_balance" :disabled="!!editId" />
        </NFormItem>
        <NFormItem label="功能描述" required>
          <NInput v-model:value="form.description" type="textarea" :rows="3" placeholder="LLM 据此决定何时调用此接口" />
        </NFormItem>
        <NFormItem label="业务系统" required>
          <NSelect v-model:value="selectedApiDefId" :options="apiDefOptions" placeholder="选择业务系统（自动填充接口地址）" clearable style="width:100%" @update:value="onApiDefChange" />
        </NFormItem>
        <NFormItem label="LLM 参数 Schema">
          <NInput v-model:value="form.paramsSchemaJson" type="textarea" :rows="3" placeholder='{"type":"object","properties":{},"required":[]}' />
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

      <div v-for="(param, i) in params" :key="i" style="border:1px solid #e8e8e8; border-radius:6px; padding:10px 12px; margin-bottom:10px; background:#fafafa">
        <div style="display:flex; align-items:center; gap:8px; justify-content:space-between; flex-wrap:nowrap">
          <div style="display:flex; align-items:center; gap:8px; flex:1; min-width:0">
            <span style="font-size:12px; color:#aaa; flex-shrink:0">参数名</span>
            <NInput v-model:value="param.key" placeholder="如 methodName" style="width:140px; flex-shrink:0" size="small" />
            <span style="font-size:12px; color:#aaa; flex-shrink:0">数据类型</span>
            <NSelect
              v-model:value="param.fieldType"
              :options="[
                { label: 'String', value: 'String' },
                { label: 'Number', value: 'Number' },
                { label: 'Boolean', value: 'Boolean' },
                { label: 'Object', value: 'Object' },
                { label: 'Array', value: 'Array' }
              ]"
              style="width:100px; flex-shrink:0"
              size="small"
            />
            <!-- 只有非Object/Array类型才显示参数来源 -->
            <template v-if="param.fieldType !== 'Object' && param.fieldType !== 'Array'">
              <span style="font-size:12px; color:#aaa; flex-shrink:0">参数来源</span>
              <NSelect
                v-model:value="param.valueSource"
                :options="[
                  { label: '静态值', value: 'static' },
                  { label: '用户数据', value: 'context' },
                  { label: '出参传递', value: 'response' }
                ]"
                style="width:100px; flex-shrink:0"
                size="small"
                @update:value="onRootValueSourceChange(param)"
              />
              <!-- 静态值 -->
              <NInput v-if="param.valueSource === 'static'" v-model:value="param.sourceValue" placeholder="固定值" style="flex:1; min-width:160px" size="small" />
              <!-- 用户数据 -->
              <NSelect v-else-if="param.valueSource === 'context'" v-model:value="param.fieldKey" :options="contextFieldOptions" placeholder="选择用户数据字段" style="flex:1; min-width:160px" size="small" />
              <!-- 出参传递 -->
              <NSelect v-else-if="param.valueSource === 'response'" v-model:value="param.sourceValue" :options="responseSources.map(r => ({ label: r.toolName + ' → ' + r.paramLabel, value: r.paramName }))" placeholder="选择出参字段" style="flex:1; min-width:160px" size="small" />
            </template>
            <span v-else style="flex:1; color:#aaa; font-size:12px">子参数配置见下方</span>
          </div>
          <NButton size="small" type="error" text @click="removeRootParam(i)">删除</NButton>
        </div>
        <template v-if="param.fieldType === 'Array'">
          <NDivider style="margin:8px 0" />
          <div style="padding-left:16px">
            <!-- 数组元素列表 -->
            <div v-if="param.children && param.children.length > 0" style="margin-bottom:8px">
              <div style="font-size:12px; color:#aaa; margin-bottom:6px; font-weight:600">数组元素列表</div>
              <div v-for="(child, j) in param.children" :key="j" style="display:flex; align-items:center; gap:8px; margin-bottom:8px; flex-wrap:nowrap; padding:8px; background:#fff; border:1px solid #e8e8e8; border-radius:4px">
                <span style="font-size:12px; color:#aaa; flex-shrink:0">第 {{ j + 1 }} 个元素</span>
                <span style="font-size:12px; color:#aaa; flex-shrink:0">数据类型</span>
                <NSelect
                  v-model:value="child.fieldType"
                  :options="[
                    { label: 'String', value: 'String' },
                    { label: 'Number', value: 'Number' },
                    { label: 'Boolean', value: 'Boolean' },
                    { label: 'Object', value: 'Object' },
                    { label: 'Array', value: 'Array' }
                  ]"
                  style="width:100px; flex-shrink:0"
                  size="small"
                />
                <span style="font-size:12px; color:#aaa; flex-shrink:0">参数来源</span>
                <NSelect
                  v-model:value="child.valueSource"
                  :options="[
                    { label: '静态值', value: 'static' },
                    { label: '用户数据', value: 'context' },
                    { label: '出参传递', value: 'response' }
                  ]"
                  style="width:100px; flex-shrink:0"
                  size="small"
                />
                <!-- 静态值 -->
                <NInput v-if="child.valueSource === 'static'" v-model:value="child.sourceValue" placeholder="固定值" style="flex:1; min-width:140px" size="small" />
                <!-- 用户数据 -->
                <NSelect v-else-if="child.valueSource === 'context'" v-model:value="child.fieldKey" :options="contextFieldOptions" placeholder="选择用户数据字段" style="flex:1; min-width:140px" size="small" />
                <!-- 出参传递 -->
                <NSelect v-else v-model:value="child.sourceValue" :options="responseSources.map(r => ({ label: r.toolName + ' → ' + r.paramLabel, value: r.paramName }))" placeholder="选择出参字段" style="flex:1; min-width:140px" size="small" />
                <NButton size="small" type="error" text @click="removeChildParam(i, j)">删除</NButton>
              </div>
            </div>
            <NButton size="tiny" dashed @click="addChildParam(i)">+ 添加元素</NButton>
          </div>
        </template>
        <template v-if="param.fieldType === 'Object'">
          <NDivider style="margin:8px 0" />
          <div style="padding-left:16px">
            <div v-for="(child, j) in param.children" :key="j" style="display:flex; align-items:center; gap:8px; margin-bottom:6px; flex-wrap:nowrap">
              <span style="font-size:12px; color:#aaa; flex-shrink:0">└ 子参数名</span>
              <NInput v-model:value="child.key" placeholder="如 userId" style="width:120px; flex-shrink:0" size="small" />
              <span style="font-size:12px; color:#aaa; flex-shrink:0">数据类型</span>
              <NSelect
                v-model:value="child.fieldType"
                :options="[
                  { label: 'String', value: 'String' },
                  { label: 'Number', value: 'Number' },
                  { label: 'Boolean', value: 'Boolean' },
                  { label: 'Object', value: 'Object' },
                  { label: 'Array', value: 'Array' }
                ]"
                style="width:100px; flex-shrink:0"
                size="small"
              />
              <span style="font-size:12px; color:#aaa; flex-shrink:0">参数来源</span>
              <NSelect
                v-model:value="child.valueSource"
                :options="[
                  { label: '静态值', value: 'static' },
                  { label: '用户数据', value: 'context' },
                  { label: '出参传递', value: 'response' }
                ]"
                style="width:100px; flex-shrink:0"
                size="small"
              />
              <!-- 静态值 -->
              <NInput v-if="child.valueSource === 'static'" v-model:value="child.sourceValue" placeholder="固定值" style="flex:1; min-width:140px" size="small" />
              <!-- 用户数据 -->
              <NSelect v-else-if="child.valueSource === 'context'" v-model:value="child.fieldKey" :options="contextFieldOptions" placeholder="选择用户数据字段" style="flex:1; min-width:140px" size="small" />
              <!-- 出参传递 -->
              <NSelect v-else v-model:value="child.sourceValue" :options="responseSources.map(r => ({ label: r.toolName + ' → ' + r.paramLabel, value: r.paramName }))" placeholder="选择出参字段" style="flex:1; min-width:140px" size="small" />
              <NButton size="small" type="error" text @click="removeChildParam(i, j)">删除</NButton>
            </div>
            <NButton size="tiny" dashed @click="addChildParam(i)">+ 添加子参数</NButton>
          </div>
        </template>
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

      <!-- 参数行公共样式 -->
      <template v-for="(p1, i) in respParams" :key="i">
        <!-- 一级 -->
        <div style="border:1px solid #e0e0e0; border-radius:6px; padding:8px 10px; margin-bottom:8px; background:#fafafa">
          <div style="display:flex; align-items:center; gap:6px; flex-wrap:wrap">
            <NInput v-model:value="p1.key" placeholder="参数名" style="width:130px; flex-shrink:0" size="small" />
            <NSelect v-model:value="p1.fieldType" :options="fieldTypeOptions" style="width:100px; flex-shrink:0" size="small" />
            <NInput v-model:value="p1.label" placeholder="参数描述" style="width:160px; flex-shrink:0" size="small" />
            <NInput v-model:value="p1.description" placeholder="补充说明（可选）" style="flex:1; min-width:120px" size="small" />
            <NButton size="small" type="error" text @click="removeRespL1(i)">删除</NButton>
          </div>

          <!-- 二级（Object/Array 才显示） -->
          <template v-if="p1.fieldType === 'Object' || p1.fieldType === 'Array'">
            <div style="margin-top:8px; padding-left:16px; border-left:2px solid #e8e8e8">
              <div v-for="(p2, j) in p1.children" :key="j" style="margin-bottom:6px">
                <div style="display:flex; align-items:center; gap:6px; flex-wrap:wrap">
                  <span style="font-size:11px; color:#bbb; flex-shrink:0">└</span>
                  <NInput v-model:value="p2.key" placeholder="子参数名" style="width:120px; flex-shrink:0" size="small" />
                  <NSelect v-model:value="p2.fieldType" :options="fieldTypeOptions" style="width:100px; flex-shrink:0" size="small" />
                  <NInput v-model:value="p2.label" placeholder="参数描述" style="width:150px; flex-shrink:0" size="small" />
                  <NInput v-model:value="p2.description" placeholder="补充说明" style="flex:1; min-width:100px" size="small" />
                  <NButton size="small" type="error" text @click="removeRespL2(i, j)">删除</NButton>
                </div>

                <!-- 三级（Object/Array 才显示） -->
                <template v-if="p2.fieldType === 'Object' || p2.fieldType === 'Array'">
                  <div style="margin-top:6px; padding-left:20px; border-left:2px solid #f0f0f0">
                    <div v-for="(p3, k) in p2.children" :key="k" style="display:flex; align-items:center; gap:6px; flex-wrap:wrap; margin-bottom:4px">
                      <span style="font-size:11px; color:#ccc; flex-shrink:0">└</span>
                      <NInput v-model:value="p3.key" placeholder="三级参数名" style="width:110px; flex-shrink:0" size="small" />
                      <NSelect v-model:value="p3.fieldType" :options="fieldTypeOptions" style="width:90px; flex-shrink:0" size="small" />
                      <NInput v-model:value="p3.label" placeholder="参数描述" style="width:140px; flex-shrink:0" size="small" />
                      <NInput v-model:value="p3.description" placeholder="补充说明" style="flex:1; min-width:90px" size="small" />
                      <NButton size="small" type="error" text @click="removeRespL3(i, j, k)">删除</NButton>
                    </div>
                    <NButton size="tiny" dashed @click="addRespL3(i, j)">+ 添加三级参数</NButton>
                  </div>
                </template>
              </div>
              <NButton size="tiny" dashed @click="addRespL2(i)">+ 添加子参数</NButton>
            </div>
          </template>
        </div>
      </template>

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
        <div v-else style="color:#aaa; font-size:12px; margin-bottom:12px">
          该接口无需用户数据参数，直接点击"发起测试"即可
        </div>

        <!-- 发起测试 -->
        <NButton type="primary" :loading="testRunning" style="width:100%; margin-bottom:12px" @click="runTest">
          {{ testRunning ? '请求中...' : '发起测试' }}
        </NButton>

        <!-- 结果展示 -->
        <template v-if="testResult || testElapsed !== null">
          <NDivider title-placement="left" style="margin:0 0 8px">
            响应结果
            <span v-if="testElapsed !== null" style="font-size:11px; color:#aaa; font-weight:normal; margin-left:8px">
              耗时 {{ testElapsed }} ms
            </span>
          </NDivider>
          <NInput
            :value="testResult"
            type="textarea"
            :rows="12"
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

    <!-- ===== 接口上下文配置 Drawer ===== -->
    <NDrawer v-model:show="showContextDrawer" placement="right" :width="900">
      <NDrawerContent title="接口上下文配置" :native-scrollbar="false" closable>
        <!-- 操作栏 -->
        <div style="display:flex; justify-content:flex-end; margin-bottom:12px">
          <NButton type="primary" @click="openContextCreate">+ 新建上下文字段</NButton>
        </div>

        <!-- 列表 -->
        <NDataTable :columns="contextColumns" :data="contextFields" :bordered="false" size="small" />

      <!-- 新建/编辑表单 Modal -->
      <NModal v-model:show="showContextForm" preset="card" :title="contextEditId ? '编辑上下文字段' : '新建上下文字段'" style="width:680px" :mask-closable="false">
        <NForm :model="contextForm" label-placement="left" label-width="110px">
          <NFormItem label="字段 Key" required>
            <NInput v-model:value="contextForm.fieldKey" placeholder="如：esusMobile" :disabled="!!contextEditId && contextEditId > 0" />
          </NFormItem>
          <NFormItem label="显示名" required>
            <NInput v-model:value="contextForm.label" placeholder="如：用户手机号" />
          </NFormItem>
          <NFormItem label="字段类型" required>
            <NSelect v-model:value="contextForm.fieldType" :options="fieldTypeOptions" style="width:200px" />
          </NFormItem>
          <NFormItem label="解析逻辑">
            <NInput v-model:value="contextForm.parseExpression" type="textarea" :rows="3" placeholder='如：JSON.parseObject("").getJSONObject("data").getString("esusMobile")' />
          </NFormItem>
          <NFormItem label="说明">
            <NInput v-model:value="contextForm.description" type="textarea" :rows="3" placeholder="如：用户手机号，系统唯一" />
          </NFormItem>
          <NFormItem label="状态">
            <NSwitch v-model:value="contextForm.enabled" />
          </NFormItem>
        </NForm>
        <template #footer>
          <NSpace justify="end">
            <NButton @click="showContextForm = false">取消</NButton>
            <NButton type="primary" @click="handleContextSave">保存</NButton>
          </NSpace>
        </template>
      </NModal>

      <!-- 上下文字段测试 Modal -->
      <NModal v-model:show="showCtxTestModal" preset="card" :title="`测试运行 · ${ctxTestField?.label || ctxTestField?.fieldKey || ''}`" style="width:520px" :mask-closable="false">
        <template v-if="ctxTestField">
          <div style="font-size:12px; color:#888; margin-bottom:12px; line-height:1.8">
            <div>字段 Key：<span style="font-family:monospace; color:#333">{{ ctxTestField.fieldKey }}</span></div>
            <div style="display:flex; align-items:center; gap:6px; flex-wrap:wrap; margin-top:4px">
              <span>解析路径：</span>
              <template v-if="ctxTestField.parseExpression">
                <template v-for="(seg, i) in ctxTestField.parseExpression.split('.')" :key="i">
                  <span v-if="i > 0" style="color:#aaa">→</span>
                  <NTag size="tiny" style="font-family:monospace">{{ seg }}</NTag>
                </template>
              </template>
              <span v-else style="color:#bbb">未配置路径</span>
            </div>
          </div>
          <NButton type="primary" :loading="ctxTestRunning" style="width:100%; margin-bottom:14px" @click="runContextTest">
            {{ ctxTestRunning ? '解析中...' : '从当前登录数据中提取' }}
          </NButton>
          <template v-if="ctxTestFound !== null || ctxTestError">
            <NDivider title-placement="left" style="margin:0 0 10px">解析结果</NDivider>
            <div v-if="ctxTestError" style="background:#fff2f0; border:1px solid #ffccc7; border-radius:6px; padding:10px 14px; font-size:13px; color:#cf1322">
              {{ ctxTestError }}
            </div>
            <template v-else-if="ctxTestFound">
              <div style="background:#f6ffed; border:1px solid #b7eb8f; border-radius:6px; padding:10px 14px">
                <div style="font-size:12px; color:#52c41a; margin-bottom:6px; font-weight:600">✓ 解析成功</div>
                <div style="font-size:12px; color:#555; margin-bottom:4px">提取到的值：</div>
                <NInput :value="ctxTestValue ?? ''" type="textarea" :rows="4" readonly style="font-family:monospace; font-size:12px" />
              </div>
            </template>
            <div v-else style="background:#fffbe6; border:1px solid #ffe58f; border-radius:6px; padding:10px 14px; font-size:13px; color:#d46b08">
              ⚠ 路径 <span style="font-family:monospace">{{ ctxTestExpression }}</span> 在登录数据中未找到对应值，请检查路径是否正确
            </div>
          </template>
        </template>
        <template #footer>
          <NSpace justify="end">
            <NButton @click="showCtxTestModal = false">关闭</NButton>
          </NSpace>
        </template>
      </NModal>
      </NDrawerContent>
    </NDrawer>

    <!-- ===== 系统API配置 Drawer ===== -->
    <NDrawer v-model:show="showApiDrawer" placement="right" :width="900">
      <NDrawerContent title="系统API配置" :native-scrollbar="false" closable>
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
