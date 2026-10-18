<script setup lang="ts">
import { Collection } from '@element-plus/icons-vue'
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
        <router-link :to="{ name: 'home' }" class="brand">
          <span class="brand-logo"><el-icon><Collection /></el-icon></span>
          <span>Jotang Note</span>
        </router-link>
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
/* 吸顶 + 毛玻璃：滚动时内容从下面透过来有层次感 */
.header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(10px);
  box-shadow: var(--jt-shadow);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 24px;
  flex: 1;
  min-width: 0;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 700;
  color: var(--jt-ink);
  text-decoration: none;
  white-space: nowrap;
}

/* 品牌渐变小徽标，与登录页同款 */
.brand-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--jt-radius-sm);
  color: #fff;
  font-size: 15px;
  background: var(--jt-gradient);
  box-shadow: 0 3px 8px -2px rgba(97, 106, 255, 0.55);
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
  padding: 5px 12px;
  border-radius: 999px;
  cursor: pointer;
  outline: none;
  transition: background-color 0.2s ease;
}

.user-entry:hover {
  background: var(--jt-fill-hover);
}
</style>
