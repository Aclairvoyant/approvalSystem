<template>
  <div class="detail-page">
    <van-nav-bar title="申请详情" left-arrow fixed placeholder @click-left="goBack" />

    <div v-if="loading" class="page-state">
      <van-loading size="24px" vertical>加载中...</van-loading>
    </div>

    <van-empty v-else-if="!application" class="page-state" image="error" :description="loadError || '申请不存在'">
      <van-button size="small" type="primary" @click="fetchAll">重新加载</van-button>
    </van-empty>

    <div v-else class="m-page">
      <!-- 概要 -->
      <section class="m-card m-card--pad">
        <div class="summary-head">
          <h1 class="summary-title">{{ application.title || '语音申请' }}</h1>
          <StatusTag :status="application.status" />
        </div>
        <div class="m-meta summary-meta">
          <span>{{ application.applicantName || '申请人' }} → {{ application.approverName || '审批人' }}</span>
          <span>{{ formatDateTime(application.createdAt) }}</span>
        </div>

        <p v-if="application.status === ApplicationStatus.REJECTED && application.rejectReason" class="m-note reject-note">
          <b>驳回原因：</b>{{ application.rejectReason }}
        </p>

        <dl class="fields">
          <div v-if="application.description" class="field">
            <dt>说明</dt>
            <dd>{{ application.description }}</dd>
          </div>
          <div v-if="application.remark" class="field">
            <dt>备注</dt>
            <dd>{{ application.remark }}</dd>
          </div>
        </dl>
      </section>

      <!-- 语音申请 -->
      <section v-if="isVoiceApplication" class="m-card m-card--pad">
        <h2 class="m-section-title">语音内容</h2>
        <div v-for="att in applicationAudio" :key="att.attachmentId || att.fileUrl" class="audio-row">
          <audio :src="att.fileUrl" controls preload="none"></audio>
        </div>
        <p v-if="applicationAudio.length === 0" class="muted">音频还没有上传完成</p>

        <div v-if="voiceTranscript" class="transcript">
          <div class="transcript-label">转写文字</div>
          <p>{{ voiceTranscript }}</p>
        </div>

        <van-button
          v-if="applicationAudio.length > 0"
          class="transcribe-btn"
          size="small"
          plain
          icon="chat-o"
          :loading="transcribing"
          @click="handleTranscribe"
        >
          {{ voiceTranscript ? '重新转文字' : '转文字' }}
        </van-button>
      </section>

      <!-- 附件 -->
      <section v-if="applicationImages.length || applicationFiles.length" class="m-card m-card--pad">
        <h2 class="m-section-title">申请附件</h2>
        <AttachmentList :images="applicationImages" :files="applicationFiles" />
      </section>

      <section v-if="approvalImages.length || approvalFiles.length || approvalAudio.length" class="m-card m-card--pad">
        <h2 class="m-section-title">审批附件</h2>
        <div v-for="att in approvalAudio" :key="att.attachmentId || att.fileUrl" class="audio-row">
          <audio :src="att.fileUrl" controls preload="none"></audio>
        </div>
        <AttachmentList :images="approvalImages" :files="approvalFiles" />
      </section>

      <!-- 申请人：电话提醒对方 -->
      <section v-if="canSendVoiceNotification" class="m-card m-card--pad remind">
        <div>
          <h2 class="m-section-title">提醒对方</h2>
          <p class="muted">
            {{ userStore.voiceNotificationEnabled ? '给审批人打一个语音电话，提醒尽快处理' : '语音通知未开通，需要管理员为你开启' }}
          </p>
        </div>
        <van-button
          size="small"
          plain
          type="primary"
          icon="phone-o"
          :loading="sendingVoiceNotification"
          :disabled="!userStore.voiceNotificationEnabled"
          @click="handleSendVoiceNotification"
        >
          打电话
        </van-button>
      </section>

      <!-- 审批人：审批操作 -->
      <ApprovalActionPanel v-if="canApprove" :application-id="applicationId" @done="fetchAll" />

      <!-- 处理记录与评论 -->
      <section class="m-card m-card--pad">
        <h2 class="m-section-title timeline-title">处理记录</h2>
        <ApplicationTimeline
          :logs="operationLogs"
          :comments="comments"
          :current-user-id="userStore.userId"
          @reply="replyTo = $event"
          @delete="handleDeleteComment"
        />
      </section>

      <CommentComposer
        :application-id="applicationId"
        :reply-to="replyTo"
        @posted="onCommentPosted"
        @cancel-reply="replyTo = null"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { useUserStore } from '@/store/modules/user'
import {
  applicationAPI,
  attachmentAPI,
  commentAPI,
  logAPI,
  type Application,
  type ApplicationComment,
  type FileUploadResponse,
  type OperationLogDTO,
} from '@/services/api'
import { formatDateTime } from '@/utils/format'
import { AppType, ApplicationStatus } from '@/utils/status'
import StatusTag from '@/components/StatusTag.vue'
import AttachmentList from '@/components/application/AttachmentList.vue'
import ApplicationTimeline from '@/components/application/ApplicationTimeline.vue'
import ApprovalActionPanel from '@/components/application/ApprovalActionPanel.vue'
import CommentComposer from '@/components/application/CommentComposer.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// MobileLayout 以 path 作为 key，换一个 id 会重新挂载本页面，这里读一次即可
const applicationId = Number(route.params.id)

const application = ref<Application | null>(null)
const loading = ref(true)
const loadError = ref('')
const operationLogs = ref<OperationLogDTO[]>([])
const comments = ref<ApplicationComment[]>([])
const applicationAttachments = ref<FileUploadResponse[]>([])
const approvalAttachments = ref<FileUploadResponse[]>([])
const voiceTranscript = ref('')
const transcribing = ref(false)
const sendingVoiceNotification = ref(false)
const replyTo = ref<ApplicationComment | null>(null)

