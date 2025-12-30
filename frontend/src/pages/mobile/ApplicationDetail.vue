<template>
  <div class="application-detail-page">
    <!-- 顶部导航 -->
    <van-nav-bar
      title="申请详情"
      left-arrow
      @click-left="router.back()"
    />

    <!-- 加载中 -->
    <van-loading v-if="loading" class="loading-state" size="24px" vertical>
      加载中...
    </van-loading>

    <template v-else>
      <!-- 状态卡片 -->
      <div class="status-card">
        <div class="status-header">
          <span class="title">{{ application?.title }}</span>
          <van-tag :type="getStatusType(application?.status)" size="medium">
            {{ getStatusText(application?.status) }}
          </van-tag>
        </div>
        <div class="status-time">
          <van-icon name="clock-o" />
          <span>{{ formatDate(application?.createdAt) }}</span>
        </div>
      </div>

      <!-- 基本信息 -->
      <van-cell-group inset class="info-group">
        <van-cell title="事项描述" :label="application?.description" />
        <van-cell title="备注" :label="application?.remark || '无'" />
        <van-cell title="审批人ID" :value="application?.approverId" />
      </van-cell-group>

      <!-- 申请附件 -->
      <van-cell-group v-if="applicationAttachments.length > 0" inset class="info-group">
        <van-cell title="申请附件" />
        <div class="attachment-section">
          <!-- 图片预览 -->
          <div v-if="applicationImages.length > 0" class="image-preview">
            <van-image
              v-for="(img, index) in applicationImages"
              :key="img.attachmentId"
              width="80"
              height="80"
              fit="cover"
              :src="img.fileUrl"
              @click="previewImages(applicationImages, index)"
            />
          </div>
          <!-- 其他文件 -->
          <div v-for="att in applicationFiles" :key="att.attachmentId" class="file-item">
            <van-icon name="description" />
            <a :href="att.fileUrl" target="_blank">{{ att.fileName }}</a>
          </div>
        </div>
      </van-cell-group>

      <!-- 审批附件 -->
      <van-cell-group v-if="approvalAttachments.length > 0" inset class="info-group">
        <van-cell title="审批附件" />
        <div class="attachment-section">
          <!-- 图片预览 -->
          <div v-if="approvalImages.length > 0" class="image-preview">
            <van-image
              v-for="(img, index) in approvalImages"
              :key="img.attachmentId"
              width="80"
              height="80"
              fit="cover"
              :src="img.fileUrl"
              @click="previewImages(approvalImages, index)"
            />
          </div>
          <!-- 其他文件 -->
          <div v-for="att in approvalFiles" :key="att.attachmentId" class="file-item">
            <van-icon name="description" />
            <a :href="att.fileUrl" target="_blank">{{ att.fileName }}</a>
          </div>
        </div>
      </van-cell-group>

      <!-- 语音通知 -->
      <van-cell-group v-if="canSendVoiceNotification" inset class="info-group">
        <van-cell title="语音通知" />
        <div class="voice-notification-section">
          <van-button
            type="warning"
            block
            icon="phone-o"
            @click="handleSendVoiceNotification"
            :loading="sendingVoiceNotification"
          >
            发送语音通知给审批人
          </van-button>
        </div>
      </van-cell-group>

      <!-- 审批操作 -->
      <van-cell-group v-if="canApprove" inset class="info-group">
        <van-cell title="审批操作" />
        <div class="approval-form">
          <van-field
            v-model="approvalDetail"
            type="textarea"
            placeholder="请输入审批意见"
            rows="3"
            autosize
          />
          <van-field name="uploader" label="上传附件">
            <template #input>
              <van-uploader
                v-model="approvalFileList"
                :max-count="5"
                :max-size="10 * 1024 * 1024"
                accept="image/*,.pdf,.doc,.docx"
                @oversize="onOversize"
              />
            </template>
          </van-field>
          <div class="action-buttons">
            <van-button type="primary" block @click="handleApprove" :loading="approving">
              批准
            </van-button>
            <van-button type="danger" block plain @click="handleReject" :loading="rejecting">
              驳回
            </van-button>
          </div>
        </div>
      </van-cell-group>

      <!-- 处理流程 -->
      <van-cell-group inset class="info-group">
        <van-cell title="处理流程" />
        <div class="timeline-section">
          <van-steps direction="vertical" :active="operationLogs.length - 1">
            <van-step v-for="log in operationLogs" :key="log.id">
              <div class="step-title">{{ log.operationTypeDesc }}</div>
              <div class="step-desc">{{ log.operatorName }} - {{ log.operationDetail || '无备注' }}</div>
              <div class="step-time">{{ formatDate(log.createdAt) }}</div>
            </van-step>
          </van-steps>
          <van-empty v-if="operationLogs.length === 0" description="暂无处理记录" />
        </div>
      </van-cell-group>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showSuccessToast, showLoadingToast, closeToast, showImagePreview } from 'vant'
