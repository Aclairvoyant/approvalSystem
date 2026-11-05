<template>
  <div class="mj-room">
    <div v-if="!room" class="table loading">
      <van-loading size="24px" vertical color="#f6c453">正在进入房间...</van-loading>
    </div>

    <div v-else class="table" :class="[`seats-${room.seatCount}`, { waiting: !inGame }]">
      <!-- 顶栏 -->
      <header class="top-bar">
        <button type="button" class="icon-btn" aria-label="返回大厅" @click="goBack">
          <van-icon name="arrow-left" />
        </button>
        <div class="top-info">
          <template v-if="inGame && round">
            <span>第 {{ round.roundNo }} 局</span>
            <span v-if="round.wild" class="top-wild">
              百搭 <MahjongTile :tile="round.wild" size="xs" wild />
            </span>
            <span v-else>{{ room.modeName }}</span>
            <span v-if="round.huangCount">荒番 {{ round.huangCount }}</span>
            <span>封顶 {{ capText }}</span>
          </template>
          <template v-else>
            <span>{{ room.modeName }}</span>
            <span>{{ room.seatCount }} 人桌</span>
            <span>封顶 {{ capText }}</span>
          </template>
        </div>
        <span v-if="store.mySeat !== null && !store.isConnected" class="conn-off">重连中</span>
        <button type="button" class="icon-btn" aria-label="刷新" @click="refresh">
          <van-icon name="replay" />
        </button>
        <button type="button" class="icon-btn" aria-label="规则" @click="showRules = true">
          <van-icon name="question-o" />
        </button>
      </header>

      <!-- 桌面 -->
      <div class="felt">
        <!-- 等待开局：大家入座 -->
        <template v-if="!inGame">
          <div
            v-for="seat in room.seats"
            :key="seat.seatIndex"
            class="wait-seat"
            :class="`at-${sideOf(seat.seatIndex)}`"
          >
            <MahjongSeatPlate
              :info="seat"
              :name="seat.seatIndex === store.mySeat ? '你' : ''"
              :show-score="false"
              :compact="isSideSeat(seat.seatIndex)"
            />
            <span class="wait-note">{{ seatNote(seat) }}</span>
            <template v-if="store.isOwner && store.isWaiting && seat.seatIndex > 0">
              <button
                v-if="seat.type === 'EMPTY'"
                type="button"
                class="chip-btn"
                :disabled="busySeat === seat.seatIndex"
                @click="toggleBot(seat.seatIndex, true)"
              >设为电脑</button>
              <button
                v-else-if="seat.type === 'BOT'"
                type="button"
                class="chip-btn"
                :disabled="busySeat === seat.seatIndex"
                @click="toggleBot(seat.seatIndex, false)"
              >改回空位</button>
            </template>
          </div>

          <div class="wait-center">
            <p class="room-label">房间号</p>
            <strong class="room-code tabular">{{ room.gameCode }}</strong>
            <button type="button" class="chip-btn copy-btn" @click="copyCode">复制房间号</button>
            <p class="room-hint">{{ waitingHint }}</p>
          </div>
        </template>

        <!-- 对局中 / 本局结束 -->
        <template v-else-if="round">
          <MahjongTableSeat
            v-for="item in opponents"
            :key="item.seat.seat"
            class="seat-anchor"
            :class="`at-${item.side}`"
            :side="item.side"
            :seat="item.seat"
            :info="room.seats[item.seat.seat]"
            :wind="seatWind(item.seat.seat, round.dealer, seatCount)"
            :dealer="item.seat.seat === round.dealer"
            :active="isActiveSeat(item.seat.seat)"
            :wild="round.wild ?? null"
            :deal-key="round.roundNo"
            @inspect="inspectSeat = item.seat.seat"
          />

          <div class="board">
            <MahjongRiver
              v-for="river in rivers"
              :key="river.side"
              :class="`river-${river.side}`"
              :side="river.side"
              :tiles="river.tiles"
              :wild="round.wild ?? null"
              :highlight-last="river.highlight"
              :animate-last="spotlight?.side === river.side"
              :wide="seatCount === 2"
            />
            <MahjongCompass
              class="board-compass"
              :winds="compassWinds"
              :wall-count="round.wallCount"
              :countdown="countdown"
              :limit="round.phase === 'CLAIM' ? CLAIM_LIMIT : TURN_LIMIT"
            />
          </div>

          <!-- 动效层：出牌特写、吃碰杠胡、胡牌 -->
          <div class="fx-layer" aria-hidden="true">
            <div v-if="spotlight" :key="spotlight.key" class="fx-spotlight" :class="`at-${spotlight.side}`">
              <MahjongTile :tile="spotlight.tile" size="lg" :wild="spotlight.tile === round.wild" />
            </div>
            <div
              v-for="burst in bursts"
              :key="burst.key"
              class="fx-burst"
              :class="[`at-${burst.side}`, `kind-${burst.kind}`]"
            >{{ burst.text }}</div>
            <div v-if="finishFx" :key="finishFx.key" class="fx-finish" :class="`kind-${finishFx.kind}`">
              <span class="fx-rays" />
              <strong class="fx-title">{{ finishFx.text }}</strong>
              <span v-if="finishFx.sub" class="fx-sub">{{ finishFx.sub }}</span>
            </div>
          </div>
        </template>
      </div>

      <!-- 底部：等待开局的操作 / 自己的手牌 -->
      <footer class="my-area">
        <template v-if="!inGame">
          <div class="wait-actions">
            <template v-if="store.isCancelled">
              <button type="button" class="pill-btn" @click="goBack">房间已取消，返回大厅</button>
            </template>
            <template v-else-if="store.isOwner">
              <button
                v-if="canInvitePartner"
                type="button"
                class="pill-btn ghost"
                :disabled="inviting"
                @click="invitePartner"
              >邀请{{ partner?.name }}</button>
              <button type="button" class="pill-btn ghost" @click="cancelRoom">取消房间</button>
              <button type="button" class="pill-btn primary" :disabled="!allSeated || busy" @click="startGame">
                {{ allSeated ? '开始' : `还差 ${emptySeats} 人` }}
              </button>
            </template>
            <template v-else-if="store.mySeat === null">
              <button type="button" class="pill-btn primary" :disabled="busy" @click="joinRoom">入座</button>
            </template>
            <template v-else>
              <span class="wait-text">等房主开局</span>
              <button type="button" class="pill-btn ghost" @click="leaveSeat">离开座位</button>
            </template>
          </div>
        </template>

        <template v-else-if="round">
          <!-- 漂在手牌上方的大按钮 -->
          <TransitionGroup tag="div" name="act" class="actions" :class="{ claim: store.claimPending }">
            <button
              v-for="action in actionButtons"
              :key="action.key"
              type="button"
              class="act-btn"
              :class="[`kind-${action.kind}`, { on: action.on }]"
              :disabled="store.pending"
              @click="action.run"
            >
              <span class="act-label">{{ action.label }}</span>
              <span v-if="action.sub" class="act-sub">{{ action.sub }}</span>
            </button>
          </TransitionGroup>

          <div class="my-bar">
            <MahjongSeatPlate
              v-if="mySeatInfo"
              :info="mySeatInfo"
              name="你"
              :wind="store.mySeat !== null ? seatWind(store.mySeat, round.dealer, seatCount) : ''"
              :dealer="store.mySeat === round.dealer"
              :active="store.mySeat !== null && isActiveSeat(store.mySeat)"
              :qiao="!!myPublicSeat?.qiao"
              :auto-play="!!myPublicSeat?.autoPlay"
              :flowers="myPublicSeat?.flowers.length ?? 0"
              @inspect="inspectSeat = store.mySeat"
            />
            <p class="my-hint">{{ dockHint }}</p>
            <template v-if="store.isFinished">
              <button type="button" class="pill-btn ghost small" @click="showResult = true">结算</button>
              <button v-if="store.isOwner" type="button" class="pill-btn primary small" :disabled="busy" @click="nextRound">
                再来一局
              </button>
            </template>
          </div>

          <div
            class="my-tiles"
            :style="{ '--hand-tile-w': handTileWidth, '--meld-tile-w': `${MELD_TILE_W}px` }"
          >
            <div v-if="myMelds.length" class="my-melds">
              <span v-for="(meld, index) in myMelds" :key="`m-${index}`" class="my-meld">
                <MahjongTile
                  v-for="(tile, i) in meld.tiles"
                  :key="i"
                  :tile="tile"
                  size="meld"
                />
              </span>
            </div>
            <TransitionGroup
              :key="round.roundNo"
              tag="div"
              name="hand"
              class="my-hand"
              :class="{ dealing }"
              appear
              role="group"
              aria-label="我的手牌"
              @pointerup="onPointerUp"
              @pointercancel="pointerStart = null"
            >
              <MahjongTile
                v-for="(item, index) in handTiles"
                :key="item.key"
                :tile="item.tile"
                size="hand"
                interactive
                :class="{ 'is-drawn': item.drawn }"
                :style="{ '--i': index }"
                :wild="item.tile === round.wild"
                :selected="selectedKey === item.key"
                :marked="qiaoMode && qiaoTiles.has(item.tile)"
                :dim="tileDimmed(item.tile)"
                :disabled="!canDiscardNow"
                @pointerdown="onPointerDown($event, item)"
                @click="tapTile(item)"
              />
            </TransitionGroup>
          </div>
        </template>
      </footer>
    </div>

    <van-action-sheet
      v-model:show="showChiSheet"
      :actions="chiActions"
      cancel-text="取消"
      close-on-click-action
      @select="onChiSelect"
    />

    <van-popup v-model:show="showResult" position="bottom" round closeable :style="{ maxHeight: '88%' }">
      <MahjongResultSheet
        v-if="round?.result"
        :result="round.result"
        :round="round"
        :totals="totals"
        :seat-name="seatNameForResult"
        :my-seat="store.mySeat"
        :is-owner="store.isOwner"
        :busy="busy"
        @next="nextRound"
        @back="goBack"
      />
    </van-popup>

    <van-popup v-model:show="inspectOpen" position="bottom" round closeable>
      <div v-if="inspected" class="inspect">
        <div class="m-sheet-title">{{ inspected.name }}</div>
        <p class="inspect-meta">
          {{ inspected.wind }}<template v-if="inspected.dealer"> · 庄家</template> · 总分 {{ inspected.score }}
          <template v-if="inspected.qiao"> · 已敲</template><template v-if="inspected.autoPlay"> · 托管中</template>
        </p>
        <p class="inspect-label">花牌 {{ inspected.flowers.length }} 张</p>
        <div class="inspect-tiles">
          <MahjongTile v-for="(tile, i) in inspected.flowers" :key="i" :tile="tile" size="md" />
          <span v-if="!inspected.flowers.length" class="inspect-empty">还没有花</span>
        </div>
        <p class="inspect-label">打出的牌 {{ inspected.discards.length }} 张</p>
        <div class="inspect-tiles">
          <MahjongTile v-for="(tile, i) in inspected.discards" :key="i" :tile="tile" size="sm" />
          <span v-if="!inspected.discards.length" class="inspect-empty">还没出牌</span>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="showRules" position="bottom" round closeable :style="{ maxHeight: '80%' }">
      <div class="rules">
        <div class="m-sheet-title">上海麻将规则</div>
        <h4>共同</h4>
        <ul>
          <li>暗牌打：别人的手牌只显示牌背，一局结束只亮胡家的牌。</li>
          <li>144 张牌，中发白和春夏秋冬梅兰竹菊都算花，摸到亮出、从牌尾补。</li>
          <li>只认 4 副 + 1 对，不带七对。只能吃上家；吃碰后这一手不能马上打出同一种牌。</li>
          <li>抢牌：胡 > 杠 / 碰 > 吃；几家同时能胡就一炮多响，放炮者分别付。</li>
          <li>倍数 = 花数 × 2<sup>番</sup>。自摸其余每家各付，点炮由放炮者付；每人每局最多付封顶分。</li>
          <li>荒庄不计分，下一局加 1 荒番（每个 1 番），有人胡后清零。庄家每局顺延。</li>
          <li>真人出牌限时 20 秒、吃碰响应 10 秒；超时由系统代打并进入托管。</li>
          <li>点两下或往上滑打出一张牌。</li>
        </ul>
        <h4>敲麻</h4>
        <ul>
          <li>必须先敲才能胡：点「敲」再选一张打出，打出后要听牌；敲后手牌锁定，系统自动摸打，有胡必胡。</li>
          <li>底花 2。花牌 1 花；万条筒明杠 1、暗杠 2；风牌碰或暗刻 1、明杠 2、暗杠 3。</li>
          <li>不算底花，自摸至少 2 花、点炮至少 3 花；一朵花都没有算无花果，按 10 花。</li>
          <li>番：清一色 2，混一色、碰碰胡、门清、杠开、大吊车各 1。</li>
        </ul>
        <h4>百搭</h4>
        <ul>
          <li>开局翻一张，下一张为百搭（9 接 1、北接东，翻到花时东风为百搭），可替代任意非花牌。</li>
          <li>打出去或吃碰杠里的百搭按本身算。手里有百搭只能自摸；集齐 4 张百搭立即胡，2 番。</li>
          <li>底花 1。花牌、风碰 / 风暗刻、明杠、手里每张百搭各 1 花；暗杠、风明杠 2，风暗杠 3。</li>
          <li>番：无百搭、杠开、大吊车、门清各 1。</li>
        </ul>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import MahjongTile from '@/components/mahjong/MahjongTile.vue'
