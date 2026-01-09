import SockJS from 'sockjs-client'
import { Client, Message } from '@stomp/stompjs'
import { useUserStore } from '@/store/modules/user'

let stompClient: Client | null = null
let currentGameId: number | null = null

interface MahjongWebSocketCallbacks {
  onGameState?: (state: any) => void
  onPlayerJoined?: (data: any) => void
  onPlayerLeft?: (data: any) => void
  onGameStarted?: (data: any) => void
  onActionExecuted?: (data: any) => void
  onRoundStarted?: (data: any) => void
  onRoundEnded?: (data: any) => void
  onError?: (error: string) => void
}

/**
 * 连接麻将游戏WebSocket
 */
export function connectMahjongWebSocket(gameId: number, callbacks: MahjongWebSocketCallbacks) {
  // 如果已经连接到同一个游戏，不重复连接
  if (stompClient?.active && currentGameId === gameId) {
    return
  }

  // 断开之前的连接
  disconnectMahjongWebSocket()

  currentGameId = gameId
  const userStore = useUserStore()
  const token = userStore.token

  // 创建STOMP客户端
  stompClient = new Client({
    webSocketFactory: () => new SockJS(`/ws/game?token=${token}`),
    connectHeaders: {
      Authorization: `Bearer ${token}`
    },
    debug: (str) => {
      console.debug('[Mahjong WS]', str)
    },
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000
  })

  stompClient.onConnect = () => {
    console.log('[Mahjong WS] Connected')

    // 订阅游戏频道
    stompClient?.subscribe(`/topic/mahjong/game/${gameId}`, (message: Message) => {
      handleMessage(message, callbacks)
    })

    // 发送加入消息
    stompClient?.publish({
      destination: `/app/mahjong/${gameId}/join`
    })
  }

  stompClient.onStompError = (frame) => {
    console.error('[Mahjong WS] STOMP error:', frame)
    callbacks.onError?.('WebSocket连接错误')
  }

  stompClient.onWebSocketClose = () => {
    console.log('[Mahjong WS] WebSocket closed')
  }

  stompClient.activate()
}

/**
 * 断开WebSocket连接
 */
export function disconnectMahjongWebSocket() {
  if (stompClient) {
    stompClient.deactivate()
    stompClient = null
    currentGameId = null
  }
}

/**
 * 处理WebSocket消息
 */
function handleMessage(message: Message, callbacks: MahjongWebSocketCallbacks) {
  try {
    const data = JSON.parse(message.body)
    console.log('[Mahjong WS] Received:', data)

    switch (data.type) {
      case 'GAME_STATE':
        if (data.gameState) {
          callbacks.onGameState?.(data.gameState)
        }
        break

      case 'GAME_STATE_UPDATE':
        // 通知前端需要刷新状态（通过REST API）
        callbacks.onGameState?.(null) // null表示需要重新加载
        break

      case 'PLAYER_JOINED':
        callbacks.onPlayerJoined?.(data)
        break

      case 'PLAYER_LEFT':
        callbacks.onPlayerLeft?.(data)
        break

      case 'GAME_STARTED':
        callbacks.onGameStarted?.(data)
        break

      case 'ACTION_EXECUTED':
        callbacks.onActionExecuted?.(data)
        break

      case 'ROUND_STARTED':
        callbacks.onRoundStarted?.(data)
        break

      case 'ROUND_ENDED':
        callbacks.onRoundEnded?.(data)
        break

      case 'ERROR':
        callbacks.onError?.(data.error || '发生错误')
        break

      default:
        console.warn('[Mahjong WS] Unknown message type:', data.type)
    }
  } catch (e) {
    console.error('[Mahjong WS] Failed to parse message:', e)
  }
}

/**
 * 发送出牌消息
 */
export function sendDiscard(gameId: number, tile: string) {
  if (!stompClient?.active) {
    console.error('[Mahjong WS] Not connected')
    return
  }
  stompClient.publish({
    destination: `/app/mahjong/${gameId}/discard`,
    body: JSON.stringify({ tile })
  })
}

/**
 * 发送碰牌消息
 */
export function sendPong(gameId: number, tile: string) {
  if (!stompClient?.active) return
  stompClient.publish({
    destination: `/app/mahjong/${gameId}/pong`,
    body: JSON.stringify({ tile })
  })
}

/**
 * 发送杠牌消息
 */
export function sendKong(gameId: number, tile: string, kongType: string) {
  if (!stompClient?.active) return
  stompClient.publish({
    destination: `/app/mahjong/${gameId}/kong`,
    body: JSON.stringify({ tile, kongType })
  })
}

/**
 * 发送胡牌消息
 */
export function sendHu(gameId: number) {
  if (!stompClient?.active) return
  stompClient.publish({
    destination: `/app/mahjong/${gameId}/hu`
  })
}

/**
 * 发送过消息
 */
export function sendPass(gameId: number) {
  if (!stompClient?.active) return
  stompClient.publish({
    destination: `/app/mahjong/${gameId}/pass`
  })
}

/**
 * 发送同步请求
 */
export function sendSync(gameId: number) {
  if (!stompClient?.active) return
  stompClient.publish({
    destination: `/app/mahjong/${gameId}/sync`
  })
}

/**
 * 发送开始游戏消息
 */
export function sendStartGame(gameId: number) {
  if (!stompClient?.active) return
  stompClient.publish({
    destination: `/app/mahjong/${gameId}/start`
  })
}

/**
 * 发送下一局消息
 */
export function sendNextRound(gameId: number) {
  if (!stompClient?.active) return
  stompClient.publish({
    destination: `/app/mahjong/${gameId}/next-round`
  })
}

/**
 * 发送心跳
 */
export function sendHeartbeat(gameId: number) {
  if (!stompClient?.active) return
  stompClient.publish({
    destination: `/app/mahjong/${gameId}/heartbeat`
  })
}
