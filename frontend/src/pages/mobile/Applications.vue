<template>
  <div class="applications-page">
    <!-- 顶部导航 -->
    <van-nav-bar
      title="我的申请"
      left-arrow
      @click-left="$router.back()"
    >
      <template #right>
        <van-icon name="add-o" size="20" @click="openCreateSheet" />
      </template>
    </van-nav-bar>

    <!-- 状态筛选 -->
    <div class="filter-tabs">
      <van-tabs v-model:active="activeTab" @change="handleTabChange">
        <van-tab title="全部" :name="0" />
        <van-tab title="待审批" :name="1" :badge="pendingCount > 0 ? pendingCount : ''" />
        <van-tab title="已通过" :name="2" />
        <van-tab title="已驳回" :name="3" />
        <van-tab title="已取消" :name="5" />
      </van-tabs>
    </div>

    <!-- 申请列表 -->
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="onLoad"
      >
        <div v-if="visibleApplications.length === 0 && !loading" class="empty-state">
          <van-empty description="暂无申请记录">
            <van-button type="primary" round size="small" @click="openCreateSheet">
              创建申请
            </van-button>
          </van-empty>
        </div>

        <div v-else class="application-list">
          <van-swipe-cell v-for="app in visibleApplications" :key="app.id">
            <div class="application-card" @click="goToDetail(app.id)">
              <div class="card-header">
                <div class="card-title">{{ app.title }}</div>
                <van-tag :type="getStatusType(app.status)" size="medium">
                  {{ getStatusText(app.status) }}
                </van-tag>
                <van-tag
                  v-if="shouldShowVoiceUploadStatus(app)"
                  :type="getVoiceUploadStatusType(app.voiceStatus)"
                  size="medium"
                  plain
                >
                  {{ getVoiceUploadStatusText(app.voiceStatus) }}
                </van-tag>
              </div>
              <div class="card-content">{{ app.description }}</div>
              <div class="card-footer">
                <div class="footer-item">
                  <van-icon name="clock-o" />
                  <span>{{ formatDate(app.createdAt) }}</span>
                </div>
                <div class="footer-item">
                  <van-icon name="user-o" />
                  <span>审批人: {{ app.approverId }}</span>
                </div>
              </div>
            </div>
            <template #right>
              <van-button
                v-if="app.status === 1"
                square
                type="primary"
                text="修改"
                class="swipe-btn"
                @click.stop="openEditSheet(app)"
              />
              <van-button
                v-if="app.status === 1"
                square
                type="danger"
                text="取消"
                class="swipe-btn"
                @click.stop="handleCancel(app)"
              />
              <van-button
                v-if="app.appType === 2 && app.voiceStatus === 3"
                square
                type="primary"
                text="Retry"
                class="swipe-btn"
                @click.stop="retryVoiceUpload(app)"
              />
              <van-button
                square
                type="warning"
                text="删除"
                class="swipe-btn swipe-btn-delete"
                @click.stop="handleDelete(app)"
              />
            </template>
          </van-swipe-cell>
        </div>
      </van-list>
    </van-pull-refresh>

    <!-- 创建申请弹窗 -->
    <van-action-sheet
      v-model:show="showCreateSheet"
      title="创建申请"
      :closeable="true"
    >
      <div class="create-form">
        <van-form @submit="submitApplication">
          <van-cell-group inset>
            <van-field name="createMode" label="申请方式">
              <template #input>
                <van-radio-group v-model="createMode" direction="horizontal">
                  <van-radio name="normal">普通申请</van-radio>
                  <van-radio name="voice">语音申请</van-radio>
                </van-radio-group>
              </template>
            </van-field>

            <template v-if="createMode === 'normal'">
              <van-cell
                title="申请模板"
                :value="selectedTemplateName || '选择常用模板'"
                is-link
                clickable
                @click="openTemplatePicker"
              >
                <template #icon>
                  <van-icon name="records-o" class="template-cell-icon" />
                </template>
              </van-cell>
              <van-cell
                title="语音填写"
                value="说一句话自动填表"
                is-link
                clickable
                @click="startVoiceFill"
              >
                <template #icon>
                  <van-icon name="volume-o" class="voice-cell-icon" />
                </template>
              </van-cell>
              <van-cell v-if="voiceTranscript" class="voice-transcript">
                <template #title>
                  <div class="voice-transcript-label">
                    <van-icon name="chat-o" /> 识别原文
                  </div>
                  <div class="voice-transcript-text">{{ voiceTranscript }}</div>
                </template>
              </van-cell>
              <van-field
                v-model="newApplication.title"
                label="事项标题"
                placeholder="请输入事项标题"
                required
                :rules="[{ required: true, message: '请输入事项标题' }]"
              />
              <van-field
                v-model="newApplication.description"
                label="事项描述"
                type="textarea"
                placeholder="请输入事项描述"
                rows="3"
                required
                :rules="[{ required: true, message: '请输入事项描述' }]"
              />
            </template>

            <van-field
              v-model="selectedApproverName"
              is-link
              readonly
              label="选择审批人"
              placeholder="请选择审批人"
              required
              @click="showApproverPicker = true"
            />

            <template v-if="createMode === 'normal'">
              <van-field
                v-model="newApplication.remark"
                label="备注"
                placeholder="请输入备注（选填）"
              />
              <van-field name="uploader" label="附件">
                <template #input>
                  <van-uploader
                    v-model="uploadFileList"
                    :max-count="5"
                    :max-size="10 * 1024 * 1024"
                    accept="image/*,.pdf,.doc,.docx"
                    @oversize="onOversize"
                  />
                </template>
              </van-field>
              <van-field name="voiceNotification" label="语音通知">
                <template #input>
                  <van-switch
                    v-model="newApplication.sendVoiceNotification"
                    size="20"
                    :disabled="!userStore.voiceNotificationEnabled"
                  />
                </template>
                <template #right-icon v-if="!userStore.voiceNotificationEnabled">
                  <van-icon name="warning-o" color="#ff7d00" />
                </template>
              </van-field>
              <van-cell v-if="!userStore.voiceNotificationEnabled" class="voice-warning">
                <template #title>
                  <span style="color: #ff7d00; font-size: 12px;">
                    ⚠️ 语音通知权限未开通，请联系管理员开通后使用
                  </span>
                </template>
              </van-cell>
            </template>

            <template v-else>
              <van-cell v-if="!recordingAvailable" class="voice-recording-tip">
                <template #title>
                  <div class="voice-recording-tip-title">
                    <van-icon name="info-o" /> 录音暂不可用
                  </div>
                  <div class="voice-recording-tip-text">
                    {{ recordingSupport.message }}，可上传音频文件
                  </div>
                </template>
              </van-cell>
              <van-cell
                title="语音内容"
                :value="voiceRequestValueText"
                is-link
                clickable
                @click="startVoiceRequest"
              >
                <template #icon>
                  <van-icon name="volume-o" class="voice-cell-icon" />
                </template>
              </van-cell>
              <van-field name="voiceUploader" label="上传音频">
                <template #input>
                  <van-uploader
                    v-model="voiceUploadFileList"
                    :max-count="1"
                    :max-size="10 * 1024 * 1024"
                    accept="audio/*,.wav,.mp3,.m4a,.aac,.webm,.ogg"
                    :after-read="handleVoiceFileRead"
                    @oversize="onOversize"
                  >
                    <van-button
                      size="small"
                      round
                      plain
                      type="primary"
                      icon="plus"
                      :loading="voiceUploadConverting"
                    >
                      选择音频
                    </van-button>
                  </van-uploader>
                </template>
              </van-field>
              <van-cell v-if="voiceRequestAudioUrl" class="voice-request-preview">
                <template #title>
                  <audio
                    class="voice-request-audio"
                    :src="voiceRequestAudioUrl"
                    controls
                    preload="none"
                  ></audio>
                  <div class="voice-request-actions">
                    <van-button size="small" round plain type="primary" @click.stop="startVoiceRequest">
                      重录
                    </van-button>
                    <van-button size="small" round plain @click.stop="clearVoiceRequestRecording">
                      取消
                    </van-button>
                  </div>
                </template>
              </van-cell>
            </template>
          </van-cell-group>

          <div class="form-actions">
            <van-button
              round
              block
              type="primary"
              native-type="submit"
              :loading="submitting"
              :disabled="voiceUploadConverting"
            >
              {{ createMode === 'voice' ? '提交语音申请' : '提交申请' }}
            </van-button>
          </div>
        </van-form>
      </div>
    </van-action-sheet>

    <!-- 审批人选择器 -->
    <van-popup v-model:show="showApproverPicker" position="bottom" round>
      <van-picker
        title="选择审批人"
        :columns="approverColumns"
        @confirm="onApproverConfirm"
        @cancel="showApproverPicker = false"
      />
    </van-popup>

    <!-- 语音录制浮层 -->
    <van-popup
      v-model:show="showVoiceSheet"
      position="bottom"
      round
      :close-on-click-overlay="false"
    >
      <div class="voice-recorder">
        <div class="voice-recorder-title">
          {{ voiceRecorderTitle }}
        </div>
        <div class="voice-recorder-hint">
          {{ voiceRecorderHint }}
        </div>

        <div class="voice-mic" :class="{ active: recording, parsing: voiceParsing }">
          <van-loading v-if="voiceParsing" color="#fff" size="32px" />
          <van-icon v-else name="volume" size="40" />
        </div>

        <div v-if="recording" class="voice-timer">{{ formatDuration(recordSeconds) }}</div>

        <div class="voice-recorder-actions">
          <van-button round plain @click="cancelVoice" :disabled="voiceParsing">
            取消
          </van-button>
          <van-button
            v-if="recording"
            round
            type="primary"
            @click="finishRecording"
          >
            完成
          </van-button>
        </div>
      </div>
    </van-popup>

    <!-- 申请模板选择器 -->
    <van-popup v-model:show="showTemplatePicker" position="bottom" round>
      <div class="template-picker">
        <div class="template-picker-header">
          <div>
            <div class="template-picker-title">选择模板</div>
            <div class="template-picker-subtitle">套用后只填充标题、描述和备注</div>
          </div>
          <van-button size="small" type="primary" plain @click="goTemplateManage">管理模板</van-button>
        </div>

        <van-loading v-if="templatesLoading" class="template-loading" size="24px">
          加载中...
        </van-loading>

        <van-empty v-else-if="templates.length === 0" description="还没有申请模板">
          <van-button type="primary" size="small" round @click="goTemplateManage">去创建模板</van-button>
        </van-empty>

        <van-cell-group v-else inset>
          <van-cell
            v-for="template in templates"
            :key="template.id"
            clickable
            is-link
            :title="template.title"
            :label="template.description"
            @click="applyTemplate(template)"
          >
            <template #value>
              <span class="template-usage">用过 {{ template.usageCount || 0 }} 次</span>
            </template>
          </van-cell>
        </van-cell-group>
      </div>
    </van-popup>

    <!-- 修改申请弹窗 -->
    <van-action-sheet
      v-model:show="showEditSheet"
      title="修改申请"
      :closeable="true"
    >
      <div class="create-form">
        <van-form @submit="submitEdit">
          <van-cell-group inset>
            <van-field
              v-model="editApplication.title"
              label="事项标题"
              placeholder="请输入事项标题"
              required
              :rules="[{ required: true, message: '请输入事项标题' }]"
            />
            <van-field
              v-model="editApplication.description"
              label="事项描述"
              type="textarea"
              placeholder="请输入事项描述"
              rows="3"
              required
              :rules="[{ required: true, message: '请输入事项描述' }]"
            />
            <van-field
              v-model="editApplication.remark"
              label="备注"
              placeholder="请输入备注（选填）"
            />
          </van-cell-group>

          <div class="form-actions">
            <van-button round block type="primary" native-type="submit" :loading="editing">
              保存修改
            </van-button>
          </div>
        </van-form>
      </div>
    </van-action-sheet>

    <!-- 浮动按钮 -->
    <div class="fab-button" @click="openCreateSheet">
      <van-icon name="plus" size="24" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showSuccessToast, showLoadingToast, closeToast, showConfirmDialog } from 'vant'
