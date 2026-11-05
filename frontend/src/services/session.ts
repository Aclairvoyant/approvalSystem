import { gameWebSocket } from './websocket'
import { gobangWebSocket } from './gobangWebsocket'
import { useUserStore } from '@/store/modules/user'
import { useGameStore } from '@/store/modules/game'
import { useGobangStore } from '@/store/modules/gobang'

/**
 * 退出登录。除了清掉本地凭证，还要断开两个 STOMP 连接并清空游戏状态：
 * WebSocket 会话在握手时绑定了用户身份，不断开的话换账号登录后仍会以上一个用户的身份发消息。
 */
export function endSession(): void {
  useGameStore().leaveGameRoom()
  useGobangStore().leaveGameRoom()
  gameWebSocket.disconnect()
  gobangWebSocket.disconnect()
  useUserStore().clearUserInfo()
}
