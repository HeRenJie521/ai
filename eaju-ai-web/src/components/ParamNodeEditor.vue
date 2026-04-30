<script setup lang="ts">
import { NButton, NDivider, NInput, NSelect, NTag, NRadioGroup, NRadio, NSpace } from 'naive-ui'

export interface ParamNode {
  key: string
  fieldType: string               // String / Number / Boolean / Object / Array
  valueSource: 'static' | 'context' | 'response' | 'llm' | 'apikey' | 'dynamic'
  sourceValue: string             // 静态值 / LLM参数描述 / 出参传递时的路径（编排时填）/ 动态解析时的LLM描述
  fieldKey: string                // 用户数据字段 key / 动态解析时的上下文字段
  dynamicApikeyField?: string     // 动态解析时的 APIKey 字段名
  arrayMode?: 'object' | 'value'  // Array 类型子项模式：对象子参数 or 纯值列表
  children: ParamNode[]
}

const props = defineProps<{
  nodes: ParamNode[]
  depth?: number
  contextFieldOptions: { label: string; value: string }[]
  responseSources: { label: string; value: string }[]
  addLabel?: string               // 添加按钮文字，默认"添加参数"
  valueArrayMode?: boolean        // true 时子项为纯值（不显示 key 输入）
}>()

const d = props.depth ?? 0

const fieldTypeOpts = [
  { label: 'String',  value: 'String'  },
  { label: 'Number',  value: 'Number'  },
  { label: 'Boolean', value: 'Boolean' },
  { label: 'Object',  value: 'Object'  },
  { label: 'Array',   value: 'Array'   },
]

const valueSourceOpts = [
  { label: '静态值',    value: 'static'   },
  { label: '用户数据',  value: 'context'  },
  { label: 'LLM参数',   value: 'llm'      },
  { label: 'APIKey参数', value: 'apikey'  },
  { label: '动态解析',  value: 'dynamic'  },
  { label: '出参传递',  value: 'response' },
]

function newNode(): ParamNode {
  return { key: '', fieldType: 'String', valueSource: 'static', sourceValue: '', fieldKey: '', children: [] }
}

function addNode() {
  props.nodes.push(newNode())
}

function removeNode(i: number) {
  props.nodes.splice(i, 1)
}

function borderColor(depth: number) {
  return depth === 0 ? '#e8e8e8' : depth === 1 ? '#eef0f4' : '#f3f4f6'
}
function bgColor(depth: number) {
  return depth === 0 ? '#fafafa' : '#fff'
}
function lineColor(depth: number) {
  return depth % 2 === 0 ? '#d9e0f0' : '#e8e8e8'
}
</script>

