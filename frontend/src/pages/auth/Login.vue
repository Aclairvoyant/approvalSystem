<template>
  <main class="auth-page">
    <div class="auth-panel">
      <header class="auth-header">
        <h1 class="auth-title">审批管理系统</h1>
        <p class="auth-subtitle">登录后处理你和对象之间的申请</p>
      </header>

      <van-form class="auth-card" @submit="handleLogin">
        <van-field
          v-model.trim="form.username"
          name="username"
          label="用户名"
          placeholder="请输入用户名"
          autocomplete="username"
          maxlength="50"
          :rules="[{ required: true, message: '请输入用户名' }]"
          clearable
        />
        <van-field
          v-model="form.password"
          type="password"
          name="password"
          label="密码"
          placeholder="请输入密码"
          autocomplete="current-password"
          :rules="[{ required: true, message: '请输入密码' }]"
          clearable
        />
        <div class="auth-actions">
          <van-button block type="primary" native-type="submit" :loading="loading" loading-text="登录中...">
            登录
          </van-button>
        </div>
      </van-form>

      <p class="auth-footer">
        还没有账号？<router-link to="/register">注册</router-link>
      </p>
    </div>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast } from 'vant'
import { useUserStore } from '@/store/modules/user'
import { authAPI } from '@/services/api'
import { homePathFor, safeRedirect } from '@/utils/navigation'
import '@/styles/auth.css'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)

const form = reactive({
  username: typeof route.query.username === 'string' ? route.query.username : '',
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
    // 有站内 redirect 就回到原页面，否则管理员进后台、普通用户进移动端首页
    await router.replace(safeRedirect(route.query.redirect) ?? homePathFor(response.role))
  } catch (error: any) {
    showFailToast(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>