import MahjongTableSeat from '@/components/mahjong/MahjongTableSeat.vue'
import MahjongSeatPlate from '@/components/mahjong/MahjongSeatPlate.vue'
import MahjongRiver from '@/components/mahjong/MahjongRiver.vue'
import MahjongCompass from '@/components/mahjong/MahjongCompass.vue'
import MahjongResultSheet from '@/components/mahjong/MahjongResultSheet.vue'
import { relationAPI } from '@/services/api'
import {
  seatWind,
  sideOfSeat,
  tileName,
  tileNames,
  type MahjongActionPayload,
  type MahjongPublicSeat,
  type MahjongSeat,
  type TableSide
} from '@/services/mahjongApi'
import { useMahjongStore } from '@/store/modules/mahjong'
import { copyText } from '@/utils/clipboard'

interface HandItem {
  key: string
  tile: number
  /** 刚摸到的那张，和手牌隔开一点 */
  drawn?: boolean
}

interface ActionButton {
  key: string
  label: string
  sub?: string
  kind: 'hu' | 'gang' | 'peng' | 'chi' | 'qiao' | 'pass' | 'cancel'
  on?: boolean
  run: () => void
}

interface Burst {
  key: number
  side: TableSide
  text: string
  kind: string
}

const TURN_LIMIT = 20
const CLAIM_LIMIT = 10
const MELD_TILE_W = 19
/** 往上滑超过这么多像素就算打出 */
const SWIPE_DISCARD_PX = 28

