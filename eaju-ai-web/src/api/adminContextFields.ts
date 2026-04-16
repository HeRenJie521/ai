import http from './http'

export interface ContextFieldRow {
  id: number
  fieldKey: string
  label: string
  description: string | null
  enabled: boolean
  createdAt: string | null
}

export interface ContextFieldSavePayload {
  fieldKey: string
  label: string
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
