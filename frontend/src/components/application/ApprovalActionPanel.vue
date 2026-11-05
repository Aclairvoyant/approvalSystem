<template>
  <section class="m-card m-card--pad approval-panel">
    <h2 class="m-section-title">审批</h2>

    <van-field
      v-model="comment"
      class="comment-field"
      type="textarea"
      placeholder="审批意见或驳回原因（选填）"
      rows="3"
      autosize
      maxlength="500"
    />

    <div class="extras">
      <van-uploader
        v-model="files"
        :max-count="5"
        :max-size="MAX_FILE_SIZE"
        accept="image/*,.pdf,.doc,.docx"
        @oversize="onOversize"
      >
        <van-button size="small" plain icon="photo-o" native-type="button">附件</van-button>
      </van-uploader>

      <van-button
        size="small"
        plain
        icon="volume-o"
        native-type="button"
        :disabled="!recordingSupport.supported"
        @click="startRecording"
      >
        {{ voiceReply.blob.value ? '重录语音' : '语音回复' }}
      </van-button>

      <van-uploader
        v-model="voiceFiles"
        :max-count="1"
        :max-size="MAX_FILE_SIZE"
        accept="audio/*,.wav,.mp3,.m4a,.aac,.webm,.ogg"
        :after-read="handleVoiceFileRead"
        :preview-image="false"
      >
        <van-button size="small" plain icon="music-o" native-type="button" :loading="voiceConverting">上传音频</van-button>
      </van-uploader>
    </div>

    <div v-if="voiceReply.url.value" class="voice-reply">
      <audio :src="voiceReply.url.value" controls preload="none"></audio>
      <button type="button" class="remove-voice" @click="clearVoiceReply">删除</button>
    </div>

    <p v-if="!recordingSupport.supported" class="hint">{{ recordingSupport.message }}，可以上传音频代替录音。</p>

    <div class="m-actions decision">
      <van-button plain type="danger" :loading="submitting === 'reject'" :disabled="!!submitting" @click="decide('reject')">
        驳回
      </van-button>
      <van-button type="primary" :loading="submitting === 'approve'" :disabled="!!submitting" @click="decide('approve')">
        通过
      </van-button>
    </div>
  </section>

  <VoiceRecorderPopup
    :show="showRecorder"
    title="语音回复"
    hint="说出你的审批意见，最长 60 秒"
    :state="recorder.recording.value ? 'recording' : recorder.starting.value ? 'starting' : 'idle'"
    :seconds="recorder.seconds.value"
    :max-seconds="MAX_RECORD_SECONDS"
    @cancel="cancelRecording"
    @finish="finishRecording"
  />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { applicationAPI, attachmentAPI } from '@/services/api'
import { useVoiceRecorder } from '@/composables/useVoiceRecorder'
import { useBlobUrl } from '@/composables/useBlobUrl'
import { getRecordingSupportStatus, normalizeAudioBlobToWav } from '@/utils/wavRecorder'
import VoiceRecorderPopup from './VoiceRecorderPopup.vue'

const MAX_FILE_SIZE = 10 * 1024 * 1024
const MAX_RECORD_SECONDS = 60

const props = defineProps<{ applicationId: number }>()
const emit = defineEmits<{ done: [] }>()

const comment = ref('')
const files = ref<any[]>([])
const voiceFiles = ref<any[]>([])
const voiceConverting = ref(false)
const submitting = ref<'approve' | 'reject' | null>(null)
const showRecorder = ref(false)

const recordingSupport = getRecordingSupportStatus()
const voiceReply = useBlobUrl()
const recorder = useVoiceRecorder({
  maxSeconds: MAX_RECORD_SECONDS,
  onLimit: () => void finishRecording(),
})

function onOversize(): void {
  showFailToast('文件不能超过 10MB')
}

