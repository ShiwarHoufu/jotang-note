<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { aiApi } from '@/api/ai'
import { ApiError } from '@/api/http'
import { notesApi } from '@/api/notes'
import { ErrorCode, type FieldViolation, type NoteDetail } from '@/api/types'
import { useCatalogStore } from '@/stores/catalog'

const route = useRoute()
const router = useRouter()
const catalog = useCatalogStore()

const noteId = Number(route.params.id)
const detail = ref<NoteDetail | null>(null)

const formRef = ref<FormInstance>()
const form = ref({
  title: '',
  summary: '',
  teacher: '',
  courseId: undefined as number | undefined,
  tags: [] as string[],
})
const loading = ref(false)
const summarizing = ref(false)
const fieldErrors = ref<Record<string, string>>({})

const rules: FormRules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 128, message: '标题不超过 128 个字符', trigger: 'blur' },
  ],
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  teacher: [{ max: 64, message: '教师不超过 64 个字符', trigger: 'blur' }],
  summary: [{ max: 512, message: '简介不超过 512 个字符', trigger: 'blur' }],
}

/** 编辑场景按笔记附件生成摘要，仅图片类笔记可用（后端同样只吃图片） */
const canSummarize = ref(false)

onMounted(async () => {
  await catalog.ensureLoaded()
  const res = await notesApi.detail(noteId)
  detail.value = res
  // PUT 是全量替换：把现有值全部回填，没动的字段原样提交回去
  form.value = {
    title: res.title,
    summary: res.summary ?? '',
    teacher: res.teacher ?? '',
    courseId: res.course.id,
    tags: res.tags.map((t) => t.name),
  }
  canSummarize.value = res.file?.previewMode === 'IMAGE_INLINE'
})

async function summarize() {
  summarizing.value = true
  try {
    const res = await aiApi.summarizeNote(noteId, form.value.title, form.value.teacher)
    form.value.summary = res.summary
  } finally {
    summarizing.value = false
  }
}

async function submit() {
  fieldErrors.value = {}
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  try {
    await notesApi.update(noteId, {
      title: form.value.title,
      summary: form.value.summary || undefined,
      teacher: form.value.teacher || undefined,
      courseId: form.value.courseId!,
      tags: form.value.tags,
    })
    ElMessage.success('已保存')
    router.push({ name: 'note-detail', params: { id: noteId } })
  } catch (error) {
    applyFieldErrors(error)
  } finally {
    loading.value = false
  }
}

function applyFieldErrors(error: unknown) {
  if (error instanceof ApiError && error.code === ErrorCode.PARAM_INVALID && Array.isArray(error.data)) {
    for (const violation of error.data as FieldViolation[]) {
      fieldErrors.value[violation.field] = violation.message
    }
  }
}
</script>

<template>
  <div class="page-body">
    <el-alert
      v-if="detail?.status === 'OFFLINE'"
      title="该笔记当前处于下架状态，编辑保存后仍是下架状态"
      type="warning"
      :closable="false"
      show-icon
      class="banner"
    />
    <el-card v-loading="!detail">
      <h1 class="page-title">编辑笔记</h1>
      <el-form
        v-if="detail"
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item label="标题" prop="title" :error="fieldErrors.title">
          <el-input v-model="form.title" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="课程" prop="courseId" :error="fieldErrors.courseId">
          <el-select v-model="form.courseId" filterable placeholder="请选择课程" style="width: 100%">
            <el-option v-for="c in catalog.courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="教师（可选）" prop="teacher" :error="fieldErrors.teacher">
          <el-input v-model="form.teacher" maxlength="64" />
        </el-form-item>
        <el-form-item label="标签（最多 10 个，可选）" prop="tags" :error="fieldErrors.tags">
          <el-select
            v-model="form.tags"
            multiple
            filterable
            allow-create
            default-first-option
            :reserve-keyword="false"
            placeholder="选择或输入新标签"
            style="width: 100%"
          >
            <el-option v-for="t in catalog.tags" :key="t.id" :label="t.name" :value="t.name" />
          </el-select>
        </el-form-item>
        <el-form-item prop="summary" :error="fieldErrors.summary">
          <template #label>
            <span class="summary-label">
              简介（可选，≤512 字）
              <el-tooltip v-if="canSummarize" content="按笔记附件生成" placement="top">
                <el-button link type="primary" size="small" :loading="summarizing" @click="summarize">
                  AI 生成简介
                </el-button>
              </el-tooltip>
            </span>
          </template>
          <el-input
            v-model="form.summary"
            type="textarea"
            :rows="4"
            maxlength="512"
            show-word-limit
          />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="编辑只能修改元数据，不能替换附件"
          class="file-tip"
        />
        <el-button class="submit" type="primary" :loading="loading" native-type="submit">保存</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.page-body {
  max-width: 720px;
  margin: 0 auto;
}

.banner {
  margin-bottom: 16px;
}

.page-title {
  margin: 0 0 16px;
  font-size: 20px;
}

.summary-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.file-tip {
  margin-bottom: 16px;
}

.submit {
  margin-top: 8px;
}
</style>
