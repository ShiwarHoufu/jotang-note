<script setup lang="ts">
import { ref } from 'vue'

import { favoritesApi } from '@/api/favorites'
import type { NoteListItem } from '@/api/types'
import { formatTime } from '@/utils/format'
import UserAvatar from '@/components/UserAvatar.vue'

/**
 * 收藏态用本地覆盖：列表翻页 / 重新查询会重建卡片，初始值重新取 props，
 * 避免把 props 塞进 ref 造成不同步
 */
const props = defineProps<{ note: NoteListItem }>()

const favorited = ref(props.note.isFavorited)
const favoriteCount = ref(props.note.favoriteCount)
const acting = ref(false)

/** 收藏 / 取消都拿响应里的最新计数直接覆盖，不再自己加减 */
async function toggleFavorite() {
  acting.value = true
  try {
    const res = favorited.value
      ? await favoritesApi.unfavorite(props.note.id)
      : await favoritesApi.favorite(props.note.id)
    favorited.value = !favorited.value
    favoriteCount.value = res.favoriteCount
  } finally {
    acting.value = false
  }
}
</script>

<template>
  <el-card class="note-card" shadow="hover" :body-style="{ padding: '16px' }">
    <div class="card-head">
      <router-link :to="{ name: 'note-detail', params: { id: props.note.id } }" class="title">
        {{ props.note.title }}
      </router-link>
      <el-tag size="small" type="primary" effect="light">{{ props.note.course.name }}</el-tag>
    </div>
    <div v-if="props.note.tags.length" class="tags">
      <el-tag v-for="tag in props.note.tags" :key="tag.id" size="small" type="info" effect="plain">
        {{ tag.name }}
      </el-tag>
    </div>
    <div class="uploader">
      <UserAvatar :url="props.note.uploader.avatarUrl" :name="props.note.uploader.nickname" :size="20" />
      <span class="uploader-name">{{ props.note.uploader.nickname }}</span>
      <span v-if="props.note.uploader.collegeName" class="uploader-college">{{ props.note.uploader.collegeName }}</span>
    </div>
    <div class="card-foot">
      <span class="stats">
        <span>{{ props.note.viewCount }} 浏览</span>
        <span>{{ props.note.downloadCount }} 下载</span>
        <span>{{ favoriteCount }} 收藏</span>
        <span class="time">更新于 {{ formatTime(props.note.updatedAt) }}</span>
      </span>
      <el-button link :type="favorited ? 'warning' : 'default'" :loading="acting" @click="toggleFavorite">
        {{ favorited ? '已收藏' : '收藏' }}
      </el-button>
    </div>
  </el-card>
</template>

<style scoped>
.note-card {
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.note-card:hover {
  transform: translateY(-2px);
}

.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title {
  font-size: 16px;
  font-weight: 600;
  color: var(--jt-ink, #1f2d4d);
  text-decoration: none;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.2s ease;
}

.title:hover {
  color: var(--jt-brand, #4f7cff);
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.uploader {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  font-size: 13px;
  color: #606266;
}

.uploader-college {
  color: #909399;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.stats {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}

.time {
  margin-left: 4px;
}
</style>
