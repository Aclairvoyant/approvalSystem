<template>
  <div class="mahjong-room">
    <!-- 等待中状态 -->
    <div v-if="gameStatus === 1" class="waiting-screen">
      <van-nav-bar title="等待中" left-arrow @click-left="handleLeave" />
      <div class="waiting-content">
        <div class="room-code">
          <span class="code-label">房间号</span>
          <span class="code-value">{{ gameState?.gameCode }}</span>
          <van-button size="small" @click="copyCode">复制</van-button>
        </div>

        <div class="game-config">
          <div class="config-item">
            <span class="config-label">规则</span>
            <span class="config-value">{{ gameState?.ruleTypeName }}</span>
          </div>
          <div class="config-item">
            <span class="config-label">局数</span>
            <span class="config-value">{{ gameState?.totalRounds }}局</span>
          </div>
          <div class="config-item">
            <span class="config-label">封顶</span>
            <span class="config-value">{{ gameState?.maxScore || '无' }}</span>
          </div>
          <div class="config-item">
            <span class="config-label">底分</span>
            <span class="config-value">{{ gameState?.baseScore }}</span>
          </div>
          <div class="config-item">
            <span class="config-label">飞苍蝇</span>
            <span class="config-value">{{ gameState?.flyCount }}只</span>
          </div>
        </div>

        <div class="players-list">
          <div class="player-slot" v-for="i in (gameState?.playerCount || 4)" :key="i">
            <template v-if="getPlayerInfo(i)">
              <div class="player-avatar">
                <img v-if="getPlayerInfo(i)?.avatar" :src="getPlayerInfo(i)?.avatar" />
                <span v-else>{{ getPlayerInfo(i)?.name?.charAt(0) }}</span>
              </div>
              <span class="player-name">{{ getPlayerInfo(i)?.name }}</span>
              <van-tag v-if="i === 1" type="warning" size="small">房主</van-tag>
            </template>
            <template v-else>
              <div class="player-avatar empty">
                <van-icon name="plus" />
              </div>
              <span class="player-name">等待加入...</span>
            </template>
          </div>
        </div>

        <van-button
          v-if="isRoomOwner"
          type="primary"
          size="large"
          block
          :disabled="!canStart"
          @click="startGame"
        >
          开始游戏
        </van-button>
        <van-button v-else type="default" size="large" block disabled>
          等待房主开始...
        </van-button>
      </div>
    </div>

    <!-- 游戏中状态 -->
    <div v-else-if="gameStatus === 2" class="game-screen">
      <MahjongTable
        :current-round="gameState?.currentRound"
        :total-rounds="gameState?.totalRounds"
        :wall-remaining="roundData?.wallRemaining"
        :wild-tile="gameState?.wildTile"
        :dealer-seat="gameState?.dealerSeat"
        :current-turn="roundData?.currentTurn"
        :my-seat="mySeat"
        :player-count="gameState?.playerCount"
        :my-hand="roundData?.myHand"
        :player1-name="gameState?.player1Name"
        :player1-avatar="gameState?.player1Avatar"
        :player1-score="gameState?.player1Score"
        :player1-melds="roundData?.player1Melds"
        :player1-flowers="roundData?.player1Flowers"
        :player1-discards="roundData?.player1Discards"
        :player2-name="gameState?.player2Name"
        :player2-avatar="gameState?.player2Avatar"
        :player2-score="gameState?.player2Score"
        :player2-hand-count="roundData?.player2HandCount"
        :player2-melds="roundData?.player2Melds"
        :player2-flowers="roundData?.player2Flowers"
        :player2-discards="roundData?.player2Discards"
        :player3-name="gameState?.player3Name"
        :player3-avatar="gameState?.player3Avatar"
        :player3-score="gameState?.player3Score"
        :player3-hand-count="roundData?.player3HandCount"
        :player3-melds="roundData?.player3Melds"
        :player3-flowers="roundData?.player3Flowers"
        :player3-discards="roundData?.player3Discards"
        :player4-name="gameState?.player4Name"
        :player4-avatar="gameState?.player4Avatar"
        :player4-score="gameState?.player4Score"
        :player4-hand-count="roundData?.player4HandCount"
        :player4-melds="roundData?.player4Melds"
        :player4-flowers="roundData?.player4Flowers"
        :player4-discards="roundData?.player4Discards"
        :available-actions="roundData?.availableActions || []"
        :chi-options="roundData?.chiOptions || []"
        :last-discard-tile="roundData?.lastTile"
        :last-action-seat="roundData?.lastActionSeat"
        :show-result="showResult"
        :result-title="resultTitle"
        :score-changes="scoreChanges"
        :is-game-over="isGameOver"
        @pass="handlePass"
        @chi="handleChi"
        @pong="handlePong"
        @kong="handleKong"
        @hu="handleHu"
        @discard="handleDiscard"
        @next-round="handleNextRound"
      />
    </div>

    <!-- 游戏结束状态 -->
    <div v-else-if="gameStatus === 3" class="end-screen">
      <van-nav-bar title="游戏结束" left-arrow @click-left="goBack" />
      <div class="end-content">
        <h2>最终战绩</h2>
        <div class="final-scores">
          <div
            v-for="i in (gameState?.playerCount || 4)"
            :key="i"
            class="score-row"
            :class="{ winner: isWinner(i) }"
          >
            <div class="player-info">
              <span class="rank">{{ getRank(i) }}</span>
              <span class="name">{{ getPlayerInfo(i)?.name }}</span>
            </div>
            <span class="score" :class="{ positive: getPlayerScore(i) > 0 }">
              {{ getPlayerScore(i) > 0 ? '+' : '' }}{{ getPlayerScore(i) }}
            </span>
          </div>
        </div>
        <van-button type="primary" block @click="goBack">返回大厅</van-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showDialog, showLoadingToast, closeToast } from 'vant'
