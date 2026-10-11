import { defineStore } from 'pinia'
import { ref } from 'vue'

import { catalogApi } from '@/api/catalog'
import type { Course, Tag } from '@/api/types'

/**
 * 课程与标签的全局缓存：筛选栏和上传 / 编辑表单共用同一份数据，
 * 避免每进一个页面都拉一次目录接口。数据是预置的字典，会话内不失效
 */
export const useCatalogStore = defineStore('catalog', () => {
  const courses = ref<Course[]>([])
  const tags = ref<Tag[]>([])
  /** 并发触发时只发一次请求 */
  let loading: Promise<void> | null = null

  async function ensureLoaded(): Promise<void> {
    if (courses.value.length > 0) {
      return
    }
    loading ??= Promise.all([catalogApi.courses(), catalogApi.tags()])
      .then(([courseList, tagList]) => {
        courses.value = courseList
        tags.value = tagList
      })
      .finally(() => {
        loading = null
      })
    await loading
  }

  return { courses, tags, ensureLoaded }
})
