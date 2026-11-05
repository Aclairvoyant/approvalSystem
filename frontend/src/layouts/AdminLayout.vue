<template>
  <div class="admin-layout">
    <aside class="admin-sidebar">
      <div class="logo">
        <span class="logo-mark" aria-hidden="true">审</span>
        <span class="logo-text">审批管理后台</span>
      </div>

      <nav class="menu" aria-label="后台导航">
        <router-link
          v-for="item in adminMenu"
          :key="item.path"
          :to="item.path"
          class="menu-item"
          :class="{ active: isActive(item.path) }"
          :title="item.label"
        >
          <component :is="item.icon" class="menu-icon" />
          <span class="menu-label">{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">v1.0.0</div>
    </aside>

    <div class="admin-content">
      <header class="admin-header">
        <div class="header-left">
          <a-button class="mobile-menu-button" type="text" aria-label="打开导航" @click="mobileMenuVisible = true">
            <template #icon><icon-menu-unfold /></template>
          </a-button>
          <h1 class="header-title">{{ currentPageTitle }}</h1>
        </div>

        <a-dropdown trigger="click" @select="handleUserMenuSelect">
          <button type="button" class="user-info">
            <UserAvatar :src="userStore.avatar" :name="userStore.displayName" :size="32" />
            <span class="user-details">
              <span class="user-name">{{ userStore.displayName }}</span>
              <span class="user-role">{{ userStore.isAdmin ? '管理员' : '普通用户' }}</span>
            </span>
            <icon-down class="user-caret" />
          </button>
          <template #content>
            <a-doption value="mobile">
              <template #icon><icon-mobile /></template>
              返回移动端
            </a-doption>
            <a-doption value="logout" class="logout-option">
              <template #icon><icon-export /></template>
              退出登录
            </a-doption>
          </template>
        </a-dropdown>
      </header>

      <main class="admin-main">
        <router-view />
      </main>
    </div>

    <a-drawer
      v-model:visible="mobileMenuVisible"
      title="后台导航"
      placement="left"
      :width="264"
      :footer="false"
      unmount-on-close
    >
      <nav class="mobile-menu" aria-label="后台导航">
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
import { Message } from '@arco-design/web-vue'
import {
  IconDashboard,
  IconFile,
  IconUserGroup,
  IconNotification,
  IconSettings,
  IconDown,
  IconExport,
  IconMenuUnfold,
  IconMobile
} from '@arco-design/web-vue/es/icon'
import { useUserStore } from '@/store/modules/user'
import { endSession } from '@/services/session'
import UserAvatar from '@/components/UserAvatar.vue'

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
  { path: '/admin/notifications', label: '通知记录', icon: IconNotification },
  { path: '/admin/system', label: '系统配置', icon: IconSettings }
]

const isActive = (path: string): boolean => route.path === path || route.path.startsWith(`${path}/`)

const currentPageTitle = computed(() => adminMenu.find(item => isActive(item.path))?.label || '管理后台')

const handleUserMenuSelect = (value: string | number | Record<string, unknown> | undefined): void => {
  if (value === 'logout') {
    endSession()
    Message.success('已退出登录')
    router.replace('/login')
  } else if (value === 'mobile') {
    router.push('/mobile/home')
  }
}
</script>

<style scoped>
/* 整个后台固定在一屏内，只有主内容区滚动，头部和侧栏始终可见 */
.admin-layout {
  display: flex;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: var(--admin-bg);
}

.admin-sidebar {
  display: flex;
  flex: 0 0 232px;
  flex-direction: column;
  width: 232px;
  background: #172033;
  color: #fff;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 60px;
  padding: 0 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.logo-mark {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--accent);
  font-size: 14px;
  font-weight: 700;
}

.logo-text {
  overflow: hidden;
  color: #f8fafc;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
}

.menu {
  flex: 1;
  padding: 12px 8px;
  overflow-y: auto;
}

.menu-item,
.mobile-menu-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 10px 12px;
  border-radius: 6px;
  color: rgba(255, 255, 255, 0.68);
  font-size: 14px;
  text-decoration: none;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.menu-item + .menu-item {
  margin-top: 2px;
}

.menu-icon {
  flex: 0 0 auto;
  font-size: 18px;
}

.menu-item:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.06);
}

.menu-item.active {
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
  font-weight: 600;
}

.menu-item.active::before {
  position: absolute;
  top: 9px;
  bottom: 9px;
  left: 0;
  width: 3px;
  border-radius: 0 2px 2px 0;
  background: #a5b4fc;
  content: '';
}

.sidebar-footer {
  padding: 14px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.4);
  font-size: 12px;
}

.admin-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.admin-header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 60px;
  padding: 0 24px;
  border-bottom: 1px solid var(--admin-border);
  background: var(--admin-surface);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.header-title {
  overflow: hidden;
  color: var(--admin-text);
  font-size: 16px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mobile-menu-button {
  display: none;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 6px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
}

.user-info:hover {
  background: var(--surface-2);
}

.user-details {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
}

.user-name {
  max-width: 140px;
  overflow: hidden;
  color: var(--admin-text);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  color: var(--admin-text-muted);
  font-size: 12px;
  line-height: 1.3;
}

.user-caret {
  color: var(--admin-text-muted);
}

.admin-main {
  flex: 1;
  min-height: 0;
  padding: 24px;
  overflow-x: hidden;
  overflow-y: auto;
}

.mobile-menu {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mobile-menu-item {
  color: var(--ink-2);
}

.mobile-menu-item.active {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}

:deep(.logout-option) {
  color: var(--danger);
}

/* 平板宽度：侧栏收成只显示图标，给表格留出空间 */
@media (min-width: 769px) and (max-width: 1199px) {
  .admin-sidebar {
    flex-basis: 64px;
    width: 64px;
  }

  .logo {
    justify-content: center;
    padding: 0;
  }

  .logo-text,
  .menu-label,
  .sidebar-footer {
    display: none;
  }

  .menu-item {
    justify-content: center;
    padding: 10px 0;
  }
}

@media (max-width: 768px) {
  .admin-sidebar {
    display: none;
  }

  .admin-header {
    padding: 0 12px;
  }

  .mobile-menu-button {
    display: inline-flex;
  }

  .user-details {
    display: none;
  }

  .admin-main {
    padding: 16px 12px 24px;
  }
}
</style>
