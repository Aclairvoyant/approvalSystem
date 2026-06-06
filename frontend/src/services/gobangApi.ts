import http from './http'

export const GOBANG_BOARD_SIZE = 15

export enum GobangGameStatus {
  WAITING = 1,
  PLAYING = 2,
  FINISHED = 3,
  CANCELLED = 4
}

export enum GobangColor {
  EMPTY = 0,
  BLACK = 1,
  WHITE = 2
}

export type GobangCell = GobangColor.EMPTY | GobangColor.BLACK | GobangColor.WHITE

export interface GobangPoint {
  row: number
  col: number
}

export interface GobangAiPoint extends GobangPoint {
  label?: string | null
  type?: 'WINNING_MOVE' | 'BLOCK' | 'BETTER_MOVE' | 'COMMENTARY' | string | null
  score?: number | null
}

export interface GobangAiMoveInsight {
  moveNumber: number
  playerColor?: number | null
  rowIndex?: number | null
  colIndex?: number | null
  winRate?: number | null
  bestMoveWinRate?: number | null
  severity?: 'good' | 'info' | 'warning' | 'critical' | string | null
  summary?: string | null
  suggestedPoints?: GobangAiPoint[]
}

export interface GobangAiReview {
  gameId: number
  source: 'mimo' | 'heuristic' | string
  overview?: string | null
  insights: GobangAiMoveInsight[]
  generatedAt?: string | null
}

export interface GobangBoardData {
  board?: GobangCell[][]
  matrix?: GobangCell[][]
  lastMove?: GobangPoint | { rowIndex: number; colIndex: number }
  winningLine?: Array<GobangPoint | { rowIndex: number; colIndex: number }>
}

export interface GobangGame {
  id: number
  gameCode: string
  blackPlayerId: number
  blackPlayerName?: string | null
  blackPlayerAvatar?: string | null
  invitedPlayerId?: number | null
  invitedPlayerName?: string | null
  invitedPlayerAvatar?: string | null
  whitePlayerId?: number | null
  whitePlayerName?: string | null
  whitePlayerAvatar?: string | null
  currentTurn: number
  gameStatus: GobangGameStatus | number
  winnerId?: number | null
  board?: GobangCell[][]
  boardData?: GobangBoardData | string | null
  lastMove?: GobangPoint | { rowIndex: number; colIndex: number } | null
  winningLine?: Array<GobangPoint | { rowIndex: number; colIndex: number }> | null
  pendingUndoRequesterId?: number | null
  pendingUndoResponderId?: number | null
  pendingUndoTargetMoveNumber?: number | null
  createdAt: string
  startedAt?: string | null
  endedAt?: string | null
  updatedAt?: string | null
}

export type GobangMoveType = 'PLACE_STONE' | 'UNDO' | 'SURRENDER' | 'SYSTEM_END'

export interface GobangMove {
  id: number
  gameId: number
  playerId?: number | null
  playerName?: string | null
  moveNumber: number
  moveType: GobangMoveType | string
  rowIndex?: number | null
  colIndex?: number | null
  row?: number | null
  col?: number | null
  color?: GobangColor | number | null
  moveData?: Record<string, unknown> | string | null
  createdAt: string
}

export interface PageGobangGame {
  records: GobangGame[]
  total: number
  size: number
  current: number
  pages: number
}

export interface GobangCreateRequest {
  opponentUserId: number
}

export interface GobangJoinRequest {
  gameCode: string
}

export interface GobangMoveRequest {
  row: number
  col: number
}

export interface GobangUndoResponseRequest {
  accepted: boolean
}

export const gobangApi = {
  createGame(opponentUserId: number) {
    return http.post<GobangGame>('/gobang/create', { opponentUserId })
  },

  joinGame(gameCode: string) {
    return http.post<GobangGame>('/gobang/join', { gameCode })
  },

  getGameDetail(gameId: number) {
    return http.get<GobangGame>(`/gobang/${gameId}`)
  },

  getUserGames(status?: number, pageNum = 1, pageSize = 10) {
    return http.get<PageGobangGame | GobangGame[]>('/gobang/list', {
      params: { status, pageNum, pageSize }
    })
  },

  getMoves(gameId: number) {
    return http.get<GobangMove[]>(`/gobang/${gameId}/moves`)
  },

  getAiReview(gameId: number) {
    return http.get<GobangAiReview>(`/gobang/${gameId}/ai-review`, {
      timeout: 120000
    })
  },

  getSavedAiReview(gameId: number) {
    return http.get<GobangAiReview | null>(`/gobang/${gameId}/ai-review/saved`)
  },

  cancelGame(gameId: number) {
    return http.post<void>(`/gobang/${gameId}/cancel`)
  }
}

export function createEmptyGobangBoard(): GobangCell[][] {
  return Array.from({ length: GOBANG_BOARD_SIZE }, () =>
    Array.from({ length: GOBANG_BOARD_SIZE }, () => GobangColor.EMPTY as GobangCell)
  )
}
