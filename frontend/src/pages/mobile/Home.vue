<template>
  <div class="home-container">
    <div class="header">
      <h1>审批管理系统</h1>
      <p>欢迎使用，{{ userStore.realName || userStore.username }}</p>
    </div>
    <div class="quick-links">
      <router-link to="/mobile/applications" class="quick-link">
        <div class="link-icon">📝</div>
        <div class="link-text">创建申请</div>
      </router-link>
      <router-link to="/mobile/approvals" class="quick-link">
        <div class="link-icon">✅</div>
        <div class="link-text">待审批</div>
      </router-link>
      <router-link to="/mobile/relations" class="quick-link">
        <div class="link-icon">👥</div>
        <div class="link-text">管理对象</div>
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
</script>

<style scoped>
.home-container {
  padding: 20px;
}

.header {
  text-align: center;
  margin-bottom: 30px;
}

.header h1 {
  margin: 0;
  color: #333;
  font-size: 28px;
}

.header p {
  margin: 10px 0 0 0;
  color: #999;
  font-size: 14px;
}

.quick-links {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.quick-link {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  text-decoration: none;
  color: #333;
  transition: transform 0.3s;
}

.quick-link:active {
  transform: scale(0.95);
}

.link-icon {
  font-size: 32px;
  margin-bottom: 8px;
}

.link-text {
  font-size: 14px;
  font-weight: 500;
}
</style>
