import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/services/http'

// 麻将游戏状态
interface MahjongGame {
  id: number
  gameCode: string
  ruleType: number
  ruleTypeName: string
  flowerMode: number
  playerCount: number
  totalRounds: number
  baseScore: number
  maxScore: number | null
  flyCount: number
  wildTile?: string
  guideTile?: string
  dice1?: number
  dice2?: number
  player1Id?: number
  player1Name?: string
  player1Avatar?: string
  player2Id?: number
  player2Name?: string
  player2Avatar?: string
  player3Id?: number
  player3Name?: string
  player3Avatar?: string
  player4Id?: number
  player4Name?: string
  player4Avatar?: string
  gameStatus: number
  gameStatusName: string
  currentRound: number
  dealerSeat: number
  player1Score: number
  player2Score: number
  player3Score: number
  player4Score: number
  currentRoundData?: MahjongRound
  createdAt: string
  startedAt?: string
  endedAt?: string
}

interface MahjongRound {
  id: number
  roundNumber: number
  roundStatus: number
  dealerSeat: number
  currentTurn: number
  wallRemaining: number
  myHand?: string[]
  mySeat?: number
  player1Melds?: MeldInfo[]
  player2Melds?: MeldInfo[]
  player3Melds?: MeldInfo[]
  player4Melds?: MeldInfo[]
  player1Discards?: string[]
  player2Discards?: string[]
  player3Discards?: string[]
  player4Discards?: string[]
  player1Flowers?: string[]
  player2Flowers?: string[]
  player3Flowers?: string[]
  player4Flowers?: string[]
  player1HandCount?: number
  player2HandCount?: number
  player3HandCount?: number
  player4HandCount?: number
  lastTile?: string
  lastAction?: string
  lastActionSeat?: number
  availableActions?: string[]
  winnerSeat?: number
  huType?: string
  fanCount?: number
  scoreChanges?: Record<string, number>
}

interface MeldInfo {
  type: 'PONG' | 'MING_KONG' | 'AN_KONG' | 'BU_KONG'
  tiles: string[]
  concealed?: boolean
}

interface CreateGameRequest {
  ruleType: number
  flowerMode?: number
  playerCount?: number
  totalRounds?: number
  baseScore?: number
  maxScore?: number | null
  flyCount?: number
}

interface ActionRequest {
  actionType: string
  tile?: string
  extraData?: string
}

export const useMahjongStore = defineStore('mahjong', () => {
  // 当前游戏状态
  const currentGame = ref<MahjongGame | null>(null)
  const currentRound = ref<MahjongRound | null>(null)

  // 创建游戏
  async function createGame(request: CreateGameRequest): Promise<MahjongGame> {
    const game = await http.post<any>('/mahjong/create', request)
    currentGame.value = game
    return game
  }

  // 加入游戏
  async function joinGame(gameCode: string): Promise<MahjongGame> {
    const game = await http.post<any>('/mahjong/join', { gameCode })
    currentGame.value = game
    return game
  }

  // 离开游戏
  async function leaveGame(gameId: number): Promise<void> {
    await http.post(`/mahjong/${gameId}/leave`)
    currentGame.value = null
    currentRound.value = null
  }

  // 开始游戏
  async function startGame(gameId: number): Promise<MahjongGame> {
    const game = await http.post<any>(`/mahjong/${gameId}/start`)
    currentGame.value = game
    currentRound.value = game.currentRoundData
    return game
  }

  // 获取游戏状态
  async function getGameState(gameId: number): Promise<MahjongGame> {
    const game = await http.get<any>(`/mahjong/${gameId}`)
    currentGame.value = game
    currentRound.value = game?.currentRoundData
    return game
  }

  // 通过房间号获取游戏
  async function getGameByCode(gameCode: string): Promise<MahjongGame> {
    const game = await http.get<any>(`/mahjong/code/${gameCode}`)
    return game
  }

  // 执行操作
  async function executeAction(gameId: number, request: ActionRequest): Promise<MahjongGame> {
    const game = await http.post<any>(`/mahjong/${gameId}/action`, request)
    currentGame.value = game
    currentRound.value = game?.currentRoundData
    return game
  }

  // 获取我的游戏列表
  async function getMyGames(): Promise<MahjongGame[]> {
    const games = await http.get<any>('/mahjong/my-games')
    return games || []
  }

  // 获取当前进行中的游戏
  async function getActiveGame(): Promise<MahjongGame | null> {
    const game = await http.get<any>('/mahjong/active')
    if (game) {
      currentGame.value = game
      currentRound.value = game.currentRoundData
    }
    return game
  }

  // 开始下一局
  async function nextRound(gameId: number): Promise<MahjongGame> {
    const game = await http.post<any>(`/mahjong/${gameId}/next-round`)
    currentGame.value = game
    currentRound.value = game?.currentRoundData
    return game
  }

  // 更新游戏状态（从WebSocket）
  function updateGameState(game: MahjongGame) {
    currentGame.value = game
    currentRound.value = game.currentRoundData || null
  }

  // 清除当前游戏
  function clearGame() {
    currentGame.value = null
    currentRound.value = null
  }

  return {
    currentGame,
    currentRound,
    createGame,
    joinGame,
    leaveGame,
    startGame,
    getGameState,
    getGameByCode,
    executeAction,
    getMyGames,
    getActiveGame,
    nextRound,
    updateGameState,
    clearGame
  }
})
