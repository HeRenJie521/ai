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