import {
  applicationAPI,
  relationAPI,
  attachmentAPI,
  applicationTemplateAPI,
  type Application,
  type ApplicationTemplate,
} from '@/services/api'
import { useUserStore } from '@/store/modules/user'
import {
  startRecording,
  getRecordingSupportStatus,
  normalizeAudioBlobToWav,
  type RecorderHandle,
} from '@/utils/wavRecorder'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 列表数据
const applications = ref<Application[]>([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const activeTab = ref(0)
const pageNum = ref(1)
const pageSize = 10
const pendingCount = ref(0)

// 隐藏的申请IDs（使用响应式状态）
const hiddenApplicationIds = ref<Set<number>>(new Set())

const loadHiddenApplications = (): void => {
  const stored = localStorage.getItem('hiddenApplications')
  hiddenApplicationIds.value = stored ? new Set(JSON.parse(stored)) : new Set()
}

const hideApplication = (id: number): void => {
  hiddenApplicationIds.value.add(id)
  localStorage.setItem('hiddenApplications', JSON.stringify([...hiddenApplicationIds.value]))
}

// 过滤已隐藏的申请
const visibleApplications = computed(() => {
  return applications.value.filter(app => !hiddenApplicationIds.value.has(app.id))
})

// 创建申请
const showCreateSheet = ref(false)
const submitting = ref(false)
const showApproverPicker = ref(false)
const showTemplatePicker = ref(false)
const templatesLoading = ref(false)
const relations = ref<any[]>([])
const templates = ref<ApplicationTemplate[]>([])
const selectedTemplateName = ref('')
const selectedApproverName = ref('')
const uploadFileList = ref<any[]>([])
const createMode = ref<'normal' | 'voice'>('normal')

// 修改申请
const showEditSheet = ref(false)
const editing = ref(false)
const editingId = ref<number>(0)
const editApplication = ref({
  title: '',
  description: '',
  remark: ''
})

const newApplication = ref({
  approverId: 0,
  title: '',
  description: '',
  remark: '',
  sendVoiceNotification: false
})

// 语音填写
const showVoiceSheet = ref(false)
const recording = ref(false)
const voiceParsing = ref(false)
const voiceRecorderMode = ref<'fill' | 'request'>('fill')
const voiceTranscript = ref('')
const voiceRequestBlob = ref<Blob | null>(null)
const voiceRequestAudioUrl = ref('')
const voiceUploadFileList = ref<any[]>([])
const voiceUploadConverting = ref(false)
const voiceRetryBlobs = ref<Map<number, Blob>>(new Map())
const recordSeconds = ref(0)
let recorderHandle: RecorderHandle | null = null
let recordTimer: ReturnType<typeof setInterval> | null = null

const formatDuration = (sec: number): string => {
  const m = Math.floor(sec / 60).toString().padStart(2, '0')
  const s = (sec % 60).toString().padStart(2, '0')
  return `${m}:${s}`
}

const clearRecordTimer = (): void => {
  if (recordTimer) {
    clearInterval(recordTimer)
    recordTimer = null
  }
}

const voiceRecorderTitle = computed(() => {
  if (voiceRecorderMode.value === 'request') {
    return recording.value ? '正在录音' : '语音申请'
  }
  return voiceParsing.value ? '正在识别...' : (recording.value ? '正在聆听' : '语音填写')
})

const voiceRecorderHint = computed(() => {
  if (voiceRecorderMode.value === 'request') {
    return '说出要提交给审批人的语音内容'
  }
  return voiceParsing.value ? '请稍候，大模型整理中' : '说出你要申请的事情和理由'
})

const recordingSupport = computed(() => getRecordingSupportStatus())
const recordingAvailable = computed(() => recordingSupport.value.supported)
const voiceRequestValueText = computed(() => {
  if (voiceRequestBlob.value) return '已准备'
  return recordingAvailable.value ? '开始录音' : '录音不可用'
})

const clearVoiceRequestRecording = (clearUpload = true): void => {
  if (voiceRequestAudioUrl.value) {
    URL.revokeObjectURL(voiceRequestAudioUrl.value)
  }
  voiceRequestAudioUrl.value = ''
  voiceRequestBlob.value = null
  if (clearUpload) {
    voiceUploadFileList.value = []
  }
}

const setVoiceRequestRecording = (audioBlob: Blob, clearUpload = true): void => {
  clearVoiceRequestRecording(clearUpload)
  voiceRequestBlob.value = audioBlob
  voiceRequestAudioUrl.value = URL.createObjectURL(audioBlob)
}

const getRecordingUnavailableMessage = (mode: 'fill' | 'request'): string => {
  const message = getRecordingSupportStatus().message
  return mode === 'request' ? `${message}，请上传音频文件` : `${message}，请手动填写`
}

const startVoiceRecording = async (mode: 'fill' | 'request'): Promise<void> => {
  if (!getRecordingSupportStatus().supported) {
    showToast(getRecordingUnavailableMessage(mode))
    return
  }
  voiceRecorderMode.value = mode
  if (mode === 'fill') {
    voiceTranscript.value = ''
  }
  showVoiceSheet.value = true
  voiceParsing.value = false
  recordSeconds.value = 0
  try {
    recorderHandle = await startRecording()
    recording.value = true
    recordTimer = setInterval(() => {
      recordSeconds.value++
      // 上限 60 秒，自动停止
      if (recordSeconds.value >= 60) {
        finishRecording()
      }
    }, 1000)
  } catch (error: any) {
    recording.value = false
    showVoiceSheet.value = false
    showToast(error?.message || '无法访问麦克风，请检查权限')
  }
}

// 入口：开始语音填写
const startVoiceFill = async (): Promise<void> => {
  await startVoiceRecording('fill')
}

const startVoiceRequest = async (): Promise<void> => {
  await startVoiceRecording('request')
}

const isAudioFile = (file: File): boolean => {
  return file.type.startsWith('audio/') || /\.(wav|mp3|m4a|aac|mp4|webm|ogg)$/i.test(file.name)
}

const handleVoiceFileRead = async (fileItem: any): Promise<void> => {
  const item = Array.isArray(fileItem) ? fileItem[0] : fileItem
  const file = item?.file as File | undefined
  if (!file) return

  if (!isAudioFile(file)) {
    voiceUploadFileList.value = []
    showToast('请上传音频文件')
    return
  }

  voiceUploadConverting.value = true
  item.status = 'uploading'
  item.message = '处理中'
  try {
    const wavBlob = await normalizeAudioBlobToWav(file)
    setVoiceRequestRecording(wavBlob, false)
    item.status = 'done'
    item.message = '已处理'
    showSuccessToast('音频已准备好')
  } catch (error: any) {
    item.status = 'failed'
    item.message = '处理失败'
    showToast(error?.message || '音频处理失败，请换一个文件')
  } finally {
    voiceUploadConverting.value = false
  }
}

// 结束录音并解析
const finishRecording = async (): Promise<void> => {
  if (!recorderHandle || !recording.value) return
  clearRecordTimer()
  recording.value = false

  let audioBlob: Blob
  try {
    audioBlob = await recorderHandle.stop()
  } catch (error: any) {
    recorderHandle = null
    showVoiceSheet.value = false
    showToast(error?.message || '录音处理失败')
    return
  }
  recorderHandle = null

  if (!audioBlob || audioBlob.size <= 44) {
    showVoiceSheet.value = false
    showToast('没有录到声音，请重试')
    return
  }

  if (voiceRecorderMode.value === 'request') {
    setVoiceRequestRecording(audioBlob)
    showVoiceSheet.value = false
    showSuccessToast('已录音')
    return
  }

  voiceParsing.value = true
  try {
    const result = await applicationAPI.parseVoice(audioBlob, 'zh')
    voiceTranscript.value = result.transcript || ''
    if (result.title) newApplication.value.title = result.title
    if (result.description) newApplication.value.description = result.description
    if (result.remark) newApplication.value.remark = result.remark
    selectedTemplateName.value = '语音填写'
    showVoiceSheet.value = false
    showSuccessToast('已填入，请核对')
  } catch (error: any) {
    showToast(error?.message || '语音解析失败，请重试')
  } finally {
    voiceParsing.value = false
  }
}

// 取消语音
const cancelVoice = (): void => {
  clearRecordTimer()
  if (recorderHandle) {
    recorderHandle.cancel()
    recorderHandle = null
  }
  recording.value = false
  voiceParsing.value = false
  showVoiceSheet.value = false
}

const resetCreateForm = (): void => {
  showCreateSheet.value = false
  createMode.value = 'normal'
  newApplication.value = { approverId: 0, title: '', description: '', remark: '', sendVoiceNotification: false }
  selectedTemplateName.value = ''
  selectedApproverName.value = ''
  uploadFileList.value = []
  voiceUploadFileList.value = []
  voiceTranscript.value = ''
  clearVoiceRequestRecording()
}

// 审批人选择列 - 使用后端返回的 otherUserId 和 otherUserName
const approverColumns = computed(() => {
  return relations.value.map(r => ({
    text: r.otherUserName || r.otherUserUsername || `用户${r.otherUserId}`,
    value: r.otherUserId
  }))
})

const openCreateSheet = (): void => {
  showCreateSheet.value = true
}

const openTemplatePicker = async (): Promise<void> => {
  showTemplatePicker.value = true
  await loadTemplates()
}

const loadTemplates = async (): Promise<void> => {
  templatesLoading.value = true
  try {
    templates.value = await applicationTemplateAPI.list()
  } catch (error: any) {
    showToast(error.message || '模板加载失败')
  } finally {
    templatesLoading.value = false
  }
}

const applyTemplate = async (template: ApplicationTemplate): Promise<void> => {
  try {
    const used = await applicationTemplateAPI.use(template.id)
    newApplication.value.title = used.title || ''
    newApplication.value.description = used.description || ''
    newApplication.value.remark = used.remark || ''
    selectedTemplateName.value = used.title || template.title
    showTemplatePicker.value = false
    showSuccessToast('已套用模板')
  } catch (error: any) {
    showToast(error.message || '模板套用失败')
  }
}

const goTemplateManage = (): void => {
  showTemplatePicker.value = false
  showCreateSheet.value = false
  router.push('/mobile/templates')
}

// 获取状态类型
const getStatusType = (status: number): any => {
  const types: Record<number, string> = {
    1: 'warning',
    2: 'success',
    3: 'danger',
    4: 'default',
    5: 'default'
  }
  return types[status] || 'default'
}

// 获取状态文字
const getStatusText = (status: number): string => {
  const texts: Record<number, string> = {
    1: '待审批',
    2: '已通过',
    3: '已驳回',
    4: '草稿',
    5: '已取消'
  }
  return texts[status] || '未知'
}

// 格式化日期
const shouldShowVoiceUploadStatus = (app: Application): boolean => {
  return app.appType === 2 && app.status === 4 && !!app.voiceStatus
}

const getVoiceUploadStatusType = (voiceStatus?: number): any => {
  const types: Record<number, string> = {
    1: 'warning',
    2: 'success',
    3: 'danger'
  }
  return voiceStatus ? types[voiceStatus] : 'default'
}

const getVoiceUploadStatusText = (voiceStatus?: number): string => {
  const texts: Record<number, string> = {
    1: '上传中',
    2: '就绪',
    3: '失败'
  }
  return voiceStatus ? texts[voiceStatus] : ''
}

const formatDate = (date: string): string => {
  if (!date) return ''
  const d = new Date(date)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days === 0) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } else if (days === 1) {
    return '昨天'
  } else if (days < 7) {
    return `${days}天前`
  } else {
    return d.toLocaleDateString('zh-CN')
  }
}

