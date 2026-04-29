import http from './http'

export interface ChatRequestBody {
  provider: string
  mode?: string
  model?: string
  sessionId?: string
  userId?: string
  messages: { role: string; content: string; fileUrls?: string[]; reasoningContent?: string }[]
  stream?: boolean
  temperature?: number
  maxTokens?: number
  /** 与后端 ChatRequestDto.thinkingMode 一致；false 时不展示/不落库思考流 */
  thinkingMode?: boolean
  /** 选择的 Agent 应用 ID，后端据此应用 Agent 的提示词/工具等配置 */
  agentId?: number
}

/** 流式增量：正文与思考过程（OpenAI 兼容里常为 delta.reasoning_content） */
export interface StreamDelta {
  content?: string
  reasoning?: string
}

/** 从 content 流中剥离 <think>...</think> 块，路由到 reasoning */
class ThinkTagParser {
  private state: 'content' | 'think' = 'content'
  private buf = ''

  process(chunk: string): { content: string; reasoning: string } {
    let input = this.buf + chunk
    this.buf = ''
    let content = ''
    let reasoning = ''
    while (input.length > 0) {
      if (this.state === 'content') {
        // 先找 <think> 开启标签
        const openIdx = input.indexOf('<think>')
        // 再找孤立的 </think>（vLLM 有时不发 <think> 只发 </think> token）
        const closeIdx = input.indexOf('</think>')
        if (openIdx === -1 && closeIdx === -1) {
          const partial = longestPartialPrefix(input, '<think>') || longestPartialPrefix(input, '</think>')
          if (partial > 0) {
            content += input.slice(0, input.length - partial)
            this.buf = input.slice(input.length - partial)
          } else {
            content += input
          }
          break
        }
        // 取最先出现的标签处理
        if (closeIdx !== -1 && (openIdx === -1 || closeIdx < openIdx)) {
          // 孤立 </think>：直接丢弃，前面的内容保留
          content += input.slice(0, closeIdx)
          input = input.slice(closeIdx + 8)
          continue
        }
        content += input.slice(0, openIdx)
        this.state = 'think'
        input = input.slice(openIdx + 7)
      } else {
        const idx = input.indexOf('</think>')
        if (idx === -1) {
          const partial = longestPartialPrefix(input, '</think>')
          if (partial > 0) {
            reasoning += input.slice(0, input.length - partial)
            this.buf = input.slice(input.length - partial)
          } else {
            reasoning += input
          }
          break
        }
        reasoning += input.slice(0, idx)
        this.state = 'content'
        input = input.slice(idx + 8)
      }
    }
    return { content, reasoning }
  }
}

function longestPartialPrefix(text: string, tag: string): number {
  for (let len = Math.min(tag.length - 1, text.length); len > 0; len--) {
    if (tag.startsWith(text.slice(text.length - len))) return len
  }
  return 0
}

function waitForPaint(): Promise<void> {
  return new Promise((resolve) => {
    if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
      window.requestAnimationFrame(() => resolve())
      return
    }
    window.setTimeout(resolve, 0)
  })
}

function parseSseBlock(block: string): { event: string; data: string } | null {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }
  if (!dataLines.length) {
    return null
  }
  return { event, data: dataLines.join('\n') }
}

function isAbortError(e: unknown): boolean {
  if (e instanceof DOMException && e.name === 'AbortError') {
    return true
  }
  return typeof e === 'object' && e !== null && 'name' in e && (e as { name: string }).name === 'AbortError'
}

export interface ChatStreamOptions {
  signal?: AbortSignal
  /** 用户取消（主动停止或离开页面等）；保留已展示的增量，不调用 onError */
  onAbort?: () => void | Promise<void>
}

