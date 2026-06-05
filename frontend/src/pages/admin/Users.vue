<template>
  <div class="admin-page users-page">
    <div class="admin-page-header">
      <div class="admin-page-heading">
        <div class="admin-page-kicker">USERS</div>
        <h1 class="admin-page-title">用户管理</h1>
        <p class="admin-page-subtitle">维护账号状态、角色与语音通知权限。</p>
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
      :columns="columns"
      :data="users"
      :loading="loading"
      :pagination="pagination"
      @page-change="handlePageChange"
    >
      <template #avatar="{ record }">
        <a-avatar :size="32">
          <img v-if="record.avatar" :src="getAvatarUrl(record.avatar)" alt="avatar" />
          <template v-else>{{ getAvatarText(record) }}</template>
        </a-avatar>
      </template>
      <template #role="{ record }">
        <a-tag :color="record.role === 1 ? 'orange' : 'blue'">
          {{ record.role === 1 ? '管理员' : '普通用户' }}
        </a-tag>
      </template>
      <template #status="{ record }">
        <a-tag :color="record.status === 1 ? 'green' : 'red'">
          {{ record.status === 1 ? '正常' : '禁用' }}
        </a-tag>
      </template>
      <template #voiceNotification="{ record }">
        <a-switch
          :model-value="record.voiceNotificationEnabled"
          @change="(value) => toggleVoiceNotification(record, Boolean(value))"
          :before-change="() => confirmVoiceNotificationChange(record)"
        />
      </template>
      <template #createdAt="{ record }">
        {{ formatDate(record.createdAt) }}
      </template>
      <template #actions="{ record }">
        <a-space>
          <a-button
            type="text"
            size="small"
            @click="showEditModal(record)"
          >
            编辑
          </a-button>
          <a-button
            type="text"
            size="small"
            :status="record.status === 1 ? 'warning' : 'success'"
            @click="toggleUserStatus(record)"
          >
            {{ record.status === 1 ? '禁用' : '启用' }}
          </a-button>
          <a-button
            type="text"
            size="small"
            @click="showRoleModal(record)"
          >
            修改角色
          </a-button>
        </a-space>
      </template>
    </a-table>

    <a-spin class="admin-mobile-spin mobile-only" :loading="loading">
      <div class="admin-mobile-list">
        <article
          v-for="record in users"
          :key="record.id"
          class="admin-mobile-card"
        >
          <div class="admin-mobile-card-header user-card-header">
            <div class="user-card-identity">
              <a-avatar :size="38">
                <img v-if="record.avatar" :src="getAvatarUrl(record.avatar)" alt="avatar" />
                <template v-else>{{ getAvatarText(record) }}</template>
              </a-avatar>
              <div>
                <div class="admin-mobile-title">{{ record.realName || record.username }}</div>
                <div class="admin-mobile-meta">#{{ record.id }} · {{ record.username }}</div>
              </div>
            </div>
            <a-tag :color="record.status === 1 ? 'green' : 'red'">
              {{ record.status === 1 ? '正常' : '禁用' }}
            </a-tag>
          </div>

          <div class="admin-mobile-field-grid">
            <div class="admin-mobile-field">
              <div class="admin-mobile-field-label">角色</div>
              <div class="admin-mobile-field-value">
                <a-tag :color="record.role === 1 ? 'orange' : 'blue'">
                  {{ record.role === 1 ? '管理员' : '普通用户' }}
                </a-tag>
              </div>
            </div>
            <div class="admin-mobile-field">
              <div class="admin-mobile-field-label">语音通知</div>
              <div class="admin-mobile-field-value">
                <a-switch
                  :model-value="record.voiceNotificationEnabled"
                  @change="(value) => toggleVoiceNotification(record, Boolean(value))"
                  :before-change="() => confirmVoiceNotificationChange(record)"
                />
              </div>
            </div>
            <div class="admin-mobile-field">
              <div class="admin-mobile-field-label">手机号</div>
              <div class="admin-mobile-field-value">{{ record.phone || '-' }}</div>
            </div>
            <div class="admin-mobile-field">
              <div class="admin-mobile-field-label">邮箱</div>
              <div class="admin-mobile-field-value">{{ record.email || '-' }}</div>
            </div>
            <div class="admin-mobile-field">
              <div class="admin-mobile-field-label">创建时间</div>
              <div class="admin-mobile-field-value">{{ formatDate(record.createdAt) }}</div>
            </div>
          </div>

          <div class="admin-mobile-actions">
            <a-button size="small" @click="showEditModal(record)">编辑</a-button>
            <a-button
              size="small"
              :status="record.status === 1 ? 'warning' : 'success'"
              @click="toggleUserStatus(record)"
            >
              {{ record.status === 1 ? '禁用' : '启用' }}
            </a-button>
            <a-button size="small" @click="showRoleModal(record)">修改角色</a-button>
          </div>
        </article>

        <div v-if="!users.length && !loading" class="admin-empty-state">
          暂无用户数据
        </div>

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

    <!-- 编辑用户信息弹窗 -->
    <a-modal
      v-model:visible="editModalVisible"
      title="编辑用户信息"
      @ok="handleUpdateUserInfo"
      :ok-loading="updating"
      :width="editModalWidth"
    >
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="用户名" tooltip="用户名不可修改">
          <a-input :value="currentUser?.username" disabled />
        </a-form-item>
        <a-form-item label="真实姓名">
          <a-input v-model="editForm.realName" placeholder="请输入真实姓名" />
        </a-form-item>
        <a-form-item label="手机号">
          <a-input
            v-model="editForm.phone"
            placeholder="请输入手机号"
            max-length="11"
          />
        </a-form-item>
        <a-form-item label="邮箱">
          <a-input
            v-model="editForm.email"
            placeholder="请输入邮箱"
            type="email"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 修改角色弹窗 -->
    <a-modal
      v-model:visible="roleModalVisible"
      title="修改用户角色"
      @ok="handleUpdateRole"
      :ok-loading="updating"
      :width="roleModalWidth"
    >
      <a-form :model="roleForm">
        <a-form-item label="用户名">
          <a-input :value="currentUser?.username" disabled />
        </a-form-item>
        <a-form-item label="真实姓名">
          <a-input :value="currentUser?.realName || '-'" disabled />
        </a-form-item>
        <a-form-item label="当前角色">
          <a-tag :color="currentUser?.role === 1 ? 'orange' : 'blue'">
            {{ currentUser?.role === 1 ? '管理员' : '普通用户' }}
          </a-tag>
        </a-form-item>
        <a-form-item label="新角色">
          <a-radio-group v-model="roleForm.role">
            <a-radio :value="0">普通用户</a-radio>
            <a-radio :value="1">管理员</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { adminAPI, type User } from '@/services/api'
