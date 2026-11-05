<template>
  <div class="game-room">
    <van-nav-bar :title="gameTitle" left-arrow fixed placeholder @click-left="handleBack">
      <template #right>
        <div class="nav-right">
          <van-button
            v-if="gameStore.isGamePlaying && gameStore.isRoomOwner"
            type="danger"
            size="mini"
            plain
            @click="handleForceEndGame"
          >
            结束
          </van-button>
          <van-icon name="question-o" size="20" aria-label="游戏规则" @click="showRules = true" />
        </div>
      </template>
    </van-nav-bar>

    <div v-if="!gameStore.currentGame" class="page-state">
      <van-loading size="24px" vertical>{{ loadError || '正在进入房间...' }}</van-loading>
    </div>

    <!-- 等待加入 -->
    <div v-else-if="gameStore.isGameWaiting" class="m-page">
      <section class="m-card m-card--pad waiting-card">
        <p class="waiting-text">等对方加入</p>
        <p class="waiting-hint">把房间号发给对方，在游戏大厅输入即可加入</p>
        <div class="room-code">
          <span class="code tabular">{{ gameStore.currentGame.gameCode }}</span>
          <van-button size="small" plain type="primary" @click="copyRoomCode">复制</van-button>
        </div>

        <div v-if="gameStore.isRoomOwner" class="task-config">
          <div class="config-head">
            <span>任务格子 {{ gameStore.currentGame.taskPositions?.length || DEFAULT_TASK_POSITIONS.length }} 个</span>
            <van-button size="mini" plain type="primary" @click="showTaskConfig = true">自定义</van-button>
          </div>
          <p class="config-positions tabular">{{ (gameStore.currentGame.taskPositions || DEFAULT_TASK_POSITIONS).join('、') }}</p>
        </div>

        <van-button class="cancel-btn" plain size="small" @click="handleCancelGame">取消房间</van-button>
      </section>
    </div>

    <!-- 对局中 / 已结束 -->
    <div v-else class="m-page game-content">
      <section class="m-card players">
        <div class="player" :class="{ active: gameStore.currentGame.currentTurn === 1 }">
          <UserAvatar :src="gameStore.currentGame.player1Avatar" :name="gameStore.currentGame.player1Name || '玩家1'" :size="40" />
          <span class="player-name"><i class="dot dot-1"></i>{{ gameStore.currentGame.player1Name || '玩家 1' }}</span>
          <span class="turn-label">{{ gameStore.currentGame.currentTurn === 1 && gameStore.isGamePlaying ? '当前回合' : '' }}</span>
        </div>
        <span class="vs">VS</span>
        <div class="player" :class="{ active: gameStore.currentGame.currentTurn === 2 }">
          <UserAvatar :src="gameStore.currentGame.player2Avatar" :name="gameStore.currentGame.player2Name || '玩家2'" :size="40" />
          <span class="player-name"><i class="dot dot-2"></i>{{ gameStore.currentGame.player2Name || '玩家 2' }}</span>
          <span class="turn-label">{{ gameStore.currentGame.currentTurn === 2 && gameStore.isGamePlaying ? '当前回合' : '' }}</span>
        </div>
      </section>

      <section class="m-card board-card">
        <ChessBoard
          :player1-pieces="gameStore.currentGame.player1Pieces || [0, 0]"
          :player2-pieces="gameStore.currentGame.player2Pieces || [0, 0]"
          :current-player="gameStore.currentGame.currentTurn || 1"
          :my-player-number="gameStore.myPlayerNumber || 1"
          :dice-result="gameStore.diceResult"
          :is-my-turn="gameStore.isMyTurn"
          :task-positions="gameStore.currentGame.taskPositions || DEFAULT_TASK_POSITIONS"
          :task-infos="gameStore.currentGame.taskInfos || []"
          @piece-click="handlePieceClick"
        />
      </section>

      <section class="m-card m-card--pad action-card">
        <div class="dice-row">
          <div class="dice tabular" :class="{ rolling: gameStore.isDiceRolling }" aria-live="polite">
            {{ displayedDice ?? '–' }}
          </div>
          <p class="dice-hint">{{ diceHint }}</p>
        </div>
        <p v-if="!gameStore.isConnected && !gameStore.isGameFinished" class="conn-off">连接中断，正在重连…</p>

        <van-button
          v-if="gameStore.isGamePlaying"
          block
          type="primary"
          :disabled="!gameStore.isMyTurn || gameStore.diceResult !== null || gameStore.isDiceRolling"
          :loading="gameStore.isDiceRolling"
          loading-text="掷骰子中..."
          @click="handleRollDice"
        >
          掷骰子
        </van-button>
        <van-button v-else block type="primary" @click="router.push('/mobile/game')">返回大厅</van-button>
      </section>
    </div>

    <!-- 对局结束：只在本次进入房间后从对局中变为结束时弹出 -->
    <van-dialog
      v-model:show="showResultDialog"
      :title="resultTitle"
      confirm-button-text="返回大厅"
      show-cancel-button
      cancel-button-text="留在这里"
      @confirm="router.push('/mobile/game')"
    >
      <p class="result-text">{{ resultText }}</p>
    </van-dialog>

    <!-- 任务：等服务端确认完成 / 放弃后才关闭 -->
    <van-popup v-model:show="showTaskDialog" round :close-on-click-overlay="!gameStore.isTaskExecutor" class="task-popup">
      <div v-if="gameStore.triggeredTask" class="task-content">
        <p class="task-from">
          {{ gameStore.isTaskExecutor
            ? `${gameStore.triggeredTask.triggerPlayerName || '对方'} 触发了任务，完成后请你确认`
            : `你触发了任务，等 ${otherPlayerName} 确认` }}
        </p>
        <h3 class="task-title">{{ gameStore.triggeredTask.title }}</h3>
        <p v-if="gameStore.triggeredTask.description" class="task-desc">{{ gameStore.triggeredTask.description }}</p>
        <p class="task-points tabular">{{ gameStore.triggeredTask.points }} 积分</p>

        <div v-if="gameStore.isTaskExecutor" class="m-actions task-actions">
          <van-button plain :loading="taskAction === 'abandon'" :disabled="!!taskAction" @click="handleAbandonTask">
            没完成
          </van-button>
          <van-button type="primary" :loading="taskAction === 'complete'" :disabled="!!taskAction" @click="handleCompleteTask">
            已完成
          </van-button>
        </div>
        <van-button v-else block plain class="task-close" @click="showTaskDialog = false">知道了</van-button>
      </div>
    </van-popup>

    <van-popup v-model:show="showRules" position="bottom" round closeable>
      <div class="rules">
        <div class="m-sheet-title">游戏规则</div>
        <ul>
          <li>每人 2 枚棋子，开局都在基地</li>
          <li>掷到 5 或 6 才能把棋子移出基地</li>
          <li>按骰子点数前进，走到对方棋子所在格可以把它打回基地</li>
          <li>掷到 6 可以再掷一次</li>
          <li>停在带 ★ 的格子会触发任务，由对方确认是否完成</li>
          <li>先把两枚棋子都送到终点的一方获胜</li>
        </ul>
      </div>
    </van-popup>

    <van-popup v-model:show="showTaskConfig" position="bottom" round :style="{ height: '80%' }">
      <div class="task-config-sheet">
        <div class="m-sheet-title">选择任务格子</div>
        <p class="config-tip">停在选中的格子上会触发任务（1–52）</p>

        <div class="position-grid">
          <button
            v-for="pos in 52"
            :key="pos"
            type="button"
            class="position-item tabular"
            :class="{ selected: selectedPositions.includes(pos) }"
            @click="togglePosition(pos)"
          >
            {{ pos }}
          </button>
        </div>

        <p class="config-summary">已选 {{ selectedPositions.length }} 个</p>
        <div class="m-sheet-footer">
          <van-button @click="resetPositions">恢复默认</van-button>
          <van-button type="primary" :loading="gameStore.isLoading" @click="saveTaskPositions">保存</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast, showToast } from 'vant'