// 加载更多
const onLoad = async (): Promise<void> => {
  try {
    const params: any = { pageNum: pageNum.value, pageSize }
    if (activeTab.value > 0) {
      params.status = activeTab.value
    }

    const response = await applicationAPI.getMyApplications(params)
    const records = response.records || []

    if (pageNum.value === 1) {
      applications.value = records
    } else {
      applications.value.push(...records)
    }

    // 计算待审批数量
    if (activeTab.value === 0) {
      pendingCount.value = records.filter((r: Application) => r.status === 1).length
    }

    if (records.length < pageSize) {
      finished.value = true
    } else {
      pageNum.value++
    }
  } catch (error: any) {
    showToast(error.message || '加载失败')
    finished.value = true
  } finally {
    loading.value = false
  }
}

// 下拉刷新
const onRefresh = async (): Promise<void> => {
  pageNum.value = 1
  finished.value = false
  await onLoad()
  refreshing.value = false
}

// Tab切换
const handleTabChange = (): void => {
  pageNum.value = 1
  finished.value = false
  applications.value = []
  onLoad()
}

// 跳转详情
const goToDetail = (id: number): void => {
  router.push(`/mobile/application-detail/${id}`)
}

// 加载对象关系
const loadRelations = async (): Promise<void> => {
  try {
    const response = await relationAPI.getMyRelations({ pageNum: 1, pageSize: 100 })
    relations.value = response.records || []
  } catch (error) {
    console.error('获取对象列表失败', error)
  }
}

