import axios, {
  AxiosInstance,
  AxiosRequestConfig,
  AxiosResponse,
  InternalAxiosRequestConfig
} from 'axios'
import { useUserStore } from '@/store/modules/user'

const LOGIN_PATH = '/login'
const AUTH_EXPIRED_MESSAGE = '\u767b\u5f55\u72b6\u6001\u5df2\u5931\u6548\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55'
const FORBIDDEN_MESSAGE = '\u6ca1\u6709\u6743\u9650\u8bbf\u95ee\u8be5\u8d44\u6e90'
const REQUEST_FAILED_MESSAGE = '\u8bf7\u6c42\u5931\u8d25'

let redirectingToLogin = false

const instance: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
  transformRequest: [
    (data, headers) => {
      if (data instanceof FormData) {
        if (headers) {
          delete headers['Content-Type']
        }
        return data
      }
      if (typeof data === 'object' && data !== null) {
        return JSON.stringify(data)
      }
      return data
    },
  ],
})

const getToken = (): string => {
  const userStore = useUserStore()
  return userStore.token || localStorage.getItem('token') || ''
}

const redirectToLogin = (): void => {
  if (redirectingToLogin || window.location.pathname === LOGIN_PATH) {
    return
  }

  redirectingToLogin = true
  const currentPath = `${window.location.pathname}${window.location.search}`
  const redirectQuery = currentPath && currentPath !== LOGIN_PATH
    ? `?redirect=${encodeURIComponent(currentPath)}`
    : ''
  window.location.href = `${LOGIN_PATH}${redirectQuery}`
}

const rejectAuthExpired = (message?: string): Promise<never> => {
  const userStore = useUserStore()
  userStore.clearUserInfo()
  redirectToLogin()
  return Promise.reject(new Error(message || AUTH_EXPIRED_MESSAGE))
}

instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

instance.interceptors.response.use(
  (response: AxiosResponse) => {
    const data = response.data

    if (data?.code === 200) {
      return Object.prototype.hasOwnProperty.call(data, 'data') ? data.data : data
    }

    if (data?.code === 401) {
      return rejectAuthExpired(data.message)
    }

    if (data?.code === 403) {
      return Promise.reject(new Error(data.message || FORBIDDEN_MESSAGE))
    }

    if (typeof data?.code !== 'undefined') {
      return Promise.reject(new Error(data.message || REQUEST_FAILED_MESSAGE))
    }

    return data
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message

    if (status === 401) {
      return rejectAuthExpired(message)
    }

    if (status === 403) {
      return Promise.reject(new Error(message || FORBIDDEN_MESSAGE))
    }

    return Promise.reject(error)
  }
)

const http = {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.get(url, config) as unknown as Promise<T>
  },
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.delete(url, config) as unknown as Promise<T>
  },
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.post(url, data, config) as unknown as Promise<T>
  },
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.put(url, data, config) as unknown as Promise<T>
  },
  patch<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.patch(url, data, config) as unknown as Promise<T>
  },
}

export default http
