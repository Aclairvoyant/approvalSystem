<template>
  <div class="game-room">
    <!-- 返回按钮 -->
    <van-nav-bar
      :title="gameTitle"
      left-arrow
      @click-left="handleBack"
    >
      <template #right>
        <div class="nav-right">
          <!-- 房主结束游戏按钮 -->
          <van-button
            v-if="gameStore.isGamePlaying && gameStore.isRoomOwner"
            type="danger"
            size="mini"
            plain
            @click="handleForceEndGame"
          >
            结束
          </van-button>
          <van-icon name="question-o" size="20" @click="showRules = true" />
        </div>
      </template>
    </van-nav-bar>

    <!-- 等待加入状态 -->
    <div v-if="gameStore.isGameWaiting" class="waiting-section">
      <div class="waiting-card">
        <van-loading type="spinner" size="50" color="#667eea" />
        <p class="waiting-text">等待对方加入...</p>
        <div class="room-code">
          <span class="label">房间号</span>
          <span class="code">{{ gameStore.currentGame?.gameCode }}</span>
          <van-button size="small" type="primary" @click="copyRoomCode">
            复制
          </van-button>
        </div>

        <!-- 任务位置配置（仅房主可见） -->
        <div v-if="gameStore.isRoomOwner" class="task-config">
          <div class="config-title">
            <span>任务格子配置</span>
            <van-button size="mini" type="primary" plain @click="showTaskConfig = true">
              自定义
            </van-button>
          </div>
          <div class="task-preview">
            <span class="task-count">当前任务数: {{ gameStore.currentGame?.taskPositions?.length || 10 }}</span>
            <span class="task-positions">位置: {{ (gameStore.currentGame?.taskPositions || []).join(', ') }}</span>
          </div>
        </div>

        <van-button type="danger" plain size="small" @click="handleCancelGame">
          取消游戏
        </van-button>
      </div>
    </div>

    <!-- 游戏进行中/已结束 -->
    <div v-else class="game-content">
      <!-- 玩家信息区 -->
      <div class="players-section">
        <div class="player player-1" :class="{ active: gameStore.currentGame?.currentTurn === 1 }">
          <van-image
            round
            width="50"
            height="50"
            :src="gameStore.currentGame?.player1Avatar || defaultAvatar"
          />
          <span class="name">{{ gameStore.currentGame?.player1Name || '玩家1' }}</span>
          <van-tag v-if="gameStore.currentGame?.currentTurn === 1" type="primary" size="small">
            当前回合
          </van-tag>
        </div>
        <div class="vs">VS</div>
        <div class="player player-2" :class="{ active: gameStore.currentGame?.currentTurn === 2 }">
          <van-image
            round
            width="50"
            height="50"
            :src="gameStore.currentGame?.player2Avatar || defaultAvatar"
          />
          <span class="name">{{ gameStore.currentGame?.player2Name || '玩家2' }}</span>
          <van-tag v-if="gameStore.currentGame?.currentTurn === 2" type="primary" size="small">
            当前回合
          </van-tag>
        </div>
      </div>

      <!-- 棋盘区域 -->
      <div class="board-section">
        <ChessBoard
          :player1-pieces="gameStore.currentGame?.player1Pieces || [0,0]"
          :player2-pieces="gameStore.currentGame?.player2Pieces || [0,0]"
          :current-player="gameStore.currentGame?.currentTurn || 1"
          :my-player-number="gameStore.myPlayerNumber || 1"
          :dice-result="gameStore.diceResult"
          :is-my-turn="gameStore.isMyTurn"
          :task-positions="gameStore.currentGame?.taskPositions || [5, 10, 15, 20, 25, 30, 35, 40, 45, 50]"
          :task-infos="gameStore.currentGame?.taskInfos || []"
          @piece-click="handlePieceClick"
        />
      </div>

      <!-- 操作区域 -->
      <div class="action-section">
        <!-- 骰子结果显示 -->
        <div class="dice-area">
          <div class="dice" :class="{ rolling: gameStore.isDiceRolling }">
            <span v-if="gameStore.diceResult">{{ gameStore.diceResult }}</span>
            <span v-else>?</span>
          </div>
          <p class="dice-hint">
            <template v-if="gameStore.isGamePlaying">
              <template v-if="gameStore.isMyTurn">
                <template v-if="gameStore.diceResult">
                  点击可移动的棋子
                </template>
                <template v-else>
                  轮到你了，点击骰子掷出
                </template>
              </template>
              <template v-else>
                等待对方操作...
              </template>
            </template>
            <template v-else-if="gameStore.isGameFinished">
              游戏已结束
            </template>
          </p>
        </div>

        <!-- 操作按钮 -->
        <div class="action-buttons">
          <van-button
            v-if="gameStore.isGamePlaying"
            type="primary"
            size="large"
            :disabled="!gameStore.isMyTurn || gameStore.diceResult !== null || gameStore.isDiceRolling"
            :loading="gameStore.isDiceRolling"
            @click="handleRollDice"
          >
            {{ gameStore.isDiceRolling ? '掷骰子中...' : '掷骰子' }}
          </van-button>

          <van-button
            v-if="gameStore.isGameFinished"
            type="primary"
            size="large"
            @click="router.push('/mobile/game')"
          >
            返回大厅
          </van-button>
        </div>
      </div>

      <!-- 游戏结束弹窗 -->
      <van-dialog
        v-model:show="showResultDialog"
        :title="gameStore.isWinner ? '恭喜获胜!' : '游戏结束'"
        :show-confirm-button="true"
        confirm-button-text="返回大厅"
        @confirm="router.push('/mobile/game')"
      >
        <div class="result-content">
          <van-icon
            :name="gameStore.isWinner ? 'like-o' : 'smile-o'"
            :color="gameStore.isWinner ? '#ff976a' : '#07c160'"
            size="60"
          />
          <p v-if="gameStore.isWinner">太棒了！你赢得了这场游戏！</p>
          <p v-else>下次再接再厉！</p>
        </div>
      </van-dialog>

      <!-- 任务弹窗 -->
      <van-dialog
        v-model:show="showTaskDialog"
        :title="gameStore.isTaskExecutor ? '确认任务完成' : '对方触发任务'"
        :show-cancel-button="gameStore.isTaskExecutor"
        :show-confirm-button="gameStore.isTaskExecutor"
        confirm-button-text="完成任务"
        cancel-button-text="放弃任务"
        :close-on-click-overlay="false"
        @confirm="handleCompleteTask"
        @cancel="handleAbandonTask"
      >
        <div class="task-content" v-if="gameStore.triggeredTask">
          <div class="task-trigger-info">
            <span v-if="gameStore.isTaskExecutor">
              {{ gameStore.triggeredTask.triggerPlayerName || '对方' }} 触发了任务，请确认对方是否完成：
            </span>
            <span v-else>
              你触发了任务，等待 {{ getOtherPlayerName() }} 确认完成：
            </span>
          </div>
          <h3 class="task-title">{{ gameStore.triggeredTask.title }}</h3>
          <p class="task-desc">{{ gameStore.triggeredTask.description }}</p>
          <div class="task-points">
            <van-icon name="star" color="#ffd700" />
            <span>{{ gameStore.triggeredTask.points }} 积分</span>
          </div>
          <div v-if="!gameStore.isTaskExecutor" class="waiting-confirm">
            <van-loading size="24" color="#667eea" />
            <span>等待对方确认...</span>
          </div>
        </div>
      </van-dialog>
    </div>

    <!-- 游戏规则弹窗 -->
    <van-popup v-model:show="showRules" position="bottom" round style="height: 60%">
      <div class="rules-content">
        <h3>游戏规则</h3>
        <ul>
          <li>每个玩家有2个棋子，初始都在基地</li>
          <li>掷到5或6点才能将棋子移出基地</li>
          <li>按骰子点数移动棋子</li>
          <li>走到对方棋子的位置可以"吃掉"对方，对方棋子返回基地</li>
          <li>掷到6点可以再掷一次</li>
          <li>金色格子会触发特殊任务</li>
          <li>先把所有棋子全部移到终点的玩家获胜</li>
        </ul>
        <van-button type="primary" block @click="showRules = false">知道了</van-button>
      </div>
    </van-popup>

    <!-- 任务配置弹窗 -->
    <van-popup v-model:show="showTaskConfig" position="bottom" round style="height: 80%">
      <div class="task-config-content">
        <h3>自定义任务格子</h3>
        <p class="config-tip">选择哪些格子会触发任务（1-52）</p>

        <div class="position-grid">
          <div
            v-for="pos in 52"
            :key="pos"
            class="position-item"
            :class="{ selected: selectedPositions.includes(pos) }"
            @click="togglePosition(pos)"
          >
            {{ pos }}
          </div>
        </div>

        <div class="config-summary">
          <span>已选择 {{ selectedPositions.length }} 个任务格</span>
        </div>

        <div class="config-actions">
          <van-button type="default" @click="resetPositions">重置为默认</van-button>
          <van-button type="primary" @click="saveTaskPositions" :loading="gameStore.isLoading">保存配置</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showDialog } from 'vant'
