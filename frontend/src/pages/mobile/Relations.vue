<template>
  <div class="relations-page">
    <!-- 顶部导航栏 -->
    <van-nav-bar title="我的对象" fixed placeholder>
      <template #right>
        <van-icon name="plus" size="20" @click="showAddDialog = true" />
      </template>
    </van-nav-bar>

    <!-- 标签页 -->
    <van-tabs v-model:active="activeTab" sticky offset-top="46">
      <!-- 已建立的对象 -->
      <van-tab title="我的对象" :badge="relations.length || ''">
        <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
          <van-empty
            v-if="!loading && relations.length === 0"
            image="default"
            description="还没有对象，点击右上角添加"
          />

          <van-list
            v-else
            v-model:loading="loading"
            :finished="finished"
            finished-text="没有更多了"
            @load="loadRelations"
          >
            <van-swipe-cell v-for="relation in relations" :key="relation.id">
              <van-cell-group inset class="relation-card">
                <div class="card-content">
                  <van-image
                    round
                    width="50"
                    height="50"
                    :src="relation.otherUserAvatar || defaultAvatar"
                    class="avatar"
                  />
                  <div class="info">
                    <div class="name">{{ relation.otherUserName || relation.otherUserUsername }}</div>
                    <div class="detail">
                      <van-icon name="phone-o" size="12" />
                      <span>{{ relation.otherUserPhone || '未设置' }}</span>
                    </div>
                    <div class="detail" v-if="relation.otherUserEmail">
                      <van-icon name="envelop-o" size="12" />
                      <span>{{ relation.otherUserEmail }}</span>
                    </div>
                  </div>
                  <van-button
                    size="small"
                    type="primary"
                    plain
                    icon="edit"
                    @click="createApplication(relation)"
                  >
                    发起申请
                  </van-button>
                </div>
              </van-cell-group>

              <template #right>
                <van-button
                  square
                  type="danger"
                  text="删除"
                  class="swipe-btn"
                  @click="handleDelete(relation)"
                />
              </template>
            </van-swipe-cell>
          </van-list>
        </van-pull-refresh>
      </van-tab>

      <!-- 待处理的申请 -->
      <van-tab title="待处理" :badge="pendingRequests.length || ''">
        <van-pull-refresh v-model="refreshingPending" @refresh="onRefreshPending">
          <van-empty
            v-if="!loadingPending && pendingRequests.length === 0"
            image="search"
            description="暂无待处理的申请"
          />

          <van-list
            v-else
            v-model:loading="loadingPending"
            :finished="finishedPending"
            finished-text="没有更多了"
            @load="loadPendingRequests"
          >
            <van-cell-group
              v-for="request in pendingRequests"
              :key="request.id"
              inset
              class="request-card"
            >
              <div class="card-content">
                <van-image
                  round
                  width="50"
                  height="50"
                  :src="request.requesterAvatar || defaultAvatar"
                  class="avatar"
                />
                <div class="info">
                  <div class="name">{{ request.requesterName || request.requesterUsername }}</div>
                  <div class="detail">
                    <van-icon name="clock-o" size="12" />
                    <span>{{ formatDate(request.createdAt) }}</span>
                  </div>
                </div>
              </div>
              <div class="card-actions">
                <van-button
                  size="small"
                  type="success"
                  @click="handleAccept(request)"
                >
                  接受
                </van-button>
                <van-button
                  size="small"
                  type="danger"
                  plain
                  @click="handleReject(request)"
                >
                  拒绝
                </van-button>
              </div>
            </van-cell-group>
          </van-list>
        </van-pull-refresh>
      </van-tab>
    </van-tabs>

    <!-- 添加对象弹窗 -->
    <van-popup
      v-model:show="showAddDialog"
      position="bottom"
      round
      :style="{ height: '60%' }"
    >
      <div class="add-dialog">
        <div class="dialog-header">
          <span class="title">添加对象</span>
          <van-icon name="cross" size="20" @click="showAddDialog = false" />
        </div>

        <van-search
          v-model="searchKeyword"
          placeholder="输入用户名或手机号搜索"
          show-action
          @search="handleSearch"
          @cancel="searchKeyword = ''; searchResult = null"
        />

        <!-- 搜索结果 -->
        <div v-if="searching" class="searching">
          <van-loading size="24">搜索中...</van-loading>
        </div>

        <div v-else-if="searchResult" class="search-result">
          <van-cell-group inset>
            <div class="result-card">
              <van-image
                round
                width="60"
                height="60"
                :src="searchResult.avatar || defaultAvatar"
                class="avatar"
              />
              <div class="info">
                <div class="name">{{ searchResult.realName || searchResult.username }}</div>
                <div class="username">@{{ searchResult.username }}</div>
                <div class="phone" v-if="searchResult.phone">
                  <van-icon name="phone-o" size="12" />
                  {{ searchResult.phone }}
                </div>
              </div>
              <van-button
                type="primary"
                size="small"
                :loading="sending"
                @click="sendRequest"
              >
                发送申请
              </van-button>
            </div>
          </van-cell-group>
        </div>

        <van-empty
          v-else-if="searchKeyword && !searchResult"
          image="search"
          description="未找到用户，请检查用户名或手机号"
        />

        <div v-else class="search-tips">
          <van-icon name="info-o" size="48" color="#ddd" />
          <p>输入对方的用户名或手机号进行搜索</p>
        </div>
      </div>
    </van-popup>

    <!-- 删除确认弹窗 -->
    <van-dialog
      v-model:show="showDeleteDialog"
      title="确认删除"
      message="删除后将解除对象关系，确定要删除吗？"
      show-cancel-button
      @confirm="confirmDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showFailToast, showLoadingToast, closeToast } from 'vant'
