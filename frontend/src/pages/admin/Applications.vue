<template>
  <div class="admin-page">
    <div class="admin-page-header">
      <div class="admin-page-heading">
        <h1 class="admin-page-title">申请管理</h1>
        <p class="admin-page-subtitle">查看所有申请；待审批的申请管理员可以直接批准或驳回</p>
      </div>
      <div class="admin-page-actions">
        <a-select v-model="statusFilter" placeholder="全部状态" style="width: 128px" allow-clear>
          <a-option v-for="option in APPLICATION_STATUS_OPTIONS" :key="option.value" :value="option.value">
            {{ option.text }}
          </a-option>
        </a-select>
        <a-button :loading="loading" @click="fetchApplications">
          <template #icon><icon-refresh /></template>
          刷新
        </a-button>
      </div>
    </div>

    <a-table
      class="admin-table desktop-only"
      row-key="id"
      :columns="columns"
      :data="applications"
      :loading="loading"
      :pagination="pagination"
      :scroll="{ x: 1100 }"
      @page-change="handlePageChange"
    >
      <template #people="{ record }">
        {{ personName(record.applicantName, record.applicantId) }}
        <span class="arrow">→</span>
        {{ personName(record.approverName, record.approverId) }}
      </template>
      <template #status="{ record }">
        <a-tag :color="applicationStatusArcoColor(record.status)">{{ applicationStatusText(record.status) }}</a-tag>
      </template>
      <template #createdAt="{ record }">
        <span class="tabular">{{ formatDateTime(record.createdAt) }}</span>
      </template>
      <template #actions="{ record }">
        <a-space :size="4">
          <a-button type="text" size="small" @click="viewDetail(record)">详情</a-button>
          <template v-if="record.status === ApplicationStatus.PENDING">
            <a-button type="text" size="small" @click="openDecision(record, 'approve')">批准</a-button>
            <a-button type="text" size="small" status="danger" @click="openDecision(record, 'reject')">驳回</a-button>
          </template>
        </a-space>
      </template>
    </a-table>

    <a-spin class="mobile-only" :loading="loading">
      <div class="admin-mobile-list">
        <article v-for="record in applications" :key="record.id" class="admin-mobile-card">
          <div class="admin-mobile-card-header">
            <div>
              <div class="admin-mobile-title">{{ record.title || '语音申请' }}</div>
              <div class="admin-mobile-meta">#{{ record.id }} · {{ formatDateTime(record.createdAt) }}</div>
            </div>
            <a-tag :color="applicationStatusArcoColor(record.status)">{{ applicationStatusText(record.status) }}</a-tag>
          </div>

          <div class="admin-mobile-field-grid">
            <div class="admin-mobile-field">
              <div class="admin-mobile-field-label">申请人</div>
              <div class="admin-mobile-field-value">{{ personName(record.applicantName, record.applicantId) }}</div>
            </div>
            <div class="admin-mobile-field">
              <div class="admin-mobile-field-label">审批人</div>
              <div class="admin-mobile-field-value">{{ personName(record.approverName, record.approverId) }}</div>
            </div>
          </div>

          <div class="admin-mobile-actions">
            <a-button size="small" @click="viewDetail(record)">详情</a-button>
            <template v-if="record.status === ApplicationStatus.PENDING">
              <a-button size="small" type="primary" @click="openDecision(record, 'approve')">批准</a-button>
              <a-button size="small" status="danger" @click="openDecision(record, 'reject')">驳回</a-button>
            </template>
          </div>
        </article>

        <div v-if="!applications.length && !loading" class="admin-empty-state">没有符合条件的申请</div>

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

    <a-modal v-model:visible="detailVisible" title="申请详情" :footer="false" :width="modalWidth">
      <template v-if="currentApplication">
        <a-descriptions :column="1" bordered size="medium">
          <a-descriptions-item label="编号">#{{ currentApplication.id }}</a-descriptions-item>
          <a-descriptions-item label="标题">{{ currentApplication.title || '语音申请' }}</a-descriptions-item>
          <a-descriptions-item label="说明">{{ currentApplication.description || '-' }}</a-descriptions-item>
          <a-descriptions-item label="备注">{{ currentApplication.remark || '-' }}</a-descriptions-item>
          <a-descriptions-item label="申请人">{{ personName(currentApplication.applicantName, currentApplication.applicantId) }}</a-descriptions-item>
          <a-descriptions-item label="审批人">{{ personName(currentApplication.approverName, currentApplication.approverId) }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="applicationStatusArcoColor(currentApplication.status)">
              {{ applicationStatusText(currentApplication.status) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item v-if="currentApplication.rejectReason" label="驳回原因">
            {{ currentApplication.rejectReason }}
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ formatDateTime(currentApplication.createdAt) }}</a-descriptions-item>
        </a-descriptions>

        <h3 class="timeline-title">处理记录</h3>
        <a-spin :loading="timelineLoading" class="timeline-spin">
          <a-timeline v-if="timeline.length">
            <a-timeline-item v-for="log in timeline" :key="log.id" :label="formatDateTime(log.createdAt)">
              <b>{{ log.operatorName || '未知用户' }}</b> {{ log.operationTypeDesc || operationTypeText(log.operationType) }}
              <div v-if="log.operationDetail" class="timeline-detail">{{ log.operationDetail }}</div>
            </a-timeline-item>
          </a-timeline>
          <p v-else-if="!timelineLoading" class="timeline-empty">没有处理记录</p>
        </a-spin>
      </template>
    </a-modal>

    <!-- on-before-ok：接口失败时返回 false，弹窗不关闭，已填的内容不丢 -->
    <a-modal
      v-model:visible="decisionVisible"
      :title="decision === 'approve' ? '批准申请' : '驳回申请'"
      :ok-text="decision === 'approve' ? '批准' : '驳回'"
      :ok-button-props="decision === 'reject' ? { status: 'danger' } : {}"
      :width="modalWidth"
      :on-before-ok="submitDecision"
    >
      <p class="decision-subject">{{ currentApplication?.title || '语音申请' }}</p>
      <p class="decision-hint">
        以管理员身份处理，审批人会收到通知。{{ decision === 'approve' ? '不填写意见时记为"管理员批准"。' : '不填写原因时记为"管理员驳回"。' }}
      </p>
      <a-textarea
        v-model="decisionComment"
        :placeholder="decision === 'approve' ? '审批意见（选填）' : '驳回原因（选填）'"
        :max-length="500"
        show-word-limit
        :auto-size="{ minRows: 3, maxRows: 6 }"
      />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { IconRefresh } from '@arco-design/web-vue/es/icon'
import { adminAPI, logAPI, type Application, type OperationLogDTO } from '@/services/api'
import { formatDateTime } from '@/utils/format'
import {
  APPLICATION_STATUS_OPTIONS,
  ApplicationStatus,
  applicationStatusArcoColor,
  applicationStatusText,
  operationTypeText,
} from '@/utils/status'

const route = useRoute()
const modalWidth = 'min(600px, calc(100vw - 32px))'

const applications = ref<Application[]>([])
const loading = ref(false)
const initialStatus = Number(route.query.status)
const statusFilter = ref<number | undefined>(Number.isInteger(initialStatus) && initialStatus > 0 ? initialStatus : undefined)
const pagination = ref({ current: 1, pageSize: 10, total: 0 })

const detailVisible = ref(false)
const currentApplication = ref<Application | null>(null)
const timeline = ref<OperationLogDTO[]>([])
const timelineLoading = ref(false)

const decisionVisible = ref(false)
const decision = ref<'approve' | 'reject'>('approve')
const decisionComment = ref('')

const columns = [
  { title: '编号', dataIndex: 'id', width: 80 },
  { title: '标题', dataIndex: 'title', ellipsis: true, tooltip: true, width: 220 },
  { title: '说明', dataIndex: 'description', ellipsis: true, tooltip: true, width: 260 },
  { title: '申请人 → 审批人', slotName: 'people', width: 200 },
  { title: '状态', slotName: 'status', width: 96 },
  { title: '申请时间', slotName: 'createdAt', width: 160 },
  { title: '操作', slotName: 'actions', width: 196, fixed: 'right' as const },
]

const personName = (name: string | undefined, id: number): string => name || `用户 #${id}`

const fetchApplications = async (): Promise<void> => {
  loading.value = true
  try {
    const page = await adminAPI.getAllApplications({
      pageNum: pagination.value.current,
      pageSize: pagination.value.pageSize,
      status: statusFilter.value,
    })
    applications.value = page.records || []
    pagination.value.total = page.total || 0

    // 处理掉当前页最后一条后，这一页可能已经不存在了，退回上一页
    if (applications.value.length === 0 && pagination.value.current > 1 && pagination.value.total > 0) {
      pagination.value.current -= 1
      await fetchApplications()
    }
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

const viewDetail = async (record: Application): Promise<void> => {
  currentApplication.value = record
  detailVisible.value = true
  timeline.value = []
  timelineLoading.value = true
  try {
    timeline.value = await logAPI.getApplicationTimeline(record.id) || []
  } catch (error: any) {
    Message.error(error.message || '处理记录加载失败')
  } finally {
    timelineLoading.value = false
  }
}

const openDecision = (record: Application, type: 'approve' | 'reject'): void => {
  currentApplication.value = record
  decision.value = type
  decisionComment.value = ''
  decisionVisible.value = true
}

const submitDecision = async (): Promise<boolean> => {
  if (!currentApplication.value) return true
  try {
    if (decision.value === 'approve') {
      await adminAPI.adminApproveApplication(currentApplication.value.id, decisionComment.value)
      Message.success('已批准')
    } else {
      await adminAPI.adminRejectApplication(currentApplication.value.id, decisionComment.value)
      Message.success('已驳回')
    }
    fetchApplications()
    return true
  } catch (error: any) {
    Message.error(error.message || '操作失败')
    return false
  }
}

// Arco Select 清空时给的是空字符串，统一成 undefined，避免请求里带 status=
watch(statusFilter, (value) => {
  if ((value as unknown) === '') {
    statusFilter.value = undefined
    return
  }
  pagination.value.current = 1
  fetchApplications()
})

onMounted(fetchApplications)
</script>

<style scoped>
.arrow {
  margin: 0 4px;
  color: var(--admin-text-soft);
}

.timeline-title {
  margin: 20px 0 12px;
  color: var(--admin-text);
  font-size: 14px;
  font-weight: 600;
}

.timeline-spin {
  display: block;
}

.timeline-detail {
  margin-top: 2px;
  color: var(--admin-text-muted);
}

.timeline-empty {
  color: var(--admin-text-soft);
  font-size: 13px;
}

.decision-subject {
  color: var(--admin-text);
  font-size: 15px;
  font-weight: 600;
}

.decision-hint {
  margin: 4px 0 12px;
  color: var(--admin-text-muted);
  font-size: 13px;
}
</style>
