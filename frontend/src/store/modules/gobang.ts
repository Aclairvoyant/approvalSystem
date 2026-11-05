import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { showToast } from 'vant'
import {
  createEmptyGobangBoard,
  GOBANG_BOARD_SIZE,
  GobangColor,
  GobangGameStatus,
  gobangApi,
  type GobangAiMoveInsight,
  type GobangAiPoint,
  type GobangAiReview,
  type GobangBoardData,
  type GobangCell,
  type GobangGame,
  type GobangMove,
  type GobangPoint
} from '@/services/gobangApi'
import {
  GobangMessageType,
  gobangWebSocket,
  type GobangMessage
} from '@/services/gobangWebsocket'
import { useUserStore } from './user'

interface GobangUndoRequestState {
  requesterId: number
  responderId?: number | null
  targetMoveNumber?: number | null
}

type UnknownRecord = Record<string, unknown>

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === 'object' && value !== null
}

function parseJsonValue(value: unknown): unknown {
  if (typeof value !== 'string') {
    return value
  }

  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

function parseJsonObject(value: unknown): UnknownRecord | null {
  const parsed = parseJsonValue(value)
  return isRecord(parsed) ? parsed : null
}

function normalizeOptionalNumber(value: unknown): number | null {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) && numberValue > 0 ? numberValue : null
}

function normalizePoint(value: unknown): GobangPoint | null {
  if (!isRecord(value)) return null

  const row = Number(value.row ?? value.rowIndex)
  const col = Number(value.col ?? value.colIndex)

  if (!Number.isInteger(row) || !Number.isInteger(col)) return null
  if (row < 0 || row >= GOBANG_BOARD_SIZE || col < 0 || col >= GOBANG_BOARD_SIZE) return null

  return { row, col }
}

function normalizeWinningLine(value: unknown): GobangPoint[] {
  if (!Array.isArray(value)) return []
  return value
    .map(point => normalizePoint(point))
    .filter((point): point is GobangPoint => point !== null)
}

function normalizeBoardMatrix(value: unknown): GobangCell[][] | null {
  if (!Array.isArray(value) || value.length !== GOBANG_BOARD_SIZE) return null

  const board = value.map(row => {
    if (!Array.isArray(row) || row.length !== GOBANG_BOARD_SIZE) return null
    return row.map(cell => {
      const value = Number(cell)
      return value === GobangColor.BLACK || value === GobangColor.WHITE
        ? value
        : GobangColor.EMPTY
    }) as GobangCell[]
  })

  if (board.some(row => row === null)) return null
  return board as GobangCell[][]
}

function cloneBoard(board: GobangCell[][]): GobangCell[][] {
  return board.map(row => [...row])
}

function getBoardData(game: GobangGame | null): GobangBoardData | null {
  if (!game) return null

  const boardData = parseJsonObject(game.boardData)
  if (boardData) return boardData as GobangBoardData

  if (isRecord(game.boardData)) return game.boardData as GobangBoardData
  return null
}

function getMoveData(move: GobangMove): UnknownRecord | null {
  return parseJsonObject(move.moveData)
}

function getMovePoint(move: GobangMove): GobangPoint | null {
  return normalizePoint({
    row: move.row ?? move.rowIndex,
    col: move.col ?? move.colIndex
  })
}

function getMoveColor(move: GobangMove): GobangColor.BLACK | GobangColor.WHITE | null {
  const color = Number(move.color)
  if (color === GobangColor.BLACK || color === GobangColor.WHITE) return color
  return null
}