// 审批人选择确认
const onApproverConfirm = ({ selectedOptions }: any): void => {
  if (selectedOptions && selectedOptions[0]) {
    newApplication.value.approverId = selectedOptions[0].value
    selectedApproverName.value = selectedOptions[0].text
  }
  showApproverPicker.value = false
}

// 提交申请
const uploadVoiceAudioInBackground = async (applicationId: number, audio: Blob): Promise<void> => {
  voiceRetryBlobs.value.set(applicationId, audio)
  try {
    await applicationAPI.uploadVoiceApplicationAudio(applicationId, audio)
    voiceRetryBlobs.value.delete(applicationId)
    showSuccessToast('Voice uploaded')
  } catch (error: any) {
    showToast(error.message || 'Voice upload failed')
  } finally {
    await onRefresh()
  }
}

const retryVoiceUpload = (app: Application): void => {
  const cachedAudio = voiceRetryBlobs.value.get(app.id)
  if (cachedAudio) {
    void uploadVoiceAudioInBackground(app.id, cachedAudio)
    return
  }

  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'audio/*,.wav,.mp3,.m4a,.aac,.webm,.ogg'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    if (!isAudioFile(file)) {
      showToast('Please select an audio file')
      return
    }
    try {
      showLoadingToast({ message: 'Processing...', forbidClick: true })
      const wavBlob = await normalizeAudioBlobToWav(file)
      closeToast()
      void uploadVoiceAudioInBackground(app.id, wavBlob)
    } catch (error: any) {
      closeToast()
      showToast(error.message || 'Audio processing failed')
    }
  }
  input.click()
}