const BURST_KIND: Record<string, string> = {
  胡: 'hu',
  杠: 'gang',
  加杠: 'gang',
  暗杠: 'gang',
  碰: 'peng',
  吃: 'chi',
  敲: 'qiao',
  补花: 'flower',
  托管: 'auto'
}

const route = useRoute()
const router = useRouter()
const store = useMahjongStore()

const showRules = ref(false)
const showResult = ref(false)
const showChiSheet = ref(false)
const busy = ref(false)
const busySeat = ref<number | null>(null)
const inviting = ref(false)
const selectedKey = ref<string | null>(null)
const qiaoMode = ref(false)
const dealing = ref(false)
const inspectSeat = ref<number | null>(null)
const partner = ref<{ id: number; name: string } | null>(null)
const spotlight = ref<{ key: number; side: TableSide; tile: number } | null>(null)
const finishFx = ref<{ key: number; kind: string; text: string; sub: string } | null>(null)
const pointerStart = ref<{ key: string; tile: number; y: number } | null>(null)

let fxKey = 0
let suppressClick = false
const timers: Array<ReturnType<typeof setTimeout>> = []

// MobileLayout 以 path 作 key，换房间会重新挂载，这里读一次即可
const gameId = computed(() => Number(route.params.id))

const room = computed(() => store.room)
const round = computed(() => store.round)
const hand = computed(() => store.hand)
const options = computed(() => store.options)

/** 开过局（对局中或本局结束）就显示牌桌，否则显示入座 */
const inGame = computed(() => !!round.value && (store.isPlaying || store.isFinished))
const seatCount = computed(() => round.value?.seatCount ?? room.value?.seatCount ?? 4)
const capText = computed(() => (room.value?.capPoints ? `${room.value.capPoints}` : '不封顶'))
const totals = computed(() => room.value?.seats.map(seat => seat.totalScore ?? 0) ?? [])

function sideOf(seat: number): TableSide {
  return sideOfSeat(seat, store.mySeat, seatCount.value)
}

function isSideSeat(seat: number): boolean {
  const side = sideOf(seat)
  return side === 'left' || side === 'right'
}

// ------------------------------------------------------------------ 入座

const allSeated = computed(() => !!room.value?.seats.every(seat => seat.type !== 'EMPTY'))
const emptySeats = computed(() => room.value?.seats.filter(seat => seat.type === 'EMPTY').length ?? 0)

const waitingHint = computed(() => {
  if (store.isCancelled) return '房主已经取消了这个房间'
  if (store.isOwner) {
    return emptySeats.value ? '把房间号发给朋友，或把空位设成电脑' : '人齐了，可以开始'
  }
  if (store.mySeat === null) return '你被邀请加入这桌'
  return '等房主开局'
})

const canInvitePartner = computed(() => {
  const target = partner.value
  if (!target || !room.value || !store.isWaiting || emptySeats.value === 0) return false
  if (room.value.invitedUserId === target.id) return false
  return !room.value.seats.some(seat => seat.userId === target.id)
})

function seatNote(seat: MahjongSeat): string {
  if (seat.owner) return '房主'
  if (seat.type === 'BOT') return '电脑'
  if (seat.type === 'HUMAN') return '已入座'
  const invited = room.value?.invitedUserName
  if (invited && !room.value?.seats.some(item => item.userId === room.value?.invitedUserId)) {
    return `等${invited}`
  }
  return '空位'
}

// ------------------------------------------------------------------ 牌桌

const opponents = computed(() => {
  if (!round.value) return []
  return round.value.seats
    .filter(seat => sideOf(seat.seat) !== 'bottom')
    .map(seat => ({ seat, side: sideOf(seat.seat) as Exclude<TableSide, 'bottom'> }))
})

const myPublicSeat = computed<MahjongPublicSeat | null>(() => {
  if (!round.value || store.mySeat === null) return null
  return round.value.seats[store.mySeat] ?? null
})

const mySeatInfo = computed(() => (store.mySeat !== null ? room.value?.seats[store.mySeat] ?? null : null))

const rivers = computed(() => {
  const current = round.value
  if (!current) return []
  return current.seats.map(seat => ({
    side: sideOf(seat.seat),
    tiles: seat.discards,
    highlight: current.phase === 'CLAIM' && current.lastDiscardSeat === seat.seat
  }))
})

function isActiveSeat(seat: number): boolean {
  const current = round.value
  return !!current && store.isPlaying && current.phase === 'ACTION' && current.turn === seat
}

const compassWinds = computed(() => {
  const current = round.value
  if (!current) return []
  return current.seats.map(seat => ({
    side: sideOf(seat.seat),
    label: seatWind(seat.seat, current.dealer, seatCount.value),
    active: isActiveSeat(seat.seat)
  }))
})