<template>
  <div>
    <div
      v-for="(node, i) in nodes"
      :key="i"
      :style="`border:1px solid ${borderColor(d)}; border-radius:6px; padding:8px 10px; margin-bottom:8px; background:${bgColor(d)}`"
    >
      <!-- 参数行 -->
      <div style="display:flex; align-items:flex-start; gap:6px; flex-wrap:wrap">
        <span v-if="d > 0" style="font-size:11px; color:#bbb; flex-shrink:0; margin-top:6px">└</span>

        <!-- 非值数组模式才显示 key -->
        <template v-if="!valueArrayMode">
          <span style="font-size:12px; color:#aaa; flex-shrink:0; margin-top:6px">参数名</span>
          <NInput v-model:value="node.key" placeholder="参数名" style="width:130px; flex-shrink:0" size="small" />
        </template>

        <span style="font-size:12px; color:#aaa; flex-shrink:0; margin-top:6px">类型</span>
        <NSelect v-model:value="node.fieldType" :options="fieldTypeOpts" style="width:120px; flex-shrink:0" size="small" />

        <!-- 非容器类型显示来源 -->
        <template v-if="node.fieldType !== 'Object' && node.fieldType !== 'Array'">
          <span style="font-size:12px; color:#aaa; flex-shrink:0; margin-top:6px">来源</span>
          <NSelect v-model:value="node.valueSource" :options="valueSourceOpts" style="width:140px; flex-shrink:0" size="small" />

          <!-- 静态值 -->
          <NInput
            v-if="node.valueSource === 'static'"
            v-model:value="node.sourceValue"
            placeholder="固定值"
            style="flex:1; min-width:120px"
            size="small"
          />
          <!-- 用户数据 -->
          <NSelect
            v-else-if="node.valueSource === 'context'"
            v-model:value="node.fieldKey"
            :options="contextFieldOptions"
            placeholder="选择用户数据字段"
            style="flex:1; min-width:140px"
            size="small"
          />
          <!-- LLM参数 -->
          <NInput
            v-else-if="node.valueSource === 'llm'"
            v-model:value="node.sourceValue"
            placeholder="参数描述，LLM 据此填值，如：查询日期，格式 YYYY-MM-DD"
            style="flex:1; min-width:160px"
            size="small"
          />
          <!-- APIKey参数 -->
          <NInput
            v-else-if="node.valueSource === 'apikey'"
            v-model:value="node.fieldKey"
            placeholder="extendedParameters 中的 key 名称"
            style="flex:1; min-width:140px"
            size="small"
          />
          <!-- 动态解析：三个可选来源配置 -->
          <div
            v-else-if="node.valueSource === 'dynamic'"
            style="flex:1; min-width:240px; display:flex; flex-direction:column; gap:4px; padding:4px 6px; background:#f5f7ff; border-radius:4px; border:1px solid #dde3f8"
          >
            <div style="font-size:11px; color:#888; margin-bottom:2px">
              解析顺序：① LLM 提取 → ② APIKey 参数 → ③ 用户上下文
            </div>
            <div style="display:flex; align-items:center; gap:6px">
              <span style="font-size:11px; color:#999; width:56px; flex-shrink:0">LLM描述</span>
              <NInput
                v-model:value="node.sourceValue"
                placeholder="选填，如：用户查询的日期"
                size="small"
                style="flex:1"
              />
            </div>
            <div style="display:flex; align-items:center; gap:6px">
              <span style="font-size:11px; color:#999; width:56px; flex-shrink:0">APIKey字段</span>
              <NInput
                v-model:value="node.dynamicApikeyField"
                placeholder="选填，extendedParameters 中的 key"
                size="small"
                style="flex:1"
              />
            </div>
            <div style="display:flex; align-items:center; gap:6px">
              <span style="font-size:11px; color:#999; width:56px; flex-shrink:0">用户数据</span>
              <NSelect
                v-model:value="node.fieldKey"
                :options="contextFieldOptions"
                placeholder="选填，用户上下文字段"
                clearable
                size="small"
                style="flex:1"
              />
            </div>
          </div>
          <!-- 出参传递 -->
          <span
            v-else
            style="flex:1; font-size:12px; color:#aaa; font-style:italic; padding-left:4px; margin-top:6px"
          >应用编排时设置</span>
        </template>

        <!-- 容器类型标签 -->
        <NTag v-else size="tiny" style="flex:1; background:transparent; border:none; color:#aaa; margin-top:4px">
          含子参数 ↓
        </NTag>

        <NButton size="small" type="error" text style="flex-shrink:0; margin-top:2px" @click="removeNode(i)">删除</NButton>
      </div>

      <!-- Array 类型：子项模式切换 -->
      <template v-if="node.fieldType === 'Array'">
        <div style="display:flex; align-items:center; gap:8px; margin-top:8px; padding:0 2px">
          <span style="font-size:12px; color:#888; flex-shrink:0">子项模式</span>
          <NRadioGroup
            :value="node.arrayMode ?? 'object'"
            size="small"
            @update:value="(v: string) => { node.arrayMode = v as 'object' | 'value' }"
          >
            <NSpace>
              <NRadio value="object">对象子参数</NRadio>
              <NRadio value="value">值列表（仅写值，不需要 key）</NRadio>
            </NSpace>
          </NRadioGroup>
        </div>
      </template>

      <!-- 容器类型：递归渲染子参数 -->
      <template v-if="node.fieldType === 'Object' || node.fieldType === 'Array'">
        <NDivider style="margin:8px 0" />
        <div :style="`padding-left:12px; border-left:2px solid ${lineColor(d)}`">
          <ParamNodeEditor
            :nodes="node.children"
            :depth="d + 1"
            :context-field-options="contextFieldOptions"
            :response-sources="responseSources"
            :add-label="node.fieldType === 'Array' ? '添加元素' : '添加子参数'"
            :value-array-mode="node.fieldType === 'Array' && (node.arrayMode ?? 'object') === 'value'"
          />
        </div>
      </template>
    </div>

    <!-- 同级添加按钮（只有 depth>0 或节点为空时才内嵌显示；depth=0 由调用方控制） -->
    <NButton v-if="d > 0 || nodes.length === 0" dashed size="small" style="width:100%; margin-top:2px" @click="addNode">
      + {{ addLabel ?? '添加参数' }}
    </NButton>
  </div>
</template>

<style scoped>
/* 让下拉框内容完全显示，不使用省略号 */
:deep(.n-select .n-base-selection-label__input) {
  text-overflow: clip !important;
  overflow: visible !important;
}
:deep(.n-select .n-base-selection-label) {
  text-overflow: clip !important;
  overflow: visible !important;
}
</style>
