import http from './http'

/**
 * 上海麻将（百搭 / 敲麻）接口与牌面工具。
 *
 * 牌的编码和后端 MahjongTiles 一致：花色 × 10 + 点数。
 * 万 11–19，条 21–29，筒 31–39，东南西北 41–44，中发白 45–47，春夏秋冬梅兰竹菊 51–58。
 */

export type MahjongMode = 'BAIDA' | 'QIAOMA'

export enum MahjongRoomStatus {
  WAITING = 1,
  PLAYING = 2,
  FINISHED = 3,
  CANCELLED = 4
}

export type MahjongSeatType = 'EMPTY' | 'HUMAN' | 'BOT'

export interface MahjongSeat {
  seatIndex: number
  type: MahjongSeatType
  userId?: number | null
  name?: string | null
  avatar?: string | null
  totalScore?: number
  owner?: boolean
}

export type MahjongMeldType = 'CHI' | 'PONG' | 'MING_GANG' | 'JIA_GANG' | 'AN_GANG'

export interface MahjongMeld {
  type: MahjongMeldType
  /** 暗杠在本局结束前为空 */
  tiles?: number[] | null
  fromSeat?: number | null
  claimedTile?: number | null
  concealed?: boolean
}

export interface MahjongPublicSeat {
  seat: number
  handCount: number
  holdingDrawn?: boolean
  melds: MahjongMeld[]
  flowers: number[]
  discards: number[]
  qiao?: boolean
  autoPlay?: boolean
  /** 只在本局结束后给出 */
  hand?: number[] | null
}

export interface MahjongScoreItem {
  name: string
  value: number
}

export interface MahjongWin {
  seat: number
  selfDraw: boolean
  winningTile: number
  handFlowers: number
  flowers: number
  flowerItems: MahjongScoreItem[]
  fan: number
  fanItems: MahjongScoreItem[]
  multiple: number
  payEach: number
  payers: number[]
  fourWild?: boolean
  wuHuaGuo?: boolean
}

export interface MahjongRoundResult {
  drawGame?: boolean
  selfDraw?: boolean
  discarder?: number | null
  winningTile?: number | null
  wins: MahjongWin[]
  deltas: number[]
  seatFlowers: number[]
}

export interface MahjongEventView {
  type: string
  seat: number
  tile?: number | null
}

export type MahjongPhase = 'ACTION' | 'CLAIM' | 'FINISHED'

export interface MahjongPublicRound {
  mode: MahjongMode
  roundNo: number
  dealer: number
  seatCount: number
  phase: MahjongPhase
  turn: number
  wallCount: number
  indicator?: number | null
  wild?: number | null
  huangCount: number
  capPoints: number
  lastDiscard?: number | null
  lastDiscardSeat?: number | null
  waitingClaims?: boolean
  step: number
  deadline: number
  seats: MahjongPublicSeat[]
  result?: MahjongRoundResult | null
  events?: MahjongEventView[]
}

export interface MahjongQiaoOption {
  tile: number
  waits: number[]
}

export interface MahjongSeatOptions {
  myTurn?: boolean
  discardable?: number[]
  canHu?: boolean
  kongTiles?: number[]
  qiaoOptions?: MahjongQiaoOption[]
  claimPending?: boolean
  claimTile?: number | null
  canPong?: boolean
  canKong?: boolean
  chiStarts?: number[]
}

export interface MahjongPrivateHand {
  seat: number
  step: number
  /** 已排序，不含刚摸到的那张 */
  hand: number[]
  drawnTile?: number | null
  /** 自己暗杠的牌，按副露顺序 */
  concealedKongs?: number[]
  waits?: number[]
  qiao?: boolean
  autoPlay?: boolean
  deadline?: number
  options?: MahjongSeatOptions
  events?: MahjongEventView[]
}

export interface MahjongRoom {
  id: number
  gameCode: string
  mode: MahjongMode
  modeName: string
  seatCount: number
  capPoints: number
  ownerId: number
  gameStatus: MahjongRoomStatus | number
  roundNo: number
  dealerSeat: number
  huangCount: number
  invitedUserId?: number | null
  invitedUserName?: string | null
  seats: MahjongSeat[]
  mySeat?: number | null
  version: number
  serverTime?: number
  round?: MahjongPublicRound | null
  me?: MahjongPrivateHand | null
  createdAt: string
  startedAt?: string | null
  endedAt?: string | null
  updatedAt?: string | null
}

export interface PageMahjongRoom {
  records: MahjongRoom[]
  total: number
  size: number
  current: number
  pages: number
}

export interface MahjongCreatePayload {
  mode: MahjongMode
  seatCount: number
  /** 每个付分人每局最多付多少分；0 不封顶，不传用默认 128 */
  capPoints?: number
  inviteUserId?: number
}

export type MahjongActionType = 'DISCARD' | 'QIAO' | 'HU' | 'KONG' | 'PONG' | 'CHI' | 'PASS' | 'CANCEL_AUTO'

export interface MahjongActionPayload {
  type: MahjongActionType
  tile?: number
  chiStart?: number
}

export const MAHJONG_MODE_OPTIONS: Array<{ value: MahjongMode; label: string; hint: string }> = [
  { value: 'BAIDA', label: '百搭', hint: '翻牌定百搭，百搭可替代任意牌，有百搭只能自摸' },
  { value: 'QIAOMA', label: '敲麻', hint: '必须先敲（听牌）才能胡，自摸 2 花、点炮 3 花起胡' }
]