import { useGameStore } from '@/store/modules/game'
import ChessBoard from '@/components/ChessBoard.vue'

const route = useRoute()
const router = useRouter()
const gameStore = useGameStore()

// 默认头像
const defaultAvatar = 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg'

// 状态
const showRules = ref(false)
const showResultDialog = ref(false)
const showTaskDialog = ref(false)
const showTaskConfig = ref(false)

// 任务位置配置
const DEFAULT_TASK_POSITIONS = [5, 10, 15, 20, 25, 30, 35, 40, 45, 50]
const selectedPositions = ref<number[]>([...DEFAULT_TASK_POSITIONS])

// 计算属性
const gameId = computed(() => Number(route.params.id))

const gameTitle = computed(() => {
  if (gameStore.isGameWaiting) return '等待加入'
  if (gameStore.isGamePlaying) return '游戏进行中'
  if (gameStore.isGameFinished) return '游戏结束'
  return '飞行棋'
})

// 监听游戏结束
watch(() => gameStore.isGameFinished, (isFinished) => {
  if (isFinished) {
    showResultDialog.value = true
  }
})

// 监听任务触发
watch(() => gameStore.triggeredTask, (task) => {
  if (task) {
    showTaskDialog.value = true
  } else {
    // 任务完成或放弃时关闭弹窗
    showTaskDialog.value = false
  }
})

