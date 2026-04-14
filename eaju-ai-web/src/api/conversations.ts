import http from './http'

export interface ConversationItem {
  sessionId: string
  title: string
  lastMessageAt: string | null
  /** 该会话上次发消息时使用的提供方，用于恢复下拉选择 */
  lastProviderCode?: string | null
  lastModeKey?: string | null
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

export async function listConversations(): Promise<ConversationItem[]> {
  const { data } = await http.get<ConversationItem[]>('/api/conversations')
  return data
}

export async function createConversation(): Promise<ConversationItem> {
  const { data } = await http.post<ConversationItem>('/api/conversations')
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
