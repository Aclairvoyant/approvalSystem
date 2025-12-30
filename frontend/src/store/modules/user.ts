import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { components } from '@/services/api.d'

type LoginResponse = components['schemas']['LoginResponse']

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userId = ref<number>(parseInt(localStorage.getItem('userId') || '0'))
  const username = ref<string>(localStorage.getItem('username') || '')
  const realName = ref<string>(localStorage.getItem('realName') || '')
  const phone = ref<string>(localStorage.getItem('phone') || '')
  const email = ref<string>(localStorage.getItem('email') || '')
  const avatar = ref<string>(localStorage.getItem('avatar') || '')
  const role = ref<number>(parseInt(localStorage.getItem('role') || '0'))

  // 计算属性
  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 1)

  const setUserInfo = (data: LoginResponse): void => {
    token.value = data.token || ''
    userId.value = data.userId || 0
    username.value = data.username || ''
    realName.value = data.realName || ''
    phone.value = data.phone || ''
    email.value = data.email || ''
    avatar.value = data.avatar || ''
    role.value = data.role || 0

    // 保存到本地存储
    localStorage.setItem('token', data.token || '')
    localStorage.setItem('userId', String(data.userId || 0))
    localStorage.setItem('username', data.username || '')
    localStorage.setItem('realName', data.realName || '')
    localStorage.setItem('phone', data.phone || '')
    localStorage.setItem('email', data.email || '')
    localStorage.setItem('avatar', data.avatar || '')
    localStorage.setItem('role', String(data.role || 0))
  }

  const clearUserInfo = (): void => {
    token.value = ''
    userId.value = 0
    username.value = ''
    realName.value = ''
    phone.value = ''
    email.value = ''
    avatar.value = ''
    role.value = 0

    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    localStorage.removeItem('realName')
    localStorage.removeItem('phone')
    localStorage.removeItem('email')
    localStorage.removeItem('avatar')
    localStorage.removeItem('role')
  }

  return {
    token,
    userId,
    username,
    realName,
    phone,
    email,
    avatar,
    role,
    isLoggedIn,
    isAdmin,
    setUserInfo,
    clearUserInfo,
  }
})
