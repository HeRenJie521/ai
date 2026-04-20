import http from './http'
import type { ChatMessage } from './conversations'

export interface AiAppRow {
  id: number
  name: string
  welcomeText: string | null
  suggestions: string | null
  systemRole: string | null
  systemTask: string | null
  systemConstraints: string | null
  llmModelId: number | null
  modelDisplayName: string | null
  createdAt: string | null
}

export interface AiAppCreatePayload {
  name: string
  welcomeText?: string
  suggestions?: string
  systemRole?: string
  systemTask?: string
  systemConstraints?: string
  llmModelId?: number
}

export interface AiAppUpdatePayload {
  name?: string
  welcomeText?: string
  suggestions?: string
  systemRole?: string
  systemTask?: string
  systemConstraints?: string
  llmModelId?: number
}

export async function adminListAiApps(): Promise<AiAppRow[]> {
  const { data } = await http.get<AiAppRow[]>('/api/admin/ai-apps')
  return data
}

export async function adminCreateAiApp(payload: AiAppCreatePayload): Promise<AiAppRow> {
  const { data } = await http.post<AiAppRow>('/api/admin/ai-apps', payload)
  return data
}

export async function adminUpdateAiApp(id: number, payload: AiAppUpdatePayload): Promise<AiAppRow> {
  const { data } = await http.patch<AiAppRow>(`/api/admin/ai-apps/${id}`, payload)
  return data
}

export async function adminDeleteAiApp(id: number): Promise<void> {
  await http.delete(`/api/admin/ai-apps/${id}`)
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

export interface AiAppUsage {
  turnCount: number
  totalPromptTokens: number
  totalCompletionTokens: number
  totalTokens: number
  byModel: ModelUsageRow[]
  recentTurns: RecentTurnRow[]
}

export async function adminAiAppUsage(id: number): Promise<AiAppUsage> {
  const { data } = await http.get<AiAppUsage>(`/api/admin/ai-apps/${id}/usage`)
  return data
}

export async function adminAiAppSessionMessages(
  id: number,
  sessionId: string,
): Promise<ChatMessage[]> {
  const { data } = await http.get<ChatMessage[]>(
    `/api/admin/ai-apps/${id}/sessions/${encodeURIComponent(sessionId)}/messages`,
  )
  return data
}
