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
}

/** 流式增量：正文与思考过程（OpenAI 兼容里常为 delta.reasoning_content） */
export interface StreamDelta {
  content?: string
  reasoning?: string
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
    await onError(e instanceof Error ? e : new Error(String(e)))
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
              const content =
                delta?.content != null && typeof delta.content === 'string' && delta.content.length > 0
                  ? delta.content
                  : undefined
              const reasoning =
                delta?.reasoning_content != null &&
                typeof delta.reasoning_content === 'string' &&
                delta.reasoning_content.length > 0
                  ? delta.reasoning_content
                  : undefined
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
  const { data } = await http.post<ChatBlockResponse>('/api/chat', { ...body, stream: false })
  return data
}
