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
  adminCreateContextField,
  adminDeleteContextField,
  adminListContextFields,
  adminTestContextField,
  adminUpdateContextField,
  type ContextFieldRow,
} from '@/api/adminContextFields'

const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const rows = ref<ContextFieldRow[]>([])

const fieldTypeOptions = [
  { label: 'String（字符串）', value: 'String' },
  { label: 'Number（数值）', value: 'Number' },
  { label: 'Boolean（布尔值）', value: 'Boolean' },
  { label: 'Array（数组/列表）', value: 'Array' },
  { label: 'Object（对象）', value: 'Object' },
]

// ---- 新建 / 编辑 ----
const showModal = ref(false)
const editId = ref<number | null>(null)
const form = ref({
  fieldKey: '',
  label: '',
  fieldType: 'String',
  description: '',
  enabled: true,
})

// 解析路径分段：每个 segment 是一个路径层级名称，保存时 join('.') 生成 parseExpression
const parseSegments = ref<string[]>([''])

// 预览路径
const previewExpression = computed(() =>
  parseSegments.value.filter((s) => s.trim()).join('.')
)

function addSegment() {
  parseSegments.value.push('')
}
function removeSegment(i: number) {
  if (parseSegments.value.length <= 1) {
    parseSegments.value[0] = ''
  } else {
    parseSegments.value.splice(i, 1)
  }
}

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
  form.value = { fieldKey: '', label: '', fieldType: 'String', description: '', enabled: true }
  parseSegments.value = ['']
  showModal.value = true
}

function openEdit(row: ContextFieldRow) {
  editId.value = row.id
  form.value = {
    fieldKey: row.fieldKey,
    label: row.label,
    fieldType: row.fieldType || 'String',
    description: row.description ?? '',
    enabled: row.enabled,
  }
  // 将 dot-notation 拆成 segments
  const expr = row.parseExpression || ''
  parseSegments.value = expr ? expr.split('.') : ['']
  showModal.value = true
}

