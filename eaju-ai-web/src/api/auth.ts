import http from './http'

export interface LoginPayload {
  phone: string
  password: string
}

export interface LoginResult {
  token: string
  jti: string
  expiresIn: number
  userId: string
  phone: string
  username: string
  admin: boolean
  /** WEB_EMBED 登录时返回，用于聊天页自动选中该模型 */
  defaultModel?: string | null
  /** WEB_EMBED 登录时返回集成名称，用于 AI 助手标题 */
  integrationName?: string | null
}

export async function loginApi(payload: LoginPayload): Promise<LoginResult> {
  const { data } = await http.post<Record<string, unknown>>('/api/auth/login', payload)
  const uid = String(data.userId ?? data.phone ?? '')
  return {
    token: String(data.token ?? ''),
    jti: String(data.jti ?? ''),
    expiresIn: Number(data.expiresIn ?? 0) || 0,
    userId: uid,
    phone: String(data.phone ?? uid),
    username: String(data.username ?? uid),
    admin: data.admin === true || data.isAdmin === true,
  }
}

export async function logoutApi(): Promise<void> {
  await http.post('/api/auth/logout')
}

export interface EmbedLoginPayload {
  integrationId: number
  userId: string
  token: string
  username?: string
}

/**
 * 嵌入网站 SSO 免密登录（WEB_EMBED 集成方式）。
 * 参数由集成方后端计算签名后注入 iframe URL，前端解析 URL 参数后调用此接口。
 */
export async function embedLoginApi(payload: EmbedLoginPayload): Promise<LoginResult> {
  const { data } = await http.post<Record<string, unknown>>('/api/embed/login', payload)
  const uid = String(data.userId ?? data.phone ?? '')
  return {
    token: String(data.token ?? ''),
    jti: String(data.jti ?? ''),
    expiresIn: Number(data.expiresIn ?? 0) || 0,
    userId: uid,
    phone: String(data.phone ?? uid),
    username: String(data.username ?? uid),
    admin: data.admin === true,
    defaultModel: data.defaultModel ? String(data.defaultModel) : null,
    integrationName: data.integrationName ? String(data.integrationName) : null,
  }
}

export interface AppEmbedLoginPayload {
  appId: number
  userId: string
  username?: string
  extraContext?: Record<string, unknown>
}

/**
 * 应用管理嵌入登录（应用直接嵌入方式）。
 * 通过 URL 参数 aid/uid/username 无需签名直接登录，JWT 中携带 appId。
 */
export async function appEmbedLoginApi(payload: AppEmbedLoginPayload): Promise<LoginResult> {
  const { data } = await http.post<Record<string, unknown>>('/api/embed/app-login', payload)
  const uid = String(data.userId ?? data.phone ?? '')
  return {
    token: String(data.token ?? ''),
    jti: String(data.jti ?? ''),
    expiresIn: Number(data.expiresIn ?? 0) || 0,
    userId: uid,
    phone: String(data.phone ?? uid),
    username: String(data.username ?? uid),
    admin: data.admin === true,
    defaultModel: data.defaultModel ? String(data.defaultModel) : null,
    integrationName: data.integrationName ? String(data.integrationName) : null,
  }
}