import { useUserStore } from '@/store/modules/user'
import { applicationAPI, logAPI, attachmentAPI } from '@/services/api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const applicationId = ref<number>(parseInt(route.params.id as string))
const application = ref<any>(null)
const operationLogs = ref<any[]>([])
const applicationAttachments = ref<any[]>([])
const approvalAttachments = ref<any[]>([])
const approvalFileList = ref<any[]>([])
const approvalDetail = ref('')
const loading = ref(true)
const approving = ref(false)
const rejecting = ref(false)
const sendingVoiceNotification = ref(false)

// 判断是否是图片
const isImage = (fileType: string): boolean => {
  return fileType?.startsWith('image/')
}

// 分离图片和文件
const applicationImages = computed(() =>
  applicationAttachments.value.filter(a => isImage(a.fileType))
)
const applicationFiles = computed(() =>
  applicationAttachments.value.filter(a => !isImage(a.fileType))
)
const approvalImages = computed(() =>
  approvalAttachments.value.filter(a => isImage(a.fileType))
)
const approvalFiles = computed(() =>
  approvalAttachments.value.filter(a => !isImage(a.fileType))
)

const canApprove = computed(() => {
  return application.value?.approverId === userStore.userId && application.value?.status === 1
})

const canSendVoiceNotification = computed(() => {
  // 只有待审批状态的申请，且当前用户是申请人，才能发送语音通知
  return application.value?.applicantId === userStore.userId && application.value?.status === 1
})

onMounted(async () => {
  await fetchAll()
})

const fetchAll = async (): Promise<void> => {
  loading.value = true
  try {
    await Promise.all([
      fetchApplicationDetail(),
      fetchTimeline(),
      fetchAttachments()
    ])
  } finally {
    loading.value = false
  }
}

const fetchApplicationDetail = async (): Promise<void> => {
  try {
    const response = await applicationAPI.getApplicationDetail(applicationId.value)
    application.value = response
  } catch (error: any) {
    showToast(error.message || '获取申请详情失败')
  }
}

const fetchTimeline = async (): Promise<void> => {
  try {
    const response = await logAPI.getApplicationTimeline(applicationId.value)
    operationLogs.value = response || []
  } catch (error: any) {
    console.error('获取操作日志失败', error)
  }
}

const fetchAttachments = async (): Promise<void> => {
  try {
    const [appAtts, approvalAtts] = await Promise.all([
      attachmentAPI.getApplicationAttachments(applicationId.value),
      attachmentAPI.getApprovalAttachments(applicationId.value)
    ])
    applicationAttachments.value = appAtts || []
    approvalAttachments.value = approvalAtts || []
  } catch (error: any) {
    console.error('获取附件失败', error)
  }
}

// 预览图片
const previewImages = (images: any[], startIndex: number): void => {
  showImagePreview({
    images: images.map(img => img.fileUrl),
    startPosition: startIndex
  })
}

// 文件超限
const onOversize = (): void => {
  showToast('文件大小不能超过10MB')
}

// 上传审批附件
const uploadApprovalFiles = async (): Promise<void> => {
  for (const fileItem of approvalFileList.value) {
    if (fileItem.file) {
      try {
        await attachmentAPI.uploadApprovalAttachment(applicationId.value, fileItem.file)
      } catch (error) {
        console.error('上传附件失败:', error)
      }
    }
  }
}