import { GameStatus, useGameStore } from '@/store/modules/game'
import { copyText } from '@/utils/clipboard'
import ChessBoard from '@/components/ChessBoard.vue'
import UserAvatar from '@/components/UserAvatar.vue'

const DEFAULT_TASK_POSITIONS = [5, 10, 15, 20, 25, 30, 35, 40, 45, 50]

const route = useRoute()
const router = useRouter()
const gameStore = useGameStore()

const showRules = ref(false)
const showResultDialog = ref(false)
const showTaskDialog = ref(false)
const showTaskConfig = ref(false)
const loadError = ref('')
const taskAction = ref<'complete' | 'abandon' | null>(null)
const selectedPositions = ref<number[]>([...DEFAULT_TASK_POSITIONS])

const gameId = Number(route.params.id)

const gameTitle = computed(() => {
  if (gameStore.isGameWaiting) return '等待加入'
  if (gameStore.isGamePlaying) return gameStore.isMyTurn ? '轮到你了' : '对方回合'
  if (gameStore.isGameFinished) return '对局结束'
  return '飞行棋'
})

// 自己掷出的点数优先；轮到对方时显示最近一次掷出的点数
const displayedDice = computed(() => gameStore.diceResult ?? gameStore.currentGame?.lastDiceResult ?? null)