import { IconRefresh } from '@arco-design/web-vue/es/icon'

const users = ref<User[]>([])
const loading = ref(false)
const updating = ref(false)
const roleModalVisible = ref(false)
const editModalVisible = ref(false)
const currentUser = ref<User | null>(null)
const editModalWidth = 'min(500px, calc(100vw - 32px))'
const roleModalWidth = 'min(520px, calc(100vw - 32px))'
const roleForm = ref({
  role: 0
})
const editForm = ref({
  realName: '',
  phone: '',
  email: ''
})

const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0
})

const columns = [
  { title: '头像', slotName: 'avatar', width: 70 },
  { title: '用户ID', dataIndex: 'id', width: 80 },
  { title: '用户名', dataIndex: 'username', width: 120 },
  { title: '真实姓名', dataIndex: 'realName', width: 120 },
  { title: '手机号', dataIndex: 'phone', width: 130 },
  { title: '邮箱', dataIndex: 'email', ellipsis: true },
  { title: '角色', slotName: 'role', width: 100 },
  { title: '状态', slotName: 'status', width: 80 },
  { title: '语音通知', slotName: 'voiceNotification', width: 100 },
  { title: '创建时间', slotName: 'createdAt', width: 160 },
  { title: '操作', slotName: 'actions', width: 220, fixed: 'right' }
]

const fetchUsers = async (): Promise<void> => {
  loading.value = true
  try {
    const response = await adminAPI.getAllUsers({
      pageNum: pagination.value.current,
      pageSize: pagination.value.pageSize
    })
    users.value = response.records || []
    pagination.value.total = response.total || 0
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
  const newStatus = user.status === 1 ? 0 : 1
  const action = newStatus === 0 ? '禁用' : '启用'

  Modal.confirm({
    title: `确认${action}用户`,
    content: `是否确认${action}用户 "${user.realName || user.username}"？`,
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      try {
        await adminAPI.updateUserStatus(user.id, newStatus)
        Message.success(`${action}成功`)
        fetchUsers()
      } catch (error: any) {
        Message.error(error.message || `${action}失败`)
      }
    }
  })
}