import { useMahjongStore } from '@/store/modules/mahjong'
import { useUserStore } from '@/store/modules/user'
import MahjongTable from '@/components/MahjongTable.vue'
import { connectMahjongWebSocket, disconnectMahjongWebSocket } from '@/services/mahjongWebsocket'

const route = useRoute()
const router = useRouter()
const mahjongStore = useMahjongStore()
const userStore = useUserStore()

const gameId = computed(() => Number(route.params.id))

// 游戏状态
const gameState = ref<any>(null)
const roundData = ref<any>(null)
const showResult = ref(false)
const resultTitle = ref('')
const scoreChanges = ref<Record<string, number>>({})
const isGameOver = ref(false)
const isActionPending = ref(false) // 防止重复操作

// 计算属性
const gameStatus = computed(() => gameState.value?.gameStatus || 1)
const mySeat = computed(() => {
  const userId = Number(userStore.userId)
  const p1 = Number(gameState.value?.player1Id)
  const p2 = Number(gameState.value?.player2Id)
  const p3 = Number(gameState.value?.player3Id)
  const p4 = Number(gameState.value?.player4Id)

  console.log('[MahjongRoom] mySeat计算: userId=', userId, 'player1Id=', p1, 'player2Id=', p2)

  if (p1 && p1 === userId) return 1
  if (p2 && p2 === userId) return 2
  if (p3 && p3 === userId) return 3
  if (p4 && p4 === userId) return 4
  return 0
})

const isRoomOwner = computed(() => {
  return Number(gameState.value?.player1Id) === Number(userStore.userId)
})

const canStart = computed(() => {
  const count = gameState.value?.playerCount || 4
  let joined = 0
  if (gameState.value?.player1Id) joined++
  if (gameState.value?.player2Id) joined++
  if (gameState.value?.player3Id) joined++
  if (gameState.value?.player4Id) joined++
  return joined >= count
})

onMounted(async () => {
  await loadGameState()
  connectWebSocket()
})

onUnmounted(() => {
  disconnectMahjongWebSocket()
})

// 加载游戏状态
async function loadGameState() {
  try {
    const state = await mahjongStore.getGameState(gameId.value)
    console.log('[MahjongRoom] loadGameState:', state)
    console.log('[MahjongRoom] 服务器返回的 currentUserId:', state?.currentUserId)
    console.log('[MahjongRoom] 本地 userStore.userId:', userStore.userId)
    console.log('[MahjongRoom] mySeat:', state?.currentRoundData?.mySeat)
    console.log('[MahjongRoom] myHand:', state?.currentRoundData?.myHand)
    console.log('[MahjongRoom] availableActions:', state?.currentRoundData?.availableActions)

    // 验证：如果服务器返回的userId和本地不一致，说明有问题
    if (state?.currentUserId && Number(state.currentUserId) !== Number(userStore.userId)) {
      console.error('[MahjongRoom] 警告: 服务器userId与本地userId不匹配!',
        'server:', state.currentUserId, 'local:', userStore.userId)
    }

    gameState.value = state
    roundData.value = state.currentRoundData
  } catch (e: any) {
    showToast(e.message || '加载游戏状态失败')
  }
}

