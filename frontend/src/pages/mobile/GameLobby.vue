<template>
  <div class="game-lobby">
    <!-- 顶部区域 -->
    <div class="header-section">
      <div class="title">飞行棋游戏</div>
      <div class="subtitle">和TA一起玩，感情升温</div>
    </div>

    <!-- 操作区域 -->
    <div class="action-section">
      <van-cell-group inset>
        <van-cell
          title="创建房间"
          label="邀请你的对象一起玩"
          is-link
          center
          @click="showCreateDialog = true"
        >
          <template #icon>
            <van-icon name="add-o" size="24" color="#1989fa" class="cell-icon" />
          </template>
        </van-cell>
        <van-cell
          title="加入房间"
          label="输入房间号加入游戏"
          is-link
          center
          @click="showJoinDialog = true"
        >
          <template #icon>
            <van-icon name="scan" size="24" color="#07c160" class="cell-icon" />
          </template>
        </van-cell>
        <van-cell
          title="任务管理"
          label="查看和自定义游戏任务"
          is-link
          center
          @click="router.push('/mobile/game/tasks')"
        >
          <template #icon>
            <van-icon name="todo-list-o" size="24" color="#ff976a" class="cell-icon" />
          </template>
        </van-cell>
      </van-cell-group>
    </div>

    <!-- 游戏历史 -->
    <div class="history-section">
      <div class="section-title">
        <span>游戏记录</span>
        <van-tabs v-model:active="activeTab" shrink @change="loadGames">
          <van-tab title="进行中" :name="2" />
          <van-tab title="已结束" :name="3" />
          <van-tab title="全部" :name="0" />
        </van-tabs>
      </div>

      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <van-list
          v-model:loading="loading"
          :finished="finished"
          finished-text="没有更多了"
          @load="loadMore"
        >
          <div v-if="gameList.length === 0 && !loading" class="empty-state">
            <van-empty description="暂无游戏记录" />
          </div>

          <div v-for="game in gameList" :key="game.id" class="game-card" @click="goToGame(game)">
            <div class="game-header">
              <van-tag :type="getStatusType(game.gameStatus)" size="medium">
                {{ getStatusText(game.gameStatus) }}
              </van-tag>
              <span class="game-code">房间号: {{ game.gameCode }}</span>
            </div>
            <div class="game-players">
              <div class="player">
                <van-image
                  round
                  width="40"
                  height="40"
                  :src="game.player1Avatar || defaultAvatar"
                />
                <span class="name">{{ game.player1Name || '玩家1' }}</span>
              </div>
              <span class="vs">VS</span>
              <div class="player">
                <van-image
                  round
                  width="40"
                  height="40"
                  :src="game.player2Avatar || defaultAvatar"
                />
                <span class="name">{{ game.player2Name || '等待中...' }}</span>
              </div>
            </div>
            <div class="game-footer">
              <span class="time">{{ formatTime(game.createdAt) }}</span>
              <span v-if="game.winnerId" class="winner">
                获胜者: {{ game.winnerId === game.player1Id ? game.player1Name : game.player2Name }}
              </span>
            </div>
          </div>
        </van-list>
      </van-pull-refresh>
    </div>

    <!-- 创建房间弹窗 -->
    <van-dialog
      v-model:show="showCreateDialog"
      title="创建游戏房间"
      show-cancel-button
      :before-close="handleCreateGame"
    >
      <div class="dialog-content">
        <p class="dialog-tip">选择你的对象一起玩游戏</p>
        <van-field
          v-model="selectedPartnerName"
          readonly
          label="对象"
          placeholder="请选择对象"
          @click="showPartnerPicker = true"
        />
      </div>
    </van-dialog>

    <!-- 选择对象弹窗 -->
    <van-popup v-model:show="showPartnerPicker" position="bottom" round>
      <van-picker
        :columns="partnerColumns"
        @confirm="onPartnerConfirm"
        @cancel="showPartnerPicker = false"
      />
    </van-popup>

    <!-- 加入房间弹窗 -->
    <van-dialog
      v-model:show="showJoinDialog"
      title="加入游戏房间"
      show-cancel-button
      :before-close="handleJoinGame"
    >
      <div class="dialog-content">
        <van-field
          v-model="joinGameCode"
          label="房间号"
          placeholder="请输入6位房间号"
          maxlength="6"
          type="number"
        />
      </div>
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showLoadingToast, closeToast } from 'vant'
import { useGameStore, GameStatus } from '@/store/modules/game'
import { relationAPI } from '@/services/api'
import type { GameInfo } from '@/services/api'
import dayjs from 'dayjs'

const router = useRouter()
const gameStore = useGameStore()

// 默认头像
const defaultAvatar = 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg'

