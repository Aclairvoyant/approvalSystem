<template>
  <div class="mobile-layout">
    <div class="mobile-content">
      <router-view />
    </div>

    <nav class="mobile-nav">
      <router-link to="/mobile/home" class="nav-item" :class="{ active: isActive('home') }">
        <van-icon name="wap-home-o" class="nav-icon" />
        <div class="nav-label">首页</div>
      </router-link>
      <router-link to="/mobile/daily" class="nav-item" :class="{ active: isActive('daily') }">
        <van-icon name="notes-o" class="nav-icon" />
        <div class="nav-label">日常</div>
      </router-link>
      <router-link to="/mobile/applications" class="nav-item" :class="{ active: isActive('applications') || isActive('approvals') }">
        <van-icon name="records-o" class="nav-icon" />
        <div class="nav-label">申请</div>
      </router-link>
      <router-link to="/mobile/game" class="nav-item" :class="{ active: isActive('game') }">
        <van-icon name="flag-o" class="nav-icon" />
        <div class="nav-label">游戏</div>
      </router-link>
      <router-link to="/mobile/profile" class="nav-item" :class="{ active: isActive('profile') }">
        <van-icon name="user-o" class="nav-icon" />
        <div class="nav-label">我的</div>
      </router-link>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()

const isActive = (name: string): boolean => route.path.includes(name)
</script>

<style scoped>
.mobile-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: #f6f7f9;
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
  z-index: 1000;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  height: 60px;
  background: #fff;
  border-top: 1px solid #eceff3;
  box-shadow: 0 -2px 10px rgba(31, 35, 41, 0.06);
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 0;
  text-decoration: none;
  color: #8a8f99;
}

.nav-item.active {
  color: #4f46e5;
}

.nav-icon {
  font-size: 22px;
  margin-bottom: 3px;
}

.nav-label {
  font-size: 11px;
  line-height: 1;
}
</style>
