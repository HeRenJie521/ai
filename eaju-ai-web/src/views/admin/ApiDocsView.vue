<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NButton,
  NCard,
  NCollapse,
  NCollapseItem,
  NSpace,
  NTag,
  NText,
  useMessage,
} from 'naive-ui'

const router = useRouter()
const message = useMessage()

// ---- /api/chat 数据 ----
const blockingExample = `{
  "provider": "DEEPSEEK",
  "mode": "deepseek-chat",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "messages": [
    { "role": "user", "content": "你好，介绍一下自己。" }
  ],
  "stream": false,
  "temperature": 0.7,
  "maxTokens": 1024
}`

const blockingResponse = `{
  "provider": "DEEPSEEK",
  "model": "deepseek-chat",
  "id": "chatcmpl-abc123",
  "content": "你好！我是 DeepSeek，一个由深度求索公司开发的 AI 助手……",
  "reasoningContent": null,
  "finishReason": "stop",
  "usage": {
    "prompt_tokens": 12,
    "completion_tokens": 38,
    "total_tokens": 50
  },
  "raw": { ... }
}`

const streamingExample = `{
  "provider": "DEEPSEEK",
  "mode": "deepseek-chat",
  "messages": [
    { "role": "user", "content": "用三句话介绍 Spring Boot。" }
  ],
  "stream": true
}`

const streamingResponse = `event: chunk
data: {"content":"Spring Boot ","reasoning":null}

event: chunk
data: {"content":"是一个基于 Spring 的","reasoning":null}

event: chunk
data: {"content":"快速开发框架……","reasoning":null}

event: done
data: [DONE]`

const curlBlocking = `curl -X POST http://your-host/api/chat \\
  -H "Content-Type: application/json" \\
  -H "X-API-Key: eaju_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" \\
  -d '{
    "provider": "DEEPSEEK",
    "mode": "deepseek-chat",
    "messages": [{ "role": "user", "content": "你好" }],
    "stream": false
  }'`

const curlStreaming = `curl -N -X POST http://your-host/api/chat \\
  -H "Content-Type: application/json" \\
  -H "X-API-Key: eaju_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" \\
  -d '{
    "provider": "DEEPSEEK",
    "mode": "deepseek-chat",
    "messages": [{ "role": "user", "content": "你好" }],
    "stream": true
  }'`

const reqParams = [
  { field: 'provider', type: 'string', required: true, desc: '模型提供商代码，与后台 llm_provider_config.code 一致，大小写不敏感。可选值：DEEPSEEK、QWEN、KIMI、QIANFAN、GEMINI 等。' },
  { field: 'messages', type: 'Message[]', required: true, desc: '对话消息列表，至少包含一条。多轮对话时可只传最新一轮，历史记录由 sessionId 从服务端自动拼接。' },
  { field: 'mode', type: 'string', required: false, desc: '逻辑模型名，对应后台配置的 modes_json 中的 key。不传时使用该提供商的默认模型。与 model 二选一，model 优先。' },
  { field: 'model', type: 'string', required: false, desc: '直接指定上游模型 ID，绕过 mode 映射，原样透传给上游接口。' },
  { field: 'sessionId', type: 'string', required: false, desc: '会话 ID。有值时从 Redis 读取该会话历史并拼接在本轮消息之前，阻塞模式成功后将本轮写回 Redis 并落库。不传时服务端自动生成 UUID（X-API-Key 调用时）。' },
  { field: 'stream', type: 'boolean', required: false, desc: 'false 或不传：阻塞式，响应为 application/json；true：流式，响应为 text/event-stream（SSE）。' },
  { field: 'temperature', type: 'number', required: false, desc: '采样温度，范围 0~2，值越高输出越随机。不传时使用后台推理默认值。' },
  { field: 'maxTokens', type: 'integer', required: false, desc: '最大输出 token 数（对应 OpenAI 的 max_tokens）。不传时使用后台推理默认值。' },
  { field: 'topP', type: 'number', required: false, desc: 'Top-P 核采样，范围 0~1。与 temperature 一般二选一使用。' },
  { field: 'topK', type: 'integer', required: false, desc: 'Top-K 采样（部分厂商支持，如通义、Gemini）。' },
  { field: 'frequencyPenalty', type: 'number', required: false, desc: '频率惩罚，范围 -2~2，减少已出现词的重复频率。' },
  { field: 'presencePenalty', type: 'number', required: false, desc: '存在惩罚，范围 -2~2，鼓励模型涉及新话题。' },
  { field: 'responseFormat', type: 'string', required: false, desc: '回复格式。TEXT（默认）或 JSON_OBJECT（要求模型输出合法 JSON）。' },
  { field: 'thinkingMode', type: 'boolean', required: false, desc: '思考模式，当前对 DeepSeek 生效。开启后响应中包含 reasoningContent 字段（思维链）。' },
]

