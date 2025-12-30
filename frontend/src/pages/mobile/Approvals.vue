<template>
  <div class="approvals-page">
    <!-- 顶部导航栏 -->
    <van-nav-bar title="待审批" fixed placeholder>
      <template #right>
        <van-icon name="filter-o" size="20" @click="showFilter = true" />
      </template>
    </van-nav-bar>

    <!-- 下拉刷新 -->
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <!-- 空状态 -->
      <van-empty
        v-if="!loading && approvals.length === 0"
        image="search"
        description="暂无待审批申请"
      />

      <!-- 审批列表 -->
      <van-list
        v-else
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="onLoad"
      >
        <van-swipe-cell v-for="app in approvals" :key="app.id">
          <van-cell-group inset class="approval-card" @click="goToDetail(app.id)">
            <div class="card-header">
              <div class="card-title">
                <van-icon name="description" color="#4f46e5" />
                <span>{{ app.title }}</span>
              </div>
              <van-tag type="warning" size="medium">待审批</van-tag>
            </div>

            <div class="card-body">
              <div class="info-row">
                <van-icon name="user-o" size="14" />
                <span class="label">申请人：</span>
                <span class="value">{{ app.applicantName || app.applicantUsername }}</span>
              </div>
              <div class="info-row description">
                <van-icon name="notes-o" size="14" />
                <span class="label">说明：</span>
                <span class="value">{{ app.description || '无' }}</span>
              </div>
              <div class="info-row">
                <van-icon name="clock-o" size="14" />
                <span class="label">申请时间：</span>
                <span class="value">{{ formatDate(app.createdAt) }}</span>
              </div>
            </div>

            <div class="card-footer">
              <van-button
                size="small"
                type="success"
                icon="success"
                @click.stop="handleQuickApprove(app)"
              >
                通过
              </van-button>
              <van-button
                size="small"
                type="danger"
                icon="cross"
                @click.stop="handleQuickReject(app)"
              >
                拒绝
              </van-button>
            </div>
          </van-cell-group>

          <!-- 左滑操作按钮 -->
          <template #right>
            <van-button
              square
              type="success"
              text="通过"
              class="swipe-btn"
              @click="handleQuickApprove(app)"
            />
            <van-button
              square
              type="danger"
              text="拒绝"
              class="swipe-btn"
              @click="handleQuickReject(app)"
            />
          </template>
        </van-swipe-cell>
      </van-list>
    </van-pull-refresh>

    <!-- 审批确认弹窗 -->
    <van-dialog
      v-model:show="showApproveDialog"
      title="确认通过"
      show-cancel-button
      @confirm="confirmApprove"
    >
      <van-field
        v-model="approvalDetail"
        rows="3"
        autosize
        type="textarea"
        placeholder="请输入审批意见（选填）"
        class="dialog-input"
      />
    </van-dialog>

    <!-- 拒绝确认弹窗 -->
    <van-dialog
      v-model:show="showRejectDialog"
      title="确认拒绝"
      show-cancel-button
      @confirm="confirmReject"
    >
      <van-field
        v-model="rejectDetail"
        rows="3"
        autosize
        type="textarea"
        placeholder="请输入拒绝原因"
        class="dialog-input"
      />
    </van-dialog>

    <!-- 筛选弹窗 -->
    <van-action-sheet
      v-model:show="showFilter"
      title="筛选条件"
    >
      <div class="filter-content">
        <van-cell-group inset>
          <van-field
            v-model="filterForm.keyword"
            label="关键词"
            placeholder="搜索标题或申请人"
            clearable
          />
        </van-cell-group>
        <div class="filter-actions">
          <van-button round block type="primary" @click="applyFilter">
            应用筛选
          </van-button>
          <van-button round block @click="resetFilter" style="margin-top: 12px;">
            重置
          </van-button>
        </div>
      </div>
    </van-action-sheet>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showFailToast, showLoadingToast, closeToast } from 'vant'
import { applicationAPI } from '@/services/api'

interface Application {
  id: number
  title: string
  description: string
  applicantId: number
  applicantName?: string
  applicantUsername?: string
  status: string
  createdAt: string
}

const router = useRouter()

