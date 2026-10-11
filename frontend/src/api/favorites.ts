import { api } from './http'
import type { FavoriteCountResponse, FavoriteItem, PageResponse } from './types'

export interface FavoriteListParams {
  page?: number
  size?: number
}

/**
 * 收藏接口（§5.5）。收藏返回最新收藏量直接刷新计数；
 * 取消收藏幂等，已下架 / 已删除的笔记也允许取消（不然占位项清不掉）
 */
export const favoritesApi = {
  favorite: (noteId: number) =>
    api.post<FavoriteCountResponse>(`/notes/${noteId}/favorite`),

  unfavorite: (noteId: number) =>
    api.delete<FavoriteCountResponse>(`/notes/${noteId}/favorite`),

  list: (params?: FavoriteListParams) =>
    api.get<PageResponse<FavoriteItem>>('/users/me/favorites', params),
}
