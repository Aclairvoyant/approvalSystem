import { StompRoomClient, type RoomCallbacks } from './stompRoom'

export enum MahjongMessageType {
  /** 房间公共视角：/topic/mahjong/{id} */
  ROOM = 'MAHJONG_ROOM',
  /** 只发给自己的手牌和可选操作：/user/queue/mahjong/{id} */
  HAND = 'MAHJONG_HAND',
  /** 主动同步时返回的完整个人视角 */
  SYNC = 'MAHJONG_SYNC',
  /** 只发给操作者本人的错误 */
  ERROR = 'MAHJONG_ERROR'
}

export interface MahjongMessage {
  type: MahjongMessageType | string
  gameId: number
  version?: number
  serverTime?: number
  senderId?: number
  data?: Record<string, unknown>
  timestamp?: string
}

export type MahjongWebSocketCallbacks = RoomCallbacks<MahjongMessage>

/** 麻将房间的 STOMP 连接：房间 topic + 自己的私有队列 */
class MahjongWebSocket extends StompRoomClient<MahjongMessage> {
  constructor() {
    super('mahjong', { userQueue: true })
  }

  act(gameId: number, body: Record<string, unknown>): boolean {
    return this.send(gameId, 'action', body)
  }

  sync(gameId: number): boolean {
    return this.send(gameId, 'sync')
  }
}

export const mahjongWebSocket = new MahjongWebSocket()

export default mahjongWebSocket
