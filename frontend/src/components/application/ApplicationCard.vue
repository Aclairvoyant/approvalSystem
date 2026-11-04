<template>
  <article class="app-card" @click="emit('open', application.id)">
    <div class="app-card-head">
      <h3 class="m-title app-card-title">
        <van-icon v-if="application.appType === AppType.VOICE" name="volume-o" class="voice-icon" />
        {{ application.title || '语音申请' }}
      </h3>
      <StatusTag :status="application.status" />
    </div>
    <p v-if="application.description" class="app-card-desc">{{ application.description }}</p>
    <p v-if="voiceNote" class="app-card-voice" :class="{ 'is-failed': application.voiceStatus === VoiceStatus.FAILED }">
      {{ voiceNote }}
    </p>
    <div class="m-meta app-card-meta">
      <span>{{ counterpartLabel }}</span>
      <span>{{ relativeTime(application.createdAt) }}</span>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Application } from '@/services/api'
import StatusTag from '@/components/StatusTag.vue'
import { relativeTime } from '@/utils/format'
import { AppType, ApplicationStatus, VoiceStatus, voiceStatusText } from '@/utils/status'

const props = defineProps<{
  application: Application
  /** 是否正在本次会话中后台上传音频 */
  uploading?: boolean
  /** 卡片上显示对方是谁：我发起的显示审批人，我审批的显示申请人 */
  perspective?: 'applicant' | 'approver'
}>()

const emit = defineEmits<{ open: [id: number] }>()

const counterpartLabel = computed(() => props.perspective === 'approver'
  ? `申请人 ${props.application.applicantName || '对方'}`
  : `审批人 ${props.application.approverName || '对方'}`)

const voiceNote = computed(() => {
  const app = props.application
  if (app.appType !== AppType.VOICE || app.status !== ApplicationStatus.DRAFT || !app.voiceStatus) return ''
  if (props.uploading) return '音频上传中…'
  if (app.voiceStatus === VoiceStatus.UPLOADING) return '音频还没传完，左滑可以重新上传'
  if (app.voiceStatus === VoiceStatus.FAILED) return '音频上传失败，左滑可以重新上传'
  return voiceStatusText(app.voiceStatus)
})
</script>

<style scoped>
.app-card {
  padding: 14px 16px;
}

.app-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.app-card-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.voice-icon {
  margin-right: 2px;
  color: var(--ink-2);
  vertical-align: -2px;
}

.app-card-desc {
  display: -webkit-box;
  margin-top: 4px;
  overflow: hidden;
  color: var(--ink-2);
  font-size: 14px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.app-card-voice {
  margin-top: 4px;
  color: var(--ink-2);
  font-size: 13px;
}

.app-card-voice.is-failed {
  color: var(--danger);
}

.app-card-meta {
  margin-top: 10px;
}
</style>
