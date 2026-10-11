<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { ref } from 'vue'

import { authApi } from '@/api/auth'
import { ApiError } from '@/api/http'
import { ErrorCode } from '@/api/types'

const formRef = ref<FormInstance>()
const form = ref({ email: '' })
const loading = ref(false)
const sent = ref(false)
const errorText = ref('')

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    await authApi.forgotPassword(form.value.email)
    // 邮箱不存在也返回成功，文案由前端写死（防枚举，§6.6）
    sent.value = true
  } catch (error) {
    if (error instanceof ApiError && error.code === ErrorCode.ACCOUNT_LOCKED) {
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
      <h1 class="auth-title">找回密码</h1>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />
        <el-alert
          v-if="sent"
          title="若该邮箱已注册，重置链接已发送，请查收邮件"
          type="success"
          :closable="false"
          show-icon
        />
        <el-button class="auth-submit" type="primary" :loading="loading" native-type="submit">
          发送重置邮件
        </el-button>
      </el-form>
      <div class="auth-links">
        <router-link :to="{ name: 'login' }">返回登录</router-link>
      </div>
    </el-card>
  </div>
</template>
