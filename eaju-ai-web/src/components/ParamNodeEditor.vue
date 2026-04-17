<script setup lang="ts">
import { NButton, NDivider, NInput, NSelect, NTag } from 'naive-ui'

export interface ParamNode {
  key: string
  fieldType: string               // String / Number / Boolean / Object / Array
  valueSource: 'static' | 'context' | 'response'
  sourceValue: string             // 静态值 / 出参传递时的路径（编排时填）
  fieldKey: string                // 接口上下文字段 key
  children: ParamNode[]
}

const props = defineProps<{
  nodes: ParamNode[]
  depth?: number
  contextFieldOptions: { label: string; value: string }[]
  responseSources: { label: string; value: string }[]
  addLabel?: string               // 添加按钮文字，默认"添加参数"
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
  { label: '接口上下文', value: 'context'  },
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

function addChild(node: ParamNode) {
  node.children.push(newNode())
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
      <div style="display:flex; align-items:center; gap:6px; flex-wrap:wrap">
        <span v-if="d > 0" style="font-size:11px; color:#bbb; flex-shrink:0">└</span>
        <span style="font-size:12px; color:#aaa; flex-shrink:0">参数名</span>
        <NInput v-model:value="node.key" placeholder="参数名" style="width:130px; flex-shrink:0" size="small" />
        <span style="font-size:12px; color:#aaa; flex-shrink:0">类型</span>
        <NSelect v-model:value="node.fieldType" :options="fieldTypeOpts" style="width:95px; flex-shrink:0" size="small" />

        <!-- 非容器类型显示来源 -->
        <template v-if="node.fieldType !== 'Object' && node.fieldType !== 'Array'">
          <span style="font-size:12px; color:#aaa; flex-shrink:0">来源</span>
          <NSelect v-model:value="node.valueSource" :options="valueSourceOpts" style="width:105px; flex-shrink:0" size="small" />
          <!-- 静态值 -->
          <NInput
            v-if="node.valueSource === 'static'"
            v-model:value="node.sourceValue"
            placeholder="固定值"
            style="flex:1; min-width:120px"
            size="small"
          />
          <!-- 接口上下文 -->
          <NSelect
            v-else-if="node.valueSource === 'context'"
            v-model:value="node.fieldKey"
            :options="contextFieldOptions"
            placeholder="选择上下文字段"
            style="flex:1; min-width:140px"
            size="small"
          />
          <!-- 出参传递 -->
          <span
            v-else
            style="flex:1; font-size:12px; color:#aaa; font-style:italic; padding-left:4px"
          >应用编排时设置</span>
        </template>
        <NTag v-else size="tiny" style="flex:1; background:transparent; border:none; color:#aaa">
          含子参数 ↓
        </NTag>

        <NButton size="small" type="error" text style="flex-shrink:0" @click="removeNode(i)">删除</NButton>
      </div>

      <!-- 容器类型：递归渲染子参数 -->
      <template v-if="node.fieldType === 'Object' || node.fieldType === 'Array'">
        <NDivider style="margin:8px 0" />
        <div :style="`padding-left:12px; border-left:2px solid ${lineColor(d)}`">
          <!-- 递归 -->
          <ParamNodeEditor
            :nodes="node.children"
            :depth="d + 1"
            :context-field-options="contextFieldOptions"
            :response-sources="responseSources"
            :add-label="node.fieldType === 'Array' ? '添加元素' : '添加子参数'"
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
