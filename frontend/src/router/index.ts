import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/modules/user'

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
    component: () => import('@/pages/mobile/Home.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/mobile',
    name: 'MobileLayout',
    component: () => import('@/layouts/MobileLayout.vue'),
    meta: { requiresAuth: true },
    children: [
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
    ],
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth) {
    if (!userStore.token) {
      next('/login')
    } else {
      next()
    }
  } else {
    next()
  }
})

export default router
