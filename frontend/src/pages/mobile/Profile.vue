<template>
  <div class="profile-page">
    <!-- 顶部背景 -->
    <div class="profile-header">
      <div class="header-bg"></div>
      <div class="header-content">
        <!-- 头像区域 -->
        <div class="avatar-section" @click="handleAvatarClick">
          <van-image
            round
            width="80"
            height="80"
            :src="avatarUrl"
            fit="cover"
            class="user-avatar"
          >
            <template #error>
              <div class="avatar-fallback">
                {{ avatarText }}
              </div>
            </template>
          </van-image>
          <div class="avatar-edit-icon">
            <van-icon name="photograph" />
          </div>
          <van-tag
            v-if="userStore.isAdmin"
            type="warning"
            class="role-badge"
          >
            管理员
          </van-tag>
          <van-tag
            v-else
            type="primary"
            class="role-badge"
          >
            普通用户
          </van-tag>
        </div>

        <!-- 用户名和欢迎语 -->
        <div class="user-name">{{ userStore.realName || userStore.username }}</div>
        <div class="welcome-text">欢迎使用审批管理系统</div>
      </div>
    </div>

    <!-- 用户信息卡片 -->
    <div class="info-section">
      <van-cell-group inset title="个人信息">
        <van-cell
          title="用户名"
          :value="userStore.username"
          icon="user-o"
        />
        <van-cell
          title="真实姓名"
          :value="userStore.realName || '未设置'"
          icon="contact"
          is-link
          @click="showEditField('realName')"
        />
        <van-cell
          title="手机号"
          :value="formatPhone(userStore.phone)"
          icon="phone-o"
          is-link
          @click="showEditField('phone')"
        />
        <van-cell
          title="邮箱"
          :value="userStore.email || '未设置'"
          icon="envelop-o"
          is-link
          @click="showEmailDialog = true"
        />
        <van-cell
          title="用户ID"
          :value="String(userStore.userId)"
          icon="idcard"
        />
      </van-cell-group>

      <!-- 统计信息 -->
      <div class="stats-card">
        <div class="stat-item" @click="navigateTo('/mobile/approvals')">
          <div class="stat-value">{{ stats.pendingForMe }}</div>
          <div class="stat-label">待我审批</div>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item" @click="navigateTo('/mobile/applications')">
          <div class="stat-value">{{ stats.myApproved }}</div>
          <div class="stat-label">已通过</div>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item" @click="navigateTo('/mobile/relations')">
          <div class="stat-value">{{ stats.myRelations }}</div>
          <div class="stat-label">我的对象</div>
        </div>
      </div>

      <!-- 功能菜单 -->
      <van-cell-group inset title="功能菜单" class="menu-section">
        <van-cell
          title="我的申请"
          icon="description"
          is-link
          @click="navigateTo('/mobile/applications')"
        >
          <template #right-icon>
            <van-badge :content="stats.myPending > 0 ? stats.myPending : ''" :show-zero="false">
              <van-icon name="arrow" />
            </van-badge>
          </template>
        </van-cell>
        <van-cell
          title="待审批"
          icon="todo-list-o"
          is-link
          @click="navigateTo('/mobile/approvals')"
        >
          <template #right-icon>
            <van-badge :content="stats.pendingForMe > 0 ? stats.pendingForMe : ''" :show-zero="false">
              <van-icon name="arrow" />
            </van-badge>
          </template>
        </van-cell>
        <van-cell
          title="我的审批记录"
          icon="passed"
          is-link
          @click="openApprovalHistory"
        />
        <van-cell
          title="我的对象"
          icon="friends-o"
          is-link
          @click="navigateTo('/mobile/relations')"
        />
        <van-cell
          v-if="userStore.isAdmin"
          title="管理后台"
          icon="setting-o"
          is-link
          @click="navigateTo('/admin/dashboard')"
        />
      </van-cell-group>

      <!-- 设置菜单 -->
      <van-cell-group inset title="设置" class="settings-section">
        <van-cell
          title="修改密码"
          icon="lock"
          is-link
          @click="showPasswordDialog = true"
        />
        <van-cell
          title="刷新数据"
          icon="replay"
          is-link
          @click="refreshData"
        />
        <van-cell
          title="关于我们"
          icon="info-o"
          is-link
          @click="showAbout"
        />
      </van-cell-group>

      <!-- 退出登录按钮 -->
      <div class="logout-section">
        <van-button
          type="danger"
          block
          round
          size="large"
          @click="handleLogout"
        >
          <van-icon name="revoke" style="margin-right: 8px;" />
          退出登录
        </van-button>
      </div>

      <!-- 版本信息 -->
      <div class="version-info">
        审批管理系统 v1.0.0
      </div>
    </div>

    <!-- 编辑字段弹窗 -->
    <van-dialog
      v-model:show="showEditDialog"
      :title="editDialogTitle"
      show-cancel-button
      @confirm="handleUpdateField"
    >
      <div class="edit-form">
        <van-field
          v-model="editValue"
          :label="editFieldLabel"
          :placeholder="editFieldPlaceholder"
          :type="editFieldType"
        />
      </div>
    </van-dialog>

    <!-- 修改密码弹窗 -->
    <van-dialog
      v-model:show="showPasswordDialog"
      title="修改密码"
      show-cancel-button
      @confirm="handleChangePassword"
    >
      <div class="password-form">
        <van-field
          v-model="passwordForm.oldPassword"
          type="password"
          label="原密码"
          placeholder="请输入原密码"
        />
        <van-field
          v-model="passwordForm.newPassword"
          type="password"
          label="新密码"
          placeholder="请输入新密码"
        />
        <van-field
          v-model="passwordForm.confirmPassword"
          type="password"
          label="确认密码"
          placeholder="请再次输入新密码"
        />
      </div>
    </van-dialog>

    <!-- 修改邮箱弹窗 -->
    <van-dialog
      v-model:show="showEmailDialog"
      title="修改邮箱"
      show-cancel-button
      @confirm="handleChangeEmail"
    >
      <div class="email-form">
        <van-field
          v-model="emailForm.password"
          type="password"
          label="当前密码"
          placeholder="请输入当前密码"
        />
        <van-field
          v-model="emailForm.newEmail"
          type="email"
          label="新邮箱"
          placeholder="请输入新邮箱"
        />
        <van-field
          v-model="emailForm.verificationCode"
          label="验证码"
          placeholder="请输入验证码"
        >
          <template #button>
            <van-button
              size="small"
              type="primary"
              :disabled="emailCountdown > 0 || !emailForm.newEmail"
              @click="sendEmailVerificationCode"
            >
              {{ emailCountdown > 0 ? `${emailCountdown}秒后重试` : '发送验证码' }}
            </van-button>
          </template>
        </van-field>
      </div>
    </van-dialog>

    <!-- 审批记录弹窗 -->
    <van-popup
      v-model:show="showApprovalHistory"
      position="bottom"
      round
      :style="{ height: '80%' }"
    >
      <div class="approval-history-popup">
        <div class="popup-header">
          <span class="popup-title">我的审批记录</span>
          <van-icon name="cross" class="popup-close" @click="showApprovalHistory = false" />
        </div>

        <div class="popup-content">
          <van-loading v-if="approvalHistoryLoading" class="loading-state">加载中...</van-loading>

          <van-empty v-else-if="approvalHistory.length === 0" description="暂无审批记录" />

          <div v-else class="approval-list">
            <div
              v-for="app in approvalHistory"
              :key="app.id"
              class="approval-item"
              @click="goToApplicationDetail(app.id)"
            >
              <div class="approval-header">
                <span class="approval-title">{{ app.title }}</span>
                <van-tag :type="getStatusType(app.status)" size="medium">
                  {{ getStatusText(app.status) }}
                </van-tag>
              </div>
              <div class="approval-desc">{{ app.description }}</div>
              <div class="approval-footer">
                <span class="approval-applicant">
                  <van-icon name="user-o" />
                  申请人ID: {{ app.applicantId }}
                </span>
                <span class="approval-time">
                  <van-icon name="clock-o" />
                  {{ formatDate(app.approvedAt || app.createdAt) }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </van-popup>

    <!-- 头像上传 -->
    <input
      ref="avatarInput"
      type="file"
      accept="image/*"
      style="display: none"
      @change="handleAvatarChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast, showLoadingToast, showConfirmDialog, closeToast } from 'vant'
import { useUserStore } from '@/store/modules/user'
import { authAPI, adminAPI, applicationAPI, type UserStats, type Application } from '@/services/api'

const router = useRouter()
const userStore = useUserStore()
const avatarInput = ref<HTMLInputElement | null>(null)

// 头像URL
const avatarUrl = computed(() => {
  if (userStore.avatar) {
    // 如果是相对路径，拼接后端地址
    if (userStore.avatar.startsWith('/')) {
      return `http://localhost:8080${userStore.avatar}`
    }
    return userStore.avatar
  }
  return ''
})

// 头像文字
const avatarText = computed(() => {
  const name = userStore.realName || userStore.username
  return name ? name.charAt(0).toUpperCase() : 'U'
})

// 统计数据
const stats = ref<UserStats>({
  myPending: 0,
  myApproved: 0,
  myRejected: 0,
  pendingForMe: 0,
  myRelations: 0
})

// 编辑字段
const showEditDialog = ref(false)
const editField = ref<'realName' | 'phone' | 'email'>('realName')
const editValue = ref('')

const editDialogTitle = computed(() => {
  const titles: Record<string, string> = {
    realName: '修改真实姓名',
    phone: '修改手机号',
    email: '修改邮箱'
  }
  return titles[editField.value]
})

const editFieldLabel = computed(() => {
  const labels: Record<string, string> = {
    realName: '真实姓名',
    phone: '手机号',
    email: '邮箱'
  }
  return labels[editField.value]
})

const editFieldPlaceholder = computed(() => {
  const placeholders: Record<string, string> = {
    realName: '请输入真实姓名',
    phone: '请输入手机号',
    email: '请输入邮箱'
  }
  return placeholders[editField.value]
})

const editFieldType = computed(() => {
  if (editField.value === 'phone') return 'tel'
  if (editField.value === 'email') return 'text'
  return 'text'
})

// 修改密码表单
const showPasswordDialog = ref(false)
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 审批记录
const showApprovalHistory = ref(false)
const approvalHistory = ref<Application[]>([])
const approvalHistoryLoading = ref(false)

// 修改邮箱
const showEmailDialog = ref(false)
const emailCountdown = ref(0)
const emailForm = ref({
  password: '',
  newEmail: '',
  verificationCode: ''
})

// 格式化手机号
const formatPhone = (phone: string): string => {
  if (!phone) return '未设置'
  if (phone.length === 11) {
    return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
  }
  return phone
}

// 显示编辑弹窗
const showEditField = (field: 'realName' | 'phone' | 'email'): void => {
  editField.value = field
  editValue.value = userStore[field] || ''
  showEditDialog.value = true
}

// 更新字段
const handleUpdateField = async (): Promise<void> => {
  if (!editValue.value.trim()) {
    showToast('请输入内容')
    return
  }

  // 验证手机号
  if (editField.value === 'phone' && !/^1[3-9]\d{9}$/.test(editValue.value)) {
    showToast('请输入正确的手机号')
    return
  }

  // 验证邮箱
  if (editField.value === 'email' && !/^[\w-]+(\.[\w-]+)*@[\w-]+(\.[\w-]+)+$/.test(editValue.value)) {
    showToast('请输入正确的邮箱')
    return
  }

  try {
    showLoadingToast({ message: '保存中...', forbidClick: true })
    const updateData: Record<string, string> = {}
    updateData[editField.value] = editValue.value

    const response = await authAPI.updateProfile(updateData)

    // 更新本地存储
    if (editField.value === 'realName') {
      userStore.realName = editValue.value
      localStorage.setItem('realName', editValue.value)
    } else if (editField.value === 'phone') {
      userStore.phone = editValue.value
      localStorage.setItem('phone', editValue.value)
    } else if (editField.value === 'email') {
      userStore.email = editValue.value
      localStorage.setItem('email', editValue.value)
    }

    closeToast()
    showSuccessToast('修改成功')
  } catch (error: any) {
    closeToast()
    showToast(error.message || '修改失败')
  }
}

// 页面跳转
const navigateTo = (path: string): void => {
  router.push(path)
}

// 退出登录
const handleLogout = async (): Promise<void> => {
  try {
    await showConfirmDialog({
      title: '确认退出',
      message: '确定要退出登录吗？',
    })
    userStore.clearUserInfo()
    showSuccessToast('已退出登录')
    router.push('/login')
  } catch {
    // 用户取消
  }
}

// 修改密码
const handleChangePassword = async (): Promise<void> => {
  const { oldPassword, newPassword, confirmPassword } = passwordForm.value

  if (!oldPassword || !newPassword || !confirmPassword) {
    showToast('请填写完整信息')
    return
  }

  if (newPassword !== confirmPassword) {
    showToast('两次输入的密码不一致')
    return
  }

  if (newPassword.length < 6) {
    showToast('密码长度至少6位')
    return
  }

  try {
    showLoadingToast({ message: '修改中...', forbidClick: true })
    await authAPI.changePassword(oldPassword, newPassword)
    closeToast()
    showSuccessToast('密码修改成功')
    passwordForm.value = {
      oldPassword: '',
      newPassword: '',
      confirmPassword: ''
    }
  } catch (error: any) {
    closeToast()
    showToast(error.message || '密码修改失败')
  }
}

// 发送邮箱验证码
const sendEmailVerificationCode = async (): Promise<void> => {
  if (!emailForm.value.newEmail) {
    showToast('请先输入新邮箱')
    return
  }

  // 验证邮箱格式
  const emailPattern = /^[\w-]+(\.[\w-]+)*@[\w-]+(\.[\w-]+)+$/
  if (!emailPattern.test(emailForm.value.newEmail)) {
    showToast('邮箱格式不正确')
    return
  }

  showLoadingToast({ message: '发送中...', forbidClick: true })
  try {
    await authAPI.sendEmailCode(emailForm.value.newEmail)
    closeToast()
    showSuccessToast('验证码已发送，请查收邮件')

    // 开始倒计时（60秒）
    emailCountdown.value = 60
    const timer = setInterval(() => {
      emailCountdown.value--
      if (emailCountdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (error: any) {
    closeToast()
    showToast(error.message || '发送失败')
  }
}

// 修改邮箱
const handleChangeEmail = async (): Promise<void> => {
  const { password, newEmail, verificationCode } = emailForm.value

  if (!password || !newEmail || !verificationCode) {
    showToast('请填写完整信息')
    return
  }

  // 验证邮箱格式
  const emailPattern = /^[\w-]+(\.[\w-]+)*@[\w-]+(\.[\w-]+)+$/
  if (!emailPattern.test(newEmail)) {
    showToast('邮箱格式不正确')
    return
  }

  try {
    showLoadingToast({ message: '修改中...', forbidClick: true })
    const updatedUser = await authAPI.changeEmail(password, newEmail, verificationCode)

    // 更新本地存储
    userStore.email = newEmail
    localStorage.setItem('email', newEmail)

    closeToast()
    showSuccessToast('邮箱修改成功')

    // 重置表单
    emailForm.value = {
      password: '',
      newEmail: '',
      verificationCode: ''
    }
    showEmailDialog.value = false
  } catch (error: any) {
    closeToast()
    showToast(error.message || '修改失败')
  }
}

// 点击头像
const handleAvatarClick = (): void => {
  avatarInput.value?.click()
}

// 头像文件变化
const handleAvatarChange = async (event: Event): Promise<void> => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  // 检查文件大小 (最大5MB)
  if (file.size > 5 * 1024 * 1024) {
    showToast('图片大小不能超过5MB')
    return
  }

  try {
    showLoadingToast({ message: '上传中...', forbidClick: true })
    const avatarPath = await authAPI.uploadAvatar(file)

    // 更新本地存储
    userStore.avatar = avatarPath
    localStorage.setItem('avatar', avatarPath)

    closeToast()
    showSuccessToast('头像上传成功')
  } catch (error: any) {
    closeToast()
    showToast(error.message || '头像上传失败')
  }

  // 清空input
  target.value = ''
}

// 刷新数据
const refreshData = async (): Promise<void> => {
  showLoadingToast({ message: '刷新中...', forbidClick: true })
  await loadStats()
  closeToast()
  showSuccessToast('刷新成功')
}

// 关于我们
const showAbout = (): void => {
  showConfirmDialog({
    title: '关于我们',
    message: '审批管理系统 v1.0.0\n\n一个简洁高效的双向审批管理平台\n\n支持申请提交、审批流转、对象关系管理等功能',
    showCancelButton: false,
    confirmButtonText: '我知道了'
  })
}

// 加载统计数据
const loadStats = async (): Promise<void> => {
  try {
    const data = await adminAPI.getUserStats()
    stats.value = data
  } catch (error) {
    console.error('获取统计数据失败', error)
  }
}

// 加载审批记录
const loadApprovalHistory = async (): Promise<void> => {
  approvalHistoryLoading.value = true
  try {
    const response = await applicationAPI.getMyApprovals({ pageNum: 1, pageSize: 50 })
    approvalHistory.value = response.records || []
  } catch (error) {
    console.error('获取审批记录失败', error)
    showToast('获取审批记录失败')
  } finally {
    approvalHistoryLoading.value = false
  }
}

// 打开审批记录弹窗
const openApprovalHistory = async (): Promise<void> => {
  showApprovalHistory.value = true
  await loadApprovalHistory()
}

// 获取状态类型
const getStatusType = (status: number): string => {
  const types: Record<number, string> = {
    1: 'warning',
    2: 'success',
    3: 'danger',
    4: 'default',
    5: 'default'
  }
  return types[status] || 'default'
}

// 获取状态文字
const getStatusText = (status: number): string => {
  const texts: Record<number, string> = {
    1: '待审批',
    2: '已通过',
    3: '已驳回',
    4: '草稿',
    5: '已取消'
  }
  return texts[status] || '未知'
}

// 格式化日期
const formatDate = (date: string): string => {
  if (!date) return ''
  const d = new Date(date)
  return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 跳转申请详情
const goToApplicationDetail = (id: number): void => {
  showApprovalHistory.value = false
  router.push(`/mobile/application-detail/${id}`)
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding-bottom: 20px;
}

.profile-header {
  position: relative;
  padding: 40px 20px 30px;
  color: white;
}

.header-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 200px;
  background: linear-gradient(135deg, rgba(255,255,255,0.1) 0%, rgba(255,255,255,0.05) 100%);
  border-radius: 0 0 30px 30px;
}

.header-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.avatar-section {
  position: relative;
  margin-bottom: 16px;
  cursor: pointer;
}

.user-avatar {
  border: 4px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
}

.avatar-edit-icon {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 28px;
  height: 28px;
  background: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.avatar-edit-icon :deep(.van-icon) {
  color: #667eea;
  font-size: 14px;
}

.avatar-fallback {
  width: 80px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-size: 32px;
  font-weight: bold;
  border-radius: 50%;
}

.role-badge {
  position: absolute;
  bottom: -5px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 10px;
  padding: 2px 8px;
}

.user-name {
  font-size: 22px;
  font-weight: 600;
  margin-bottom: 4px;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.welcome-text {
  font-size: 14px;
  opacity: 0.9;
}

.info-section {
  padding: 0 12px;
}

:deep(.van-cell-group__title) {
  color: #666;
  font-size: 13px;
  padding-left: 20px;
}

:deep(.van-cell-group--inset) {
  margin: 12px 0;
  border-radius: 12px;
  overflow: hidden;
}

:deep(.van-cell) {
  padding: 14px 16px;
}

:deep(.van-cell__left-icon) {
  color: #667eea;
  font-size: 18px;
  margin-right: 10px;
}

.stats-card {
  display: flex;
  align-items: center;
  justify-content: space-around;
  background: white;
  margin: 12px 0;
  padding: 20px 16px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.stat-item {
  flex: 1;
  text-align: center;
  cursor: pointer;
}

.stat-item:active {
  opacity: 0.7;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #667eea;
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.stat-divider {
  width: 1px;
  height: 40px;
  background: #eee;
}

.menu-section {
  margin-top: 12px;
}

.settings-section {
  margin-top: 12px;
}

.logout-section {
  padding: 20px 16px 12px;
}

:deep(.van-button--danger) {
  background: linear-gradient(135deg, #ff6b6b 0%, #ee5a5a 100%);
  border: none;
}

.version-info {
  text-align: center;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
  padding: 8px 0;
}

.edit-form {
  padding: 20px 16px;
}

.password-form {
  padding: 20px 16px;
}

.password-form :deep(.van-field) {
  margin-bottom: 12px;
}

/* 审批记录弹窗样式 */
.approval-history-popup {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #eee;
}

.popup-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.popup-close {
  font-size: 20px;
  color: #999;
  cursor: pointer;
}

.popup-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

.approval-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.approval-item {
  background: white;
  border-radius: 10px;
  padding: 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: transform 0.2s;
}

.approval-item:active {
  transform: scale(0.98);
}

.approval-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.approval-title {
  font-size: 15px;
  font-weight: 500;
  color: #333;
  flex: 1;
  margin-right: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.approval-desc {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.approval-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #999;
}

.approval-applicant,
.approval-time {
  display: flex;
  align-items: center;
  gap: 4px;
}

.approval-applicant :deep(.van-icon),
.approval-time :deep(.van-icon) {
  font-size: 14px;
}
</style>
