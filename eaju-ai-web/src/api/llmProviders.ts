import http from './http'

/** 与 modes 中每个逻辑名对应的能力 */
export interface ModeCapability {
  textGeneration?: boolean
  deepThinking?: boolean
  vision?: boolean
  streamOutput?: boolean
  toolCall?: boolean
  /** 模型最大上下文 token 数，null 表示未配置 */
  contextWindow?: number | null
}

export interface LlmProviderOption {
  id: number
  code: string
  displayName: string
  defaultMode: string
  /** 与库表 modes_json 一致：逻辑名(展示) → 上游 model id；请求 /api/chat 时传逻辑名为 mode */
  modes?: Record<string, string>
  /** 与 modes 键一致：文本生成 / 深度思考 / 视觉理解 */
  modeCapabilities?: Record<string, ModeCapability>
  /** 是否支持在请求里带 thinking（如 DeepSeek） */
  supportsThinking?: boolean
  /** 系统设置里推理默认是否开启思考，用作聊天页开关初值 */
  defaultThinkingMode?: boolean
}

export async function listLlmProviders(): Promise<LlmProviderOption[]> {
  const { data } = await http.get<LlmProviderOption[]>('/api/llm-providers')
  return data
}
