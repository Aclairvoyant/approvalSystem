<template>
  <div class="admin-layout">
    <aside class="admin-sidebar">
      <div class="logo">
        <div class="logo-icon">
          <icon-apps />
        </div>
        <div class="logo-text">审批管理系统</div>
      </div>

      <nav class="menu" aria-label="后台导航">
        <router-link
          v-for="item in adminMenu"
          :key="item.path"
          :to="item.path"
          class="menu-item"
          :class="{ active: isActive(item.path) }"
        >
          <component :is="item.icon" class="menu-icon" />
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="version-info">
          <icon-code-square class="version-icon" />
          <span>v1.0.0</span>
        </div>
      </div>
    </aside>

    <div class="admin-content">
      <header class="admin-header">
        <div class="header-left">
          <a-button class="mobile-menu-button" type="text" @click="mobileMenuVisible = true">
            <template #icon><icon-menu-unfold /></template>
          </a-button>
          <div class="header-breadcrumb">
            <icon-home />
            <span class="breadcrumb-divider">/</span>
            <span class="breadcrumb-current">{{ currentPageTitle }}</span>
          </div>
        </div>

        <div class="user-info">
          <a-avatar :size="36" :style="{ backgroundColor: '#2563eb' }">
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
      </header>

      <main class="admin-main">
        <router-view />
      </main>
    </div>

    <a-drawer
      v-model:visible="mobileMenuVisible"
      title="后台导航"
      placement="left"
      :width="288"
      :footer="false"
      unmount-on-close
    >
      <nav class="mobile-menu" aria-label="手机后台导航">
        <router-link
          v-for="item in adminMenu"
          :key="item.path"
          :to="item.path"
          class="mobile-menu-item"
          :class="{ active: isActive(item.path) }"
          @click="mobileMenuVisible = false"
        >
          <component :is="item.icon" class="menu-icon" />
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, type Component } from 'vue'
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
  IconExport,
  IconMenuUnfold
} from '@arco-design/web-vue/es/icon'

interface AdminMenuItem {
  path: string
  label: string
  icon: Component
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const mobileMenuVisible = ref(false)

const adminMenu: AdminMenuItem[] = [
  { path: '/admin/dashboard', label: '仪表盘', icon: IconDashboard },
  { path: '/admin/applications', label: '申请管理', icon: IconFile },
  { path: '/admin/users', label: '用户管理', icon: IconUserGroup },
  { path: '/admin/notifications', label: '通知管理', icon: IconNotification },
  { path: '/admin/system', label: '系统配置', icon: IconSettings }
]

const currentPageTitle = computed(() => {
  const current = adminMenu.find((item) => route.path === item.path || route.path.startsWith(`${item.path}/`))
  return current?.label || '管理后台'
})

const isActive = (path: string): boolean => {
  return route.path === path || route.path.startsWith(`${path}/`)
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
  min-height: 100vh;
  overflow: hidden;
  background: var(--admin-bg);
}

.admin-sidebar {
  position: relative;
  z-index: 100;
  display: flex;
  width: 260px;
  flex: 0 0 260px;
  flex-direction: column;
  color: #ffffff;
  background: #172033;
  box-shadow: 8px 0 24px rgba(23, 32, 51, 0.12);
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 22px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.logo-icon {
  display: flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 8px;
  color: #93c5fd;
  background: rgba(37, 99, 235, 0.16);
  font-size: 22px;
}

.logo-text {
  min-width: 0;
  color: #f8fafc;
  font-size: 16px;
  font-weight: 700;
}

.menu {
  flex: 1;
  padding: 14px 10px;
  overflow-y: auto;
}

.menu-item,
.mobile-menu-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 12px 14px;
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.72);
  font-size: 14px;
  font-weight: 550;
  line-height: 1.3;
  text-decoration: none;
  transition: background 0.2s ease, color 0.2s ease;
}

.menu-item + .menu-item {
  margin-top: 4px;
}

.menu-icon {
  flex: 0 0 auto;
  font-size: 18px;
}

.menu-item:hover,
.menu-item.active {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.09);
}

.menu-item.active::before {
  position: absolute;
  top: 50%;
  left: 0;
  width: 3px;
  height: 22px;
  border-radius: 0 3px 3px 0;
  background: #60a5fa;
  content: '';
  transform: translateY(-50%);
}

.sidebar-footer {
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.version-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: rgba(255, 255, 255, 0.52);
  font-size: 13px;
}

.version-icon {
  font-size: 16px;
}

.admin-content {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
}

.admin-header {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 28px;
  background: #ffffff;
  border-bottom: 1px solid var(--admin-border);
}

.header-left {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.mobile-menu-button {
  display: none;
}

.header-breadcrumb {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  color: #86909c;
  font-size: 14px;
}

.breadcrumb-divider {
  color: #c9cdd4;
}

.breadcrumb-current {
  overflow: hidden;
  color: var(--admin-text);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-info {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.user-details {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  max-width: 132px;
  overflow: hidden;
  color: var(--admin-text);
  font-size: 14px;
  font-weight: 650;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  color: var(--admin-text-muted);
  font-size: 12px;
  line-height: 1.2;
}

.user-menu-btn {
  color: #4e5969;
}

.admin-main {
  flex: 1;
  min-width: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 24px 28px;
}

.mobile-menu {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mobile-menu-item {
  min-height: 44px;
  color: #4e5969;
  background: #f7f8fa;
}

.mobile-menu-item.active {
  color: #1d4ed8;
  background: #eaf2ff;
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
}

.menu::-webkit-scrollbar,
.admin-main::-webkit-scrollbar {
  width: 6px;
}

.menu::-webkit-scrollbar-track,
.admin-main::-webkit-scrollbar-track {
  background: transparent;
}

.menu::-webkit-scrollbar-thumb,
.admin-main::-webkit-scrollbar-thumb {
  border-radius: 3px;
  background: rgba(148, 163, 184, 0.6);
}

@media (max-width: 768px) {
  .admin-layout {
    display: block;
    min-width: 0;
    overflow-x: hidden;
  }

  .admin-sidebar {
    display: none;
  }

  .admin-content {
    min-height: 100vh;
  }

  .admin-header {
    gap: 10px;
    padding: 12px 14px;
  }

  .mobile-menu-button {
    display: inline-flex;
    flex: 0 0 auto;
  }

  .header-breadcrumb {
    font-size: 13px;
  }

  .user-details {
    display: none;
  }

  .admin-main {
    padding: 16px 14px 24px;
  }
}
</style>
