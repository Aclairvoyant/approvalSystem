import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { gameWebSocket, MessageType, type GameMessage } from '@/services/websocket'
import { gameApi } from '@/services/api'
import { useUserStore } from './user'
import { showToast } from 'vant'

// 游戏状态枚举
export enum GameStatus {
  WAITING = 1,    // 等待加入
  PLAYING = 2,    // 游戏中
  FINISHED = 3,   // 已结束
  CANCELLED = 4   // 已取消
}

// 任务位置信息
export interface TaskPositionInfo {
  position: number
  taskId: number | null
  title: string
}

// 游戏信息接口
export interface GameInfo {
  id: number
  gameCode: string
  player1Id: number
  player1Name: string | null
  player1Avatar: string | null
  player2Id: number | null
  player2Name: string | null
  player2Avatar: string | null
  currentTurn: number  // 1 或 2
  gameStatus: GameStatus
  winnerId: number | null
  player1Pieces: number[]  // [0,0] 每个棋子的位置
  player2Pieces: number[]
  lastDiceResult: number | null
  taskPositions: number[]  // 任务触发位置
  taskInfos: TaskPositionInfo[]  // 任务位置详细信息
  createdAt: string
  startedAt: string | null
  endedAt: string | null
}

// 触发的任务信息
export interface TriggeredTask {
  taskId: number
  recordId: number
  title: string
  description: string
  points: number
  triggerPlayerId: number    // 触发任务的玩家
  executorPlayerId: number   // 执行/确认任务的玩家（另一方）
  triggerPlayerName?: string
}

