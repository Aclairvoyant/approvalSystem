<template>
  <div class="relations-page">
    <van-nav-bar title="我的对象" left-arrow fixed placeholder @click-left="$router.back()">
      <template #right>
        <van-icon name="plus" size="20" aria-label="添加对象" @click="openAdd" />
      </template>
    </van-nav-bar>

    <van-tabs v-model:active="activeTab" sticky offset-top="46">
      <van-tab title="我的对象" :name="0">
        <van-pull-refresh v-model="relationsList.refreshing.value" class="m-page" @refresh="relationsList.refresh">
          <van-list
            v-model:loading="relationsList.loading.value"
            v-model:error="relationsList.error.value"
            :finished="relationsList.finished.value"
            error-text="加载失败，点击重试"
            @load="relationsList.load"
          >
            <van-empty
              v-if="relationsList.finished.value && relationsList.list.value.length === 0"
              class="m-empty"
              description="还没有对象。添加对方后，你们就可以互相发起申请"
            >
              <van-button type="primary" size="small" @click="openAdd">添加对象</van-button>
            </van-empty>

            <van-swipe-cell v-for="relation in relationsList.list.value" :key="relation.id" class="m-card">
              <div class="person-row">
                <UserAvatar :src="relation.otherUserAvatar" :name="relation.otherUserName || relation.otherUserUsername" :size="44" />
                <div class="person-info">
                  <div class="m-title">{{ relation.otherUserName || relation.otherUserUsername }}</div>
                  <div class="m-meta">
                    <span v-if="relation.otherUserUsername">@{{ relation.otherUserUsername }}</span>
                    <span v-if="relation.otherUserPhone">{{ relation.otherUserPhone }}</span>
                  </div>
                </div>
                <van-button size="small" plain type="primary" @click="createApplication(relation)">发起申请</van-button>
              </div>
              <template #right>
                <van-button square type="danger" text="解除" class="m-swipe-btn" @click="handleDelete(relation)" />
              </template>
            </van-swipe-cell>
          </van-list>
        </van-pull-refresh>
      </van-tab>

      <van-tab title="待处理" :name="1" :badge="pendingList.total.value || ''">
        <van-pull-refresh v-model="pendingList.refreshing.value" class="m-page" @refresh="pendingList.refresh">
          <van-list
            v-model:loading="pendingList.loading.value"
            v-model:error="pendingList.error.value"
            :finished="pendingList.finished.value"
            error-text="加载失败，点击重试"
            @load="pendingList.load"
          >
            <van-empty
              v-if="pendingList.finished.value && pendingList.list.value.length === 0"
              class="m-empty"
              description="没有待处理的对象请求"
            />

            <div v-for="request in pendingList.list.value" :key="request.id" class="m-card person-row">
              <UserAvatar :src="request.requesterAvatar" :name="request.requesterName || request.requesterUsername" :size="44" />
              <div class="person-info">
                <div class="m-title">{{ request.requesterName || request.requesterUsername }}</div>
                <div class="m-meta">
                  <span>想和你成为对象</span>
                  <span>{{ relativeTime(request.createdAt) }}</span>
                </div>
              </div>
              <div class="request-actions">
                <van-button size="small" plain :loading="handlingId === request.id && handlingAction === 'reject'" @click="handleReject(request)">
                  拒绝
                </van-button>
                <van-button size="small" type="primary" :loading="handlingId === request.id && handlingAction === 'accept'" @click="handleAccept(request)">
                  接受
                </van-button>
              </div>
            </div>
          </van-list>
        </van-pull-refresh>
      </van-tab>
    </van-tabs>

    <van-popup v-model:show="showAddDialog" position="bottom" round :style="{ height: '60%' }">
      <div class="add-sheet">
        <div class="m-sheet-title">添加对象</div>

        <van-search
          v-model="searchKeyword"
          placeholder="对方的用户名或手机号"
          shape="round"
          @search="handleSearch"
          @update:model-value="searched = false"
        />

        <div v-if="searching" class="sheet-state">
          <van-loading size="24">搜索中...</van-loading>
        </div>

        <div v-else-if="searchResult" class="m-card person-row result-row">
          <UserAvatar :src="searchResult.avatar" :name="searchResult.realName || searchResult.username" :size="48" />
          <div class="person-info">
            <div class="m-title">{{ searchResult.realName || searchResult.username }}</div>
            <div class="m-meta">
              <span>@{{ searchResult.username }}</span>
              <span v-if="searchResult.phone">{{ searchResult.phone }}</span>
            </div>
          </div>
          <van-button type="primary" size="small" :loading="sending" @click="sendRequest">发送请求</van-button>
        </div>

        <p v-else-if="searched" class="sheet-state">没有找到这个用户，请检查用户名或手机号</p>

        <p v-else class="sheet-state">对方同意后，你们就可以互相发起申请</p>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { relationAPI, type SearchUserResult, type UserRelation } from '@/services/api'