async function handleSave() {
  if (!form.value.fieldKey.trim()) { message.warning('请填写字段 Key'); return }
  if (!form.value.label.trim()) { message.warning('请填写显示名'); return }
  if (!previewExpression.value) { message.warning('请至少配置一段解析路径'); return }
  try {
    const payload = {
      fieldKey: form.value.fieldKey.trim(),
      label: form.value.label.trim(),
      fieldType: form.value.fieldType,
      parseExpression: previewExpression.value,
      description: form.value.description?.trim() || undefined,
      enabled: form.value.enabled,
    }
    if (editId.value) {
      await adminUpdateContextField(editId.value, payload)
      message.success('已更新')
    } else {
      await adminCreateContextField(payload)
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
    content: `确定删除用户数据字段"${row.label}"？`,
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

// ---- 测试运行 ----
const showTestModal = ref(false)
const testField = ref<ContextFieldRow | null>(null)
const testRunning = ref(false)
const testFound = ref<boolean | null>(null)
const testValue = ref<string | null>(null)
const testError = ref<string | null>(null)
const testExpression = ref('')

async function openTest(row: ContextFieldRow) {
  testField.value = row
  testFound.value = null
  testValue.value = null
  testError.value = null
  testExpression.value = row.parseExpression || ''
  showTestModal.value = true
}

async function runTest() {
  if (!testField.value) return
  testRunning.value = true
  testFound.value = null
  testValue.value = null
  testError.value = null
  try {
    const res = await adminTestContextField(testField.value.id)
    testFound.value = res.found
    testValue.value = res.value
    testExpression.value = res.expression
    if (res.error) testError.value = res.error
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    testError.value = err.response?.data?.message || err.message || '请求失败'
  } finally {
    testRunning.value = false
  }
}

const columns: DataTableColumns<ContextFieldRow> = [
  {
    title: '字段 Key', key: 'fieldKey', width: 160, align: 'center', titleAlign: 'center',
    render: (row) => h('span', { style: 'font-family:monospace' }, row.fieldKey),
  },
  { title: '显示名', key: 'label', width: 140, align: 'center', titleAlign: 'center' },
  {
    title: '类型', key: 'fieldType', width: 100, align: 'center', titleAlign: 'center',
    render: (row) => h(NTag, { size: 'small', type: 'info' }, { default: () => row.fieldType || 'String' }),
  },
  {
    title: '解析路径', key: 'parseExpression', align: 'center', titleAlign: 'center',
    render: (row) => {
      if (!row.parseExpression) return h('span', { style: 'color:#bbb' }, '-')
      const segs = row.parseExpression.split('.')
      return h('div', { style: 'display:flex; align-items:center; gap:4px; justify-content:center; flex-wrap:wrap' },
        segs.flatMap((s, i) => {
          const tag = h(NTag, { size: 'tiny', type: 'default', style: 'font-family:monospace' }, { default: () => s })
          if (i < segs.length - 1) {
            return [tag, h(NText, { depth: 3, style: 'font-size:11px' }, { default: () => '→' })]
          }
          return [tag]
        })
      )
    },
  },
  { title: '说明', key: 'description', align: 'center', titleAlign: 'center', ellipsis: { tooltip: true } },
  {
    title: '状态', key: 'enabled', width: 80, align: 'center', titleAlign: 'center',
    render: (row) =>
      h(NTag, { size: 'small', type: row.enabled ? 'success' : 'default' }, {
        default: () => (row.enabled ? '启用' : '禁用'),
      }),
  },
  {
    title: '操作', key: 'actions', width: 200, align: 'center', titleAlign: 'center',
    render: (row) =>
      h(NSpace, { size: 4, wrap: false, justify: 'center' }, {
        default: () => [
          h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => '编辑' }),
          h(NButton, { size: 'small', type: 'info', ghost: true, onClick: () => openTest(row) }, { default: () => '测试运行' }),
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
    <NCard :bordered="false" class="card" title="用户数据管理">
      <template #header-extra>
        <NButton type="primary" size="large" @click="openCreate">+ 新建字段</NButton>
      </template>
      <div style="color:#888; font-size:12px; margin-bottom:12px;">
        配置从 DMS 登录响应中提取的字段，登录时自动解析并缓存，可在工具调用时通过引用注入请求参数。
      </div>
      <NSpin :show="loading">
        <NDataTable :columns="columns" :data="rows" :bordered="false" size="small" />
      </NSpin>
    </NCard>

    <!-- ===== 新建 / 编辑 ===== -->
    <NModal
      v-model:show="showModal"
      preset="card"
      :title="editId ? '编辑用户数据字段' : '新建用户数据字段'"
      style="width:560px"
      :mask-closable="false"
    >
      <NForm :model="form" label-placement="left" label-width="90px">
        <NFormItem label="字段 Key" required>
          <NInput
            v-model:value="form.fieldKey"
            placeholder="英文，如 esusMobile"
            :disabled="!!editId"
          />
        </NFormItem>
        <NFormItem label="显示名" required>
          <NInput v-model:value="form.label" placeholder="如：用户手机号" />
        </NFormItem>
        <NFormItem label="字段类型" required>
          <NSelect v-model:value="form.fieldType" :options="fieldTypeOptions" style="width:100%" />
        </NFormItem>

        <!-- 解析路径分段编辑器 -->
        <NFormItem label="解析路径" required>
          <div style="width:100%">
            <div style="display:flex; flex-wrap:wrap; align-items:center; gap:6px; margin-bottom:8px;">
              <template v-for="(_, i) in parseSegments" :key="i">
                <span v-if="i > 0" style="color:#aaa; font-size:13px; font-weight:bold">.</span>
                <div style="display:flex; align-items:center; gap:2px">
                  <NInput
                    v-model:value="parseSegments[i]"
                    :placeholder="`层级${i + 1}`"
                    size="small"
                    style="width:120px"
                  />
                  <NButton size="tiny" text type="error" style="padding:0 2px" @click="removeSegment(i)">×</NButton>
                </div>
              </template>
              <NButton size="tiny" dashed @click="addSegment">+ 添加层级</NButton>
            </div>
            <div style="font-size:12px; color:#888; display:flex; align-items:center; gap:6px">
              <span>路径预览：</span>
              <span
                v-if="previewExpression"
                style="font-family:monospace; background:#f5f5f5; padding:2px 8px; border-radius:4px; color:#333"
              >{{ previewExpression }}</span>
              <span v-else style="color:#bbb">请填写各层级名称</span>
            </div>
          </div>
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

    <!-- ===== 测试运行 ===== -->
    <NModal
      v-model:show="showTestModal"
      preset="card"
      :title="`测试运行 · ${testField?.label || testField?.fieldKey || ''}`"
      style="width:520px"
      :mask-closable="false"
    >
      <template v-if="testField">
        <div style="font-size:12px; color:#888; margin-bottom:12px; line-height:1.8">
          <div>
            字段 Key：<span style="font-family:monospace; color:#333">{{ testField.fieldKey }}</span>
          </div>
          <div style="display:flex; align-items:center; gap:6px; flex-wrap:wrap; margin-top:4px">
            <span>解析路径：</span>
            <template v-if="testField.parseExpression">
              <template v-for="(seg, i) in testField.parseExpression.split('.')" :key="i">
                <span v-if="i > 0" style="color:#aaa">→</span>
                <NTag size="tiny" style="font-family:monospace">{{ seg }}</NTag>
              </template>
            </template>
            <span v-else style="color:#bbb">未配置路径</span>
          </div>
        </div>

        <NButton
          type="primary"
          :loading="testRunning"
          style="width:100%; margin-bottom:14px"
          @click="runTest"
        >
          {{ testRunning ? '解析中...' : '从当前登录数据中提取' }}
        </NButton>

        <!-- 结果 -->
        <template v-if="testFound !== null || testError">
          <NDivider title-placement="left" style="margin:0 0 10px">解析结果</NDivider>

          <!-- 有错误 -->
          <div v-if="testError" style="background:#fff2f0; border:1px solid #ffccc7; border-radius:6px; padding:10px 14px; font-size:13px; color:#cf1322">
            {{ testError }}
          </div>

          <!-- 解析成功 -->
          <template v-else-if="testFound">
            <div style="background:#f6ffed; border:1px solid #b7eb8f; border-radius:6px; padding:10px 14px">
              <div style="font-size:12px; color:#52c41a; margin-bottom:6px; font-weight:600">✓ 解析成功</div>
              <div style="font-size:12px; color:#555; margin-bottom:4px">提取到的值：</div>
              <NInput
                :value="testValue ?? ''"
                type="textarea"
                :rows="4"
                readonly
                style="font-family:monospace; font-size:12px"
              />
            </div>
          </template>

          <!-- 解析到空值 -->
          <div
            v-else
            style="background:#fffbe6; border:1px solid #ffe58f; border-radius:6px; padding:10px 14px; font-size:13px; color:#d46b08"
          >
            ⚠ 路径 <span style="font-family:monospace">{{ testExpression }}</span> 在登录数据中未找到对应值，请检查路径是否正确
          </div>
        </template>
      </template>

      <template #footer>
        <NSpace justify="end">
          <NButton @click="showTestModal = false">关闭</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