const diceHint = computed(() => {
  if (gameStore.isGameFinished) return '对局已结束'
  if (!gameStore.isGamePlaying) return ''
  if (!gameStore.isMyTurn) return '等对方操作'
  if (gameStore.diceResult !== null) return '点一枚高亮的棋子前进'
  return '轮到你了，点下面的按钮掷骰子'
})

const otherPlayerName = computed(() => {
  const game = gameStore.currentGame
  if (!game || !gameStore.myPlayerNumber) return '对方'
  return gameStore.myPlayerNumber === 1 ? (game.player2Name || '对方') : (game.player1Name || '对方')
})

const resultTitle = computed(() => {
  if (!gameStore.currentGame?.winnerId) return '对局结束'
  return gameStore.isWinner ? '你赢了' : '对局结束'
})

const resultText = computed(() => {
  const game = gameStore.currentGame
  if (!game?.winnerId) return '这局没有分出胜负。'
  if (gameStore.isWinner) return '两枚棋子都到达终点，恭喜！'
  return `${otherPlayerName.value} 先到达终点。`
})

// 只有本次在房间里看到对局从进行中变为结束时才弹结果；从历史记录打开已结束的对局不弹
watch(() => gameStore.currentGame?.gameStatus, (status, previous) => {
  if (status === GameStatus.FINISHED && previous === GameStatus.PLAYING) {
    showResultDialog.value = true
  }
})

watch(() => gameStore.triggeredTask, (task) => {
  taskAction.value = null
  showTaskDialog.value = !!task
})

watch(showTaskConfig, (visible) => {
  if (visible && gameStore.currentGame?.taskPositions) {
    selectedPositions.value = [...gameStore.currentGame.taskPositions]
  }
})

async function initGame() {
  try {
    await gameStore.enterGameRoom(gameId)
  } catch (error: any) {
    loadError.value = error.message || '加载对局失败'
    showFailToast(loadError.value)
    router.replace('/mobile/game')
  }
}

async function handleBack() {
  if (gameStore.isGamePlaying) {
    try {
      await showConfirmDialog({ title: '离开房间', message: '对局会保留，之后可以从大厅回来继续。' })
    } catch {
      return
    }
  }
  router.push('/mobile/game')
}

async function copyRoomCode() {
  const code = gameStore.currentGame?.gameCode
  if (!code) return
  try {
    await copyText(code)
    showSuccessToast('已复制房间号')
  } catch (error: any) {
    showFailToast(error.message || '复制失败')
  }
}

async function handleCancelGame() {
  try {
    await showConfirmDialog({ title: '取消房间', message: '取消后房间号失效，确定吗？' })
  } catch {
    return
  }
  try {
    await gameStore.cancelGame()
    showSuccessToast('房间已取消')
    router.push('/mobile/game')
  } catch (error: any) {
    showFailToast(error.message || '取消失败')
  }
}

async function handleForceEndGame() {
  try {
    await showConfirmDialog({ title: '结束对局', message: '提前结束不分胜负，确定结束吗？' })
  } catch {
    return
  }
  try {
    await gameStore.forceEndGame()
    showSuccessToast('对局已结束')
    router.push('/mobile/game')
  } catch (error: any) {
    showFailToast(error.message || '结束失败')
  }
}

function handleRollDice() {
  gameStore.rollDice()
}

function handlePieceClick(player: number, pieceIndex: number) {
  if (player !== gameStore.myPlayerNumber) {
    showToast('只能移动自己的棋子')
    return
  }
  gameStore.movePiece(pieceIndex)
}

function handleCompleteTask() {
  const task = gameStore.triggeredTask
  if (!task || !gameStore.isTaskExecutor) return
  if (gameStore.completeTask(task.recordId)) taskAction.value = 'complete'
}

function handleAbandonTask() {
  const task = gameStore.triggeredTask
  if (!task || !gameStore.isTaskExecutor) return
  if (gameStore.abandonTask(task.recordId)) taskAction.value = 'abandon'
}

function togglePosition(pos: number) {
  const index = selectedPositions.value.indexOf(pos)
  if (index === -1) {
    selectedPositions.value = [...selectedPositions.value, pos].sort((a, b) => a - b)
  } else {
    selectedPositions.value = selectedPositions.value.filter(item => item !== pos)
  }
}

