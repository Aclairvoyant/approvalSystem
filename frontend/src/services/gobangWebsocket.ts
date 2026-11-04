import { StompRoomClient, type RoomCallbacks } from './stompRoom'

export enum GobangMessageType {
  STATE_UPDATE = 'GOBANG_STATE_UPDATE',
  STONE_PLACED = 'GOBANG_STONE_PLACED',
  UNDO_REQUESTED = 'GOBANG_UNDO_REQUESTED',
  UNDO_RESOLVED = 'GOBANG_UNDO_RESOLVED',
  PLAYER_SURRENDERED = 'GOBANG_PLAYER_SURRENDERED',
  GAME_ENDED = 'GOBANG_GAME_ENDED',
  ERROR = 'GOBANG_ERROR'
}

export interface GobangMessage {
  type: GobangMessageType | string
  gameId: number
  senderId?: number
  senderName?: string
  data?: Record<string, unknown>
  timestamp?: string
}

export type GobangWebSocketCallbacks = RoomCallbacks<GobangMessage>

/** 五子棋房间的 STOMP 连接 */
class GobangWebSocket extends StompRoomClient<GobangMessage> {
  constructor() {
    super('gobang')
  }

  subscribeToGame(gameId: number): void {
    this.enterRoom(gameId)
  }

  unsubscribeFromGame(): void {
    this.leaveRoom()
  }

  placeStone(gameId: number, row: number, col: number): boolean {
    return this.send(gameId, 'place-stone', { row, col })
  }

  requestUndo(gameId: number): boolean {
    return this.send(gameId, 'undo/request')
  }

  respondUndo(gameId: number, accepted: boolean): boolean {
    return this.send(gameId, 'undo/respond', { accepted })
  }

  surrender(gameId: number): boolean {
    return this.send(gameId, 'surrender')
  }

  syncGameState(gameId: number): boolean {
    return this.send(gameId, 'sync')
  }
}

export const gobangWebSocket = new GobangWebSocket()

export default gobangWebSocket
