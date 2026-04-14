import http from './http'

export interface LlmAdminRow {
  id: number
  code: string
  displayName: string
  apiKeyMasked: string
  apiKey?: string
  baseUrl: string
  defaultMode: string
  modesJson: string
  inferenceDefaultsJson: string | null
  enabled: boolean
  sortOrder: number
  createdAt: string | null
  updatedAt: string | null
}

/** 新增大模型：仅名称与连接信息；服务端根据名称生成 code，并写入占位 mode，需在「高级配置」中完善。 */
export interface LlmCreatePayload {
  displayName: string
  apiKey?: string
  baseUrl: string
  enabled?: boolean
  /** 不传则由服务端自动排在末尾 */
  sortOrder?: number
}

export type LlmUpdatePayload = Partial<{
  displayName: string
  apiKey: string
  baseUrl: string
  defaultMode: string
  modesJson: string
  inferenceDefaultsJson: string | null
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
