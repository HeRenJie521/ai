import http from './http'

export interface AiAppRow {
  id: number
  name: string
  welcomeText: string | null
  suggestions: string | null
  systemRole: string | null
  systemTask: string | null
  systemConstraints: string | null
  modelId: string | null
  createdAt: string | null
}

export interface AiAppCreatePayload {
  name: string
  welcomeText?: string
  suggestions?: string
  systemRole?: string
  systemTask?: string
  systemConstraints?: string
  modelId?: string
}

export interface AiAppUpdatePayload {
  name?: string
  welcomeText?: string
  suggestions?: string
  systemRole?: string
  systemTask?: string
  systemConstraints?: string
  modelId?: string
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
