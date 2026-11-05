<template>
  <van-action-sheet v-model:show="visible" title="发起申请" :closeable="true" class="create-sheet">
    <van-form class="m-sheet" @submit="submit">
      <div class="mode-switch">
        <van-radio-group v-model="mode" direction="horizontal">
          <van-radio name="normal">文字申请</van-radio>
          <van-radio name="voice">语音申请</van-radio>
        </van-radio-group>
      </div>

      <van-cell-group inset>
        <van-field
          :model-value="approverName"
          is-link
          readonly
          label="审批人"
          placeholder="选择你的对象"
          required
          @click="openApproverPicker"
        />

        <template v-if="mode === 'normal'">
          <van-cell title="套用模板" :value="templateName || '选择常用模板'" is-link @click="openTemplatePicker" />
          <van-cell title="语音填写" value="说一句话自动填写" is-link @click="startVoice('fill')" />
          <van-cell v-if="voiceTranscript" class="transcript">
            <template #title>
              <div class="transcript-label">识别原文</div>
              <div class="transcript-text">{{ voiceTranscript }}</div>
            </template>
          </van-cell>
          <van-field
            v-model="form.title"
            label="标题"
            placeholder="要申请什么"
            maxlength="100"
            required
            :rules="[{ required: true, message: '请输入标题' }]"
          />
          <van-field
            v-model="form.description"
            label="说明"
            type="textarea"
            placeholder="时间、地点、原因"
            rows="3"
            autosize
            maxlength="1000"
            required
            :rules="[{ required: true, message: '请输入说明' }]"
          />
          <van-field v-model="form.remark" label="备注" placeholder="选填" maxlength="200" />
          <van-field label="附件">
            <template #input>
              <van-uploader
                v-model="attachments"
                :max-count="5"
                :max-size="MAX_FILE_SIZE"
                accept="image/*,.pdf,.doc,.docx"
                @oversize="onOversize"
              />
            </template>
          </van-field>
          <van-field label="语音通知" :disabled="!userStore.voiceNotificationEnabled">
            <template #input>
              <van-switch
                v-model="form.sendVoiceNotification"
                size="20"
                :disabled="!userStore.voiceNotificationEnabled"
              />
            </template>
          </van-field>
        </template>

        <template v-else>
          <van-cell
            title="录一段语音"
            :value="voiceRequest.blob.value ? '已录好' : (recordingSupport.supported ? '开始录音' : '当前不可录音')"
            is-link
            @click="startVoice('request')"
          />
          <van-field label="或上传音频">
            <template #input>
              <van-uploader
                v-model="voiceUploadFiles"
                :max-count="1"
                :max-size="MAX_FILE_SIZE"
                accept="audio/*,.wav,.mp3,.m4a,.aac,.webm,.ogg"
                :after-read="handleVoiceFileRead"
                @oversize="onOversize"
              >
                <van-button size="small" plain type="primary" icon="plus" native-type="button" :loading="voiceConverting">
                  选择音频
                </van-button>
              </van-uploader>
            </template>
          </van-field>
          <van-cell v-if="voiceRequest.url.value">
            <template #title>
              <audio class="voice-preview" :src="voiceRequest.url.value" controls preload="none"></audio>
              <div class="voice-preview-actions">
                <van-button size="small" plain native-type="button" @click="startVoice('request')">重录</van-button>
                <van-button size="small" plain native-type="button" @click="clearVoiceRequest()">删除</van-button>
              </div>
            </template>
          </van-cell>
        </template>
      </van-cell-group>

      <p v-if="mode === 'normal' && !userStore.voiceNotificationEnabled" class="m-note sheet-note">
        <b>语音通知未开通。</b>开通后提交申请时可以打电话提醒对方，需要请管理员开启。
      </p>
      <p v-if="mode === 'voice' && !recordingSupport.supported" class="m-note sheet-note">
        <b>当前不能直接录音：</b>{{ recordingSupport.message }}。可以先用手机录音，再上传音频文件。
      </p>

      <div class="m-sheet-footer">
        <van-button block type="primary" native-type="submit" :loading="submitting" :disabled="voiceConverting">
          {{ mode === 'voice' ? '提交语音申请' : '提交申请' }}
        </van-button>
      </div>
    </van-form>
  </van-action-sheet>

  <van-popup v-model:show="showApproverPicker" position="bottom" round>
    <van-picker
      title="选择审批人"
      :columns="approverColumns"
      @confirm="onApproverConfirm"
      @cancel="showApproverPicker = false"
    />
  </van-popup>

  <van-popup v-model:show="showTemplatePicker" position="bottom" round>
    <div class="template-picker">
      <div class="template-picker-head">
        <div>
          <div class="m-section-title">选择模板</div>
          <p class="template-picker-hint">套用后会填入标题、说明和备注</p>
        </div>
        <van-button size="small" plain type="primary" @click="goTemplateManage">管理模板</van-button>
      </div>

      <van-loading v-if="templatesLoading" class="template-loading" size="24px">加载中...</van-loading>

      <van-empty v-else-if="templates.length === 0" description="还没有申请模板">
        <van-button type="primary" size="small" @click="goTemplateManage">去创建</van-button>
      </van-empty>

      <van-cell-group v-else inset>
        <van-cell
          v-for="template in templates"
          :key="template.id"
          is-link
          :title="template.title"
          :label="template.description"
          @click="applyTemplate(template)"
        >
          <template #value>
            <span class="template-usage tabular">用过 {{ template.usageCount || 0 }} 次</span>
          </template>
        </van-cell>
      </van-cell-group>
    </div>
  </van-popup>

  <VoiceRecorderPopup
    :show="showVoicePopup"
    :title="voiceMode === 'fill' ? '语音填写' : '语音申请'"
    :hint="voiceMode === 'fill' ? '说出你要申请的事情和理由' : '说出要交给对方审批的内容'"
    :state="recorderState"
    :seconds="recorder.seconds.value"
    :max-seconds="MAX_RECORD_SECONDS"
    @cancel="cancelVoice"
    @finish="finishVoice"
  />
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast, showToast } from 'vant'
import {
  applicationAPI,
  applicationTemplateAPI,
  attachmentAPI,
  type ApplicationTemplate,
  type UserRelation,
} from '@/services/api'
import { useUserStore } from '@/store/modules/user'
import { useVoiceRecorder } from '@/composables/useVoiceRecorder'
import { useBlobUrl } from '@/composables/useBlobUrl'
import { getRecordingSupportStatus, normalizeAudioBlobToWav } from '@/utils/wavRecorder'
import VoiceRecorderPopup, { type RecorderState } from './VoiceRecorderPopup.vue'

