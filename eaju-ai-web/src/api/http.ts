import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 120000,
})

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (r) => r,
  (err) => {
    if (err.response?.status === 401) {
      useAuthStore().logout()
      const path = window.location.pathname
      // 嵌入页面不跳转到登录页（iframe 内不适合全页重定向）
      if (!path.startsWith('/login') && !path.startsWith('/embed')) {
        window.location.href = '/login'
      }
    }
    return Promise.reject(err)
  },
)

export default http
