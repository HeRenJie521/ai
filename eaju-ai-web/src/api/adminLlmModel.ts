import http from './http'

export interface LlmModelAdminRow {
  id: number
  providerId: number
  providerCode: string
  providerDisplayName: string
  name: string
  upstreamModelId: string
  textGeneration: boolean
  deepThinking: boolean
  vision: boolean
  streamOutput: boolean
  toolCall: boolean
  forceThinkingEnabled: boolean
  temperature: number | null
  maxTokens: number | null
  topP: number | null
  topK: number | null
  frequencyPenalty: number | null
  presencePenalty: number | null
  responseFormat: string | null
  thinkingMode: boolean | null
  contextWindow: number | null
  sortOrder: number
  enabled: boolean
  createdAt: string | null
}

export interface LlmModelSavePayload {
  providerId: number
  name: string
  upstreamModelId?: string
  textGeneration?: boolean
  deepThinking?: boolean
  vision?: boolean
  streamOutput?: boolean
  toolCall?: boolean
  forceThinkingEnabled?: boolean
  temperature?: number | null
  maxTokens?: number | null
  topP?: number | null
  topK?: number | null
  frequencyPenalty?: number | null
  presencePenalty?: number | null
  responseFormat?: string | null
  thinkingMode?: boolean | null
  contextWindow?: number | null
  sortOrder?: number
  enabled?: boolean
}

export async function adminListLlmModels(providerId?: number): Promise<LlmModelAdminRow[]> {
  const params = providerId != null ? { providerId } : {}
  const { data } = await http.get<LlmModelAdminRow[]>('/api/admin/llm-models', { params })
  return data
}

export async function adminCreateLlmModel(
  payload: LlmModelSavePayload,
): Promise<LlmModelAdminRow> {
  const { data } = await http.post<LlmModelAdminRow>('/api/admin/llm-models', payload)
  return data
}

export async function adminUpdateLlmModel(
  id: number,
  payload: LlmModelSavePayload,
): Promise<LlmModelAdminRow> {
  const { data } = await http.put<LlmModelAdminRow>(`/api/admin/llm-models/${id}`, payload)
  return data
}

export async function adminDeleteLlmModel(id: number): Promise<void> {
  await http.delete(`/api/admin/llm-models/${id}`)
}