export interface CreatePrefill {
  title?: string
  description?: string
  remark?: string
  approverId?: number
  approverName?: string
}

export interface CreatedPayload {
  applicationId: number
  /** 语音申请：草稿已创建，音频需要由页面在后台上传 */
  voiceAudio?: Blob
}

const MAX_FILE_SIZE = 10 * 1024 * 1024
const MAX_RECORD_SECONDS = 60

const props = defineProps<{ relations: UserRelation[] }>()
const emit = defineEmits<{ created: [payload: CreatedPayload] }>()

const router = useRouter()
const userStore = useUserStore()

const visible = ref(false)
const mode = ref<'normal' | 'voice'>('normal')
const submitting = ref(false)
const form = reactive({
  approverId: 0,
  title: '',
  description: '',
  remark: '',
  sendVoiceNotification: false,
})
const approverName = ref('')
const templateName = ref('')
const attachments = ref<any[]>([])

const showApproverPicker = ref(false)
const showTemplatePicker = ref(false)
const templatesLoading = ref(false)
const templates = ref<ApplicationTemplate[]>([])

// 语音
const recordingSupport = getRecordingSupportStatus()
const showVoicePopup = ref(false)
const voiceMode = ref<'fill' | 'request'>('fill')
const voiceParsing = ref(false)
const voiceTranscript = ref('')
const voiceUploadFiles = ref<any[]>([])
const voiceConverting = ref(false)
const voiceRequest = useBlobUrl()
const recorder = useVoiceRecorder({
  maxSeconds: MAX_RECORD_SECONDS,
  onLimit: () => void finishVoice(),
})

