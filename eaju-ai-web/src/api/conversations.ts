import http from './http'

export interface ConversationItem {
  sessionId: string
  title: string
  lastMessageAt: string | null
  /** 该会话上次发消息时使用的提供方，用于恢复下拉选择 */
  lastProviderCode?: string | null
  lastModeKey?: string | null
  /** 提供商·模型展示名，如"通义千问·qwen-plus" */
  lastModelDisplayName?: string | null
  /** Agent 应用 ID，Agent 会话时有值，用于恢复 Agent 选择 */
  appId?: number | null
}

export interface ChatMessage {
  role: string
  content: string
  /** DeepSeek 等流式返回的 reasoning_content 拼接结果 */
  reasoningContent?: string
  /** 经 /api/file/upload 上传后的公网 URL，落库在 chat_turn.client_messages_json */
  fileUrls?: string[]
  /** 消息时间（ISO-8601），管理端消息查看接口返回 */
  createdAt?: string | null
}

export async function listConversations(agentId?: number | null, all?: boolean): Promise<ConversationItem[]> {
  const params: Record<string, unknown> = {}
  if (agentId != null) params.agentId = agentId
  if (all) params.all = true
  const { data } = await http.get<ConversationItem[]>('/api/conversations', { params })
  return data
}

export async function createConversation(agentId?: number | null): Promise<ConversationItem> {
  const params = agentId != null ? { agentId } : {}
  const { data } = await http.post<ConversationItem>('/api/conversations', undefined, { params })
  return data
}

export async function deleteConversation(sessionId: string): Promise<void> {
  await http.delete(`/api/conversations/${encodeURIComponent(sessionId)}`)
}

export async function loadConversationMessages(sessionId: string): Promise<ChatMessage[]> {
  const { data } = await http.get<ChatMessage[]>(
    `/api/conversations/${encodeURIComponent(sessionId)}/messages`,
  )
  return data
}
