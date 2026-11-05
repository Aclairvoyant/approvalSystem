<template>
  <div class="admin-page">
    <div class="admin-page-header">
      <div class="admin-page-heading">
        <h1 class="admin-page-title">通知记录</h1>
        <p class="admin-page-subtitle">申请创建和审批时发出的短信、邮件、推送，以及每条的发送结果</p>
      </div>
      <div class="admin-page-actions">
        <a-select v-model="notifyTypeFilter" placeholder="全部渠道" style="width: 120px" allow-clear>
          <a-option v-for="option in NOTIFY_TYPE_OPTIONS" :key="option.value" :value="option.value">
            {{ option.text }}
          </a-option>
        </a-select>
        <a-select v-model="sendStatusFilter" placeholder="全部状态" style="width: 120px" allow-clear>
          <a-option v-for="option in SEND_STATUS_OPTIONS" :key="option.value" :value="option.value">
            {{ option.text }}
          </a-option>
        </a-select>
        <a-button :loading="loading" @click="fetchNotifications">
          <template #icon><icon-refresh /></template>
          刷新
        </a-button>
      </div>
    </div>

    <a-table
      class="admin-table desktop-only"
      row-key="id"
      :columns="columns"
      :data="notifications"
      :loading="loading"
      :pagination="pagination"
      :scroll="{ x: 1100 }"
      @page-change="handlePageChange"
    >
      <template #title="{ record }">
        <div class="cell-title">{{ record.notifyTitle || '未命名通知' }}</div>
        <div class="cell-sub">申请 #{{ record.applicationId }} · 用户 #{{ record.notifyUserId }}</div>
      </template>
      <template #notifyType="{ record }">{{ notifyTypeText(record.notifyType) }}</template>
      <template #receiver="{ record }">
        <span class="tabular">{{ receiverText(record) }}</span>
      </template>
      <template #sendStatus="{ record }">
        <a-tag :color="sendStatusColor(record.sendStatus)">{{ sendStatusText(record.sendStatus) }}</a-tag>
      </template>
      <template #createdAt="{ record }">
        <span class="tabular">{{ formatDateTime(record.createdAt) }}</span>
      </template>
      <template #actions="{ record }">
        <a-button type="text" size="small" @click="viewDetail(record)">详情</a-button>
      </template>
    </a-table>

    <a-spin class="mobile-only" :loading="loading">
      <div class="admin-mobile-list">
        <article v-for="record in notifications" :key="record.id" class="admin-mobile-card">
          <div class="admin-mobile-card-header">
            <div>
              <div class="admin-mobile-title">{{ record.notifyTitle || '未命名通知' }}</div>
              <div class="admin-mobile-meta">
                {{ notifyTypeText(record.notifyType) }} · 申请 #{{ record.applicationId }} · {{ formatDateTime(record.createdAt) }}
              </div>
            </div>
            <a-tag :color="sendStatusColor(record.sendStatus)">{{ sendStatusText(record.sendStatus) }}</a-tag>
          </div>
          <div class="admin-mobile-field-grid">
            <div class="admin-mobile-field">
              <div class="admin-mobile-field-label">接收方</div>
              <div class="admin-mobile-field-value">{{ receiverText(record) }}</div>
            </div>
            <div class="admin-mobile-field">
              <div class="admin-mobile-field-label">发送时间</div>
              <div class="admin-mobile-field-value">{{ formatDateTime(record.sentAt) }}</div>
            </div>
          </div>
          <div class="admin-mobile-actions">
            <a-button size="small" @click="viewDetail(record)">详情</a-button>
          </div>
        </article>

        <div v-if="!notifications.length && !loading" class="admin-empty-state">没有符合条件的通知</div>

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

    <a-modal v-model:visible="detailVisible" title="通知详情" :footer="false" :width="modalWidth">
      <a-descriptions v-if="currentNotification" :column="1" bordered>
        <a-descriptions-item label="编号">#{{ currentNotification.id }}</a-descriptions-item>
        <a-descriptions-item label="关联申请">#{{ currentNotification.applicationId }}</a-descriptions-item>
        <a-descriptions-item label="渠道">{{ notifyTypeText(currentNotification.notifyType) }}</a-descriptions-item>
        <a-descriptions-item label="接收方">
          用户 #{{ currentNotification.notifyUserId }}<template v-if="receiverText(currentNotification) !== '-'">，{{ receiverText(currentNotification) }}</template>
        </a-descriptions-item>
        <a-descriptions-item label="标题">{{ currentNotification.notifyTitle }}</a-descriptions-item>
        <a-descriptions-item label="内容">
          <div class="notification-content">{{ currentNotification.notifyContent }}</div>
        </a-descriptions-item>
        <a-descriptions-item label="发送状态">
          <a-tag :color="sendStatusColor(currentNotification.sendStatus)">
            {{ sendStatusText(currentNotification.sendStatus) }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item v-if="currentNotification.sendError" label="失败原因">
          <div class="send-error">{{ currentNotification.sendError }}</div>
        </a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ formatDateTime(currentNotification.createdAt) }}</a-descriptions-item>
        <a-descriptions-item label="发送时间">{{ formatDateTime(currentNotification.sentAt) }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconRefresh } from '@arco-design/web-vue/es/icon'
