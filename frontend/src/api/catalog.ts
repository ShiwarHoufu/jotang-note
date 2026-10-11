import { api } from './http'
import type { College, Course, Tag } from './types'

/** 目录类只读接口（§5.3）。colleges 也在 authApi 里有一份，注册页在用它 */
export const catalogApi = {
  colleges: () => api.get<College[]>('/colleges'),

  /** keyword 按课程名模糊匹配，不传返回全部 */
  courses: (keyword?: string) => api.get<Course[]>('/courses', keyword ? { keyword } : undefined),

  tags: () => api.get<Tag[]>('/tags'),
}
