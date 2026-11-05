<template>
  <div class="game-lobby">
    <header class="header-section">
      <h1 class="title">游戏</h1>
      <p class="subtitle">{{ subtitle }}</p>
    </header>

    <div class="game-selector" role="tablist" aria-label="游戏类型">
      <button
        v-for="item in gameOptions"
        :key="item.value"
        type="button"
        class="game-option"
        :class="{ active: selectedGame === item.value }"
        @click="selectGame(item.value)"
      >
        {{ item.label }}
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
            <van-icon name="add-o" size="22" class="cell-icon" />
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
            <van-icon name="enlarge" size="22" class="cell-icon" />
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
            <van-icon name="todo-list-o" size="22" class="cell-icon" />
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
        <van-empty v-if="displayGames.length === 0 && !loading" class="m-empty" description="还没有对局记录" />

        <div
          v-for="game in displayGames"
          :key="`${selectedGame}-${game.id}`"
          class="m-card game-card"
          @click="goToGame(game)"
        >
          <div class="game-header">
            <span class="game-status" :class="`is-${getStatusTone(game.gameStatus)}`">{{ getStatusText(game) }}</span>
            <span class="game-code tabular">房间号 {{ game.gameCode }}</span>
          </div>
          <template v-if="isMahjongRoom(game)">
            <p class="mj-summary">
              {{ game.modeName }} · {{ game.seatCount }} 人桌<template v-if="game.roundNo"> · 第 {{ game.roundNo }} 局</template>
              <template v-if="isMahjongInvite(game)"> · 邀请你加入</template>
            </p>
            <div class="mj-players">
              <div v-for="seat in game.seats" :key="seat.seatIndex" class="mj-player">
                <UserAvatar v-if="seat.type === 'HUMAN'" :src="seat.avatar" :name="seat.name" :size="32" />
                <span v-else class="mj-seat-mark" :class="{ bot: seat.type === 'BOT' }">{{ seat.type === 'BOT' ? '电' : '' }}</span>
                <span class="name">{{ seat.type === 'EMPTY' ? '空位' : seat.name }}</span>
                <span v-if="game.roundNo" class="score tabular">{{ signedScore(seat.totalScore ?? 0) }}</span>
              </div>
            </div>
            <div class="game-footer">
              <span class="time">{{ formatTime(game.createdAt) }}</span>
              <span v-if="game.capPoints" class="winner">封顶 {{ game.capPoints }}</span>
              <span v-else class="winner">不封顶</span>
            </div>
          </template>
          <template v-else>
          <div class="game-players">
            <div class="player">
              <UserAvatar :src="getFirstAvatar(game)" :name="getFirstPlayerName(game)" :size="36" />
              <span class="name">{{ getFirstPlayerName(game) }}</span>
            </div>
            <span class="vs">VS</span>
            <div class="player">
              <UserAvatar :src="getSecondAvatar(game)" :name="getSecondPlayerName(game)" :size="36" />
              <span class="name">{{ getSecondPlayerName(game) }}</span>
            </div>
          </div>
          <div class="game-footer">
            <span class="time">{{ formatTime(game.createdAt) }}</span>
            <span v-if="game.winnerId" class="winner">{{ getWinnerText(game) }}</span>
          </div>
          </template>
        </div>

        <van-loading v-if="loading" class="list-loading" size="24" />
      </van-pull-refresh>
    </div>

    <van-dialog
      v-model:show="showCreateDialog"
      :title="`创建${selectedGameLabel}房间`"
      show-cancel-button
      confirm-button-text="创建"
      :before-close="handleCreateGame"
    >
      <div v-if="selectedGame === 'mahjong'" class="dialog-content mj-create">
        <p class="dialog-tip">房间号发给谁都能加入，空位可以设成电脑</p>
        <div class="mj-field">
          <span class="mj-field-label">玩法</span>
          <van-radio-group v-model="mahjongMode" direction="horizontal">
            <van-radio v-for="item in MAHJONG_MODE_OPTIONS" :key="item.value" :name="item.value">{{ item.label }}</van-radio>
          </van-radio-group>
        </div>
        <p class="mj-field-hint">{{ mahjongModeHint }}</p>
        <div class="mj-field">
          <span class="mj-field-label">座位</span>
          <van-radio-group v-model="mahjongSeats" direction="horizontal">
            <van-radio v-for="count in [2, 3, 4]" :key="count" :name="count">{{ count }} 人</van-radio>
          </van-radio-group>
        </div>
        <div class="mj-field">
          <span class="mj-field-label">封顶</span>
          <van-radio-group v-model="mahjongCap" direction="horizontal" class="mj-cap">
            <van-radio v-for="item in MAHJONG_CAP_OPTIONS" :key="item.value" :name="item.value">{{ item.label }}</van-radio>
          </van-radio-group>
        </div>
        <p class="mj-field-hint">每个付分的人每局最多付这么多分</p>
        <van-checkbox v-if="partnerList.length" v-model="mahjongInvitePartner" class="mj-invite" shape="square">
          同时邀请{{ partnerList[0].name }}
        </van-checkbox>
      </div>
      <div v-else class="dialog-content">
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
          type="digit"
        />
      </div>
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { closeToast, showLoadingToast, showToast } from 'vant'
import { relationAPI, type GameInfo } from '@/services/api'
import { formatMonthDayTime } from '@/utils/format'
import UserAvatar from '@/components/UserAvatar.vue'
import { GameStatus, useGameStore } from '@/store/modules/game'
import { useGobangStore } from '@/store/modules/gobang'
import { useMahjongStore } from '@/store/modules/mahjong'
import { GobangGameStatus, type GobangGame } from '@/services/gobangApi'
import {
  DEFAULT_CAP_POINTS,
  MAHJONG_CAP_OPTIONS,
  MAHJONG_MODE_OPTIONS,
  MahjongRoomStatus,
  type MahjongMode,
  type MahjongRoom
} from '@/services/mahjongApi'
import { useUserStore } from '@/store/modules/user'