// 连接WebSocket
function connectWebSocket() {
  connectMahjongWebSocket(gameId.value, {
    onGameState: (state) => {
      // 收到状态更新通知，始终重新加载个人化数据
      if (state && state.type === 'GAME_STATE') {
        // 可以先更新基础信息（不含个人数据）
        updateGameStateFromBroadcast(state)
      }
      // 始终重新加载以获取个人化数据（手牌、可用操作等）
      loadGameState()
    },
    onPlayerJoined: (data) => {
      // 有新玩家加入，重新加载个人化状态
      // 注意：不要直接使用 data.gameState，因为它可能是为其他用户准备的
      console.log('[MahjongRoom] Player joined:', data.playerName)
      loadGameState()
    },
    onPlayerLeft: (data) => {
      // 有玩家离开
      console.log('[MahjongRoom] Player left:', data)
      // 如果游戏被取消（房主离开且无其他玩家），跳转回大厅
      if (data.gameStatus === 4) { // CANCELLED status = 4
        showToast('房间已解散')
        router.replace('/mobile/mahjong')
        return
      }
      // 重新加载个人化状态
      loadGameState()
    },
    onGameStarted: (data) => {
      // 游戏开始，重新加载个人化状态
      console.log('[MahjongRoom] Game started')
      loadGameState()
    },
    onActionExecuted: (data) => {
      loadGameState()
    },
    onRoundStarted: (data) => {
      loadGameState()
    },
    onRoundEnded: (data) => {
      showResult.value = true
      resultTitle.value = data.title || '本局结束'
      scoreChanges.value = data.scoreChanges || {}
      isGameOver.value = data.isGameOver || false
    },
    onError: (error) => {
      showToast(error)
    }
  })
}

// 从广播数据更新游戏状态
function updateGameStateFromBroadcast(data: any) {
  if (!gameState.value) {
    gameState.value = {} as any
  }
  // 更新基本信息
  if (data.gameId) gameState.value.id = data.gameId
  if (data.gameCode) gameState.value.gameCode = data.gameCode
  if (data.gameStatus !== undefined) gameState.value.gameStatus = data.gameStatus
  if (data.currentRound !== undefined) gameState.value.currentRound = data.currentRound
  if (data.playerCount !== undefined) gameState.value.playerCount = data.playerCount
  if (data.dealerSeat !== undefined) gameState.value.dealerSeat = data.dealerSeat
  if (data.totalRounds !== undefined) gameState.value.totalRounds = data.totalRounds

  // 更新玩家ID信息（关键！）
  gameState.value.player1Id = data.player1Id
  gameState.value.player2Id = data.player2Id
  gameState.value.player3Id = data.player3Id
  gameState.value.player4Id = data.player4Id

  // 更新玩家名称和头像
  if (data.player1Name) gameState.value.player1Name = data.player1Name
  if (data.player1Avatar) gameState.value.player1Avatar = data.player1Avatar
  if (data.player2Name) gameState.value.player2Name = data.player2Name
  if (data.player2Avatar) gameState.value.player2Avatar = data.player2Avatar
  if (data.player3Name) gameState.value.player3Name = data.player3Name
  if (data.player3Avatar) gameState.value.player3Avatar = data.player3Avatar
  if (data.player4Name) gameState.value.player4Name = data.player4Name
  if (data.player4Avatar) gameState.value.player4Avatar = data.player4Avatar

  // 更新积分
  if (data.player1Score !== undefined) gameState.value.player1Score = data.player1Score
  if (data.player2Score !== undefined) gameState.value.player2Score = data.player2Score
  if (data.player3Score !== undefined) gameState.value.player3Score = data.player3Score
  if (data.player4Score !== undefined) gameState.value.player4Score = data.player4Score

  console.log('[MahjongRoom] Updated game state from broadcast:', gameState.value)
}

// 获取玩家信息
function getPlayerInfo(seat: number) {
  if (!gameState.value) return null
  const prefix = `player${seat}`
  const id = gameState.value[`${prefix}Id`]
  if (!id) return null
  return {
    id,
    name: gameState.value[`${prefix}Name`] || `玩家${seat}`,
    avatar: gameState.value[`${prefix}Avatar`]
  }
}

// 复制房间号
function copyCode() {
  if (gameState.value?.gameCode) {
    navigator.clipboard.writeText(gameState.value.gameCode)
    showToast('已复制房间号')
  }
}

// 离开房间
function handleLeave() {
  showDialog({
    title: '确认离开',
    message: '确定要离开房间吗？',
    showCancelButton: true
  }).then(async () => {
    try {
      await mahjongStore.leaveGame(gameId.value)
      router.replace('/mobile/mahjong')
    } catch (e: any) {
      showToast(e.message || '离开失败')
    }
  }).catch(() => {})
}

// 开始游戏
async function startGame() {
  showLoadingToast({ message: '开始游戏...', forbidClick: true })
  try {
    await mahjongStore.startGame(gameId.value)
    closeToast()
  } catch (e: any) {
    closeToast()
    showToast(e.message || '开始游戏失败')
  }
}

