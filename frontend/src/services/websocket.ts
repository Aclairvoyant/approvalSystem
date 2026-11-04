import { StompRoomClient, type RoomCallbacks } from './stompRoom'

// 消息类型
export enum MessageType {
  CONNECTED = 'CONNECTED',
  DISCONNECTED = 'DISCONNECTED',
  PLAYER_JOINED = 'PLAYER_JOINED',
  PLAYER_LEFT = 'PLAYER_LEFT',
  GAME_STARTED = 'GAME_STARTED',
  GAME_ENDED = 'GAME_ENDED',
  GAME_STATE_UPDATE = 'GAME_STATE_UPDATE',
  DICE_ROLLED = 'DICE_ROLLED',
  PIECE_MOVED = 'PIECE_MOVED',
  PIECE_CAPTURED = 'PIECE_CAPTURED',
  TURN_CHANGED = 'TURN_CHANGED',
  TASK_TRIGGERED = 'TASK_TRIGGERED',
  TASK_COMPLETED = 'TASK_COMPLETED',
  TASK_ABANDONED = 'TASK_ABANDONED',
  TASK_TIMEOUT = 'TASK_TIMEOUT',
  ERROR = 'ERROR',
  HEARTBEAT = 'HEARTBEAT',
  SYNC_REQUEST = 'SYNC_REQUEST',
  SYNC_RESPONSE = 'SYNC_RESPONSE'
}

// 游戏消息接口
export interface GameMessage {
  type: MessageType
  gameId: number
  senderId?: number
  senderName?: string
  data?: Record<string, any>
  timestamp?: string
}

export type WebSocketCallbacks = RoomCallbacks<GameMessage>

/** 飞行棋房间的 STOMP 连接 */
class GameWebSocket extends StompRoomClient<GameMessage> {
  constructor() {
    super('game')
  }

  subscribeToGame(gameId: number): void {
    this.enterRoom(gameId)
  }

  unsubscribeFromGame(): void {
    this.leaveRoom()
  }

  rollDice(gameId: number): boolean {
    return this.send(gameId, 'roll-dice')
  }

  movePiece(gameId: number, pieceIndex: number, diceResult: number): boolean {
    return this.send(gameId, 'move-piece', { pieceIndex, diceResult })
  }

  completeTask(gameId: number, recordId: number, completionNote?: string): boolean {
    return this.send(gameId, 'complete-task', { recordId, completionNote: completionNote || '' })
  }

  abandonTask(gameId: number, recordId: number): boolean {
    return this.send(gameId, 'abandon-task', { recordId })
  }

  syncGameState(gameId: number): boolean {
    return this.send(gameId, 'sync')
  }
}

export const gameWebSocket = new GameWebSocket()

export default gameWebSocket
