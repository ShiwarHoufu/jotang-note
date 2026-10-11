<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

import { notesApi, type NoteListParams } from '@/api/notes'
import type { NoteListItem, NoteSort } from '@/api/types'
import NoteCard from '@/components/NoteCard.vue'
import { useCatalogStore } from '@/stores/catalog'

const catalog = useCatalogStore()

const PAGE_SIZE = 20

const filters = ref<{ courseId?: number; tagId?: number; sort: NoteSort }>({ sort: 'LATEST' })
const items = ref<NoteListItem[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

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
    const res = await notesApi.list(params)
    items.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

/** 筛选或排序变化回到第 1 页；翻页只换页码 */
watch(
  () => [filters.value.courseId, filters.value.tagId, filters.value.sort],
  () => {
    page.value = 1
    load()
  },
)

watch(page, load)

function clearFilters() {
  filters.value.courseId = undefined
  filters.value.tagId = undefined
}

onMounted(async () => {
  await catalog.ensureLoaded()
  await load()
})
</script>

<template>
  <div class="home">
    <div class="toolbar">
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
      <el-button
        v-if="filters.courseId !== undefined || filters.tagId !== undefined"
        link
        @click="clearFilters"
      >
        清除筛选
      </el-button>
    </div>

    <div v-loading="loading" class="list">
      <template v-if="items.length">
        <NoteCard v-for="item in items" :key="item.id" :note="item" />
      </template>
      <el-empty v-else-if="!loading" description="没有符合条件的笔记" />
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
