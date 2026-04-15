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
  defaultModel: string | null
  /** WEB_EMBED 开场白文本 */
  welcomeText: string | null
  /** WEB_EMBED 推荐问题 JSON 字符串 */
  suggestions: string | null
}

export interface ApiKeyCreated extends ApiKeyRow {
  /** 仅创建时返回完整凭证（API_KEY 或 WEB_EMBED），请立即保存 */
  plainSecret: string | null
}

export interface CreateIntegrationPayload {
  name: string
  type: IntegrationType
  /** WEB_EMBED 时必填 */
  defaultModel?: string
  /** WEB_EMBED 允许的来源域名（可选） */
  allowedOrigins?: string
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
  body: Partial<{ name: string; enabled: boolean; welcomeText: string; suggestions: string }>,
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
