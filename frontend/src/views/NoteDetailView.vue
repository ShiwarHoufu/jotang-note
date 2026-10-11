<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { adminApi } from '@/api/admin'
import { favoritesApi } from '@/api/favorites'
import { notesApi } from '@/api/notes'
import type { NoteDetail } from '@/api/types'
import NotePreview from '@/components/NotePreview.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { useAuthStore } from '@/stores/auth'
import { formatSize, formatTime } from '@/utils/format'

const route = useRoute()
const auth = useAuthStore()

const noteId = computed(() => Number(route.params.id))
const detail = ref<NoteDetail | null>(null)
const loadFailed = ref(false)
const loading = ref(false)

const favorited = ref(false)
const favoriteCount = ref(0)
const acting = ref(false)

const isOwner = computed(
  () => detail.value !== null && auth.user !== null && detail.value.uploader.id === auth.user.id,
)
const isOnline = computed(() => detail.value?.status === 'ONLINE')

async function load() {
  // 路由参数不是数字（比如误入 /notes/xxx）就不打请求，直接落错误态
  if (Number.isNaN(noteId.value)) {
    loadFailed.value = true
    return
  }
  loading.value = true
  loadFailed.value = false
  try {
    detail.value = await notesApi.detail(noteId.value)
    favorited.value = detail.value.isFavorited
    favoriteCount.value = detail.value.favoriteCount
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

watch(noteId, load, { immediate: true })

async function toggleFavorite() {
  if (!detail.value) {
    return
  }
  acting.value = true
  try {
    const res = favorited.value
      ? await favoritesApi.unfavorite(detail.value.id)
      : await favoritesApi.favorite(detail.value.id)
    favorited.value = !favorited.value
    favoriteCount.value = res.favoriteCount
  } finally {
    acting.value = false
  }
}

/** 签发即计数（D2）：拿到 URL 后新开下载，URL 是短时效的一次性票据 */
async function download() {
  if (!detail.value) {
    return
  }
  const res = await notesApi.downloadUrl(detail.value.id)
  window.open(res.url)
}

async function remove() {
  if (!detail.value) {
    return
  }
  await ElMessageBox.confirm('删除后其他人将无法看到这篇笔记，确定删除？', '删除笔记', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await notesApi.delete(detail.value.id)
  ElMessage.success('已删除')
  await load()
}

async function adminToggle() {
  if (!detail.value) {
    return
  }
  const offline = detail.value.status === 'ONLINE'
  if (offline) {
    await ElMessageBox.confirm('下架后所有用户将无法预览、下载与收藏这篇笔记。', '下架笔记', {
      confirmButtonText: '下架',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await adminApi.offline(detail.value.id)
    ElMessage.success('已下架')
  } else {
    await adminApi.restore(detail.value.id)
    ElMessage.success('已恢复上架')
  }
  await load()
}
</script>

<template>
  <div v-loading="loading" class="detail">
    <el-result v-if="loadFailed" icon="warning" title="笔记不存在或已被删除">
      <template #extra>
        <el-button type="primary" @click="$router.push({ name: 'home' })">回首页</el-button>
      </template>
    </el-result>

    <template v-else-if="detail">
      <el-alert
        v-if="detail.status === 'OFFLINE'"
        title="该笔记已被管理员下架，暂不可预览、下载与收藏"
        type="warning"
        :closable="false"
        show-icon
        class="banner"
      />
      <el-alert
        v-else-if="detail.status === 'DELETED'"
        title="该笔记已被删除"
        type="error"
        :closable="false"
        show-icon
        class="banner"
      />

      <div class="head">
        <h1 class="title">{{ detail.title }}</h1>
        <el-tag size="small" type="info" effect="plain">{{ detail.course.name }}</el-tag>
        <el-tag v-if="detail.teacher" size="small" effect="plain">{{ detail.teacher }}</el-tag>
      </div>

      <div class="meta">
        <div class="uploader">
          <UserAvatar :url="detail.uploader.avatarUrl" :name="detail.uploader.nickname" :size="24" />
          <span>{{ detail.uploader.nickname }}</span>
          <span v-if="detail.uploader.collegeName" class="college">{{ detail.uploader.collegeName }}</span>
        </div>
        <span class="stats">
          {{ detail.viewCount }} 浏览 · {{ detail.downloadCount }} 下载 · {{ favoriteCount }} 收藏 ·
          上传于 {{ formatTime(detail.createdAt) }} · 更新于 {{ formatTime(detail.updatedAt) }}
        </span>
      </div>

      <div v-if="detail.tags.length" class="tags">
        <el-tag v-for="tag in detail.tags" :key="tag.id" size="small" type="primary" effect="light">
          {{ tag.name }}
        </el-tag>
      </div>

      <p v-if="detail.summary" class="summary">{{ detail.summary }}</p>

      <div class="actions">
        <el-button
          v-if="isOnline"
          :type="favorited ? 'warning' : 'primary'"
          plain
          :loading="acting"
          @click="toggleFavorite"
        >
          {{ favorited ? '已收藏' : '收藏' }}
        </el-button>
        <el-button v-if="isOnline && detail.file" type="primary" @click="download">下载</el-button>
        <template v-if="isOwner">
          <el-button
            v-if="detail.status !== 'DELETED'"
            @click="$router.push({ name: 'note-edit', params: { id: detail.id } })"
          >
            编辑
          </el-button>
          <el-button v-if="detail.status !== 'DELETED'" type="danger" plain @click="remove">删除</el-button>
        </template>
        <template v-if="auth.isAdmin && detail.status !== 'DELETED'">
          <el-button v-if="detail.status === 'ONLINE'" type="danger" plain @click="adminToggle">
            下架
          </el-button>
          <el-button v-else type="success" plain @click="adminToggle">恢复上架</el-button>
        </template>
      </div>

      <el-card v-if="isOnline && detail.file" class="file-card" shadow="never">
        <template #header>
          <div class="file-head">
            <span class="file-name">{{ detail.file.originalName }}</span>
            <span class="file-size">{{ formatSize(detail.file.size) }}</span>
          </div>
        </template>
        <NotePreview :note-id="detail.id" :file="detail.file" />
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.detail {
  max-width: 860px;
  margin: 0 auto;
  min-height: 300px;
}

.banner {
  margin-bottom: 16px;
}

.head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.title {
  margin: 0;
  font-size: 22px;
  word-break: break-all;
}

.meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.uploader {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #606266;
  font-size: 14px;
}

.college {
  color: #909399;
  font-size: 13px;
}

.stats {
  font-size: 12px;
  color: #909399;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.summary {
  margin: 16px 0 0;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 4px;
  color: #606266;
  line-height: 1.7;
  white-space: pre-wrap;
}

.actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}

.file-card {
  margin-top: 16px;
}

.file-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.file-name {
  font-weight: 600;
  word-break: break-all;
}

.file-size {
  color: #909399;
  font-size: 13px;
  white-space: nowrap;
}
</style>