type GameKind = 'flight' | 'gobang' | 'mahjong'
type LobbyGame = GameInfo | GobangGame | MahjongRoom

interface PartnerOption {
  id: number
  name: string
}

const router = useRouter()
const gameStore = useGameStore()
const gobangStore = useGobangStore()
const mahjongStore = useMahjongStore()
const userStore = useUserStore()

const gameOptions: Array<{ label: string; value: GameKind }> = [
  { label: '飞行棋', value: 'flight' },
  { label: '五子棋', value: 'gobang' },
  { label: '上海麻将', value: 'mahjong' }
]

const GAME_LABELS: Record<GameKind, string> = {
  flight: '飞行棋',
  gobang: '五子棋',
  mahjong: '上海麻将'
}

const selectedGame = ref<GameKind>('flight')
const activeTab = ref(2)
const flightGames = ref<GameInfo[]>([])
const gobangGames = ref<GobangGame[]>([])
const mahjongGames = ref<MahjongRoom[]>([])

const mahjongMode = ref<MahjongMode>('QIAOMA')
const mahjongSeats = ref(4)
const mahjongCap = ref(DEFAULT_CAP_POINTS)
const mahjongInvitePartner = ref(false)
const mahjongModeHint = computed(() => MAHJONG_MODE_OPTIONS.find(item => item.value === mahjongMode.value)?.hint ?? '')
const loading = ref(false)
const refreshing = ref(false)

const showCreateDialog = ref(false)
const selectedPartnerId = ref<number | null>(null)
const selectedPartnerName = ref('')
const showPartnerPicker = ref(false)
const partnerList = ref<PartnerOption[]>([])

const showJoinDialog = ref(false)
const joinGameCode = ref('')

