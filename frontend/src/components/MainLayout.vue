<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import AiChatWidget from '@/components/AiChatWidget.vue'
import UserAvatar from '@/components/UserAvatar.vue'

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
      <div class="header-left">
        <router-link :to="{ name: 'home' }" class="brand">Jotang Note</router-link>
        <!-- el-menu 的 router 模式默认把 index 当路径跳转，这里显式传 route 对象按名称跳 -->
        <el-menu mode="horizontal" :default-active="String($route.name)" :ellipsis="false" class="nav" router>
          <el-menu-item index="home" :route="{ name: 'home' }">首页</el-menu-item>
          <el-menu-item index="my-notes" :route="{ name: 'my-notes' }">我的上传</el-menu-item>
          <el-menu-item index="my-favorites" :route="{ name: 'my-favorites' }">我的收藏</el-menu-item>
        </el-menu>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="router.push({ name: 'note-upload' })">上传笔记</el-button>
        <el-dropdown v-if="auth.user" @command="(name: string) => name === 'profile' ? router.push({ name: 'profile' }) : logout()">
          <span class="user-entry">
            <UserAvatar :url="auth.user.avatarUrl" :name="auth.user.nickname" :size="28" />
            <span class="nickname">{{ auth.user.nickname }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人资料</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-main class="main">
      <router-view />
    </el-main>
    <AiChatWidget />
  </el-container>
</template>

<style scoped>
.header-left {
  display: flex;
  align-items: center;
  gap: 24px;
  flex: 1;
  min-width: 0;
}

.brand {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  text-decoration: none;
  white-space: nowrap;
}

.nav {
  border-bottom: none;
  flex: 1;
  min-width: 0;
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
</style>
