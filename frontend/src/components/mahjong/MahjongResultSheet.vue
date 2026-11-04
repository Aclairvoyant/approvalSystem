<template>
  <div class="result-sheet">
    <div class="m-sheet-title">{{ title }}</div>
    <p class="result-sub">{{ subtitle }}</p>

    <section v-for="win in result.wins" :key="win.seat" class="win-block">
      <div class="win-head">
        <strong>{{ seatName(win.seat) }}</strong>
        <span>{{ winTypeText(win) }}</span>
        <span class="win-gain tabular">+{{ gainOf(win) }}</span>
      </div>
      <div v-if="handOf(win.seat).length" class="win-hand">
        <MahjongTile
          v-for="(tile, i) in handOf(win.seat)"
          :key="i"
          :tile="tile"
          size="sm"
          :wild="tile === wild"
        />
        <template v-if="!win.selfDraw">
          <span class="win-gap" aria-hidden="true"></span>
          <MahjongTile :tile="win.winningTile" size="sm" latest />
        </template>
      </div>
      <dl class="breakdown">
        <div>
          <dt>花</dt>
          <dd>{{ itemsText(win.flowerItems) }} = <b class="tabular">{{ win.flowers }}</b> 花</dd>
        </div>
        <div>
          <dt>番</dt>
          <dd>
            {{ win.fanItems.length ? itemsText(win.fanItems) : '无' }} = <b class="tabular">{{ win.fan }}</b> 番
          </dd>
        </div>
        <div>
          <dt>分</dt>
          <dd>
            {{ win.flowers }} × 2<sup>{{ win.fan }}</sup> = {{ win.multiple }}<template v-if="win.payEach < win.multiple">，封顶 {{ win.payEach }}</template>；{{ payText(win) }}
          </dd>
        </div>
      </dl>
    </section>

    <table class="score-table">
      <thead>
        <tr>
          <th scope="col">玩家</th>
          <th scope="col">花</th>
          <th scope="col">番</th>
          <th scope="col">本局</th>
          <th scope="col">总分</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(delta, seat) in result.deltas" :key="seat" :class="{ me: seat === mySeat }">
          <th scope="row">{{ seatName(seat) }}</th>
          <td class="tabular">{{ flowersOf(seat) }}</td>
          <td class="tabular">{{ fanOf(seat) }}</td>
          <td class="tabular" :class="{ up: delta > 0 }">{{ signed(delta) }}</td>
          <td class="tabular">{{ signed(totals[seat] ?? 0) }}</td>
        </tr>
      </tbody>
    </table>

    <div class="m-sheet-footer">
      <van-button plain @click="emit('back')">返回大厅</van-button>
      <van-button v-if="isOwner" type="primary" :loading="busy" @click="emit('next')">再来一局</van-button>
      <van-button v-else disabled>等房主开下一局</van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MahjongTile from './MahjongTile.vue'
import type { MahjongPublicRound, MahjongRoundResult, MahjongScoreItem, MahjongWin } from '@/services/mahjongApi'

const props = withDefaults(defineProps<{
  result: MahjongRoundResult
  round: MahjongPublicRound
  totals: number[]
  seatName: (seat: number) => string
  mySeat?: number | null
  isOwner?: boolean
  busy?: boolean
}>(), {
  mySeat: null,
  isOwner: false,
  busy: false
})

const emit = defineEmits<{
  (event: 'next'): void
  (event: 'back'): void
}>()

const wild = computed(() => props.round.wild ?? null)

const title = computed(() => {
  const result = props.result
  if (result.drawGame) return '荒庄'
  if (result.wins.length > 1) return '一炮多响'
  const win = result.wins[0]
  if (!win) return '本局结束'
  return `${props.seatName(win.seat)}${win.selfDraw ? '自摸' : '胡了'}`
})

const subtitle = computed(() => {
  const result = props.result
  const roundText = `第 ${props.round.roundNo} 局`
  if (result.drawGame) return `${roundText} · 牌摸完了没人胡，本局不计分，下一局加 1 荒番`
  if (!result.selfDraw && result.discarder !== null && result.discarder !== undefined) {
    return `${roundText} · ${props.seatName(result.discarder)}点炮`
  }
  return roundText
})

function winTypeText(win: MahjongWin): string {
  if (win.fourWild) return '四百搭'
  return win.selfDraw ? '自摸' : '接炮'
}

function handOf(seat: number): number[] {
  return props.round.seats[seat]?.hand ?? []
}

/** 「花牌 ×4」这类按张数计的项目，分值和张数一样时不再重复写数字 */
function itemsText(items: MahjongScoreItem[]): string {
  return items.map(item => {
    const count = item.name.match(/×(\d+)$/)
    return count && Number(count[1]) === item.value ? item.name : `${item.name} ${item.value}`
  }).join(' + ')
}

function gainOf(win: MahjongWin): number {
  return win.payEach * win.payers.length
}

function payText(win: MahjongWin): string {
  if (win.selfDraw) {
    return win.payers.length > 1 ? `其余 ${win.payers.length} 家各付 ${win.payEach}` : `对家付 ${win.payEach}`
  }
  return `${props.seatName(win.payers[0])}付 ${win.payEach}`
}

function winOf(seat: number): MahjongWin | undefined {
  return props.result.wins.find(win => win.seat === seat)
}

function flowersOf(seat: number): number {
  return winOf(seat)?.flowers ?? props.result.seatFlowers[seat] ?? 0
}

function fanOf(seat: number): string {
  const win = winOf(seat)
  return win ? String(win.fan) : '—'
}

function signed(value: number): string {
  return value > 0 ? `+${value}` : String(value)
}
</script>

<style scoped>
.result-sheet {
  padding: 4px 16px calc(16px + env(safe-area-inset-bottom));
}

.result-sheet .m-sheet-footer {
  padding: 16px 0 0;
}

.result-sub {
  color: var(--ink-2);
  font-size: 13px;
  text-align: center;
}

.win-block {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--rule);
}

.win-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  color: var(--ink-2);
  font-size: 13px;
}

.win-head strong {
  color: var(--ink);
  font-size: 15px;
}

.win-gain {
  margin-left: auto;
  color: var(--accent);
  font-size: 16px;
  font-weight: 600;
}

.win-hand {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 2px;
  margin-top: 10px;
}

.win-gap {
  width: 6px;
}

.breakdown {
  margin: 10px 0 0;
}

.breakdown div {
  display: flex;
  gap: 10px;
  padding: 3px 0;
  font-size: 13px;
  line-height: 1.5;
}

.breakdown dt {
  flex-shrink: 0;
  width: 16px;
  color: var(--ink-3);
}

.breakdown dd {
  margin: 0;
  color: var(--ink-2);
}

.breakdown b {
  color: var(--ink);
}

.score-table {
  width: 100%;
  margin-top: 16px;
  border-collapse: collapse;
  font-size: 13px;
}

.score-table th,
.score-table td {
  padding: 8px 4px;
  border-bottom: 1px solid var(--rule);
  text-align: right;
}

.score-table th:first-child {
  max-width: 96px;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score-table thead th {
  color: var(--ink-3);
  font-weight: 400;
}

.score-table tbody th {
  color: var(--ink);
  font-weight: 400;
}

.score-table tr.me th,
.score-table tr.me td {
  font-weight: 600;
}

.score-table td.up {
  color: var(--accent);
}
</style>
