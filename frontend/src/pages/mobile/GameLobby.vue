<template>
  <div class="game-lobby">
    <div class="header-section">
      <div class="title">情侣游戏</div>
      <div class="subtitle">选择游戏，创建房间或输入房间号加入</div>
    </div>

    <div class="game-selector" role="tablist" aria-label="游戏类型">
      <button
        v-for="item in gameOptions"
        :key="item.value"
        type="button"
        class="game-option"
        :class="{ active: selectedGame === item.value }"
        @click="selectGame(item.value)"
      >
        <van-icon :name="item.icon" />
        <span>{{ item.label }}</span>
      </button>
    </div>

    <div class="action-section">
      <van-cell-group inset>
        <van-cell
          title="创建房间"
          :label="createLabel"
          is-link
          center
          @click="showCreateDialog = true"
        >
          <template #icon>
            <van-icon name="add-o" size="24" color="#0f766e" class="cell-icon" />
          </template>
        </van-cell>
        <van-cell
          title="加入房间"
          :label="joinLabel"
          is-link
          center
          @click="showJoinDialog = true"
        >
          <template #icon>
            <van-icon name="scan" size="24" color="#2563eb" class="cell-icon" />
          </template>
        </van-cell>
        <van-cell
          v-if="selectedGame === 'flight'"
          title="任务管理"
          label="查看和自定义飞行棋任务"
          is-link
          center
          @click="router.push('/mobile/game/tasks')"
        >
          <template #icon>
            <van-icon name="todo-list-o" size="24" color="#f59e0b" class="cell-icon" />
          </template>
        </van-cell>
      </van-cell-group>
    </div>

    <div class="history-section">
      <div class="section-title">
        <span>{{ historyTitle }}</span>
        <van-tabs v-model:active="activeTab" shrink @change="loadGames">
          <van-tab title="进行中" :name="2" />
          <van-tab title="已结束" :name="3" />
          <van-tab title="全部" :name="0" />
        </van-tabs>
      </div>

      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <div v-if="displayGames.length === 0 && !loading" class="empty-state">
          <van-empty description="暂无游戏记录" />
        </div>

        <div
          v-for="game in displayGames"
          :key="`${selectedGame}-${game.id}`"
          class="game-card"
          @click="goToGame(game)"
        >
          <div class="game-header">
            <div class="tag-row">
              <van-tag :type="getStatusType(game.gameStatus)" size="medium">
                {{ getStatusText(game.gameStatus) }}
              </van-tag>
              <van-tag plain size="medium">{{ getGameTypeLabel(game) }}</van-tag>
            </div>
            <span class="game-code">房间号 {{ game.gameCode }}</span>
          </div>
          <div class="game-players">
            <div class="player">
              <van-image round width="40" height="40" :src="getFirstAvatar(game)" />
              <span class="name">{{ getFirstPlayerName(game) }}</span>
            </div>
            <span class="vs">VS</span>
            <div class="player">
              <van-image round width="40" height="40" :src="getSecondAvatar(game)" />
              <span class="name">{{ getSecondPlayerName(game) }}</span>
            </div>
          </div>
          <div class="game-footer">
            <span class="time">{{ formatTime(game.createdAt) }}</span>
            <span v-if="game.winnerId" class="winner">{{ getWinnerText(game) }}</span>
          </div>
        </div>

        <van-loading v-if="loading" class="list-loading" size="24" />
      </van-pull-refresh>
    </div>

    <van-dialog
      v-model:show="showCreateDialog"
      :title="`创建${selectedGameLabel}房间`"
      show-cancel-button
      :before-close="handleCreateGame"
    >
      <div class="dialog-content">
        <p class="dialog-tip">选择你的对象一起玩{{ selectedGameLabel }}</p>
        <van-field
          v-model="selectedPartnerName"
          readonly
          label="对象"
          placeholder="请选择对象"
          @click="showPartnerPicker = true"
        />
      </div>
    </van-dialog>

    <van-popup v-model:show="showPartnerPicker" position="bottom" round>
      <van-picker
        :columns="partnerColumns"
        @confirm="onPartnerConfirm"
        @cancel="showPartnerPicker = false"
      />
    </van-popup>

    <van-dialog
      v-model:show="showJoinDialog"
      :title="`加入${selectedGameLabel}房间`"
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
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { closeToast, showLoadingToast, showToast } from 'vant'
import dayjs from 'dayjs'
import { relationAPI, type GameInfo } from '@/services/api'
import { GameStatus, useGameStore } from '@/store/modules/game'
import { useGobangStore } from '@/store/modules/gobang'
import { GobangGameStatus, type GobangGame } from '@/services/gobangApi'

type GameKind = 'flight' | 'gobang'
type LobbyGame = GameInfo | GobangGame

interface PartnerOption {
  id: number
  name: string
}

const router = useRouter()
const gameStore = useGameStore()
const gobangStore = useGobangStore()
const defaultAvatar = 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg'

