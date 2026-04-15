import http from './http'

export interface WelcomeConfig {
  welcomeText: string | null
  suggestions: string[] | null
}

/**
 * 获取开场引导配置
 * @param integrationId 集成 ID
 */
export async function getWelcomeConfig(integrationId: number): Promise<WelcomeConfig> {
  const { data } = await http.get<WelcomeConfig>('/api/chat/welcome', {
    params: { id: integrationId },
  })
  return data
}
