import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LoginResponse, User } from '@/services/api'

const STORAGE_KEYS = [
  'token',
  'userId',
  'username',
  'realName',
  'phone',
  'email',
  'avatar',
  'role',
  'voiceNotificationEnabled',
] as const

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userId = ref<number>(parseInt(localStorage.getItem('userId') || '0'))
  const username = ref<string>(localStorage.getItem('username') || '')
  const realName = ref<string>(localStorage.getItem('realName') || '')
  const phone = ref<string>(localStorage.getItem('phone') || '')
  const email = ref<string>(localStorage.getItem('email') || '')
  const avatar = ref<string>(localStorage.getItem('avatar') || '')
  const role = ref<number>(parseInt(localStorage.getItem('role') || '0'))
  const voiceNotificationEnabled = ref<boolean>(
    localStorage.getItem('voiceNotificationEnabled') === 'true'
  )

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 1)
  const displayName = computed(() => realName.value || username.value)

  const persist = (): void => {
    localStorage.setItem('token', token.value)
    localStorage.setItem('userId', String(userId.value))
    localStorage.setItem('username', username.value)
    localStorage.setItem('realName', realName.value)
    localStorage.setItem('phone', phone.value)
    localStorage.setItem('email', email.value)
    localStorage.setItem('avatar', avatar.value)
    localStorage.setItem('role', String(role.value))
    localStorage.setItem('voiceNotificationEnabled', String(voiceNotificationEnabled.value))
  }

  const setUserInfo = (data: LoginResponse): void => {
    token.value = data.token || ''
    userId.value = data.userId || 0
    username.value = data.username || ''
    realName.value = data.realName || ''
    phone.value = data.phone || ''
    email.value = data.email || ''
    avatar.value = data.avatar || ''
    role.value = data.role || 0
    voiceNotificationEnabled.value = data.voiceNotificationEnabled || false
    persist()
  }

  /**
   * 用 /auth/user-info 返回的最新资料刷新本地缓存（保留 token）。
   * 管理员改了角色或语音通知权限后，用户不用重新登录就能生效。
   */
  const updateProfile = (user: Partial<User>): void => {
    if (user.id !== undefined) userId.value = user.id
    if (user.username !== undefined) username.value = user.username || ''
    if (user.realName !== undefined) realName.value = user.realName || ''
    if (user.phone !== undefined) phone.value = user.phone || ''
    if (user.email !== undefined) email.value = user.email || ''
    if (user.avatar !== undefined) avatar.value = user.avatar || ''
    if (user.role !== undefined) role.value = user.role || 0
    if (user.voiceNotificationEnabled !== undefined) {
      voiceNotificationEnabled.value = !!user.voiceNotificationEnabled
    }
    persist()
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
    voiceNotificationEnabled.value = false
    STORAGE_KEYS.forEach(key => localStorage.removeItem(key))
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
    voiceNotificationEnabled,
    isLoggedIn,
    isAdmin,
    displayName,
    setUserInfo,
    updateProfile,
    clearUserInfo,
  }
})
