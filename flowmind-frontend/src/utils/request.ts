import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
const client = axios.create({ baseURL: '/api/v1', timeout: 20000 })
client.interceptors.request.use(config => {
  const token = localStorage.getItem('flowmind.token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
client.interceptors.response.use(response => response, error => {
  const status = error.response?.status
  if (status === 401) {
    localStorage.removeItem('flowmind.token'); localStorage.removeItem('flowmind.user')
    if (location.pathname !== '/login') location.assign(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`)
  } else if (status === 403 && location.pathname !== '/403') location.assign('/403')
  ElMessage.error(error.response?.data?.message ?? (error.code === 'ECONNABORTED' ? '请求超时，请重试' : '无法连接服务器，请稍后重试'))
  return Promise.reject(error)
})
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await client.request<{ code: number; message: string; data: T; timestamp: number }>(config)
  if (response.data.code !== 200) { ElMessage.error(response.data.message); throw new Error(response.data.message) }
  return response.data.data
}
export default client
