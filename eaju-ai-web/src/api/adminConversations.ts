import http from './http'
import type { ChatMessage } from './conversations'

export interface ConversationAdminRow {
  id: number
  sessionId: string
  userId: string
  title: string
  lastMessageAt: string | null
  createdAt: string | null
  lastProviderCode: string | null
  lastModeKey: string | null
  apiKeyId: number | null
  apiKeyName: string | null
  deletedAt: string | null
  turnCount: number
  totalTokens: number
  type: 'CHAT' | 'API_KEY' | 'EMBED' | 'APP'
}

export interface ConversationDetail {
  sessionId: string
  userId: string
  title: string
  createdAt: string | null
  lastMessageAt: string | null
  lastProviderCode: string | null
  lastModeKey: string | null
  apiKeyId: number | null
  apiKeyName: string | null
  deletedAt: string | null
  usage: {
    promptTokens: number
    completionTokens: number
    totalTokens: number
  } | null
  byModel: ModelUsage[]
  type: 'CHAT' | 'API_KEY' | 'EMBED' | 'APP'
}

export interface ModelUsage {
  model: string
  turnCount: number
  totalTokens: number
}

export interface ConversationListResponse {
  content: ConversationAdminRow[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export async function adminListConversations(
  page: number,
  size: number,
  userId?: string,
  apiKeyId?: number,
  appId?: number
): Promise<ConversationListResponse> {
  const params: Record<string, unknown> = { page, size }
  if (userId) params.userId = userId
  if (apiKeyId) params.apiKeyId = apiKeyId
  if (appId) params.appId = appId
  const { data } = await http.get<ConversationListResponse>('/api/admin/conversations', { params })
  return data
}

export async function adminGetConversationDetail(sessionId: string): Promise<ConversationDetail> {
  const { data } = await http.get<ConversationDetail>(`/api/admin/conversations/${sessionId}`)
  return data
}

export async function adminGetConversationMessages(sessionId: string): Promise<ChatMessage[]> {
  const { data } = await http.get<ChatMessage[]>(`/api/admin/conversations/${sessionId}/messages`)
  return data
}