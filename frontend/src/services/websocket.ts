import SockJS from 'sockjs-client'
import { Client, IMessage, StompSubscription } from '@stomp/stompjs'

// WebSocket服务器地址 - 使用相对路径走Vite代理
const WS_URL = '/ws/game'

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

// WebSocket事件回调
export interface WebSocketCallbacks {
  onConnected?: () => void
  onDisconnected?: () => void
  onError?: (error: any) => void
  onMessage?: (message: GameMessage) => void
}

/**
 * 游戏WebSocket客户端
 * 封装STOMP协议的WebSocket通信
 */
class GameWebSocket {
  private client: Client | null = null
  private subscription: StompSubscription | null = null
  private currentGameId: number | null = null
  private callbacks: WebSocketCallbacks = {}
  private reconnectAttempts = 0
  private maxReconnectAttempts = 5
  private reconnectDelay = 3000
  private heartbeatInterval: ReturnType<typeof setInterval> | null = null
  private isConnecting = false

  /**
   * 连接WebSocket服务器
   * @param token JWT token用于认证
   * @param callbacks 事件回调函数
   */
  connect(token: string, callbacks: WebSocketCallbacks = {}): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.client?.connected) {
        resolve()
        return
      }

      if (this.isConnecting) {
        reject(new Error('正在连接中...'))
        return
      }

      this.isConnecting = true
      this.callbacks = callbacks

      // 创建STOMP客户端
      this.client = new Client({
        // 使用SockJS作为WebSocket工厂
        webSocketFactory: () => new SockJS(`${WS_URL}?token=${token}`),

        // 连接成功回调
        onConnect: () => {
          console.log('WebSocket连接成功')
          this.isConnecting = false
          this.reconnectAttempts = 0
          this.callbacks.onConnected?.()
          resolve()
        },

        // 连接断开回调
        onDisconnect: () => {
          console.log('WebSocket连接断开')
          this.isConnecting = false
          this.stopHeartbeat()
          this.callbacks.onDisconnected?.()
        },

        // STOMP错误回调
        onStompError: (frame) => {
          console.error('STOMP错误:', frame.headers['message'])
          this.isConnecting = false
          this.callbacks.onError?.(new Error(frame.headers['message']))
          reject(new Error(frame.headers['message']))
        },

        // WebSocket错误回调
        onWebSocketError: (event) => {
          console.error('WebSocket错误:', event)
          this.isConnecting = false
          this.handleDisconnect()
        },

        // WebSocket关闭回调
        onWebSocketClose: (event) => {
          console.log('WebSocket关闭:', event)
          this.isConnecting = false
          this.handleDisconnect()
        },

        // 心跳配置
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,

        // 调试输出（开发环境启用）
        debug: import.meta.env.DEV ? (str) => console.log('STOMP:', str) : () => {}
      })

      // 激活连接
      this.client.activate()
    })
  }

  /**
   * 订阅游戏房间
   * @param gameId 游戏ID
   */
  subscribeToGame(gameId: number): void {
    if (!this.client?.connected) {
      console.error('WebSocket未连接')
      return
    }

    // 如果已订阅其他游戏，先取消订阅
    if (this.subscription) {
      this.subscription.unsubscribe()
    }

    this.currentGameId = gameId

    // 订阅游戏房间频道
    this.subscription = this.client.subscribe(
      `/topic/game/${gameId}`,
      (message: IMessage) => {
        try {
          const gameMessage: GameMessage = JSON.parse(message.body)
          console.log('收到游戏消息:', gameMessage.type, gameMessage)
          this.callbacks.onMessage?.(gameMessage)
        } catch (error) {
          console.error('解析消息失败:', error)
        }
      }
    )

    console.log('已订阅游戏房间:', gameId)

    // 启动心跳
    this.startHeartbeat(gameId)

    // 发送同步请求
    this.syncGameState(gameId)
  }

  /**
   * 取消订阅游戏房间
   */
  unsubscribeFromGame(): void {
    if (this.subscription) {
      this.subscription.unsubscribe()
      this.subscription = null
    }
    this.currentGameId = null
    this.stopHeartbeat()
    console.log('已取消订阅游戏房间')
  }

  /**
   * 掷骰子
   * @param gameId 游戏ID
   */
  rollDice(gameId: number): void {
    this.sendMessage(`/app/game/${gameId}/roll-dice`, {})
  }

  /**
   * 移动棋子
   * @param gameId 游戏ID
   * @param pieceIndex 棋子索引（0-3）
   * @param diceResult 骰子结果
   */
  movePiece(gameId: number, pieceIndex: number, diceResult: number): void {
    this.sendMessage(`/app/game/${gameId}/move-piece`, {
      pieceIndex,
      diceResult
    })
  }

  /**
   * 完成任务
   * @param gameId 游戏ID
   * @param recordId 任务记录ID
   * @param completionNote 完成备注（可选）
   */
  completeTask(gameId: number, recordId: number, completionNote?: string): void {
    this.sendMessage(`/app/game/${gameId}/complete-task`, {
      recordId,
      completionNote: completionNote || ''
    })
  }

  /**
   * 放弃任务
   * @param gameId 游戏ID
   * @param recordId 任务记录ID
   */
  abandonTask(gameId: number, recordId: number): void {
    this.sendMessage(`/app/game/${gameId}/abandon-task`, {
      recordId
    })
  }

  /**
   * 同步游戏状态
   * @param gameId 游戏ID
   */
  syncGameState(gameId: number): void {
    this.sendMessage(`/app/game/${gameId}/sync`, {})
  }

  /**
   * 发送心跳
   * @param gameId 游戏ID
   */
  sendHeartbeat(gameId: number): void {
    this.sendMessage(`/app/game/${gameId}/heartbeat`, {})
  }

  /**
   * 发送消息到服务器
   * @param destination 目标地址
   * @param body 消息内容
   */
  private sendMessage(destination: string, body: Record<string, any>): void {
    if (!this.client?.connected) {
      console.error('WebSocket未连接，无法发送消息')
      return
    }

    this.client.publish({
      destination,
      body: JSON.stringify(body)
    })
  }

  /**
   * 启动心跳
   * @param gameId 游戏ID
   */
  private startHeartbeat(gameId: number): void {
    this.stopHeartbeat()
    this.heartbeatInterval = setInterval(() => {
      this.sendHeartbeat(gameId)
    }, 30000) // 每30秒发送一次心跳
  }

  /**
   * 停止心跳
   */
  private stopHeartbeat(): void {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval)
      this.heartbeatInterval = null
    }
  }

  /**
   * 处理连接断开
   */
  private handleDisconnect(): void {
    this.stopHeartbeat()

    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++
      console.log(`尝试重新连接 (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`)

      setTimeout(() => {
        if (this.client && !this.client.connected && !this.isConnecting) {
          this.client.activate()
        }
      }, this.reconnectDelay)
    } else {
      console.error('重连失败，已达到最大重连次数')
      this.callbacks.onError?.(new Error('连接失败，请刷新页面重试'))
    }
  }

  /**
   * 断开连接
   */
  disconnect(): void {
    this.stopHeartbeat()

    if (this.subscription) {
      this.subscription.unsubscribe()
      this.subscription = null
    }

    if (this.client) {
      this.client.deactivate()
      this.client = null
    }

    this.currentGameId = null
    this.reconnectAttempts = 0
    console.log('WebSocket已断开')
  }

  /**
   * 检查是否已连接
   */
  isConnected(): boolean {
    return this.client?.connected ?? false
  }

  /**
   * 获取当前订阅的游戏ID
   */
  getCurrentGameId(): number | null {
    return this.currentGameId
  }
}

// 导出单例实例
export const gameWebSocket = new GameWebSocket()

export default gameWebSocket
