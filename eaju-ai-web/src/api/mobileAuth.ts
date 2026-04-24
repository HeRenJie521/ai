import http from './http'
import type { LoginResult } from './auth'

export async function mobileLoginApi(phone: string): Promise<LoginResult> {
  const { data } = await http.post<Record<string, unknown>>('/api/mobile/login', { phone })
  const uid = String(data.userId ?? data.phone ?? '')
  return {
    token: String(data.token ?? ''),
    jti: String(data.jti ?? ''),
    expiresIn: Number(data.expiresIn ?? 0) || 0,
    userId: uid,
    phone: String(data.phone ?? uid),
    username: String(data.username ?? uid),
    admin: data.admin === true,
  }
}
