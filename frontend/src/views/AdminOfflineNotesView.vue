<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { onMounted, onUnmounted, ref, watch } from 'vue'

import { adminApi } from '@/api/admin'
import type { AdminNoteItem } from '@/api/types'
import NoteListSkeleton from '@/components/NoteListSkeleton.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { formatTime } from '@/utils/format'

const PAGE_SIZE = 20
const KEYWORD_DEBOUNCE_MS = 300

const keyword = ref('')
const items = ref<AdminNoteItem[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const restoringId = ref<number | null>(null)

let keywordTimer: ReturnType<typeof setTimeout> | undefined

async function load() {
  loading.value = true
  try {
    const kw = keyword.value.trim()
    const res = await adminApi.offlineNotes({
      page: page.value,
      size: PAGE_SIZE,
      ...(kw ? { keyword: kw } : {}),
    })
    items.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

/** 与首页同一套做法：页码已是 1 就直接加载，否则交给 watch(page) 发请求，避免连发两次 */
function reloadFromFirstPage() {
  if (page.value === 1) {
    load()
  } else {
    page.value = 1
  }
}

watch(keyword, (next, prev) => {
  clearTimeout(keywordTimer)
  // 只有「改成了非空的搜索词」才等防抖；清空是一次明确动作，立即加载
  if (next && next !== prev) {
    keywordTimer = setTimeout(reloadFromFirstPage, KEYWORD_DEBOUNCE_MS)
  } else {
    reloadFromFirstPage()
  }
})

watch(page, load)

/** 回车立即搜，不等防抖窗口走完 */
function searchNow() {
  clearTimeout(keywordTimer)
  reloadFromFirstPage()
}

async function restore(item: AdminNoteItem) {
  restoringId.value = item.id
  try {
    await adminApi.restore(item.id)
    ElMessage.success('已恢复上架')
    // 恢复后条目会从列表消失：当前页只剩这一条时回退一页，避免停在空页
    if (items.value.length === 1 && page.value > 1) {
      page.value -= 1
    } else {
      await load()
    }
  } finally {
    restoringId.value = null
  }
}

onMounted(load)
onUnmounted(() => clearTimeout(keywordTimer))
</script>

<template>
  <div class="page-body">
    <div class="page-head">
      <h1 class="page-title">已下架笔记</h1>
      <span class="hint">按最后编辑时间倒序</span>
    </div>

    <div class="toolbar">
      <!-- maxlength 与后端 @Size(max = 50) 对齐，免得敲满再被 40001 打回 -->
      <el-input
        v-model="keyword"
        placeholder="搜索笔记标题"
        clearable
        maxlength="50"
        :prefix-icon="Search"
        style="width: 240px"
        @keyup.enter="searchNow"
      />
    </div>

    <div v-loading="loading && items.length > 0" class="list">
      <NoteListSkeleton v-if="loading && !items.length" />
      <template v-else-if="items.length">
        <el-card v-for="item in items" :key="item.id" shadow="never" :body-style="{ padding: '16px' }">
          <div class="card-head">
            <router-link :to="{ name: 'note-detail', params: { id: item.id } }" class="title">
              {{ item.title }}
            </router-link>
            <el-tag size="small" type="primary" effect="light">{{ item.course.name }}</el-tag>
          </div>

          <div class="uploader">
            <UserAvatar :url="item.uploader.avatarUrl" :name="item.uploader.nickname" :size="20" />
            <span class="uploader-name">{{ item.uploader.nickname }}</span>
            <span v-if="item.uploader.collegeName" class="uploader-college">
              {{ item.uploader.collegeName }}
            </span>
          </div>

          <div v-if="item.tags.length" class="tags">
            <el-tag v-for="tag in item.tags" :key="tag.id" size="small" type="info" effect="plain">
              {{ tag.name }}
            </el-tag>
          </div>

          <div class="card-foot">
            <span class="meta">
              {{ item.viewCount }} 浏览 · {{ item.downloadCount }} 下载 · {{ item.favoriteCount }} 收藏 ·
              上传于 {{ formatTime(item.createdAt) }} · 最后编辑 {{ formatTime(item.updatedAt) }}
            </span>
            <div class="actions">
              <el-button
                size="small"
                type="success"
                plain
                :loading="restoringId === item.id"
                @click="restore(item)"
              >
                恢复上架
              </el-button>
            </div>
          </div>
        </el-card>
      </template>
      <el-empty v-else description="暂无已下架笔记" />
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

.page-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
}

.hint {
  font-size: 12px;
  color: var(--jt-text-muted);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  padding: 14px 16px;
  border-radius: var(--jt-radius-lg);
  background: var(--jt-surface);
  box-shadow: var(--jt-shadow-sm);
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
  color: var(--jt-ink);
  text-decoration: none;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.2s ease;
}

.title:hover {
  color: var(--jt-brand);
}

.uploader {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  font-size: 13px;
  color: var(--jt-text-secondary);
}

.uploader-college {
  color: var(--jt-text-muted);
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.meta {
  font-size: 12px;
  color: var(--jt-text-muted);
  font-variant-numeric: tabular-nums;
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

.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
