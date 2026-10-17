<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, ref, watch } from 'vue'

import { favoritesApi } from '@/api/favorites'
import type { FavoriteItem } from '@/api/types'
import UserAvatar from '@/components/UserAvatar.vue'
import { formatTime } from '@/utils/format'

const PAGE_SIZE = 20

const items = ref<FavoriteItem[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const actingId = ref<number | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await favoritesApi.list({ page: page.value, size: PAGE_SIZE })
    items.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

watch(page, load)
onMounted(load)

/** 占位项（已下架 / 已删除）不可打开详情，只有 ONLINE 才给链接 */
function isAvailable(item: FavoriteItem): boolean {
  return item.status === 'ONLINE'
}

/** 取消是幂等的，占位项也允许取消，不然它永远清不掉 */
async function unfavorite(item: FavoriteItem) {
  actingId.value = item.noteId
  try {
    await favoritesApi.unfavorite(item.noteId)
    ElMessage.success('已取消收藏')
    await load()
  } finally {
    actingId.value = null
  }
}
</script>

<template>
  <div class="page-body">
    <h1 class="page-title">我的收藏</h1>
    <div v-loading="loading" class="list">
      <template v-if="items.length">
        <el-card
          v-for="item in items"
          :key="item.noteId"
          shadow="hover"
          :body-style="{ padding: '16px' }"
        >
          <div class="card-head">
            <router-link
              v-if="isAvailable(item)"
              :to="{ name: 'note-detail', params: { id: item.noteId } }"
              class="title"
            >
              {{ item.title }}
            </router-link>
            <span v-else class="title disabled">{{ item.title }}</span>
            <el-tag size="small" type="primary" effect="light">{{ item.course.name }}</el-tag>
            <el-tag v-if="item.status === 'OFFLINE'" size="small" type="warning">已下架</el-tag>
            <el-tag v-else-if="item.status === 'DELETED'" size="small" type="danger">笔记已删除</el-tag>
          </div>
          <div v-if="isAvailable(item) && item.tags.length" class="tags">
            <el-tag v-for="tag in item.tags" :key="tag.id" size="small" type="info" effect="plain">
              {{ tag.name }}
            </el-tag>
          </div>
          <div v-if="isAvailable(item) && item.uploader" class="uploader">
            <UserAvatar :url="item.uploader.avatarUrl" :name="item.uploader.nickname" :size="20" />
            <span class="uploader-name">{{ item.uploader.nickname }}</span>
            <span v-if="item.uploader.collegeName" class="uploader-college">{{ item.uploader.collegeName }}</span>
          </div>
          <div class="card-foot">
            <span class="stats">
              <template v-if="isAvailable(item)">
                <span>{{ item.viewCount }} 浏览</span>
                <span>{{ item.downloadCount }} 下载</span>
                <span>{{ item.favoriteCount }} 收藏</span>
              </template>
              <span class="time">收藏于 {{ formatTime(item.favoritedAt) }}</span>
            </span>
            <el-button link :loading="actingId === item.noteId" @click="unfavorite(item)">
              取消收藏
            </el-button>
          </div>
        </el-card>
      </template>
      <el-empty v-else-if="!loading" description="还没有收藏任何笔记" />
    </div>
    <div v-if="total > PAGE_SIZE" class="pager">
      <el-pagination
        v-model:current-page="page"
        :total="total"
        :page-size="PAGE_SIZE"
        layout="prev, pager, next"
        background
      />
    </div>
  </div>
</template>

<style scoped>
.page-body {
  max-width: 860px;
  margin: 0 auto;
}

.page-title {
  margin: 0 0 16px;
  font-size: 20px;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 200px;
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

.title.disabled {
  color: #909399;
  cursor: default;
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

.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
