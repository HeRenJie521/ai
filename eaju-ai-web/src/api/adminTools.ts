import http from './http'

export interface AiToolRow {
  id: number
  name: string
  label: string
  description: string
  httpMethod: string
  url: string
  headersJson: string | null
  bodyTemplate: string | null
  contentType: string | null
  methodName: string | null
  dataParamsJson: string | null
  responseParamsJson: string | null
  paramsSchemaJson: string
  enabled: boolean
  createdAt: string | null
}

export interface AiToolSavePayload {
  name: string
  label?: string
  description: string
  httpMethod?: string
  url: string
  headersJson?: string
  bodyTemplate?: string
  contentType?: string
  methodName?: string
  dataParamsJson?: string
  responseParamsJson?: string
  paramsSchemaJson: string
  enabled?: boolean
}

export async function adminListTools(): Promise<AiToolRow[]> {
  const { data } = await http.get<AiToolRow[]>('/api/admin/tools')
  return data
}

export async function adminCreateTool(payload: AiToolSavePayload): Promise<AiToolRow> {
  const { data } = await http.post<AiToolRow>('/api/admin/tools', payload)
  return data
}

export async function adminUpdateTool(id: number, payload: Partial<AiToolSavePayload>): Promise<AiToolRow> {
  const { data } = await http.put<AiToolRow>(`/api/admin/tools/${id}`, payload)
  return data
}

export async function adminDeleteTool(id: number): Promise<void> {
  await http.delete(`/api/admin/tools/${id}`)
}

/** 获取应用已绑定的工具列表 */
export async function adminGetAppTools(appId: number): Promise<AiToolRow[]> {
  const { data } = await http.get<AiToolRow[]>(`/api/admin/ai-apps/${appId}/tools`)
  return data
}

/** 更新应用的工具绑定（传工具 id 数组，顺序即 sortOrder；传空数组表示解绑全部） */
export async function adminBindAppTools(appId: number, toolIds: number[]): Promise<void> {
  await http.put(`/api/admin/ai-apps/${appId}/tools`, { toolIds })
}

/** 测试工具调用 */
export async function adminTestTool(
  id: number,
  testContext: Record<string, string>,
  toolArgs?: string,
): Promise<{ result: string; elapsedMs: number; requestBody?: string }> {
  const { data } = await http.post(`/api/admin/tools/${id}/test`, { testContext, toolArgs })
  return data
}
