import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { showToast } from 'vant'
import {
  mahjongApi,
  MahjongRoomStatus,
  type MahjongActionPayload,
  type MahjongCreatePayload,
  type MahjongEventView,
  type MahjongPrivateHand,
  type MahjongRoom,
  type MahjongSeatOptions
} from '@/services/mahjongApi'
import { MahjongMessageType, mahjongWebSocket, type MahjongMessage } from '@/services/mahjongWebsocket'
import { useUserStore } from './user'

/** 操作发出后多久没收到新状态就放开按钮 */
const PENDING_TIMEOUT_MS = 6000
/** 公共视角和手牌版本对不上超过这么久，就用 HTTP 重新拉一次 */
const RESYNC_DELAY_MS = 1500
/** 座位上「碰」「杠」这类提示显示多久 */
const FLASH_MS = 1800

const EVENT_FLASH: Record<string, string> = {
  CHI: '吃',
  PONG: '碰',
  MING_GANG: '杠',
  JIA_GANG: '加杠',
  AN_GANG: '暗杠',
  QIAO: '敲',
  HU: '胡',
  AUTO_ON: '托管'
}

export interface SeatFlash {
  text: string
  key: number
}

/**
 * 麻将房间状态。
 *
 * 房间公共视角（谁坐哪、牌河、亮出的牌……）来自 /topic/mahjong/{id}，自己的手牌和可选操作
 * 来自 /user/queue/mahjong/{id}；两者都带版本号，旧消息直接丢弃，版本长时间对不上就重新拉取。
 */