const recorderState = computed<RecorderState>(() => {
  if (voiceParsing.value) return 'parsing'
  if (recorder.starting.value) return 'starting'
  if (recorder.recording.value) return 'recording'
  return 'idle'
})

const approverColumns = computed(() =>
  props.relations.map(r => ({
    text: r.otherUserName || r.otherUserUsername || `用户${r.otherUserId}`,
    value: r.otherUserId,
  }))
)

function reset(): void {
  mode.value = 'normal'
  Object.assign(form, { approverId: 0, title: '', description: '', remark: '', sendVoiceNotification: false })
  approverName.value = ''
  templateName.value = ''
  attachments.value = []
  voiceTranscript.value = ''
  clearVoiceRequest()
}

/** 打开创建面板；每个用户只能有一个对象，所以只有一个可选审批人时直接选上 */
function open(prefill: CreatePrefill = {}): void {
  reset()
  form.title = prefill.title || ''
  form.description = prefill.description || ''
  form.remark = prefill.remark || ''
  if (prefill.title) templateName.value = '从模板带入'

  if (prefill.approverId) {
    form.approverId = prefill.approverId
    approverName.value = prefill.approverName || ''
  } else if (props.relations.length === 1) {
    form.approverId = props.relations[0].otherUserId
    approverName.value = approverColumns.value[0].text
  }
  visible.value = true
}

defineExpose({ open })

function openApproverPicker(): void {
  if (props.relations.length === 0) {
    showFailToast('还没有对象，请先在"我的对象"里添加')
    return
  }
  showApproverPicker.value = true
}

function onApproverConfirm({ selectedOptions }: any): void {
  const option = selectedOptions?.[0]
  if (option) {
    form.approverId = option.value
    approverName.value = option.text
  }
  showApproverPicker.value = false
}

async function openTemplatePicker(): Promise<void> {
  showTemplatePicker.value = true
  templatesLoading.value = true
  try {
    templates.value = await applicationTemplateAPI.list()
  } catch (error: any) {
    showFailToast(error.message || '模板加载失败')
  } finally {
    templatesLoading.value = false
  }
}

async function applyTemplate(template: ApplicationTemplate): Promise<void> {
  try {
    const used = await applicationTemplateAPI.use(template.id)
    form.title = used.title || ''
    form.description = used.description || ''
    form.remark = used.remark || ''
    templateName.value = used.title || template.title
    showTemplatePicker.value = false
  } catch (error: any) {
    showFailToast(error.message || '模板套用失败')
  }
}

function goTemplateManage(): void {
  showTemplatePicker.value = false
  visible.value = false
  router.push('/mobile/templates')
}

function onOversize(): void {
  showFailToast('文件不能超过 10MB')
}

// ---------- 语音 ----------

function clearVoiceRequest(clearUpload = true): void {
  voiceRequest.clear()
  if (clearUpload) voiceUploadFiles.value = []
}

async function startVoice(nextMode: 'fill' | 'request'): Promise<void> {
  if (!recordingSupport.supported) {
    showToast(nextMode === 'request'
      ? `${recordingSupport.message}，请上传音频文件`
      : `${recordingSupport.message}，请手动填写`)
    return
  }
  voiceMode.value = nextMode
  voiceParsing.value = false
  showVoicePopup.value = true
  try {
    await recorder.start()
  } catch (error: any) {
    showVoicePopup.value = false
    showFailToast(error?.message || '无法访问麦克风，请检查权限')
  }
}

function cancelVoice(): void {
  if (voiceParsing.value) return
  recorder.cancel()
  showVoicePopup.value = false
}