export const useGameStore = defineStore('game', () => {
  const userStore = useUserStore()

  // 状态
  const currentGame = ref<GameInfo | null>(null)
  const isConnected = ref(false)
  const isLoading = ref(false)
  const diceResult = ref<number | null>(null)
  const isDiceRolling = ref(false)
  const triggeredTask = ref<TriggeredTask | null>(null)
  const errorMessage = ref<string | null>(null)
  const gameHistory = ref<GameInfo[]>([])

  // 计算属性
  const isMyTurn = computed(() => {
    if (!currentGame.value || !userStore.userId) return false
    const myId = userStore.userId
    const currentTurn = currentGame.value.currentTurn
    if (currentTurn === 1 && currentGame.value.player1Id === myId) return true
    if (currentTurn === 2 && currentGame.value.player2Id === myId) return true
    return false
  })

  const myPlayerNumber = computed(() => {
    if (!currentGame.value || !userStore.userId) return null
    if (currentGame.value.player1Id === userStore.userId) return 1
    if (currentGame.value.player2Id === userStore.userId) return 2
    return null
  })

  const myPieces = computed(() => {
    if (!currentGame.value || !myPlayerNumber.value) return [0, 0]
    return myPlayerNumber.value === 1
      ? currentGame.value.player1Pieces
      : currentGame.value.player2Pieces
  })

  const opponentPieces = computed(() => {
    if (!currentGame.value || !myPlayerNumber.value) return [0, 0]
    return myPlayerNumber.value === 1
      ? currentGame.value.player2Pieces
      : currentGame.value.player1Pieces
  })

  const isGameWaiting = computed(() => currentGame.value?.gameStatus === GameStatus.WAITING)
  const isGamePlaying = computed(() => currentGame.value?.gameStatus === GameStatus.PLAYING)
  const isGameFinished = computed(() => currentGame.value?.gameStatus === GameStatus.FINISHED)

  const isWinner = computed(() => {
    if (!currentGame.value?.winnerId || !userStore.userId) return false
    return currentGame.value.winnerId === userStore.userId
  })

  // 是否是房主（player1）
  const isRoomOwner = computed(() => {
    if (!currentGame.value || !userStore.userId) return false
    return currentGame.value.player1Id === userStore.userId
  })

  // 是否是任务执行者（只有执行者才能完成/放弃任务）
  const isTaskExecutor = computed(() => {
    if (!triggeredTask.value || !userStore.userId) return false
    return triggeredTask.value.executorPlayerId === userStore.userId
  })

  // 方法

  /**
   * 连接WebSocket
   */
  async function connectWebSocket(): Promise<void> {
    if (!userStore.token) {
      throw new Error('未登录')
    }

    try {
      await gameWebSocket.connect(userStore.token, {
        onConnected: () => {
          isConnected.value = true
          console.log('游戏WebSocket已连接')
        },
        onDisconnected: () => {
          isConnected.value = false
          console.log('游戏WebSocket已断开')
        },
        onError: (error) => {
          errorMessage.value = error.message
          showToast({ type: 'fail', message: error.message })
        },
        onMessage: handleMessage
      })
    } catch (error: any) {
      errorMessage.value = error.message
      throw error
    }
  }

  /**
   * 断开WebSocket
   */
  function disconnectWebSocket(): void {
    gameWebSocket.disconnect()
    isConnected.value = false
    currentGame.value = null
    diceResult.value = null
    triggeredTask.value = null
  }

  /**
   * 处理WebSocket消息
   */
  function handleMessage(message: GameMessage): void {
    switch (message.type) {
      case MessageType.GAME_STATE_UPDATE:
        handleGameStateUpdate(message)
        break
      case MessageType.DICE_ROLLED:
        handleDiceRolled(message)
        break
      case MessageType.PIECE_MOVED:
        handlePieceMoved(message)
        break
      case MessageType.PIECE_CAPTURED:
        handlePieceCaptured(message)
        break
      case MessageType.TURN_CHANGED:
        handleTurnChanged(message)
        break
      case MessageType.PLAYER_JOINED:
        handlePlayerJoined(message)
        break
      case MessageType.TASK_TRIGGERED:
        handleTaskTriggered(message)
        break
      case MessageType.TASK_COMPLETED:
      case MessageType.TASK_ABANDONED:
        handleTaskEnded(message)
        break
      case MessageType.GAME_ENDED:
        handleGameEnded(message)
        break
      case MessageType.ERROR:
        handleError(message)
        break
    }
  }

  function handleGameStateUpdate(message: GameMessage): void {
    if (currentGame.value && message.data) {
      currentGame.value.currentTurn = message.data.currentTurn
      currentGame.value.player1Pieces = message.data.player1Pieces
      currentGame.value.player2Pieces = message.data.player2Pieces
      currentGame.value.lastDiceResult = message.data.lastDiceResult
    }
  }

  function handleDiceRolled(message: GameMessage): void {
    if (message.data) {
      // 只有当前回合的玩家才需要记录骰子结果（用于移动棋子）
      // 其他玩家只是显示 Toast 通知
      if (message.senderId === userStore.userId) {
        diceResult.value = message.data.diceResult
      }
      isDiceRolling.value = false
      showToast({
        message: `${message.senderName || '玩家'}掷出了 ${message.data.diceResult}`,
        position: 'top'
      })
    }
  }

  function handlePieceMoved(message: GameMessage): void {
    if (currentGame.value && message.data) {
      // 更新会在GAME_STATE_UPDATE中处理
      console.log('棋子移动:', message.data)
    }
  }

  function handlePieceCaptured(message: GameMessage): void {
    if (message.data) {
      showToast({
        message: '吃子！对方棋子返回基地',
        type: 'success',
        position: 'top'
      })
    }
  }

  function handleTurnChanged(message: GameMessage): void {
    if (currentGame.value && message.data) {
      currentGame.value.currentTurn = message.data.currentTurn
      // 重置骰子状态，新回合需要重新掷骰子
      diceResult.value = null
      isDiceRolling.value = false
      if (isMyTurn.value) {
        showToast({ message: '轮到你了！', position: 'top' })
      }
    }
  }

  function handlePlayerJoined(message: GameMessage): void {
    showToast({
      message: `${message.senderName || '对方'}加入了游戏`,
      type: 'success',
      position: 'top'
    })
    // 重新获取游戏详情
    if (currentGame.value) {
      fetchGameDetail(currentGame.value.id)
    }
  }

  function handleTaskTriggered(message: GameMessage): void {
    if (message.data) {
      triggeredTask.value = {
        taskId: message.data.taskId,
        recordId: message.data.recordId,
        title: message.data.taskTitle,
        description: message.data.taskDescription,
        points: message.data.points,
        triggerPlayerId: message.data.triggerPlayerId,
        executorPlayerId: message.data.executorPlayerId,
        triggerPlayerName: message.senderName
      }
    }
  }

  function handleTaskEnded(message: GameMessage): void {
    triggeredTask.value = null
    const isCompleted = message.type === MessageType.TASK_COMPLETED
    showToast({
      message: isCompleted ? '任务完成！' : '任务已放弃',
      type: isCompleted ? 'success' : 'text',
      position: 'top'
    })
  }

  function handleGameEnded(message: GameMessage): void {
    if (currentGame.value && message.data) {
      currentGame.value.gameStatus = GameStatus.FINISHED
      currentGame.value.winnerId = message.data.winnerId

      const isWin = message.data.winnerId === userStore.userId
      showToast({
        message: isWin ? '恭喜你赢了！' : `${message.data.winnerName}获胜`,
        type: isWin ? 'success' : 'text',
        duration: 3000
      })
    }
  }

  function handleError(message: GameMessage): void {
    if (message.data?.error) {
      errorMessage.value = message.data.error
      showToast({ type: 'fail', message: message.data.error })
    }
  }

  /**
   * 创建游戏
   */
  async function createGame(opponentUserId: number, taskPositions?: number[]): Promise<GameInfo> {
    isLoading.value = true
    try {
      const response: any = await gameApi.createGame(opponentUserId, taskPositions)
      currentGame.value = response
      return response
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 更新任务位置配置
   */
  async function updateTaskPositions(taskPositions: number[]): Promise<GameInfo> {
    if (!currentGame.value) {
      throw new Error('没有当前游戏')
    }
    isLoading.value = true
    try {
      const response: any = await gameApi.updateTaskPositions(currentGame.value.id, taskPositions)
      currentGame.value = response
      return response
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 加入游戏
   */
  async function joinGame(gameCode: string): Promise<GameInfo> {
    isLoading.value = true
    try {
      const response: any = await gameApi.joinGame(gameCode)
      currentGame.value = response
      return response
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取游戏详情
   */
  async function fetchGameDetail(gameId: number): Promise<GameInfo> {
    isLoading.value = true
    try {
      const response: any = await gameApi.getGameDetail(gameId)
      currentGame.value = response
      return response
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 进入游戏房间（连接WebSocket并订阅）
   */
  async function enterGameRoom(gameId: number): Promise<void> {
    // 先获取游戏详情
    await fetchGameDetail(gameId)

    // 连接WebSocket
    if (!isConnected.value) {
      await connectWebSocket()
    }

    // 订阅游戏房间
    gameWebSocket.subscribeToGame(gameId)
  }

  /**
   * 离开游戏房间
   */
  function leaveGameRoom(): void {
    gameWebSocket.unsubscribeFromGame()
    currentGame.value = null
    diceResult.value = null
    triggeredTask.value = null
  }

  /**
   * 掷骰子
   */
  function rollDice(): void {
    if (!currentGame.value || !isMyTurn.value) {
      showToast({ type: 'fail', message: '还没轮到你' })
      return
    }

    isDiceRolling.value = true
    diceResult.value = null
    gameWebSocket.rollDice(currentGame.value.id)
  }

  /**
   * 移动棋子
   */
  function movePiece(pieceIndex: number): void {
    if (!currentGame.value || !isMyTurn.value || diceResult.value === null) {
      showToast({ type: 'fail', message: '无法移动' })
      return
    }

    gameWebSocket.movePiece(currentGame.value.id, pieceIndex, diceResult.value)
    diceResult.value = null
  }

  /**
   * 完成任务
   */
  function completeTask(recordId: number, completionNote?: string): void {
    if (!currentGame.value) return
    gameWebSocket.completeTask(currentGame.value.id, recordId, completionNote)
  }

  /**
   * 放弃任务
   */
  function abandonTask(recordId: number): void {
    if (!currentGame.value) return
    gameWebSocket.abandonTask(currentGame.value.id, recordId)
  }

  /**
   * 取消游戏
   */
  async function cancelGame(): Promise<void> {
    if (!currentGame.value) return
    await gameApi.cancelGame(currentGame.value.id)
    currentGame.value = null
  }

  /**
   * 强制结束游戏（房主可用）
   */
  async function forceEndGame(): Promise<void> {
    if (!currentGame.value) return
    await gameApi.forceEndGame(currentGame.value.id)
    currentGame.value = null
  }

  /**
   * 获取游戏历史
   */
  async function fetchGameHistory(status?: number): Promise<void> {
    isLoading.value = true
    try {
      const response: any = await gameApi.getUserGames(status)
      gameHistory.value = response?.records || []
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 清除错误消息
   */
  function clearError(): void {
    errorMessage.value = null
  }

  return {
    // 状态
    currentGame,
    isConnected,
    isLoading,
    diceResult,
    isDiceRolling,
    triggeredTask,
    errorMessage,
    gameHistory,

    // 计算属性
    isMyTurn,
    myPlayerNumber,
    myPieces,
    opponentPieces,
    isGameWaiting,
    isGamePlaying,
    isGameFinished,
    isWinner,
    isRoomOwner,
    isTaskExecutor,

    // 方法
    connectWebSocket,
    disconnectWebSocket,
    createGame,
    updateTaskPositions,
    joinGame,
    fetchGameDetail,
    enterGameRoom,
    leaveGameRoom,
    rollDice,
    movePiece,
    completeTask,
    abandonTask,
    cancelGame,
    forceEndGame,
    fetchGameHistory,
    clearError
  }
})