import { relationAPI } from '@/services/api'

interface UserRelation {
  id: number
  userId: number
  relatedUserId: number
  requesterId: number
  // 对象用户信息（对方）
  otherUserId: number
  otherUserName?: string
  otherUserUsername?: string
  otherUserPhone?: string
  otherUserEmail?: string
  otherUserAvatar?: string
  // 申请发起人信息
  requesterName?: string
  requesterUsername?: string
  requesterAvatar?: string
  createdAt: string
}

interface SearchUser {
  id: number
  username: string
  realName?: string
  phone?: string
  email?: string
  avatar?: string
}

const router = useRouter()
const defaultAvatar = 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg'

// 标签页
const activeTab = ref(0)

// 我的对象列表
const relations = ref<UserRelation[]>([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const pageNum = ref(1)

// 待处理申请列表
const pendingRequests = ref<UserRelation[]>([])
const loadingPending = ref(false)
const finishedPending = ref(false)
const refreshingPending = ref(false)
const pageNumPending = ref(1)

// 添加对象
const showAddDialog = ref(false)
const searchKeyword = ref('')
const searchResult = ref<SearchUser | null>(null)
const searching = ref(false)
const sending = ref(false)

// 删除
const showDeleteDialog = ref(false)
const currentRelation = ref<UserRelation | null>(null)

onMounted(() => {
  loadRelations()
  loadPendingRequests()
})

// 加载我的对象
const loadRelations = async (): Promise<void> => {
  try {
    const response = await relationAPI.getMyRelations({
      pageNum: pageNum.value,
      pageSize: 10,
    })
    const records = response.records || response || []

    if (pageNum.value === 1) {
      relations.value = records
    } else {
      relations.value.push(...records)
    }

    loading.value = false

    if (records.length < 10) {
      finished.value = true
    } else {
      pageNum.value++
    }
  } catch (error: any) {
    loading.value = false
    showFailToast(error.message || '加载失败')
  }
}

// 加载待处理申请
const loadPendingRequests = async (): Promise<void> => {
  try {
    const response = await relationAPI.getPendingRequests({
      pageNum: pageNumPending.value,
      pageSize: 10,
    })
    const records = response.records || response || []

    if (pageNumPending.value === 1) {
      pendingRequests.value = records
    } else {
      pendingRequests.value.push(...records)
    }

    loadingPending.value = false

    if (records.length < 10) {
      finishedPending.value = true
    } else {
      pageNumPending.value++
    }
  } catch (error: any) {
    loadingPending.value = false
    showFailToast(error.message || '加载失败')
  }
}

// 下拉刷新
const onRefresh = async (): Promise<void> => {
  pageNum.value = 1
  finished.value = false
  await loadRelations()
  refreshing.value = false
}

const onRefreshPending = async (): Promise<void> => {
  pageNumPending.value = 1
  finishedPending.value = false
  await loadPendingRequests()
  refreshingPending.value = false
}

// 搜索用户
const handleSearch = async (): Promise<void> => {
  if (!searchKeyword.value.trim()) {
    showFailToast('请输入搜索关键词')
    return
  }

  searching.value = true
  searchResult.value = null

  try {
    const result = await relationAPI.searchUser(searchKeyword.value.trim())
    searchResult.value = result as SearchUser
  } catch (error: any) {
    searchResult.value = null
    if (error.message !== '未找到该用户') {
      showFailToast(error.message || '搜索失败')
    }
  } finally {
    searching.value = false
  }
}

// 发送申请
const sendRequest = async (): Promise<void> => {
  if (!searchResult.value) return

  sending.value = true
  try {
    await relationAPI.initiateRelation(searchResult.value.id)
    showSuccessToast('申请已发送')
    showAddDialog.value = false
    searchKeyword.value = ''
    searchResult.value = null
  } catch (error: any) {
    showFailToast(error.message || '发送失败')
  } finally {
    sending.value = false
  }
}

// 接受申请
const handleAccept = async (request: UserRelation): Promise<void> => {
  showLoadingToast({ message: '处理中...', forbidClick: true })
  try {
    // 使用申请发起人ID来接受申请
    await relationAPI.acceptRelation(request.requesterId)
    closeToast()
    showSuccessToast('已接受')
    // 刷新列表
    pageNum.value = 1
    pageNumPending.value = 1
    finished.value = false
    finishedPending.value = false
    await Promise.all([loadRelations(), loadPendingRequests()])
  } catch (error: any) {
    closeToast()
    showFailToast(error.message || '操作失败')
  }
}

// 拒绝申请
const handleReject = async (request: UserRelation): Promise<void> => {
  showLoadingToast({ message: '处理中...', forbidClick: true })
  try {
    // 使用申请发起人ID来拒绝申请
    await relationAPI.rejectRelation(request.requesterId)
    closeToast()
    showSuccessToast('已拒绝')
    pendingRequests.value = pendingRequests.value.filter(r => r.id !== request.id)
  } catch (error: any) {
    closeToast()
    showFailToast(error.message || '操作失败')
  }
}

// 删除对象
const handleDelete = (relation: UserRelation): void => {
  currentRelation.value = relation
  showDeleteDialog.value = true
}

const confirmDelete = async (): Promise<void> => {
  if (!currentRelation.value) return

  showLoadingToast({ message: '删除中...', forbidClick: true })
  try {
    // 使用对方用户ID来删除关系
    await relationAPI.deleteRelation(currentRelation.value.otherUserId)
    closeToast()
    showSuccessToast('已删除')
    relations.value = relations.value.filter(r => r.id !== currentRelation.value!.id)
  } catch (error: any) {
    closeToast()
    showFailToast(error.message || '删除失败')
  }
}

// 发起申请
const createApplication = (relation: UserRelation): void => {
  router.push({
    path: '/mobile/applications',
    query: { approverId: relation.otherUserId, approverName: relation.otherUserName || relation.otherUserUsername }
  })
}

// 格式化日期
const formatDate = (date: string): string => {
  if (!date) return '-'
  const d = new Date(date)
  const now = new Date()
  const diff = now.getTime() - d.getTime()

  if (diff < 86400000) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  if (diff < 604800000) {
    return `${Math.floor(diff / 86400000)}天前`
  }
  return d.toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.relations-page {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 60px;
}

.relation-card,
.request-card {
  margin: 12px 16px;
  border-radius: 12px;
  overflow: hidden;
}

.card-content {
  display: flex;
  align-items: center;
  padding: 16px;
  gap: 12px;
}

.avatar {
  flex-shrink: 0;
}

.info {
  flex: 1;
  min-width: 0;
}

.info .name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.info .detail {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #999;
  margin-top: 2px;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 0 16px 16px;
}

.swipe-btn {
  height: 100%;
}

/* 添加对象弹窗 */
.add-dialog {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #eee;
}

.dialog-header .title {
  font-size: 16px;
  font-weight: 600;
}

.searching {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

.search-result {
  padding: 16px 0;
}

.result-card {
  display: flex;
  align-items: center;
  padding: 16px;
  gap: 12px;
}

.result-card .info {
  flex: 1;
}

.result-card .username {
  font-size: 13px;
  color: #999;
  margin-top: 2px;
}

.result-card .phone {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #666;
  margin-top: 4px;
}

.search-tips {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #999;
  text-align: center;
}

.search-tips p {
  margin-top: 16px;
  font-size: 14px;
}

/* 下拉刷新 */
:deep(.van-pull-refresh) {
  min-height: calc(100vh - 90px);
}

:deep(.van-empty) {
  padding-top: 80px;
}

:deep(.van-tabs__content) {
  padding-top: 0;
}
</style>