const turnSeatType = computed(() => (round.value ? room.value?.seats[round.value.turn]?.type : undefined))

const countdown = computed(() => {
  const current = round.value
  if (!store.isPlaying || !current) return null
  const turnSeat = current.seats[current.turn]
  const humanWaiting = current.phase === 'CLAIM'
    ? store.claimPending
    : turnSeatType.value === 'HUMAN' && !turnSeat?.autoPlay && !turnSeat?.qiao
  if (!humanWaiting) return null
  return Math.ceil(store.remainingMs / 1000)
})

/** 自己的副露：暗杠用私有视角补上牌面 */
const myMelds = computed(() => {
  const seat = myPublicSeat.value
  if (!seat) return []
  const kongs = [...(hand.value?.concealedKongs ?? [])]
  return seat.melds.map(meld => {
    if (meld.tiles && meld.tiles.length) return { tiles: meld.tiles as Array<number | null> }
    const tile = kongs.shift()
    // 暗杠：两头扣着、中间两张亮给自己看
    return { tiles: tile !== undefined ? [null, tile, tile, null] : [null, null, null, null] }
  })
})

const qiaoTiles = computed(() => new Set((options.value?.qiaoOptions ?? []).map(option => option.tile)))
const discardable = computed(() => new Set(options.value?.discardable ?? []))
const canDiscardNow = computed(() => store.isMyTurn && !store.pending && !hand.value?.qiao && !hand.value?.autoPlay)

/** 手牌：同一种牌按第几张编号，摸牌、出牌、理牌时每张牌的 key 不变，可以平滑移动 */
const handTiles = computed<HandItem[]>(() => {
  const current = hand.value
  if (!current) return []
  const wild = round.value?.wild
  const sorted = [...current.hand].sort((a, b) => {
    const wa = a === wild ? 0 : 1
    const wb = b === wild ? 0 : 1
    return wa - wb || a - b
  })
  const seen = new Map<number, number>()
  const keyOf = (tile: number): string => {
    const index = seen.get(tile) ?? 0
    seen.set(tile, index + 1)
    return `${tile}#${index}`
  }
  const items: HandItem[] = sorted.map(tile => ({ key: keyOf(tile), tile }))
  if (current.drawnTile !== null && current.drawnTile !== undefined) {
    items.push({ key: keyOf(current.drawnTile), tile: current.drawnTile, drawn: true })
  }
  return items
})

/** 手牌排成一行：扣掉副露、牌间 2px 间隙和刚摸那张前面的空隙 */
const handTileWidth = computed(() => {
  const count = Math.max(handTiles.value.length, 8)
  const meldTiles = myMelds.value.reduce((sum, meld) => sum + meld.tiles.length, 0)
  const meldPx = meldTiles * MELD_TILE_W + myMelds.value.length * 4 + (myMelds.value.length ? 8 : 0)
  const gaps = (count - 1) * 2
  return `min(46px, calc((min(100vw, 760px) - 20px - ${meldPx + gaps}px) / ${count + 0.4}))`
})

const selectedTile = computed(() => {
  const item = handTiles.value.find(entry => entry.key === selectedKey.value)
  return item ? item.tile : null
})

function tileDimmed(tile: number): boolean {
  if (!store.isMyTurn) return false
  if (qiaoMode.value) return !qiaoTiles.value.has(tile)
  return !discardable.value.has(tile)
}

const actionButtons = computed<ActionButton[]>(() => {
  const current = hand.value
  const opts = options.value
  if (!store.isPlaying || !current) return []
  if (current.autoPlay) {
    return [{ key: 'cancel-auto', label: '取消', sub: '托管', kind: 'cancel', run: () => act({ type: 'CANCEL_AUTO' }) }]
  }
  if (!opts) return []
  const list: ActionButton[] = []
  if (store.claimPending) {
    if (opts.canHu) list.push({ key: 'hu', label: '胡', kind: 'hu', run: () => act({ type: 'HU' }) })
    if (opts.canKong) list.push({ key: 'gang', label: '杠', kind: 'gang', run: () => act({ type: 'KONG' }) })
    if (opts.canPong) list.push({ key: 'peng', label: '碰', kind: 'peng', run: () => act({ type: 'PONG' }) })
    if (opts.chiStarts?.length) list.push({ key: 'chi', label: '吃', kind: 'chi', run: chooseChi })
    list.push({ key: 'pass', label: '过', kind: 'pass', run: () => act({ type: 'PASS' }) })
    return list
  }
  if (store.isMyTurn && !current.qiao) {
    if (opts.canHu) list.push({ key: 'zimo', label: '胡', sub: '自摸', kind: 'hu', run: () => act({ type: 'HU' }) })
    const kongs = opts.kongTiles ?? []
    kongs.forEach(tile => list.push({
      key: `gang-${tile}`,
      label: '杠',
      sub: kongs.length > 1 ? tileName(tile) : undefined,
      kind: 'gang',
      run: () => act({ type: 'KONG', tile })
    }))
    if (opts.qiaoOptions?.length) {
      list.push({ key: 'qiao', label: '敲', sub: qiaoMode.value ? '取消' : undefined, kind: 'qiao', on: qiaoMode.value, run: toggleQiao })
    }
  }
  return list
})

const centerText = computed(() => {
  const current = round.value
  if (!current) return ''
  if (store.isFinished) return current.result?.drawGame ? '荒庄，本局不计分' : '本局结束'
  if (current.phase === 'CLAIM') {
    if (store.claimPending) return `${store.seatName(current.lastDiscardSeat)} 打出 ${tileName(current.lastDiscard)}，要吗？`
    return '等其他人选择'
  }
  if (store.isMyTurn) return '轮到你出牌'
  const name = store.seatName(current.turn)
  return turnSeatType.value === 'BOT' ? `${name} 思考中` : `等 ${name} 出牌`
})

const dockHint = computed(() => {
  const current = hand.value
  if (!current || !store.isPlaying) return centerText.value
  if (store.pending) return '处理中...'
  if (current.autoPlay) return '托管中，系统代打'
  if (current.qiao) {
    return current.waits?.length ? `已敲，听 ${tileNames(current.waits)}` : '已敲，系统自动摸打'
  }
  if (qiaoMode.value) {
    const option = options.value?.qiaoOptions?.find(item => item.tile === selectedTile.value)
    return option ? `打出${tileName(option.tile)}，敲后听 ${tileNames(option.waits)}` : '选一张亮线的牌打出就敲了'
  }
  if (store.isMyTurn) {
    return selectedTile.value !== null ? `再点一次打出${tileName(selectedTile.value)}` : '点两下或上滑出牌'
  }
  if (!store.claimPending && current.waits?.length) return `听 ${tileNames(current.waits)}`
  return centerText.value
})

