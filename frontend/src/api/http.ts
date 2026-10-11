import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

import router from '@/router'
import { clearToken, getToken } from '@/utils/token'
import { ErrorCode, FORM_LEVEL_CODES, type ApiResponse } from './types'

/** 业务码非 0 时抛出，调用方按 code 分支，data 里可能是字段明细或剩余锁定秒数 */
export class ApiError extends Error {
  code: number
  data: unknown

  constructor(code: number, message: string, data?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.data = data
  }
}

/** 传输层失败（断网、超时、后端没起）统一用这个码，与后端业务码不重叠 */
export const NETWORK_ERROR = -1

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 30000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 40100 说明 token 已失效，清掉登录态并回登录页。
 * 用一个标志位去重：页面同时发多个请求时，只弹一次提示、只跳一次。
 */
let sessionExpiredHandled = false

function handleUnauthorized(): void {
  clearToken()
  if (sessionExpiredHandled || router.currentRoute.value.meta.public) {
    return
  }
  sessionExpiredHandled = true
  ElMessage.error('登录已过期，请重新登录')
  const redirect = router.currentRoute.value.fullPath
  router.push({ name: 'login', query: { redirect } }).finally(() => {
    sessionExpiredHandled = false
  })
}

function toApiError(code: number, message: string, data?: unknown): ApiError {
  if (code === ErrorCode.UNAUTHORIZED) {
    handleUnauthorized()
  } else if (!FORM_LEVEL_CODES.includes(code)) {
    ElMessage.error(message)
  }
  return new ApiError(code, message, data)
}

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body.code === ErrorCode.OK) {
      // 成功时把 ApiResponse 外壳剥掉，调用方直接拿 data
      return body.data as never
    }
    return Promise.reject(toApiError(body.code, body.message, body.data))
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    const body = error.response?.data
    if (body && typeof body.code === 'number') {
      return Promise.reject(toApiError(body.code, body.message, body.data))
    }
    if (error.code === AxiosError.ECONNABORTED) {
      ElMessage.error('请求超时，请稍后重试')
      return Promise.reject(new ApiError(NETWORK_ERROR, '请求超时'))
    }
    ElMessage.error('网络异常，请检查网络后重试')
    return Promise.reject(new ApiError(NETWORK_ERROR, '网络异常'))
  },
)

/** 拦截器已把响应剥成 data，这里的泛型是给调用方标注返回类型的 */
export const api = {
  get: <T>(url: string, params?: object, config?: AxiosRequestConfig) =>
    http.get(url, { params, ...config }) as unknown as Promise<T>,
  post: <T>(url: string, data?: unknown) => http.post(url, data) as unknown as Promise<T>,
  put: <T>(url: string, data?: unknown) => http.put(url, data) as unknown as Promise<T>,
  delete: <T>(url: string, config?: AxiosRequestConfig) =>
    http.delete(url, config) as unknown as Promise<T>,
}
