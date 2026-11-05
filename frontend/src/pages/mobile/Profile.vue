<template>
  <div class="m-page profile-page">
    <header class="profile-head">
      <button type="button" class="avatar-btn" aria-label="更换头像" @click="avatarInput?.click()">
        <UserAvatar :src="userStore.avatar" :name="userStore.displayName" :size="64" />
        <span class="avatar-badge"><van-icon name="photograph" /></span>
      </button>
      <div class="profile-name">
        <h1>{{ userStore.displayName }}</h1>
        <p class="m-meta">
          <span>@{{ userStore.username }}</span>
          <span v-if="userStore.isAdmin">管理员</span>
        </p>
      </div>
    </header>

    <section class="m-card stats">
      <router-link to="/mobile/approvals" class="stat">
        <span class="stat-value tabular">{{ stats.pendingForMe }}</span>
        <span class="stat-label">待我审批</span>
      </router-link>
      <router-link to="/mobile/applications" class="stat">
        <span class="stat-value tabular">{{ stats.myPending }}</span>
        <span class="stat-label">我在等对方</span>
      </router-link>
      <router-link to="/mobile/relations" class="stat">
        <span class="stat-value tabular">{{ stats.myRelations }}</span>
        <span class="stat-label">我的对象</span>
      </router-link>
    </section>

    <section class="m-section">
      <van-cell-group class="m-card" :border="false">
        <van-cell title="姓名" :value="userStore.realName || '未设置'" is-link @click="openFieldEditor('realName')" />
        <van-cell title="手机号" :value="maskPhone(userStore.phone)" is-link @click="openFieldEditor('phone')" />
        <van-cell title="邮箱" :value="userStore.email || '未设置'" is-link @click="openEmailEditor" />
      </van-cell-group>
    </section>

    <section class="m-section">
      <van-cell-group class="m-card" :border="false">
        <van-cell title="我的审批记录" icon="passed" is-link @click="openApprovalHistory" />
        <van-cell title="我的对象" icon="friends-o" is-link to="/mobile/relations" />
        <van-cell title="申请模板" icon="orders-o" is-link to="/mobile/templates" />
        <van-cell title="纪念日" icon="calendar-o" is-link to="/mobile/events" />
        <van-cell v-if="userStore.isAdmin" title="管理后台" icon="setting-o" is-link to="/admin/dashboard" />
      </van-cell-group>
    </section>

    <section class="m-section">
      <van-cell-group class="m-card" :border="false">
        <van-cell title="修改密码" icon="lock" is-link @click="openPasswordEditor" />
        <van-cell title="关于" icon="info-o" is-link @click="showAbout" />
      </van-cell-group>
    </section>

    <van-button class="logout" block plain type="danger" @click="handleLogout">退出登录</van-button>

    <!-- 修改姓名 / 手机号：校验或接口失败时弹窗不关闭 -->
    <van-dialog
      v-model:show="fieldEditor.show"
      :title="fieldEditor.field === 'phone' ? '修改手机号' : '修改姓名'"
      show-cancel-button
      :before-close="beforeFieldEditorClose"
    >
      <van-field
        v-model.trim="fieldEditor.value"
        class="dialog-field"
        :type="fieldEditor.field === 'phone' ? 'tel' : 'text'"
        :maxlength="fieldEditor.field === 'phone' ? 11 : 50"
        :placeholder="fieldEditor.field === 'phone' ? '11 位手机号' : '对方看到的名字'"
      />
    </van-dialog>

    <van-dialog v-model:show="passwordEditor.show" title="修改密码" show-cancel-button :before-close="beforePasswordClose">
      <van-field v-model="passwordEditor.oldPassword" type="password" label="原密码" autocomplete="current-password" />
      <van-field v-model="passwordEditor.newPassword" type="password" label="新密码" placeholder="至少 6 位" autocomplete="new-password" />
      <van-field v-model="passwordEditor.confirmPassword" type="password" label="确认密码" autocomplete="new-password" />
    </van-dialog>

    <van-dialog v-model:show="emailEditor.show" title="修改邮箱" show-cancel-button :before-close="beforeEmailClose">
      <van-field v-model="emailEditor.password" type="password" label="当前密码" autocomplete="current-password" />
      <van-field v-model.trim="emailEditor.newEmail" type="email" label="新邮箱" maxlength="100" />
      <van-field v-model.trim="emailEditor.code" label="验证码" maxlength="8">
        <template #button>
          <van-button
            size="small"
            plain
            type="primary"
            :loading="sendingCode"
            :disabled="emailCountdown.running.value"
            @click="sendEmailCode"
          >
            {{ emailCountdown.running.value ? `${emailCountdown.remaining.value} 秒` : '发送验证码' }}
          </van-button>
        </template>
      </van-field>
    </van-dialog>

    <van-popup v-model:show="showApprovalHistory" position="bottom" round closeable :style="{ height: '80%' }">
      <div class="history">
        <div class="m-sheet-title">我的审批记录</div>
        <div class="history-list">
          <van-loading v-if="approvalHistoryLoading" class="history-state" size="24px">加载中...</van-loading>
          <van-empty v-else-if="approvalHistory.length === 0" description="还没有审批过申请" />
          <template v-else>
            <ApplicationCard
              v-for="app in approvalHistory"
              :key="app.id"
              class="m-card history-card"
              :application="app"
              perspective="approver"
              @open="goToApplicationDetail"
            />
          </template>
        </div>
      </div>
    </van-popup>

    <input ref="avatarInput" type="file" accept="image/*" hidden @change="handleAvatarChange" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { closeToast, showConfirmDialog, showDialog, showFailToast, showLoadingToast, showSuccessToast } from 'vant'
