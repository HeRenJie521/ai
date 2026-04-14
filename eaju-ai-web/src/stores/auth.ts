import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { LoginResult } from '@/api/auth'
import { logoutApi } from '@/api/auth'

const LS_TOKEN = 'eaju_token'
const LS_USER = 'eaju_username'
const LS_UID = 'eaju_userId'
const LS_ADMIN = 'eaju_isAdmin'
const LS_SNAPSHOT = 'eaju_login_snapshot'

export interface LoginCachePayload {
  token: string
  jti: string
  expiresIn: number
  userId: string
  phone: string
  username: string
  admin: boolean
  cachedAt: number
}

function readSnapshot(): LoginCachePayload | null {
  const raw = localStorage.getItem(LS_SNAPSHOT)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as LoginCachePayload
  } catch {
    return null
  }
}

function persistSnapshotFromLogin(res: LoginResult) {
  const payload: LoginCachePayload = {
    token: res.token,
    jti: res.jti,
    expiresIn: res.expiresIn,
    userId: res.userId,
    phone: res.phone,
    username: res.username,
    admin: res.admin,
    cachedAt: Date.now(),
  }
  localStorage.setItem(LS_SNAPSHOT, JSON.stringify(payload))
}

export const useAuthStore = defineStore('auth', () => {
  const snap = readSnapshot()
  const token = ref(snap?.token || localStorage.getItem(LS_TOKEN) || '')
  const username = ref(snap?.username || localStorage.getItem(LS_USER) || '')
  const userId = ref(snap?.userId || localStorage.getItem(LS_UID) || '')
  const isAdmin = ref(
    snap ? !!snap.admin : localStorage.getItem(LS_ADMIN) === 'true',
  )

  const isLoggedIn = computed(() => !!token.value)

  function setFromLogin(res: LoginResult) {
    token.value = res.token
    username.value = res.username
    userId.value = res.userId
    isAdmin.value = res.admin
    localStorage.setItem(LS_TOKEN, res.token)
    localStorage.setItem(LS_USER, res.username)
    localStorage.setItem(LS_UID, res.userId)
    localStorage.setItem(LS_ADMIN, String(res.admin))
    persistSnapshotFromLogin(res)
  }

  function logout() {
    token.value = ''
    username.value = ''
    userId.value = ''
    isAdmin.value = false
    localStorage.removeItem(LS_TOKEN)
    localStorage.removeItem(LS_USER)
    localStorage.removeItem(LS_UID)
    localStorage.removeItem(LS_ADMIN)
    localStorage.removeItem(LS_SNAPSHOT)
  }

  async function logoutRemote() {
    try {
      if (token.value) {
        await logoutApi()
      }
    } catch {
      /* 仍清理本地 */
    } finally {
      logout()
    }
  }

  function getLoginSnapshot(): LoginCachePayload | null {
    return readSnapshot()
  }

  return {
    token,
    username,
    userId,
    isAdmin,
    isLoggedIn,
    setFromLogin,
    logout,
    logoutRemote,
    getLoginSnapshot,
  }
})
