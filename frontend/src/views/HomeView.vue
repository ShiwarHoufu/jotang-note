<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

import { notesApi, type NoteListParams } from '@/api/notes'
import type { NoteListItem, NoteSort } from '@/api/types'
import NoteCard from '@/components/NoteCard.vue'
import NoteListSkeleton from '@/components/NoteListSkeleton.vue'
import { useCatalogStore } from '@/stores/catalog'

const catalog = useCatalogStore()

const PAGE_SIZE = 20

/** 搜索词防抖窗口。手写而不引 lodash-es：整个前端只有这一处需要防抖 */
const KEYWORD_DEBOUNCE_MS = 300

const filters = ref<{ courseId?: number; tagId?: number; keyword?: string; sort: NoteSort }>({
  sort: 'LATEST',
})
const items = ref<NoteListItem[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

const hasFilters = computed(
  () =>
    filters.value.courseId !== undefined ||
    filters.value.tagId !== undefined ||
    !!filters.value.keyword?.trim(),
)

let keywordTimer: ReturnType<typeof setTimeout> | undefined

async function load() {
  loading.value = true
  try {
    const params: NoteListParams = {
      sort: filters.value.sort,
      page: page.value,
      size: PAGE_SIZE,
    }
    if (filters.value.courseId !== undefined) {
      params.courseId = filters.value.courseId
    }
    if (filters.value.tagId !== undefined) {
      params.tagId = filters.value.tagId
    }
    // 空串与纯空白都当没搜——与后端 escapeLikeKeyword 的口径一致，不把空串发上去
    const keyword = filters.value.keyword?.trim()
    if (keyword) {
      params.keyword = keyword
    }
    const res = await notesApi.list(params)
    items.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

/**
 * 回到第 1 页并加载。
 *
 * 页码确实变了就直接返回，交给下面那条 watch(page) 去发请求——否则在第 3 页改条件会发两遍：
 * page 从 3 拨回 1 会触发一次，这里再 load() 一次。
 */
function reloadFromFirstPage() {
  if (page.value === 1) {
    load()
  } else {
    page.value = 1
  }
}

/**
 * 筛选条件变化。四项收在同一个 watch 里，不是一项一条。
 *
 * 分开写的话，「清除筛选」一次改三项就会连着发三次请求，拉的是同一份数据。
 * 收在一起，一个 tick 内只触发一次（Vue 对 pre-flush watcher 有去重）。
 *
 * 只有「搜索词变了、且变成了非空」才等防抖窗口——那是用户还在打字。切课程、清空搜索词、
 * 点清除筛选都是一次明确的动作，立即加载。
 */
watch(
  () => [filters.value.courseId, filters.value.tagId, filters.value.sort, filters.value.keyword],
  (next, prev) => {
    clearTimeout(keywordTimer)
    if (next[3] !== prev[3] && next[3]) {
      keywordTimer = setTimeout(reloadFromFirstPage, KEYWORD_DEBOUNCE_MS)
    } else {
      reloadFromFirstPage()
    }
  },
)

// 翻页只换页码，不重置筛选条件
watch(page, load)

/** 回车立即搜，不等防抖窗口走完；顺手取消挂着的定时器，免得随后又发一次 */
function searchNow() {
  clearTimeout(keywordTimer)
  reloadFromFirstPage()
}

function clearFilters() {
  clearTimeout(keywordTimer)
  filters.value.courseId = undefined
  filters.value.tagId = undefined
  filters.value.keyword = undefined
}

onMounted(async () => {
  await catalog.ensureLoaded()
  await load()
})

onUnmounted(() => clearTimeout(keywordTimer))
</script>

<template>
  <div class="home">
    <div class="toolbar">
      <!-- maxlength 与后端 @Size(max = 50) 对齐，免得敲满再被 40001 打回 -->
      <el-input
        v-model="filters.keyword"
        placeholder="搜索笔记标题"
        clearable
        maxlength="50"
        :prefix-icon="Search"
        style="width: 240px"
        @keyup.enter="searchNow"
      />
      <el-select
        v-model="filters.courseId"
        placeholder="全部课程"
        clearable
        filterable
        style="width: 200px"
      >
        <el-option v-for="c in catalog.courses" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select
        v-model="filters.tagId"
        placeholder="全部标签"
        clearable
        filterable
        style="width: 160px"
      >
        <el-option v-for="t in catalog.tags" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <el-radio-group v-model="filters.sort">
        <el-radio-button value="LATEST">最近更新</el-radio-button>
        <el-radio-button value="HOT_VIEW">最多浏览</el-radio-button>
        <el-radio-button value="HOT_DOWNLOAD">最多下载</el-radio-button>
      </el-radio-group>
      <el-button v-if="hasFilters" link @click="clearFilters">清除筛选</el-button>
    </div>

    <!-- 首屏用骨架屏；已有内容时保留列表，只叠加载态，避免筛选时闪一下骨架 -->
    <div v-loading="loading && items.length > 0" class="list">
      <NoteListSkeleton v-if="loading && !items.length" :rows="3" />
      <template v-else-if="items.length">
        <NoteCard v-for="item in items" :key="item.id" :note="item" />
      </template>
      <el-empty v-else description="没有符合条件的笔记">
        <el-button v-if="hasFilters" type="primary" plain @click="clearFilters">清除筛选</el-button>
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
.home {
  max-width: 860px;
  margin: 0 auto;
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

.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