import { adminAPI, type Notification } from '@/services/api'
import { formatDateTime } from '@/utils/format'
import {
  NOTIFY_TYPE_OPTIONS,
  SEND_STATUS_OPTIONS,
  notifyTypeText,
  sendStatusColor,
  sendStatusText,
} from '@/utils/status'

const modalWidth = 'min(640px, calc(100vw - 32px))'

const notifications = ref<Notification[]>([])
const loading = ref(false)
const notifyTypeFilter = ref<number | undefined>(undefined)
const sendStatusFilter = ref<number | undefined>(undefined)
const detailVisible = ref(false)
const currentNotification = ref<Notification | null>(null)
const pagination = ref({ current: 1, pageSize: 10, total: 0 })

const columns = [
  { title: '通知', slotName: 'title', width: 280 },
  { title: '渠道', slotName: 'notifyType', width: 90 },
  { title: '接收方', slotName: 'receiver', width: 220 },
  { title: '状态', slotName: 'sendStatus', width: 100 },
  { title: '创建时间', slotName: 'createdAt', width: 160 },
  { title: '操作', slotName: 'actions', width: 80, fixed: 'right' as const },
]

const receiverText = (record: Notification): string => {
  if (record.notifyType === 1) return record.phone || '-'
  if (record.notifyType === 2) return record.email || '-'
  return record.phone || record.email || '-'
}

const fetchNotifications = async (): Promise<void> => {
  loading.value = true
  try {
    const page = await adminAPI.getAllNotifications({
      pageNum: pagination.value.current,
      pageSize: pagination.value.pageSize,
      sendStatus: sendStatusFilter.value,
      notifyType: notifyTypeFilter.value,
    })
    notifications.value = page.records || []
    pagination.value.total = page.total || 0
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

// Arco Select 清空时给空字符串，统一成 undefined 再请求
watch([notifyTypeFilter, sendStatusFilter], ([type, status]) => {
  if ((type as unknown) === '') {
    notifyTypeFilter.value = undefined
    return
  }
  if ((status as unknown) === '') {
    sendStatusFilter.value = undefined
    return
  }
  pagination.value.current = 1
  fetchNotifications()
})

onMounted(fetchNotifications)
</script>

<style scoped>
.cell-title {
  overflow: hidden;
  color: var(--admin-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-sub {
  color: var(--admin-text-soft);
  font-size: 12px;
}

.notification-content {
  max-height: 200px;
  overflow-y: auto;
  line-height: 1.6;
  white-space: pre-wrap;
}

.send-error {
  color: var(--danger);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.6;
  word-break: break-all;
}
</style>
