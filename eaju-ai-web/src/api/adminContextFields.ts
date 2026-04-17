import http from './http'

export interface ContextFieldRow {
  id: number
  fieldKey: string
  label: string
  fieldType: string
  parseExpression: string | null
  description: string | null
  enabled: boolean
  createdAt: string | null
}

export interface ContextFieldSavePayload {
  fieldKey: string
  label: string
  fieldType?: string
  parseExpression?: string
  description?: string
  enabled?: boolean
}

export async function adminListContextFields(): Promise<ContextFieldRow[]> {
  const { data } = await http.get<ContextFieldRow[]>('/api/admin/context-fields')
  return data
}

export async function adminCreateContextField(payload: ContextFieldSavePayload): Promise<ContextFieldRow> {
  const { data } = await http.post<ContextFieldRow>('/api/admin/context-fields', payload)
  return data
}

export async function adminUpdateContextField(id: number, payload: Partial<ContextFieldSavePayload>): Promise<ContextFieldRow> {
  const { data } = await http.put<ContextFieldRow>(`/api/admin/context-fields/${id}`, payload)
  return data
}

export async function adminDeleteContextField(id: number): Promise<void> {
  await http.delete(`/api/admin/context-fields/${id}`)
}

/** 用当前登录账号的 DMS 会话数据测试字段解析结果 */
export async function adminTestContextField(id: number): Promise<{
  found: boolean
  value: string | null
  expression: string
  error?: string
}> {
  const { data } = await http.post(`/api/admin/context-fields/${id}/test`)
  return data
}