function buildBoardFromMoves(moves: GobangMove[], count: number): {
  board: GobangCell[][]
  lastMove: GobangPoint | null
} {
  const board = createEmptyGobangBoard()
  const placements: Array<{ moveNumber: number; row: number; col: number }> = []

  moves.slice(0, count).forEach(move => {
    if (move.moveType === 'PLACE_STONE') {
      const point = getMovePoint(move)
      const color = getMoveColor(move)
      if (!point || !color) return

      board[point.row][point.col] = color
      placements.push({ moveNumber: move.moveNumber, ...point })
      return
    }

    if (move.moveType === 'UNDO') {
      const moveData = getMoveData(move)
      const targetMoveNumber = Number(
        moveData?.targetMoveNumber ?? moveData?.requestedTargetMoveNumber
      )
      const placementIndex = Number.isFinite(targetMoveNumber)
        ? placements.findIndex(item => item.moveNumber === targetMoveNumber)
        : placements.length - 1

      if (placementIndex >= 0) {
        const [removed] = placements.splice(placementIndex, 1)
        board[removed.row][removed.col] = GobangColor.EMPTY
      }
    }
  })

  const lastPlacement = placements[placements.length - 1]
  return {
    board,
    lastMove: lastPlacement ? { row: lastPlacement.row, col: lastPlacement.col } : null
  }
}

const DIRECTIONS: Array<[number, number]> = [[0, 1], [1, 0], [1, 1], [1, -1]]

/** 后端没有返回连珠位置时，从终局棋盘和最后一手推算五连 */
function findWinningLine(board: GobangCell[][], from: GobangPoint | null): GobangPoint[] {
  if (!from) return []
  const color = board[from.row]?.[from.col]
  if (!color) return []

  for (const [dr, dc] of DIRECTIONS) {
    const line: GobangPoint[] = [from]
    for (const sign of [1, -1]) {
      let row = from.row + dr * sign
      let col = from.col + dc * sign
      while (board[row]?.[col] === color) {
        line.push({ row, col })
        row += dr * sign
        col += dc * sign
      }
    }
    if (line.length >= 5) {
      return line.sort((a, b) => a.row - b.row || a.col - b.col)
    }
  }
  return []
}