const showEditModal = (user: User): void => {
  currentUser.value = user
  editForm.value = {
    realName: user.realName || '',
    phone: user.phone || '',
    email: user.email || ''
  }
  editModalVisible.value = true
}

const handleUpdateUserInfo = async (): Promise<void> => {
  if (!currentUser.value) return

  // 验证手机号格式
  if (editForm.value.phone && !/^1[3-9]\d{9}$/.test(editForm.value.phone)) {
    Message.error('请输入正确的手机号格式')
    return
  }

  // 验证邮箱格式
  if (editForm.value.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(editForm.value.email)) {
    Message.error('请输入正确的邮箱格式')
    return
  }

  updating.value = true
  try {
    await adminAPI.updateUserInfo(currentUser.value.id, editForm.value)
    Message.success('用户信息更新成功')
    editModalVisible.value = false
    fetchUsers()
  } catch (error: any) {
    Message.error(error.message || '用户信息更新失败')
  } finally {
    updating.value = false
  }
}

const showRoleModal = (user: User): void => {
  currentUser.value = user
  roleForm.value.role = user.role || 0
  roleModalVisible.value = true
}

const handleUpdateRole = async (): Promise<void> => {
  if (!currentUser.value) return

  if (roleForm.value.role === currentUser.value.role) {
    Message.warning('角色未改变')
    roleModalVisible.value = false
    return
  }

  updating.value = true
  try {
    await adminAPI.updateUserRole(currentUser.value.id, roleForm.value.role)
    Message.success('角色修改成功')
    roleModalVisible.value = false
    fetchUsers()
  } catch (error: any) {
    Message.error(error.message || '角色修改失败')
  } finally {
    updating.value = false
  }
}

const confirmVoiceNotificationChange = (user: User): Promise<boolean> => {
  return new Promise((resolve) => {
    const action = user.voiceNotificationEnabled ? '关闭' : '开启'
    Modal.confirm({
      title: `确认${action}语音通知权限`,
      content: `是否确认${action}用户 "${user.realName || user.username}" 的语音通知权限？${!user.voiceNotificationEnabled ? '\n\n注意：语音通知功能会产生费用。' : ''}`,
      okText: '确认',
      cancelText: '取消',
      onOk: () => resolve(true),
      onCancel: () => resolve(false)
    })
  })
}

const toggleVoiceNotification = async (user: User, enabled: boolean): Promise<void> => {
  try {
    await adminAPI.updateVoiceNotificationPermission(user.id, enabled)
    Message.success(`${enabled ? '开启' : '关闭'}语音通知权限成功`)
    fetchUsers()
  } catch (error: any) {
    Message.error(error.message || `${enabled ? '开启' : '关闭'}语音通知权限失败`)
  }
}

const getAvatarUrl = (avatar: string): string => {
  if (avatar.startsWith('/')) {
    return `http://localhost:8080${avatar}`
  }
  return avatar
}

const getAvatarText = (user: User): string => {
  const name = user.realName || user.username
  return name ? name.charAt(0).toUpperCase() : 'U'
}

const formatDate = (date?: string): string => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

onMounted(() => {
  fetchUsers()
})
</script>

<style scoped>
.users-page {
  max-width: 1600px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 600;
  color: #1d2129;
}

:deep(.arco-table) {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

:deep(.arco-table-th) {
  background: #f7f8fa;
  font-weight: 600;
}

:deep(.arco-avatar) {
  font-weight: 600;
  box-shadow: 0 2px 4px rgba(102, 126, 234, 0.2);
}

:deep(.arco-tag) {
  font-weight: 500;
}

:deep(.arco-btn-text[status="warning"]) {
  color: #ff7d00;
}

:deep(.arco-btn-text[status="warning"]:hover) {
  background: rgba(255, 125, 0, 0.1);
}

:deep(.arco-btn-text[status="success"]) {
  color: #00b42a;
}

:deep(.arco-btn-text[status="success"]:hover) {
  background: rgba(0, 180, 42, 0.1);
}
</style>