import { useUserStore } from '@/store/modules/user'
import { adminAPI, applicationAPI, authAPI, type Application, type UserStats } from '@/services/api'
import { endSession } from '@/services/session'
import { useCountdown } from '@/composables/useCountdown'
import UserAvatar from '@/components/UserAvatar.vue'
import ApplicationCard from '@/components/application/ApplicationCard.vue'

const PHONE_PATTERN = /^1[3-9]\d{9}$/
const EMAIL_PATTERN = /^[\w.+-]+@[\w-]+(\.[\w-]+)+$/

const router = useRouter()
const userStore = useUserStore()
const avatarInput = ref<HTMLInputElement | null>(null)

const stats = ref<UserStats>({
  myPending: 0,
  myApproved: 0,
  myRejected: 0,
  pendingForMe: 0,
  myRelations: 0,
})

const fieldEditor = reactive({ show: false, field: 'realName' as 'realName' | 'phone', value: '' })
const passwordEditor = reactive({ show: false, oldPassword: '', newPassword: '', confirmPassword: '' })
const emailEditor = reactive({ show: false, password: '', newEmail: '', code: '' })
const emailCountdown = useCountdown(60)
const sendingCode = ref(false)

const showApprovalHistory = ref(false)
const approvalHistory = ref<Application[]>([])
const approvalHistoryLoading = ref(false)

// 换了新邮箱，之前发的验证码作废
watch(() => emailEditor.newEmail, () => {
  emailCountdown.reset()
  emailEditor.code = ''
})

function maskPhone(phone: string): string {
  if (!phone) return '未设置'
  return phone.length === 11 ? phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2') : phone
}

function openFieldEditor(field: 'realName' | 'phone'): void {
  fieldEditor.field = field
  fieldEditor.value = userStore[field] || ''
  fieldEditor.show = true
}

async function beforeFieldEditorClose(action: string): Promise<boolean> {
  if (action !== 'confirm') return true

  const { field, value } = fieldEditor
  if (!value) {
    showFailToast(field === 'phone' ? '请输入手机号' : '请输入姓名')
    return false
  }
  if (field === 'phone' && !PHONE_PATTERN.test(value)) {
    showFailToast('请输入正确的手机号')
    return false
  }

  try {
    await authAPI.updateProfile({ [field]: value })
    userStore.updateProfile({ [field]: value })
    showSuccessToast('已保存')
    return true
  } catch (error: any) {
    showFailToast(error.message || '保存失败')
    return false
  }
}

function openPasswordEditor(): void {
  Object.assign(passwordEditor, { show: true, oldPassword: '', newPassword: '', confirmPassword: '' })
}

async function beforePasswordClose(action: string): Promise<boolean> {
  if (action !== 'confirm') return true

  const { oldPassword, newPassword, confirmPassword } = passwordEditor
  if (!oldPassword || !newPassword || !confirmPassword) {
    showFailToast('请填写完整')
    return false
  }
  if (newPassword.length < 6) {
    showFailToast('新密码至少 6 位')
    return false
  }
  if (newPassword !== confirmPassword) {
    showFailToast('两次输入的新密码不一致')
    return false
  }

  try {
    await authAPI.changePassword(oldPassword, newPassword)
    showSuccessToast('密码已修改')
    return true
  } catch (error: any) {
    showFailToast(error.message || '修改失败')
    return false
  }
}

