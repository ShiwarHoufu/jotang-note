/** 与后端 com.wlf.common.ApiResponse 对应 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 与后端 com.wlf.common.PageResponse 对应 */
export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
}

/** 参数校验失败时 data 里的字段明细 */
export interface FieldViolation {
  field: string
  message: string
}

/**
 * 业务码，与后端 com.wlf.common.ErrorCode 一一对应。
 * 取值见《概要设计》§5.7；前端只该按 code 分支，不去解析 message 文案。
 */
export const ErrorCode = {
  OK: 0,
  PARAM_INVALID: 40001,
  UNAUTHORIZED: 40100,
  BAD_CREDENTIALS: 40101,
  FORBIDDEN: 40300,
  NOTE_UNAVAILABLE: 40301,
  NOT_FOUND: 40400,
  USERNAME_TAKEN: 40901,
  ALREADY_FAVORITED: 40902,
  EMAIL_TAKEN: 40903,
  FILE_INVALID: 42200,
  ACCOUNT_LOCKED: 42300,
  AI_RATE_LIMITED: 42900,
  SERVER_ERROR: 50000,
  AI_UNAVAILABLE: 50300,
} as const

export type ErrorCodeValue = (typeof ErrorCode)[keyof typeof ErrorCode]

/** 这些码由表单就地展示，拦截器不再弹全局提示 */
export const FORM_LEVEL_CODES: readonly number[] = [
  ErrorCode.PARAM_INVALID,
  ErrorCode.BAD_CREDENTIALS,
  ErrorCode.USERNAME_TAKEN,
  ErrorCode.EMAIL_TAKEN,
  ErrorCode.ACCOUNT_LOCKED,
]

export interface UserInfo {
  id: number
  username: string
  nickname: string
  collegeId: number | null
  avatarUrl: string | null
  role: 'USER' | 'ADMIN'
}

export interface LoginResponse {
  token: string
  user: UserInfo
}

export interface College {
  id: number
  name: string
}

export interface Course {
  id: number
  name: string
}

export interface Tag {
  id: number
  name: string
}

/** 与后端 com.wlf.note.dto.UploaderResponse 对应；avatarUrl 已是完整公网地址 */
export interface Uploader {
  id: number
  nickname: string
  avatarUrl: string | null
  collegeName: string | null
}

/** 与后端 com.wlf.note.NoteStatus 枚举对应 */
export type NoteStatus = 'ONLINE' | 'OFFLINE' | 'DELETED'

/** 与后端 com.wlf.note.PreviewMode 枚举对应，决定详情页用哪种方式渲染文件 */
export type PreviewMode = 'IMAGE_INLINE' | 'TEXT_PROXY' | 'DOWNLOAD_ONLY'

export type NoteSort = 'LATEST' | 'HOT_VIEW' | 'HOT_DOWNLOAD'

/** 公开列表项（§5.4）：只有 ONLINE，无 status / file / summary */
export interface NoteListItem {
  id: number
  title: string
  course: Course
  uploader: Uploader
  tags: Tag[]
  viewCount: number
  downloadCount: number
  favoriteCount: number
  updatedAt: string
  isFavorited: boolean
}

/** 详情出参（§5.4）：非 ONLINE 时 file 为 null，status 原样返回由前端渲染提示 */
export interface NoteDetail {
  id: number
  title: string
  summary: string | null
  teacher: string | null
  status: NoteStatus
  course: Course
  uploader: Uploader
  tags: Tag[]
  viewCount: number
  downloadCount: number
  favoriteCount: number
  createdAt: string
  updatedAt: string
  file: NoteFileInfo | null
  isFavorited: boolean
}

export interface NoteFileInfo {
  originalName: string
  size: number
  contentType: string
  previewMode: PreviewMode
}

/** 「我的上传」项：无上传者信息（恒为本人），status 只会是 ONLINE / OFFLINE */
export interface MyNoteItem {
  id: number
  status: NoteStatus
  title: string
  course: Course
  tags: Tag[]
  viewCount: number
  downloadCount: number
  favoriteCount: number
  createdAt: string
}

/**
 * 收藏列表项（§5.5）：占位项保留 title / course，uploader 与计数为 null，
 * 「是不是占位」只需看 status
 */
export interface FavoriteItem {
  noteId: number
  status: NoteStatus
  favoritedAt: string
  title: string
  course: Course
  uploader: Uploader | null
  tags: Tag[]
  viewCount: number | null
  downloadCount: number | null
  favoriteCount: number | null
  updatedAt: string | null
}

/** /api/notes/{id}/preview-url 与 /download 的出参：签发好的 OSS 签名 URL */
export interface PresignedUrlResponse {
  url: string
}

/** /api/notes/{id}/favorite 的出参：操作后的最新收藏量 */
export interface FavoriteCountResponse {
  favoriteCount: number
}

/** AI 摘要（§5.8）：建议文案，已按 note.summary 列宽截断，直接回填输入框 */
export interface AiSummaryResponse {
  summary: string
}

/** AI 对话响应 */
export interface AiChatResponse {
  reply: string
  conversationId: string
}