const handleApprove = async (): Promise<void> => {
  approving.value = true
  showLoadingToast({ message: '处理中...', forbidClick: true })
  try {
    if (approvalFileList.value.length > 0) {
      await uploadApprovalFiles()
    }
    await applicationAPI.approveApplication(applicationId.value, { approvalDetail: approvalDetail.value })
    closeToast()
    showSuccessToast('审批通过')
    approvalFileList.value = []
    approvalDetail.value = ''
    await fetchAll()
  } catch (error: any) {
    closeToast()
    showToast(error.message || '审批失败')
  } finally {
    approving.value = false
  }
}

const handleReject = async (): Promise<void> => {
  rejecting.value = true
  showLoadingToast({ message: '处理中...', forbidClick: true })
  try {
    if (approvalFileList.value.length > 0) {
      await uploadApprovalFiles()
    }
    await applicationAPI.rejectApplication(applicationId.value, { approvalDetail: approvalDetail.value })
    closeToast()
    showSuccessToast('已驳回')
    approvalFileList.value = []
    approvalDetail.value = ''
    await fetchAll()
  } catch (error: any) {
    closeToast()
    showToast(error.message || '驳回失败')
  } finally {
    rejecting.value = false
  }
}

// 发送语音通知
const handleSendVoiceNotification = async (): Promise<void> => {
  sendingVoiceNotification.value = true
  showLoadingToast({ message: '发送中...', forbidClick: true })
  try {
    await applicationAPI.sendVoiceNotification(applicationId.value)
    closeToast()
    showSuccessToast('语音通知已发送')
  } catch (error: any) {
    closeToast()
    showToast(error.message || '发送失败')
  } finally {
    sendingVoiceNotification.value = false
  }
}

const getStatusType = (status?: number): string => {
  const types: Record<number, string> = {
    1: 'warning',
    2: 'success',
    3: 'danger',
    4: 'default'
  }
  return status ? types[status] : 'default'
}

const getStatusText = (status?: number): string => {
  const texts: Record<number, string> = {
    1: '待审批',
    2: '已通过',
    3: '已驳回',
    4: '草稿'
  }
  return status ? texts[status] : '未知'
}

const formatDate = (date?: string): string => {
  if (!date) return ''
  const d = new Date(date)
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>

<style scoped>
.application-detail-page {
  min-height: 100vh;
  background: #f5f6f7;
  padding-bottom: 20px;
}

:deep(.van-nav-bar) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

:deep(.van-nav-bar__title) {
  color: white;
}

:deep(.van-nav-bar__arrow) {
  color: white;
}

.loading-state {
  padding: 60px 0;
}

.status-card {
  margin: 12px;
  padding: 16px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.status-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.status-header .title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  flex: 1;
  margin-right: 12px;
}

.status-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #999;
}

.info-group {
  margin: 12px;
}

.attachment-section {
  padding: 12px 16px;
}

.image-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.image-preview :deep(.van-image) {
  border-radius: 8px;
  overflow: hidden;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f5f5f5;
  border-radius: 6px;
  margin-bottom: 8px;
}

.file-item a {
  color: #667eea;
  text-decoration: none;
  font-size: 14px;
}

.file-item :deep(.van-icon) {
  color: #999;
  font-size: 18px;
}

.approval-form {
  padding: 12px 16px;
}

.voice-notification-section {
  padding: 12px 16px;
}

.voice-notification-section .van-button {
  background: linear-gradient(135deg, #ff9a3e 0%, #ff7a18 100%);
  border: none;
}

.action-buttons {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.action-buttons .van-button {
  flex: 1;
}

.timeline-section {
  padding: 12px 16px;
}

.step-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.step-desc {
  font-size: 13px;
  color: #666;
  margin-top: 4px;
}

.step-time {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

:deep(.van-step__circle) {
  background: #667eea;
}

:deep(.van-step__line) {
  background: #667eea;
}
</style>
