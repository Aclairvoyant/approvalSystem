<template>
  <div class="mobile-layout">
    <div class="mobile-content">
      <router-view />
    </div>
    <div class="mobile-nav">
      <router-link to="/mobile/applications" class="nav-item" :class="{ active: isActive('applications') }">
        <div class="nav-icon">📝</div>
        <div class="nav-label">我的申请</div>
      </router-link>
      <router-link to="/mobile/approvals" class="nav-item" :class="{ active: isActive('approvals') }">
        <div class="nav-icon">✅</div>
        <div class="nav-label">待审批</div>
      </router-link>
      <router-link to="/mobile/relations" class="nav-item" :class="{ active: isActive('relations') }">
        <div class="nav-icon">👥</div>
        <div class="nav-label">我的对象</div>
      </router-link>
      <router-link to="/mobile/game" class="nav-item" :class="{ active: isActive('game') }">
        <div class="nav-icon">🎮</div>
        <div class="nav-label">游戏</div>
      </router-link>
      <router-link to="/mobile/profile" class="nav-item" :class="{ active: isActive('profile') }">
        <div class="nav-icon">👤</div>
        <div class="nav-label">个人中心</div>
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()

const isActive = (name: string): boolean => {
  return route.path.includes(name)
}
</script>

<style scoped>
.mobile-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

.mobile-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  position: relative;
}

.mobile-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-around;
  align-items: center;
  height: 60px;
  background: white;
  border-top: 1px solid #f0f0f0;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.1);
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  height: 100%;
  text-decoration: none;
  color: #999;
  transition: all 0.3s;
}

.nav-item.active {
  color: #667eea;
}

.nav-icon {
  font-size: 24px;
  margin-bottom: 4px;
}

.nav-label {
  font-size: 12px;
}
</style>
