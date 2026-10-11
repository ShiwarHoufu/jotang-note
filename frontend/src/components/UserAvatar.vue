<script setup lang="ts">
import { ref, watch } from 'vue'

/**
 * 带回退的头像：URL 加载失败（OSS 对象被清、地址失效）时降级为首字母占位。
 * el-avatar 的 error 事件回调不返回 false 就会渲染默认 slot，正好放首字母
 */
const props = defineProps<{ url: string | null; name: string; size?: number }>()

const broken = ref(false)
watch(() => props.url, () => {
  broken.value = false
})
</script>

<template>
  <el-avatar
    v-if="props.url && !broken"
    :size="props.size ?? 24"
    :src="props.url"
    @error="broken = true"
  >
    {{ props.name.slice(0, 1) }}
  </el-avatar>
  <el-avatar v-else :size="props.size ?? 24">{{ props.name.slice(0, 1) }}</el-avatar>
</template>