function resetPositions() {
  selectedPositions.value = [...DEFAULT_TASK_POSITIONS]
}

async function saveTaskPositions() {
  if (selectedPositions.value.length === 0) {
    showFailToast('至少选择一个任务格')
    return
  }
  try {
    await gameStore.updateTaskPositions(selectedPositions.value)
    showSuccessToast('已保存')
    showTaskConfig.value = false
  } catch (error: any) {
    showFailToast(error.message || '保存失败')
  }
}

onMounted(initGame)

onUnmounted(() => {
  gameStore.leaveGameRoom()
})
</script>

<style scoped>
.game-room {
  min-height: 100%;
  background: var(--bg);
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-state {
  padding-top: 96px;
  text-align: center;
}

.game-content > * + * {
  margin-top: 10px;
}

.waiting-card {
  padding: 28px 20px;
  text-align: center;
}

.waiting-text {
  color: var(--ink);
  font-size: 18px;
  font-weight: 600;
}

.waiting-hint {
  margin-top: 4px;
  color: var(--ink-2);
  font-size: 13px;
}

.room-code {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
}

.room-code .code {
  color: var(--ink);
  font-size: 30px;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.task-config {
  margin-top: 20px;
  padding: 12px;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  text-align: left;
}

.config-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ink);
  font-size: 14px;
  font-weight: 500;
}

.config-positions {
  margin-top: 4px;
  color: var(--ink-3);
  font-size: 12px;
  word-break: break-all;
}

.cancel-btn {
  margin-top: 20px;
}

.players {
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 12px 8px;
}

.player {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 8px 4px;
  border: 1px solid transparent;
  border-radius: var(--radius);
}

.player.active {
  border-color: var(--rule);
  background: var(--surface-2);
}

.player-name {
  display: flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  overflow: hidden;
  color: var(--ink);
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot-1 {
  background: #e5534b;
}

.dot-2 {
  background: #3b7dd8;
}

.turn-label {
  min-height: 16px;
  color: var(--accent);
  font-size: 12px;
}

.vs {
  color: var(--ink-3);
  font-size: 14px;
  font-weight: 600;
}

.board-card {
  padding: 10px;
}

.dice-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}

.dice {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border: 1px solid var(--rule-strong);
  border-radius: 10px;
  background: var(--surface);
  color: var(--ink);
  font-size: 26px;
  font-weight: 700;
}

.dice.rolling {
  animation: shake 0.4s ease-in-out infinite;
}

@keyframes shake {
  0%, 100% { transform: rotate(-6deg); }
  50% { transform: rotate(6deg); }
}

.dice-hint {
  color: var(--ink-2);
  font-size: 14px;
}

.conn-off {
  margin: -6px 0 12px;
  color: var(--danger);
  font-size: 13px;
}

.result-text {
  padding: 8px 24px 20px;
  color: var(--ink-2);
  font-size: 14px;
  text-align: center;
}

.task-popup {
  width: min(88vw, 340px);
}

.task-content {
  padding: 24px 20px 20px;
  text-align: center;
}

.task-from {
  color: var(--ink-2);
  font-size: 13px;
  line-height: 1.5;
}

.task-title {
  margin-top: 12px;
  color: var(--ink);
  font-size: 18px;
}

.task-desc {
  margin-top: 6px;
  color: var(--ink-2);
  font-size: 14px;
  line-height: 1.6;
}

.task-points {
  margin-top: 10px;
  color: var(--ink);
  font-size: 14px;
  font-weight: 600;
}

.task-actions,
.task-close {
  margin-top: 20px;
}

.rules {
  padding-bottom: calc(20px + env(safe-area-inset-bottom));
}

.rules ul {
  margin: 0;
  padding: 4px 20px 0 36px;
}

.rules li {
  color: var(--ink);
  font-size: 14px;
  line-height: 1.9;
}

.task-config-sheet {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding-bottom: calc(16px + env(safe-area-inset-bottom));
}

.config-tip {
  color: var(--ink-3);
  font-size: 13px;
  text-align: center;
}

.position-grid {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(7, 1fr);
  gap: 6px;
  padding: 12px 16px;
  overflow-y: auto;
  align-content: start;
}

.position-item {
  aspect-ratio: 1;
  padding: 0;
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--ink);
  font-size: 14px;
  cursor: pointer;
}

.position-item.selected {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
}

.config-summary {
  color: var(--ink-2);
  font-size: 13px;
  text-align: center;
}
</style>
