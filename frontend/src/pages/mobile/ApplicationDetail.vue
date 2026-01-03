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
        <van-cell title="语音通知">
          <template #label v-if="!userStore.voiceNotificationEnabled">
            <span style="color: #ff7d00; font-size: 12px;">
              ⚠️ 未开通权限，请联系管理员
            </span>
          </template>
        </van-cell>
        <div class="voice-notification-section">
          <van-button
            type="warning"
            block
            icon="phone-o"
            @click="handleSendVoiceNotification"
            :loading="sendingVoiceNotification"
            :disabled="!userStore.voiceNotificationEnabled"
          >
            {{ userStore.voiceNotificationEnabled ? '发送语音通知给审批人' : '语音通知权限未开通' }}
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
          <div class="timeline-wrapper">
            <div v-for="(item, index) in timeline" :key="item.id" class="timeline-item">
              <!-- 操作日志项 -->
              <template v-if="item.type === 'operation'">
                <!-- 左侧头像和连线 -->
                <div class="timeline-left">
                  <div class="timeline-avatar">
                    <van-image
                      v-if="item.data.operatorAvatar"
                      round
                      width="40"
                      height="40"
                      :src="getAvatarUrl(item.data.operatorAvatar)"
                      fit="cover"
                    >
                      <template #error>
                        <div class="avatar-fallback">
                          {{ getAvatarText(item.data.operatorName) }}
                        </div>
                      </template>
                    </van-image>
                    <div v-else class="avatar-fallback">
                      {{ getAvatarText(item.data.operatorName) }}
                    </div>
                  </div>
                  <div v-if="index < timeline.length - 1" class="timeline-line"></div>
                </div>

                <!-- 右侧内容 -->
                <div class="timeline-right">
                  <div class="timeline-content">
                    <div class="step-header">
                      <span class="step-title">{{ item.data.operationTypeDesc }}</span>
                      <span class="step-time">{{ formatDate(item.data.createdAt) }}</span>
                    </div>
                    <div class="step-operator">{{ item.data.operatorName }}</div>
                    <div v-if="item.data.operationDetail" class="step-desc">{{ item.data.operationDetail }}</div>

                    <!-- 印章 - 只在最后一步且状态为已批准或已驳回时显示 -->
                    <div
                      v-if="index === timeline.length - 1 && (application?.status === 2 || application?.status === 3)"
                      class="stamp-wrapper"
                    >
                      <div :class="['stamp', application?.status === 2 ? 'stamp-approved' : 'stamp-rejected']">
                        <div class="stamp-text">{{ application?.status === 2 ? '已批准' : '已驳回' }}</div>
                      </div>
                    </div>
                  </div>
                </div>
              </template>

              <!-- 评论项 -->
              <template v-else-if="item.type === 'comment'">
                <!-- 左侧头像和连线 -->
                <div class="timeline-left">
                  <div class="timeline-avatar comment-avatar">
                    <van-image
                      v-if="item.data.userAvatar"
                      round
                      width="40"
                      height="40"
                      :src="getAvatarUrl(item.data.userAvatar)"
                      fit="cover"
                    >
                      <template #error>
                        <div class="avatar-fallback comment-avatar-bg">
                          {{ getAvatarText(item.data.userName) }}
                        </div>
                      </template>
                    </van-image>
                    <div v-else class="avatar-fallback comment-avatar-bg">
                      {{ getAvatarText(item.data.userName) }}
                    </div>
                  </div>
                  <div v-if="index < timeline.length - 1" class="timeline-line"></div>
                </div>

                <!-- 右侧内容 -->
                <div class="timeline-right">
                  <div class="timeline-content comment-content-wrapper">
                    <div class="comment-header-inline">
                      <div class="comment-info">
                        <span class="comment-author">{{ item.data.userName }}</span>
                        <span class="comment-badge">发表评论</span>
                      </div>
                      <div class="comment-actions-inline">
                        <van-button
                          size="mini"
                          type="primary"
                          plain
                          icon="chat-o"
                          @click="handleReply(item.data)"
                        >
                          回复
                        </van-button>
                        <van-popover
                          v-if="item.data.userId === userStore.userId"
                          v-model:show="item.data.showPopover"
                          :actions="commentActions"
                          placement="bottom-end"
                          @select="handleCommentAction($event, item.data)"
                        >
                          <template #reference>
                            <van-icon name="ellipsis" class="comment-more-icon" />
                          </template>
                        </van-popover>
                      </div>
                    </div>
                    <div class="comment-time-inline">{{ formatCommentTime(item.data.createdAt) }}</div>
                    <div class="comment-text">{{ item.data.content }}</div>

                    <!-- 回复列表 -->
                    <div v-if="item.data.replies && item.data.replies.length > 0" class="replies-inline">
                      <div v-for="reply in item.data.replies" :key="reply.id" class="reply-item-inline">
                        <div class="reply-header-inline">
                          <div class="reply-avatar-small">
                            <van-image
                              v-if="reply.userAvatar"
                              round
                              width="24"
                              height="24"
                              :src="getAvatarUrl(reply.userAvatar)"
                              fit="cover"
                            >
                              <template #error>
                                <div class="reply-avatar-fallback">{{ getAvatarText(reply.userName) }}</div>
                              </template>
                            </van-image>
                            <div v-else class="reply-avatar-fallback">{{ getAvatarText(reply.userName) }}</div>
                          </div>
                          <span class="reply-author">{{ reply.userName }}</span>
                          <span class="reply-time-inline">{{ formatCommentTime(reply.createdAt) }}</span>
                          <van-popover
                            v-if="reply.userId === userStore.userId"
                            v-model:show="reply.showPopover"
                            :actions="commentActions"
                            placement="bottom-end"
                            @select="handleCommentAction($event, reply)"
                          >
                            <template #reference>
                              <van-icon name="ellipsis" class="reply-more-icon" />
                            </template>
                          </van-popover>
                        </div>
                        <div class="reply-text-inline">{{ reply.content }}</div>
                      </div>
                    </div>
                  </div>
                </div>
              </template>
            </div>
          </div>
          <van-empty v-if="timeline.length === 0" description="暂无处理记录" />
        </div>
      </van-cell-group>

      <!-- 评论输入区 -->
      <van-cell-group inset class="info-group">
        <van-cell title="添加评论" />
        <div class="comment-input-section">
          <div v-if="replyTo" class="reply-indicator">
            <van-icon name="arrow-up" />
            <span>回复 {{ replyTo.userName }}</span>
            <van-button size="mini" plain @click="cancelReply">取消</van-button>
          </div>
          <van-field
            v-model="newComment"
            type="textarea"
            placeholder="请输入评论内容..."
            rows="3"
            autosize
            maxlength="500"
            show-word-limit
          >
            <template #button>
              <van-button
                size="small"
                type="primary"
                round
                :loading="submittingComment"
                :disabled="!newComment.trim()"
                @click="handleSubmitComment"
              >
                发送
              </van-button>
            </template>
          </van-field>
        </div>
      </van-cell-group>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showSuccessToast, showLoadingToast, closeToast, showImagePreview, showConfirmDialog } from 'vant'