const messageParams = [
  { field: 'role', type: 'string', required: true, desc: '消息角色：user（用户）/ assistant（模型）/ system（系统提示）。' },
  { field: 'content', type: 'string', required: true, desc: '消息内容文本。' },
]

const respFields = [
  { field: 'provider', type: 'string', desc: '与请求 provider 一致，使用库表中的规范 code。' },
  { field: 'model', type: 'string', desc: '实际调用的上游模型 ID。' },
  { field: 'id', type: 'string', desc: '上游返回的对话 ID。' },
  { field: 'content', type: 'string', desc: 'AI 回复的正文内容。' },
  { field: 'reasoningContent', type: 'string', desc: '思维链内容（DeepSeek 思考模式等场景下返回，否则为 null）。' },
  { field: 'finishReason', type: 'string', desc: '上游返回的停止原因，常见值：stop / length / content_filter。' },
  { field: 'usage', type: 'object', desc: 'Token 用量统计，包含 prompt_tokens、completion_tokens、total_tokens。' },
  { field: 'raw', type: 'object', desc: '上游原始响应体，供调试或扩展使用。' },
]

const sseFields = [
  { event: 'chunk', data: '{ "content": "…", "reasoning": "…" }', desc: '每次推送一个增量文本片段。content 为正文 delta，reasoning 为思维链 delta（无则为 null）。' },
  { event: 'done', data: '[DONE]', desc: '流结束标志，收到后客户端应关闭连接。' },
]

const openRows = ref(['params', 'message-type', 'resp-block', 'resp-stream', 'examples'])

// ---- /api/file/upload 数据 ----
const curlUpload = `curl -X POST http://your-host/api/file/upload \\
  -H "X-API-Key: eaju_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" \\
  -F "file=@/path/to/your/image.png"`

const uploadResponse = `{
  "url": "https://cdn.example.com/uploads/abc123.png"
}`

const uploadRespFields = [
  { field: 'url', type: 'string', desc: '文件上传成功后返回的公网可访问 URL，可直接传入 /api/chat 的 fileUrls 字段供多模态模型使用。' },
]

const uploadErrors = [
  { status: '400', desc: '文件为空，或文件大小超过 5 MB 限制。' },
  { status: '401', desc: '未提供鉴权信息或 API Key 无效。' },
  { status: '500', desc: '上传服务暂不可用（下游代理转发失败）。' },
]

const openUploadRows = ref(['upload-params', 'upload-resp', 'upload-errors', 'upload-example'])

// ---- 复制 ----
async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}

// ---- 右侧导航 ----
const navItems = [
  {
    id: 'anchor-chat',
    label: 'POST /api/chat',
    tag: 'POST',
    children: [
      { id: 'anchor-chat-params', label: '请求体参数' },
      { id: 'anchor-chat-message', label: 'Message 结构' },
      { id: 'anchor-chat-resp-block', label: '响应 · 阻塞' },
      { id: 'anchor-chat-resp-stream', label: '响应 · 流式' },
      { id: 'anchor-chat-examples', label: '调用示例' },
    ],
  },
  {
    id: 'anchor-upload',
    label: 'POST /api/file/upload',
    tag: 'POST',
    children: [
      { id: 'anchor-upload-params', label: '请求参数' },
      { id: 'anchor-upload-resp', label: '响应' },
      { id: 'anchor-upload-errors', label: '错误码说明' },
      { id: 'anchor-upload-example', label: '调用示例' },
    ],
  },
]