const isImage = (type?: string) => !!type && type.startsWith('image/')
const isAudio = (type?: string) => !!type && type.startsWith('audio/')
const isFile = (type?: string) => !isImage(type) && !isAudio(type)

const applicationAudio = computed(() => applicationAttachments.value.filter(a => isAudio(a.fileType)))
const applicationImages = computed(() => applicationAttachments.value.filter(a => isImage(a.fileType)))
const applicationFiles = computed(() => applicationAttachments.value.filter(a => isFile(a.fileType)))
const approvalAudio = computed(() => approvalAttachments.value.filter(a => isAudio(a.fileType)))
const approvalImages = computed(() => approvalAttachments.value.filter(a => isImage(a.fileType)))
const approvalFiles = computed(() => approvalAttachments.value.filter(a => isFile(a.fileType)))

const isVoiceApplication = computed(() =>
  application.value?.appType === AppType.VOICE || applicationAudio.value.length > 0 || !!voiceTranscript.value
)

const isPending = computed(() => application.value?.status === ApplicationStatus.PENDING)
const canApprove = computed(() => isPending.value && application.value?.approverId === userStore.userId)
const canSendVoiceNotification = computed(() => isPending.value && application.value?.applicantId === userStore.userId)

function goBack(): void {
  if (window.history.state?.back) {
    router.back()
  } else {
    router.replace('/mobile/applications')
  }
}

async function fetchAll(): Promise<void> {
  if (!Number.isFinite(applicationId)) {
    loading.value = false
    loadError.value = '申请编号不正确'
    return
  }

  loading.value = !application.value
  loadError.value = ''
  try {
    const detail = await applicationAPI.getApplicationDetail(applicationId)
    application.value = detail
    voiceTranscript.value = detail.voiceTranscript || ''
  } catch (error: any) {
    loadError.value = error.message || '加载失败'
    loading.value = false
    return
  }

  // 详情能打开时，其余几块各自失败只影响各自区域
  const [logs, commentList, appAtts, approvalAtts] = await Promise.allSettled([
    logAPI.getApplicationTimeline(applicationId),
    commentAPI.getApplicationComments(applicationId),
    attachmentAPI.getApplicationAttachments(applicationId),
    attachmentAPI.getApprovalAttachments(applicationId),
  ])
  if (logs.status === 'fulfilled') operationLogs.value = logs.value || []
  if (commentList.status === 'fulfilled') comments.value = commentList.value || []
  if (appAtts.status === 'fulfilled') applicationAttachments.value = appAtts.value || []
  if (approvalAtts.status === 'fulfilled') approvalAttachments.value = approvalAtts.value || []
  loading.value = false
}

async function refreshComments(): Promise<void> {
  try {
    comments.value = await commentAPI.getApplicationComments(applicationId) || []
  } catch (error: any) {
    showFailToast(error.message || '评论加载失败')
  }
}

function onCommentPosted(): void {
  replyTo.value = null
  refreshComments()
}

async function handleDeleteComment(comment: ApplicationComment): Promise<void> {
  try {
    await showConfirmDialog({ title: '删除评论', message: '确定删除这条评论吗？' })
  } catch {
    return
  }
  try {
    await commentAPI.deleteComment(comment.id)
    showSuccessToast('已删除')
    refreshComments()
  } catch (error: any) {
    showFailToast(error.message || '删除失败')
  }
}

async function handleSendVoiceNotification(): Promise<void> {
  sendingVoiceNotification.value = true
  try {
    await applicationAPI.sendVoiceNotification(applicationId)
    showSuccessToast('已拨打提醒电话')
  } catch (error: any) {
    showFailToast(error.message || '发送失败')
  } finally {
    sendingVoiceNotification.value = false
  }
}

async function handleTranscribe(): Promise<void> {
  transcribing.value = true
  try {
    voiceTranscript.value = (await applicationAPI.transcribeVoiceApplication(applicationId, 'zh')) || ''
  } catch (error: any) {
    showFailToast(error.message || '转写失败')
  } finally {
    transcribing.value = false
  }
}

onMounted(fetchAll)
</script>

<style scoped>
.detail-page {
  min-height: 100%;
  background: var(--bg);
}

.page-state {
  padding-top: 80px;
  text-align: center;
}

.m-page > * + * {
  margin-top: 10px;
}

.summary-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.summary-title {
  color: var(--ink);
  font-size: 19px;
  font-weight: 600;
  line-height: 1.4;
}

.summary-head :deep(.status-tag) {
  margin-top: 3px;
}

.summary-meta {
  margin-top: 6px;
}

.reject-note {
  margin-top: 12px;
}

.fields {
  margin: 0;
}

.field {
  margin-top: 14px;
}

.field dt {
  color: var(--ink-3);
  font-size: 12px;
}

.field dd {
  margin: 2px 0 0;
  color: var(--ink);
  font-size: 15px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.audio-row {
  margin-top: 10px;
}

.audio-row audio {
  display: block;
  width: 100%;
  height: 40px;
}

.muted {
  margin-top: 4px;
  color: var(--ink-3);
  font-size: 13px;
}

.transcript {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: var(--radius);
  background: var(--surface-2);
}

.transcript-label {
  color: var(--ink-3);
  font-size: 12px;
}

.transcript p {
  margin-top: 2px;
  color: var(--ink);
  font-size: 14px;
  line-height: 1.6;
}

.transcribe-btn {
  margin-top: 12px;
}

.remind {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.timeline-title {
  margin-bottom: 14px;
}
</style>
