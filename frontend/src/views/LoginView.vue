<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { ErrorCode } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const form = ref({ username: '', password: '' })
const loading = ref(false)
/** 登录失败的提示留在表单里，不弹全局 toast */
const errorText = ref('')

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    await auth.login(form.value.username, form.value.password)
    const redirect = route.query.redirect
    router.push(typeof redirect === 'string' ? redirect : { name: 'home' })
  } catch (error) {
    // 40101 / 42300 在后端的 FORM_LEVEL_CODES 里，http 层不会弹提示，这里就地展示
    if (error instanceof ApiError && ErrorCode.BAD_CREDENTIALS === error.code) {
      errorText.value = error.message
    } else if (error instanceof ApiError && ErrorCode.ACCOUNT_LOCKED === error.code) {
      errorText.value = `${error.message}（剩余 ${String(error.data)} 秒）`
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h1 class="auth-title">登录</h1>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />
        <el-button class="auth-submit" type="primary" :loading="loading" native-type="submit">
          登录
        </el-button>
      </el-form>
      <div class="auth-links">
        <router-link :to="{ name: 'register' }">注册</router-link>
        <router-link :to="{ name: 'forgot-password' }">忘记密码</router-link>
      </div>
    </el-card>
  </div>
</template>
