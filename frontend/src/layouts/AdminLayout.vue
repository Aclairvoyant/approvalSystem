<template>
  <div class="admin-layout">
    <div class="admin-sidebar">
      <div class="logo">管理后台</div>
      <nav class="menu">
        <router-link to="/admin/dashboard" class="menu-item" :class="{ active: isActive('dashboard') }">
          📊 仪表盘
        </router-link>
        <router-link to="/admin/applications" class="menu-item" :class="{ active: isActive('applications') }">
          📋 申请管理
        </router-link>
        <router-link to="/admin/users" class="menu-item" :class="{ active: isActive('users') }">
          👥 用户管理
        </router-link>
        <router-link to="/admin/notifications" class="menu-item" :class="{ active: isActive('notifications') }">
          🔔 通知管理
        </router-link>
      </nav>
    </div>
    <div class="admin-content">
      <div class="admin-header">
        <div class="user-info">
          欢迎，{{ userStore.realName || userStore.username }}
          <a-button type="text" @click="handleLogout">退出</a-button>
        </div>
      </div>
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isActive = (name: string): boolean => {
  return route.path.includes(name)
}

const handleLogout = (): void => {
  userStore.clearUserInfo()
  router.push('/login')
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
}

.admin-sidebar {
  width: 250px;
  background: #001529;
  color: white;
  overflow-y: auto;
}

.logo {
  padding: 20px;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid #233141;
}

.menu {
  padding: 10px 0;
}

.menu-item {
  display: block;
  padding: 12px 20px;
  color: rgba(255, 255, 255, 0.7);
  text-decoration: none;
  transition: all 0.3s;
  border-left: 3px solid transparent;
}

.menu-item:hover,
.menu-item.active {
  color: white;
  background: rgba(255, 255, 255, 0.1);
  border-left-color: #667eea;
}

.admin-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.admin-header {
  padding: 16px 20px;
  background: white;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: flex-end;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 16px;
  color: #333;
}

router-view {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}
</style>