export async function chatStreamFetch(
  token: string,
  body: ChatRequestBody,
  onDelta: (delta: StreamDelta) => void | Promise<void>,
  onDone: () => void | Promise<void>,
  onError: (e: Error) => void | Promise<void>,
  options?: ChatStreamOptions,
): Promise<void> {
  const signal = options?.signal
  let res: Response
  try {
    res = await fetch('/api/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ ...body, stream: true }),
      signal,
    })
  } catch (e) {
    if (isAbortError(e)) {
      await options?.onAbort?.()
      return
    }
    // 增强错误提示：识别超时错误
    const errMsg = e instanceof Error ? e.message : String(e)
    if (errMsg.includes('timeout') || errMsg.includes('timed out')) {
      await onError(new Error('请求超时，模型响应时间过长。请稍后重试或简化问题。'))
    } else {
      await onError(e instanceof Error ? e : new Error(String(e)))
    }
    return
  }
  if (!res.ok) {
    const t = await res.text()
    await onError(new Error(t || `HTTP ${res.status}`))
    return
  }
  const reader = res.body?.getReader()
  if (!reader) {
    await onError(new Error('无响应流'))
    return
  }
  const onSigAbort = () => {
    try {
      void reader.cancel()
    } catch {
      /* ignore */
    }
  }
  if (signal) {
    signal.addEventListener('abort', onSigAbort)
  }
  const dec = new TextDecoder()
  let carry = ''
  const thinkParser = new ThinkTagParser()
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      carry += dec.decode(value, { stream: true }).replace(/\r\n/g, '\n')
      let sep = carry.indexOf('\n\n')
      while (sep !== -1) {
        const block = carry.slice(0, sep)
        carry = carry.slice(sep + 2)
        const parsed = parseSseBlock(block)
        if (parsed) {
          if (parsed.event === 'done' || parsed.data === '[DONE]') {
            await onDone()
            return
          }
          if (parsed.event === 'chunk' && parsed.data) {
            try {
              const j = JSON.parse(parsed.data) as {
                choices?: {
                  delta?: {
                    content?: string | null
                    reasoning_content?: string | null
                  }
                }[]
              }
              const delta = j.choices?.[0]?.delta
              const rawContent = typeof delta?.content === 'string' ? delta.content : ''
              const rawReasoning = typeof delta?.reasoning_content === 'string' ? delta.reasoning_content : ''
              let content: string | undefined
              let reasoning: string | undefined
              if (rawReasoning) {
                // 模型使用 reasoning_content 字段（如官方 DeepSeek API / vLLM separate_reasoning）
                // vLLM 有时仍会把 <think>/<\/think> token 输出到 content 里，需要过滤掉
                const cleanContent = rawContent.replace(/<\/?think>/g, '')
                content = cleanContent || undefined
                reasoning = rawReasoning || undefined
              } else {
                // 本地部署模型将 <think>...</think> 内联在 content 里（vLLM 默认行为）
                const parsed2 = thinkParser.process(rawContent)
                content = parsed2.content || undefined
                reasoning = parsed2.reasoning || undefined
              }
              if (content || reasoning) {
                await onDelta({ content, reasoning })
                await waitForPaint()
              }
            } catch {
              /* ignore */
            }
          }
        }
        sep = carry.indexOf('\n\n')
      }
    }
    await onDone()
  } catch (e) {
    if (isAbortError(e) || signal?.aborted) {
      await options?.onAbort?.()
      return
    }
    await onError(e instanceof Error ? e : new Error(String(e)))
  } finally {
    if (signal) {
      signal.removeEventListener('abort', onSigAbort)
    }
  }
}

export interface ChatBlockResponse {
  provider: string
  model: string
  content?: string
  reasoningContent?: string
  finishReason?: string
}

export async function chatBlock(body: ChatRequestBody): Promise<ChatBlockResponse> {
  try {
    const { data } = await http.post<ChatBlockResponse>('/api/chat', { ...body, stream: false })
    return data
  } catch (e) {
    // 增强超时错误提示
    if (e instanceof Error && (e.message.includes('timeout') || e.message.includes('timed out'))) {
      throw new Error('请求超时，模型响应时间过长。请稍后重试或简化问题。')
    }
    throw e
  }
}
