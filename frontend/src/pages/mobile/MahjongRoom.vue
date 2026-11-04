<template>
  <div class="mj-room">
    <van-nav-bar :title="title" left-arrow fixed placeholder @click-left="goBack">
      <template #right>
        <div class="nav-actions">
          <van-icon name="replay" size="20" aria-label="刷新" @click="refresh" />
          <van-icon name="question-o" size="20" aria-label="规则" @click="showRules = true" />
        </div>
      </template>
    </van-nav-bar>

    <div v-if="!room" class="page-state">
      <van-loading size="24px" vertical>正在进入房间...</van-loading>
    </div>

    <!-- 等待开局 / 已取消 -->
    <div v-else-if="store.isWaiting || store.isCancelled" class="m-page waiting">
      <section class="m-card m-card--pad room-card">
        <div class="room-code-row">
          <div>
            <p class="room-label">房间号</p>
            <strong class="room-code tabular">{{ room.gameCode }}</strong>
          </div>
          <van-button size="small" plain type="primary" @click="copyCode">复制</van-button>
        </div>
        <p class="room-meta">{{ room.modeName }} · {{ room.seatCount }} 人桌 · 封顶 {{ capText }}</p>
        <p class="room-hint">{{ waitingHint }}</p>
      </section>

      <section class="m-card seat-list" aria-label="座位">
        <div v-for="seat in room.seats" :key="seat.seatIndex" class="seat-item">
          <span class="seat-no tabular">{{ seat.seatIndex + 1 }}</span>
          <UserAvatar v-if="seat.type === 'HUMAN'" :src="seat.avatar" :name="seat.name" :size="36" />
          <span v-else class="seat-placeholder" :class="{ bot: seat.type === 'BOT' }" aria-hidden="true">
            {{ seat.type === 'BOT' ? '电' : '' }}
          </span>
          <div class="seat-text">
            <strong>{{ seatTitle(seat) }}</strong>
            <span>{{ seatNote(seat) }}</span>
          </div>
          <template v-if="store.isOwner && store.isWaiting && seat.seatIndex > 0">
            <van-button
              v-if="seat.type === 'EMPTY'"
              size="small"
              plain
              :loading="busySeat === seat.seatIndex"
              @click="toggleBot(seat.seatIndex, true)"
            >设为电脑</van-button>
            <van-button
              v-else-if="seat.type === 'BOT'"
              size="small"
              plain
              :loading="busySeat === seat.seatIndex"
              @click="toggleBot(seat.seatIndex, false)"
            >改回空位</van-button>
          </template>
        </div>
      </section>

      <section class="waiting-actions">
        <template v-if="store.isCancelled">
          <van-button block @click="goBack">返回大厅</van-button>
        </template>
        <template v-else-if="store.isOwner">
          <van-button type="primary" block :disabled="!allSeated" :loading="busy" @click="startGame">
            {{ allSeated ? '开始' : '等座位坐满后开始' }}
          </van-button>
          <van-button v-if="canInvitePartner" plain block :loading="inviting" @click="invitePartner">
            邀请{{ partner?.name }}
          </van-button>
          <van-button plain block @click="cancelRoom">取消房间</van-button>
        </template>
        <template v-else-if="store.mySeat === null">
          <van-button type="primary" block :loading="busy" @click="joinRoom">加入</van-button>
        </template>
        <template v-else>
          <van-button plain block @click="leaveSeat">离开座位</van-button>
        </template>
      </section>
    </div>

    <!-- 牌局 -->
    <div v-else-if="round" class="table">
      <section class="status-strip" aria-label="牌局信息">
        <span>第 {{ round.roundNo }} 局</span>
        <span class="tabular">剩 {{ round.wallCount }} 张</span>
        <span v-if="round.wild" class="wild-info">
          百搭 <MahjongTile :tile="round.wild" size="xs" wild />
          <template v-if="round.indicator">（翻 <MahjongTile :tile="round.indicator" size="xs" />）</template>
        </span>
        <span v-if="round.huangCount">荒番 {{ round.huangCount }}</span>
        <span>封顶 {{ capText }}</span>
        <span class="conn" :class="{ off: !store.isConnected }">{{ store.isConnected ? '' : '连接中断，正在重连' }}</span>
      </section>

      <div class="opponents">
        <MahjongSeatPanel
          v-for="seat in opponentSeats"
          :key="seat.seat"
          :seat="seat"
          :info="room.seats[seat.seat]"
          :relation="relationOf(seat.seat)"
          :dealer="seat.seat === round.dealer"
          :active="isActiveSeat(seat.seat)"
          :waiting-claims="!!round.waitingClaims"
          :flash="store.flashes[seat.seat] ?? null"
          :wild="round.wild ?? null"
          :latest-discard="round.lastDiscardSeat === seat.seat && round.phase === 'CLAIM'"
        />
      </div>

      <section class="center" aria-live="polite">
        <div v-if="showClaimTile" class="claim-tile">
          <MahjongTile :tile="round.lastDiscard" size="md" latest />
          <span>{{ store.seatName(round.lastDiscardSeat) }} 打出 {{ tileName(round.lastDiscard) }}</span>
        </div>
        <p class="center-text">
          {{ centerText }}
          <span v-if="countdown !== null" class="countdown tabular">{{ countdown }} 秒</span>
        </p>
        <van-button v-if="store.isFinished && round.result" size="small" plain type="primary" @click="showResult = true">
          查看结算
        </van-button>
      </section>

      <MahjongSeatPanel
        v-if="mySeatView"
        :seat="mySeatView"
        :info="room.seats[mySeatView.seat]"
        relation="你"
        self
        :dealer="mySeatView.seat === round.dealer"
        :active="isActiveSeat(mySeatView.seat)"
        :waiting-claims="!!round.waitingClaims"
        :flash="store.flashes[mySeatView.seat] ?? null"
        :wild="round.wild ?? null"
        :latest-discard="round.lastDiscardSeat === mySeatView.seat && round.phase === 'CLAIM'"
      />

      <!-- 手牌和操作，固定在底部 -->
      <div v-if="hand && store.isPlaying" class="dock">
        <p v-if="dockHint" class="dock-hint">{{ dockHint }}</p>
        <div class="hand-row" :style="{ '--hand-tile-w': handTileWidth }" role="group" aria-label="我的手牌">
          <MahjongTile
            v-for="item in handTiles"
            :key="item.key"
            :tile="item.tile"
            size="hand"
            :class="{ 'is-drawn': item.drawn }"
            interactive
            :wild="item.tile === round.wild"
            :selected="selectedKey === item.key"
            :marked="qiaoTiles.has(item.tile)"
            :dim="store.isMyTurn && !discardable.has(item.tile)"
            :disabled="!store.isMyTurn || store.pending"
            @click="tapTile(item)"
          />
        </div>

        <div class="action-row">
          <template v-if="hand.autoPlay">
            <span class="dock-state">托管中，系统代打</span>
            <van-button size="small" type="primary" @click="act({ type: 'CANCEL_AUTO' })">取消托管</van-button>
          </template>
          <template v-else-if="store.claimPending && options">
            <van-button v-if="options.canHu" size="small" type="primary" :disabled="store.pending" @click="act({ type: 'HU' })">胡</van-button>
            <van-button v-if="options.canKong" size="small" plain type="primary" :disabled="store.pending" @click="act({ type: 'KONG' })">杠</van-button>
            <van-button v-if="options.canPong" size="small" plain type="primary" :disabled="store.pending" @click="act({ type: 'PONG' })">碰</van-button>
            <van-button v-if="options.chiStarts?.length" size="small" plain type="primary" :disabled="store.pending" @click="chooseChi">吃</van-button>
            <van-button size="small" plain :disabled="store.pending" @click="act({ type: 'PASS' })">过</van-button>
          </template>
          <template v-else-if="store.isMyTurn && options && !hand.qiao">
            <van-button v-if="options.canHu" size="small" type="primary" :disabled="store.pending" @click="act({ type: 'HU' })">自摸</van-button>
            <van-button
              v-for="tile in options.kongTiles ?? []"
              :key="`kong-${tile}`"
              size="small"
              plain
              type="primary"
              :disabled="store.pending"
              @click="act({ type: 'KONG', tile })"
            >杠{{ tileName(tile) }}</van-button>
            <van-button
              v-if="selectedTile !== null && qiaoTiles.has(selectedTile)"
              size="small"
              plain
              type="primary"
              :disabled="store.pending"
              @click="act({ type: 'QIAO', tile: selectedTile })"
            >敲</van-button>
            <van-button
              size="small"
              type="primary"
              :plain="options.canHu"
              :disabled="selectedTile === null || store.pending"
              @click="discardSelected"
            >出牌</van-button>
          </template>
          <span v-else class="dock-state">{{ idleText }}</span>
        </div>
      </div>
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

    <van-popup v-model:show="showRules" position="bottom" round closeable :style="{ maxHeight: '80%' }">
      <div class="rules">
        <div class="m-sheet-title">上海麻将规则</div>
        <h4>共同</h4>
        <ul>
          <li>144 张牌，中发白和春夏秋冬梅兰竹菊都算花，摸到亮出、从牌尾补。</li>
          <li>只认 4 副 + 1 对，不带七对。只能吃上家；吃碰后这一手不能马上打出同一种牌。</li>
          <li>抢牌：胡 > 杠 / 碰 > 吃；几家同时能胡就一炮多响，放炮者分别付。</li>
          <li>倍数 = 花数 × 2<sup>番</sup>。自摸其余每家各付，点炮由放炮者付；每人每局最多付封顶分。</li>
          <li>荒庄不计分，下一局加 1 荒番（每个 1 番），有人胡后清零。庄家每局顺延。</li>
          <li>真人出牌限时 20 秒、吃碰响应 10 秒；超时由系统代打并进入托管。</li>
        </ul>
        <h4>敲麻</h4>
        <ul>
          <li>必须先敲（出牌时选「敲」，打出后要听牌）才能胡；敲后手牌锁定，系统自动摸打，有胡必胡。</li>
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
import UserAvatar from '@/components/UserAvatar.vue'
import MahjongTile from '@/components/mahjong/MahjongTile.vue'
import MahjongSeatPanel from '@/components/mahjong/MahjongSeatPanel.vue'
import MahjongResultSheet from '@/components/mahjong/MahjongResultSheet.vue'
import { relationAPI } from '@/services/api'
import { tileName, tileNames, type MahjongActionPayload, type MahjongSeat } from '@/services/mahjongApi'
import { useMahjongStore } from '@/store/modules/mahjong'
import { copyText } from '@/utils/clipboard'