import { usePagedList } from '@/composables/usePagedList'
import { relativeTime } from '@/utils/format'
import UserAvatar from '@/components/UserAvatar.vue'

const router = useRouter()
const activeTab = ref(0)

const relationsList = usePagedList<UserRelation>((pageNum, pageSize) =>
  relationAPI.getMyRelations({ pageNum, pageSize })
)
const pendingList = usePagedList<UserRelation>((pageNum, pageSize) =>
  relationAPI.getPendingRequests({ pageNum, pageSize })
)

const showAddDialog = ref(false)
const searchKeyword = ref('')
const searchResult = ref<SearchUserResult | null>(null)
const searched = ref(false)
const searching = ref(false)
const sending = ref(false)
const handlingId = ref<number | null>(null)
const handlingAction = ref<'accept' | 'reject' | null>(null)

const openAdd = (): void => {
  searchKeyword.value = ''
  searchResult.value = null
  searched.value = false
  showAddDialog.value = true
}

const handleSearch = async (): Promise<void> => {
  const keyword = searchKeyword.value.trim()
  if (!keyword) {
    showFailToast('请输入用户名或手机号')
    return
  }

  searching.value = true
  searchResult.value = null
  try {
    searchResult.value = await relationAPI.searchUser(keyword)
  } catch (error: any) {
    if (error.message !== '未找到该用户') {
      showFailToast(error.message || '搜索失败')
    }
  } finally {
    searching.value = false
    searched.value = true
  }
}

const sendRequest = async (): Promise<void> => {
  if (!searchResult.value) return

  sending.value = true
  try {
    await relationAPI.initiateRelation(searchResult.value.id)
    showSuccessToast('请求已发送')
    showAddDialog.value = false
  } catch (error: any) {
    showFailToast(error.message || '发送失败')
  } finally {
    sending.value = false
  }
}

const handleAccept = async (request: UserRelation): Promise<void> => {
  handlingId.value = request.id
  handlingAction.value = 'accept'
  try {
    // 接受和拒绝都以发起人的 ID 定位这条请求
    await relationAPI.acceptRelation(request.requesterId)
    showSuccessToast('已接受')
    pendingList.refresh()
    relationsList.refresh()
    activeTab.value = 0
  } catch (error: any) {
    showFailToast(error.message || '操作失败')
  } finally {
    handlingId.value = null
    handlingAction.value = null
  }
}

const handleReject = async (request: UserRelation): Promise<void> => {
  handlingId.value = request.id
  handlingAction.value = 'reject'
  try {
    await relationAPI.rejectRelation(request.requesterId)
    showSuccessToast('已拒绝')
    pendingList.refresh()
  } catch (error: any) {
    showFailToast(error.message || '操作失败')
  } finally {
    handlingId.value = null
    handlingAction.value = null
  }
}

const handleDelete = async (relation: UserRelation): Promise<void> => {
  const name = relation.otherUserName || relation.otherUserUsername || '对方'
  try {
    await showConfirmDialog({
      title: '解除对象关系',
      message: `解除后你和${name}将不能再互相发起申请。`,
      confirmButtonText: '解除',
      confirmButtonColor: 'var(--danger)',
    })
  } catch {
    return
  }

  try {
    // 删除关系用的是对方的用户 ID
    await relationAPI.deleteRelation(relation.otherUserId)
    showSuccessToast('已解除')
    relationsList.refresh()
  } catch (error: any) {
    showFailToast(error.message || '解除失败')
  }
}

const createApplication = (relation: UserRelation): void => {
  router.push({
    path: '/mobile/applications',
    query: {
      approverId: String(relation.otherUserId),
      approverName: relation.otherUserName || relation.otherUserUsername || '',
    },
  })
}
</script>

<style scoped>
.relations-page {
  min-height: 100%;
  background: var(--bg);
}

.m-card + .m-card,
:deep(.van-swipe-cell + .van-swipe-cell) {
  margin-top: 8px;
}

:deep(.van-swipe-cell) {
  overflow: hidden;
}

.person-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
}

.person-info {
  flex: 1;
  min-width: 0;
}

.m-meta {
  margin-top: 2px;
}

.request-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.add-sheet {
  padding-bottom: env(safe-area-inset-bottom);
}

.result-row {
  margin: 8px 12px 0;
  border: 1px solid var(--rule);
}

.sheet-state {
  padding: 32px 24px;
  color: var(--ink-3);
  font-size: 14px;
  text-align: center;
}
</style>
