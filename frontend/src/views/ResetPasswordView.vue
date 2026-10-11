<script setup lang="ts">
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { authApi } from '@/api/auth'
import { ApiError } from '@/api/http'
import { ErrorCode } from '@/api/types'

const route = useRoute()
const router = useRouter()

// 邮件里的链接形如 /reset-password?token=xxx（《概要设计》§6.6）
const token = typeof route.query.token === 'string' ? route.query.token : ''

const formRef = ref<FormInstance>()
const form = ref({ newPassword: '' })
const loading = ref(false)
const errorText = ref('')

const rules: FormRules = {
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }],
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    await authApi.resetPassword(token, form.value.newPassword)
    ElMessage.success('密码已重置，请用新密码登录')
    router.push({ name: 'login' })
  } catch (error) {
    // token 无效或过期返回 40001，就地提示，不让用户重试到底
    if (error instanceof ApiError && error.code === ErrorCode.PARAM_INVALID) {
      errorText.value = error.message
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h1 class="auth-title">重置密码</h1>
      <el-alert
        v-if="!token"
        title="链接不完整，请从邮件里的重置链接进入"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form v-else ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password />
        </el-form-item>
        <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />
        <el-button class="auth-submit" type="primary" :loading="loading" native-type="submit">
          提交
        </el-button>
      </el-form>
      <div class="auth-links">
        <router-link :to="{ name: 'login' }">返回登录</router-link>
      </div>
    </el-card>
  </div>
</template>