import { useUserStore } from '@/store/modules/user'
import { applicationAPI, logAPI, attachmentAPI, commentAPI, type ApplicationComment } from '@/services/api'

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

// 评论相关
const comments = ref<(ApplicationComment & { showPopover?: boolean })[]>([])
const newComment = ref('')
const replyTo = ref<ApplicationComment | null>(null)
const submittingComment = ref(false)

// 合并的时间线（包含操作日志和评论）
interface TimelineItem {
  id: string | number
  type: 'operation' | 'comment'
  time: string
  data: any
}

const timeline = computed<TimelineItem[]>(() => {
  const items: TimelineItem[] = []

  // 添加操作日志
  operationLogs.value.forEach(log => {
    items.push({
      id: `log-${log.id}`,
      type: 'operation',
      time: log.createdAt,
      data: log
    })
  })

  // 添加顶级评论（不包括回复）
  comments.value
    .filter(comment => !comment.parentId)
    .forEach(comment => {
      items.push({
        id: `comment-${comment.id}`,
        type: 'comment',
        time: comment.createdAt,
        data: comment
      })
    })

  // 按时间排序
  return items.sort((a, b) => new Date(a.time).getTime() - new Date(b.time).getTime())
})

const commentActions = [
  { text: '删除', value: 'delete' }
]

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
      fetchAttachments(),
      fetchComments()
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
  // 检查语音通知权限
  if (!userStore.voiceNotificationEnabled) {
    showConfirmDialog({
      title: '语音通知权限未开通',
      message: '您尚未开通语音通知权限，该功能需要管理员为您开启。请联系管理员开通权限后使用。',
      confirmButtonText: '知道了',
      showCancelButton: false
    })
    return
  }

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

