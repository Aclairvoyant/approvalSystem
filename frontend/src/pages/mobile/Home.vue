<template>
  <div class="home-container">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="circle circle-1"></div>
      <div class="circle circle-2"></div>
      <div class="circle circle-3"></div>
    </div>

    <!-- 头部区域 -->
    <div class="header">
      <div class="logo-wrapper">
        <div class="logo">
          <div class="logo-icon">📋</div>
        </div>
      </div>
      <h1 class="title">审批管理系统</h1>
      <p class="subtitle">
        <span class="wave">👋</span>
        欢迎回来，{{ userStore.realName || userStore.username }}
      </p>
    </div>

    <!-- 快捷入口 -->
    <div class="quick-links">
      <router-link
        to="/mobile/applications"
        class="quick-link"
        :style="{ animationDelay: '0.1s' }"
      >
        <div class="link-bg"></div>
        <div class="link-icon">📝</div>
        <div class="link-text">创建申请</div>
        <div class="link-arrow">→</div>
      </router-link>

      <router-link
        to="/mobile/approvals"
        class="quick-link"
        :style="{ animationDelay: '0.2s' }"
      >
        <div class="link-bg"></div>
        <div class="link-icon">✅</div>
        <div class="link-text">待审批</div>
        <div class="link-arrow">→</div>
      </router-link>

      <router-link
        to="/mobile/relations"
        class="quick-link"
        :style="{ animationDelay: '0.3s' }"
      >
        <div class="link-bg"></div>
        <div class="link-icon">👥</div>
        <div class="link-text">管理对象</div>
        <div class="link-arrow">→</div>
      </router-link>

      <router-link
        to="/mobile/profile"
        class="quick-link"
        :style="{ animationDelay: '0.4s' }"
      >
        <div class="link-bg"></div>
        <div class="link-icon">⚙️</div>
        <div class="link-text">个人设置</div>
        <div class="link-arrow">→</div>
      </router-link>
    </div>

    <!-- 底部装饰 -->
    <div class="footer-decoration">
      <div class="wave-line"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
  overflow: hidden;
}

/* 背景装饰 */
.bg-decoration {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  z-index: 0;
}

.circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  animation: float 20s infinite ease-in-out;
}

.circle-1 {
  width: 300px;
  height: 300px;
  top: -150px;
  right: -150px;
  animation-delay: 0s;
}

.circle-2 {
  width: 200px;
  height: 200px;
  bottom: -100px;
  left: -100px;
  animation-delay: 5s;
}

.circle-3 {
  width: 150px;
  height: 150px;
  top: 50%;
  left: 50%;
  animation-delay: 10s;
}

@keyframes float {
  0%, 100% {
    transform: translate(0, 0) scale(1);
    opacity: 0.3;
  }
  50% {
    transform: translate(20px, -20px) scale(1.1);
    opacity: 0.5;
  }
}

/* 头部区域 */
.header {
  text-align: center;
  margin-bottom: 40px;
  position: relative;
  z-index: 1;
  animation: slideDown 0.6s ease-out;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.logo-wrapper {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

.logo {
  width: 80px;
  height: 80px;
  background: white;
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
  animation: pulse 2s infinite ease-in-out;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
  }
  50% {
    transform: scale(1.05);
    box-shadow: 0 15px 40px rgba(0, 0, 0, 0.3);
  }
}

.logo-icon {
  font-size: 48px;
  animation: rotate 3s infinite ease-in-out;
}

@keyframes rotate {
  0%, 100% {
    transform: rotate(0deg);
  }
  50% {
    transform: rotate(10deg);
  }
}

.title {
  margin: 0 0 12px 0;
  color: white;
  font-size: 32px;
  font-weight: 700;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
  letter-spacing: 1px;
}

.subtitle {
  margin: 0;
  color: rgba(255, 255, 255, 0.9);
  font-size: 16px;
  font-weight: 400;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.wave {
  display: inline-block;
  animation: wave 2s infinite ease-in-out;
  transform-origin: 70% 70%;
}

@keyframes wave {
  0%, 100% {
    transform: rotate(0deg);
  }
  25% {
    transform: rotate(20deg);
  }
  75% {
    transform: rotate(-20deg);
  }
}

/* 快捷入口 */
.quick-links {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  position: relative;
  z-index: 1;
  padding-bottom: 60px;
}

.quick-link {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-radius: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  text-decoration: none;
  color: #333;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  animation: slideUp 0.6s ease-out backwards;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.link-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.quick-link:active {
  transform: scale(0.95);
}

.quick-link:active .link-bg {
  opacity: 0.1;
}

.link-icon {
  font-size: 48px;
  margin-bottom: 12px;
  transition: transform 0.3s ease;
  position: relative;
  z-index: 1;
}

.quick-link:active .link-icon {
  transform: scale(1.2) rotate(10deg);
}

.link-text {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  position: relative;
  z-index: 1;
  transition: color 0.3s ease;
}

.link-arrow {
  position: absolute;
  bottom: 12px;
  right: 12px;
  font-size: 20px;
  color: #667eea;
  opacity: 0;
  transform: translateX(-10px);
  transition: all 0.3s ease;
}

.quick-link:active .link-arrow {
  opacity: 1;
  transform: translateX(0);
}

/* 底部装饰 */
.footer-decoration {
  position: fixed;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 60px;
  z-index: 0;
}

.wave-line {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 200%;
  height: 100%;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 50% 50% 0 0;
  animation: waveMove 10s infinite linear;
}

@keyframes waveMove {
  0% {
    transform: translateX(0);
  }
  100% {
    transform: translateX(-50%);
  }
}

/* 响应式调整 */
@media (max-width: 360px) {
  .quick-links {
    gap: 12px;
  }

  .quick-link {
    padding: 24px 16px;
  }

  .link-icon {
    font-size: 40px;
  }

  .link-text {
    font-size: 14px;
  }

  .title {
    font-size: 28px;
  }
}
</style>
