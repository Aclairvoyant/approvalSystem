<template>
  <div class="register-page">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="circle circle-1"></div>
      <div class="circle circle-2"></div>
      <div class="circle circle-3"></div>
    </div>

    <!-- 注册卡片 -->
    <div class="register-wrapper">
      <div class="register-header">
        <div class="logo">
          <van-icon name="user-circle-o" size="48" color="#4f46e5" />
        </div>
        <h1 class="title">创建账号</h1>
        <p class="subtitle">注册后即可使用审批管理系统</p>
      </div>

      <van-form @submit="handleRegister" class="register-form">
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
            v-model="form.phone"
            name="phone"
            label="手机号"
            placeholder="请输入手机号"
            :rules="[
              { required: true, message: '请输入手机号' },
              { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号' }
            ]"
            left-icon="phone-o"
            clearable
          />
          <van-field
            v-model="form.realName"
            name="realName"
            label="真实姓名"
            placeholder="请输入真实姓名（选填）"
            left-icon="contact"
            clearable
          />
          <van-field
            v-model="form.email"
            name="email"
            label="邮箱"
            placeholder="请输入邮箱"
            :rules="[
              { required: true, message: '请输入邮箱' },
              { pattern: /^[\w-]+(\.[\w-]+)*@[\w-]+(\.[\w-]+)+$/, message: '请输入正确的邮箱' }
            ]"
            left-icon="envelop-o"
            clearable
          />
          <van-field
            v-model="form.emailVerificationCode"
            name="emailVerificationCode"
            label="验证码"
            placeholder="请输入邮箱验证码"
            :rules="[{ required: true, message: '请输入验证码' }]"
            left-icon="shield-o"
            clearable
          >
            <template #button>
              <van-button
                size="small"
                type="primary"
                :disabled="countdown > 0 || !form.email"
                @click="sendEmailCode"
                class="code-btn"
              >
                {{ countdown > 0 ? `${countdown}秒后重试` : '发送验证码' }}
              </van-button>
            </template>
          </van-field>
          <van-field
            v-model="form.password"
            type="password"
            name="password"
            label="密码"
            placeholder="请输入密码（至少6位）"
            :rules="[
              { required: true, message: '请输入密码' },
              { pattern: /^.{6,}$/, message: '密码至少6位' }
            ]"
            left-icon="lock"
            clearable
          />
          <van-field
            v-model="form.confirmPassword"
            type="password"
            name="confirmPassword"
            label="确认密码"
            placeholder="请再次输入密码"
            :rules="[
              { required: true, message: '请确认密码' },
              { validator: validateConfirmPassword, message: '两次密码不一致' }
            ]"
            left-icon="lock"
            clearable
          />
        </van-cell-group>

        <!-- 服务条款 -->
        <div class="agreement">
          <van-checkbox v-model="agreed" shape="square" icon-size="16">
            <span class="agreement-text">
              我已阅读并同意
              <a href="#" @click.prevent>《服务条款》</a>
              和
              <a href="#" @click.prevent>《隐私政策》</a>
            </span>
          </van-checkbox>
        </div>

        <div class="form-actions">
          <van-button
            round
            block
            type="primary"
            native-type="submit"
            :loading="loading"
            :disabled="!agreed"
            loading-text="注册中..."
            class="register-btn"
          >
            立即注册
          </van-button>
        </div>
      </van-form>

      <div class="register-footer">
        <span class="hint">已有账号？</span>
        <router-link to="/login" class="login-link">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showFailToast, showLoadingToast, closeToast } from 'vant'
import { authAPI } from '@/services/api'

const router = useRouter()
const loading = ref(false)
const agreed = ref(false)
const countdown = ref(0)

const form = reactive({
  username: '',
  phone: '',
  realName: '',
  email: '',
  emailVerificationCode: '',
  password: '',
  confirmPassword: '',
})

const validateConfirmPassword = (): boolean => {
  return form.password === form.confirmPassword
}

// 发送邮箱验证码
const sendEmailCode = async (): Promise<void> => {
  if (!form.email) {
    showFailToast('请先输入邮箱')
    return
  }

  // 验证邮箱格式
  const emailPattern = /^[\w-]+(\.[\w-]+)*@[\w-]+(\.[\w-]+)+$/
  if (!emailPattern.test(form.email)) {
    showFailToast('邮箱格式不正确')
    return
  }

  showLoadingToast({ message: '发送中...', forbidClick: true })
  try {
    await authAPI.sendEmailCode(form.email)
    closeToast()
    showSuccessToast('验证码已发送，请查收邮件')

    // 开始倒计时（60秒）
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (error: any) {
    closeToast()
    showFailToast(error.message || '发送失败')
  }
}

const handleRegister = async (): Promise<void> => {
  if (!agreed.value) {
    showFailToast('请先同意服务条款')
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
    showSuccessToast('注册成功')
    router.push('/login')
  } catch (error: any) {
    showFailToast(error.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
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
  left: -100px;
  animation: float 8s ease-in-out infinite;
}

.circle-2 {
  width: 200px;
  height: 200px;
  bottom: -50px;
  right: -50px;
  animation: float 6s ease-in-out infinite reverse;
}

.circle-3 {
  width: 150px;
  height: 150px;
  top: 40%;
  right: 10%;
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

/* 注册卡片 */
.register-wrapper {
  width: 100%;
  max-width: 420px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-radius: 24px;
  padding: 32px 24px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  position: relative;
  z-index: 1;
  max-height: 90vh;
  overflow-y: auto;
}

/* 头部 */
.register-header {
  text-align: center;
  margin-bottom: 24px;
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
.register-form {
  margin-bottom: 16px;
}

.register-form :deep(.van-cell-group--inset) {
  margin: 0;
  border-radius: 16px;
  overflow: hidden;
}

.register-form :deep(.van-cell) {
  padding: 14px 16px;
}

.register-form :deep(.van-field__label) {
  width: 70px;
  margin-right: 8px;
  color: #374151;
  font-size: 14px;
}

.code-btn {
  font-size: 12px;
  padding: 0 12px;
  height: 28px;
}

.code-btn:disabled {
  opacity: 0.6;
}

/* 服务条款 */
.agreement {
  margin: 16px 16px 0;
}

.agreement-text {
  font-size: 12px;
  color: #6b7280;
}

.agreement-text a {
  color: #4f46e5;
  text-decoration: none;
}

.form-actions {
  margin-top: 20px;
  padding: 0 16px;
}

.register-btn {
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
}

.register-btn:disabled {
  opacity: 0.6;
}

/* 底部链接 */
.register-footer {
  text-align: center;
  margin-top: 16px;
}

.hint {
  color: #6b7280;
  font-size: 14px;
}

.login-link {
  color: #4f46e5;
  font-weight: 600;
  text-decoration: none;
  margin-left: 4px;
}

/* 响应式适配 */
@media (max-width: 480px) {
  .register-wrapper {
    padding: 24px 16px;
    border-radius: 20px;
  }

  .title {
    font-size: 20px;
  }

  .logo {
    width: 64px;
    height: 64px;
  }

  .register-form :deep(.van-field__label) {
    width: 60px;
  }
}
</style>
