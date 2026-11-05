<template>
  <div class="approvals-page">
    <header class="page-head">
      <ApplicationsSwitch current="approvals" />
      <van-search
        v-model="keyword"
        class="search"
        placeholder="按标题搜索"
        shape="round"
        :clearable="true"
        @search="reset"
        @clear="onClear"
      />
    </header>

    <van-pull-refresh v-model="refreshing" class="m-page" @refresh="refresh">
      <van-list
        v-model:loading="loading"
        v-model:error="error"
        :finished="finished"
        :finished-text="list.length ? '没有更多了' : ''"
        error-text="加载失败，点击重试"
        @load="load"
      >
        <van-empty
          v-if="finished && !error && list.length === 0"
          class="m-empty"
          :description="keyword ? '没有匹配的申请' : '没有等你审批的申请'"
        />

        <article v-for="app in list" :key="app.id" class="m-card approval-card" @click="goToDetail(app.id)">
          <div class="card-head">
            <h3 class="m-title">
              <van-icon v-if="app.appType === AppType.VOICE" name="volume-o" class="voice-icon" />
              {{ app.title || '语音申请' }}
            </h3>
          </div>
          <p class="card-desc">
            {{ app.description || (app.appType === AppType.VOICE ? '语音申请，进入详情收听' : '没有填写说明') }}
          </p>
          <div class="card-foot">
            <div class="m-meta">
              <span>{{ app.applicantName || '对方' }}</span>
              <span>{{ relativeTime(app.createdAt) }}</span>
            </div>
            <div class="card-actions">
              <van-button size="small" plain @click.stop="openDecision(app, 'reject')">驳回</van-button>
              <van-button size="small" type="primary" @click.stop="openDecision(app, 'approve')">通过</van-button>
            </div>
          </div>
        </article>
      </van-list>
    </van-pull-refresh>

    <!-- before-close：接口失败时弹窗保持打开，填写的意见不会丢 -->
    <van-dialog
      v-model:show="showDecision"
      :title="decision === 'approve' ? '通过申请' : '驳回申请'"
      show-cancel-button
      :confirm-button-text="decision === 'approve' ? '通过' : '驳回'"
      :confirm-button-color="decision === 'approve' ? 'var(--accent)' : 'var(--danger)'"
      :before-close="beforeDecisionClose"
    >
      <p v-if="currentApp" class="dialog-subject">{{ currentApp.title || '语音申请' }}</p>
      <van-field
        v-model="comment"
        rows="3"
        autosize
        type="textarea"
        maxlength="500"
        :placeholder="decision === 'approve' ? '审批意见（选填）' : '驳回原因（选填）'"
        class="dialog-input"
      />
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { applicationAPI, type Application } from '@/services/api'
import { usePagedList } from '@/composables/usePagedList'
import { relativeTime } from '@/utils/format'
import { AppType } from '@/utils/status'
import ApplicationsSwitch from '@/components/application/ApplicationsSwitch.vue'

const router = useRouter()
const keyword = ref('')

const { list, loading, finished, error, refreshing, load, reset, refresh } = usePagedList<Application>(
  (pageNum, pageSize) => applicationAPI.getPendingApplications({
    pageNum,
    pageSize,
    keyword: keyword.value.trim() || undefined,
  })
)

const showDecision = ref(false)
const decision = ref<'approve' | 'reject'>('approve')
const currentApp = ref<Application | null>(null)
const comment = ref('')

const goToDetail = (id: number): void => {
  router.push(`/mobile/application-detail/${id}`)
}

const onClear = (): void => {
  keyword.value = ''
  reset()
}

const openDecision = (app: Application, type: 'approve' | 'reject'): void => {
  currentApp.value = app
  decision.value = type
  comment.value = ''
  showDecision.value = true
}

const beforeDecisionClose = async (action: string): Promise<boolean> => {
  if (action !== 'confirm' || !currentApp.value) return true

  const payload = { approvalDetail: comment.value.trim() || undefined }
  try {
    if (decision.value === 'approve') {
      await applicationAPI.approveApplication(currentApp.value.id, payload)
      showSuccessToast('已通过')
    } else {
      await applicationAPI.rejectApplication(currentApp.value.id, payload)
      showSuccessToast('已驳回')
    }
    // 处理掉一条后从第一页重新拉，避免分页偏移漏掉后面的申请
    refresh()
    return true
  } catch (e: any) {
    showFailToast(e.message || '操作失败')
    return false
  }
}
</script>

<style scoped>
.approvals-page {
  min-height: 100%;
  background: var(--bg);
}

.page-head {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: max(10px, env(safe-area-inset-top)) 12px 0;
  background: var(--bg);
}

.search {
  margin-top: 8px;
  padding: 0 0 4px;
  background: transparent;
}

.search :deep(.van-search__content) {
  background: var(--surface);
}

.approval-card {
  padding: 14px 16px;
}

.approval-card + .approval-card {
  margin-top: 8px;
}

.voice-icon {
  margin-right: 2px;
  color: var(--ink-2);
  vertical-align: -2px;
}

.card-desc {
  display: -webkit-box;
  margin-top: 4px;
  overflow: hidden;
  color: var(--ink-2);
  font-size: 14px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.card-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.card-actions .van-button {
  min-width: 64px;
}

.dialog-subject {
  padding: 8px 24px 0;
  color: var(--ink-2);
  font-size: 14px;
  text-align: center;
}

.dialog-input {
  margin: 12px 16px 4px;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  width: auto;
}
</style>
