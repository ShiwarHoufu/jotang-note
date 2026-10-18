import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { getToken } from '@/utils/token'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('@/components/MainLayout.vue'),
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
        },
        {
          path: 'notes/upload',
          name: 'note-upload',
          component: () => import('@/views/NoteUploadView.vue'),
        },
        {
          path: 'notes/:id',
          name: 'note-detail',
          component: () => import('@/views/NoteDetailView.vue'),
        },
        {
          path: 'notes/:id/edit',
          name: 'note-edit',
          component: () => import('@/views/NoteEditView.vue'),
        },
        {
          path: 'my/notes',
          name: 'my-notes',
          component: () => import('@/views/MyNotesView.vue'),
        },
        {
          path: 'my/favorites',
          name: 'my-favorites',
          component: () => import('@/views/FavoritesView.vue'),
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/ProfileView.vue'),
        },
      ],
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { public: true },
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('@/views/ForgotPasswordView.vue'),
      meta: { public: true },
    },
    {
      // 与 jotang.mail.reset-link-base 的默认值对齐（《概要设计》§6.6）
      path: '/reset-password',
      name: 'reset-password',
      component: () => import('@/views/ResetPasswordView.vue'),
      meta: { public: true },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { public: true },
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  // 以 localStorage 为准同步内存登录态：http 层处理 40100 时只清存储、不反向依赖 store，
  // 否则残留的 token ref 会让下面「已登录不进登录页」的判断把跳转弹回首页
  if (!getToken() && auth.isLoggedIn) {
    auth.logout()
  }

  if (to.meta.public) {
    // 已登录再进登录/注册页就直接回首页
    if (auth.isLoggedIn && (to.name === 'login' || to.name === 'register')) {
      return { name: 'home' }
    }
    return true
  }

  if (!auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  try {
    await auth.loadCurrentUser()
  } catch {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
