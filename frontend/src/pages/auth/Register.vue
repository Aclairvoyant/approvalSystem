<template>
  <main class="auth-page">
    <div class="auth-panel">
      <header class="auth-header">
        <h1 class="auth-title">创建账号</h1>
        <p class="auth-subtitle">注册后添加对象，就可以互相发起申请</p>
      </header>

      <van-form class="auth-card" @submit="handleRegister">
        <van-field
          v-model.trim="form.username"
          name="username"
          label="用户名"
          placeholder="登录时使用"
          autocomplete="username"
          maxlength="50"
          :rules="[{ required: true, message: '请输入用户名' }]"
          clearable
        />
        <van-field
          v-model.trim="form.phone"
          name="phone"
          label="手机号"
          type="tel"
          placeholder="请输入手机号"
          autocomplete="tel"
          maxlength="11"
          :rules="[
            { required: true, message: '请输入手机号' },
            { pattern: PHONE_PATTERN, message: '请输入正确的手机号' }
          ]"
          clearable
        />
        <van-field
          v-model.trim="form.realName"
          name="realName"
          label="姓名"
          placeholder="对方看到的名字（选填）"
          autocomplete="name"
          maxlength="50"
          clearable
        />
        <van-field
          v-model.trim="form.email"
          name="email"
          label="邮箱"
          type="email"
          placeholder="用于接收验证码"
          autocomplete="email"
          maxlength="100"
          :rules="[
            { required: true, message: '请输入邮箱' },
            { pattern: EMAIL_PATTERN, message: '请输入正确的邮箱' }
          ]"
          clearable
        />
        <van-field
          v-model.trim="form.emailVerificationCode"
          name="emailVerificationCode"
          label="验证码"
          placeholder="邮箱验证码"
          autocomplete="one-time-code"
          maxlength="8"
          :rules="[{ required: true, message: '请输入验证码' }]"
        >
          <template #button>
            <van-button
              size="small"
              plain
              type="primary"
              native-type="button"
              :loading="sendingCode"
              :disabled="codeCountdown.running.value"
              @click="sendEmailCode"
            >
              {{ codeCountdown.running.value ? `${codeCountdown.remaining.value} 秒后重发` : '发送验证码' }}
            </van-button>
          </template>
        </van-field>
        <van-field
          v-model="form.password"
          type="password"
          name="password"
          label="密码"
          placeholder="至少 6 位"
          autocomplete="new-password"
          maxlength="64"
          :rules="[
            { required: true, message: '请输入密码' },
            { pattern: /^.{6,}$/, message: '密码至少 6 位' }
          ]"
        />
        <van-field
          v-model="form.confirmPassword"
          type="password"
          name="confirmPassword"
          label="确认密码"
          placeholder="再输入一次"
          autocomplete="new-password"
          maxlength="64"
          :rules="[
            { required: true, message: '请再输入一次密码' },
            { validator: validateConfirmPassword, message: '两次输入的密码不一致' }
          ]"
        />

        <div class="auth-agreement">
          <van-checkbox v-model="agreed" shape="square" icon-size="16">我已阅读并同意服务条款和隐私政策</van-checkbox>
        </div>

        <div class="auth-actions">
          <van-button block type="primary" native-type="submit" :loading="loading" loading-text="注册中...">
            注册
          </van-button>
        </div>
      </van-form>

      <p class="auth-footer">
        已有账号？<router-link to="/login">登录</router-link>
      </p>
    </div>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showFailToast } from 'vant'
import { authAPI } from '@/services/api'
import { useCountdown } from '@/composables/useCountdown'
import '@/styles/auth.css'

const PHONE_PATTERN = /^1[3-9]\d{9}$/
const EMAIL_PATTERN = /^[\w.+-]+@[\w-]+(\.[\w-]+)+$/

const router = useRouter()
const loading = ref(false)
const sendingCode = ref(false)
const agreed = ref(false)
const codeCountdown = useCountdown(60)

const form = reactive({
  username: '',
  phone: '',
  realName: '',
  email: '',
  emailVerificationCode: '',
  password: '',
  confirmPassword: '',
})

// 换了邮箱，旧验证码作废，允许立即重新发送
watch(() => form.email, () => {
  codeCountdown.reset()
  form.emailVerificationCode = ''
})

const validateConfirmPassword = (): boolean => form.password === form.confirmPassword

const sendEmailCode = async (): Promise<void> => {
  if (!EMAIL_PATTERN.test(form.email)) {
    showFailToast(form.email ? '邮箱格式不正确' : '请先输入邮箱')
    return
  }

  sendingCode.value = true
  try {
    await authAPI.sendEmailCode(form.email)
    showSuccessToast('验证码已发送')
    codeCountdown.start()
  } catch (error: any) {
    showFailToast(error.message || '发送失败')
  } finally {
    sendingCode.value = false
  }
}

const handleRegister = async (): Promise<void> => {
  if (!agreed.value) {
    showFailToast('请先勾选同意服务条款')
    return
  }

  loading.value = true
  try {
    await authAPI.register({
      username: form.username,
      phone: form.phone,
      realName: form.realName || undefined,
      email: form.email,
      emailVerificationCode: form.emailVerificationCode,
      password: form.password,
    })
    showSuccessToast('注册成功，请登录')
    await router.replace({ path: '/login', query: { username: form.username } })
  } catch (error: any) {
    showFailToast(error.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>
