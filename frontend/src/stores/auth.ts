import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { authApi } from '@/api/auth'
import type { UserInfo } from '@/api/types'
import { clearToken, getToken, setToken } from '@/utils/token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  /** 刷新页面后为 null，由路由守卫调 loadCurrentUser 补上 */
  const user = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => token.value !== null)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  async function login(username: string, password: string): Promise<void> {
    const res = await authApi.login(username, password)
    token.value = res.token
    setToken(res.token)
    user.value = res.user
  }

  /** 已有 token 但没有用户信息时拉一次；失败由 http 层统一处理 */
  async function loadCurrentUser(): Promise<void> {
    if (!token.value || user.value) {
      return
    }
    user.value = await authApi.me()
  }

  function logout(): void {
    token.value = null
    user.value = null
    clearToken()
  }

  return { token, user, isLoggedIn, isAdmin, login, loadCurrentUser, logout }
})
