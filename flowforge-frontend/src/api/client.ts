import axios, { type AxiosInstance } from 'axios'
import { ElMessage } from 'element-plus'

export const TOKEN_KEY = 'ff_token'

/**
 * 统一请求封装。
 *
 * 后端所有响应都是 `{ code, message, data }`：`code === '0'` 才算成功，
 * 因此这里把「HTTP 200 但业务失败」也转成 reject，业务代码只需要关心 data。
 */
const client: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

client.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code !== '0') {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body?.data
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message

    if (status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      // 用 location 而不是路由实例，避免 client ↔ router 的循环依赖
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
      ElMessage.error('登录已过期，请重新登录')
    } else if (status === 403) {
      ElMessage.error(message || '没有权限执行该操作')
    } else if (status === 404) {
      ElMessage.error(message || '资源不存在')
    } else {
      ElMessage.error(message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  },
)

export default client