const chiActions = computed(() => {
  const claimTile = options.value?.claimTile
  return (options.value?.chiStarts ?? []).map(start => ({
    name: [start, start + 1, start + 2].map(tile => tileName(tile)).join(' '),
    subname: claimTile ? `吃${tileName(claimTile)}` : undefined,
    start
  }))
})

/** 吃碰杠胡的大字，出在动作人那一边 */
const bursts = computed<Burst[]>(() => {
  const list: Burst[] = []
  for (const [seat, flash] of Object.entries(store.flashes)) {
    list.push({
      key: flash.key,
      side: sideOf(Number(seat)),
      text: flash.text,
      kind: BURST_KIND[flash.text] ?? 'peng'
    })
  }
  return list
})

const inspectOpen = computed({
  get: () => inspectSeat.value !== null,
  set: (open: boolean) => {
    if (!open) inspectSeat.value = null
  }
})

const inspected = computed(() => {
  const seatIndex = inspectSeat.value
  const current = round.value
  if (seatIndex === null || !current || !room.value) return null
  const seat = current.seats[seatIndex]
  const info = room.value.seats[seatIndex]
  if (!seat || !info) return null
  const score = info.totalScore ?? 0
  return {
    name: seatIndex === store.mySeat ? '你' : info.name || `${seatIndex + 1} 号位`,
    wind: `${seatWind(seatIndex, current.dealer, seatCount.value)}位`,
    dealer: seatIndex === current.dealer,
    score: score > 0 ? `+${score}` : String(score),
    qiao: seat.qiao,
    autoPlay: seat.autoPlay,
    flowers: seat.flowers,
    discards: seat.discards
  }
})

function seatNameForResult(seat: number): string {
  return store.seatName(seat)
}

// ------------------------------------------------------------------ 操作

function act(payload: MahjongActionPayload): void {
  store.act(payload)
  selectedKey.value = null
  qiaoMode.value = false
}

function play(item: HandItem): void {
  if (qiaoMode.value) {
    if (!qiaoTiles.value.has(item.tile)) {
      showToast({ message: '这张打出后还没听牌，不能敲', position: 'top' })
      return
    }
    act({ type: 'QIAO', tile: item.tile })
    return
  }
  act({ type: 'DISCARD', tile: item.tile })
}

function tapTile(item: HandItem): void {
  if (suppressClick) {
    suppressClick = false
    return
  }
  if (!canDiscardNow.value) return
  if (!qiaoMode.value && !discardable.value.has(item.tile)) {
    showToast({ message: '吃碰后不能马上打出这张牌', position: 'top' })
    return
  }
  if (selectedKey.value === item.key) {
    play(item)
    return
  }
  selectedKey.value = item.key
}

function onPointerDown(event: PointerEvent, item: HandItem): void {
  if (!canDiscardNow.value) return
  pointerStart.value = { key: item.key, tile: item.tile, y: event.clientY }
}

/** 往上滑出牌 */
function onPointerUp(event: PointerEvent): void {
  const start = pointerStart.value
  pointerStart.value = null
  if (!start || !canDiscardNow.value) return
  if (start.y - event.clientY < SWIPE_DISCARD_PX) return
  const item = handTiles.value.find(entry => entry.key === start.key)
  if (!item) return
  if (!qiaoMode.value && !discardable.value.has(item.tile)) return
  suppressClick = true
  play(item)
}

function toggleQiao(): void {
  qiaoMode.value = !qiaoMode.value
  selectedKey.value = null
}

function chooseChi(): void {
  const starts = options.value?.chiStarts ?? []
  if (starts.length === 1) {
    act({ type: 'CHI', chiStart: starts[0] })
    return
  }
  showChiSheet.value = true
}

function onChiSelect(action: { start: number }): void {
  act({ type: 'CHI', chiStart: action.start })
}

async function runRequest(task: () => Promise<void>, failText: string): Promise<boolean> {
  busy.value = true
  try {
    await task()
    return true
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || failText })
    return false
  } finally {
    busy.value = false
  }
}

async function toggleBot(seatIndex: number, bot: boolean): Promise<void> {
  busySeat.value = seatIndex
  try {
    await store.setSeat(seatIndex, bot)
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '设置失败' })
  } finally {
    busySeat.value = null
  }
}

async function startGame(): Promise<void> {
  await runRequest(() => store.startGame(), '开局失败')
}

async function nextRound(): Promise<void> {
  if (await runRequest(() => store.nextRound(), '开局失败')) {
    showResult.value = false
  }
}

async function joinRoom(): Promise<void> {
  await runRequest(() => store.joinCurrentRoom(), '加入失败')
}

async function invitePartner(): Promise<void> {
  if (!partner.value) return
  inviting.value = true
  try {
    await store.invite(partner.value.id)
    showToast({ type: 'success', message: `已邀请${partner.value.name}` })
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '邀请失败' })
  } finally {
    inviting.value = false
  }
}

async function leaveSeat(): Promise<void> {
  try {
    await showConfirmDialog({ title: '离开座位', message: '离开后可以凭房间号再加入。' })
  } catch {
    return
  }
  if (await runRequest(() => store.leaveSeat(), '离开失败')) {
    router.push('/mobile/game')
  }
}

async function cancelRoom(): Promise<void> {
  try {
    await showConfirmDialog({ title: '取消房间', message: '确定要取消这个麻将房间吗？' })
  } catch {
    return
  }
  if (await runRequest(() => store.cancelRoom(), '取消失败')) {
    router.push('/mobile/game')
  }
}

async function copyCode(): Promise<void> {
  if (!room.value) return
  try {
    await copyText(room.value.gameCode)
    showToast({ type: 'success', message: '房间号已复制' })
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '复制失败' })
  }
}

async function refresh(): Promise<void> {
  try {
    await store.refresh()
    showToast({ type: 'success', message: '已刷新' })
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '刷新失败' })
  }
}

function goBack(): void {
  router.push('/mobile/game')
}

async function loadPartner(): Promise<void> {
  try {
    const response = await relationAPI.getMyRelations({ pageNum: 1, pageSize: 10 })
    const relation = response?.records?.[0]
    if (relation) {
      partner.value = {
        id: relation.otherUserId,
        name: relation.otherUserName || relation.otherUserUsername || '对象'
      }
    }
  } catch {
    // 没有对象或加载失败时不显示邀请按钮
  }
}

function later(ms: number, task: () => void): void {
  timers.push(setTimeout(task, ms))
}

// ------------------------------------------------------------------ 动效

