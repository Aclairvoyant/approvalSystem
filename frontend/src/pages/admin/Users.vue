<template>
  <div class="users-page">
    <div class="page-header">
      <h1>用户管理</h1>
      <a-button @click="fetchUsers">
        <template #icon><icon-refresh /></template>
        刷新
      </a-button>
    </div>

    <a-table
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
      <template #createdAt="{ record }">
        {{ formatDate(record.createdAt) }}
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { adminAPI, type User } from '@/services/api'
import { IconRefresh } from '@arco-design/web-vue/es/icon'

const users = ref<User[]>([])
const loading = ref(false)

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
  { title: '创建时间', slotName: 'createdAt', width: 160 }
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

const formatDate = (date: string): string => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

onMounted(() => {
  fetchUsers()
})
</script>

<style scoped>
.users-page {
  max-width: 1400px;
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
}

:deep(.arco-table-th) {
  background: #f7f8fa;
}

:deep(.arco-avatar) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
</style>
