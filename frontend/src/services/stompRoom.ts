import SockJS from 'sockjs-client'
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'

// 走 Vite 代理的相对路径
const WS_URL = '/ws/game'
const HEARTBEAT_MS = 30000

export interface RoomCallbacks<M> {
  onConnectionChange?: (connected: boolean) => void
  onMessage?: (message: M) => void
  onError?: (error: Error) => void
}

/**
 * 飞行棋和五子棋共用的 STOMP 房间连接。
 *
 * - 断线后由 stompjs 自己按 reconnectDelay 重连，重连成功会自动重新订阅当前房间；
 * - 每次建立连接时现读 token，换号登录或 token 刷新后不会沿用旧身份；
 * - connect() 在连接过程中被重复调用时返回同一个 Promise，连接失败会 reject，不会一直挂起；
 * - 房间号在检查连接状态之前就记下，连接恢复后能补上订阅。
 */
export class StompRoomClient<M extends { type: string; senderId?: number }> {
  private client: Client | null = null
  private subscription: StompSubscription | null = null
  private roomId: number | null = null
  private callbacks: RoomCallbacks<M> = {}
  private connecting: Promise<void> | null = null
  private heartbeat: ReturnType<typeof setInterval> | null = null

  constructor(private readonly channel: 'game' | 'gobang') {}

  setCallbacks(callbacks: RoomCallbacks<M>): void {
    this.callbacks = callbacks
  }

  /**
   * 确保连接已建立。客户端一旦激活就会一直自动重连（直到 disconnect）；
   * 返回的 Promise 在连上时 resolve，在这一轮第一次连接失败时 reject，
   * 调用方可以提示"正在重连"，同时照常进入房间，连上后会自动订阅。
   */
  connect(): Promise<void> {
    if (this.client?.connected) return Promise.resolve()
    if (this.connecting) return this.connecting

    if (!this.client) this.createClient()

    this.connecting = new Promise<void>((resolve, reject) => {
      this.waiter = { resolve, reject }
    })
    return this.connecting
  }

  private waiter: { resolve: () => void; reject: (error: Error) => void } | null = null

  private settleWaiter(error?: Error): void {
    const waiter = this.waiter
    this.waiter = null
    this.connecting = null
    if (!waiter) return
    if (error) waiter.reject(error)
    else waiter.resolve()
  }

  private createClient(): void {
    const client = new Client({
      webSocketFactory: () => {
        const token = encodeURIComponent(localStorage.getItem('token') || '')
        return new SockJS(`${WS_URL}?token=${token}`)
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
      onConnect: () => {
        this.callbacks.onConnectionChange?.(true)
        if (this.roomId !== null) this.subscribeNow(this.roomId)
        this.settleWaiter()
      },
      onStompError: (frame) => {
        const error = new Error(frame.headers.message || '实时连接出错')
        if (this.waiter) this.settleWaiter(error)
        else this.callbacks.onError?.(error)
      },
      onWebSocketClose: () => {
        this.stopHeartbeat()
        // 连接断开后旧订阅随之失效，重连成功时 onConnect 会重新订阅
        this.subscription = null
        this.callbacks.onConnectionChange?.(false)
        this.settleWaiter(new Error('实时连接失败，正在重连'))
      },
    })
    this.client = client
    client.activate()
  }

  /** 进入房间：已连接就立即订阅，否则等连接建立后订阅 */
  enterRoom(roomId: number): void {
    this.roomId = roomId
    if (this.client?.connected) this.subscribeNow(roomId)
  }

  leaveRoom(): void {
    this.unsubscribe()
    this.roomId = null
    this.stopHeartbeat()
  }

  /** 发送到 /app/{channel}/{roomId}/{action}；未连接时返回 false，由调用方回滚界面状态 */
  send(roomId: number, action: string, body: Record<string, unknown> = {}): boolean {
    if (!this.client?.connected) return false
    this.client.publish({
      destination: `/app/${this.channel}/${roomId}/${action}`,
      body: JSON.stringify(body),
    })
    return true
  }

  disconnect(): void {
    this.leaveRoom()
    this.waiter = null
    this.connecting = null
    if (this.client) {
      void this.client.deactivate()
      this.client = null
    }
    this.callbacks.onConnectionChange?.(false)
  }

  isConnected(): boolean {
    return this.client?.connected ?? false
  }

  getCurrentRoomId(): number | null {
    return this.roomId
  }

  private subscribeNow(roomId: number): void {
    if (!this.client?.connected) return
    this.unsubscribe()
    this.subscription = this.client.subscribe(`/topic/${this.channel}/${roomId}`, (frame: IMessage) => {
      try {
        this.callbacks.onMessage?.(JSON.parse(frame.body) as M)
      } catch {
        this.callbacks.onError?.(new Error('收到无法解析的消息'))
      }
    })
    this.startHeartbeat(roomId)
    // 订阅后主动要一次最新状态，补上订阅之前错过的消息
    this.send(roomId, 'sync')
  }

  private unsubscribe(): void {
    try {
      this.subscription?.unsubscribe()
    } catch {
      // 连接已断开时取消订阅会失败，忽略即可
    }
    this.subscription = null
  }

  private startHeartbeat(roomId: number): void {
    this.stopHeartbeat()
    this.heartbeat = setInterval(() => this.send(roomId, 'heartbeat'), HEARTBEAT_MS)
  }

  private stopHeartbeat(): void {
    if (this.heartbeat) {
      clearInterval(this.heartbeat)
      this.heartbeat = null
    }
  }
}
