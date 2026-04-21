import http from './http'

/** 应用工具绑定信息（含调用策略） */
export interface AppToolBinding {
  toolId: number
  toolName: string
  toolLabel: string
  toolDescription: string
  callStrategy: string | null
}

/** 应用工具绑定输入（含调用策略） */
export interface AppToolBindingInput {
  toolId: number
  callStrategy: string | null
}

export interface AiToolRow {
  id: number
  name: string
  label: string
  description: string
  /** 关联的接口定义 ID */
  apiDefinitionId: number | null
  /** 从关联的接口定义中获取的 URL（只读） */
  url: string | null
  /** 从关联的接口定义中获取的 HTTP 方法（只读） */
  httpMethod: string | null
  /** 从关联的接口定义中获取的 Content-Type（只读） */
  contentType: string | null
  headersJson: string | null
  bodyTemplate: string | null
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
  /** 关联的接口定义 ID */
  apiDefinitionId: number | null
  headersJson?: string
  bodyTemplate?: string
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

/** 获取应用绑定的工具及调用策略 */
export async function adminGetAppToolBindings(appId: number): Promise<AppToolBinding[]> {
  const { data } = await http.get<AppToolBinding[]>(`/api/admin/ai-apps/${appId}/tools`)
  return data
}

/** 保存应用工具绑定及调用策略 */
export async function adminSaveAppToolBindings(appId: number, bindings: AppToolBindingInput[]): Promise<void> {
  await http.put(`/api/admin/ai-apps/${appId}/tool-bindings`, bindings)
}

/** 获取应用已绑定的工具列表（旧接口，仅返回 toolIds） */
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
  extendedParams?: Record<string, string>,
): Promise<{ result: string; elapsedMs: number; requestBody?: string }> {
  const { data } = await http.post(`/api/admin/tools/${id}/test`, { testContext, toolArgs, extendedParams })
  return data
}