// 有人出牌：在他那一边放大闪一下这张牌，同时牌落进牌河。只和同一局里上一次的牌河比较
let lastRivers: { roundNo: number; sizes: number[] } | null = null
watch(
  () => round.value?.seats.map(seat => seat.discards.length).join(','),
  () => {
    const current = round.value
    if (!current) return
    const sizes = current.seats.map(seat => seat.discards.length)
    const previous = lastRivers?.roundNo === current.roundNo ? lastRivers.sizes : null
    lastRivers = { roundNo: current.roundNo, sizes }
    if (!previous || previous.length !== sizes.length) return
    const seat = sizes.findIndex((size, index) => size > previous[index])
    if (seat < 0) return
    const tile = current.seats[seat].discards[sizes[seat] - 1]
    const key = ++fxKey
    spotlight.value = { key, side: sideOf(seat), tile }
    later(1000, () => {
      if (spotlight.value?.key === key) spotlight.value = null
    })
  }
)

// 一局结束：先放胡牌 / 荒庄的大字，再弹出结算
watch(
  () => round.value?.phase,
  (phase, previous) => {
    if (phase === 'FINISHED' && previous && previous !== 'FINISHED') {
      const result = round.value?.result
      const wins = result?.wins ?? []
      const key = ++fxKey
      if (!result || result.drawGame) {
        finishFx.value = { key, kind: 'draw', text: '荒庄', sub: '下一局加一个荒番' }
      } else {
        const names = wins.map(win => store.seatName(win.seat)).join('、')
        const text = wins.length > 1 ? '一炮多响' : wins[0]?.fourWild ? '四百搭' : result.selfDraw ? '自摸' : '胡'
        finishFx.value = { key, kind: 'hu', text, sub: `${names} 胡了` }
      }
      later(1900, () => {
        if (finishFx.value?.key === key) finishFx.value = null
        if (store.isFinished) showResult.value = true
      })
    } else if (phase !== 'FINISHED') {
      showResult.value = false
      finishFx.value = null
    }
  }
)

// 新的一局：发牌动画
watch(
  () => round.value?.roundNo,
  (roundNo, previous) => {
    if (roundNo && roundNo !== previous) {
      dealing.value = true
      later(1500, () => {
        dealing.value = false
      })
    }
  }
)

// 局面变了就清掉选中的牌和「敲」模式
watch(() => hand.value?.step, () => {
  if (selectedKey.value && !handTiles.value.some(item => item.key === selectedKey.value)) {
    selectedKey.value = null
  }
  if (!store.isMyTurn) {
    selectedKey.value = null
    qiaoMode.value = false
  }
})

watch(() => store.isOwner && store.isWaiting, (owner) => {
  if (owner && !partner.value) void loadPartner()
}, { immediate: true })

onMounted(async () => {
  try {
    await store.enterRoom(gameId.value)
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '加载麻将房间失败' })
    router.replace('/mobile/game')
  }
})

onUnmounted(() => {
  timers.forEach(timer => clearTimeout(timer))
  store.leaveRoomView()
})
</script>

<style scoped>
/* ------------------------------------------------ 牌桌骨架 */

.mj-room {
  height: 100%;
}

.table {
  --felt-1: #2c7b58;
  --felt-2: #1c5c41;
  --felt-3: #0d3a29;
  --gold: #f6c453;

  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 100%;
  overflow: hidden;
  background: var(--felt-3);
  color: #fff;
  user-select: none;
}

.table.loading {
  align-items: center;
  justify-content: center;
}

.top-bar {
  position: relative;
  z-index: 3;
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 4px;
  padding: calc(4px + env(safe-area-inset-top)) 6px 4px;
  background: rgba(3, 18, 12, 0.6);
}

.icon-btn {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  font-size: 18px;
  cursor: pointer;
}

.top-info {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 2px 10px;
  min-width: 0;
  color: rgba(255, 255, 255, 0.86);
  font-size: 12px;
}

