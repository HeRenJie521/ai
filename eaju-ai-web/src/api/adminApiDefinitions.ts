import http from './http'

export interface ApiDefinitionRow {
  id: number
  systemName: string
  requestUrl: string
  httpMethod: string
  contentType: string
  remark: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface ApiDefinitionSavePayload {
  systemName: string
  requestUrl: string
  httpMethod?: string
  contentType: string
  remark?: string
}

export async function adminListApiDefinitions(): Promise<ApiDefinitionRow[]> {
  const { data } = await http.get<ApiDefinitionRow[]>('/api/admin/api-definitions')
  return data
}

export async function adminCreateApiDefinition(payload: ApiDefinitionSavePayload): Promise<ApiDefinitionRow> {
  const { data } = await http.post<ApiDefinitionRow>('/api/admin/api-definitions', payload)
  return data
}

export async function adminUpdateApiDefinition(id: number, payload: Partial<ApiDefinitionSavePayload>): Promise<ApiDefinitionRow> {
  const { data } = await http.put<ApiDefinitionRow>(`/api/admin/api-definitions/${id}`, payload)
  return data
}

export async function adminDeleteApiDefinition(id: number): Promise<void> {
  await http.delete(`/api/admin/api-definitions/${id}`)
}
