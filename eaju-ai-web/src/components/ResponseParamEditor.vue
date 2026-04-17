<script setup lang="ts">
import { NButton, NDivider, NInput, NSelect } from 'naive-ui'

export interface ResponseParam {
  key: string
  fieldType: string               // String / Number / Boolean / Object / Array
  label: string
  description: string
  children: ResponseParam[]
}

const props = defineProps<{
  nodes: ResponseParam[]
  depth?: number
}>()

const d = props.depth ?? 0

const fieldTypeOpts = [
  { label: 'String',  value: 'String'  },
  { label: 'Number',  value: 'Number'  },
  { label: 'Boolean', value: 'Boolean' },
  { label: 'Object',  value: 'Object'  },
  { label: 'Array',   value: 'Array'   },
]

function newNode(): ResponseParam {
  return { key: '', fieldType: 'String', label: '', description: '', children: [] }
}

function addNode() {
  props.nodes.push(newNode())
}

function removeNode(i: number) {
  props.nodes.splice(i, 1)
}

function addChild(node: ResponseParam) {
  node.children.push(newNode())
}

function borderColor(depth: number) {
  return depth === 0 ? '#e0e0e0' : depth === 1 ? '#eceef2' : '#f3f4f6'
}
function bgColor(depth: number) {
  return depth === 0 ? '#fafafa' : '#fff'
}
function lineColor(depth: number) {
  return depth % 2 === 0 ? '#d0d8eb' : '#e8e8e8'
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
        <NInput v-model:value="node.key" placeholder="参数名（key）" style="width:120px; flex-shrink:0" size="small" />
        <NSelect v-model:value="node.fieldType" :options="fieldTypeOpts" style="width:120px; flex-shrink:0" size="small" />
        <NInput v-model:value="node.label" placeholder="字段描述（必填）" style="width:160px; flex-shrink:0" size="small" />
        <NInput v-model:value="node.description" placeholder="补充说明（可选）" style="flex:1; min-width:120px" size="small" />
        <NButton size="small" type="error" text style="flex-shrink:0" @click="removeNode(i)">删除</NButton>
      </div>

      <!-- 容器类型：递归渲染子字段 -->
      <template v-if="node.fieldType === 'Object' || node.fieldType === 'Array'">
        <NDivider style="margin:8px 0" />
        <div :style="`padding-left:12px; border-left:2px solid ${lineColor(d)}`">
          <!-- 递归 -->
          <ResponseParamEditor :nodes="node.children" :depth="d + 1" />
        </div>
      </template>
    </div>

    <!-- depth>0 或列表为空时内嵌显示添加按钮 -->
    <NButton v-if="d > 0 || nodes.length === 0" dashed size="small" style="width:100%; margin-top:2px" @click="addNode">
      + 添加字段
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