// 游戏操作 - 带防重复保护
async function handlePass() {
  if (isActionPending.value) return
  isActionPending.value = true
  try {
    await mahjongStore.executeAction(gameId.value, { actionType: 'PASS' })
  } finally {
    isActionPending.value = false
  }
}

async function handlePong() {
  if (isActionPending.value) return
  isActionPending.value = true
  try {
    await mahjongStore.executeAction(gameId.value, { actionType: 'PONG' })
  } finally {
    isActionPending.value = false
  }
}

async function handleChi(chiTiles: string[]) {
  if (isActionPending.value) return
  isActionPending.value = true
  try {
    await mahjongStore.executeAction(gameId.value, { actionType: 'CHI', chiTiles })
  } finally {
    isActionPending.value = false
  }
}

async function handleKong(tile: string, type: string) {
  if (isActionPending.value) return
  isActionPending.value = true
  try {
    await mahjongStore.executeAction(gameId.value, { actionType: type, tile })
  } finally {
    isActionPending.value = false
  }
}

async function handleHu() {
  if (isActionPending.value) return
  isActionPending.value = true
  try {
    await mahjongStore.executeAction(gameId.value, { actionType: 'HU' })
  } finally {
    isActionPending.value = false
  }
}

async function handleDiscard(tile: string) {
  if (isActionPending.value) return
  isActionPending.value = true
  try {
    await mahjongStore.executeAction(gameId.value, { actionType: 'DISCARD', tile })
  } finally {
    isActionPending.value = false
  }
}

async function handleNextRound() {
  showResult.value = false
  if (isGameOver.value) {
    await loadGameState()
  } else {
    await mahjongStore.nextRound(gameId.value)
    await loadGameState()
  }
}

// 返回大厅
function goBack() {
  router.replace('/mobile/mahjong')
}

// 获取玩家得分
function getPlayerScore(seat: number): number {
  if (!gameState.value) return 0
  return gameState.value[`player${seat}Score`] || 0
}

// 判断是否是赢家
function isWinner(seat: number): boolean {
  const scores = [
    gameState.value?.player1Score || 0,
    gameState.value?.player2Score || 0,
    gameState.value?.player3Score || 0,
    gameState.value?.player4Score || 0
  ]
  const maxScore = Math.max(...scores)
  return getPlayerScore(seat) === maxScore && maxScore > 0
}

// 获取排名
function getRank(seat: number): string {
  const scores: Array<{ seat: number; score: number }> = []
  for (let i = 1; i <= (gameState.value?.playerCount || 4); i++) {
    scores.push({ seat: i, score: getPlayerScore(i) })
  }
  scores.sort((a, b) => b.score - a.score)
  const rank = scores.findIndex(s => s.seat === seat) + 1
  const medals = ['', '', '', '']
  return medals[rank - 1] || String(rank)
}
</script>

<style scoped>
.mahjong-room {
  min-height: 100vh;
  background: #1a472a;
}

/* 等待屏幕 */
.waiting-screen {
  min-height: 100vh;
}

.waiting-content {
  padding: 20px;
}

.room-code {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  margin-bottom: 20px;
}

.code-label {
  color: #aaa;
}

.code-value {
  font-size: 28px;
  font-weight: bold;
  color: #ffd93d;
  letter-spacing: 4px;
}

.game-config {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  margin-bottom: 20px;
}

.config-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.config-label {
  color: #888;
  font-size: 12px;
}

.config-value {
  color: #fff;
  font-weight: 500;
}

.players-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}

.player-slot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
}

.player-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #4cd137;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20px;
  font-weight: bold;
  overflow: hidden;
}

.player-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.player-avatar.empty {
  background: rgba(255, 255, 255, 0.1);
  color: #666;
}

.player-name {
  color: #fff;
  font-size: 14px;
}

/* 游戏屏幕 */
.game-screen {
  width: 100%;
  height: 100vh;
}

/* 结束屏幕 */
.end-screen {
  min-height: 100vh;
}

.end-content {
  padding: 20px;
}

.end-content h2 {
  text-align: center;
  color: #ffd93d;
  margin-bottom: 24px;
}

.final-scores {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 24px;
}

.score-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.score-row:last-child {
  border-bottom: none;
}

.score-row.winner {
  background: rgba(255, 215, 0, 0.1);
}

.player-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rank {
  font-size: 20px;
}

.name {
  color: #fff;
  font-weight: 500;
}

.score {
  font-size: 20px;
  font-weight: bold;
  color: #ff4757;
}

.score.positive {
  color: #4cd137;
}
</style>
