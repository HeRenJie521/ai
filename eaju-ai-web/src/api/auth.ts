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
}

/**
 * 嵌入网站 SSO 免密登录。
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
  }
}
