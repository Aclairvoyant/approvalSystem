<template>
  <div class="applications-page">
    <header class="page-head">
      <div class="head-row">
        <ApplicationsSwitch current="mine" />
        <van-button class="create-btn" type="primary" size="small" icon="plus" @click="openCreate()">发起</van-button>
      </div>
      <van-tabs v-model:active="activeStatus" class="status-tabs" @change="reset">
        <van-tab title="全部" :name="0" />
        <van-tab title="待审批" :name="ApplicationStatus.PENDING" :badge="pendingCount || ''" />
        <van-tab title="已通过" :name="ApplicationStatus.APPROVED" />
        <van-tab title="已驳回" :name="ApplicationStatus.REJECTED" />
        <van-tab title="已取消" :name="ApplicationStatus.CANCELLED" />
      </van-tabs>
    </header>

    <van-pull-refresh v-model="refreshing" class="m-page" @refresh="refreshAll">
      <van-list
        v-model:loading="loading"
        v-model:error="error"
        :finished="finished"
        :finished-text="visibleApplications.length ? '没有更多了' : ''"
        error-text="加载失败，点击重试"
        @load="load"
      >
        <van-empty
          v-if="finished && !error && visibleApplications.length === 0"
          class="m-empty"
          :description="activeStatus === 0 ? '还没有发起过申请' : '这个状态下没有申请'"
        >
          <van-button v-if="activeStatus === 0" type="primary" size="small" @click="openCreate()">发起申请</van-button>
        </van-empty>

        <van-swipe-cell v-for="app in visibleApplications" :key="app.id" class="m-card">
          <ApplicationCard :application="app" :uploading="uploadingIds.has(app.id)" @open="goToDetail" />
          <template #right>
            <van-button
              v-if="app.status === ApplicationStatus.PENDING"
              square
              type="primary"
              text="修改"
              class="m-swipe-btn"
              @click="editSheet?.open(app)"
            />
            <van-button
              v-if="app.status === ApplicationStatus.PENDING"
              square
              type="danger"
              text="撤回"
              class="m-swipe-btn"
              @click="handleCancel(app)"
            />
            <van-button
              v-if="canRetryVoice(app)"
              square
              type="primary"
              text="重传语音"
              class="m-swipe-btn"
              @click="retryVoiceUpload(app)"
            />
            <van-button square text="隐藏" class="m-swipe-btn" @click="handleHide(app)" />
          </template>
        </van-swipe-cell>
      </van-list>
    </van-pull-refresh>

    <ApplicationCreateSheet ref="createSheet" :relations="relations" @created="onCreated" />
    <ApplicationEditSheet ref="editSheet" @saved="refreshAll" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { closeToast, showConfirmDialog, showFailToast, showLoadingToast, showSuccessToast } from 'vant'
import { applicationAPI, relationAPI, type Application, type UserRelation } from '@/services/api'
import { usePagedList } from '@/composables/usePagedList'
import { normalizeAudioBlobToWav } from '@/utils/wavRecorder'
import { AppType, ApplicationStatus, VoiceStatus } from '@/utils/status'
import ApplicationsSwitch from '@/components/application/ApplicationsSwitch.vue'
import ApplicationCard from '@/components/application/ApplicationCard.vue'
import ApplicationCreateSheet, { type CreatedPayload } from '@/components/application/ApplicationCreateSheet.vue'
import ApplicationEditSheet from '@/components/application/ApplicationEditSheet.vue'

const HIDDEN_KEY = 'hiddenApplications'

const router = useRouter()
const route = useRoute()

const activeStatus = ref(0)
const pendingCount = ref(0)
const relations = ref<UserRelation[]>([])
const createSheet = ref<InstanceType<typeof ApplicationCreateSheet> | null>(null)
const editSheet = ref<InstanceType<typeof ApplicationEditSheet> | null>(null)

const { list, loading, finished, error, refreshing, load, reset, refresh } = usePagedList<Application>(
  (pageNum, pageSize) => applicationAPI.getMyApplications({
    pageNum,
    pageSize,
    status: activeStatus.value || undefined,
  })
)

// "隐藏"只影响本机列表显示，不改后端数据
const hiddenIds = ref<Set<number>>(new Set(JSON.parse(localStorage.getItem(HIDDEN_KEY) || '[]')))
const visibleApplications = computed(() => list.value.filter(app => !hiddenIds.value.has(app.id)))

// 本次会话里正在后台上传音频的语音申请
const uploadingIds = reactive(new Set<number>())
const voiceRetryBlobs = new Map<number, Blob>()

async function loadPendingCount(): Promise<void> {
  try {
    const page = await applicationAPI.getMyApplications({ pageNum: 1, pageSize: 1, status: ApplicationStatus.PENDING })
    pendingCount.value = page.total || 0
  } catch {
    // 角标只是提示，失败时保持原值
  }
}