export const MAHJONG_CAP_OPTIONS: Array<{ value: number; label: string }> = [
  { value: 32, label: '32 分' },
  { value: 64, label: '64 分' },
  { value: 128, label: '128 分' },
  { value: 256, label: '256 分' },
  { value: 0, label: '不封顶' }
]

export const DEFAULT_CAP_POINTS = 128

export const mahjongApi = {
  createRoom(payload: MahjongCreatePayload) {
    return http.post<MahjongRoom>('/mahjong/create', payload)
  },

  joinRoom(gameCode: string) {
    return http.post<MahjongRoom>('/mahjong/join', { gameCode })
  },

  getRoom(gameId: number) {
    return http.get<MahjongRoom>(`/mahjong/${gameId}`)
  },

  listRooms(status?: number, pageNum = 1, pageSize = 10) {
    return http.get<PageMahjongRoom>('/mahjong/list', { params: { status, pageNum, pageSize } })
  },

  setSeat(gameId: number, seatIndex: number, bot: boolean) {
    return http.post<MahjongRoom>(`/mahjong/${gameId}/seat`, { seatIndex, bot })
  },

  invite(gameId: number, userId: number) {
    return http.post<MahjongRoom>(`/mahjong/${gameId}/invite`, { userId })
  },

  leaveRoom(gameId: number) {
    return http.post<void>(`/mahjong/${gameId}/leave`)
  },

  startGame(gameId: number) {
    return http.post<MahjongRoom>(`/mahjong/${gameId}/start`)
  },

  nextRound(gameId: number) {
    return http.post<MahjongRoom>(`/mahjong/${gameId}/next-round`)
  },

  cancelRoom(gameId: number) {
    return http.post<void>(`/mahjong/${gameId}/cancel`)
  },

  /** 实时连接断开时的备用入口 */
  act(gameId: number, payload: MahjongActionPayload) {
    return http.post<MahjongRoom>(`/mahjong/${gameId}/action`, payload)
  }
}

// ------------------------------------------------------------------ 牌面

const RANKS = ['', '一', '二', '三', '四', '五', '六', '七', '八', '九']
const SUITS = ['', '万', '条', '筒']
const HONORS = ['东', '南', '西', '北', '中', '发', '白']
const FLOWERS = ['春', '夏', '秋', '冬', '梅', '兰', '竹', '菊']

export type TileKind = 'wan' | 'tiao' | 'tong' | 'wind' | 'dragon' | 'flower' | 'unknown'

export function tileKind(tile: number): TileKind {
  const suit = Math.floor(tile / 10)
  const rank = tile % 10
  if (suit >= 1 && suit <= 3 && rank >= 1 && rank <= 9) {
    return suit === 1 ? 'wan' : suit === 2 ? 'tiao' : 'tong'
  }
  if (tile >= 41 && tile <= 44) return 'wind'
  if (tile >= 45 && tile <= 47) return 'dragon'
  if (tile >= 51 && tile <= 58) return 'flower'
  return 'unknown'
}

export function isSuitedTile(tile: number): boolean {
  const kind = tileKind(tile)
  return kind === 'wan' || kind === 'tiao' || kind === 'tong'
}

/** 牌面主字：数牌是一到九，字牌和花牌是本字 */
export function tileGlyph(tile: number): string {
  if (isSuitedTile(tile)) return RANKS[tile % 10]
  if (tile >= 41 && tile <= 47) return HONORS[tile - 41]
  if (tile >= 51 && tile <= 58) return FLOWERS[tile - 51]
  return '?'
}

/** 数牌的花色字（万 / 条 / 筒），其他牌为空 */
export function tileSuitGlyph(tile: number): string {
  return isSuitedTile(tile) ? SUITS[Math.floor(tile / 10)] : ''
}

export function tileName(tile: number | null | undefined): string {
  if (tile === null || tile === undefined) return ''
  if (isSuitedTile(tile)) return `${RANKS[tile % 10]}${SUITS[Math.floor(tile / 10)]}`
  if (tile >= 41 && tile <= 44) return `${HONORS[tile - 41]}风`
  return tileGlyph(tile)
}

export function tileNames(tiles: number[] | null | undefined): string {
  return (tiles ?? []).map(tileName).join('、')
}

// ------------------------------------------------------------------ 牌桌方位

/** 牌桌上的方位：自己永远在下方，出牌顺序是 下 → 右 → 上 → 左 */
export type TableSide = 'bottom' | 'right' | 'top' | 'left'

const SIDES_BY_COUNT: Record<number, TableSide[]> = {
  2: ['bottom', 'top'],
  3: ['bottom', 'right', 'left'],
  4: ['bottom', 'right', 'top', 'left']
}

/** seat 坐在 viewer 看来的哪一边；viewer 不在桌上时以 0 号位为下方 */
export function sideOfSeat(seat: number, viewer: number | null | undefined, seatCount: number): TableSide {
  const sides = SIDES_BY_COUNT[seatCount] ?? SIDES_BY_COUNT[4]
  const base = viewer ?? 0
  return sides[((seat - base) % seatCount + seatCount) % seatCount] ?? 'top'
}

/** 每一边的牌顺时针转多少度，让牌头朝向桌子中间 */
export const SIDE_ROTATION: Record<TableSide, 0 | 90 | 180 | 270> = {
  bottom: 0,
  right: 270,
  top: 0,
  left: 90
}

/** 庄家是东，按出牌顺序依次南、西、北 */
export function seatWind(seat: number, dealer: number, seatCount: number): string {
  return ['东', '南', '西', '北'][((seat - dealer) % seatCount + seatCount) % seatCount]
}
