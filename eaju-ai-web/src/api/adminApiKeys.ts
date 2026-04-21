import http from './http'
import type { ChatMessage, ConversationItem } from './conversations'

/** 集成类型：1=API_KEY  2=WEB_EMBED */
export type IntegrationType = 1 | 2

export interface ApiKeyRow {
  id: number
  name: string
  secretPrefix: string
  enabled: boolean
  createdAt: string | null
  /** 1=API_KEY  2=WEB_EMBED */
  type: IntegrationType
  /** WEB_EMBED 允许嵌入的来源域名 */
  allowedOrigins: string | null
  /** API_KEY 绑定的 AI 应用 ID */
  appId: number | null
}

export interface ApiKeyCreated extends ApiKeyRow {
  /** 仅创建时返回完整凭证（API_KEY 或 WEB_EMBED），请立即保存 */
  plainSecret: string | null
}

export interface CreateIntegrationPayload {
  name: string
  type: IntegrationType
  /** WEB_EMBED 允许的来源域名（可选） */
  allowedOrigins?: string
  /** API_KEY 绑定的 AI 应用 ID（可选） */
  appId?: number | null
}

export async function adminListApiKeys(): Promise<ApiKeyRow[]> {
  const { data } = await http.get<ApiKeyRow[]>('/api/admin/api-keys')
  return data
}

export async function adminCreateApiKey(payload: CreateIntegrationPayload): Promise<ApiKeyCreated> {
  const { data } = await http.post<ApiKeyCreated>('/api/admin/api-keys', payload)
  return data
}

export async function adminPatchApiKey(
  id: number,
  body: Partial<{
    name: string
    enabled: boolean
    allowedOrigins: string
    appId: number | null
  }>,
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
  userId: string | null
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
