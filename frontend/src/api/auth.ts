import { api } from './http'
import type { College, LoginResponse, UserInfo } from './types'

export interface RegisterPayload {
  username: string
  email: string
  password: string
  collegeId: number
}

/** 见《概要设计》§5.1；密码找回两个接口见 §6.6 */
export const authApi = {
  login: (username: string, password: string) =>
    api.post<LoginResponse>('/auth/login', { username, password }),

  register: (payload: RegisterPayload) => api.post<UserInfo>('/auth/register', payload),

  me: () => api.get<UserInfo>('/auth/me'),

  logout: () => api.post<null>('/auth/logout'),

  /** 邮箱不论是否存在都返回成功，前端文案写死（防枚举，§6.6） */
  forgotPassword: (email: string) => api.post<null>('/auth/password/forgot', { email }),

  resetPassword: (token: string, newPassword: string) =>
    api.post<null>('/auth/password/reset', { token, newPassword }),

  colleges: () => api.get<College[]>('/colleges'),
}
