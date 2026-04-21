import http from './http'

export interface AdminAccountRow {
  id: number
  phone: string
  name: string
  enabled: boolean
  createdAt: number
  updatedAt: number
}

export interface AdminAccountSavePayload {
  phone: string
  name: string
  enabled: boolean
}

export async function adminListAdminAccounts(): Promise<AdminAccountRow[]> {
  const { data } = await http.get<AdminAccountRow[]>('/api/admin/admin-accounts')
  return data
}

export async function adminCreateAdminAccount(payload: AdminAccountSavePayload): Promise<AdminAccountRow> {
  const { data } = await http.post<AdminAccountRow>('/api/admin/admin-accounts', payload)
  return data
}

export async function adminUpdateAdminAccount(id: number, payload: AdminAccountSavePayload): Promise<AdminAccountRow> {
  const { data } = await http.put<AdminAccountRow>(`/api/admin/admin-accounts/${id}`, payload)
  return data
}

export async function adminDeleteAdminAccount(id: number): Promise<void> {
  await http.delete(`/api/admin/admin-accounts/${id}`)
}

export async function adminBatchDeleteAdminAccounts(ids: number[]): Promise<void> {
  await http.post('/api/admin/admin-accounts/batch-delete', ids)
}
