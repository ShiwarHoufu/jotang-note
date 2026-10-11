import { api } from './http'
import type { UserInfo } from './types'

export interface UpdateProfilePayload {
  nickname: string
  collegeId: number
}

/** 个人资料接口（§5.2）。两个接口都回 UserInfo，前端拿去直接覆盖 Pinia 里的用户 */
export const userApi = {
  updateProfile: (payload: UpdateProfilePayload) => api.put<UserInfo>('/users/me', payload),

  uploadAvatar: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api.post<UserInfo>('/users/me/avatar', form)
  },
}
