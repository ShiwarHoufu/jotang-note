<script setup lang="ts">
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { ref, watch } from 'vue'

import { notesApi } from '@/api/notes'
import type { NoteFileInfo } from '@/api/types'

/**
 * 按 previewMode 渲染文件内容（§6.2）：
 * - IMAGE_INLINE：后端签发的内联签名 URL，<img> 直出
 * - TEXT_PROXY：后端 /raw 代理拿原文，marked 渲染后必须过 DOMPurify 再 v-html（§8.3）
 * - DOWNLOAD_ONLY：不渲染，只留下载入口（由父级提供）
 */
const props = defineProps<{ noteId: number; file: NoteFileInfo }>()

const imageUrl = ref('')
const html = ref('')
const failed = ref(false)
const loading = ref(false)

async function load() {
  loading.value = true
  failed.value = false
  try {
    if (props.file.previewMode === 'IMAGE_INLINE') {
      const res = await notesApi.previewUrl(props.noteId)
      imageUrl.value = res.url
    } else if (props.file.previewMode === 'TEXT_PROXY') {
      const raw = await notesApi.raw(props.noteId)
      // 先转 Markdown 再消毒：DOMPurify 过滤脚本与危险属性，用户输入只经这一条路进 DOM
      html.value = DOMPurify.sanitize(await marked.parse(raw))
    }
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

watch(() => [props.noteId, props.file.previewMode], load, { immediate: true })
</script>

<template>
  <div v-loading="loading" class="preview">
    <template v-if="!failed">
      <img v-if="file.previewMode === 'IMAGE_INLINE' && imageUrl" :src="imageUrl" alt="笔记预览" class="image" />
      <!-- eslint-disable-next-line vue/no-v-html -- 内容已过 DOMPurify（§8.3 唯一放行路径） -->
      <div v-else-if="file.previewMode === 'TEXT_PROXY' && html" class="markdown-body" v-html="html" />
    </template>
    <el-alert v-else type="warning" :closable="false" show-icon title="预览加载失败，可以尝试直接下载" />
  </div>
</template>

<style scoped>
.preview {
  min-height: 80px;
}

.image {
  max-width: 100%;
  display: block;
  margin: 0 auto;
  border-radius: var(--jt-radius-sm);
}
</style>