// 列表状态
const approvals = ref<Application[]>([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const pageNum = ref(1)
const pageSize = 10

// 弹窗状态
const showApproveDialog = ref(false)
const showRejectDialog = ref(false)
const showFilter = ref(false)

// 表单数据
const currentApp = ref<Application | null>(null)
const approvalDetail = ref('')
const rejectDetail = ref('')

// 筛选条件
const filterForm = reactive({
  keyword: '',
})

onMounted(() => {
  onLoad()
})

// 加载数据
const onLoad = async (): Promise<void> => {
  try {
    const response = await applicationAPI.getPendingApplications({
      pageNum: pageNum.value,
      pageSize,
      keyword: filterForm.keyword || undefined,
    })

    const records = response.records || response || []

    if (pageNum.value === 1) {
      approvals.value = records
    } else {
      approvals.value.push(...records)
    }

    loading.value = false

    if (records.length < pageSize) {
      finished.value = true
    } else {
      pageNum.value++
    }
  } catch (error: any) {
    loading.value = false
    showFailToast(error.message || '加载失败')
  }
}

// 下拉刷新
const onRefresh = async (): Promise<void> => {
  pageNum.value = 1
  finished.value = false
  await onLoad()
  refreshing.value = false
  showSuccessToast('刷新成功')
}

// 跳转详情
const goToDetail = (id: number): void => {
  router.push(`/mobile/application-detail/${id}`)
}

// 快速通过
const handleQuickApprove = (app: Application): void => {
  currentApp.value = app
  approvalDetail.value = ''
  showApproveDialog.value = true
}

// 快速拒绝
const handleQuickReject = (app: Application): void => {
  currentApp.value = app
  rejectDetail.value = ''
  showRejectDialog.value = true
}

// 确认通过
const confirmApprove = async (): Promise<void> => {
  if (!currentApp.value) return

  showLoadingToast({ message: '处理中...', forbidClick: true })

  try {
    await applicationAPI.approveApplication(currentApp.value.id, {
      approvalDetail: approvalDetail.value || undefined,
    })
    closeToast()
    showSuccessToast('审批通过')

    // 从列表中移除
    approvals.value = approvals.value.filter(a => a.id !== currentApp.value!.id)
  } catch (error: any) {
    closeToast()
    showFailToast(error.message || '操作失败')
  }
}

// 确认拒绝
const confirmReject = async (): Promise<void> => {
  if (!currentApp.value) return

  showLoadingToast({ message: '处理中...', forbidClick: true })

  try {
    await applicationAPI.rejectApplication(currentApp.value.id, {
      approvalDetail: rejectDetail.value || undefined,
    })
    closeToast()
    showSuccessToast('已拒绝')

    // 从列表中移除
    approvals.value = approvals.value.filter(a => a.id !== currentApp.value!.id)
  } catch (error: any) {
    closeToast()
    showFailToast(error.message || '操作失败')
  }
}

// 应用筛选
const applyFilter = (): void => {
  showFilter.value = false
  pageNum.value = 1
  finished.value = false
  approvals.value = []
  onLoad()
}

// 重置筛选
const resetFilter = (): void => {
  filterForm.keyword = ''
  applyFilter()
}

// 格式化日期
const formatDate = (date: string): string => {
  if (!date) return '-'
  const d = new Date(date)
  const now = new Date()
  const diff = now.getTime() - d.getTime()

  // 小于1分钟
  if (diff < 60000) {
    return '刚刚'
  }
  // 小于1小时
  if (diff < 3600000) {
    return `${Math.floor(diff / 60000)}分钟前`
  }
  // 小于24小时
  if (diff < 86400000) {
    return `${Math.floor(diff / 3600000)}小时前`
  }
  // 小于7天
  if (diff < 604800000) {
    return `${Math.floor(diff / 86400000)}天前`
  }

  return d.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}
</script>

<style scoped>
.approvals-page {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 60px;
}

.approval-card {
  margin: 12px 16px;
  border-radius: 12px;
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 16px 12px;
  border-bottom: 1px solid #f0f0f0;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
}

.card-body {
  padding: 12px 16px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 14px;
  color: #666;
}

.info-row:last-child {
  margin-bottom: 0;
}

.info-row .label {
  color: #999;
}

.info-row .value {
  color: #333;
}

.info-row.description .value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
  background: #fafafa;
}

.swipe-btn {
  height: 100%;
}

.dialog-input {
  margin: 16px;
  background: #f7f8fa;
  border-radius: 8px;
}

.filter-content {
  padding: 16px;
}

.filter-actions {
  margin-top: 24px;
}

/* 下拉刷新样式 */
:deep(.van-pull-refresh) {
  min-height: calc(100vh - 46px);
}

/* 空状态居中 */
:deep(.van-empty) {
  padding-top: 100px;
}

/* 滑动单元格样式 */
:deep(.van-swipe-cell__right) {
  display: flex;
}
</style>
