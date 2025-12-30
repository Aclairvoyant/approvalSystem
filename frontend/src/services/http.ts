import axios, { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { useUserStore } from '@/store/modules/user'

const instance: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
  // 对于FormData，axios会自动设置正确的Content-Type
  transformRequest: [
    (data, headers) => {
      if (data instanceof FormData) {
        // 删除预设的Content-Type，让浏览器自动设置（包含boundary）
        if (headers) {
          delete headers['Content-Type']
        }
        return data
      }
      // 对于普通对象，转换为JSON
      if (typeof data === 'object' && data !== null) {
        return JSON.stringify(data)
      }
      return data
    },
  ],
})

// 请求拦截器
instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
instance.interceptors.response.use(
  (response: AxiosResponse) => {
    const data = response.data
    if (data.code === 200) {
      return data.data || data
    } else if (data.code === 401) {
      const userStore = useUserStore()
      userStore.clearUserInfo()
      window.location.href = '/login'
      return Promise.reject(new Error(data.message || '认证失败'))
    } else {
      return Promise.reject(new Error(data.message || '请求失败'))
    }
  },
  (error) => {
    // 处理 403 错误
    if (error.response?.status === 403) {
      console.error('403 Forbidden:', error.response.data)
      return Promise.reject(new Error('权限被拒绝，请检查服务器配置'))
    }
    return Promise.reject(error)
  }
)

export default instance
