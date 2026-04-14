import http from './http'
import type { ChatMessage, ConversationItem } from './conversations'

export interface ApiKeyRow {
  id: number
  name: string
  secretPrefix: string
  enabled: boolean
  createdAt: string | null
}

export interface ApiKeyCreated extends ApiKeyRow {
  plainSecret: string
}

export async function adminListApiKeys(): Promise<ApiKeyRow[]> {
  const { data } = await http.get<ApiKeyRow[]>('/api/admin/api-keys')
  return data
}

export async function adminCreateApiKey(name: string): Promise<ApiKeyCreated> {
  const { data } = await http.post<ApiKeyCreated>('/api/admin/api-keys', { name })
  return data
}

export async function adminPatchApiKey(
  id: number,
  body: Partial<{ name: string; enabled: boolean }>,
): Promise<ApiKeyRow> {
  const { data } = await http.patch<ApiKeyRow>(`/api/admin/api-keys/${id}`, body)
  return data
}

export async function adminDeleteApiKey(id: number): Promise<void> {
  await http.delete(`/api/admin/api-keys/${id}`)
}

export interface ModelUsageRow {
  model: string
  turnCount: number
  totalTokens: number
}

export interface RecentTurnRow {
  id: number
  sessionId: string
  provider: string
  model: string
  promptTokens: number | null
  completionTokens: number | null
  totalTokens: number | null
  createdAt: string | null
  assistantPreview: string | null
}

export interface ApiKeyUsage {
  turnCount: number
  totalPromptTokens: number
  totalCompletionTokens: number
  totalTokens: number
  byModel: ModelUsageRow[]
  recentTurns: RecentTurnRow[]
}

export async function adminApiKeyUsage(id: number): Promise<ApiKeyUsage> {
  const { data } = await http.get<ApiKeyUsage>(`/api/admin/api-keys/${id}/usage`)
  return data
}

export async function adminApiKeyConversations(id: number): Promise<ConversationItem[]> {
  const { data } = await http.get<ConversationItem[]>(`/api/admin/api-keys/${id}/conversations`)
  return data
}

export async function adminApiKeySessionMessages(
  id: number,
  sessionId: string,
): Promise<ChatMessage[]> {
  const { data } = await http.get<ChatMessage[]>(
    `/api/admin/api-keys/${id}/sessions/${encodeURIComponent(sessionId)}/messages`,
  )
  return data
}
