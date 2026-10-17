<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, ref, watch } from 'vue'

import { notesApi } from '@/api/notes'
import { favoritesApi } from '@/api/favorites'
import type { MyNoteItem } from '@/api/types'
import { formatTime } from '@/utils/format'

const PAGE_SIZE = 20

const items = ref<MyNoteItem[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await notesApi.myNotes({ page: page.value, size: PAGE_SIZE })
    items.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

watch(page, load)
onMounted(load)

const favoriteActing = ref<number | null>(null)

/** 收藏 / 取消拿响应里的最新计数直接覆盖，与 NoteCard 同一套本地覆盖模式 */
async function toggleFavorite(item: MyNoteItem) {
  favoriteActing.value = item.id
  try {
    const res = item.isFavorited
      ? await favoritesApi.unfavorite(item.id)
      : await favoritesApi.favorite(item.id)
    item.isFavorited = !item.isFavorited
    item.favoriteCount = res.favoriteCount
  } finally {
    favoriteActing.value = null
  }
}

async function remove(item: MyNoteItem) {
  await ElMessageBox.confirm('删除后其他人将无法看到这篇笔记，确定删除？', '删除笔记', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await notesApi.delete(item.id)
  ElMessage.success('已删除')
  await load()
}
</script>

<template>
  <div class="page-body">
    <h1 class="page-title">我的上传</h1>
    <div v-loading="loading" class="list">
      <template v-if="items.length">
        <el-card v-for="item in items" :key="item.id" shadow="never" :body-style="{ padding: '14px 16px' }">
          <div class="row">
            <div class="info">
              <div class="line-1">
                <router-link :to="{ name: 'note-detail', params: { id: item.id } }" class="title">
                  {{ item.title }}
                </router-link>
                <el-tag v-if="item.status === 'OFFLINE'" size="small" type="warning">已下架</el-tag>
              </div>
              <div v-if="item.tags.length" class="tags">
                <el-tag v-for="tag in item.tags" :key="tag.id" size="small" type="info" effect="plain">
                  {{ tag.name }}
                </el-tag>
              </div>
            </div>
            <!-- 课程标签固定在卡片右上角 -->
            <el-tag size="small" type="primary" effect="light" class="course-tag">{{ item.course.name }}</el-tag>
          </div>
          <div class="meta-row">
            <div class="meta">
              {{ item.viewCount }} 浏览 · {{ item.downloadCount }} 下载 · {{ item.favoriteCount }} 收藏 ·
              上传于 {{ formatTime(item.createdAt) }}
            </div>
            <div class="actions">
              <el-button size="small" type="danger" plain @click="remove(item)">删除</el-button>
              <el-button size="small" @click="$router.push({ name: 'note-edit', params: { id: item.id } })">
                编辑
              </el-button>
              <!-- 与首页 NoteCard 同款 link 收藏按钮：已收藏 warning 色，未收藏默认色 -->
              <el-button
                link
                :type="item.isFavorited ? 'warning' : 'default'"
                :loading="favoriteActing === item.id"
                @click="toggleFavorite(item)"
              >
                {{ item.isFavorited ? '已收藏' : '收藏' }}
              </el-button>
            </div>
          </div>
        </el-card>
      </template>
      <el-empty v-else-if="!loading" description="还没有上传过笔记">
        <el-button type="primary" @click="$router.push({ name: 'note-upload' })">去上传</el-button>
      </el-empty>
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
  color: var(--jt-ink, #1f2d4d);
  text-decoration: none;
  word-break: break-all;
  transition: color 0.2s ease;
}

.title:hover {
  color: var(--jt-brand, #4f7cff);
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
}

.meta {
  font-size: 12px;
  color: #909399;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.actions .el-button {
  margin-left: 0;
}

.course-tag {
  flex-shrink: 0;
  align-self: flex-start;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
