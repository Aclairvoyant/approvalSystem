<template>
  <div class="admin-layout">
    <div class="admin-sidebar">
      <div class="logo">
        <div class="logo-icon">
          <icon-apps />
        </div>
        <div class="logo-text">审批管理系统</div>
      </div>
      <nav class="menu">
        <router-link to="/admin/dashboard" class="menu-item" :class="{ active: isActive('dashboard') }">
          <icon-dashboard class="menu-icon" />
          <span>仪表盘</span>
        </router-link>
        <router-link to="/admin/applications" class="menu-item" :class="{ active: isActive('applications') }">
          <icon-file class="menu-icon" />
          <span>申请管理</span>
        </router-link>
        <router-link to="/admin/users" class="menu-item" :class="{ active: isActive('users') }">
          <icon-user-group class="menu-icon" />
          <span>用户管理</span>
        </router-link>
        <router-link to="/admin/notifications" class="menu-item" :class="{ active: isActive('notifications') }">
          <icon-notification class="menu-icon" />
          <span>通知管理</span>
        </router-link>
      </nav>
      <div class="sidebar-footer">
        <div class="version-info">
          <icon-code-square class="version-icon" />
          <span>v1.0.0</span>
        </div>
      </div>
    </div>
    <div class="admin-content">
      <div class="admin-header">
        <div class="header-breadcrumb">
          <icon-home />
          <span class="breadcrumb-divider">/</span>
          <span class="breadcrumb-current">{{ currentPageTitle }}</span>
        </div>
        <div class="user-info">
          <a-avatar :size="36" :style="{ backgroundColor: '#667eea' }">
            {{ getInitials(userStore.realName || userStore.username) }}
          </a-avatar>
          <div class="user-details">
            <div class="user-name">{{ userStore.realName || userStore.username }}</div>
            <div class="user-role">管理员</div>
          </div>
          <a-dropdown @select="handleUserMenuSelect">
            <a-button type="text" class="user-menu-btn">
              <icon-down />
            </a-button>
            <template #content>
              <a-doption value="profile">
                <icon-user /> 个人资料
              </a-doption>
              <a-doption value="settings">
                <icon-settings /> 设置
              </a-doption>
              <a-doption value="logout" class="logout-option">
                <icon-export /> 退出登录
              </a-doption>
            </template>
          </a-dropdown>
        </div>
      </div>
      <div class="admin-main">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { Message } from '@arco-design/web-vue'
import {
  IconApps,
  IconDashboard,
  IconFile,
  IconUserGroup,
  IconNotification,
  IconCodeSquare,
  IconHome,
  IconDown,
  IconUser,
  IconSettings,
  IconExport
} from '@arco-design/web-vue/es/icon'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const currentPageTitle = computed(() => {
  const titles: Record<string, string> = {
    '/admin/dashboard': '仪表盘',
    '/admin/applications': '申请管理',
    '/admin/users': '用户管理',
    '/admin/notifications': '通知管理'
  }
  return titles[route.path] || '管理后台'
})

const isActive = (name: string): boolean => {
  return route.path.includes(name)
}

const getInitials = (name: string): string => {
  if (!name) return 'A'
  return name.charAt(0).toUpperCase()
}

const handleUserMenuSelect = (value: string | number): void => {
  if (value === 'logout') {
    userStore.clearUserInfo()
    Message.success('已退出登录')
    router.push('/login')
  } else if (value === 'profile') {
    Message.info('个人资料功能开发中')
  } else if (value === 'settings') {
    Message.info('设置功能开发中')
  }
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
  background: #f5f6f8;
}

.admin-sidebar {
  width: 260px;
  background: linear-gradient(180deg, #1d2532 0%, #0f1419 100%);
  color: white;
  display: flex;
  flex-direction: column;
  box-shadow: 4px 0 12px rgba(0, 0, 0, 0.1);
  position: relative;
  z-index: 100;
}

.logo {
  padding: 24px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo-icon {
  width: 42px;
  height: 42px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.logo-text {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.3px;
  background: linear-gradient(135deg, #ffffff 0%, #e0e7ff 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.menu {
  flex: 1;
  padding: 16px 12px;
  overflow-y: auto;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  color: rgba(255, 255, 255, 0.7);
  text-decoration: none;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border-radius: 10px;
  margin-bottom: 6px;
  position: relative;
  font-size: 14px;
  font-weight: 500;
}

.menu-icon {
  font-size: 18px;
  transition: transform 0.3s;
}

.menu-item:hover {
  color: white;
  background: rgba(255, 255, 255, 0.08);
  transform: translateX(4px);
}

.menu-item:hover .menu-icon {
  transform: scale(1.1);
}

.menu-item.active {
  color: white;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.2) 0%, rgba(118, 75, 162, 0.2) 100%);
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

.menu-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 24px;
  background: linear-gradient(180deg, #667eea 0%, #764ba2 100%);
  border-radius: 0 2px 2px 0;
}

.sidebar-footer {
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.version-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 13px;
}

.version-icon {
  font-size: 16px;
}

.admin-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.admin-header {
  padding: 16px 32px;
  background: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  position: relative;
  z-index: 10;
}

.header-breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #86909c;
  font-size: 14px;
}

.breadcrumb-divider {
  color: #c9cdd4;
}

.breadcrumb-current {
  color: #1d2129;
  font-weight: 500;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-details {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  line-height: 1.2;
}

.user-role {
  font-size: 12px;
  color: #86909c;
  line-height: 1.2;
}

.user-menu-btn {
  color: #4e5969;
}

.admin-main {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
}

:deep(.arco-dropdown-option) {
  display: flex;
  align-items: center;
  gap: 8px;
}

:deep(.logout-option) {
  color: #f53f3f;
}

:deep(.arco-avatar) {
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

/* Scrollbar styling */
.menu::-webkit-scrollbar,
.admin-main::-webkit-scrollbar {
  width: 6px;
}

.menu::-webkit-scrollbar-track {
  background: transparent;
}

.menu::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 3px;
}

.menu::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.3);
}

.admin-main::-webkit-scrollbar-track {
  background: #f5f6f8;
}

.admin-main::-webkit-scrollbar-thumb {
  background: #c9cdd4;
  border-radius: 3px;
}

.admin-main::-webkit-scrollbar-thumb:hover {
  background: #a5aab3;
}
</style>
