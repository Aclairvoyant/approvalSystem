import SockJS from 'sockjs-client'
import { Client, IMessage, StompSubscription } from '@stomp/stompjs'

const WS_URL = '/ws/game'

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

export interface GobangWebSocketCallbacks {
  onConnected?: () => void
  onDisconnected?: () => void
  onError?: (error: Error) => void
  onMessage?: (message: GobangMessage) => void
}

class GobangWebSocket {
  private client: Client | null = null
  private subscription: StompSubscription | null = null
  private currentGameId: number | null = null
  private callbacks: GobangWebSocketCallbacks = {}
  private reconnectAttempts = 0
  private readonly maxReconnectAttempts = 5
  private readonly reconnectDelay = 3000
  private heartbeatInterval: ReturnType<typeof setInterval> | null = null
  private isConnecting = false

  connect(token: string, callbacks: GobangWebSocketCallbacks = {}): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.client?.connected) {
        this.callbacks = callbacks
        resolve()
        return
      }

      if (this.isConnecting) {
        reject(new Error('WebSocket is connecting'))
        return
      }

      this.isConnecting = true
      this.callbacks = callbacks

      this.client = new Client({
        webSocketFactory: () => new SockJS(`${WS_URL}?token=${token}`),
        onConnect: () => {
          this.isConnecting = false
          this.reconnectAttempts = 0
          const gameId = this.currentGameId
          this.callbacks.onConnected?.()
          if (gameId !== null) {
            this.subscribeToGame(gameId)
          }
          resolve()
        },
        onDisconnect: () => {
          this.isConnecting = false
          this.stopHeartbeat()
          this.callbacks.onDisconnected?.()
        },
        onStompError: (frame) => {
          const message = frame.headers.message || 'Gobang WebSocket error'
          this.isConnecting = false
          const error = new Error(message)
          this.callbacks.onError?.(error)
          reject(error)
        },
        onWebSocketError: () => {
          this.isConnecting = false
          this.handleDisconnect()
        },
        onWebSocketClose: () => {
          this.isConnecting = false
          this.handleDisconnect()
        },
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        debug: () => {}
      })

      this.client.activate()
    })
  }

  subscribeToGame(gameId: number): void {
    if (!this.client?.connected) {
      this.callbacks.onError?.(new Error('WebSocket is not connected'))
      return
    }

    this.subscription?.unsubscribe()
    this.currentGameId = gameId

    this.subscription = this.client.subscribe(`/topic/gobang/${gameId}`, (message: IMessage) => {
      try {
        const parsedMessage: GobangMessage = JSON.parse(message.body)
        this.callbacks.onMessage?.(parsedMessage)
      } catch {
        this.callbacks.onError?.(new Error('Failed to parse Gobang message'))
      }
    })

    this.startHeartbeat(gameId)
    this.syncGameState(gameId)
  }

  unsubscribeFromGame(): void {
    this.subscription?.unsubscribe()
    this.subscription = null
    this.currentGameId = null
    this.stopHeartbeat()
  }

  placeStone(gameId: number, row: number, col: number): void {
    this.sendMessage(`/app/gobang/${gameId}/place-stone`, { row, col })
  }

  requestUndo(gameId: number): void {
    this.sendMessage(`/app/gobang/${gameId}/undo/request`, {})
  }

  respondUndo(gameId: number, accepted: boolean): void {
    this.sendMessage(`/app/gobang/${gameId}/undo/respond`, { accepted })
  }

  surrender(gameId: number): void {
    this.sendMessage(`/app/gobang/${gameId}/surrender`, {})
  }

  syncGameState(gameId: number): void {
    this.sendMessage(`/app/gobang/${gameId}/sync`, {})
  }

  sendHeartbeat(gameId: number): void {
    this.sendMessage(`/app/gobang/${gameId}/heartbeat`, {})
  }

  disconnect(): void {
    this.stopHeartbeat()
    this.subscription?.unsubscribe()
    this.subscription = null

    if (this.client) {
      this.client.deactivate()
      this.client = null
    }

    this.currentGameId = null
    this.reconnectAttempts = 0
    this.isConnecting = false
  }

  isConnected(): boolean {
    return this.client?.connected ?? false
  }

  getCurrentGameId(): number | null {
    return this.currentGameId
  }

  private sendMessage(destination: string, body: Record<string, unknown>): void {
    if (!this.client?.connected) {
      this.callbacks.onError?.(new Error('WebSocket is not connected'))
      return
    }

    this.client.publish({
      destination,
      body: JSON.stringify(body)
    })
  }

  private startHeartbeat(gameId: number): void {
    this.stopHeartbeat()
    this.heartbeatInterval = setInterval(() => {
      this.sendHeartbeat(gameId)
    }, 30000)
  }

  private stopHeartbeat(): void {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval)
      this.heartbeatInterval = null
    }
  }

  private handleDisconnect(): void {
    this.stopHeartbeat()

    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts += 1
      setTimeout(() => {
        if (this.client && !this.client.connected && !this.isConnecting) {
          this.client.activate()
        }
      }, this.reconnectDelay)
      return
    }

    this.callbacks.onDisconnected?.()
    this.callbacks.onError?.(new Error('Gobang WebSocket disconnected'))
  }
}

export const gobangWebSocket = new GobangWebSocket()

export default gobangWebSocket