function openEmailEditor(): void {
  Object.assign(emailEditor, { show: true, password: '', newEmail: '', code: '' })
}

async function sendEmailCode(): Promise<void> {
  if (!EMAIL_PATTERN.test(emailEditor.newEmail)) {
    showFailToast(emailEditor.newEmail ? '邮箱格式不正确' : '请先输入新邮箱')
    return
  }
  sendingCode.value = true
  try {
    await authAPI.sendEmailCode(emailEditor.newEmail)
    showSuccessToast('验证码已发送')
    emailCountdown.start()
  } catch (error: any) {
    showFailToast(error.message || '发送失败')
  } finally {
    sendingCode.value = false
  }
}

async function beforeEmailClose(action: string): Promise<boolean> {
  if (action !== 'confirm') return true

  const { password, newEmail, code } = emailEditor
  if (!password || !newEmail || !code) {
    showFailToast('请填写完整')
    return false
  }
  if (!EMAIL_PATTERN.test(newEmail)) {
    showFailToast('邮箱格式不正确')
    return false
  }

  try {
    await authAPI.changeEmail(password, newEmail, code)
    userStore.updateProfile({ email: newEmail })
    showSuccessToast('邮箱已修改')
    return true
  } catch (error: any) {
    showFailToast(error.message || '修改失败')
    return false
  }
}

async function handleAvatarChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  if (file.size > 5 * 1024 * 1024) {
    showFailToast('图片不能超过 5MB')
    return
  }

  showLoadingToast({ message: '上传中...', forbidClick: true })
  try {
    const avatar = await authAPI.uploadAvatar(file)
    userStore.updateProfile({ avatar })
    closeToast()
    showSuccessToast('头像已更新')
  } catch (error: any) {
    closeToast()
    showFailToast(error.message || '头像上传失败')
  }
}

async function handleLogout(): Promise<void> {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确定要退出当前账号吗？' })
  } catch {
    return
  }
  endSession()
  router.replace('/login')
}

function showAbout(): void {
  showDialog({
    title: '关于',
    message: '审批管理系统 v1.0.0\n和对象互相发起、审批申请，记录日常和纪念日。',
    confirmButtonText: '知道了',
  })
}

async function loadStats(): Promise<void> {
  try {
    stats.value = await adminAPI.getUserStats()
  } catch {
    // 统计只是概览，失败时保留 0
  }
}

async function openApprovalHistory(): Promise<void> {
  showApprovalHistory.value = true
  approvalHistoryLoading.value = true
  try {
    const page = await applicationAPI.getMyApprovals({ pageNum: 1, pageSize: 50 })
    approvalHistory.value = page.records || []
  } catch (error: any) {
    showFailToast(error.message || '审批记录加载失败')
  } finally {
    approvalHistoryLoading.value = false
  }
}

function goToApplicationDetail(id: number): void {
  showApprovalHistory.value = false
  router.push(`/mobile/application-detail/${id}`)
}

onMounted(loadStats)
</script>

<style scoped>
.profile-page {
  padding-top: max(20px, env(safe-area-inset-top));
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 4px 4px 20px;
}

.avatar-btn {
  position: relative;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: none;
  cursor: pointer;
}

.avatar-badge {
  position: absolute;
  right: -2px;
  bottom: -2px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: 2px solid var(--bg);
  border-radius: 50%;
  background: var(--ink);
  color: #fff;
  font-size: 12px;
}

.profile-name h1 {
  color: var(--ink);
  font-size: 22px;
  line-height: 1.3;
}

.profile-name .m-meta {
  margin-top: 2px;
  font-size: 13px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  padding: 16px 0;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  text-decoration: none;
}

.stat + .stat {
  border-left: 1px solid var(--rule);
}

.stat-value {
  color: var(--ink);
  font-size: 22px;
  font-weight: 600;
  line-height: 1.1;
}

.stat-label {
  color: var(--ink-3);
  font-size: 12px;
}

.m-section .m-card {
  overflow: hidden;
}

.m-section :deep(.van-cell__left-icon) {
  color: var(--ink-2);
}

.logout {
  margin-top: 24px;
  border-color: var(--rule);
  background: var(--surface);
}

.dialog-field {
  margin: 12px 16px 4px;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  width: auto;
}

.history {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.history-list {
  flex: 1;
  padding: 4px 12px calc(16px + env(safe-area-inset-bottom));
  overflow-y: auto;
  background: var(--bg);
}

.history-card {
  margin-top: 8px;
}

.history-state {
  display: flex;
  justify-content: center;
  padding: 32px 0;
}
</style>