async function startRecording(): Promise<void> {
  showRecorder.value = true
  try {
    await recorder.start()
  } catch (error: any) {
    showRecorder.value = false
    showFailToast(error?.message || '无法访问麦克风，请检查权限')
  }
}

function cancelRecording(): void {
  recorder.cancel()
  showRecorder.value = false
}

async function finishRecording(): Promise<void> {
  try {
    const audio = await recorder.stop()
    if (!audio) return
    if (audio.size <= 44) {
      showFailToast('录音太短，请重新录制')
      return
    }
    voiceReply.set(audio)
    voiceFiles.value = []
  } catch (error: any) {
    showFailToast(error?.message || '录音处理失败')
  } finally {
    showRecorder.value = false
  }
}

function clearVoiceReply(): void {
  voiceReply.clear()
  voiceFiles.value = []
}

async function handleVoiceFileRead(fileItem: any): Promise<void> {
  const item = Array.isArray(fileItem) ? fileItem[0] : fileItem
  const file = item?.file as File | undefined
  if (!file) return
  if (!file.type.startsWith('audio/') && !/\.(wav|mp3|m4a|aac|mp4|webm|ogg)$/i.test(file.name)) {
    voiceFiles.value = []
    showFailToast('请选择音频文件')
    return
  }

  voiceConverting.value = true
  item.status = 'uploading'
  item.message = '处理中'
  try {
    voiceReply.set(await normalizeAudioBlobToWav(file))
    item.status = 'done'
    item.message = ''
  } catch (error: any) {
    item.status = 'failed'
    item.message = '处理失败'
    showFailToast(error?.message || '音频处理失败')
  } finally {
    voiceConverting.value = false
  }
}

async function decide(type: 'approve' | 'reject'): Promise<void> {
  try {
    await showConfirmDialog({
      title: type === 'approve' ? '通过这条申请？' : '驳回这条申请？',
      message: comment.value.trim() || (type === 'approve' ? '没有填写审批意见' : '没有填写驳回原因'),
      confirmButtonText: type === 'approve' ? '通过' : '驳回',
      confirmButtonColor: type === 'approve' ? 'var(--accent)' : 'var(--danger)',
    })
  } catch {
    return
  }

  submitting.value = type
  const payload = { approvalDetail: comment.value.trim() || undefined }
  const audio = voiceReply.blob.value || undefined
  try {
    if (type === 'approve') {
      await applicationAPI.approveApplication(props.applicationId, payload, audio)
    } else {
      await applicationAPI.rejectApplication(props.applicationId, payload, audio)
    }
  } catch (error: any) {
    showFailToast(error.message || '操作失败')
    submitting.value = null
    return
  }

  // 审批成功之后再传附件：审批失败时不会留下孤立的附件，重试也不会重复上传
  let failed = 0
  for (const item of files.value) {
    if (!item.file) continue
    try {
      await attachmentAPI.uploadApprovalAttachment(props.applicationId, item.file)
    } catch {
      failed += 1
    }
  }

  submitting.value = null
  comment.value = ''
  files.value = []
  clearVoiceReply()
  if (failed > 0) {
    showFailToast(`${type === 'approve' ? '已通过' : '已驳回'}，但有 ${failed} 个附件上传失败`)
  } else {
    showSuccessToast(type === 'approve' ? '已通过' : '已驳回')
  }
  emit('done')
}
</script>

<style scoped>
.comment-field {
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
}

.extras {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 8px;
  margin-top: 12px;
}

.extras :deep(.van-uploader__wrapper) {
  gap: 8px;
}

.extras :deep(.van-uploader__preview) {
  margin: 0;
}

.voice-reply {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.voice-reply audio {
  flex: 1;
  min-width: 0;
  height: 36px;
}

.remove-voice {
  padding: 0;
  border: 0;
  background: none;
  color: var(--danger);
  font-size: 13px;
}

.hint {
  margin-top: 8px;
  color: var(--ink-3);
  font-size: 12px;
}

.decision {
  margin-top: 16px;
}
</style>
