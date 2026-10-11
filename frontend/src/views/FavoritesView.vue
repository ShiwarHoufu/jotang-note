<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, ref, watch } from 'vue'

import { favoritesApi } from '@/api/favorites'
import type { FavoriteItem } from '@/api/types'
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
          shadow="never"
          :body-style="{ padding: '14px 16px' }"
        >
          <div class="row">
            <div class="info">
              <div class="line-1">
                <router-link
                  v-if="isAvailable(item)"
                  :to="{ name: 'note-detail', params: { id: item.noteId } }"
                  class="title"
                >
                  {{ item.title }}
                </router-link>
                <span v-else class="title disabled">{{ item.title }}</span>
                <el-tag size="small" type="info" effect="plain">{{ item.course.name }}</el-tag>
                <el-tag v-if="item.status === 'OFFLINE'" size="small" type="warning">已下架</el-tag>
                <el-tag v-else-if="item.status === 'DELETED'" size="small" type="danger">笔记已删除</el-tag>
              </div>
              <div v-if="isAvailable(item) && item.uploader" class="uploader">
                {{ item.uploader.nickname }}
                <span v-if="item.uploader.collegeName" class="college">{{ item.uploader.collegeName }}</span>
              </div>
              <div class="meta">
                <template v-if="isAvailable(item)">
                  {{ item.viewCount }} 浏览 · {{ item.downloadCount }} 下载 · {{ item.favoriteCount }} 收藏 ·
                </template>
                收藏于 {{ formatTime(item.favoritedAt) }}
              </div>
            </div>
            <el-button
              size="small"
              :loading="actingId === item.noteId"
              @click="unfavorite(item)"
            >
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

.row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.info {
  flex: 1;
  min-width: 0;
}

.line-1 {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  text-decoration: none;
  word-break: break-all;
}

.title:hover {
  color: #409eff;
}

.title.disabled {
  color: #909399;
  cursor: default;
}

.uploader {
  margin-top: 6px;
  font-size: 13px;
  color: #606266;
}

.college {
  color: #909399;
}

.meta {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
