import http from './http'

export interface WelcomeConfig {
  welcomeText: string | null
  suggestions: string[] | null
  /** AI 应用配置的默认模型 ID，供嵌入页面初始化模型选择 */
  modelId: string | null
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