const gameOptions: Array<{ label: string; value: GameKind; icon: string }> = [
  { label: '飞行棋', value: 'flight', icon: 'guide-o' },
  { label: '五子棋', value: 'gobang', icon: 'cluster-o' }
]

const selectedGame = ref<GameKind>('flight')
const activeTab = ref(2)
const flightGames = ref<GameInfo[]>([])
const gobangGames = ref<GobangGame[]>([])
const loading = ref(false)
const refreshing = ref(false)

const showCreateDialog = ref(false)
const selectedPartnerId = ref<number | null>(null)
const selectedPartnerName = ref('')
const showPartnerPicker = ref(false)
const partnerList = ref<PartnerOption[]>([])

const showJoinDialog = ref(false)
const joinGameCode = ref('')

const selectedGameLabel = computed(() => selectedGame.value === 'flight' ? '飞行棋' : '五子棋')
const createLabel = computed(() => {
  return selectedGame.value === 'flight' ? '邀请对象一起玩飞行棋' : '邀请对象执黑白对弈'
})
const joinLabel = computed(() => {
  return selectedGame.value === 'flight' ? '输入飞行棋房间号' : '输入五子棋房间号'
})
const historyTitle = computed(() => `${selectedGameLabel.value}记录`)
const displayGames = computed<LobbyGame[]>(() => {
  return selectedGame.value === 'flight' ? flightGames.value : gobangGames.value
})

const partnerColumns = computed(() => {
  return partnerList.value.map(partner => ({
    text: partner.name,
    value: partner.id
  }))
})

function selectGame(kind: GameKind): void {
  if (selectedGame.value === kind) return
  selectedGame.value = kind
  void loadGames()
}

function isGobangGame(game: LobbyGame): game is GobangGame {
  return 'blackPlayerId' in game
}

function getStatusType(status: number): 'primary' | 'success' | 'warning' | 'danger' {
  switch (status) {
    case GameStatus.WAITING:
    case GobangGameStatus.WAITING:
      return 'warning'
    case GameStatus.PLAYING:
    case GobangGameStatus.PLAYING:
      return 'primary'
    case GameStatus.FINISHED:
    case GobangGameStatus.FINISHED:
      return 'success'
    case GameStatus.CANCELLED:
    case GobangGameStatus.CANCELLED:
      return 'danger'
    default:
      return 'primary'
  }
}

function getStatusText(status: number): string {
  switch (status) {
    case GameStatus.WAITING:
    case GobangGameStatus.WAITING:
      return '等待加入'
    case GameStatus.PLAYING:
    case GobangGameStatus.PLAYING:
      return '游戏中'
    case GameStatus.FINISHED:
    case GobangGameStatus.FINISHED:
      return '已结束'
    case GameStatus.CANCELLED:
    case GobangGameStatus.CANCELLED:
      return '已取消'
    default:
      return '未知'
  }
}

function getGameTypeLabel(game: LobbyGame): string {
  return isGobangGame(game) ? '五子棋' : '飞行棋'
}

function getFirstPlayerName(game: LobbyGame): string {
  return isGobangGame(game) ? (game.blackPlayerName || '黑棋') : (game.player1Name || '玩家1')
}

function getSecondPlayerName(game: LobbyGame): string {
  if (isGobangGame(game)) {
    return game.whitePlayerName || game.invitedPlayerName || '等待中'
  }
  return game.player2Name || '等待中'
}

function getFirstAvatar(game: LobbyGame): string {
  return isGobangGame(game)
    ? (game.blackPlayerAvatar || defaultAvatar)
    : (game.player1Avatar || defaultAvatar)
}

function getSecondAvatar(game: LobbyGame): string {
  if (isGobangGame(game)) {
    return game.whitePlayerAvatar || game.invitedPlayerAvatar || defaultAvatar
  }
  return game.player2Avatar || defaultAvatar
}

function getWinnerText(game: LobbyGame): string {
  if (!game.winnerId) return ''

  if (isGobangGame(game)) {
    const winner = game.winnerId === game.blackPlayerId
      ? game.blackPlayerName
      : game.whitePlayerName
    return `获胜者 ${winner || '玩家'}`
  }

  const winner = game.winnerId === game.player1Id ? game.player1Name : game.player2Name
  return `获胜者 ${winner || '玩家'}`
}

function formatTime(time: string): string {
  return dayjs(time).format('MM-DD HH:mm')
}

async function loadGames(): Promise<void> {
  loading.value = true
  try {
    const status = activeTab.value === 0 ? undefined : activeTab.value
    if (selectedGame.value === 'flight') {
      await gameStore.fetchGameHistory(status)
      flightGames.value = gameStore.gameHistory
    } else {
      await gobangStore.fetchGameHistory(status)
      gobangGames.value = gobangStore.gameHistory
    }
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '加载失败' })
  } finally {
    loading.value = false
  }
}

async function onRefresh(): Promise<void> {
  await loadGames()
  refreshing.value = false
}

