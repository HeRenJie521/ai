<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import {
  NCard,
  NCollapse,
  NCollapseItem,
  NTag,
  NText,
  useMessage,
} from 'naive-ui'

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
  { field: 'provider', type: 'string', required: true, desc: '模型提供商代码，与后台 llm_provider_config.code 一致，大小写不敏感。可选值：DEEPSEEK、QWEN、KIMI、QIANFAN、GEMINI、MINIMAX 等。' },
  { field: 'messages', type: 'Message[]', required: true, desc: '对话消息列表，至少包含一条。多轮对话时可只传最新一轮，历史记录由 sessionId 从服务端自动拼接。' },
  { field: 'mode', type: 'string', required: false, desc: '逻辑模型名，对应后台 llm_model.name。不传时使用该提供商的默认模型。与 model 二选一，model 优先。' },
  { field: 'model', type: 'string', required: false, desc: '直接指定上游模型 ID，绕过 mode 映射，原样透传给上游接口。' },
  { field: 'sessionId', type: 'string', required: false, desc: '会话 ID。有值时从 Redis 读取该会话历史并拼接在本轮消息之前，阻塞模式成功后将本轮写回 Redis 并落库。不传时服务端自动生成 UUID（X-API-Key 调用时）。' },
  { field: 'stream', type: 'boolean', required: false, desc: 'false 或不传：阻塞式，响应为 application/json；true：流式，响应为 text/event-stream（SSE）。注意：实际是否流式还受模型配置的 stream_output 字段控制。' },
  { field: 'temperature', type: 'number', required: false, desc: '采样温度，范围 0~2，值越高输出越随机。优先级：前端传入 > 模型配置 (llm_model.temperature) > 提供商默认值。' },
  { field: 'maxTokens', type: 'integer', required: false, desc: '最大输出 token 数（对应 OpenAI 的 max_tokens）。优先级：前端传入 > 模型配置 (llm_model.max_tokens) > 提供商默认值。' },
  { field: 'topP', type: 'number', required: false, desc: 'Top-P 核采样，范围 0~1。与 temperature 一般二选一使用。优先级：前端传入 > 模型配置 (llm_model.top_p)。' },
  { field: 'topK', type: 'integer', required: false, desc: 'Top-K 采样（部分厂商支持，如通义、Gemini）。优先级：前端传入 > 模型配置 (llm_model.top_k)。' },
  { field: 'frequencyPenalty', type: 'number', required: false, desc: '频率惩罚，范围 -2~2，减少已出现词的重复频率。优先级：前端传入 > 模型配置 (llm_model.frequency_penalty)。' },
  { field: 'presencePenalty', type: 'number', required: false, desc: '存在惩罚，范围 -2~2，鼓励模型涉及新话题。优先级：前端传入 > 模型配置 (llm_model.presence_penalty)。' },
  { field: 'responseFormat', type: 'string', required: false, desc: '回复格式。TEXT（默认）或 JSON_OBJECT（要求模型输出合法 JSON）。开启 JSON_OBJECT 时，部分提供商（如通义千问）会自动在 system message 中注入 JSON 提示。' },
  { field: 'thinkingMode', type: 'boolean', required: false, desc: '思考模式，对支持 deep_thinking 的模型生效（如 DeepSeek-R1）。开启后响应中包含 reasoningContent 字段（思维链）。优先级：前端传入 > 模型配置 (llm_model.thinking_mode)。' },
  { field: 'extendedParameters', type: 'object[]', required: false, desc: '扩展参数列表，格式：[{"key1":"value1"},{"key2":"value2"}]。用于 API Key 绑定应用场景：工具入参中来源设为「APIKey参数」的字段，会按 fieldKey 从此列表中取值，无需用户登录即可传入业务参数（如手机号、门店编码等）。' },
]

const messageParams = [
  { field: 'role', type: 'string', required: true, desc: '消息角色：user（用户）/ assistant（模型）/ system（系统提示）。' },
  { field: 'content', type: 'string', required: false, desc: '消息内容文本。可与 fileUrls 同时存在；纯附件时可传空串。' },
  { field: 'fileUrls', type: 'string[]', required: false, desc: '文件/图片公网 URL 列表，通过 /api/file/upload 上传后获得。图片按 OpenAI vision 格式发给上游，其它文件类型以链接形式触达模型。注意：实际是否支持图片上传受模型配置的 vision 字段控制。' },
]

