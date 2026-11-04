<template>
  <div class="admin-page">
    <div class="admin-page-header">
      <div class="admin-page-heading">
        <h1 class="admin-page-title">用户管理</h1>
        <p class="admin-page-subtitle">维护账号资料、状态、角色和语音通知权限</p>
      </div>
      <div class="admin-page-actions">
        <a-button :loading="loading" @click="fetchUsers">
          <template #icon><icon-refresh /></template>
          刷新
        </a-button>
      </div>
    </div>

    <a-table
      class="admin-table desktop-only"
      row-key="id"
      :columns="columns"
      :data="users"
      :loading="loading"
      :pagination="pagination"
      :scroll="{ x: 1180 }"
      @page-change="handlePageChange"
    >
      <template #user="{ record }">
        <div class="user-cell">
          <UserAvatar :src="record.avatar" :name="record.realName || record.username" :size="32" />
          <div class="user-cell-text">
            <div class="user-cell-name">
              {{ record.realName || record.username }}
              <span v-if="record.id === userStore.userId" class="self-mark">我</span>
            </div>
            <div class="user-cell-sub">@{{ record.username }} · #{{ record.id }}</div>
          </div>
        </div>
      </template>
      <template #contact="{ record }">
        <div class="tabular">{{ record.phone || '-' }}</div>
        <div class="user-cell-sub">{{ record.email || '未填写邮箱' }}</div>
      </template>
      <template #role="{ record }">
        <a-tag v-if="record.role === 1" color="arcoblue">管理员</a-tag>
        <span v-else class="muted">普通用户</span>
      </template>
      <template #status="{ record }">
        <a-tag v-if="record.status !== 1" color="red">已禁用</a-tag>
        <span v-else class="muted">正常</span>
      </template>
      <template #voiceNotification="{ record }">
        <a-switch
          :model-value="!!record.voiceNotificationEnabled"
          :before-change="(next) => changeVoiceNotification(record, Boolean(next))"
        />
      </template>
      <template #createdAt="{ record }">
        <span class="tabular">{{ formatDate(record.createdAt) }}</span>
      </template>
      <template #actions="{ record }">
        <a-space :size="4">
          <a-button type="text" size="small" @click="showEditModal(record)">编辑</a-button>
          <a-tooltip :content="record.id === userStore.userId ? '不能修改自己的角色' : ''" :disabled="record.id !== userStore.userId">
            <a-button type="text" size="small" :disabled="record.id === userStore.userId" @click="showRoleModal(record)">
              角色
            </a-button>
          </a-tooltip>
          <a-tooltip :content="record.id === userStore.userId ? '不能禁用自己' : ''" :disabled="record.id !== userStore.userId">
            <a-button
              type="text"
              size="small"
              :status="record.status === 1 ? 'danger' : 'normal'"
              :disabled="record.id === userStore.userId"
              @click="toggleUserStatus(record)"
            >
              {{ record.status === 1 ? '禁用' : '启用' }}
            </a-button>
          </a-tooltip>
        </a-space>
      </template>
    </a-table>

    <a-spin class="mobile-only" :loading="loading">
      <div class="admin-mobile-list">
        <article v-for="record in users" :key="record.id" class="admin-mobile-card">
          <div class="admin-mobile-card-header">
            <div class="user-cell">
              <UserAvatar :src="record.avatar" :name="record.realName || record.username" :size="36" />
              <div class="user-cell-text">
                <div class="admin-mobile-title">{{ record.realName || record.username }}</div>
                <div class="admin-mobile-meta">@{{ record.username }} · #{{ record.id }}</div>
              </div>
            </div>
            <a-tag v-if="record.status !== 1" color="red">已禁用</a-tag>
            <a-tag v-else-if="record.role === 1" color="arcoblue">管理员</a-tag>
          </div>

          <div class="admin-mobile-field-grid">
            <div class="admin-mobile-field">
              <div class="admin-mobile-field-label">手机号</div>
              <div class="admin-mobile-field-value">{{ record.phone || '-' }}</div>
            </div>
            <div class="admin-mobile-field">
              <div class="admin-mobile-field-label">邮箱</div>
              <div class="admin-mobile-field-value">{{ record.email || '-' }}</div>
            </div>
            <div class="admin-mobile-field">
              <div class="admin-mobile-field-label">语音通知</div>
              <div class="admin-mobile-field-value">
                <a-switch
                  size="small"
                  :model-value="!!record.voiceNotificationEnabled"
                  :before-change="(next) => changeVoiceNotification(record, Boolean(next))"
                />
              </div>
            </div>
            <div class="admin-mobile-field">
              <div class="admin-mobile-field-label">注册时间</div>
              <div class="admin-mobile-field-value">{{ formatDate(record.createdAt) }}</div>
            </div>
          </div>

          <div class="admin-mobile-actions">
            <a-button size="small" @click="showEditModal(record)">编辑</a-button>
            <a-button size="small" :disabled="record.id === userStore.userId" @click="showRoleModal(record)">角色</a-button>
            <a-button
              size="small"
              :status="record.status === 1 ? 'danger' : 'normal'"
              :disabled="record.id === userStore.userId"
              @click="toggleUserStatus(record)"
            >
              {{ record.status === 1 ? '禁用' : '启用' }}
            </a-button>
          </div>
        </article>

        <div v-if="!users.length && !loading" class="admin-empty-state">还没有用户</div>

        <div v-if="pagination.total > pagination.pageSize" class="admin-mobile-pagination">
          <a-pagination
            simple
            :current="pagination.current"
            :page-size="pagination.pageSize"
            :total="pagination.total"
            @change="handlePageChange"
          />
        </div>
      </div>
    </a-spin>

    <!-- on-before-ok：校验或接口失败时返回 false，弹窗保持打开 -->
    <a-modal
      v-model:visible="editModalVisible"
      title="编辑用户资料"
      :width="modalWidth"
      :on-before-ok="handleUpdateUserInfo"
    >
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="用户名">
          <a-input :model-value="currentUser?.username" disabled />
        </a-form-item>
        <a-form-item label="姓名">
          <a-input v-model="editForm.realName" placeholder="选填" :max-length="50" />
        </a-form-item>
        <a-form-item label="手机号" required>
          <a-input v-model="editForm.phone" placeholder="11 位手机号" :max-length="11" />
        </a-form-item>
        <a-form-item label="邮箱">
          <a-input v-model="editForm.email" placeholder="选填" :max-length="100" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:visible="roleModalVisible"
      title="修改角色"
      :width="modalWidth"
      :on-before-ok="handleUpdateRole"
    >
      <p class="role-subject">{{ currentUser?.realName || currentUser?.username }}</p>
      <a-radio-group v-model="roleForm.role" direction="vertical">
        <a-radio :value="0">普通用户</a-radio>
        <a-radio :value="1">管理员（可以进入后台、处理所有申请）</a-radio>
      </a-radio-group>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { IconRefresh } from '@arco-design/web-vue/es/icon'
