import { api } from './http'

/**
 * 管理员笔记下架 / 恢复（§5.6）。非管理员调用会被过滤器链直接拒（40100 / 40300）。
 * 幂等：已是目标状态时返回 200 什么都不做；不能复活已删除的笔记（40301）
 */
export const adminApi = {
  offline: (noteId: number) => api.post<null>(`/admin/notes/${noteId}/offline`),

  restore: (noteId: number) => api.post<null>(`/admin/notes/${noteId}/online`),
}
