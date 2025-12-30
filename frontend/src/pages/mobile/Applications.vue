<template>
  <div class="applications-page">
    <!-- 顶部导航 -->
    <van-nav-bar
      title="我的申请"
      left-arrow
      @click-left="$router.back()"
    >
      <template #right>
        <van-icon name="add-o" size="20" @click="showCreateSheet = true" />
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
        <div v-if="applications.length === 0 && !loading" class="empty-state">
          <van-empty description="暂无申请记录">
            <van-button type="primary" round size="small" @click="showCreateSheet = true">
              创建申请
            </van-button>
          </van-empty>
        </div>

        <div v-else class="application-list">
          <van-swipe-cell v-for="app in applications" :key="app.id">
            <div class="application-card" @click="goToDetail(app.id)">
              <div class="card-header">
                <div class="card-title">{{ app.title }}</div>
                <van-tag :type="getStatusType(app.status)" size="medium">
                  {{ getStatusText(app.status) }}
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
            <van-field
              v-model="selectedApproverName"
              is-link
              readonly
              label="选择审批人"
              placeholder="请选择审批人"
              required
              @click="showApproverPicker = true"
            />
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
                <van-switch v-model="newApplication.sendVoiceNotification" size="20" />
              </template>
            </van-field>
          </van-cell-group>

          <div class="form-actions">
            <van-button round block type="primary" native-type="submit" :loading="submitting">
              提交申请
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
    <div class="fab-button" @click="showCreateSheet = true">
      <van-icon name="plus" size="24" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast, showLoadingToast, closeToast, showConfirmDialog } from 'vant'
import { applicationAPI, relationAPI, attachmentAPI, type Application } from '@/services/api'

const router = useRouter()

// 列表数据
const applications = ref<Application[]>([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const activeTab = ref(0)
const pageNum = ref(1)
const pageSize = 10
const pendingCount = ref(0)

// 创建申请
const showCreateSheet = ref(false)
const submitting = ref(false)
const showApproverPicker = ref(false)
const relations = ref<any[]>([])
const selectedApproverName = ref('')
const uploadFileList = ref<any[]>([])

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

// 审批人选择列 - 使用后端返回的 otherUserId 和 otherUserName
const approverColumns = computed(() => {
  return relations.value.map(r => ({
    text: r.otherUserName || r.otherUserUsername || `用户${r.otherUserId}`,
    value: r.otherUserId
  }))
})

// 获取状态类型
const getStatusType = (status: number): string => {
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
const submitApplication = async (): Promise<void> => {
  if (!newApplication.value.approverId) {
    showToast('请选择审批人')
    return
  }

  submitting.value = true
  showLoadingToast({ message: '提交中...', forbidClick: true })

  try {
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
    showCreateSheet.value = false
    newApplication.value = { approverId: 0, title: '', description: '', remark: '', sendVoiceNotification: false }
    selectedApproverName.value = ''
    uploadFileList.value = []

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

onMounted(() => {
  loadRelations()
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

.create-form {
  padding: 16px;
  max-height: 70vh;
  overflow-y: auto;
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
</style>