const submitApplication = async (): Promise<void> => {
  if (!newApplication.value.approverId) {
    showToast('请选择审批人')
    return
  }

  if (createMode.value === 'voice' && !voiceRequestBlob.value) {
    showToast('请先录制或上传语音')
    return
  }

  submitting.value = true
  showLoadingToast({ message: '提交中...', forbidClick: true })

  try {
    if (createMode.value === 'voice') {
      const audio = voiceRequestBlob.value!
      const draft = await applicationAPI.createVoiceApplicationDraft(newApplication.value.approverId)
      closeToast()
      showSuccessToast('语音申请创建成功')
      resetCreateForm()
      await onRefresh()
      void uploadVoiceAudioInBackground(draft.id, audio)
      return
    }

    const result = await applicationAPI.createApplication(newApplication.value) as any
    const applicationId = result.id || result

    // 上传附件
    if (uploadFileList.value.length > 0) {
      for (const fileItem of uploadFileList.value) {
        if (fileItem.file) {
          try {
            await attachmentAPI.uploadApplicationAttachment(applicationId, fileItem.file)
          } catch (uploadError: any) {
            console.error('上传附件失败:', uploadError)
          }
        }
      }
    }

    closeToast()
    showSuccessToast('申请创建成功')

    // 重置表单
    resetCreateForm()

    // 刷新列表
    onRefresh()
  } catch (error: any) {
    closeToast()
    showToast(error.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

// 文件超出大小限制
const onOversize = (): void => {
  showToast('文件大小不能超过10MB')
}

// 打开修改弹窗
const openEditSheet = (app: Application): void => {
  editingId.value = app.id
  editApplication.value = {
    title: app.title || '',
    description: app.description || '',
    remark: app.remark || ''
  }
  showEditSheet.value = true
}

// 提交修改
const submitEdit = async (): Promise<void> => {
  editing.value = true
  showLoadingToast({ message: '保存中...', forbidClick: true })

  try {
    await applicationAPI.updateApplication(editingId.value, {
      approverId: 0, // 不会更新审批人
      ...editApplication.value
    })
    closeToast()
    showSuccessToast('修改成功')
    showEditSheet.value = false
    onRefresh()
  } catch (error: any) {
    closeToast()
    showToast(error.message || '修改失败')
  } finally {
    editing.value = false
  }
}

// 取消申请
const handleCancel = async (app: Application): Promise<void> => {
  try {
    await showConfirmDialog({
      title: '确认取消',
      message: '确定要取消这个申请吗？取消后无法恢复。'
    })

    showLoadingToast({ message: '取消中...', forbidClick: true })
    await applicationAPI.cancelApplication(app.id)
    closeToast()
    showSuccessToast('已取消')
    onRefresh()
  } catch (error: any) {
    if (error !== 'cancel') {
      closeToast()
      showToast(error.message || '取消失败')
    }
  }
}

// 删除申请（软删除，只在前端隐藏）
const handleDelete = async (app: Application): Promise<void> => {
  try {
    await showConfirmDialog({
      title: '删除记录',
      message: '确定要删除这条申请记录吗？删除后将不再显示，但不会影响实际数据。'
    })

    hideApplication(app.id)
    showSuccessToast('已删除')
  } catch (error: any) {
    // 用户取消
  }
}

const applyRoutePrefill = (): void => {
  const title = typeof route.query.title === 'string' ? route.query.title : ''
  const description = typeof route.query.description === 'string' ? route.query.description : ''
  const remark = typeof route.query.remark === 'string' ? route.query.remark : ''
  const approverId = typeof route.query.approverId === 'string' ? Number(route.query.approverId) : 0
  const approverName = typeof route.query.approverName === 'string' ? route.query.approverName : ''

  if (title || description || remark || approverId) {
    createMode.value = 'normal'
    newApplication.value.title = title
    newApplication.value.description = description
    newApplication.value.remark = remark
    selectedTemplateName.value = title ? '从模板带入' : ''
    if (approverId) {
      newApplication.value.approverId = approverId
      selectedApproverName.value = approverName
    }
    showCreateSheet.value = true
  }
}

onMounted(() => {
  loadRelations()
  loadHiddenApplications()
  applyRoutePrefill()
})

onUnmounted(() => {
  clearRecordTimer()
  if (recorderHandle) {
    recorderHandle.cancel()
    recorderHandle = null
  }
  clearVoiceRequestRecording()
})
</script>

<style scoped>
.applications-page {
  min-height: 100vh;
  background: #f5f6f7;
}

:deep(.van-nav-bar) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

:deep(.van-nav-bar__title) {
  color: white;
}

:deep(.van-nav-bar__arrow),
:deep(.van-nav-bar__right .van-icon) {
  color: white;
}

.filter-tabs {
  background: white;
  padding-top: 8px;
}

:deep(.van-tabs__nav) {
  background: transparent;
}

:deep(.van-tab--active) {
  color: #667eea;
}

:deep(.van-tabs__line) {
  background: #667eea;
}

.empty-state {
  padding: 60px 20px;
}

.application-list {
  padding: 12px;
}

.application-card {
  padding: 16px;
  background: white;
  margin-bottom: 12px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.card-title {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  flex: 1;
  margin-right: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-content {
  font-size: 14px;
  color: #666;
  line-height: 1.5;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  gap: 16px;
}

.footer-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #999;
}

.footer-item :deep(.van-icon) {
  font-size: 14px;
}

.swipe-btn {
  height: 100%;
}

.swipe-btn-delete {
  background: #ff976a;
}

.create-form {
  padding: 16px;
  max-height: 70vh;
  overflow-y: auto;
}

.template-cell-icon {
  margin-right: 8px;
  color: #667eea;
  line-height: 24px;
}

.voice-cell-icon {
  margin-right: 8px;
  color: #07c160;
  line-height: 24px;
}

.voice-transcript {
  background: #f7f8fa;
}

.voice-transcript-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #969799;
  margin-bottom: 4px;
}

.voice-transcript-text {
  font-size: 13px;
  color: #646566;
  line-height: 1.5;
}

.voice-request-preview {
  background: #f7f8fa;
}

.voice-recording-tip {
  background: #fff7e8;
}

.voice-recording-tip-title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #d46b08;
}

.voice-recording-tip-text {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.45;
  color: #8c6d1f;
}

.voice-request-audio {
  width: 100%;
  display: block;
}

.voice-request-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.voice-recorder {
  padding: 28px 24px 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.voice-recorder-title {
  font-size: 18px;
  font-weight: 600;
  color: #323233;
}

.voice-recorder-hint {
  margin-top: 6px;
  font-size: 13px;
  color: #969799;
  text-align: center;
}

.voice-mic {
  margin: 24px 0 12px;
  width: 88px;
  height: 88px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.voice-mic.active {
  animation: voice-pulse 1.4s ease-in-out infinite;
}

.voice-mic.parsing {
  background: linear-gradient(135deg, #969799 0%, #646566 100%);
  box-shadow: none;
}

@keyframes voice-pulse {
  0% { transform: scale(1); box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4); }
  50% { transform: scale(1.08); box-shadow: 0 8px 28px rgba(102, 126, 234, 0.6); }
  100% { transform: scale(1); box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4); }
}

.voice-timer {
  font-size: 22px;
  font-variant-numeric: tabular-nums;
  color: #323233;
  margin-bottom: 8px;
}

.voice-recorder-actions {
  margin-top: 16px;
  display: flex;
  gap: 16px;
}

.voice-recorder-actions .van-button {
  min-width: 100px;
}

.template-picker {
  max-height: 72vh;
  padding: 16px 0 20px;
  overflow-y: auto;
}

.template-picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 16px 12px;
}

.template-picker-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.template-picker-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: #999;
}

.template-loading {
  display: flex;
  justify-content: center;
  padding: 28px 0;
}

.template-usage {
  color: #999;
  font-size: 12px;
}

.form-actions {
  padding: 16px;
}

.fab-button {
  position: fixed;
  right: 20px;
  bottom: 80px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.4);
  z-index: 100;
}

.fab-button:active {
  transform: scale(0.95);
}

:deep(.van-action-sheet__header) {
  font-weight: 500;
}

:deep(.van-cell-group--inset) {
  margin: 0;
}

.voice-warning {
  background: #fff5e6;
  padding: 8px 16px;
}

.voice-warning :deep(.van-cell__title) {
  display: flex;
  align-items: center;
}
</style>
