import { api } from './http'
import type { AiChatHistoryResponse, AiChatResponse, AiSummaryResponse } from './types'

/**
 * AI 接口（§5.8）。42900（限频烧钱护栏）与 50300（上游不可用）都在
 * FORM_LEVEL_CODES 之外，http 层会弹全局提示，调用方无需重复处理
 */
export const aiApi = {
  /** 上传场景：把待上传的图片或文本交给模型（图片 base64 内联、文本直接进请求体，都不走签名 URL）。
   *  title / teacher 为可选上下文 */
  summarizeUpload: (file: File, title?: string, teacher?: string) => {
    const form = new FormData()
    form.append('file', file)
    if (title) {
      form.append('title', title)
    }
    if (teacher) {
      form.append('teacher', teacher)
    }
    return api.post<AiSummaryResponse>('/ai/summary', form)
  },

  /** 编辑场景：按已有笔记的附件生成摘要 */
  summarizeNote: (noteId: number, title?: string, teacher?: string) => {
    const query = new URLSearchParams()
    if (title) {
      query.set('title', title)
    }
    if (teacher) {
      query.set('teacher', teacher)
    }
    const qs = query.toString()
    return api.post<AiSummaryResponse>(`/ai/summary/notes/${noteId}${qs ? `?${qs}` : ''}`)
  },

  /** 一问一答；conversationId 传上一轮返回值即延续会话，留空开新会话 */
  chat: (message: string, conversationId?: string) =>
    api.post<AiChatResponse>('/ai/chat', { message, conversationId }),

  /** 恢复最近一次会话；一条会话都没有时回 { conversationId: null, messages: [] }，不是错误 */
  latest: () => api.get<AiChatHistoryResponse>('/ai/chat/latest'),
}