const selectedGameLabel = computed(() => GAME_LABELS[selectedGame.value])
const subtitle = computed(() => {
  return selectedGame.value === 'mahjong'
    ? '开房后把房间号发给朋友，谁都能加入，空位可以设成电脑'
    : '和对象一起玩，开房间后把房间号发给对方'
})
const createLabel = computed(() => {
  if (selectedGame.value === 'mahjong') return '百搭或敲麻，2-4 人，电脑可以补位'
  return selectedGame.value === 'flight' ? '邀请对象一起玩飞行棋' : '邀请对象执黑白对弈'
})
const joinLabel = computed(() => {
  if (selectedGame.value === 'mahjong') return '输入麻将房间号，不用互为对象'
  return selectedGame.value === 'flight' ? '输入飞行棋房间号' : '输入五子棋房间号'
})
const historyTitle = computed(() => `${selectedGameLabel.value}记录`)
const displayGames = computed<LobbyGame[]>(() => {
  if (selectedGame.value === 'mahjong') return mahjongGames.value
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

function isMahjongRoom(game: LobbyGame): game is MahjongRoom {
  return 'seatCount' in game && 'modeName' in game
}

function isMahjongInvite(game: MahjongRoom): boolean {
  return game.gameStatus === MahjongRoomStatus.WAITING &&
    game.invitedUserId === userStore.userId &&
    !game.seats.some(seat => seat.userId === userStore.userId)
}

function signedScore(value: number): string {
  return value > 0 ? `+${value}` : String(value)
}

// 两种游戏的状态码一致：1 等待、2 进行中、3 结束、4 取消
function getStatusTone(status: number): 'active' | 'done' {
  return status === GameStatus.WAITING || status === GameStatus.PLAYING ? 'active' : 'done'
}

function getStatusText(game: LobbyGame): string {
  const status = game.gameStatus
  if (isMahjongRoom(game)) {
    if (status === MahjongRoomStatus.WAITING) return '等待开局'
    if (status === MahjongRoomStatus.PLAYING) return '对局中'
    if (status === MahjongRoomStatus.FINISHED) return '本局结束'
    if (status === MahjongRoomStatus.CANCELLED) return '已取消'
  }
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

function getFirstPlayerName(game: GameInfo | GobangGame): string {
  return isGobangGame(game) ? (game.blackPlayerName || '黑棋') : (game.player1Name || '玩家1')
}

function getSecondPlayerName(game: GameInfo | GobangGame): string {
  if (isGobangGame(game)) {
    return game.whitePlayerName || game.invitedPlayerName || '等待中'
  }
  return game.player2Name || '等待中'
}

function getFirstAvatar(game: GameInfo | GobangGame): string {
  return (isGobangGame(game) ? game.blackPlayerAvatar : game.player1Avatar) || ''
}

function getSecondAvatar(game: GameInfo | GobangGame): string {
  if (isGobangGame(game)) {
    return game.whitePlayerAvatar || game.invitedPlayerAvatar || ''
  }
  return game.player2Avatar || ''
}

function getWinnerText(game: GameInfo | GobangGame): string {
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
  return formatMonthDayTime(time)
}

let loadRequest = 0
async function loadGames(): Promise<void> {
  // 快速切换游戏或筛选时，只采用最后一次请求的结果
  const id = ++loadRequest
  loading.value = true
  try {
    const status = activeTab.value === 0 ? undefined : activeTab.value
    if (selectedGame.value === 'mahjong') {
      await mahjongStore.fetchHistory(status)
      if (id === loadRequest) mahjongGames.value = mahjongStore.history
    } else if (selectedGame.value === 'flight') {
      await gameStore.fetchGameHistory(status)
      if (id === loadRequest) flightGames.value = gameStore.gameHistory
    } else {
      await gobangStore.fetchGameHistory(status)
      if (id === loadRequest) gobangGames.value = gobangStore.gameHistory
    }
  } catch (error: any) {
    if (id === loadRequest) showToast({ type: 'fail', message: error.message || '加载失败' })
  } finally {
    if (id === loadRequest) loading.value = false
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
    // 每个人只能有一个对象，直接替用户选好
    if (partnerList.value.length === 1) {
      selectedPartnerId.value = partnerList.value[0].id
      selectedPartnerName.value = partnerList.value[0].name
    }
    mahjongInvitePartner.value = partnerList.value.length > 0
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

  if (selectedGame.value === 'mahjong') return createMahjongRoom()

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
    showToast({ type: 'fail', message: error.message || '创建失败' })
    return false
  }
}

async function createMahjongRoom(): Promise<boolean> {
  showLoadingToast({ message: '创建中...', forbidClick: true })
  try {
    const room = await mahjongStore.createRoom({
      mode: mahjongMode.value,
      seatCount: mahjongSeats.value,
      capPoints: mahjongCap.value,
      inviteUserId: mahjongInvitePartner.value && partnerList.value.length ? partnerList.value[0].id : undefined
    })
    closeToast()
    showToast({ type: 'success', message: '房间创建成功' })
    router.push(`/mobile/mahjong/room/${room.id}`)
    return true
  } catch (error: any) {
    closeToast()
    showToast({ type: 'fail', message: error.message || '创建失败' })
    return false
  }
}

async function joinByCode(code: string): Promise<string> {
  if (selectedGame.value === 'mahjong') return `/mobile/mahjong/room/${(await mahjongStore.joinRoom(code)).id}`
  if (selectedGame.value === 'flight') return `/mobile/game/room/${(await gameStore.joinGame(code)).id}`
  return `/mobile/gobang/room/${(await gobangStore.joinGame(code)).id}`
}

async function handleJoinGame(action: string): Promise<boolean> {
  if (action !== 'confirm') return true

  if (!joinGameCode.value || joinGameCode.value.length !== 6) {
    showToast({ type: 'fail', message: '请输入6位房间号' })
    return false
  }

  showLoadingToast({ message: '加入中...', forbidClick: true })
  try {
    const targetRoute = await joinByCode(joinGameCode.value)

    closeToast()
    showToast({ type: 'success', message: '加入成功' })
    joinGameCode.value = ''
    router.push(targetRoute)
    return true
  } catch (error: any) {
    closeToast()
    showToast({ type: 'fail', message: error.message || '加入失败' })
    return false
  }
}

function resetCreateForm(): void {
  if (partnerList.value.length === 1) return
  selectedPartnerId.value = null
  selectedPartnerName.value = ''
}

function goToGame(game: LobbyGame): void {
  if (isMahjongRoom(game)) {
    router.push(`/mobile/mahjong/room/${game.id}`)
    return
  }
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

<style scoped>
.game-lobby {
  min-height: 100%;
  padding: max(20px, env(safe-area-inset-top)) 12px 24px;
  background: var(--bg);
}

.header-section {
  padding: 0 4px 16px;
}

.title {
  color: var(--ink);
  font-size: 24px;
  line-height: 1.25;
}

.subtitle {
  margin-top: 4px;
  color: var(--ink-2);
  font-size: 13px;
}

.game-selector {
  display: flex;
  gap: 4px;
  padding: 3px;
  border-radius: var(--radius);
  background: var(--surface-2);
}

.game-option {
  flex: 1;
  padding: 7px 0;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--ink-2);
  font-size: 14px;
  cursor: pointer;
}

.game-option.active {
  background: var(--surface);
  color: var(--ink);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}

.action-section {
  margin-top: 12px;
}

.action-section :deep(.van-cell-group--inset) {
  margin: 0;
  border-radius: var(--radius);
}

.cell-icon {
  margin-right: 12px;
  color: var(--ink-2);
}

.history-section {
  margin-top: 20px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 6px;
  color: var(--ink);
  font-size: 15px;
  font-weight: 600;
}

.section-title :deep(.van-tabs__nav) {
  background: transparent;
}

.game-card {
  padding: 14px 16px;
  cursor: pointer;
}

.game-card + .game-card {
  margin-top: 8px;
}

.game-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.game-status {
  font-size: 13px;
  font-weight: 600;
}

.game-status.is-active {
  color: var(--accent);
}

.game-status.is-done {
  color: var(--ink-3);
}

.game-code {
  color: var(--ink-3);
  font-size: 12px;
}

.game-players {
  display: flex;
  align-items: center;
  justify-content: space-around;
  margin: 12px 0 10px;
}

.player {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  width: 40%;
}

.player .name {
  max-width: 100%;
  overflow: hidden;
  color: var(--ink);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vs {
  color: var(--ink-3);
  font-size: 13px;
  font-weight: 600;
}

.game-footer {
  display: flex;
  justify-content: space-between;
  color: var(--ink-3);
  font-size: 12px;
}

.winner {
  color: var(--ink-2);
}

.list-loading {
  display: flex;
  justify-content: center;
  padding: 20px 0;
}

.dialog-content {
  padding: 8px 0 12px;
}

.dialog-tip {
  padding: 0 24px 8px;
  color: var(--ink-2);
  font-size: 13px;
  text-align: center;
}

/* 麻将记录卡片：最多 4 个座位 */
.mj-summary {
  margin-top: 8px;
  color: var(--ink-2);
  font-size: 13px;
}

.mj-players {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  margin: 10px 0;
}

.mj-player {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  min-width: 0;
}

.mj-player .name {
  max-width: 100%;
  overflow: hidden;
  color: var(--ink);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mj-player .score {
  color: var(--ink-2);
  font-size: 12px;
}

.mj-seat-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px dashed var(--rule-strong);
  border-radius: 50%;
}

.mj-seat-mark.bot {
  border: 0;
  background: var(--surface-2);
  color: var(--ink-2);
  font-size: 13px;
}

.mj-create {
  padding: 8px 20px 12px;
}

.mj-create .dialog-tip {
  padding: 0 0 10px;
}

.mj-field {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 8px 0;
}

.mj-field-label {
  flex-shrink: 0;
  width: 32px;
  color: var(--ink-2);
  font-size: 14px;
  line-height: 20px;
}

.mj-field :deep(.van-radio-group--horizontal) {
  row-gap: 8px;
}

.mj-field-hint {
  margin: -2px 0 4px 44px;
  color: var(--ink-3);
  font-size: 12px;
}

.mj-invite {
  margin-top: 8px;
  font-size: 14px;
}
</style>