async function refreshAll(): Promise<void> {
  await Promise.all([refresh(), loadPendingCount()])
}

async function loadRelations(): Promise<void> {
  try {
    const page = await relationAPI.getMyRelations({ pageNum: 1, pageSize: 100 })
    relations.value = page.records || []
  } catch (e: any) {
    showFailToast(e.message || '对象列表加载失败')
  }
}

function goToDetail(id: number): void {
  router.push(`/mobile/application-detail/${id}`)
}

function openCreate(prefill = {}): void {
  createSheet.value?.open(prefill)
}

function onCreated(payload: CreatedPayload): void {
  refreshAll()
  if (payload.voiceAudio) {
    void uploadVoiceAudio(payload.applicationId, payload.voiceAudio)
  }
}

function canRetryVoice(app: Application): boolean {
  if (app.appType !== AppType.VOICE || app.status !== ApplicationStatus.DRAFT) return false
  if (uploadingIds.has(app.id)) return false
  // 上传中（1）却不在本次会话的上传队列里，说明上次上传中途被打断了，同样允许重传
  return app.voiceStatus === VoiceStatus.FAILED || app.voiceStatus === VoiceStatus.UPLOADING
}

async function uploadVoiceAudio(applicationId: number, audio: Blob): Promise<void> {
  voiceRetryBlobs.set(applicationId, audio)
  uploadingIds.add(applicationId)
  try {
    await applicationAPI.uploadVoiceApplicationAudio(applicationId, audio)
    voiceRetryBlobs.delete(applicationId)
    showSuccessToast('语音已上传')
  } catch (e: any) {
    showFailToast(e.message || '语音上传失败，可以左滑重传')
  } finally {
    uploadingIds.delete(applicationId)
    refresh()
  }
}

function retryVoiceUpload(app: Application): void {
  const cached = voiceRetryBlobs.get(app.id)
  if (cached) {
    void uploadVoiceAudio(app.id, cached)
    return
  }

  // 刷新过页面就没有缓存的录音了，让用户重新选一个音频文件
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'audio/*,.wav,.mp3,.m4a,.aac,.webm,.ogg'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    showLoadingToast({ message: '处理音频...', forbidClick: true })
    try {
      const wav = await normalizeAudioBlobToWav(file)
      closeToast()
      void uploadVoiceAudio(app.id, wav)
    } catch (e: any) {
      closeToast()
      showFailToast(e.message || '音频处理失败')
    }
  }
  input.click()
}

async function handleCancel(app: Application): Promise<void> {
  try {
    await showConfirmDialog({
      title: '撤回申请',
      message: '撤回后对方将无法再审批，确定撤回吗？',
      confirmButtonText: '撤回',
      confirmButtonColor: 'var(--danger)',
    })
  } catch {
    return
  }

  try {
    await applicationAPI.cancelApplication(app.id)
    showSuccessToast('已撤回')
    refreshAll()
  } catch (e: any) {
    showFailToast(e.message || '撤回失败')
  }
}

async function handleHide(app: Application): Promise<void> {
  try {
    await showConfirmDialog({
      title: '隐藏这条申请',
      message: '只会从你的列表里隐藏，不会删除，对方仍然能看到。',
      confirmButtonText: '隐藏',
    })
  } catch {
    return
  }
  hiddenIds.value = new Set([...hiddenIds.value, app.id])
  localStorage.setItem(HIDDEN_KEY, JSON.stringify([...hiddenIds.value]))
}

/** 从模板、对象页或首页跳过来时带的参数：打开创建面板后清掉，刷新页面不会再弹 */
function consumeRouteQuery(): void {
  const q = route.query
  const text = (key: string) => (typeof q[key] === 'string' ? (q[key] as string) : '')
  const approverId = Number(text('approverId')) || undefined
  const hasPrefill = text('title') || text('description') || text('remark') || approverId

  if (hasPrefill || text('create')) {
    openCreate({
      title: text('title'),
      description: text('description'),
      remark: text('remark'),
      approverId,
      approverName: text('approverName'),
    })
    router.replace({ query: {} })
  }
}

onMounted(async () => {
  loadPendingCount()
  await loadRelations()
  consumeRouteQuery()
})
</script>

<style scoped>
.applications-page {
  min-height: 100%;
  background: var(--bg);
}

.page-head {
  position: sticky;
  top: 0;
  z-index: 10;
  padding-top: max(10px, env(safe-area-inset-top));
  background: var(--bg);
}

.head-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
}

.head-row :deep(.apps-switch) {
  flex: 1;
}

.create-btn {
  flex-shrink: 0;
  height: 36px;
}

.status-tabs {
  margin-top: 4px;
}

.status-tabs :deep(.van-tabs__nav) {
  background: transparent;
}

.m-card + .m-card,
:deep(.van-swipe-cell + .van-swipe-cell) {
  margin-top: 8px;
}

:deep(.van-swipe-cell) {
  overflow: hidden;
}
</style>
