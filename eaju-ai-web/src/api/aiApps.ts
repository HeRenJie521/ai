import http from './http'

export interface AiAppOption {
  id: number
  name: string
  modelDisplayName: string | null
  providerCode: string | null
  modeKey: string | null
  deepThinking: boolean
  vision: boolean
  streamOutput: boolean
}

export async function listAiApps(): Promise<AiAppOption[]> {
  const { data } = await http.get<AiAppOption[]>('/api/ai-apps')
  return data
}