// 方法
async function initGame() {
  try {
    await gameStore.enterGameRoom(gameId.value)
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '加载游戏失败' })
    router.push('/mobile/game')
  }
}

function handleBack() {
  showDialog({
    title: '提示',
    message: '确定要离开游戏房间吗？',
    showCancelButton: true
  }).then(() => {
    gameStore.leaveGameRoom()
    router.push('/mobile/game')
  }).catch(() => {})
}

function copyRoomCode() {
  const code = gameStore.currentGame?.gameCode
  if (code) {
    navigator.clipboard.writeText(code).then(() => {
      showToast({ type: 'success', message: '已复制房间号' })
    }).catch(() => {
      showToast({ type: 'fail', message: '复制失败' })
    })
  }
}

async function handleCancelGame() {
  try {
    await showDialog({
      title: '提示',
      message: '确定要取消游戏吗？',
      showCancelButton: true
    })

    await gameStore.cancelGame()
    showToast({ type: 'success', message: '游戏已取消' })
    router.push('/mobile/game')
  } catch (error) {
    // 用户取消
  }
}

function handleRollDice() {
  if (!gameStore.isMyTurn) {
    showToast({ type: 'fail', message: '还没轮到你' })
    return
  }
  gameStore.rollDice()
}

function handlePieceClick(player: number, pieceIndex: number) {
  if (!gameStore.isMyTurn) {
    showToast({ type: 'fail', message: '还没轮到你' })
    return
  }

  if (gameStore.diceResult === null) {
    showToast({ type: 'fail', message: '请先掷骰子' })
    return
  }

  if (player !== gameStore.myPlayerNumber) {
    showToast({ type: 'fail', message: '只能移动自己的棋子' })
    return
  }

  gameStore.movePiece(pieceIndex)
}