const activeId = ref('anchor-chat')

function scrollTo(id: string) {
  const el = document.getElementById(id)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

let observer: IntersectionObserver | null = null

onMounted(() => {
  const allIds = navItems.flatMap((g) => [g.id, ...g.children.map((c) => c.id)])
  const targets = allIds.map((id) => document.getElementById(id)).filter(Boolean) as HTMLElement[]

  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          activeId.value = entry.target.id
          break
        }
      }
    },
    { rootMargin: '-20% 0px -70% 0px', threshold: 0 },
  )

  targets.forEach((el) => observer!.observe(el))
})

onUnmounted(() => {
  observer?.disconnect()
})
</script>

<template>
  <div class="page">
    <header class="bar">
      <n-text strong class="title">API 文档</n-text>
      <n-space :size="12" wrap>
        <n-button @click="router.push('/settings/llm')">系统设置</n-button>
        <n-button type="primary" @click="router.push('/chat')">返回对话</n-button>
      </n-space>
    </header>

    <div class="doc-layout">
      <!-- 主内容区 -->
      <div class="doc-main">

        <!-- ========== /api/chat ========== -->
        <div id="anchor-chat" class="anchor-target" />
        <n-card :bordered="false" class="section-card">
          <div class="endpoint-row">
            <n-tag type="success" :bordered="false" class="method-tag">POST</n-tag>
            <code class="endpoint-path">/api/chat</code>
          </div>
          <p class="endpoint-desc">AI 对话接口，支持阻塞（JSON）和流式（SSE）两种响应模式，支持多轮会话。</p>
          <div class="auth-block">
            <n-text strong style="display:block;margin-bottom:8px">鉴权方式（二选一）</n-text>
            <div class="auth-row">
              <n-tag size="small" :bordered="false" type="info">Bearer Token</n-tag>
              <span>登录后将 JWT 放入请求头：<code>Authorization: Bearer &lt;token&gt;</code></span>
            </div>
            <div class="auth-row">
              <n-tag size="small" :bordered="false" type="warning">API Key</n-tag>
              <span>后台生成密钥后放入请求头：<code>X-API-Key: eaju_xxxxxxxx…</code>（32位固定长度）</span>
            </div>
          </div>
        </n-card>

        <n-collapse :default-expanded-names="openRows" class="collapse">
          <n-collapse-item name="params">
            <template #header>
              <div id="anchor-chat-params" class="anchor-target collapse-title">请求体参数（Request Body · application/json）</div>
            </template>
            <div class="table-wrap">
              <table class="doc-table">
                <thead><tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr></thead>
                <tbody>
                  <tr v-for="p in reqParams" :key="p.field">
                    <td><code>{{ p.field }}</code></td>
                    <td><span class="type-badge">{{ p.type }}</span></td>
                    <td><n-tag size="small" :bordered="false" :type="p.required ? 'error' : 'default'">{{ p.required ? '必填' : '可选' }}</n-tag></td>
                    <td class="desc-cell">{{ p.desc }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </n-collapse-item>

          <n-collapse-item name="message-type">
            <template #header>
              <div id="anchor-chat-message" class="anchor-target collapse-title">Message 对象结构</div>
            </template>
            <div class="table-wrap">
              <table class="doc-table">
                <thead><tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr></thead>
                <tbody>
                  <tr v-for="p in messageParams" :key="p.field">
                    <td><code>{{ p.field }}</code></td>
                    <td><span class="type-badge">{{ p.type }}</span></td>
                    <td><n-tag size="small" :bordered="false" :type="p.required ? 'error' : 'default'">{{ p.required ? '必填' : '可选' }}</n-tag></td>
                    <td class="desc-cell">{{ p.desc }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </n-collapse-item>

          <n-collapse-item name="resp-block">
            <template #header>
              <div id="anchor-chat-resp-block" class="anchor-target collapse-title">响应 · 阻塞模式（stream: false）</div>
            </template>
            <p class="section-tip">Content-Type: <code>application/json</code> &nbsp;·&nbsp; HTTP 200</p>
            <div class="table-wrap">
              <table class="doc-table">
                <thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
                <tbody>
                  <tr v-for="f in respFields" :key="f.field">
                    <td><code>{{ f.field }}</code></td>
                    <td><span class="type-badge">{{ f.type }}</span></td>
                    <td class="desc-cell">{{ f.desc }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="code-block-wrap">
              <div class="code-header"><span>响应示例</span><n-button size="tiny" @click="copy(blockingResponse)">复制</n-button></div>
              <pre class="code-block">{{ blockingResponse }}</pre>
            </div>
          </n-collapse-item>

          <n-collapse-item name="resp-stream">
            <template #header>
              <div id="anchor-chat-resp-stream" class="anchor-target collapse-title">响应 · 流式模式（stream: true）· SSE</div>
            </template>
            <p class="section-tip">Content-Type: <code>text/event-stream</code> &nbsp;·&nbsp; HTTP 200 · 每行一个事件</p>
            <div class="table-wrap">
              <table class="doc-table">
                <thead><tr><th>event</th><th>data 格式</th><th>说明</th></tr></thead>
                <tbody>
                  <tr v-for="f in sseFields" :key="f.event">
                    <td><code>{{ f.event }}</code></td>
                    <td><code style="white-space:nowrap">{{ f.data }}</code></td>
                    <td class="desc-cell">{{ f.desc }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="code-block-wrap">
              <div class="code-header"><span>SSE 推送示例</span><n-button size="tiny" @click="copy(streamingResponse)">复制</n-button></div>
              <pre class="code-block">{{ streamingResponse }}</pre>
            </div>
            <p class="section-tip" style="margin-top:10px">注意：流式模式不写入 PostgreSQL，如需落库请使用 stream: false。</p>
          </n-collapse-item>

          <n-collapse-item name="examples">
            <template #header>
              <div id="anchor-chat-examples" class="anchor-target collapse-title">调用示例（curl）</div>
            </template>
            <div class="example-group">
              <div class="code-block-wrap">
                <div class="code-header"><span>阻塞式请求</span><n-button size="tiny" @click="copy(curlBlocking)">复制</n-button></div>
                <pre class="code-block">{{ curlBlocking }}</pre>
              </div>
              <div class="code-block-wrap">
                <div class="code-header"><span>阻塞式请求体（JSON）</span><n-button size="tiny" @click="copy(blockingExample)">复制</n-button></div>
                <pre class="code-block">{{ blockingExample }}</pre>
              </div>
              <div class="code-block-wrap">
                <div class="code-header"><span>流式请求（SSE，需加 -N 禁用 curl 缓冲）</span><n-button size="tiny" @click="copy(curlStreaming)">复制</n-button></div>
                <pre class="code-block">{{ curlStreaming }}</pre>
              </div>
              <div class="code-block-wrap">
                <div class="code-header"><span>流式请求体（JSON）</span><n-button size="tiny" @click="copy(streamingExample)">复制</n-button></div>
                <pre class="code-block">{{ streamingExample }}</pre>
              </div>
            </div>
          </n-collapse-item>
        </n-collapse>

        <!-- ========== /api/file/upload ========== -->
        <div id="anchor-upload" class="anchor-target" />
        <n-card :bordered="false" class="section-card">
          <div class="endpoint-row">
            <n-tag type="success" :bordered="false" class="method-tag">POST</n-tag>
            <code class="endpoint-path">/api/file/upload</code>
          </div>
          <p class="endpoint-desc">将文件上传至对象存储，返回公网可访问 URL。上传成功后可将 URL 作为 <code>fileUrls</code> 传入 <code>/api/chat</code> 供多模态模型使用。单文件大小不超过 <strong>5 MB</strong>。</p>
          <div class="auth-block">
            <n-text strong style="display:block;margin-bottom:6px">鉴权方式（同 /api/chat，二选一）</n-text>
            <div class="auth-row">
              <n-tag size="small" :bordered="false" type="info">Bearer Token</n-tag>
              <span><code>Authorization: Bearer &lt;token&gt;</code></span>
            </div>
            <div class="auth-row">
              <n-tag size="small" :bordered="false" type="warning">API Key</n-tag>
              <span><code>X-API-Key: eaju_xxxxxxxx…</code></span>
            </div>
          </div>
        </n-card>

        <n-collapse :default-expanded-names="openUploadRows" class="collapse">
          <n-collapse-item name="upload-params">
            <template #header>
              <div id="anchor-upload-params" class="anchor-target collapse-title">请求参数（multipart/form-data）</div>
            </template>
            <div class="table-wrap">
              <table class="doc-table">
                <thead><tr><th>参数名</th><th>类型</th><th>必填</th><th>说明</th></tr></thead>
                <tbody>
                  <tr>
                    <td><code>file</code></td>
                    <td><span class="type-badge">File</span></td>
                    <td><n-tag size="small" :bordered="false" type="error">必填</n-tag></td>
                    <td class="desc-cell">要上传的文件，通过 multipart/form-data 表单字段 <code>file</code> 传入。大小限制 5 MB，超出将返回 400 错误。</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </n-collapse-item>

          <n-collapse-item name="upload-resp">
            <template #header>
              <div id="anchor-upload-resp" class="anchor-target collapse-title">响应（HTTP 200 · application/json）</div>
            </template>
            <div class="table-wrap">
              <table class="doc-table">
                <thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
                <tbody>
                  <tr v-for="f in uploadRespFields" :key="f.field">
                    <td><code>{{ f.field }}</code></td>
                    <td><span class="type-badge">{{ f.type }}</span></td>
                    <td class="desc-cell">{{ f.desc }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="code-block-wrap">
              <div class="code-header"><span>响应示例</span><n-button size="tiny" @click="copy(uploadResponse)">复制</n-button></div>
              <pre class="code-block">{{ uploadResponse }}</pre>
            </div>
          </n-collapse-item>

          <n-collapse-item name="upload-errors">
            <template #header>
              <div id="anchor-upload-errors" class="anchor-target collapse-title">错误码说明</div>
            </template>
            <div class="table-wrap">
              <table class="doc-table">
                <thead><tr><th>HTTP 状态</th><th>说明</th></tr></thead>
                <tbody>
                  <tr v-for="e in uploadErrors" :key="e.status">
                    <td><n-tag size="small" :bordered="false" type="error">{{ e.status }}</n-tag></td>
                    <td class="desc-cell">{{ e.desc }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </n-collapse-item>

          <n-collapse-item name="upload-example">
            <template #header>
              <div id="anchor-upload-example" class="anchor-target collapse-title">调用示例（curl）</div>
            </template>
            <div class="code-block-wrap">
              <div class="code-header"><span>上传文件</span><n-button size="tiny" @click="copy(curlUpload)">复制</n-button></div>
              <pre class="code-block">{{ curlUpload }}</pre>
            </div>
          </n-collapse-item>
        </n-collapse>

      </div>

      <!-- 右侧导航 -->
      <nav class="doc-nav">
        <div class="nav-title">接口导航</div>
        <div v-for="group in navItems" :key="group.id" class="nav-group">
          <div
            class="nav-group-label"
            :class="{ 'is-active': activeId === group.id }"
            @click="scrollTo(group.id)"
          >
            <span class="nav-method-badge">POST</span>
            <span class="nav-path">{{ group.label.replace('POST ', '') }}</span>
          </div>
          <div
            v-for="child in group.children"
            :key="child.id"
            class="nav-item"
            :class="{ 'is-active': activeId === child.id }"
            @click="scrollTo(child.id)"
          >
            {{ child.label }}
          </div>
        </div>
      </nav>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: #f9fafb;
  overflow: auto;
}
.bar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 24px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}
.title {
  font-size: 18px;
}

/* 两栏布局 */
.doc-layout {
  display: flex;
  align-items: flex-start;
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 20px 48px;
  gap: 24px;
}
.doc-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 锚点偏移（header 高度补偿） */
.anchor-target {
  scroll-margin-top: 72px;
}

/* 右侧导航 */
.doc-nav {
  flex-shrink: 0;
  width: 196px;
  position: sticky;
  top: 72px;
  max-height: calc(100vh - 96px);
  overflow-y: auto;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 14px 0 14px;
}
.nav-title {
  font-size: 11px;
  font-weight: 700;
  color: #9ca3af;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 0 16px 10px;
  border-bottom: 1px solid #f3f4f6;
  margin-bottom: 8px;
}
.nav-group {
  margin-bottom: 4px;
}
.nav-group-label {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 16px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  color: #374151;
  border-left: 2px solid transparent;
  transition: all 0.15s;
}
.nav-group-label:hover {
  color: #18a058;
  background: #f0fdf4;
}
.nav-group-label.is-active {
  color: #18a058;
  border-left-color: #18a058;
  background: #f0fdf4;
}
.nav-method-badge {
  display: inline-block;
  background: #dcfce7;
  color: #16a34a;
  border-radius: 3px;
  padding: 0 4px;
  font-size: 10px;
  font-weight: 700;
  flex-shrink: 0;
}
.nav-path {
  font-family: ui-monospace, monospace;
  word-break: break-all;
  line-height: 1.4;
}
.nav-item {
  padding: 4px 16px 4px 28px;
  cursor: pointer;
  font-size: 12px;
  color: #6b7280;
  border-left: 2px solid transparent;
  transition: all 0.15s;
  line-height: 1.5;
}
.nav-item:hover {
  color: #18a058;
  background: #f0fdf4;
}
.nav-item.is-active {
  color: #18a058;
  border-left-color: #18a058;
  background: #f0fdf4;
}

/* 接口卡片 */
.section-card {
  background: #fff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 12px;
}
.endpoint-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.method-tag {
  font-size: 13px;
  font-weight: 700;
  padding: 0 10px;
}
.endpoint-path {
  font-size: 18px;
  font-family: ui-monospace, monospace;
  font-weight: 600;
  color: #111;
}
.endpoint-desc {
  margin: 0 0 16px;
  color: #555;
  line-height: 1.6;
}
.auth-block {
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 14px 16px;
}
.auth-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 8px;
  line-height: 1.6;
  font-size: 13px;
  color: #444;
}
.auth-row:last-child { margin-bottom: 0; }

/* 折叠面板 */
.collapse {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
}
.collapse-title {
  font-size: 14px;
}

/* 表格 */
.table-wrap { overflow-x: auto; }
.doc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.doc-table th {
  background: #f8fafc;
  text-align: left;
  padding: 8px 12px;
  font-weight: 600;
  color: #374151;
  border-bottom: 1px solid #e5e7eb;
  white-space: nowrap;
}
.doc-table td {
  padding: 8px 12px;
  border-bottom: 1px solid #f3f4f6;
  vertical-align: top;
  color: #374151;
}
.doc-table tr:last-child td { border-bottom: none; }
.type-badge {
  display: inline-block;
  background: #eff6ff;
  color: #2563eb;
  border-radius: 4px;
  padding: 1px 6px;
  font-size: 12px;
  font-family: ui-monospace, monospace;
  white-space: nowrap;
}
.desc-cell { line-height: 1.6; color: #555; }

/* 代码块 */
.example-group { display: flex; flex-direction: column; gap: 16px; }
.code-block-wrap {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  margin-top: 12px;
}
.code-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: #f3f4f6;
  font-size: 12px;
  color: #6b7280;
  border-bottom: 1px solid #e5e7eb;
}
.code-block {
  margin: 0;
  padding: 14px 16px;
  background: #1e1e2e;
  color: #cdd6f4;
  font-family: ui-monospace, 'Cascadia Code', monospace;
  font-size: 12.5px;
  line-height: 1.7;
  overflow-x: auto;
  white-space: pre;
}
.section-tip {
  font-size: 13px;
  color: #6b7280;
  margin: 0 0 10px;
}
code {
  font-family: ui-monospace, monospace;
  font-size: 12px;
  background: #f3f4f6;
  padding: 1px 5px;
  border-radius: 4px;
}
</style>
