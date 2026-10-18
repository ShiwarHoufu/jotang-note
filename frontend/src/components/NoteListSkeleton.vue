<script setup lang="ts">
/** 列表首屏骨架：形状对齐 NoteCard，避免转圈遮罩带来的白屏感 */
const props = withDefaults(defineProps<{ rows?: number }>(), { rows: 3 })
</script>

<template>
  <div class="skeleton-list">
    <el-card v-for="i in props.rows" :key="i" shadow="never" :body-style="{ padding: '16px' }">
      <el-skeleton animated>
        <template #template>
          <div class="head">
            <el-skeleton-item variant="h3" style="width: 42%" />
            <el-skeleton-item variant="text" style="width: 64px" />
          </div>
          <div class="line">
            <el-skeleton-item variant="text" style="width: 88%" />
          </div>
          <div class="line">
            <el-skeleton-item variant="text" style="width: 56%" />
          </div>
          <div class="foot">
            <el-skeleton-item variant="text" style="width: 150px" />
            <el-skeleton-item variant="text" style="width: 48px" />
          </div>
        </template>
      </el-skeleton>
    </el-card>
  </div>
</template>

<style scoped>
.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.head,
.foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.line {
  margin-top: 14px;
}

.foot {
  margin-top: 16px;
}
</style>