function handleCompleteTask() {
  if (gameStore.triggeredTask?.recordId && gameStore.isTaskExecutor) {
    gameStore.completeTask(gameStore.triggeredTask.recordId)
  }
  showTaskDialog.value = false
}

function handleAbandonTask() {
  if (gameStore.triggeredTask?.recordId && gameStore.isTaskExecutor) {
    gameStore.abandonTask(gameStore.triggeredTask.recordId)
  }
  showTaskDialog.value = false
}

function getOtherPlayerName(): string {
  if (!gameStore.currentGame || !gameStore.myPlayerNumber) return '对方'
  return gameStore.myPlayerNumber === 1
    ? (gameStore.currentGame.player2Name || '玩家2')
    : (gameStore.currentGame.player1Name || '玩家1')
}

// 任务位置配置方法
function togglePosition(pos: number) {
  const index = selectedPositions.value.indexOf(pos)
  if (index === -1) {
    selectedPositions.value.push(pos)
    selectedPositions.value.sort((a, b) => a - b)
  } else {
    selectedPositions.value.splice(index, 1)
  }
}

function resetPositions() {
  selectedPositions.value = [...DEFAULT_TASK_POSITIONS]
}

async function saveTaskPositions() {
  if (selectedPositions.value.length === 0) {
    showToast({ type: 'fail', message: '请至少选择一个任务格' })
    return
  }
  try {
    await gameStore.updateTaskPositions(selectedPositions.value)
    showToast({ type: 'success', message: '配置已保存' })
    showTaskConfig.value = false
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '保存失败' })
  }
}

// 当打开任务配置弹窗时，同步当前游戏的任务位置
watch(showTaskConfig, (val) => {
  if (val && gameStore.currentGame?.taskPositions) {
    selectedPositions.value = [...gameStore.currentGame.taskPositions]
  }
})

async function handleForceEndGame() {
  try {
    await showDialog({
      title: '提示',
      message: '确定要结束游戏吗？游戏将以平局结束。',
      showCancelButton: true
    })

    await gameStore.forceEndGame()
    showToast({ type: 'success', message: '游戏已结束' })
    router.push('/mobile/game')
  } catch (error) {
    // 用户取消或出错
  }
}

onMounted(() => {
  initGame()
})

onUnmounted(() => {
  gameStore.leaveGameRoom()
})
</script>

<style scoped lang="scss">
.game-room {
  min-height: 100vh;
  background-color: #f7f8fa;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.waiting-section {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 46px);
  padding: 20px;
}

.waiting-card {
  background: white;
  border-radius: 16px;
  padding: 40px;
  text-align: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);

  .waiting-text {
    font-size: 18px;
    color: #323233;
    margin: 20px 0;
  }

  .room-code {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    background: #f7f8fa;
    padding: 16px;
    border-radius: 8px;
    margin-bottom: 20px;

    .label {
      color: #969799;
      font-size: 14px;
    }

    .code {
      font-size: 24px;
      font-weight: bold;
      color: #667eea;
      letter-spacing: 4px;
    }
  }
}

.game-content {
  padding: 12px;
}

