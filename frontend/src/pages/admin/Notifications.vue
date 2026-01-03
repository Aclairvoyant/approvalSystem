<template>
  <div class="notifications-page">
    <div class="page-header">
      <h1>通知管理</h1>
      <div class="header-actions">
        <a-select v-model="notifyTypeFilter" placeholder="通知类型" style="width: 120px" allow-clear>
          <a-option :value="1">短信</a-option>
          <a-option :value="2">邮件</a-option>
        </a-select>
        <a-select v-model="sendStatusFilter" placeholder="发送状态" style="width: 120px" allow-clear>
          <a-option :value="1">待发送</a-option>
          <a-option :value="2">已发送</a-option>
          <a-option :value="3">发送失败</a-option>
        </a-select>
        <a-button @click="fetchNotifications">
          <template #icon><icon-refresh /></template>
          刷新
        </a-button>
      </div>
    </div>

    <a-table
      :columns="columns"
      :data="notifications"
      :loading="loading"
      :pagination="pagination"
      @page-change="handlePageChange"
    >
      <template #notifyType="{ record }">
        <a-tag :color="getNotifyTypeColor(record.notifyType)">
          {{ getNotifyTypeText(record.notifyType) }}
        </a-tag>
      </template>
      <template #sendStatus="{ record }">
        <a-tag :color="getSendStatusColor(record.sendStatus)">
          {{ getSendStatusText(record.sendStatus) }}
        </a-tag>
      </template>
      <template #receiver="{ record }">
        <div class="receiver-info">
          <div v-if="record.phone" class="receiver-item">
            <icon-phone /> {{ record.phone }}
          </div>
          <div v-if="record.email" class="receiver-item">
            <icon-email /> {{ record.email }}
          </div>
          <div v-if="!record.phone && !record.email" class="receiver-item empty">
            未设置
          </div>
        </div>
      </template>
      <template #createdAt="{ record }">
        {{ formatDate(record.createdAt) }}
      </template>
      <template #sentAt="{ record }">
        {{ formatDate(record.sentAt) }}
      </template>
      <template #actions="{ record }">
        <a-button type="text" size="small" @click="viewDetail(record)">
          查看详情
        </a-button>
      </template>
    </a-table>

    <!-- 详情弹窗 -->
    <a-modal
      v-model:visible="detailVisible"
      title="通知详情"
      :footer="false"
      width="700px"
      :mask-closable="false"
    >
      <a-descriptions :column="1" bordered v-if="currentNotification">
        <a-descriptions-item label="通知ID">
          <a-tag color="arcoblue">{{ currentNotification.id }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="申请ID">
          <a-tag color="purple">{{ currentNotification.applicationId }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="接收用户ID">
          {{ currentNotification.notifyUserId }}
        </a-descriptions-item>
        <a-descriptions-item label="通知类型">
          <a-tag :color="getNotifyTypeColor(currentNotification.notifyType)">
            {{ getNotifyTypeText(currentNotification.notifyType) }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="接收方式">
          <div v-if="currentNotification.phone" class="receiver-detail">
            <icon-phone /> 手机号: {{ currentNotification.phone }}
          </div>
          <div v-if="currentNotification.email" class="receiver-detail">
            <icon-email /> 邮箱: {{ currentNotification.email }}
          </div>
        </a-descriptions-item>
        <a-descriptions-item label="通知标题">
          {{ currentNotification.notifyTitle }}
        </a-descriptions-item>
        <a-descriptions-item label="通知内容">
          <div class="notification-content">
            {{ currentNotification.notifyContent }}
          </div>
        </a-descriptions-item>
        <a-descriptions-item label="发送状态">
          <a-tag :color="getSendStatusColor(currentNotification.sendStatus)">
            {{ getSendStatusText(currentNotification.sendStatus) }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="错误信息" v-if="currentNotification.sendError">
          <a-alert type="error" :message="currentNotification.sendError" />
        </a-descriptions-item>
        <a-descriptions-item label="创建时间">
          {{ formatDate(currentNotification.createdAt) }}
        </a-descriptions-item>
        <a-descriptions-item label="发送时间">
          {{ formatDate(currentNotification.sentAt) }}
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { adminAPI, type Notification } from '@/services/api'
import { IconRefresh, IconPhone, IconEmail } from '@arco-design/web-vue/es/icon'

const notifications = ref<Notification[]>([])
const loading = ref(false)
const notifyTypeFilter = ref<number | undefined>(undefined)
const sendStatusFilter = ref<number | undefined>(undefined)
const detailVisible = ref(false)
const currentNotification = ref<Notification | null>(null)

const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0
})

const columns = [
  { title: '通知ID', dataIndex: 'id', width: 80 },
  { title: '申请ID', dataIndex: 'applicationId', width: 90 },
  { title: '用户ID', dataIndex: 'notifyUserId', width: 90 },
  { title: '通知类型', slotName: 'notifyType', width: 100 },
  { title: '接收方', slotName: 'receiver', width: 200 },
  { title: '通知标题', dataIndex: 'notifyTitle', ellipsis: true, width: 150 },
  { title: '发送状态', slotName: 'sendStatus', width: 100 },
  { title: '创建时间', slotName: 'createdAt', width: 160 },
  { title: '发送时间', slotName: 'sentAt', width: 160 },
  { title: '操作', slotName: 'actions', width: 100, fixed: 'right' }
]

const fetchNotifications = async (): Promise<void> => {
  loading.value = true
  try {
    const response = await adminAPI.getAllNotifications({
      pageNum: pagination.value.current,
      pageSize: pagination.value.pageSize,
      sendStatus: sendStatusFilter.value,
      notifyType: notifyTypeFilter.value
    })
    notifications.value = response.records || []
    pagination.value.total = response.total || 0
  } catch (error: any) {
    Message.error(error.message || '获取通知列表失败')
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number): void => {
  pagination.value.current = page
  fetchNotifications()
}

const viewDetail = (record: Notification): void => {
  currentNotification.value = record
  detailVisible.value = true
}

const getNotifyTypeColor = (type: number): string => {
  const colors: Record<number, string> = {
    1: 'blue',
    2: 'purple'
  }
  return colors[type] || 'gray'
}

const getNotifyTypeText = (type: number): string => {
  const texts: Record<number, string> = {
    1: '短信',
    2: '邮件'
  }
  return texts[type] || '未知'
}

const getSendStatusColor = (status: number): string => {
  const colors: Record<number, string> = {
    1: 'orange',
    2: 'green',
    3: 'red'
  }
  return colors[status] || 'gray'
}

const getSendStatusText = (status: number): string => {
  const texts: Record<number, string> = {
    1: '待发送',
    2: '已发送',
    3: '发送失败'
  }
  return texts[status] || '未知'
}

const formatDate = (date: string): string => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

watch([notifyTypeFilter, sendStatusFilter], () => {
  pagination.value.current = 1
  fetchNotifications()
})

onMounted(() => {
  fetchNotifications()
})
</script>

<style scoped>
.notifications-page {
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

.header-actions {
  display: flex;
  gap: 12px;
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

.receiver-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.receiver-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #4e5969;
}

.receiver-item.empty {
  color: #c9cdd4;
}

.receiver-detail {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f7f8fa;
  border-radius: 6px;
  margin: 4px 0;
}

.notification-content {
  padding: 12px;
  background: #f7f8fa;
  border-radius: 6px;
  line-height: 1.6;
  max-height: 200px;
  overflow-y: auto;
}

:deep(.arco-descriptions-item-label) {
  font-weight: 500;
  color: #4e5969;
}

:deep(.arco-alert) {
  margin-top: 4px;
}
</style>
