<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Document, UploadFilled } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { aiApi } from '@/api/ai'
import { ApiError } from '@/api/http'
import { notesApi } from '@/api/notes'
import { ErrorCode, type FieldViolation } from '@/api/types'
import { useCatalogStore } from '@/stores/catalog'

/** 与后端 NoteFilePolicy 的白名单同源；后端的 AiSummaryFilePolicy 认其中的图片与 md / txt */
const ACCEPT = '.md,.txt,.jpg,.jpeg,.png,.gif,.webp'
/** 能摘要的两档：光栅图走多模态、md / txt 走纯文本调用 */
const SUMMARIZABLE_EXTS = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'md', 'txt'])
const MAX_SIZE = 100 * 1024 * 1024

const router = useRouter()
const catalog = useCatalogStore()

const formRef = ref<FormInstance>()
const form = ref({
  title: '',
  summary: '',
  teacher: '',
  courseId: undefined as number | undefined,
  tags: [] as string[],
})
const file = ref<File | null>(null)
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

/** 选中图片或 md / txt 才能让 AI 生成简介——前端控可见性，可行性由后端按内容判 */
const canSummarize = computed(() => {
  if (!file.value) {
    return false
  }
  const ext = file.value.name.split('.').pop()?.toLowerCase() ?? ''
  return SUMMARIZABLE_EXTS.has(ext)
})

onMounted(() => catalog.ensureLoaded())

/** 后端只收单文件：选中即替换，拖拽框随之隐藏，天然限一个 */
function onFileChange(uploadFile: { raw?: File }) {
  const raw = uploadFile.raw
  if (!raw) {
    return
  }
  if (raw.size > MAX_SIZE) {
    ElMessage.error('文件超过 100MB')
    return
  }
  file.value = raw
}

function removeFile() {
  file.value = null
}

/** 用待上传的文件请 AI 生成简介，回填输入框；限频 / 不可用由 http 层统一提示 */
async function summarize() {
  if (!file.value) {
    return
  }
  summarizing.value = true
  try {
    const res = await aiApi.summarizeUpload(file.value, form.value.title, form.value.teacher)
    form.value.summary = res.summary
  } finally {
    summarizing.value = false
  }
}

async function submit() {
  fieldErrors.value = {}
  if (!file.value) {
    ElMessage.warning('请选择文件')
    return
  }
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  try {
    const res = await notesApi.upload({
      title: form.value.title,
      summary: form.value.summary || undefined,
      teacher: form.value.teacher || undefined,
      courseId: form.value.courseId!,
      tags: form.value.tags,
      file: file.value,
    })
    ElMessage.success('上传成功')
    router.push({ name: 'note-detail', params: { id: res.id } })
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
    <el-card>
      <h1 class="page-title">上传笔记</h1>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="form"
        @submit.prevent="submit"
      >
        <el-form-item label="标题" prop="title" :error="fieldErrors.title">
          <el-input v-model="form.title" maxlength="128" show-word-limit placeholder="一句话说明这份笔记讲什么" />
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
              <el-tooltip
                :disabled="canSummarize"
                content="选择图片或 md / txt 文件后可用"
                placement="top"
              >
                <span>
                  <el-button
                    link
                    type="primary"
                    size="small"
                    :disabled="!canSummarize"
                    :loading="summarizing"
                    @click="summarize"
                  >
                    AI 生成简介
                  </el-button>
                </span>
              </el-tooltip>
            </span>
          </template>
          <el-input
            v-model="form.summary"
            type="textarea"
            :rows="4"
            maxlength="512"
            show-word-limit
            placeholder="给这份笔记写几句说明，或让 AI 根据文件内容生成"
          />
        </el-form-item>
        <el-form-item label="文件" prop="file" :error="fieldErrors.file">
          <el-upload
            v-if="!file"
            drag
            :auto-upload="false"
            :accept="ACCEPT"
            :show-file-list="false"
            :on-change="onFileChange"
          >
            <div class="upload-hint">
              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
            </div>
          </el-upload>
          <div v-else class="file-card">
            <el-icon class="file-icon"><document /></el-icon>
            <span class="file-name" :title="file.name">{{ file.name }}</span>
            <span class="file-size">{{ (file.size / 1024 / 1024).toFixed(2) }} MB</span>
            <el-button link type="danger" @click="removeFile">移除</el-button>
          </div>
          <div class="upload-tip">支持 md / txt / jpg / png / gif / webp，单个不超过 100MB</div>
        </el-form-item>
        <el-button class="submit" type="primary" :loading="loading" native-type="submit">上传</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.page-body {
  max-width: 720px;
  margin: 0 auto;
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

.upload-hint {
  padding: 24px 0;
}

.file-card {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-light);
}

.file-icon {
  font-size: 20px;
  color: var(--el-color-primary);
}

.file-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.upload-tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.submit {
  margin-top: 8px;
}
</style>