import { adminAPI, type User } from '@/services/api'
import { useUserStore } from '@/store/modules/user'
import { formatDate } from '@/utils/format'
import UserAvatar from '@/components/UserAvatar.vue'

const PHONE_PATTERN = /^1[3-9]\d{9}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const userStore = useUserStore()
const modalWidth = 'min(480px, calc(100vw - 32px))'

const users = ref<User[]>([])
const loading = ref(false)
const roleModalVisible = ref(false)
const editModalVisible = ref(false)
const currentUser = ref<User | null>(null)
const roleForm = ref({ role: 0 })
const editForm = ref({ realName: '', phone: '', email: '' })
const pagination = ref({ current: 1, pageSize: 10, total: 0 })

const columns = [
  { title: '用户', slotName: 'user', width: 240 },
  { title: '联系方式', slotName: 'contact', width: 220 },
  { title: '角色', slotName: 'role', width: 100 },
  { title: '状态', slotName: 'status', width: 90 },
  { title: '语音通知', slotName: 'voiceNotification', width: 112 },
  { title: '注册时间', slotName: 'createdAt', width: 120 },
  { title: '操作', slotName: 'actions', width: 180, fixed: 'right' as const },
]

const displayName = (user: User): string => user.realName || user.username

const fetchUsers = async (): Promise<void> => {
  loading.value = true
  try {
    const page = await adminAPI.getAllUsers({
      pageNum: pagination.value.current,
      pageSize: pagination.value.pageSize,
    })
    users.value = page.records || []
    pagination.value.total = page.total || 0
  } catch (error: any) {
    Message.error(error.message || '获取用户列表失败')
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number): void => {
  pagination.value.current = page
  fetchUsers()
}

const toggleUserStatus = (user: User): void => {
  const disable = user.status === 1
  Modal.confirm({
    title: disable ? '禁用账号' : '启用账号',
    content: disable
      ? `禁用后"${displayName(user)}"将无法登录。`
      : `启用后"${displayName(user)}"可以重新登录。`,
    okText: disable ? '禁用' : '启用',
    okButtonProps: disable ? { status: 'danger' } : {},
    onBeforeOk: async () => {
      try {
        await adminAPI.updateUserStatus(user.id, disable ? 0 : 1)
        Message.success(disable ? '已禁用' : '已启用')
        fetchUsers()
        return true
      } catch (error: any) {
        Message.error(error.message || '操作失败')
        return false
      }
    },
  })
}

const showEditModal = (user: User): void => {
  currentUser.value = user
  editForm.value = {
    realName: user.realName || '',
    phone: user.phone || '',
    email: user.email || '',
  }
  editModalVisible.value = true
}

const handleUpdateUserInfo = async (): Promise<boolean> => {
  if (!currentUser.value) return true

  const form = {
    realName: editForm.value.realName.trim(),
    phone: editForm.value.phone.trim(),
    email: editForm.value.email.trim(),
  }
  // 手机号在数据库里是必填且唯一的，不允许清空
  if (!PHONE_PATTERN.test(form.phone)) {
    Message.error(form.phone ? '手机号格式不正确' : '手机号不能为空')
    return false
  }
  if (form.email && !EMAIL_PATTERN.test(form.email)) {
    Message.error('邮箱格式不正确')
    return false
  }

  try {
    await adminAPI.updateUserInfo(currentUser.value.id, form)
    Message.success('已保存')
    fetchUsers()
    return true
  } catch (error: any) {
    Message.error(error.message || '保存失败')
    return false
  }
}

const showRoleModal = (user: User): void => {
  currentUser.value = user
  roleForm.value.role = user.role || 0
  roleModalVisible.value = true
}

const handleUpdateRole = async (): Promise<boolean> => {
  if (!currentUser.value || roleForm.value.role === currentUser.value.role) return true

  try {
    await adminAPI.updateUserRole(currentUser.value.id, roleForm.value.role)
    Message.success('角色已修改')
    fetchUsers()
    return true
  } catch (error: any) {
    Message.error(error.message || '角色修改失败')
    return false
  }
}

/** 开关在确认并且接口成功之后才翻转，失败时保持原状 */
const changeVoiceNotification = (user: User, enabled: boolean): Promise<boolean> => {
  return new Promise((resolve) => {
    Modal.confirm({
      title: enabled ? '开启语音通知' : '关闭语音通知',
      content: enabled
        ? `开启后"${displayName(user)}"可以用电话提醒对方审批，每次通知都会产生短信语音费用。`
        : `关闭后"${displayName(user)}"不能再发送语音通知。`,
      okText: enabled ? '开启' : '关闭',
      onBeforeOk: async () => {
        try {
          await adminAPI.updateVoiceNotificationPermission(user.id, enabled)
          user.voiceNotificationEnabled = enabled
          Message.success(enabled ? '已开启' : '已关闭')
          resolve(true)
          return true
        } catch (error: any) {
          Message.error(error.message || '操作失败')
          return false
        }
      },
      onCancel: () => resolve(false),
    })
  })
}

onMounted(fetchUsers)
</script>

<style scoped>
.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.user-cell-text {
  min-width: 0;
}

.user-cell-name {
  color: var(--admin-text);
  font-weight: 600;
}

.user-cell-sub {
  overflow: hidden;
  color: var(--admin-text-soft);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.self-mark {
  margin-left: 4px;
  padding: 0 4px;
  border: 1px solid var(--admin-border-strong);
  border-radius: var(--radius-sm);
  color: var(--admin-text-muted);
  font-size: 11px;
  font-weight: 400;
}

.muted {
  color: var(--admin-text-muted);
}

.role-subject {
  margin-bottom: 12px;
  color: var(--admin-text);
  font-weight: 600;
}
</style>