.top-wild {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.conn-off {
  flex-shrink: 0;
  padding: 2px 6px;
  border-radius: 4px;
  background: #b3261e;
  font-size: 11px;
}

.felt {
  position: relative;
  flex: 1;
  min-height: 0;
  background: radial-gradient(ellipse 120% 90% at 50% 46%, var(--felt-1) 0%, var(--felt-2) 48%, var(--felt-3) 100%);
}

/* 呢面纹理和四周压暗 */
.felt::before,
.felt::after {
  position: absolute;
  inset: 0;
  content: '';
  pointer-events: none;
}

.felt::before {
  background-image:
    repeating-linear-gradient(45deg, rgba(255, 255, 255, 0.022) 0 1px, transparent 1px 4px),
    repeating-linear-gradient(-45deg, rgba(0, 0, 0, 0.035) 0 1px, transparent 1px 4px);
}

.felt::after {
  box-shadow: inset 0 0 70px rgba(0, 0, 0, 0.5);
}

/* ------------------------------------------------ 座位 */

.seat-anchor,
.wait-seat {
  position: absolute;
  z-index: 1;
}

.seat-anchor.at-top {
  top: 8px;
  left: 50%;
  transform: translateX(-50%);
}

.seat-anchor.at-left {
  top: 46%;
  left: 6px;
  transform: translateY(-50%);
}

.seat-anchor.at-right {
  top: 46%;
  right: 6px;
  transform: translateY(-50%);
}

/* ------------------------------------------------ 中间：牌河 + 方位盘 */

.board {
  --board-top: 88px;
  --board-x: 74px;

  position: absolute;
  inset: var(--board-top) var(--board-x) 8px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  grid-template-rows: 1fr auto 1fr;
  gap: 6px;
  align-items: center;
  justify-items: center;
}

.seats-3 .board {
  --board-top: 12px;
}

.seats-2 .board {
  --board-x: 12px;
}

.river-top {
  grid-area: 1 / 1 / 2 / 4;
  align-self: end;
}

.river-left {
  grid-area: 2 / 1;
  justify-self: end;
}

.board-compass {
  grid-area: 2 / 2;
}

.river-right {
  grid-area: 2 / 3;
  justify-self: start;
}

.river-bottom {
  grid-area: 3 / 1 / 4 / 4;
  align-self: start;
}

/* ------------------------------------------------ 等待开局 */

.wait-seat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.wait-seat.at-bottom {
  bottom: 18px;
  left: 50%;
  transform: translateX(-50%);
}

.wait-seat.at-top {
  top: 18px;
  left: 50%;
  transform: translateX(-50%);
}

.wait-seat.at-left {
  top: 50%;
  left: 8px;
  transform: translateY(-50%);
}

.wait-seat.at-right {
  top: 50%;
  right: 8px;
  transform: translateY(-50%);
}

.wait-note {
  color: rgba(255, 255, 255, 0.7);
  font-size: 11px;
}

.chip-btn {
  padding: 4px 10px;
  border: 1px solid rgba(246, 196, 83, 0.6);
  border-radius: 999px;
  background: rgba(246, 196, 83, 0.14);
  color: var(--gold);
  font-size: 12px;
  cursor: pointer;
}

.chip-btn:disabled {
  opacity: 0.5;
}

.wait-center {
  position: absolute;
  top: 50%;
  left: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: min(220px, 56%);
  padding: 16px 14px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 18px;
  background: rgba(5, 24, 17, 0.7);
  text-align: center;
  transform: translate(-50%, -50%);
}

.room-label {
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
}

.room-code {
  color: var(--gold);
  font-size: 30px;
  letter-spacing: 0.1em;
  line-height: 1.1;
}

.room-hint {
  color: rgba(255, 255, 255, 0.72);
  font-size: 12px;
  line-height: 1.5;
}

/* ------------------------------------------------ 底部 */

.my-area {
  position: relative;
  z-index: 2;
  flex-shrink: 0;
  padding: 6px 10px calc(8px + env(safe-area-inset-bottom));
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  background: linear-gradient(180deg, #123f2e 0%, #0b2c20 100%);
}

.wait-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 44px;
}

.wait-text {
  color: rgba(255, 255, 255, 0.75);
  font-size: 13px;
}

.pill-btn {
  min-width: 88px;
  height: 40px;
  padding: 0 18px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.pill-btn.primary {
  border-color: #ffd98a;
  background: linear-gradient(180deg, #ffd770 0%, #f0a42a 100%);
  color: #4a2a06;
  box-shadow: 0 3px 0 #a5620d;
}

.pill-btn.ghost {
  background: transparent;
}

.pill-btn.small {
  min-width: 0;
  height: 30px;
  padding: 0 12px;
  font-size: 13px;
}

.pill-btn:disabled {
  opacity: 0.55;
  cursor: default;
}

.my-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
}

.my-hint {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: rgba(255, 255, 255, 0.82);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.my-tiles {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 8px;
  padding-top: 12px;
}

.my-melds {
  display: flex;
  flex-shrink: 0;
  align-items: flex-end;
  gap: 4px;
}

.my-meld {
  display: inline-flex;
}

.my-hand {
  position: relative;
  display: flex;
  align-items: flex-end;
  gap: 2px;
}

.my-hand > .is-drawn {
  margin-left: calc(var(--hand-tile-w) * 0.4);
}

/* 理牌、摸牌、出牌时平滑移动 */
.hand-move {
  transition: transform 0.26s ease;
}

.hand-enter-active {
  transition: opacity 0.26s ease-out, transform 0.26s ease-out;
}

.dealing .hand-enter-active {
  transition-duration: 0.34s;
  transition-delay: calc(var(--i, 0) * 45ms);
}

.hand-enter-from {
  opacity: 0;
  transform: translateY(-22px);
}

.hand-leave-active {
  position: absolute;
  transition: opacity 0.2s ease-in, transform 0.2s ease-in;
}

.hand-leave-to {
  opacity: 0;
  transform: translateY(-48px) scale(0.85);
}

/* 吃碰杠胡的大按钮，漂在手牌上方 */
.actions {
  position: absolute;
  right: 10px;
  bottom: calc(100% + 10px);
  display: flex;
  gap: 10px;
}

.actions.claim {
  right: 50%;
  transform: translateX(50%);
}

.act-btn {
  position: relative;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border: 2px solid rgba(255, 255, 255, 0.85);
  border-radius: 50%;
  color: #fff;
  font-family: var(--font-sans);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.45);
  box-shadow: 0 4px 0 rgba(0, 0, 0, 0.28), 0 8px 18px rgba(0, 0, 0, 0.35);
  cursor: pointer;
}

.act-btn:active {
  transform: translateY(2px);
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.28), 0 4px 10px rgba(0, 0, 0, 0.35);
}

.act-btn:disabled {
  filter: grayscale(0.6);
  opacity: 0.7;
}

.act-label {
  font-size: 22px;
  font-weight: 800;
  line-height: 1;
}

.act-sub {
  margin-top: 2px;
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
}

.act-btn.kind-hu {
  width: 64px;
  height: 64px;
  background: radial-gradient(circle at 35% 30%, #ffe08a 0%, #f39a2b 55%, #c2410c 100%);
}

.act-btn.kind-hu .act-label {
  font-size: 28px;
}

.act-btn.kind-gang {
  background: radial-gradient(circle at 35% 30%, #9fd8ff 0%, #2f80d8 60%, #1b4d97 100%);
}

.act-btn.kind-peng {
  background: radial-gradient(circle at 35% 30%, #aef0c8 0%, #2eac6b 60%, #18673f 100%);
}

.act-btn.kind-chi {
  background: radial-gradient(circle at 35% 30%, #c8f3ff 0%, #22a3c6 60%, #126b84 100%);
}

.act-btn.kind-qiao {
  background: radial-gradient(circle at 35% 30%, #dccbff 0%, #8b5cf6 60%, #5730c4 100%);
}

.act-btn.kind-qiao.on {
  box-shadow: 0 0 0 4px rgba(246, 196, 83, 0.6), 0 4px 0 rgba(0, 0, 0, 0.28);
}

.act-btn.kind-pass,
.act-btn.kind-cancel {
  background: radial-gradient(circle at 35% 30%, #eef0f3 0%, #9aa1ab 60%, #646b75 100%);
}

.act-enter-active {
  animation: act-pop 0.3s cubic-bezier(0.2, 1.5, 0.4, 1) both;
}

.act-leave-active {
  transition: opacity 0.12s ease-in;
}

.act-leave-to {
  opacity: 0;
}

@keyframes act-pop {
  from {
    opacity: 0;
    transform: scale(0.3);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

/* ------------------------------------------------ 动效 */

.fx-layer {
  position: absolute;
  inset: 0;
  z-index: 4;
  pointer-events: none;
}

.fx-spotlight,
.fx-burst {
  position: absolute;
}

.fx-spotlight.at-bottom {
  bottom: 16%;
  left: 50%;
}

.fx-spotlight.at-top {
  top: 22%;
  left: 50%;
}

.fx-spotlight.at-left {
  top: 40%;
  left: 24%;
}

.fx-spotlight.at-right {
  top: 40%;
  right: 24%;
}

.fx-spotlight {
  animation: spotlight 1s ease-out both;
}

.fx-spotlight.at-right {
  animation-name: spotlight-right;
}

@keyframes spotlight {
  0% {
    opacity: 0;
    transform: translate(-50%, 0) scale(0.5);
  }
  18% {
    opacity: 1;
    transform: translate(-50%, 0) scale(1.1);
  }
  30% {
    transform: translate(-50%, 0) scale(1);
  }
  80% {
    opacity: 1;
  }
  100% {
    opacity: 0;
    transform: translate(-50%, 0) scale(0.8);
  }
}

@keyframes spotlight-right {
  0% {
    opacity: 0;
    transform: translate(50%, 0) scale(0.5);
  }
  18% {
    opacity: 1;
    transform: translate(50%, 0) scale(1.1);
  }
  30% {
    transform: translate(50%, 0) scale(1);
  }
  80% {
    opacity: 1;
  }
  100% {
    opacity: 0;
    transform: translate(50%, 0) scale(0.8);
  }
}

.fx-burst {
  color: #ffe29a;
  font-size: 46px;
  font-weight: 900;
  line-height: 1;
  white-space: nowrap;
  -webkit-text-stroke: 1.5px #7a3510;
  text-shadow: 0 3px 0 #7a3510, 0 0 18px rgba(255, 176, 60, 0.9);
  animation: burst 1.5s cubic-bezier(0.2, 1.3, 0.4, 1) both;
}

.fx-burst.at-bottom {
  bottom: 22%;
  left: 50%;
}

.fx-burst.at-top {
  top: 16%;
  left: 50%;
}

.fx-burst.at-left {
  top: 30%;
  left: 18%;
}

.fx-burst.at-right {
  top: 30%;
  right: 18%;
}

.fx-burst.at-right {
  animation-name: burst-right;
}

.fx-burst.kind-hu {
  color: #fff1b8;
  font-size: 64px;
  -webkit-text-stroke-color: #8f1d0e;
  text-shadow: 0 4px 0 #8f1d0e, 0 0 26px rgba(255, 120, 40, 0.95);
}

.fx-burst.kind-qiao {
  color: #e6dbff;
  -webkit-text-stroke-color: #3b2380;
  text-shadow: 0 3px 0 #3b2380, 0 0 18px rgba(170, 130, 255, 0.9);
}

.fx-burst.kind-flower,
.fx-burst.kind-auto {
  font-size: 22px;
  -webkit-text-stroke-width: 1px;
}

@keyframes burst {
  0% {
    opacity: 0;
    transform: translate(-50%, 0) scale(2.6);
  }
  14% {
    opacity: 1;
    transform: translate(-50%, 0) scale(0.88);
  }
  24% {
    transform: translate(-50%, 0) scale(1.06);
  }
  32%,
  78% {
    opacity: 1;
    transform: translate(-50%, 0) scale(1);
  }
  100% {
    opacity: 0;
    transform: translate(-50%, -14px) scale(1.04);
  }
}

@keyframes burst-right {
  0% {
    opacity: 0;
    transform: translate(50%, 0) scale(2.6);
  }
  14% {
    opacity: 1;
    transform: translate(50%, 0) scale(0.88);
  }
  24% {
    transform: translate(50%, 0) scale(1.06);
  }
  32%,
  78% {
    opacity: 1;
    transform: translate(50%, 0) scale(1);
  }
  100% {
    opacity: 0;
    transform: translate(50%, -14px) scale(1.04);
  }
}

.fx-finish {
  position: absolute;
  top: 44%;
  left: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  transform: translate(-50%, -50%);
}

.fx-rays {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 320px;
  height: 320px;
  margin: -160px 0 0 -160px;
  border-radius: 50%;
  background: repeating-conic-gradient(rgba(255, 214, 102, 0.32) 0deg 10deg, transparent 10deg 24deg);
  -webkit-mask-image: radial-gradient(circle, #000 0%, transparent 68%);
  mask-image: radial-gradient(circle, #000 0%, transparent 68%);
  animation: rays-spin 6s linear infinite, rays-in 0.5s ease-out both;
}

.kind-draw .fx-rays {
  background: repeating-conic-gradient(rgba(200, 220, 230, 0.18) 0deg 10deg, transparent 10deg 24deg);
}

.fx-title {
  position: relative;
  color: #fff1b8;
  font-size: 76px;
  font-weight: 900;
  line-height: 1;
  white-space: nowrap;
  -webkit-text-stroke: 2px #8f1d0e;
  text-shadow: 0 5px 0 #8f1d0e, 0 0 34px rgba(255, 120, 40, 0.95);
  animation: finish-title 1.9s cubic-bezier(0.2, 1.3, 0.4, 1) both;
}

.kind-draw .fx-title {
  color: #e9f1f4;
  font-size: 58px;
  -webkit-text-stroke-color: #244653;
  text-shadow: 0 4px 0 #244653, 0 0 24px rgba(160, 200, 220, 0.7);
}

@keyframes finish-title {
  0% {
    opacity: 0;
    transform: scale(2.8);
  }
  16% {
    opacity: 1;
    transform: scale(0.9);
  }
  26%,
  82% {
    opacity: 1;
    transform: scale(1);
  }
  100% {
    opacity: 0;
    transform: scale(1.05);
  }
}

.fx-sub {
  position: relative;
  padding: 3px 12px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 14px;
  animation: rays-in 0.4s ease-out 0.25s both;
}

@keyframes rays-spin {
  to {
    rotate: 360deg;
  }
}

@keyframes rays-in {
  from {
    opacity: 0;
    scale: 0.6;
  }
  to {
    opacity: 1;
    scale: 1;
  }
}

/* ------------------------------------------------ 弹层 */

.inspect,
.rules {
  padding: 4px 18px calc(20px + env(safe-area-inset-bottom));
  color: var(--ink-2);
  font-size: 13px;
  line-height: 1.7;
}

.inspect-meta {
  color: var(--ink-2);
  text-align: center;
}

.inspect-label {
  margin-top: 12px;
  color: var(--ink);
  font-weight: 600;
}

.inspect-tiles {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  margin-top: 6px;
}

.inspect-empty {
  color: var(--ink-3);
}

.rules h4 {
  margin-top: 12px;
  color: var(--ink);
  font-size: 14px;
}

.rules ul {
  margin: 4px 0 0;
  padding-left: 18px;
}

@media (prefers-reduced-motion: reduce) {
  .fx-spotlight,
  .fx-burst,
  .fx-title,
  .fx-sub,
  .fx-rays,
  .act-enter-active {
    animation: none;
  }

  .hand-move,
  .hand-enter-active,
  .hand-leave-active {
    transition: none;
  }
}
</style>
