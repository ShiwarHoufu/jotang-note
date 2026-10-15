import { api, rawHttp } from './http'
import type {
  MyNoteItem,
  NoteDetail,
  NoteListItem,
  NoteSort,
  PageResponse,
  PresignedUrlResponse,
} from './types'

export interface NoteListParams {
  courseId?: number
  tagId?: number
  /** 标题关键词。空串与纯空白都当没搜，不上送 */
  keyword?: string
  sort?: NoteSort
  page?: number
  size?: number
}

export interface NoteEditPayload {
  title: string
  summary?: string
  teacher?: string
  courseId: number
  tags?: string[]
}

export interface NoteUploadPayload extends NoteEditPayload {
  file: File
}

export interface MyNotesParams {
  page?: number
  size?: number
}

/**
 * 笔记接口（§5.4）。上传 / 编辑后没有回显体，前端自行跳详情页重新拉取。
 * preview-url / raw / download 三个预览接口见 §6.2，后端签名 URL 短时效，用一次取一次，
 * 不缓存。
 */
export const notesApi = {
  list: (params?: NoteListParams) => api.get<PageResponse<NoteListItem>>('/notes', params),

  detail: (id: number) => api.get<NoteDetail>(`/notes/${id}`),

  /** multipart 表单：文件 + 元数据（§6.1） */
  upload: (payload: NoteUploadPayload) => {
    const form = new FormData()
    form.append('file', payload.file)
    form.append('title', payload.title)
    form.append('courseId', String(payload.courseId))
    if (payload.summary) {
      form.append('summary', payload.summary)
    }
    if (payload.teacher) {
      form.append('teacher', payload.teacher)
    }
    for (const tag of payload.tags ?? []) {
      form.append('tags', tag)
    }
    return api.post<{ id: number }>('/notes', form)
  },

  /** JSON 全量替换（§5.4）：没传的元数据字段等于清空 */
  update: (id: number, payload: NoteEditPayload) => api.put<null>(`/notes/${id}`, payload),

  delete: (id: number) => api.delete<null>(`/notes/${id}`),

  myNotes: (params?: MyNotesParams) =>
    api.get<PageResponse<MyNoteItem>>('/users/me/notes', params),

  /** 图片预览：签发的内联签名 URL，前端 <img> 直出 */
  previewUrl: (id: number) => api.get<PresignedUrlResponse>(`/notes/${id}/preview-url`),

  /** MD / 文本原文，后端代理返回 text/plain；不走 ApiResponse 解包，所以用裸实例 */
  raw: async (id: number) => {
    const res = await rawHttp.get<string>(`/notes/${id}/raw`, { responseType: 'text' })
    return res.data
  },

  /** 下载：签发附件形式签名 URL，服务端同时 download_count+1（近似口径 D2） */
  downloadUrl: (id: number) => api.get<PresignedUrlResponse>(`/notes/${id}/download`),
}