const modelCapabilities = [
  { field: 'deep_thinking', type: 'boolean', desc: '是否支持深度思考（推理）能力。开启后配合 thinkingMode 参数可获得思维链回复。' },
  { field: 'vision', type: 'boolean', desc: '是否支持视觉理解（多模态）。为 false 时前端应禁止上传图片，或上传后自动移除并提示。' },
  { field: 'stream_output', type: 'boolean', desc: '是否支持流式输出。为 false 时即使请求 stream=true 也会使用阻塞式响应。' },
  { field: 'tool_call', type: 'boolean', desc: '是否支持工具调用（Function Calling）。为 false 时，绑定了工具的应用会收到错误提示："当前模型不支持工具调用，请切换到支持工具调用的模型。"' },
  { field: 'force_thinking_enabled', type: 'boolean', desc: '是否强制开启思考（MiniMax 系列专用）。为 true 时每次请求都强制带 thinking 参数。' },
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

const openRows = ref(['params', 'message-type', 'model-capabilities', 'resp-block', 'resp-stream', 'examples'])

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

// ---- /api/embed/login 数据 ----
const embedLoginExample = `{
  "integrationId": 1,
  "userId": "13800138000",
  "username": "张三",
  "token": "从管理后台「集成管理」复制的 Embed Token"
}`

const embedLoginResponse = `{
  "token": "eyJhbGciOiJIUzI1NiJ9……",
  "jti": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "expiresIn": 86400,
  "userId": "13800138000",
  "phone": "13800138000",
  "username": "张三",
  "admin": false,
  "defaultModel": null,
  "integrationName": "客服助手"
}`

const curlEmbedLogin = `curl -X POST http://your-host/api/embed/login \\
  -H "Content-Type: application/json" \\
  -d '{
    "integrationId": 1,
    "userId": "13800138000",
    "username": "张三",
    "token": "your_embed_token_here"
  }'`

const embedLoginReqParams = [
  { field: 'integrationId', type: 'Long', required: true,  desc: '集成 ID，对应 api_key 表 id，类型为 WEB_EMBED（type=2）。从管理后台「集成管理」页获取。' },
  { field: 'userId',        type: 'string', required: true,  desc: '业务用户 ID（通常为手机号）。用于在 JWT 中标识用户身份。' },
  { field: 'username',      type: 'string', required: false, desc: '用户显示名（如姓名）。未传时前端展示名降级为 userId。' },
  { field: 'token',         type: 'string', required: true,  desc: '嵌入凭证（Embed Token），从管理后台「集成管理」复制。服务端对其做 SHA-256 与库中存储的哈希值比对。' },
]

const embedLoginRespFields = [
  { field: 'token',           type: 'string',  desc: 'JWT access token，前端写入 localStorage 后续请求携带。' },
  { field: 'jti',             type: 'string',  desc: 'JWT ID，与 Redis 会话 key 一致，登出时用于作废服务端会话。' },
  { field: 'expiresIn',       type: 'Long',    desc: '过期秒数，与 Redis 会话 TTL 对齐（默认 86400 秒 / 24 小时）。' },
  { field: 'userId',          type: 'string',  desc: '业务用户 ID（与入参一致）。' },
  { field: 'phone',           type: 'string',  desc: '同 userId。' },
  { field: 'username',        type: 'string',  desc: '用户显示名（传入值，或 userId 降级）。' },
  { field: 'admin',           type: 'boolean', desc: '固定 false，嵌入用户无管理权限。' },
  { field: 'defaultModel',    type: 'string',  desc: '固定 null，WEB_EMBED 模式不携带默认模型。' },
  { field: 'integrationName', type: 'string',  desc: '集成名称（api_key.name），前端用于展示 AI 助手名称。' },
]

const openEmbedLoginRows = ref(['embed-login-params', 'embed-login-resp', 'embed-login-example'])

// ---- /api/embed/app-login 数据 ----
const appEmbedLoginExample = `{
  "appId": 1,
  "userId": "13800138000",
  "username": "张三"
}`

const appEmbedLoginResponse = `{
  "token": "eyJhbGciOiJIUzI1NiJ9……",
  "jti": "b2c3d4e5-f6a7-8901-bcde-f23456789012",
  "expiresIn": 86400,
  "userId": "13800138000",
  "phone": "13800138000",
  "username": "张三",
  "admin": false,
  "defaultModel": "42",
  "integrationName": "智能问答助手"
}`

const curlAppEmbedLogin = `curl -X POST http://your-host/api/embed/app-login \\
  -H "Content-Type: application/json" \\
  -d '{
    "appId": 1,
    "userId": "13800138000",
    "username": "张三"
  }'`

const appEmbedLoginReqParams = [
  { field: 'appId',    type: 'Long',   required: true,  desc: 'AI 应用 ID，对应 ai_app 表 id。从管理后台「应用管理」或嵌入代码中的 aid 参数获取。' },
  { field: 'userId',   type: 'string', required: true,  desc: '业务用户 ID。用于在 JWT 中标识用户身份，写入会话缓存。' },
  { field: 'username', type: 'string', required: false, desc: '用户显示名。未传时前端展示名降级为 userId。' },
]

const appEmbedLoginRespFields = [
  { field: 'token',           type: 'string',  desc: 'JWT access token，JWT claims 中携带 appId，ChatService 据此直接加载 AI 应用配置，无需用户手动选择模型。' },
  { field: 'jti',             type: 'string',  desc: 'JWT ID，登出时用于作废服务端会话。' },
  { field: 'expiresIn',       type: 'Long',    desc: '过期秒数（默认 86400 秒 / 24 小时）。' },
  { field: 'userId',          type: 'string',  desc: '业务用户 ID（与入参一致）。' },
  { field: 'phone',           type: 'string',  desc: '同 userId。' },
  { field: 'username',        type: 'string',  desc: '用户显示名。' },
  { field: 'admin',           type: 'boolean', desc: '固定 false。' },
  { field: 'defaultModel',    type: 'string',  desc: 'AI 应用绑定的 llm_model.id（字符串形式），前端聊天页默认选中该模型。' },
  { field: 'integrationName', type: 'string',  desc: 'AI 应用名称（ai_app.name），前端用于展示 AI 助手名称。' },
]

const openAppEmbedLoginRows = ref(['app-embed-login-params', 'app-embed-login-resp', 'app-embed-login-example'])

// ---- API Key 绑定应用 数据 ----
const apikeyAppExample = `curl -X POST http://your-host/api/chat \\
  -H "Content-Type: application/json" \\
  -H "X-API-Key: eaju_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" \\
  -d '{
    "provider": "QWEN",
    "mode": "qwen3.5-plus",
    "messages": [{ "role": "user", "content": "查询我今天的工单" }],
    "stream": true,
    "extendedParameters": [
      {"esusMobile": "13800138000"},
      {"esusId": "U001"},
      {"siteCode": "SITE_HZ"}
    ]
  }'`

const apikeyAppFlow = [
  { step: '1', title: '管理后台创建 API Key 并绑定应用', desc: '在「集成管理 → API Key」新建一条记录，在「绑定应用」下拉中选择已配置好系统提示和工具的 AI 应用，保存后复制生成的密钥。' },
  { step: '2', title: '工具入参来源设为 APIKey参数', desc: '在「接口管理 → 入参管理」中，将需要由调用方传入的字段（如手机号、门店编码）的「来源」设为「APIKey参数」，并填写对应的 key 名称（与 extendedParameters 中的 key 一致）。' },
  { step: '3', title: '调用 /api/chat 时携带 extendedParameters', desc: '在请求体中加入 extendedParameters 数组，每项为 {"key":"value"} 形式。服务端会在工具执行时自动将来源为 apikey 的参数从此数组中取值，无需用户登录即可完成工具调用。' },
]

const apikeyVsContext = [
  { source: 'context（用户数据）', when: '用户通过 embed/app-login 登录，Redis 中有用户上下文', typical: '用户手机号、姓名、权限标识' },
  { source: 'apikey（APIKey参数）', when: '通过 X-API-Key 直接调用，无需登录', typical: '调用方自行传入的业务参数，如手机号、门店编码、订单号等' },
  { source: 'llm（LLM参数）', when: '任意场景', typical: '由 AI 根据对话内容自动推断的参数，如日期、关键词' },
  { source: 'static（静态值）', when: '任意场景', typical: '固定不变的配置值，如接口方法名、分页大小' },
]

const openApikeyAppRows = ref(['apikey-app-flow', 'apikey-param-source', 'apikey-app-example'])

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
      { id: 'anchor-chat-capabilities', label: '模型能力配置' },
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
  {
    id: 'anchor-embed-login',
    label: 'POST /api/embed/login',
    tag: 'POST',
    children: [
      { id: 'anchor-embed-login-params', label: '请求体参数' },
      { id: 'anchor-embed-login-resp', label: '响应' },
      { id: 'anchor-embed-login-example', label: '调用示例' },
    ],
  },
  {
    id: 'anchor-app-embed-login',
    label: 'POST /api/embed/app-login',
    tag: 'POST',
    children: [
      { id: 'anchor-app-embed-login-params', label: '请求体参数' },
      { id: 'anchor-app-embed-login-resp', label: '响应' },
      { id: 'anchor-app-embed-login-example', label: '调用示例' },
    ],
  },
  {
    id: 'anchor-apikey-app',
    label: 'API Key 绑定应用',
    tag: 'GUIDE',
    children: [
      { id: 'anchor-apikey-app-flow', label: '使用流程' },
      { id: 'anchor-apikey-param-source', label: '入参来源对比' },
      { id: 'anchor-apikey-app-example', label: '调用示例' },
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

          <n-collapse-item name="model-capabilities">
            <template #header>
              <div id="anchor-chat-capabilities" class="anchor-target collapse-title">模型能力配置（后台配置，自动生效）</div>
            </template>
            <p class="section-tip">以下字段在后台「模型管理」中配置，调用时自动生效，无需前端传入。</p>
            <div class="table-wrap">
              <table class="doc-table">
                <thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
                <tbody>
                  <tr v-for="c in modelCapabilities" :key="c.field">
                    <td><code>{{ c.field }}</code></td>
                    <td><span class="type-badge">{{ c.type }}</span></td>
                    <td class="desc-cell">{{ c.desc }}</td>
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

        <!-- ========== /api/embed/login ========== -->
        <div id="anchor-embed-login" class="anchor-target" />
        <n-card :bordered="false" class="section-card">
          <div class="endpoint-row">
            <n-tag type="success" :bordered="false" class="method-tag">POST</n-tag>
            <code class="endpoint-path">/api/embed/login</code>
          </div>
          <p class="endpoint-desc">
            WEB_EMBED 集成免密 SSO 登录接口。集成方后端生成 iframe URL 时携带签名参数，前端 EmbedView 加载时调用此接口完成静默登录，JWT 写入 localStorage 后续自动维持登录态。
          </p>
          <div class="auth-block">
            <n-text strong style="display:block;margin-bottom:6px">鉴权方式</n-text>
            <div class="auth-row">
              <n-tag size="small" :bordered="false" type="default">无需 JWT / API Key</n-tag>
              <span>安全性由请求体中的 <code>token</code>（Embed Token）保证。服务端对 token 做 SHA-256 后与库中哈希比对，不匹配返回 400。</span>
            </div>
          </div>
        </n-card>

        <n-collapse :default-expanded-names="openEmbedLoginRows" class="collapse">
          <n-collapse-item name="embed-login-params">
            <template #header>
              <div id="anchor-embed-login-params" class="anchor-target collapse-title">请求体参数（Request Body · application/json）</div>
            </template>
            <div class="table-wrap">
              <table class="doc-table">
                <thead><tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr></thead>
                <tbody>
                  <tr v-for="p in embedLoginReqParams" :key="p.field">
                    <td><code>{{ p.field }}</code></td>
                    <td><span class="type-badge">{{ p.type }}</span></td>
                    <td><n-tag size="small" :bordered="false" :type="p.required ? 'error' : 'default'">{{ p.required ? '必填' : '可选' }}</n-tag></td>
                    <td class="desc-cell">{{ p.desc }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </n-collapse-item>

          <n-collapse-item name="embed-login-resp">
            <template #header>
              <div id="anchor-embed-login-resp" class="anchor-target collapse-title">响应（HTTP 200 · application/json）</div>
            </template>
            <div class="table-wrap">
              <table class="doc-table">
                <thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
                <tbody>
                  <tr v-for="f in embedLoginRespFields" :key="f.field">
                    <td><code>{{ f.field }}</code></td>
                    <td><span class="type-badge">{{ f.type }}</span></td>
                    <td class="desc-cell">{{ f.desc }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="code-block-wrap">
              <div class="code-header"><span>响应示例</span><n-button size="tiny" @click="copy(embedLoginResponse)">复制</n-button></div>
              <pre class="code-block">{{ embedLoginResponse }}</pre>
            </div>
          </n-collapse-item>

          <n-collapse-item name="embed-login-example">
            <template #header>
              <div id="anchor-embed-login-example" class="anchor-target collapse-title">调用示例（curl）</div>
            </template>
            <div class="example-group">
              <div class="code-block-wrap">
                <div class="code-header"><span>curl 请求</span><n-button size="tiny" @click="copy(curlEmbedLogin)">复制</n-button></div>
                <pre class="code-block">{{ curlEmbedLogin }}</pre>
              </div>
              <div class="code-block-wrap">
                <div class="code-header"><span>请求体（JSON）</span><n-button size="tiny" @click="copy(embedLoginExample)">复制</n-button></div>
                <pre class="code-block">{{ embedLoginExample }}</pre>
              </div>
            </div>
          </n-collapse-item>
        </n-collapse>

        <!-- ========== /api/embed/app-login ========== -->
        <div id="anchor-app-embed-login" class="anchor-target" />
        <n-card :bordered="false" class="section-card">
          <div class="endpoint-row">
            <n-tag type="success" :bordered="false" class="method-tag">POST</n-tag>
            <code class="endpoint-path">/api/embed/app-login</code>
          </div>
          <p class="endpoint-desc">
            应用管理嵌入登录接口。管理员在「应用管理」中配置好 AI 应用并生成嵌入代码，前端通过 URL 参数 <code>aid/uid/username</code> 调用此接口完成登录。<strong>无需 HMAC 签名</strong>，直接按 appId 校验应用是否存在即可。JWT 中携带 appId claim，ChatService 据此直接加载对应 AI 应用配置。
          </p>
          <div class="auth-block">
            <n-text strong style="display:block;margin-bottom:6px">鉴权方式</n-text>
            <div class="auth-row">
              <n-tag size="small" :bordered="false" type="default">无需 JWT / API Key / Embed Token</n-tag>
              <span>仅校验 appId 对应的 AI 应用是否存在且未删除，不存在则返回 400。</span>
            </div>
          </div>
        </n-card>

        <n-collapse :default-expanded-names="openAppEmbedLoginRows" class="collapse">
          <n-collapse-item name="app-embed-login-params">
            <template #header>
              <div id="anchor-app-embed-login-params" class="anchor-target collapse-title">请求体参数（Request Body · application/json）</div>
            </template>
            <div class="table-wrap">
              <table class="doc-table">
                <thead><tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr></thead>
                <tbody>
                  <tr v-for="p in appEmbedLoginReqParams" :key="p.field">
                    <td><code>{{ p.field }}</code></td>
                    <td><span class="type-badge">{{ p.type }}</span></td>
                    <td><n-tag size="small" :bordered="false" :type="p.required ? 'error' : 'default'">{{ p.required ? '必填' : '可选' }}</n-tag></td>
                    <td class="desc-cell">{{ p.desc }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </n-collapse-item>

          <n-collapse-item name="app-embed-login-resp">
            <template #header>
              <div id="anchor-app-embed-login-resp" class="anchor-target collapse-title">响应（HTTP 200 · application/json）</div>
            </template>
            <div class="table-wrap">
              <table class="doc-table">
                <thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
                <tbody>
                  <tr v-for="f in appEmbedLoginRespFields" :key="f.field">
                    <td><code>{{ f.field }}</code></td>
                    <td><span class="type-badge">{{ f.type }}</span></td>
                    <td class="desc-cell">{{ f.desc }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="code-block-wrap">
              <div class="code-header"><span>响应示例</span><n-button size="tiny" @click="copy(appEmbedLoginResponse)">复制</n-button></div>
              <pre class="code-block">{{ appEmbedLoginResponse }}</pre>
            </div>
          </n-collapse-item>

          <n-collapse-item name="app-embed-login-example">
            <template #header>
              <div id="anchor-app-embed-login-example" class="anchor-target collapse-title">调用示例（curl）</div>
            </template>
            <div class="example-group">
              <div class="code-block-wrap">
                <div class="code-header"><span>curl 请求</span><n-button size="tiny" @click="copy(curlAppEmbedLogin)">复制</n-button></div>
                <pre class="code-block">{{ curlAppEmbedLogin }}</pre>
              </div>
              <div class="code-block-wrap">
                <div class="code-header"><span>请求体（JSON）</span><n-button size="tiny" @click="copy(appEmbedLoginExample)">复制</n-button></div>
                <pre class="code-block">{{ appEmbedLoginExample }}</pre>
              </div>
            </div>
          </n-collapse-item>
        </n-collapse>

        <!-- ========== API Key 绑定应用 ========== -->
        <div id="anchor-apikey-app" class="anchor-target" />
        <n-card :bordered="false" class="section-card">
          <div class="endpoint-row">
            <n-tag type="info" :bordered="false" class="method-tag">使用指南</n-tag>
            <code class="endpoint-path">API Key 绑定应用</code>
          </div>
          <p class="endpoint-desc">
            将 API Key 与一个 AI 应用绑定后，调用方只需在请求头携带 <code>X-API-Key</code>，服务端即自动加载该应用的系统提示词和工具配置，无需任何登录操作。工具入参中来源设为「APIKey参数」的字段，从请求体的 <code>extendedParameters</code> 中取值。
          </p>
        </n-card>

        <n-collapse :default-expanded-names="openApikeyAppRows" class="collapse">
          <n-collapse-item name="apikey-app-flow">
            <template #header>
              <div id="anchor-apikey-app-flow" class="anchor-target collapse-title">使用流程</div>
            </template>
            <div class="table-wrap">
              <table class="doc-table">
                <thead><tr><th>步骤</th><th>操作</th><th>说明</th></tr></thead>
                <tbody>
                  <tr v-for="s in apikeyAppFlow" :key="s.step">
                    <td><n-tag size="small" :bordered="false" type="info">{{ s.step }}</n-tag></td>
                    <td style="white-space:nowrap; font-weight:500">{{ s.title }}</td>
                    <td class="desc-cell">{{ s.desc }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </n-collapse-item>

          <n-collapse-item name="apikey-param-source">
            <template #header>
              <div id="anchor-apikey-param-source" class="anchor-target collapse-title">工具入参来源对比</div>
            </template>
            <div class="table-wrap">
              <table class="doc-table">
                <thead><tr><th>来源类型</th><th>适用场景</th><th>典型用途</th></tr></thead>
                <tbody>
                  <tr v-for="r in apikeyVsContext" :key="r.source">
                    <td><code>{{ r.source }}</code></td>
                    <td class="desc-cell">{{ r.when }}</td>
                    <td class="desc-cell">{{ r.typical }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p class="section-tip" style="margin-top:10px">
              配置入口：接口管理 → 选择接口 → 入参管理 → 每个参数的「来源」下拉，选择「APIKey参数」后填写与 <code>extendedParameters</code> 中 key 一致的名称。
            </p>
          </n-collapse-item>

          <n-collapse-item name="apikey-app-example">
            <template #header>
              <div id="anchor-apikey-app-example" class="anchor-target collapse-title">调用示例（curl）</div>
            </template>
            <div class="code-block-wrap">
              <div class="code-header"><span>API Key 绑定应用 · 工具调用请求</span><n-button size="tiny" @click="copy(apikeyAppExample)">复制</n-button></div>
              <pre class="code-block">{{ apikeyAppExample }}</pre>
            </div>
            <p class="section-tip" style="margin-top:10px">
              服务端收到请求后，自动加载绑定应用的系统提示词和工具列表；工具执行时，来源为 <code>apikey</code> 的参数（如 <code>esusMobile</code>）从 <code>extendedParameters</code> 中取值。
            </p>
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
            <span :class="group.tag === 'GUIDE' ? 'nav-guide-badge' : 'nav-method-badge'">{{ group.tag }}</span>
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
.nav-guide-badge {
  display: inline-block;
  background: #dbeafe;
  color: #1d4ed8;
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
