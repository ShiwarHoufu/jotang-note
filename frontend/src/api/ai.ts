import { getToken } from '@/utils/token'

import { api, NETWORK_ERROR, raiseApiError } from './http'
import { ErrorCode, type AiChatHistoryResponse, type AiChatResponse, type AiSummaryResponse, type ApiResponse } from './types'

/** 流式对话的事件回调；错误提示已在 streamChat 里统一弹出，这里只做界面清理 */
export interface ChatStreamHandlers {
  onStart(conversationId: string): void
  onDelta(text: string): void
  onDone(conversationId: string): void
  onError(): void
}

/** 与后端 ChatStreamEvent 对应 */
interface ChatStreamPayload {
  type: string
  text?: string | null
  conversationId?: string | null
  code?: number | null
  message?: string | null
}

/**
 * 流式对话。EventSource 带不了 Authorization，所以用 fetch 自己按 SSE 分帧。
 * 事件协议见 ChatController#chatStream。
 */
async function streamChat(
  message: string,
  conversationId: string | undefined,
  signal: AbortSignal,
  handlers: ChatStreamHandlers,
): Promise<void> {
  const base = import.meta.env.VITE_API_BASE_URL ?? '/api'
  let res: Response
  try {
    res = await fetch(`${base}/ai/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${getToken() ?? ''}`,
      },
      body: JSON.stringify({ message, conversationId }),
      signal,
    })
  } catch (e) {
    if (signal.aborted) {
      throw e
    }
    throw raiseApiError(NETWORK_ERROR, '网络异常，请检查网络后重试')
  }

  // 流开始前的失败仍是 JSON：42900 / 40400 / 40100 都走这里
  if (!res.ok || !res.body) {
    const body = (await res.json().catch(() => null)) as ApiResponse<unknown> | null
    throw raiseApiError(body?.code ?? NETWORK_ERROR, body?.message ?? '请求失败', body?.data)
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let finished = false
  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) {
        break
      }
      buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n')
      let index: number
      while ((index = buffer.indexOf('\n\n')) >= 0) {
        const frame = buffer.slice(0, index)
        buffer = buffer.slice(index + 2)
        const event = /^event:(.*)$/m.exec(frame)?.[1]?.trim()
        const data = frame
          .split('\n')
          .filter((line) => line.startsWith('data:'))
          .map((line) => line.slice(5))
          .join('\n')
        if (!event || !data) {
          continue
        }

        const payload = JSON.parse(data) as ChatStreamPayload
        if (event === 'start' && payload.conversationId) {
          handlers.onStart(payload.conversationId)
        } else if (event === 'delta' && payload.text) {
          handlers.onDelta(payload.text)
        } else if (event === 'done' && payload.conversationId) {
          finished = true
          handlers.onDone(payload.conversationId)
        } else if (event === 'error') {
          finished = true
          raiseApiError(payload.code ?? ErrorCode.SERVER_ERROR, payload.message ?? '回答生成失败，请重试')
          handlers.onError()
          return
        }
      }
    }
  } finally {
    reader.releaseLock()
  }

  // 连接提前关闭、始终没收到 done：按网络中断处理
  if (!finished && !signal.aborted) {
    raiseApiError(NETWORK_ERROR, '连接中断，请重试')
    handlers.onError()
  }
}

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

  /** 一问一答，SSE 流式 */
  chatStream: streamChat,

  /** 恢复最近一次会话；一条会话都没有时回 { conversationId: null, messages: [] }，不是错误 */
  latest: () => api.get<AiChatHistoryResponse>('/ai/chat/latest'),
}
