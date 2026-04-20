import http from './http'

export interface LlmAdminRow {
  id: number
  code: string
  displayName: string
  apiKeyMasked: string
  apiKey?: string
  baseUrl: string
  forceTemperature: number | null
  thinkingParamStyle: string | null
  jsonModeSystemHint: boolean
  stripToolCallIndex: boolean
  enabled: boolean
  sortOrder: number
  createdAt: string | null
  updatedAt: string | null
}

export interface LlmCreatePayload {
  displayName: string
  apiKey?: string
  baseUrl: string
  enabled?: boolean
  sortOrder?: number
}

export type LlmUpdatePayload = Partial<{
  displayName: string
  apiKey: string
  baseUrl: string
  forceTemperature: number | null
  thinkingParamStyle: string | null
  jsonModeSystemHint: boolean
  stripToolCallIndex: boolean
  enabled: boolean
  sortOrder: number
}>

export async function adminListLlm(): Promise<LlmAdminRow[]> {
  const { data } = await http.get<LlmAdminRow[]>('/api/admin/llm-providers')
  return data
}

export async function adminGetLlm(id: number): Promise<LlmAdminRow> {
  const { data } = await http.get<LlmAdminRow>(`/api/admin/llm-providers/${id}`)
  return data
}

export async function adminCreateLlm(payload: LlmCreatePayload): Promise<LlmAdminRow> {
  const { data } = await http.post<LlmAdminRow>('/api/admin/llm-providers', payload)
  return data
}

export async function adminUpdateLlm(id: number, payload: LlmUpdatePayload): Promise<LlmAdminRow> {
  const { data } = await http.put<LlmAdminRow>(`/api/admin/llm-providers/${id}`, payload)
  return data
}

export async function adminDeleteLlm(id: number): Promise<void> {
  await http.delete(`/api/admin/llm-providers/${id}`)
}
