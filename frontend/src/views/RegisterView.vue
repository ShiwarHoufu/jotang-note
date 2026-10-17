<script setup lang="ts">
import { Collection, Lock, Message, User } from '@element-plus/icons-vue'
import { ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { authApi } from '@/api/auth'
import { ApiError } from '@/api/http'
import { ErrorCode, type College, type FieldViolation } from '@/api/types'

const router = useRouter()

const formRef = ref<FormInstance>()
const form = ref({ username: '', email: '', password: '', collegeId: undefined as number | undefined })
const colleges = ref<College[]>([])
const loading = ref(false)
/** 服务端返回的字段级错误，挂在对应 el-form-item 的 error 上 */
const fieldErrors = ref<Record<string, string>>({})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  collegeId: [{ required: true, message: '请选择学院', trigger: 'change' }],
}

onMounted(async () => {
  // 注册页未登录，/api/colleges 在放行名单里（SecurityConfig）
  colleges.value = await authApi.colleges()
})

async function submit() {
  fieldErrors.value = {}
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  try {
    await authApi.register({ ...form.value, collegeId: form.value.collegeId! })
    await ElMessageBox.alert('注册成功，请登录', '提示', { confirmButtonText: '去登录' })
    router.push({ name: 'login' })
  } catch (error) {
    if (error instanceof ApiError) {
      applyFieldErrors(error)
    }
  } finally {
    loading.value = false
  }
}

/** 40901 / 40903 / 40001 都把提示落到对应输入框上（《概要设计》§5.7） */
function applyFieldErrors(error: ApiError) {
  if (error.code === ErrorCode.USERNAME_TAKEN) {
    fieldErrors.value.username = error.message
  } else if (error.code === ErrorCode.EMAIL_TAKEN) {
    fieldErrors.value.email = error.message
  } else if (error.code === ErrorCode.PARAM_INVALID && Array.isArray(error.data)) {
    for (const violation of error.data as FieldViolation[]) {
      fieldErrors.value[violation.field] = violation.message
    }
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <div class="auth-brand">
        <span class="logo">
          <el-icon><Collection /></el-icon>
        </span>
        <span class="name">Jotang Note</span>
      </div>
      <h1 class="auth-title">创建账号</h1>
      <p class="auth-subtitle">加入 Jotang Note，与同学共享优质笔记</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username" :error="fieldErrors.username">
          <el-input v-model="form.username" :prefix-icon="User" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email" :error="fieldErrors.email">
          <el-input v-model="form.email" :prefix-icon="Message" placeholder="请输入校园邮箱" />
        </el-form-item>
        <el-form-item label="密码" prop="password" :error="fieldErrors.password">
          <el-input v-model="form.password" type="password" show-password :prefix-icon="Lock" placeholder="请设置密码" />
        </el-form-item>
        <el-form-item label="学院" prop="collegeId" :error="fieldErrors.collegeId">
          <el-select v-model="form.collegeId" placeholder="请选择学院" style="width: 100%">
            <el-option v-for="c in colleges" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-button class="auth-submit" type="primary" :loading="loading" native-type="submit">
          注 册
        </el-button>
      </el-form>
      <div class="auth-links">
        <span>已有账号？<router-link :to="{ name: 'login' }">去登录</router-link></span>
      </div>
    </el-card>
  </div>
</template>