async function loadPartners(): Promise<void> {
  try {
    const response: any = await relationAPI.getMyRelations({ pageNum: 1, pageSize: 10 })
    const records = response?.records || []
    partnerList.value = records.map((relation: any) => ({
      id: relation.otherUserId,
      name: relation.otherUserName || relation.otherUserUsername || '对象'
    }))
  } catch {
    showToast({ type: 'fail', message: '对象列表加载失败' })
  }
}

function onPartnerConfirm(value: { selectedOptions: Array<{ text: string; value: number }> }): void {
  const selected = value.selectedOptions[0]
  if (selected) {
    selectedPartnerId.value = selected.value
    selectedPartnerName.value = selected.text
  }
  showPartnerPicker.value = false
}

async function handleCreateGame(action: string): Promise<boolean> {
  if (action !== 'confirm') return true

  if (!selectedPartnerId.value) {
    showToast({ type: 'fail', message: '请选择对象' })
    return false
  }

  showLoadingToast({ message: '创建中...', forbidClick: true })
  try {
    const targetRoute = selectedGame.value === 'flight'
      ? `/mobile/game/room/${(await gameStore.createGame(selectedPartnerId.value)).id}`
      : `/mobile/gobang/room/${(await gobangStore.createGame(selectedPartnerId.value)).id}`

    closeToast()
    showToast({ type: 'success', message: '房间创建成功' })
    resetCreateForm()
    router.push(targetRoute)
    return true
  } catch (error: any) {
    closeToast()
    showToast({ type: 'fail', message: error.response?.data?.message || error.message || '创建失败' })
    return false
  }
}

async function handleJoinGame(action: string): Promise<boolean> {
  if (action !== 'confirm') return true

  if (!joinGameCode.value || joinGameCode.value.length !== 6) {
    showToast({ type: 'fail', message: '请输入6位房间号' })
    return false
  }

  showLoadingToast({ message: '加入中...', forbidClick: true })
  try {
    const targetRoute = selectedGame.value === 'flight'
      ? `/mobile/game/room/${(await gameStore.joinGame(joinGameCode.value)).id}`
      : `/mobile/gobang/room/${(await gobangStore.joinGame(joinGameCode.value)).id}`

    closeToast()
    showToast({ type: 'success', message: '加入成功' })
    joinGameCode.value = ''
    router.push(targetRoute)
    return true
  } catch (error: any) {
    closeToast()
    showToast({ type: 'fail', message: error.response?.data?.message || error.message || '加入失败' })
    return false
  }
}

function resetCreateForm(): void {
  selectedPartnerId.value = null
  selectedPartnerName.value = ''
}

function goToGame(game: LobbyGame): void {
  if (isGobangGame(game)) {
    router.push(`/mobile/gobang/room/${game.id}`)
    return
  }
  router.push(`/mobile/game/room/${game.id}`)
}

onMounted(() => {
  void loadGames()
  void loadPartners()
})
</script>

<style scoped lang="scss">
.game-lobby {
  min-height: 100vh;
  padding-bottom: 60px;
  background-color: #f7f8fa;
}

.header-section {
  padding: 34px 20px 46px;
  text-align: center;
  color: #fff;
  background: linear-gradient(135deg, #0f766e 0%, #2563eb 100%);

  .title {
    margin-bottom: 8px;
    font-size: 28px;
    font-weight: 700;
  }

  .subtitle {
    font-size: 14px;
    opacity: 0.92;
  }
}

.game-selector {
  margin: -24px 12px 12px;
  padding: 4px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.08);
}

.game-option {
  min-width: 0;
  height: 44px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #4b5563;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 14px;

  &.active {
    color: #fff;
    background: #0f766e;
    font-weight: 600;
  }
}

.action-section {
  padding: 0 12px;

  .cell-icon {
    margin-right: 12px;
  }
}

.history-section {
  padding: 16px 12px;
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;

  > span {
    font-size: 16px;
    font-weight: 600;
    color: #111827;
  }

  :deep(.van-tabs) {
    width: auto;
  }

  :deep(.van-tabs__nav) {
    background: transparent;
  }
}

.empty-state {
  padding: 34px 0;
}

.game-card {
  margin-bottom: 12px;
  padding: 14px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 8px rgba(15, 23, 42, 0.06);
}

.game-header,
.game-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.tag-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.game-code,
.time {
  color: #6b7280;
  font-size: 12px;
}

.game-players {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  margin: 16px 0;
}

.player {
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;

  .name {
    max-width: 112px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: #323233;
    font-size: 14px;
  }
}

.vs {
  color: #f59e0b;
  font-size: 18px;
  font-weight: 700;
}

.winner {
  color: #0f766e;
  font-size: 12px;
}

.list-loading {
  display: flex;
  justify-content: center;
  padding: 18px 0;
}

.dialog-content {
  padding: 16px;

  .dialog-tip {
    margin: 0 0 16px;
    text-align: center;
    color: #6b7280;
    font-size: 14px;
  }
}
</style>
