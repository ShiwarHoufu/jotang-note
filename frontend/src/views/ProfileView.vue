<script setup lang="ts">
import type { ElUpload, UploadRequestOptions, FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'

import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import type { College } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useCatalogStore } from '@/stores/catalog'
import UserAvatar from '@/components/UserAvatar.vue'

const auth = useAuthStore()
const catalog = useCatalogStore()

const ACCEPT = '.jpg,.jpeg,.png,.webp'
const MAX_SIZE = 5 * 1024 * 1024

const formRef = ref<FormInstance>()
const form = ref({ nickname: '', collegeId: undefined as number | undefined })
const colleges = ref<College[]>([])
const loading = ref(false)
const uploading = ref(false)
const uploadRef = ref<InstanceType<typeof ElUpload>>()

const rules: FormRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 32, message: '昵称不超过 32 个字符', trigger: 'blur' },
  ],
  collegeId: [{ required: true, message: '请选择学院', trigger: 'change' }],
}

onMounted(async () => {
  const [collegeList] = await Promise.all([authApi.colleges(), catalog.ensureLoaded()])
  colleges.value = collegeList
  form.value.nickname = auth.user?.nickname ?? ''
  // 学院在 UserInfo 里有 id；目录里没有对应项（理论上不该发生）就保持空
  form.value.collegeId = auth.user?.collegeId ?? undefined
})

/** 头像上传走 auto-upload，http 层负责带登录头与解析响应 */
async function doUploadAvatar({ file }: UploadRequestOptions) {
  if (file.size > MAX_SIZE) {
    ElMessage.error('头像超过 5MB')
    return
  }
  uploading.value = true
  try {
    auth.setUser(await userApi.uploadAvatar(file))
    ElMessage.success('头像已更新')
  } finally {
    uploading.value = false
    uploadRef.value?.clearFiles()
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  try {
    auth.setUser(await userApi.updateProfile({
      nickname: form.value.nickname,
      collegeId: form.value.collegeId!,
    }))
    ElMessage.success('资料已保存')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-body">
    <el-card>
      <h1 class="page-title">个人资料</h1>

      <div class="avatar-row">
        <UserAvatar :url="auth.user?.avatarUrl ?? null" :name="auth.user?.nickname ?? '?'" :size="64" />
        <el-upload
          ref="uploadRef"
          :auto-upload="true"
          :http-request="doUploadAvatar"
          :show-file-list="false"
          :accept="ACCEPT"
        >
          <el-button :loading="uploading">更换头像</el-button>
        </el-upload>
        <span class="avatar-tip">支持 jpg / png / webp，不超过 5MB</span>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="form">
        <el-form-item label="用户名">
          <el-input :model-value="auth.user?.username" disabled />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" maxlength="32" show-word-limit />
        </el-form-item>
        <el-form-item label="学院" prop="collegeId">
          <el-select v-model="form.collegeId" style="width: 100%">
            <el-option v-for="c in colleges" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-button type="primary" :loading="loading" native-type="submit" @click="submit">保存</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.page-body {
  max-width: 560px;
  margin: 0 auto;
}

.page-title {
  margin: 0 0 16px;
  font-size: 20px;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.avatar-tip {
  font-size: 12px;
  color: #909399;
}

.form .el-button {
  margin-top: 8px;
}
</style>