// 状态
const activeTab = ref(2)  // 默认显示进行中
const gameList = ref<GameInfo[]>([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const pageNum = ref(1)
const pageSize = 10

// 创建房间相关
const showCreateDialog = ref(false)
const selectedPartnerId = ref<number | null>(null)
const selectedPartnerName = ref('')
const showPartnerPicker = ref(false)
const partnerList = ref<Array<{ id: number; name: string }>>([])

// 加入房间相关
const showJoinDialog = ref(false)
const joinGameCode = ref('')

// 计算属性
const partnerColumns = computed(() => {
  return partnerList.value.map(p => ({
    text: p.name,
    value: p.id
  }))
})

// 方法
function getStatusType(status: number): 'primary' | 'success' | 'warning' | 'danger' {
  switch (status) {
    case GameStatus.WAITING: return 'warning'
    case GameStatus.PLAYING: return 'primary'
    case GameStatus.FINISHED: return 'success'
    case GameStatus.CANCELLED: return 'danger'
    default: return 'primary'
  }
}

function getStatusText(status: number): string {
  switch (status) {
    case GameStatus.WAITING: return '等待加入'
    case GameStatus.PLAYING: return '游戏中'
    case GameStatus.FINISHED: return '已结束'
    case GameStatus.CANCELLED: return '已取消'
    default: return '未知'
  }
}

function formatTime(time: string): string {
  return dayjs(time).format('MM-DD HH:mm')
}

async function loadGames() {
  pageNum.value = 1
  finished.value = false
  gameList.value = []
  await loadMore()
}

async function loadMore() {
  if (loading.value || finished.value) return

  loading.value = true
  try {
    const status = activeTab.value === 0 ? undefined : activeTab.value
    const response = await gameStore.fetchGameHistory(status)
    const newGames = gameStore.gameHistory

    if (pageNum.value === 1) {
      gameList.value = newGames
    } else {
      gameList.value = [...gameList.value, ...newGames]
    }

    if (newGames.length < pageSize) {
      finished.value = true
    } else {
      pageNum.value++
    }
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '加载失败' })
  } finally {
    loading.value = false
  }
}

async function onRefresh() {
  await loadGames()
  refreshing.value = false
}

async function loadPartners() {
  try {
    const response: any = await relationAPI.getMyRelations({ pageNum: 1, pageSize: 10 })
    // http拦截器已解包，直接访问records
    const records = response?.records || []
    partnerList.value = records.map((r: any) => ({
      id: r.otherUserId,
      name: r.otherUserName || r.otherUserUsername
    }))
  } catch (error) {
    console.error('加载对象列表失败', error)
  }
}

function onPartnerConfirm(value: { selectedOptions: Array<{ text: string; value: number }> }) {
  console.error(value.selectedOptions.length)
  if (value.selectedOptions && value.selectedOptions.length > 0) {

    selectedPartnerId.value = value.selectedOptions[0].value
    selectedPartnerName.value = value.selectedOptions[0].text
  }
  showPartnerPicker.value = false
}

async function handleCreateGame(action: string): Promise<boolean> {
  if (action === 'confirm') {
    if (!selectedPartnerId.value) {
      showToast({ type: 'fail', message: '请选择对象' })
      return false
    }

    const toast = showLoadingToast({ message: '创建中...', forbidClick: true })
    try {
      const game = await gameStore.createGame(selectedPartnerId.value)
      closeToast()
      showToast({ type: 'success', message: '房间创建成功' })

      // 重置表单
      selectedPartnerId.value = null
      selectedPartnerName.value = ''

      // 跳转到游戏房间
      router.push(`/mobile/game/room/${game.id}`)
      return true
    } catch (error: any) {
      closeToast()
      showToast({ type: 'fail', message: error.response?.data?.message || '创建失败' })
      return false
    }
  }
  return true
}

async function handleJoinGame(action: string): Promise<boolean> {
  if (action === 'confirm') {
    if (!joinGameCode.value || joinGameCode.value.length !== 6) {
      showToast({ type: 'fail', message: '请输入6位房间号' })
      return false
    }

    const toast = showLoadingToast({ message: '加入中...', forbidClick: true })
    try {
      const game = await gameStore.joinGame(joinGameCode.value)
      closeToast()
      showToast({ type: 'success', message: '加入成功' })

      // 重置表单
      joinGameCode.value = ''

      // 跳转到游戏房间
      router.push(`/mobile/game/room/${game.id}`)
      return true
    } catch (error: any) {
      closeToast()
      showToast({ type: 'fail', message: error.response?.data?.message || '加入失败' })
      return false
    }
  }
  return true
}

function goToGame(game: GameInfo) {
  if (game.gameStatus === GameStatus.WAITING || game.gameStatus === GameStatus.PLAYING) {
    router.push(`/mobile/game/room/${game.id}`)
  } else {
    // 已结束的游戏可以查看详情
    router.push(`/mobile/game/room/${game.id}`)
  }
}

onMounted(() => {
  loadGames()
  loadPartners()
})
</script>

<style scoped lang="scss">
.game-lobby {
  min-height: 100vh;
  background-color: #f7f8fa;
  padding-bottom: 60px;
}

.header-section {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 40px 20px;
  text-align: center;
  color: white;

  .title {
    font-size: 28px;
    font-weight: bold;
    margin-bottom: 8px;
  }

  .subtitle {
    font-size: 14px;
    opacity: 0.9;
  }
}

.action-section {
  margin-top: -20px;
  padding: 0 12px;

  .cell-icon {
    margin-right: 12px;
  }
}

.history-section {
  padding: 16px 12px;

  .section-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;

    span {
      font-size: 16px;
      font-weight: 500;
    }

    :deep(.van-tabs) {
      width: auto;
    }

    :deep(.van-tabs__nav) {
      background: transparent;
    }
  }
}

.empty-state {
  padding: 40px 0;
}

.game-card {
  background: white;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);

  .game-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .game-code {
      font-size: 12px;
      color: #969799;
    }
  }

  .game-players {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 20px;

    .player {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;

      .name {
        font-size: 14px;
        color: #323233;
      }
    }

    .vs {
      font-size: 18px;
      font-weight: bold;
      color: #ff976a;
    }
  }

  .game-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 16px;
    font-size: 12px;
    color: #969799;

    .winner {
      color: #07c160;
    }
  }
}

.dialog-content {
  padding: 16px;

  .dialog-tip {
    font-size: 14px;
    color: #969799;
    margin-bottom: 16px;
    text-align: center;
  }
}
</style>