async function finishVoice(): Promise<void> {
  let audio: Blob | null
  try {
    audio = await recorder.stop()
  } catch (error: any) {
    showVoicePopup.value = false
    showFailToast(error?.message || '录音处理失败')
    return
  }
  if (!audio) return

  if (audio.size <= 44) {
    showVoicePopup.value = false
    showFailToast('没有录到声音，请重试')
    return
  }

  if (voiceMode.value === 'request') {
    voiceRequest.set(audio)
    voiceUploadFiles.value = []
    showVoicePopup.value = false
    return
  }

  voiceParsing.value = true
  try {
    const result = await applicationAPI.parseVoice(audio, 'zh')
    voiceTranscript.value = result.transcript || ''
    if (result.title) form.title = result.title
    if (result.description) form.description = result.description
    if (result.remark) form.remark = result.remark
    templateName.value = ''
    showSuccessToast('已填入，请核对')
  } catch (error: any) {
    showFailToast(error?.message || '识别失败，请重试或手动填写')
  } finally {
    voiceParsing.value = false
    showVoicePopup.value = false
  }
}

function isAudioFile(file: File): boolean {
  return file.type.startsWith('audio/') || /\.(wav|mp3|m4a|aac|mp4|webm|ogg)$/i.test(file.name)
}

async function handleVoiceFileRead(fileItem: any): Promise<void> {
  const item = Array.isArray(fileItem) ? fileItem[0] : fileItem
  const file = item?.file as File | undefined
  if (!file) return

  if (!isAudioFile(file)) {
    voiceUploadFiles.value = []
    showFailToast('请选择音频文件')
    return
  }

  voiceConverting.value = true
  item.status = 'uploading'
  item.message = '处理中'
  try {
    voiceRequest.set(await normalizeAudioBlobToWav(file))
    item.status = 'done'
    item.message = ''
  } catch (error: any) {
    item.status = 'failed'
    item.message = '处理失败'
    showFailToast(error?.message || '音频处理失败，请换一个文件')
  } finally {
    voiceConverting.value = false
  }
}

// ---------- 提交 ----------

async function submit(): Promise<void> {
  if (!form.approverId) {
    showFailToast('请选择审批人')
    return
  }
  if (mode.value === 'voice' && !voiceRequest.blob.value) {
    showFailToast('请先录音或上传音频')
    return
  }

  submitting.value = true
  try {
    if (mode.value === 'voice') {
      const audio = voiceRequest.blob.value!
      const draft = await applicationAPI.createVoiceApplicationDraft(form.approverId)
      showSuccessToast('已提交，语音正在后台上传')
      visible.value = false
      emit('created', { applicationId: draft.id, voiceAudio: audio })
      reset()
      return
    }

    const created = await applicationAPI.createApplication({
      approverId: form.approverId,
      title: form.title.trim(),
      description: form.description.trim(),
      remark: form.remark.trim() || undefined,
      sendVoiceNotification: form.sendVoiceNotification,
    })

    let failed = 0
    for (const item of attachments.value) {
      if (!item.file) continue
      try {
        await attachmentAPI.uploadApplicationAttachment(created.id, item.file)
      } catch {
        failed += 1
      }
    }

    if (failed > 0) {
      showFailToast(`申请已提交，但有 ${failed} 个附件上传失败`)
    } else {
      showSuccessToast('申请已提交')
    }
    visible.value = false
    emit('created', { applicationId: created.id })
    reset()
  } catch (error: any) {
    showFailToast(error.message || '提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.create-sheet :deep(.van-action-sheet__content) {
  max-height: 78vh;
}

.mode-switch {
  padding: 4px 16px 12px;
}

.transcript {
  background: var(--surface-2);
}

.transcript-label {
  color: var(--ink-3);
  font-size: 12px;
}

.transcript-text {
  margin-top: 2px;
  color: var(--ink-2);
  font-size: 13px;
  line-height: 1.5;
}

.voice-preview {
  display: block;
  width: 100%;
}

.voice-preview-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.sheet-note {
  margin: 12px 16px 0;
}

.template-picker {
  max-height: 72vh;
  padding: 16px 0 calc(20px + env(safe-area-inset-bottom));
  overflow-y: auto;
}

.template-picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 16px 12px;
}

.template-picker-hint {
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 12px;
}

.template-loading {
  display: flex;
  justify-content: center;
  padding: 28px 0;
}

.template-usage {
  color: var(--ink-3);
  font-size: 12px;
}
</style>
