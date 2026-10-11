<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

async function logout() {
  // 服务端无会话可销毁，调用它只是为了统一出口（《概要设计》§5.1）
  await authApi.logout().catch(() => undefined)
  auth.logout()
  ElMessage.success('已退出登录')
  router.push({ name: 'login' })
}
</script>

<template>
  <el-container class="page">
    <el-header class="header">
      <span class="brand">Jotang Note</span>
      <div class="header-right">
        <span v-if="auth.user" class="nickname">{{ auth.user.nickname }}</span>
        <el-button link @click="logout">退出</el-button>
      </div>
    </el-header>
    <el-main>
      <el-empty description="首页占位：笔记列表、搜索、上传等页面待建" />
    </el-main>
  </el-container>
</template>
