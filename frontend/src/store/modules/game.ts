import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { gameWebSocket, MessageType, type GameMessage } from '@/services/websocket'
import { gameApi, gameTaskApi } from '@/services/api'
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

  // 每次进入 / 离开房间都会递增，用来丢弃已经离开的房间迟到的响应
  let roomToken = 0
  let diceTimeout: ReturnType<typeof setTimeout> | null = null

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

    gameWebSocket.setCallbacks({
      onConnectionChange: (connected) => {
        isConnected.value = connected
        // 断线期间发出的掷骰子请求不会有回应，避免按钮一直转圈
        if (!connected) clearDiceRolling()
      },
      onError: (error) => {
        errorMessage.value = error.message
        showToast({ type: 'fail', message: error.message })
      },
      onMessage: handleMessage
    })

    try {
      await gameWebSocket.connect()
    } catch (error: any) {
      errorMessage.value = error.message
      throw error
    }
  }

  /**
   * 断开WebSocket
   */
  function disconnectWebSocket(): void {
    leaveGameRoom()
    gameWebSocket.disconnect()
    isConnected.value = false
  }

  function clearDiceRolling(): void {
    isDiceRolling.value = false
    if (diceTimeout) {
      clearTimeout(diceTimeout)
      diceTimeout = null
    }
  }

  function resetRoomState(): void {
    currentGame.value = null
    diceResult.value = null
    triggeredTask.value = null
    errorMessage.value = null
    clearDiceRolling()
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
      clearDiceRolling()
      if (currentGame.value) {
        currentGame.value.lastDiceResult = message.data.diceResult
      }
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
      clearDiceRolling()
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
    // 重新获取游戏详情（拿到对方信息和开局状态）
    if (currentGame.value) {
      void refreshCurrentGame()
    }
  }

  async function refreshCurrentGame(): Promise<void> {
    const gameId = currentGame.value?.id
    if (!gameId) return
    const token = roomToken
    try {
      const game = await gameApi.getGameDetail(gameId)
      if (token === roomToken) currentGame.value = game as GameInfo
    } catch {
      // 只是补充刷新，失败时保留当前状态
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
    if (!currentGame.value) return
    const winnerId: number | null = message.data?.winnerId ?? null
    currentGame.value.gameStatus = GameStatus.FINISHED
    currentGame.value.winnerId = winnerId
    triggeredTask.value = null
    clearDiceRolling()

    let text = '对局已结束'
    if (winnerId && winnerId === userStore.userId) text = '恭喜你赢了！'
    else if (winnerId) text = `${message.data?.winnerName || '对方'}获胜`
    showToast({ message: text, type: winnerId === userStore.userId ? 'success' : 'text', duration: 3000 })
  }

  function handleError(message: GameMessage): void {
    // 错误会广播给房间里的两个人，只提示发起操作的那一方
    if (message.senderId && message.senderId !== userStore.userId) return
    clearDiceRolling()
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
    const token = ++roomToken
    resetRoomState()

    const game = await gameApi.getGameDetail(gameId)
    if (token !== roomToken) return
    currentGame.value = game as GameInfo

    try {
      await connectWebSocket()
    } catch {
      // 连接会在后台自动重试，连上后自动订阅；房间照常显示，状态栏提示正在重连
    }
    if (token !== roomToken) return

    gameWebSocket.subscribeToGame(gameId)
    // 订阅之前对方可能已经加入，再拉一次详情补上
    await refreshCurrentGame()
    await restorePendingTask(gameId, token)
  }

  /** 刷新页面或断线重连后，把还没处理完的任务弹窗恢复出来 */
  async function restorePendingTask(gameId: number, token: number): Promise<void> {
    try {
      const record = await gameTaskApi.getCurrentTask(gameId)
      if (!record || token !== roomToken) return
      const task = await gameTaskApi.getTaskById(record.taskId)
      if (token !== roomToken) return
      triggeredTask.value = {
        taskId: record.taskId,
        recordId: record.id,
        title: task?.title || '任务',
        description: task?.description || task?.requirement || '',
        points: task?.points || 0,
        triggerPlayerId: record.triggerPlayerId,
        executorPlayerId: record.executorPlayerId,
      }
    } catch {
      // 恢复失败不影响对局
    }
  }

  /**
   * 离开游戏房间
   */
  function leaveGameRoom(): void {
    roomToken += 1
    gameWebSocket.unsubscribeFromGame()
    resetRoomState()
  }

  /**
   * 掷骰子
   */
  function rollDice(): void {
    if (!currentGame.value || !isMyTurn.value) {
      showToast({ type: 'fail', message: '还没轮到你' })
      return
    }

    diceResult.value = null
    if (!gameWebSocket.rollDice(currentGame.value.id)) {
      showToast({ type: 'fail', message: '连接已断开，正在重连' })
      return
    }
    isDiceRolling.value = true
    // 服务端没有回应时不要让按钮一直转圈
    diceTimeout = setTimeout(clearDiceRolling, 8000)
  }

  /**
   * 移动棋子
   */
  function movePiece(pieceIndex: number): void {
    if (!currentGame.value || !isMyTurn.value || diceResult.value === null) {
      showToast({ type: 'fail', message: '无法移动' })
      return
    }

    if (!gameWebSocket.movePiece(currentGame.value.id, pieceIndex, diceResult.value)) {
      showToast({ type: 'fail', message: '连接已断开，正在重连' })
      return
    }
    diceResult.value = null
  }

  /**
   * 完成任务：弹窗等服务端广播 TASK_COMPLETED 后才关闭
   */
  function completeTask(recordId: number, completionNote?: string): boolean {
    if (!currentGame.value) return false
    const sent = gameWebSocket.completeTask(currentGame.value.id, recordId, completionNote)
    if (!sent) showToast({ type: 'fail', message: '连接已断开，正在重连' })
    return sent
  }

  /**
   * 放弃任务
   */
  function abandonTask(recordId: number): boolean {
    if (!currentGame.value) return false
    const sent = gameWebSocket.abandonTask(currentGame.value.id, recordId)
    if (!sent) showToast({ type: 'fail', message: '连接已断开，正在重连' })
    return sent
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
  let historyRequest = 0
  async function fetchGameHistory(status?: number): Promise<void> {
    // 切换筛选时只采用最后一次请求的结果
    const id = ++historyRequest
    isLoading.value = true
    try {
      const response: any = await gameApi.getUserGames(status)
      if (id === historyRequest) gameHistory.value = response?.records || []
    } finally {
      if (id === historyRequest) isLoading.value = false
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