// 获取头像URL
const getAvatarUrl = (avatar: string): string => {
  if (!avatar) return ''
  if (avatar.startsWith('/')) {
    return `http://localhost:8080${avatar}`
  }
  return avatar
}

// 获取头像文字
const getAvatarText = (name: string): string => {
  if (!name) return 'U'
  return name.charAt(0).toUpperCase()
}

// 获取评论列表
const fetchComments = async (): Promise<void> => {
  try {
    const response = await commentAPI.getApplicationComments(applicationId.value)
    comments.value = response.map(comment => ({ ...comment, showPopover: false }))
  } catch (error: any) {
    console.error('获取评论失败', error)
  }
}

// 发送评论
const handleSubmitComment = async (): Promise<void> => {
  if (!newComment.value.trim()) {
    showToast('请输入评论内容')
    return
  }

  submittingComment.value = true
  try {
    await commentAPI.createComment({
      applicationId: applicationId.value,
      content: newComment.value.trim(),
      parentId: replyTo.value?.id
    })

    showSuccessToast('评论成功')
    newComment.value = ''
    replyTo.value = null
    await fetchComments()
  } catch (error: any) {
    showToast(error.message || '评论失败')
  } finally {
    submittingComment.value = false
  }
}

// 回复评论
const handleReply = (comment: ApplicationComment): void => {
  replyTo.value = comment
  newComment.value = ''
}

// 取消回复
const cancelReply = (): void => {
  replyTo.value = null
  newComment.value = ''
}

// 评论操作
const handleCommentAction = async (action: any, comment: ApplicationComment & { showPopover?: boolean }): Promise<void> => {
  comment.showPopover = false

  if (action.value === 'delete') {
    try {
      await showConfirmDialog({
        title: '确认删除',
        message: '确定要删除这条评论吗？'
      })

      await commentAPI.deleteComment(comment.id)
      showSuccessToast('删除成功')
      await fetchComments()
    } catch (error: any) {
      if (error !== 'cancel') {
        showToast(error.message || '删除失败')
      }
    }
  }
}

