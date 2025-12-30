<template>
  <div class="applications-page">
    <div class="page-header">
      <h1>申请管理</h1>
      <div class="header-actions">
        <a-select v-model="statusFilter" placeholder="状态筛选" style="width: 120px" allow-clear>
          <a-option :value="1">待审批</a-option>
          <a-option :value="2">已批准</a-option>
          <a-option :value="3">已驳回</a-option>
        </a-select>
        <a-button @click="fetchApplications">
          <template #icon><icon-refresh /></template>
          刷新
        </a-button>
      </div>
    </div>

    <a-table
      :columns="columns"
      :data="applications"
      :loading="loading"
      :pagination="pagination"
      @page-change="handlePageChange"
    >
      <template #status="{ record }">
        <a-tag :color="getStatusColor(record.status)">
          {{ getStatusText(record.status) }}
        </a-tag>
      </template>
      <template #createdAt="{ record }">
        {{ formatDate(record.createdAt) }}
      </template>
      <template #actions="{ record }">
        <a-button type="text" size="small" @click="viewDetail(record)">
          查看详情
        </a-button>
      </template>
    </a-table>

    <!-- 详情弹窗 -->
    <a-modal v-model:visible="detailVisible" title="申请详情" :footer="false" width="600px">
      <a-descriptions :column="1" bordered v-if="currentApplication">
        <a-descriptions-item label="申请ID">{{ currentApplication.id }}</a-descriptions-item>
        <a-descriptions-item label="事项标题">{{ currentApplication.title }}</a-descriptions-item>
        <a-descriptions-item label="事项描述">{{ currentApplication.description }}</a-descriptions-item>
        <a-descriptions-item label="申请人ID">{{ currentApplication.applicantId }}</a-descriptions-item>
        <a-descriptions-item label="审批人ID">{{ currentApplication.approverId }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="getStatusColor(currentApplication.status)">
            {{ getStatusText(currentApplication.status) }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="备注">{{ currentApplication.remark || '-' }}</a-descriptions-item>
        <a-descriptions-item label="审批意见">{{ currentApplication.approvalDetail || '-' }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ formatDate(currentApplication.createdAt) }}</a-descriptions-item>
        <a-descriptions-item label="更新时间">{{ formatDate(currentApplication.updatedAt) }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { adminAPI, type Application } from '@/services/api'
import { IconRefresh } from '@arco-design/web-vue/es/icon'

const applications = ref<Application[]>([])
const loading = ref(false)
const statusFilter = ref<number | undefined>(undefined)
const detailVisible = ref(false)
const currentApplication = ref<Application | null>(null)

const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0
})

const columns = [
  { title: '申请ID', dataIndex: 'id', width: 80 },
  { title: '事项标题', dataIndex: 'title', ellipsis: true },
  { title: '事项描述', dataIndex: 'description', ellipsis: true },
  { title: '申请人ID', dataIndex: 'applicantId', width: 100 },
  { title: '审批人ID', dataIndex: 'approverId', width: 100 },
  { title: '状态', slotName: 'status', width: 100 },
  { title: '申请时间', slotName: 'createdAt', width: 160 },
  { title: '操作', slotName: 'actions', width: 100 }
]

const fetchApplications = async (): Promise<void> => {
  loading.value = true
  try {
    const response = await adminAPI.getAllApplications({
      pageNum: pagination.value.current,
      pageSize: pagination.value.pageSize,
      status: statusFilter.value
    })
    applications.value = response.records || []
    pagination.value.total = response.total || 0
  } catch (error: any) {
    Message.error(error.message || '获取申请列表失败')
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number): void => {
  pagination.value.current = page
  fetchApplications()
}

const viewDetail = (record: Application): void => {
  currentApplication.value = record
  detailVisible.value = true
}

const getStatusColor = (status: number): string => {
  const colors: Record<number, string> = {
    1: 'orange',
    2: 'green',
    3: 'red',
    4: 'gray'
  }
  return colors[status] || 'gray'
}

const getStatusText = (status: number): string => {
  const texts: Record<number, string> = {
    1: '待审批',
    2: '已批准',
    3: '已驳回',
    4: '草稿'
  }
  return texts[status] || '未知'
}

const formatDate = (date: string): string => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

watch(statusFilter, () => {
  pagination.value.current = 1
  fetchApplications()
})

onMounted(() => {
  fetchApplications()
})
</script>

<style scoped>
.applications-page {
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

.header-actions {
  display: flex;
  gap: 12px;
}

:deep(.arco-table) {
  background: white;
  border-radius: 12px;
  overflow: hidden;
}

:deep(.arco-table-th) {
  background: #f7f8fa;
}
</style>