interface HandItem {
  key: string
  tile: number
  /** 刚摸到的那张，和手牌隔开一点 */
  drawn?: boolean
}

interface PartnerOption {
  id: number
  name: string
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
const partner = ref<PartnerOption | null>(null)

// MobileLayout 以 path 作 key，换房间会重新挂载，这里读一次即可
const gameId = computed(() => Number(route.params.id))

const room = computed(() => store.room)
const round = computed(() => store.round)
const hand = computed(() => store.hand)
const options = computed(() => store.options)

const title = computed(() => {
  if (!room.value) return '上海麻将'
  if (store.isWaiting) return `${room.value.modeName} · 等待开局`
  if (store.isCancelled) return '房间已取消'
  return `上海麻将 · ${room.value.modeName}`
})

const capText = computed(() => (room.value?.capPoints ? `${room.value.capPoints} 分` : '不封顶'))
const totals = computed(() => room.value?.seats.map(seat => seat.totalScore ?? 0) ?? [])

const allSeated = computed(() => !!room.value?.seats.every(seat => seat.type !== 'EMPTY'))
const emptySeats = computed(() => room.value?.seats.filter(seat => seat.type === 'EMPTY').length ?? 0)

const waitingHint = computed(() => {
  if (store.isCancelled) return '房主已经取消了这个房间'
  if (store.isOwner) {
    return emptySeats.value
      ? `把房间号发给朋友，或把空位设成电脑；还差 ${emptySeats.value} 位`
      : '座位已坐满，可以开始了'
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

// ------------------------------------------------------------------ 牌桌

const seatCount = computed(() => round.value?.seatCount ?? room.value?.seatCount ?? 4)

/** 按出牌顺序排在我后面的人：下家、对家、上家 */
const opponentSeats = computed(() => {
  if (!round.value) return []
  const base = store.mySeat ?? 0
  const seats = []
  for (let offset = 1; offset < seatCount.value; offset++) {
    const seat = round.value.seats[(base + offset) % seatCount.value]
    if (seat) seats.push(seat)
  }
  return store.mySeat === null ? round.value.seats : seats
})

const mySeatView = computed(() => {
  if (!round.value || store.mySeat === null) return null
  return round.value.seats[store.mySeat] ?? null
})

function relationOf(seat: number): string {
  if (store.mySeat === null) return `${seat + 1} 号位`
  const offset = (seat - store.mySeat + seatCount.value) % seatCount.value
  if (seatCount.value === 2) return '对家'
  if (seatCount.value === 3) return offset === 1 ? '下家' : '上家'
  return ['你', '下家', '对家', '上家'][offset]
}

function isActiveSeat(seat: number): boolean {
  return !!round.value && store.isPlaying && round.value.turn === seat && round.value.phase !== 'FINISHED'
}

const turnSeatType = computed(() => (round.value ? room.value?.seats[round.value.turn]?.type : undefined))

const countdown = computed(() => {
  if (!store.isPlaying || !round.value) return null
  const humanWaiting = round.value.phase === 'CLAIM'
    ? store.claimPending
    : turnSeatType.value === 'HUMAN' && !round.value.seats[round.value.turn]?.autoPlay && !round.value.seats[round.value.turn]?.qiao
  if (!humanWaiting) return null
  return Math.ceil(store.remainingMs / 1000)
})

const showClaimTile = computed(() => {
  return !!round.value && round.value.phase === 'CLAIM' && round.value.lastDiscard !== null && round.value.lastDiscard !== undefined
})

const centerText = computed(() => {
  const current = round.value
  if (!current) return ''
  if (store.isFinished) return current.result?.drawGame ? '荒庄，本局不计分' : '本局结束'
  if (current.phase === 'CLAIM') {
    return store.claimPending ? '要这张牌吗？' : '等其他人选择'
  }
  if (store.isMyTurn) return '轮到你出牌'
  const name = store.seatName(current.turn)
  return turnSeatType.value === 'BOT' ? `${name}思考中` : `轮到${name}出牌`
})

const discardable = computed(() => new Set(options.value?.discardable ?? []))
const qiaoTiles = computed(() => new Set((options.value?.qiaoOptions ?? []).map(option => option.tile)))

const handTiles = computed<HandItem[]>(() => {
  const current = hand.value
  if (!current) return []
  const wild = round.value?.wild
  // 百搭放最左边
  const sorted = [...current.hand].sort((a, b) => {
    const wa = a === wild ? 0 : 1
    const wb = b === wild ? 0 : 1
    return wa - wb || a - b
  })
  const items: HandItem[] = sorted.map((tile, index) => ({ key: `h-${index}-${tile}`, tile }))
  if (current.drawnTile !== null && current.drawnTile !== undefined) {
    items.push({ key: `d-${current.drawnTile}`, tile: current.drawnTile, drawn: true })
  }
  return items
})

/** 14 张手牌在窄屏上也排成一行：扣掉左右留白、牌间 2px 间隙和刚摸那张前面的空隙 */
const handTileWidth = computed(() => {
  const count = Math.max(handTiles.value.length, 8)
  const gaps = (count - 1) * 2
  return `min(38px, calc((min(100vw, 520px) - 32px - ${gaps}px) / ${count + 0.4}))`
})

const selectedTile = computed(() => {
  const item = handTiles.value.find(entry => entry.key === selectedKey.value)
  return item ? item.tile : null
})

const dockHint = computed(() => {
  const current = hand.value
  if (!current) return ''
  if (current.qiao) {
    return current.waits?.length ? `已敲，听 ${tileNames(current.waits)}，系统自动摸打` : '已敲，系统自动摸打'
  }
  if (store.isMyTurn && options.value?.qiaoOptions?.length) {
    const option = options.value.qiaoOptions.find(item => item.tile === selectedTile.value)
    return option ? `打出${tileName(option.tile)}可以敲，听 ${tileNames(option.waits)}` : '有下划线的牌打出后可以敲'
  }
  if (store.isMyTurn) return selectedTile.value !== null ? '再点一次这张牌直接打出' : '点一张牌选中'
  if (current.waits?.length) return `听 ${tileNames(current.waits)}`
  return ''
})

const idleText = computed(() => {
  if (store.pending) return '处理中...'
  if (!store.isConnected) return '连接中断，正在重连'
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

function seatNameForResult(seat: number): string {
  return store.seatName(seat)
}

// ------------------------------------------------------------------ 操作

function act(payload: MahjongActionPayload): void {
  store.act(payload)
  selectedKey.value = null
}

function tapTile(item: HandItem): void {
  if (!store.isMyTurn || store.pending) return
  if (!discardable.value.has(item.tile)) {
    showToast({ message: hand.value?.qiao ? '敲了之后只能打摸到的牌' : '吃碰后不能马上打出这张牌', position: 'top' })
    return
  }
  if (selectedKey.value === item.key) {
    act({ type: 'DISCARD', tile: item.tile })
    return
  }
  selectedKey.value = item.key
}

function discardSelected(): void {
  if (selectedTile.value === null) return
  act({ type: 'DISCARD', tile: selectedTile.value })
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

function seatTitle(seat: MahjongSeat): string {
  if (seat.type === 'HUMAN') return seat.name || '玩家'
  if (seat.type === 'BOT') return seat.name || '电脑'
  return '空位'
}

function seatNote(seat: MahjongSeat): string {
  if (seat.owner) return '房主'
  if (seat.type === 'BOT') return '电脑玩家'
  if (seat.type === 'HUMAN') return '已入座'
  if (room.value?.invitedUserId && room.value.invitedUserName && !room.value.seats.some(item => item.userId === room.value?.invitedUserId)) {
    return `已邀请${room.value.invitedUserName}，也可以凭房间号加入`
  }
  return '凭房间号加入'
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

// 换手（新状态）后清掉选中的牌
watch(() => hand.value?.step, () => {
  if (selectedKey.value && !handTiles.value.some(item => item.key === selectedKey.value)) {
    selectedKey.value = null
  }
  if (!store.isMyTurn) selectedKey.value = null
})

// 在房间里看着一局结束时弹出结算；从记录里打开已结束的房间不弹；房主开了下一局就收起
watch(() => round.value?.phase, (phase, previous) => {
  if (phase === 'FINISHED' && previous && previous !== 'FINISHED') {
    showResult.value = true
  } else if (phase !== 'FINISHED') {
    showResult.value = false
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
  store.leaveRoomView()
})
</script>

<style scoped>
.mj-room {
  min-height: 100%;
  background: var(--bg);
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.page-state {
  padding-top: 96px;
  text-align: center;
}

/* ------------------------------------------------ 等待开局 */

.waiting {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.waiting .m-card + .m-card {
  margin-top: 0;
}

.room-code-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}

.room-label {
  color: var(--ink-3);
  font-size: 12px;
}

.room-code {
  color: var(--ink);
  font-size: 30px;
  letter-spacing: 0.08em;
  line-height: 1.2;
}

.room-meta {
  margin-top: 8px;
  color: var(--ink-2);
  font-size: 13px;
}

.room-hint {
  margin-top: 4px;
  color: var(--ink-3);
  font-size: 12px;
}

.seat-list {
  padding: 4px 0;
}

.seat-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
}

.seat-item + .seat-item {
  border-top: 1px solid var(--rule);
}

.seat-no {
  width: 14px;
  color: var(--ink-3);
  font-size: 13px;
  text-align: center;
}

.seat-placeholder {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px dashed var(--rule-strong);
  border-radius: 50%;
}

.seat-placeholder.bot {
  border: 0;
  background: var(--surface-2);
  color: var(--ink-2);
  font-size: 14px;
}

.seat-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.seat-text strong {
  overflow: hidden;
  color: var(--ink);
  font-size: 15px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.seat-text span {
  color: var(--ink-3);
  font-size: 12px;
}

.waiting-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 6px;
}

/* ------------------------------------------------ 牌桌 */

.table {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 10px 0;
}

.status-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 12px;
  padding: 2px 2px 0;
  color: var(--ink-2);
  font-size: 12px;
}

.wild-info {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.conn {
  color: var(--danger);
}

.conn:empty {
  display: none;
}

.opponents {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 6px 0;
}

.claim-tile {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ink-2);
  font-size: 13px;
}

.center-text {
  color: var(--ink);
  font-size: 14px;
  font-weight: 500;
}

.countdown {
  margin-left: 6px;
  color: var(--accent);
  font-weight: 600;
}

.dock {
  position: sticky;
  bottom: 0;
  z-index: 2;
  margin: 0 -10px;
  padding: 8px 16px 10px;
  border-top: 1px solid var(--rule);
  background: var(--surface);
}

.dock-hint {
  margin-bottom: 6px;
  color: var(--ink-2);
  font-size: 12px;
  text-align: center;
}

.hand-row {
  display: flex;
  flex-wrap: nowrap;
  align-items: flex-end;
  justify-content: center;
  gap: 2px;
  padding-top: 10px;
}

/* 刚摸到的牌和手牌隔开一点 */
.hand-row > .is-drawn {
  margin-left: calc(var(--hand-tile-w) * 0.35);
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 34px;
  margin-top: 10px;
}

.action-row .van-button {
  min-width: 64px;
}

.dock-state {
  color: var(--ink-2);
  font-size: 13px;
}

/* ------------------------------------------------ 规则 */

.rules {
  padding: 4px 18px calc(20px + env(safe-area-inset-bottom));
  color: var(--ink-2);
  font-size: 13px;
  line-height: 1.7;
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
</style>
