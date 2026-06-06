import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { authAPI } from '@/services/api'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/pages/auth/Login.vue'),
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/pages/auth/Register.vue'),
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
      {
        path: 'home',
        name: 'MobileHome',
        component: () => import('@/pages/mobile/Home.vue'),
      },
      {
        path: 'daily',
        name: 'MobileDaily',
        component: () => import('@/pages/mobile/Daily.vue'),
      },
      {
        path: 'events',
        name: 'MobileEvents',
        component: () => import('@/pages/mobile/Events.vue'),
      },
      {
        path: 'templates',
        name: 'MobileTemplates',
        component: () => import('@/pages/mobile/Templates.vue'),
      },
      {
        path: 'applications',
        name: 'MobileApplications',
        component: () => import('@/pages/mobile/Applications.vue'),
      },
      {
        path: 'approvals',
        name: 'MobileApprovals',
        component: () => import('@/pages/mobile/Approvals.vue'),
      },
      {
        path: 'relations',
        name: 'MobileRelations',
        component: () => import('@/pages/mobile/Relations.vue'),
      },
      {
        path: 'profile',
        name: 'MobileProfile',
        component: () => import('@/pages/mobile/Profile.vue'),
      },
      {
        path: 'application-detail/:id',
        name: 'MobileApplicationDetail',
        component: () => import('@/pages/mobile/ApplicationDetail.vue'),
      },
      {
        path: 'game',
        name: 'MobileGameLobby',
        component: () => import('@/pages/mobile/GameLobby.vue'),
      },
      {
        path: 'game/room/:id',
        name: 'MobileGameRoom',
        component: () => import('@/pages/mobile/GameRoom.vue'),
      },
      {
        path: 'gobang/room/:id',
        name: 'MobileGobangRoom',
        component: () => import('@/pages/mobile/GobangRoom.vue'),
      },
      {
        path: 'game/tasks',
        name: 'MobileGameTasks',
        component: () => import('@/pages/mobile/GameTasks.vue'),
      },
    ],
  },
  {
    path: '/admin',
    name: 'AdminLayout',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/pages/admin/Dashboard.vue'),
      },
      {
        path: 'applications',
        name: 'AdminApplications',
        component: () => import('@/pages/admin/Applications.vue'),
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/pages/admin/Users.vue'),
      },
      {
        path: 'notifications',
        name: 'AdminNotifications',
        component: () => import('@/pages/admin/Notifications.vue'),
      },
      {
        path: 'system',
        name: 'AdminSystem',
        component: () => import('@/pages/admin/System.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

// 路由守卫
let authChecked = false

router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth) {
    if (!userStore.token) {
      authChecked = false
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }

    if (!authChecked) {
      try {
        await authAPI.getUserInfo()
        authChecked = true
      } catch {
        authChecked = false
        userStore.clearUserInfo()
        next({ path: '/login', query: { redirect: to.fullPath } })
        return
      }
    }

    next()
    return
  }

  next()
})

export default router
