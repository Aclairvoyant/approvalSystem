<template>
  <div class="login-page">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="circle circle-1"></div>
      <div class="circle circle-2"></div>
      <div class="circle circle-3"></div>
    </div>

    <!-- 登录卡片 -->
    <div class="login-wrapper">
      <div class="login-header">
        <div class="logo">
          <van-icon name="shield-o" size="48" color="#4f46e5" />
        </div>
        <h1 class="title">审批管理系统</h1>
        <p class="subtitle">欢迎回来，请登录您的账号</p>
      </div>

      <van-form @submit="handleLogin" class="login-form">
        <van-cell-group inset>
          <van-field
            v-model="form.username"
            name="username"
            label="用户名"
            placeholder="请输入用户名"
            :rules="[{ required: true, message: '请输入用户名' }]"
            left-icon="user-o"
            clearable
          />
          <van-field
            v-model="form.password"
            type="password"
            name="password"
            label="密码"
            placeholder="请输入密码"
            :rules="[{ required: true, message: '请输入密码' }]"
            left-icon="lock"
            clearable
          />
        </van-cell-group>

        <div class="form-actions">
          <van-button
            round
            block
            type="primary"
            native-type="submit"
            :loading="loading"
            loading-text="登录中..."
            class="login-btn"
          >
            登录
          </van-button>
        </div>
      </van-form>

      <div class="login-footer">
        <span class="hint">还没有账号？</span>
        <router-link to="/register" class="register-link">立即注册</router-link>
      </div>

      <!-- 其他登录方式 -->
      <div class="other-login">
        <van-divider>其他登录方式</van-divider>
        <div class="social-icons">
          <van-icon name="wechat" size="32" color="#07c160" />
          <van-icon name="alipay" size="32" color="#1677ff" />
          <van-icon name="phone-o" size="32" color="#ff6034" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showFailToast } from 'vant'
import { useUserStore } from '@/store/modules/user'
import { authAPI } from '@/services/api'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const handleLogin = async (): Promise<void> => {
  loading.value = true
  try {
    const response = await authAPI.login({
      username: form.username,
      password: form.password,
    })

    userStore.setUserInfo(response)
    showSuccessToast('登录成功')
    router.push('/mobile/applications')
  } catch (error: any) {
    showFailToast(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}

</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

/* 背景装饰圆圈 */
.bg-decoration {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
  pointer-events: none;
}

.circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
}

.circle-1 {
  width: 300px;
  height: 300px;
  top: -100px;
  right: -100px;
  animation: float 8s ease-in-out infinite;
}

.circle-2 {
  width: 200px;
  height: 200px;
  bottom: -50px;
  left: -50px;
  animation: float 6s ease-in-out infinite reverse;
}

.circle-3 {
  width: 150px;
  height: 150px;
  top: 50%;
  left: 10%;
  animation: float 10s ease-in-out infinite;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0) rotate(0deg);
  }
  50% {
    transform: translateY(-20px) rotate(5deg);
  }
}

/* 登录卡片 */
.login-wrapper {
  width: 100%;
  max-width: 400px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-radius: 24px;
  padding: 40px 24px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  position: relative;
  z-index: 1;
}

/* 头部 */
.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.logo {
  width: 80px;
  height: 80px;
  margin: 0 auto 16px;
  background: linear-gradient(135deg, #f0f4ff 0%, #e8ecff 100%);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(79, 70, 229, 0.2);
}

.title {
  font-size: 24px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 8px 0;
}

.subtitle {
  font-size: 14px;
  color: #6b7280;
  margin: 0;
}

/* 表单 */
.login-form {
  margin-bottom: 24px;
}

.login-form :deep(.van-cell-group--inset) {
  margin: 0;
  border-radius: 16px;
  overflow: hidden;
}

.login-form :deep(.van-cell) {
  padding: 16px;
}

.login-form :deep(.van-field__label) {
  width: auto;
  margin-right: 12px;
  color: #374151;
}

.form-actions {
  margin-top: 24px;
  padding: 0 16px;
}

.login-btn {
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
}

/* 底部链接 */
.login-footer {
  text-align: center;
  margin-bottom: 24px;
}

.hint {
  color: #6b7280;
  font-size: 14px;
}

.register-link {
  color: #4f46e5;
  font-weight: 600;
  text-decoration: none;
  margin-left: 4px;
}

/* 其他登录方式 */
.other-login {
  padding: 0 16px;
}

.other-login :deep(.van-divider) {
  color: #9ca3af;
  font-size: 12px;
  margin: 16px 0;
}

.social-icons {
  display: flex;
  justify-content: center;
  gap: 32px;
}

.social-icons :deep(.van-icon) {
  cursor: pointer;
  transition: transform 0.2s;
}

.social-icons :deep(.van-icon):hover {
  transform: scale(1.1);
}

/* 响应式适配 */
@media (max-width: 480px) {
  .login-wrapper {
    padding: 32px 16px;
    border-radius: 20px;
  }

  .title {
    font-size: 20px;
  }

  .logo {
    width: 64px;
    height: 64px;
  }
}
</style>
