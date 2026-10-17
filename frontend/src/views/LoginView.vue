<script setup lang="ts">
import { Collection, Lock, MagicStick, Reading, Star, User } from '@element-plus/icons-vue'
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
    <el-card class="auth-card login-card" :body-style="{ padding: '0' }">
      <div class="login-layout">
        <!-- 左侧品牌展示区：纯 CSS 渐变 + 装饰圆，窄屏整体隐藏 -->
        <div class="login-aside">
          <div class="aside-brand">
            <span class="aside-logo"><el-icon><Collection /></el-icon></span>
            <span class="aside-name">Jotang Note</span>
          </div>
          <h2 class="aside-headline">让笔记分享更简单</h2>
          <p class="aside-desc">与同学共建共享学习资料库</p>
          <ul class="aside-feats">
            <li>
              <span class="feat-icon"><el-icon><Reading /></el-icon></span>
              <div class="feat-text">
                <b>海量课程笔记</b>
                <span>按课程分类，快速找到所需资料</span>
              </div>
            </li>
            <li>
              <span class="feat-icon"><el-icon><MagicStick /></el-icon></span>
              <div class="feat-text">
                <b>AI 摘要与问答</b>
                <span>自动生成笔记摘要，随时向笔记提问</span>
              </div>
            </li>
            <li>
              <span class="feat-icon"><el-icon><Star /></el-icon></span>
              <div class="feat-text">
                <b>收藏与预览</b>
                <span>在线预览，一键收藏优质笔记</span>
              </div>
            </li>
          </ul>
          <p class="aside-foot">© Jotang Note · 校园笔记共享平台</p>
        </div>
        <!-- 右侧登录表单区 -->
        <div class="login-main">
          <div class="auth-brand login-brand">
            <span class="logo">
              <el-icon><Collection /></el-icon>
            </span>
            <span class="name">Jotang Note</span>
          </div>
          <h1 class="auth-title">欢迎回来</h1>
          <p class="auth-subtitle">登录后继续你的笔记之旅</p>
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            class="login-form"
            @submit.prevent="submit"
          >
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" :prefix-icon="User" placeholder="请输入用户名" autocomplete="username" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                :prefix-icon="Lock"
                placeholder="请输入密码"
                autocomplete="current-password"
              />
            </el-form-item>
            <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon class="auth-alert" />
            <el-button class="auth-submit" type="primary" :loading="loading" native-type="submit">
              登 录
            </el-button>
          </el-form>
          <div class="auth-links">
            <span>还没有账号？<router-link :to="{ name: 'register' }">立即注册</router-link></span>
            <router-link :to="{ name: 'forgot-password' }">忘记密码</router-link>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
/* 大长方形分栏卡：左侧品牌展示，右侧表单 */
.login-card {
  max-width: 960px;
  width: 92vw;
}

.login-layout {
  display: flex;
  min-height: 580px;
}

.login-aside {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 44%;
  padding: 44px 42px;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(155deg, #4f7cff 0%, #6a5cff 55%, #8a5cff 100%);
}

/* 两枚半透明装饰圆 */
.login-aside::before,
.login-aside::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.login-aside::before {
  width: 280px;
  height: 280px;
  top: -90px;
  right: -90px;
  background: rgba(255, 255, 255, 0.12);
}

.login-aside::after {
  width: 220px;
  height: 220px;
  bottom: -70px;
  left: -70px;
  background: rgba(255, 255, 255, 0.08);
}

.aside-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  position: relative;
  z-index: 1;
}

.aside-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 11px;
  font-size: 19px;
  color: #6a5cff;
  background: rgba(255, 255, 255, 0.92);
}

.aside-name {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.2px;
}

.aside-headline {
  position: relative;
  z-index: 1;
  margin: 56px 0 12px;
  font-size: 30px;
  font-weight: 700;
  line-height: 1.35;
}

.aside-desc {
  position: relative;
  z-index: 1;
  margin: 0;
  font-size: 15px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.85);
}

.aside-feats {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 24px;
  margin: 48px 0 0;
  padding: 0;
  list-style: none;
}

.aside-feats li {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.feat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  font-size: 19px;
  background: rgba(255, 255, 255, 0.16);
}

.feat-text b {
  display: block;
  margin-bottom: 3px;
  font-size: 15px;
  font-weight: 600;
}

.feat-text span {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
}

.aside-foot {
  position: relative;
  z-index: 1;
  margin: auto 0 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
}

/* 右侧表单区 */
.login-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px 56px;
}

.login-form {
  width: 100%;
  max-width: 360px;
  align-self: center;
}

.login-brand {
  display: none;
}

.auth-alert {
  margin-bottom: 16px;
}

/* 窄屏隐藏左侧展示区，回退为居中卡片并显示品牌行 */
@media (max-width: 880px) {
  .login-card {
    width: 100%;
    max-width: 420px;
  }

  .login-layout {
    min-height: 0;
  }

  .login-aside {
    display: none;
  }

  .login-main {
    padding: 36px 28px 28px;
  }

  .login-brand {
    display: flex;
  }
}
</style>
