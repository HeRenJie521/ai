import http from './http'

export interface MobileAiApp {
  id: number
  name: string
  welcomeText: string | null
  suggestions: string | null
  modelDisplayName: string | null
}

export async function listMobileApps(): Promise<MobileAiApp[]> {
  const { data } = await http.get<MobileAiApp[]>('/api/mobile/apps')
  return data
}