.players-section {
  display: flex;
  justify-content: space-around;
  align-items: center;
  background: white;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;

  .player {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    padding: 8px;
    border-radius: 8px;
    transition: all 0.3s;

    &.active {
      background: linear-gradient(135deg, #667eea20 0%, #764ba220 100%);
    }

    .name {
      font-size: 14px;
      color: #323233;
      font-weight: 500;
    }
  }

  .vs {
    font-size: 20px;
    font-weight: bold;
    color: #ff976a;
  }
}

.board-section {
  background: white;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
}

.action-section {
  background: white;
  border-radius: 12px;
  padding: 20px;

  .dice-area {
    display: flex;
    flex-direction: column;
    align-items: center;
    margin-bottom: 20px;

    .dice {
      width: 60px;
      height: 60px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border-radius: 12px;
      display: flex;
      justify-content: center;
      align-items: center;
      font-size: 28px;
      font-weight: bold;
      color: white;
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);

      &.rolling {
        animation: shake 0.5s infinite;
      }
    }

    .dice-hint {
      margin-top: 12px;
      font-size: 14px;
      color: #969799;
    }
  }

  .action-buttons {
    display: flex;
    gap: 12px;

    .van-button {
      flex: 1;
    }
  }
}

.result-content {
  padding: 30px;
  text-align: center;

  p {
    margin-top: 16px;
    font-size: 16px;
    color: #323233;
  }
}

.task-content {
  padding: 20px;
  text-align: center;

  .task-trigger-info {
    font-size: 14px;
    color: #969799;
    margin-bottom: 16px;
    line-height: 1.5;
  }

  .task-title {
    font-size: 18px;
    color: #323233;
    margin-bottom: 12px;
  }

  .task-desc {
    font-size: 14px;
    color: #969799;
    margin-bottom: 16px;
  }

  .task-points {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 4px;
    font-size: 16px;
    color: #ff976a;
  }

  .waiting-confirm {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    margin-top: 20px;
    padding: 12px;
    background: #f7f8fa;
    border-radius: 8px;
    color: #667eea;
    font-size: 14px;
  }
}

.rules-content {
  padding: 20px;

  h3 {
    font-size: 18px;
    text-align: center;
    margin-bottom: 16px;
  }

  ul {
    padding-left: 20px;
    margin-bottom: 20px;

    li {
      font-size: 14px;
      color: #323233;
      line-height: 2;
    }
  }
}

@keyframes shake {
  0%, 100% { transform: rotate(-5deg); }
  50% { transform: rotate(5deg); }
}

.task-config {
  margin: 16px 0;
  padding: 12px;
  background: #f7f8fa;
  border-radius: 8px;
  width: 100%;

  .config-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
    font-size: 14px;
    font-weight: 500;
  }

  .task-preview {
    display: flex;
    flex-direction: column;
    gap: 4px;
    font-size: 12px;
    color: #969799;

    .task-count {
      color: #667eea;
      font-weight: 500;
    }

    .task-positions {
      word-break: break-all;
    }
  }
}

.task-config-content {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;

  h3 {
    font-size: 18px;
    text-align: center;
    margin-bottom: 8px;
  }

  .config-tip {
    text-align: center;
    color: #969799;
    font-size: 14px;
    margin-bottom: 16px;
  }

  .position-grid {
    flex: 1;
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    gap: 8px;
    overflow-y: auto;
    padding: 8px 0;
  }

  .position-item {
    aspect-ratio: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #f7f8fa;
    border: 2px solid #eee;
    border-radius: 8px;
    font-size: 14px;
    font-weight: 500;
    color: #323233;
    cursor: pointer;
    transition: all 0.2s;

    &.selected {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border-color: #667eea;
      color: white;
    }

    &:active {
      transform: scale(0.95);
    }
  }

  .config-summary {
    padding: 12px 0;
    text-align: center;
    font-size: 14px;
    color: #667eea;
    font-weight: 500;
  }

  .config-actions {
    display: flex;
    gap: 12px;

    .van-button {
      flex: 1;
    }
  }
}
</style>
