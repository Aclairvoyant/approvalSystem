import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { authAPI } from '@/services/api'
import { homePathFor } from '@/utils/navigation'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    requiresAdmin?: boolean
    guestOnly?: boolean
    /** 底部导航高亮哪个标签 */
    tab?: 'home' | 'daily' | 'applications' | 'game' | 'profile'
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/pages/auth/Login.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/pages/auth/Register.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/',
    name: 'Home',
    redirect: '/mobile/home',
    meta: { requiresAuth: true },
  },
  {
    path: '/mobile',
    name: 'MobileLayout',
    component: () => import('@/layouts/MobileLayout.vue'),
    meta: { requiresAuth: true },
    redirect: '/mobile/home',
    children: [
      { path: 'home', name: 'MobileHome', component: () => import('@/pages/mobile/Home.vue'), meta: { tab: 'home' } },
      { path: 'events', name: 'MobileEvents', component: () => import('@/pages/mobile/Events.vue'), meta: { tab: 'home' } },
      { path: 'templates', name: 'MobileTemplates', component: () => import('@/pages/mobile/Templates.vue'), meta: { tab: 'applications' } },
      { path: 'daily', name: 'MobileDaily', component: () => import('@/pages/mobile/Daily.vue'), meta: { tab: 'daily' } },
      { path: 'applications', name: 'MobileApplications', component: () => import('@/pages/mobile/Applications.vue'), meta: { tab: 'applications' } },
      { path: 'approvals', name: 'MobileApprovals', component: () => import('@/pages/mobile/Approvals.vue'), meta: { tab: 'applications' } },
      {
        path: 'application-detail/:id',
        name: 'MobileApplicationDetail',
        component: () => import('@/pages/mobile/ApplicationDetail.vue'),
        meta: { tab: 'applications' },
      },
      { path: 'relations', name: 'MobileRelations', component: () => import('@/pages/mobile/Relations.vue'), meta: { tab: 'profile' } },
      { path: 'profile', name: 'MobileProfile', component: () => import('@/pages/mobile/Profile.vue'), meta: { tab: 'profile' } },
      { path: 'game', name: 'MobileGameLobby', component: () => import('@/pages/mobile/GameLobby.vue'), meta: { tab: 'game' } },
      { path: 'game/room/:id', name: 'MobileGameRoom', component: () => import('@/pages/mobile/GameRoom.vue'), meta: { tab: 'game' } },
      { path: 'gobang/room/:id', name: 'MobileGobangRoom', component: () => import('@/pages/mobile/GobangRoom.vue'), meta: { tab: 'game' } },
      { path: 'game/tasks', name: 'MobileGameTasks', component: () => import('@/pages/mobile/GameTasks.vue'), meta: { tab: 'game' } },
    ],
  },
  {
    path: '/admin',
    name: 'AdminLayout',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    redirect: '/admin/dashboard',
    children: [
      { path: 'dashboard', name: 'AdminDashboard', component: () => import('@/pages/admin/Dashboard.vue') },
      { path: 'applications', name: 'AdminApplications', component: () => import('@/pages/admin/Applications.vue') },
      { path: 'users', name: 'AdminUsers', component: () => import('@/pages/admin/Users.vue') },
      { path: 'notifications', name: 'AdminNotifications', component: () => import('@/pages/admin/Notifications.vue') },
      { path: 'system', name: 'AdminSystem', component: () => import('@/pages/admin/System.vue') },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/pages/NotFound.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

// 本次页面生命周期里已经用哪个 token 拉过最新资料；换号登录后会重新拉
let profileCheckedFor = ''

router.beforeEach(async (to) => {
  const userStore = useUserStore()

  if (to.meta.guestOnly) {
    return userStore.token ? homePathFor(userStore.role) : true
  }

  if (!to.meta.requiresAuth) return true

  if (!userStore.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (profileCheckedFor !== userStore.token) {
    try {
      const user = await authAPI.getUserInfo()
      userStore.updateProfile(user)
      profileCheckedFor = userStore.token
    } catch {
      // 401 时 http.ts 已经清掉凭证并整页跳转到登录页；
      // 断网或服务端 500 时保留会话，继续用本地缓存的资料，下次导航再校验
      if (!userStore.token) {
        return { path: '/login', query: { redirect: to.fullPath } }
      }
    }
  }

  if (to.meta.requiresAdmin && !userStore.isAdmin) {
    return '/mobile/home'
  }

  return true
})

export default router
