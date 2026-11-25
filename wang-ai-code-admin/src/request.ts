import axios from 'axios'

export const sessionKey = 'academiccraft-admin-session'

const request = axios.create({
  baseURL: import.meta.env.VITE_ADMIN_API_BASE_URL || '/api',
  timeout: 30000,
})

request.interceptors.request.use((config) => {
  const token = sessionStorage.getItem(sessionKey)
  if (token) config.headers.set('Authorization', `Bearer ${token}`)
  return config
})

request.interceptors.response.use((response) => {
  if (response.data?.code === 40100 && response.config.url !== '/admin/auth/login') {
    sessionStorage.removeItem(sessionKey)
    window.dispatchEvent(new Event('admin-session-expired'))
  }
  return response
})

type Response<T> = { code: number; data: T; message?: string }
export async function data<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  try {
    const response = await request<Response<T>>(path, { method, data: body })
    if (response.data.code !== 0) throw new Error(response.data.message || '操作失败，请重试')
    return response.data.data
  } catch (error) {
    if (axios.isAxiosError<Response<T>>(error)) {
      throw new Error(error.response?.data?.message || '连接管理服务失败，请稍后重试')
    }
    throw error
  }
}

export default request
