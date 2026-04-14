import http from './http'

const MAX_FILE_SIZE = 5 * 1024 * 1024 // 5 MB

/** 经后端代理上传到 eaju-open，返回公网 URL */
export async function uploadChatFile(file: File): Promise<string> {
  if (file.size > MAX_FILE_SIZE) {
    throw new Error(`文件大小不能超过 5 MB，当前：${(file.size / 1024 / 1024).toFixed(1)} MB`)
  }
  const form = new FormData()
  form.append('file', file)
  const { data } = await http.post<{ url: string }>('/api/file/upload', form, {
    timeout: 120000,
  })
  if (!data?.url) {
    throw new Error('上传返回无 url')
  }
  return data.url
}
