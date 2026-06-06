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
    lastMove.value = normalizePoint(game.lastMove) || normalizePoint(boardData?.lastMove)
    const topLevelWinningLine = normalizeWinningLine(game.winningLine)
    winningLine.value = topLevelWinningLine.length > 0
      ? topLevelWinningLine
      : normalizeWinningLine(boardData?.winningLine)
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

    await gobangWebSocket.connect(userStore.token, {
      onConnected: () => {
        isConnected.value = true
      },
      onDisconnected: () => {
        isConnected.value = false
      },
      onError: (error) => {
        errorMessage.value = error.message
        showToast({ type: 'fail', message: error.message })
      },
      onMessage: handleMessage
    })
  }

  function disconnectWebSocket(): void {
    gobangWebSocket.disconnect()
    isConnected.value = false
  }

  function handleMessage(message: GobangMessage): void {
    const data = message.data

    switch (message.type) {
      case GobangMessageType.STATE_UPDATE:
      case GobangMessageType.STONE_PLACED:
        applyMessageData(data)
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
        errorMessage.value = String(data?.error || data?.message || '五子棋操作失败')
        showToast({ type: 'fail', message: errorMessage.value })
        break
    }
  }

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

  async function fetchGameHistory(status?: number): Promise<void> {
    isLoading.value = true
    try {
      const response = await gobangApi.getUserGames(status)
      gameHistory.value = Array.isArray(response) ? response : response.records || []
    } finally {
      isLoading.value = false
    }
  }

  async function fetchReplayMoves(gameId?: number): Promise<GobangMove[]> {
    const targetGameId = gameId ?? currentGame.value?.id
    if (!targetGameId) return []

    const moves = await gobangApi.getMoves(targetGameId)
    replayMoves.value = [...moves].sort((a, b) => a.moveNumber - b.moveNumber)
    replayIndex.value = replayMoves.value.length
    return replayMoves.value
  }

  async function fetchAiReview(gameId?: number): Promise<GobangAiReview | null> {
    const targetGameId = gameId ?? currentGame.value?.id
    if (!targetGameId) return null

    isReviewLoading.value = true
    try {
      aiReview.value = await gobangApi.getAiReview(targetGameId)
      return aiReview.value
    } catch (error: any) {
      showToast({ type: 'fail', message: error.message || 'AI 复盘生成失败' })
      return null
    } finally {
      isReviewLoading.value = false
    }
  }

  async function fetchSavedAiReview(gameId?: number): Promise<GobangAiReview | null> {
    const targetGameId = gameId ?? currentGame.value?.id
    if (!targetGameId) return null

    try {
      const savedReview = await gobangApi.getSavedAiReview(targetGameId)
      aiReview.value = savedReview
      return savedReview
    } catch {
      return null
    }
  }

  async function enterGameRoom(gameId: number): Promise<void> {
    await fetchGameDetail(gameId)
    await fetchReplayMoves(gameId)
    await fetchSavedAiReview(gameId)

    if (!isConnected.value) {
      await connectWebSocket()
    }

    gobangWebSocket.subscribeToGame(gameId)
  }

  function leaveGameRoom(): void {
    gobangWebSocket.unsubscribeFromGame()
    currentGame.value = null
    board.value = createEmptyGobangBoard()
    lastMove.value = null
    winningLine.value = []
    undoRequest.value = null
    aiReview.value = null
    isReplayMode.value = false
    replayIndex.value = 0
  }

  function placeStone(row: number, col: number): void {
    if (!currentGame.value || !isMyTurn.value) {
      showToast({ type: 'fail', message: '还没轮到你' })
      return
    }

    if (board.value[row]?.[col] !== GobangColor.EMPTY) {
      showToast({ type: 'fail', message: '这里已经有棋子了' })
      return
    }

    gobangWebSocket.placeStone(currentGame.value.id, row, col)
  }

  function requestUndo(): void {
    if (!currentGame.value || !isGamePlaying.value) return
    gobangWebSocket.requestUndo(currentGame.value.id)
  }

  function respondUndo(accepted: boolean): void {
    if (!currentGame.value) return
    gobangWebSocket.respondUndo(currentGame.value.id, accepted)
    undoRequest.value = null
  }

  function surrender(): void {
    if (!currentGame.value || !isGamePlaying.value) return
    gobangWebSocket.surrender(currentGame.value.id)
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