// 格式化评论时间
const formatCommentTime = (date: string): string => {
  if (!date) return ''
  const d = new Date(date)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const minutes = Math.floor(diff / (1000 * 60))
  const hours = Math.floor(diff / (1000 * 60 * 60))
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return d.toLocaleDateString('zh-CN')
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

.timeline-wrapper {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.timeline-item {
  display: flex;
  gap: 16px;
  min-height: 80px;
  position: relative;
}

.timeline-left {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}

.timeline-avatar {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  position: relative;
  z-index: 2;
}

.avatar-fallback {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

.timeline-avatar :deep(.van-image) {
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

.timeline-line {
  flex: 1;
  width: 2px;
  background: linear-gradient(180deg, #667eea 0%, #e0e0e0 100%);
  margin-top: 4px;
  min-height: 40px;
}

.timeline-right {
  flex: 1;
  padding-bottom: 20px;
}

.timeline-content {
  background: #f8f9fa;
  border-radius: 12px;
  padding: 12px 16px;
  position: relative;
  border-left: 3px solid #667eea;
}

.timeline-content::before {
  content: '';
  position: absolute;
  left: -11px;
  top: 16px;
  width: 0;
  height: 0;
  border-top: 6px solid transparent;
  border-bottom: 6px solid transparent;
  border-right: 8px solid #667eea;
}

.step-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.step-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.step-time {
  font-size: 11px;
  color: #999;
}

.step-operator {
  font-size: 13px;
  color: #667eea;
  font-weight: 500;
  margin-bottom: 6px;
}

.step-desc {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #e8e8e8;
}

/* 印章样式 */
.stamp-wrapper {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
}

.stamp {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  border: 3px solid;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  animation: stampAppear 0.5s ease-out;
  transform: rotate(-15deg);
  opacity: 0.9;
}

@keyframes stampAppear {
  0% {
    transform: scale(0) rotate(-15deg);
    opacity: 0;
  }
  50% {
    transform: scale(1.1) rotate(-15deg);
  }
  100% {
    transform: scale(1) rotate(-15deg);
    opacity: 0.9;
  }
}

.stamp-approved {
  border-color: #00b42a;
  background: radial-gradient(circle, rgba(0, 180, 42, 0.1) 0%, rgba(0, 180, 42, 0.05) 100%);
}

.stamp-rejected {
  border-color: #f53f3f;
  background: radial-gradient(circle, rgba(245, 63, 63, 0.1) 0%, rgba(245, 63, 63, 0.05) 100%);
}

.stamp-text {
  font-size: 20px;
  font-weight: 900;
  letter-spacing: 2px;
  writing-mode: horizontal-tb;
}

.stamp-approved .stamp-text {
  color: #00b42a;
  text-shadow: 1px 1px 2px rgba(0, 180, 42, 0.3);
}

.stamp-rejected .stamp-text {
  color: #f53f3f;
  text-shadow: 1px 1px 2px rgba(245, 63, 63, 0.3);
}

.stamp::before {
  content: '';
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  border: 2px solid;
  border-color: inherit;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  opacity: 0.5;
}

.stamp::after {
  content: '';
  position: absolute;
  width: 90%;
  height: 90%;
  border-radius: 50%;
  border: 1px solid;
  border-color: inherit;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  opacity: 0.3;
}

/* 评论时间线样式 */
.comment-avatar-bg {
  background: linear-gradient(135deg, #f5a623 0%, #f76b1c 100%) !important;
}

.comment-content-wrapper {
  background: linear-gradient(135deg, #fff5e6 0%, #fffaf0 100%);
  border: 1px solid #ffe7ba;
  border-radius: 12px;
  padding: 12px;
}

.comment-header-inline {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.comment-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.comment-author {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.comment-badge {
  display: inline-block;
  padding: 2px 8px;
  background: linear-gradient(135deg, #f5a623 0%, #f76b1c 100%);
  color: white;
  font-size: 11px;
  border-radius: 10px;
  font-weight: 500;
}

.comment-actions-inline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-more-icon {
  color: #999;
  font-size: 18px;
  cursor: pointer;
  padding: 4px;
}

.comment-time-inline {
  font-size: 12px;
  color: #999;
  margin-bottom: 8px;
}

.comment-text {
  font-size: 14px;
  color: #333;
  line-height: 1.6;
  word-wrap: break-word;
  margin-bottom: 8px;
}

/* 回复列表内联样式 */
.replies-inline {
  margin-top: 12px;
  padding: 8px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 8px;
  border: 1px solid #f0e6d2;
}

.reply-item-inline {
  padding: 8px;
  margin-bottom: 8px;
  background: white;
  border-radius: 6px;
  border: 1px solid #ffeaa7;
}

.reply-item-inline:last-child {
  margin-bottom: 0;
}

.reply-header-inline {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.reply-avatar-small {
  flex-shrink: 0;
}

.reply-avatar-fallback {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f5a623 0%, #f76b1c 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
}

.reply-author {
  font-size: 13px;
  font-weight: 600;
  color: #333;
}

.reply-time-inline {
  font-size: 11px;
  color: #999;
  margin-left: auto;
}

.reply-more-icon {
  color: #999;
  font-size: 16px;
  cursor: pointer;
  padding: 2px;
}

.reply-text-inline {
  font-size: 13px;
  color: #555;
  line-height: 1.5;
  word-wrap: break-word;
  padding-left: 30px;
}

/* 评论输入区样式 */
.comment-input-section {
  padding: 12px 16px;
}

.reply-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: linear-gradient(135deg, #e8f5ff 0%, #f0f9ff 100%);
  border: 1px solid #b3d9ff;
  border-radius: 8px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #1989fa;
}

.reply-indicator span {
  flex: 1;
}

.comment-input-section :deep(.van-field) {
  background: #f8f9fa;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  padding: 12px;
  transition: all 0.3s;
}

.comment-input-section :deep(.van-field:focus-within) {
  background: white;
  border-color: #1989fa;
  box-shadow: 0 0 0 3px rgba(25, 137, 250, 0.1);
}

.comment-input-section :deep(.van-field__control) {
  font-size: 14px;
}

.comment-input-section :deep(.van-button--primary) {
  background: linear-gradient(135deg, #1989fa 0%, #1c7ed6 100%);
  border: none;
}
</style>