export const useGobangStore = defineStore('gobang', () => {
  const userStore = useUserStore()

  const currentGame = ref<GobangGame | null>(null)
  const board = ref<GobangCell[][]>(createEmptyGobangBoard())
  const lastMove = ref<GobangPoint | null>(null)
  const winningLine = ref<GobangPoint[]>([])
  const undoRequest = ref<GobangUndoRequestState | null>(null)
  const replayMoves = ref<GobangMove[]>([])
  const aiReview = ref<GobangAiReview | null>(null)
  const replayIndex = ref(0)
  const isReplayMode = ref(false)
  const isConnected = ref(false)
  const isLoading = ref(false)
  const isReviewLoading = ref(false)
  const errorMessage = ref<string | null>(null)
  const gameHistory = ref<GobangGame[]>([])
  const isPlacing = ref(false)

  // 每次进入 / 离开房间递增，用来丢弃旧房间迟到的响应
  let roomToken = 0
  let placingTimeout: ReturnType<typeof setTimeout> | null = null

  const currentTurnColor = computed(() => {
    if (!currentGame.value) return GobangColor.BLACK

    const turn = Number(currentGame.value.currentTurn)
    if (turn === GobangColor.BLACK || turn === GobangColor.WHITE) return turn
    if (turn === currentGame.value.blackPlayerId) return GobangColor.BLACK
    if (turn === currentGame.value.whitePlayerId) return GobangColor.WHITE

    return GobangColor.BLACK
  })

  const myColor = computed(() => {
    if (!currentGame.value || !userStore.userId) return null
    if (currentGame.value.blackPlayerId === userStore.userId) return GobangColor.BLACK
    if (currentGame.value.whitePlayerId === userStore.userId) return GobangColor.WHITE
    return null
  })

  const isMyTurn = computed(() => {
    return currentGame.value?.gameStatus === GobangGameStatus.PLAYING &&
      myColor.value !== null &&
      currentTurnColor.value === myColor.value
  })

  const isRoomOwner = computed(() => {
    return !!currentGame.value && currentGame.value.blackPlayerId === userStore.userId
  })

  const isGameWaiting = computed(() => currentGame.value?.gameStatus === GobangGameStatus.WAITING)
  const isGamePlaying = computed(() => currentGame.value?.gameStatus === GobangGameStatus.PLAYING)
  const isGameFinished = computed(() => currentGame.value?.gameStatus === GobangGameStatus.FINISHED)
  const isWinner = computed(() => {
    return !!currentGame.value?.winnerId && currentGame.value.winnerId === userStore.userId
  })

  const replayState = computed(() => buildBoardFromMoves(replayMoves.value, replayIndex.value))
  const replayBoard = computed(() => replayState.value.board)
  const replayLastMove = computed(() => replayState.value.lastMove)
  const currentReplayMove = computed(() => {
    if (!isReplayMode.value || replayIndex.value <= 0) return null
    return replayMoves.value[replayIndex.value - 1] ?? null
  })
  const currentReplayInsight = computed<GobangAiMoveInsight | null>(() => {
    const moveNumber = currentReplayMove.value?.moveNumber
    if (!moveNumber) return null
    return aiReview.value?.insights?.find(insight => insight.moveNumber === moveNumber) ?? null
  })
  const displayAnalysisPoints = computed<GobangAiPoint[]>(() => {
    if (!isReplayMode.value) return []
    return currentReplayInsight.value?.suggestedPoints ?? []
  })
  const replayProgress = computed(() => {
    if (replayMoves.value.length === 0) return 0
    return Math.round((replayIndex.value / replayMoves.value.length) * 100)
  })

  const displayBoard = computed(() => isReplayMode.value ? replayBoard.value : board.value)
  const displayLastMove = computed(() => isReplayMode.value ? replayLastMove.value : lastMove.value)
  const displayWinningLine = computed(() => isReplayMode.value ? [] : winningLine.value)

  function applyGame(game: GobangGame): void {
    currentGame.value = game

    const rawBoardData = parseJsonValue(game.boardData)
    const boardData = getBoardData(game)
    const nextBoard = normalizeBoardMatrix(game.board) ||
      normalizeBoardMatrix(rawBoardData) ||
      normalizeBoardMatrix(boardData?.board) ||
      normalizeBoardMatrix(boardData?.matrix) ||
      createEmptyGobangBoard()

    board.value = cloneBoard(nextBoard)

    // 后端目前只返回 lastMoveId，没有坐标：没给就沿用之前的标记（棋子还在的话），或者从棋谱推算
    const previousLastMove = lastMove.value
    lastMove.value = normalizePoint(game.lastMove) ||
      normalizePoint(boardData?.lastMove) ||
      (previousLastMove && nextBoard[previousLastMove.row]?.[previousLastMove.col] ? previousLastMove : null) ||
      buildBoardFromMoves(replayMoves.value, replayMoves.value.length).lastMove

    const topLevelWinningLine = normalizeWinningLine(game.winningLine)
    const providedLine = topLevelWinningLine.length > 0
      ? topLevelWinningLine
      : normalizeWinningLine(boardData?.winningLine)
    winningLine.value = providedLine.length > 0 || Number(game.gameStatus) !== GobangGameStatus.FINISHED
      ? providedLine
      : findWinningLine(nextBoard, lastMove.value)

    // 刷新页面后恢复待处理的悔棋请求
    const requesterId = normalizeOptionalNumber(game.pendingUndoRequesterId)
    undoRequest.value = requesterId
      ? {
          requesterId,
          responderId: normalizeOptionalNumber(game.pendingUndoResponderId),
          targetMoveNumber: normalizeOptionalNumber(game.pendingUndoTargetMoveNumber),
        }
      : null
  }

  function applyMessageData(data: UnknownRecord | undefined): void {
    if (!data) return

    const nestedGame = data.game || data.state || data.gobangGame
    if (isRecord(nestedGame)) {
      applyGame(nestedGame as unknown as GobangGame)
      const messagePoint = normalizePoint(data.lastMove) || normalizePoint(data)
      if (messagePoint) lastMove.value = messagePoint
      if ('winningLine' in data) {
        winningLine.value = normalizeWinningLine(data.winningLine)
      }
      return
    }

    if (isRecord(data.boardData)) {
      const boardData = data.boardData as GobangBoardData
      const nextBoard = normalizeBoardMatrix(boardData.board) || normalizeBoardMatrix(boardData.matrix)
      if (nextBoard) board.value = cloneBoard(nextBoard)
      lastMove.value = normalizePoint(boardData.lastMove) || lastMove.value
      winningLine.value = normalizeWinningLine(boardData.winningLine)
    }

    const nextBoard = normalizeBoardMatrix(data.board)
    if (nextBoard) board.value = cloneBoard(nextBoard)

    const nextLastMove = normalizePoint(data.lastMove) || normalizePoint(data)
    if (nextLastMove) lastMove.value = nextLastMove

    if ('winningLine' in data) {
      winningLine.value = normalizeWinningLine(data.winningLine)
    }

    if (currentGame.value) {
      currentGame.value = {
        ...currentGame.value,
        currentTurn: Number(data.currentTurn ?? currentGame.value.currentTurn),
        gameStatus: Number(data.gameStatus ?? currentGame.value.gameStatus),
        winnerId: Number(data.winnerId ?? currentGame.value.winnerId) || currentGame.value.winnerId
      }
    }
  }

  async function connectWebSocket(): Promise<void> {
    if (!userStore.token) {
      throw new Error('请先登录')
    }

    gobangWebSocket.setCallbacks({
      onConnectionChange: (connected) => {
        isConnected.value = connected
        if (!connected) clearPlacing()
      },
      onError: (error) => {
        errorMessage.value = error.message
        showToast({ type: 'fail', message: error.message })
      },
      onMessage: handleMessage
    })
    await gobangWebSocket.connect()
  }

  function disconnectWebSocket(): void {
    leaveGameRoom()
    gobangWebSocket.disconnect()
    isConnected.value = false
  }

  function clearPlacing(): void {
    isPlacing.value = false
    if (placingTimeout) {
      clearTimeout(placingTimeout)
      placingTimeout = null
    }
  }

  function handleMessage(message: GobangMessage): void {
    const data = message.data

    switch (message.type) {
      case GobangMessageType.STATE_UPDATE:
        applyMessageData(data)
        clearPlacing()
        break
      case GobangMessageType.STONE_PLACED:
        applyMessageData(data)
        clearPlacing()
        // 棋谱已经过时，复盘时会重新拉取；这里先标记，避免对局中复盘按钮一直不可用
        replayStale = true
        break
      case GobangMessageType.UNDO_REQUESTED:
        {
          const nestedUndoGame = isRecord(data?.game) ? data.game : null
          const requesterId = normalizeOptionalNumber(data?.requesterId) ??
            normalizeOptionalNumber(nestedUndoGame?.pendingUndoRequesterId) ??
            normalizeOptionalNumber(message.senderId)
          const responderId = normalizeOptionalNumber(data?.responderId) ??
            normalizeOptionalNumber(nestedUndoGame?.pendingUndoResponderId)
          const targetMoveNumber = normalizeOptionalNumber(data?.targetMoveNumber) ??
            normalizeOptionalNumber(nestedUndoGame?.pendingUndoTargetMoveNumber)

          if (!requesterId) return

          undoRequest.value = {
            requesterId,
            responderId,
            targetMoveNumber
          }
        }
        if (undoRequest.value.requesterId !== userStore.userId) {
          showToast({ message: '对方请求悔棋', position: 'top' })
        }
        break
      case GobangMessageType.UNDO_RESOLVED:
        undoRequest.value = null
        applyMessageData(data)
        showToast({
          type: data?.accepted ? 'success' : 'text',
          message: data?.accepted ? '悔棋已同意' : '悔棋已拒绝',
          position: 'top'
        })
        break
      case GobangMessageType.PLAYER_SURRENDERED:
      case GobangMessageType.GAME_ENDED:
        applyMessageData(data)
        if (currentGame.value) {
          currentGame.value.gameStatus = GobangGameStatus.FINISHED
        }
        break
      case GobangMessageType.ERROR:
        // 错误会广播给双方，只提示发起操作的一方
        if (message.senderId && message.senderId !== userStore.userId) break
        clearPlacing()
        errorMessage.value = String(data?.error || data?.message || '五子棋操作失败')
        showToast({ type: 'fail', message: errorMessage.value })
        break
    }
  }

  let replayStale = false

  async function createGame(opponentUserId: number): Promise<GobangGame> {
    isLoading.value = true
    try {
      const game = await gobangApi.createGame(opponentUserId)
      applyGame(game)
      return game
    } finally {
      isLoading.value = false
    }
  }

  async function joinGame(gameCode: string): Promise<GobangGame> {
    isLoading.value = true
    try {
      const game = await gobangApi.joinGame(gameCode)
      applyGame(game)
      return game
    } finally {
      isLoading.value = false
    }
  }

  async function fetchGameDetail(gameId: number): Promise<GobangGame> {
    isLoading.value = true
    try {
      const game = await gobangApi.getGameDetail(gameId)
      applyGame(game)
      return game
    } finally {
      isLoading.value = false
    }
  }

  let historyRequest = 0
  async function fetchGameHistory(status?: number): Promise<void> {
    const id = ++historyRequest
    isLoading.value = true
    try {
      const response = await gobangApi.getUserGames(status)
      if (id === historyRequest) {
        gameHistory.value = Array.isArray(response) ? response : response.records || []
      }
    } finally {
      if (id === historyRequest) isLoading.value = false
    }
  }

  async function fetchReplayMoves(gameId?: number): Promise<GobangMove[]> {
    const targetGameId = gameId ?? currentGame.value?.id
    if (!targetGameId) return []

    const token = roomToken
    const moves = await gobangApi.getMoves(targetGameId)
    if (token !== roomToken) return []
    replayMoves.value = [...moves].sort((a, b) => a.moveNumber - b.moveNumber)
    replayIndex.value = replayMoves.value.length
    replayStale = false
    return replayMoves.value
  }

  /** 对局进行中棋谱会变；需要复盘时如果有新落子就重新拉一次 */
  async function ensureReplayMoves(): Promise<GobangMove[]> {
    if (replayStale || replayMoves.value.length === 0) {
      return fetchReplayMoves()
    }
    return replayMoves.value
  }

  async function fetchAiReview(gameId?: number): Promise<GobangAiReview | null> {
    const targetGameId = gameId ?? currentGame.value?.id
    if (!targetGameId) return null

    const token = roomToken
    isReviewLoading.value = true
    try {
      const review = await gobangApi.getAiReview(targetGameId)
      if (token !== roomToken) return null
      aiReview.value = review
      return review
    } catch (error: any) {
      if (token === roomToken) showToast({ type: 'fail', message: error.message || 'AI 复盘生成失败' })
      return null
    } finally {
      if (token === roomToken) isReviewLoading.value = false
    }
  }

  async function fetchSavedAiReview(gameId?: number): Promise<GobangAiReview | null> {
    const targetGameId = gameId ?? currentGame.value?.id
    if (!targetGameId) return null

    const token = roomToken
    try {
      const savedReview = await gobangApi.getSavedAiReview(targetGameId)
      if (token !== roomToken) return null
      // 没有保存过复盘时后端返回 null
      aiReview.value = savedReview && Array.isArray(savedReview.insights) ? savedReview : null
      return aiReview.value
    } catch {
      return null
    }
  }

  async function enterGameRoom(gameId: number): Promise<void> {
    const token = ++roomToken
    resetRoomState()

    const game = await gobangApi.getGameDetail(gameId)
    if (token !== roomToken) return
    await fetchReplayMoves(gameId).catch(() => [])
    if (token !== roomToken) return
    applyGame(game)
    void fetchSavedAiReview(gameId)

    try {
      await connectWebSocket()
    } catch {
      // 连接会在后台自动重试，连上后自动订阅；房间照常显示，状态栏提示正在重连
    }
    if (token !== roomToken) return
    gobangWebSocket.subscribeToGame(gameId)
  }

  function resetRoomState(): void {
    currentGame.value = null
    board.value = createEmptyGobangBoard()
    lastMove.value = null
    winningLine.value = []
    undoRequest.value = null
    replayMoves.value = []
    aiReview.value = null
    isReviewLoading.value = false
    isReplayMode.value = false
    replayIndex.value = 0
    replayStale = false
    clearPlacing()
  }

  function leaveGameRoom(): void {
    roomToken += 1
    gobangWebSocket.unsubscribeFromGame()
    resetRoomState()
  }

  function placeStone(row: number, col: number): void {
    if (isPlacing.value) return
    if (!currentGame.value || !isMyTurn.value) {
      showToast({ type: 'fail', message: '还没轮到你' })
      return
    }

    if (board.value[row]?.[col] !== GobangColor.EMPTY) {
      showToast({ type: 'fail', message: '这里已经有棋子了' })
      return
    }

    if (!gobangWebSocket.placeStone(currentGame.value.id, row, col)) {
      showToast({ type: 'fail', message: '连接已断开，正在重连' })
      return
    }
    // 等服务端确认前不接受第二次落子，避免连点下两手
    isPlacing.value = true
    placingTimeout = setTimeout(clearPlacing, 8000)
  }

  function requestUndo(): void {
    if (!currentGame.value || !isGamePlaying.value) return
    if (!gobangWebSocket.requestUndo(currentGame.value.id)) {
      showToast({ type: 'fail', message: '连接已断开，正在重连' })
    }
  }

  function respondUndo(accepted: boolean): boolean {
    if (!currentGame.value) return false
    // 弹窗等服务端广播 UNDO_RESOLVED 后再消失
    const sent = gobangWebSocket.respondUndo(currentGame.value.id, accepted)
    if (!sent) showToast({ type: 'fail', message: '连接已断开，正在重连' })
    return sent
  }

  function surrender(): void {
    if (!currentGame.value || !isGamePlaying.value) return
    if (!gobangWebSocket.surrender(currentGame.value.id)) {
      showToast({ type: 'fail', message: '连接已断开，正在重连' })
    }
  }

  async function cancelGame(): Promise<void> {
    if (!currentGame.value) return
    await gobangApi.cancelGame(currentGame.value.id)
    currentGame.value = null
  }

  function startReplay(): void {
    isReplayMode.value = true
    replayIndex.value = 0
  }

  function stopReplay(): void {
    isReplayMode.value = false
    replayIndex.value = replayMoves.value.length
  }

  function setReplayIndex(index: number): void {
    replayIndex.value = Math.max(0, Math.min(index, replayMoves.value.length))
  }

  function clearError(): void {
    errorMessage.value = null
  }

  return {
    currentGame,
    board,
    lastMove,
    winningLine,
    undoRequest,
    replayMoves,
    aiReview,
    replayIndex,
    replayBoard,
    replayLastMove,
    currentReplayMove,
    currentReplayInsight,
    displayAnalysisPoints,
    replayProgress,
    isReplayMode,
    isConnected,
    isLoading,
    isReviewLoading,
    isPlacing,
    errorMessage,
    gameHistory,
    currentTurnColor,
    myColor,
    isMyTurn,
    isRoomOwner,
    isGameWaiting,
    isGamePlaying,
    isGameFinished,
    isWinner,
    displayBoard,
    displayLastMove,
    displayWinningLine,
    connectWebSocket,
    disconnectWebSocket,
    createGame,
    joinGame,
    fetchGameDetail,
    fetchGameHistory,
    fetchReplayMoves,
    ensureReplayMoves,
    fetchAiReview,
    fetchSavedAiReview,
    enterGameRoom,
    leaveGameRoom,
    placeStone,
    requestUndo,
    respondUndo,
    surrender,
    cancelGame,
    startReplay,
    stopReplay,
    setReplayIndex,
    clearError
  }
})
