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
