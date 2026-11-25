import { api } from './http'
import type { AdminNoteItem, PageResponse } from './types'

export interface AdminOfflineNotesParams {
  /** 标题关键词。空串与纯空白都当没搜，不上送 */
  keyword?: string
  page?: number
  size?: number
}

/**
 * 管理员笔记下架 / 恢复 / 已下架列表（§5.6）。非管理员调用会被过滤器链直接拒（40100 / 40300）。
 * 幂等：已是目标状态时返回 200 什么都不做；不能复活已删除的笔记（40301）
 */
export const adminApi = {
  offline: (noteId: number) => api.post<null>(`/admin/notes/${noteId}/offline`),

  restore: (noteId: number) => api.post<null>(`/admin/notes/${noteId}/online`),

  /** 已下架笔记分页，按最后编辑时间倒序；下架动作不记录时间 */
  offlineNotes: (params?: AdminOfflineNotesParams) =>
    api.get<PageResponse<AdminNoteItem>>('/admin/notes/offline', params),
}