export const useMahjongStore = defineStore('mahjong', () => {
  const userStore = useUserStore()

  const room = ref<MahjongRoom | null>(null)
  const hand = ref<MahjongPrivateHand | null>(null)
  const history = ref<MahjongRoom[]>([])
  const isConnected = ref(false)
  const isLoading = ref(false)
  const pending = ref(false)
  /** 服务端时间 - 本机时间，用来换算倒计时 */
  const clockOffset = ref(0)
  const now = ref(Date.now())
  const flashes = ref<Record<number, SeatFlash>>({})

  // 每次进入 / 离开房间递增，丢弃旧房间迟到的响应
  let roomToken = 0
  let pendingVersion = -1
  let pendingTimer: ReturnType<typeof setTimeout> | null = null
  let resyncTimer: ReturnType<typeof setTimeout> | null = null
  let ticker: ReturnType<typeof setInterval> | null = null
  let everConnected = false
  let flashKey = 0
  const flashTimers = new Map<number, ReturnType<typeof setTimeout>>()

  const mySeat = computed<number | null>(() => {
    const seat = room.value?.seats.find(item => item.userId === userStore.userId)
    return seat ? seat.seatIndex : null
  })
  const isOwner = computed(() => !!room.value && room.value.ownerId === userStore.userId)
  const round = computed(() => room.value?.round ?? null)
  const status = computed(() => Number(room.value?.gameStatus ?? 0))
  const isWaiting = computed(() => status.value === MahjongRoomStatus.WAITING)
  const isPlaying = computed(() => status.value === MahjongRoomStatus.PLAYING)
  const isFinished = computed(() => status.value === MahjongRoomStatus.FINISHED)
  const isCancelled = computed(() => status.value === MahjongRoomStatus.CANCELLED)

  /** 手牌和公共视角是同一个版本时才给出可选操作，避免按着旧局面操作 */
  const options = computed<MahjongSeatOptions | null>(() => {
    if (!hand.value || !round.value || hand.value.step !== round.value.step) return null
    return hand.value.options ?? null
  })
  const isMyTurn = computed(() => isPlaying.value && !!options.value?.myTurn)
  const claimPending = computed(() => isPlaying.value && !!options.value?.claimPending)

  const remainingMs = computed(() => {
    const deadline = round.value?.deadline ?? 0
    if (!deadline || !isPlaying.value) return 0
    return Math.max(0, deadline - (now.value + clockOffset.value))
  })

  function seatName(seat: number | null | undefined): string {
    if (seat === null || seat === undefined) return ''
    if (seat === mySeat.value) return '你'
    return room.value?.seats[seat]?.name || `${seat + 1} 号位`
  }

  // ------------------------------------------------------------------ 状态合并

  function applyRoom(view: MahjongRoom, serverTime?: number): void {
    const current = room.value
    if (current && current.id === view.id && view.version < current.version) return
    const time = serverTime ?? view.serverTime
    if (time) clockOffset.value = time - Date.now()

    const { me, ...rest } = view
    room.value = { ...rest, me: null }
    if (me) {
      applyHand(me)
    } else if (!rest.round || mySeat.value === null) {
      hand.value = null
    }
    if (!current || current.id !== view.id || view.version > current.version) {
      showEvents(rest.round?.events)
    }
    afterUpdate()
  }

  function applyHand(next: MahjongPrivateHand): void {
    if (hand.value && next.step < hand.value.step) return
    hand.value = next
    afterUpdate()
  }

  function afterUpdate(): void {
    const version = room.value?.version ?? -1
    if (pending.value && (version > pendingVersion || (hand.value?.step ?? -1) > pendingVersion)) {
      clearPending()
    }
    scheduleResync()
  }

  function isSynced(): boolean {
    if (!round.value || mySeat.value === null) return true
    return hand.value?.step === round.value.step
  }

  function scheduleResync(): void {
    if (resyncTimer) {
      clearTimeout(resyncTimer)
      resyncTimer = null
    }
    if (isSynced()) return
    const token = roomToken
    resyncTimer = setTimeout(() => {
      resyncTimer = null
      if (token === roomToken && !isSynced()) void refresh(true)
    }, RESYNC_DELAY_MS)
  }

  function showEvents(events: MahjongEventView[] | undefined): void {
    for (const event of events ?? []) {
      const text = EVENT_FLASH[event.type]
      if (!text) continue
      const key = ++flashKey
      flashes.value = { ...flashes.value, [event.seat]: { text, key } }
      const previous = flashTimers.get(event.seat)
      if (previous) clearTimeout(previous)
      flashTimers.set(event.seat, setTimeout(() => {
        if (flashes.value[event.seat]?.key === key) {
          const next = { ...flashes.value }
          delete next[event.seat]
          flashes.value = next
        }
        flashTimers.delete(event.seat)
      }, FLASH_MS))
    }
  }

  function handleMessage(message: MahjongMessage): void {
    if (!room.value || message.gameId !== room.value.id) return
    const data = message.data ?? {}
    switch (message.type) {
      case MahjongMessageType.ROOM:
      case MahjongMessageType.SYNC:
        if (data.room) applyRoom(data.room as MahjongRoom, message.serverTime)
        break
      case MahjongMessageType.HAND:
        if (data.hand) applyHand(data.hand as MahjongPrivateHand)
        break
      case MahjongMessageType.ERROR:
        clearPending()
        showToast({ type: 'fail', message: String(data.error || '操作失败') })
        break
    }
  }

  // ------------------------------------------------------------------ 连接

  async function connect(): Promise<void> {
    if (!userStore.token) throw new Error('请先登录')
    mahjongWebSocket.setCallbacks({
      onConnectionChange: (connected) => {
        isConnected.value = connected
        if (!connected) {
          clearPending()
          return
        }
        // 断线重连后取回自己的视角（订阅后服务端也会回一次 sync，这里用 HTTP 兜底）
        if (everConnected && room.value) void refresh(true)
        everConnected = true
      },
      onError: (error) => {
        showToast({ type: 'fail', message: error.message })
      },
      onMessage: handleMessage
    })
    await mahjongWebSocket.connect()
  }

  /** 只有坐在桌上的人才能订阅（服务端会拒绝其他人） */
  async function subscribeIfSeated(gameId: number, token: number): Promise<void> {
    if (mySeat.value === null) return
    try {
      await connect()
    } catch {
      // 连接会在后台自动重试，连上后自动订阅；页面照常显示，状态栏提示正在重连
    }
    if (token !== roomToken) return
    mahjongWebSocket.enterRoom(gameId)
  }

  async function enterRoom(gameId: number): Promise<void> {
    const token = ++roomToken
    resetRoomState()
    startTicker()
    const view = await mahjongApi.getRoom(gameId)
    if (token !== roomToken) return
    applyRoom(view)
    await subscribeIfSeated(gameId, token)
  }

  function leaveRoomView(): void {
    roomToken += 1
    mahjongWebSocket.leaveRoom()
    stopTicker()
    resetRoomState()
  }

  async function refresh(silent = false): Promise<void> {
    if (!room.value) return
    const token = roomToken
    try {
      const view = await mahjongApi.getRoom(room.value.id)
      if (token === roomToken) applyRoom(view)
    } catch (error: any) {
      if (!silent) throw error
    }
  }

  function resetRoomState(): void {
    room.value = null
    hand.value = null
    flashes.value = {}
    flashTimers.forEach(timer => clearTimeout(timer))
    flashTimers.clear()
    clearPending()
    if (resyncTimer) {
      clearTimeout(resyncTimer)
      resyncTimer = null
    }
  }

  function startTicker(): void {
    stopTicker()
    now.value = Date.now()
    ticker = setInterval(() => {
      now.value = Date.now()
    }, 500)
  }

  function stopTicker(): void {
    if (ticker) {
      clearInterval(ticker)
      ticker = null
    }
  }

  // ------------------------------------------------------------------ 房间操作

  async function createRoom(payload: MahjongCreatePayload): Promise<MahjongRoom> {
    isLoading.value = true
    try {
      return await mahjongApi.createRoom(payload)
    } finally {
      isLoading.value = false
    }
  }

  async function joinRoom(gameCode: string): Promise<MahjongRoom> {
    isLoading.value = true
    try {
      return await mahjongApi.joinRoom(gameCode)
    } finally {
      isLoading.value = false
    }
  }

  /** 在房间页里加入（被邀请的对象），加入后开始接收实时消息 */
  async function joinCurrentRoom(): Promise<void> {
    if (!room.value) return
    const token = roomToken
    const view = await mahjongApi.joinRoom(room.value.gameCode)
    if (token !== roomToken) return
    applyRoom(view)
    await subscribeIfSeated(view.id, token)
  }

  async function runRoomRequest(request: (gameId: number) => Promise<MahjongRoom | void>): Promise<void> {
    if (!room.value) return
    const token = roomToken
    const view = await request(room.value.id)
    if (view && token === roomToken) applyRoom(view)
  }

  const setSeat = (seatIndex: number, bot: boolean) =>
    runRoomRequest(gameId => mahjongApi.setSeat(gameId, seatIndex, bot))
  const invite = (userId: number) => runRoomRequest(gameId => mahjongApi.invite(gameId, userId))
  const startGame = () => runRoomRequest(gameId => mahjongApi.startGame(gameId))
  const nextRound = () => runRoomRequest(gameId => mahjongApi.nextRound(gameId))

  async function leaveSeat(): Promise<void> {
    if (!room.value) return
    await mahjongApi.leaveRoom(room.value.id)
    mahjongWebSocket.leaveRoom()
  }

  async function cancelRoom(): Promise<void> {
    if (!room.value) return
    await mahjongApi.cancelRoom(room.value.id)
  }

  async function fetchHistory(status?: number): Promise<void> {
    const response = await mahjongApi.listRooms(status, 1, 20)
    history.value = response?.records ?? []
  }

  // ------------------------------------------------------------------ 牌局操作

  function act(payload: MahjongActionPayload): void {
    if (!room.value || pending.value) return
    const gameId = room.value.id
    pending.value = true
    pendingVersion = room.value.version
    pendingTimer = setTimeout(clearPending, PENDING_TIMEOUT_MS)
    if (mahjongWebSocket.act(gameId, { ...payload })) return

    // 实时连接断开时退回 HTTP
    const token = roomToken
    mahjongApi.act(gameId, payload)
      .then(view => {
        if (token === roomToken) applyRoom(view)
      })
      .catch((error: any) => {
        clearPending()
        showToast({ type: 'fail', message: error.message || '操作失败' })
      })
  }

  function clearPending(): void {
    pending.value = false
    pendingVersion = -1
    if (pendingTimer) {
      clearTimeout(pendingTimer)
      pendingTimer = null
    }
  }

  return {
    room,
    hand,
    history,
    isConnected,
    isLoading,
    pending,
    flashes,
    mySeat,
    isOwner,
    round,
    isWaiting,
    isPlaying,
    isFinished,
    isCancelled,
    options,
    isMyTurn,
    claimPending,
    remainingMs,
    seatName,
    enterRoom,
    leaveRoomView,
    refresh,
    createRoom,
    joinRoom,
    joinCurrentRoom,
    setSeat,
    invite,
    startGame,
    nextRound,
    leaveSeat,
    cancelRoom,
    fetchHistory,
    act
  }
})
