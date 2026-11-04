<template>
  <section class="seat-panel" :class="{ 'is-active': active, 'is-self': self }">
    <header class="seat-head">
      <UserAvatar v-if="info?.type === 'HUMAN'" :src="info?.avatar" :name="info?.name" :size="28" />
      <span v-else class="bot-avatar" aria-hidden="true">电</span>
      <div class="seat-id">
        <strong>{{ info?.name || `${seat.seat + 1} 号位` }}</strong>
        <span class="seat-sub">
          {{ relation }}<template v-if="dealer"> · 庄</template><template v-if="seat.qiao"> · 已敲</template><template v-if="seat.autoPlay"> · 托管</template>
        </span>
      </div>
      <span v-if="flash" :key="flash.key" class="seat-flash">{{ flash.text }}</span>
      <span v-else-if="active" class="seat-turn">{{ waitingClaims ? '等人选择' : '出牌中' }}</span>
      <span class="seat-score tabular" :class="{ negative: score < 0 }">{{ scoreText }}</span>
    </header>

    <div v-if="!self || seat.melds.length || seat.flowers.length" class="seat-row">
      <span v-if="!self && !revealedHand" class="hand-count tabular">
        手牌 {{ seat.handCount }}<template v-if="seat.holdingDrawn"> · 刚摸牌</template>
      </span>
      <div v-for="(meld, index) in seat.melds" :key="`meld-${index}`" class="meld" :aria-label="meldLabel(meld)">
        <template v-if="meld.tiles && meld.tiles.length">
          <MahjongTile v-for="(tile, i) in meld.tiles" :key="i" :tile="tile" :size="tileSize" />
        </template>
        <template v-else>
          <MahjongTile v-for="i in 4" :key="i" :size="tileSize" />
        </template>
      </div>
      <div v-if="seat.flowers.length" class="flowers" :aria-label="`花牌 ${seat.flowers.length} 张`">
        <span class="flower-count">花 {{ seat.flowers.length }}</span>
        <MahjongTile v-for="(tile, i) in seat.flowers" :key="`f-${i}`" :tile="tile" size="xs" />
      </div>
    </div>

    <div v-if="revealedHand" class="revealed" aria-label="亮出的手牌">
      <MahjongTile
        v-for="(tile, i) in revealedHand"
        :key="`h-${i}`"
        :tile="tile"
        size="sm"
        :wild="tile === wild"
      />
    </div>

    <div class="river" :class="{ empty: !seat.discards.length }" aria-label="牌河">
      <MahjongTile
        v-for="(tile, i) in seat.discards"
        :key="`d-${i}`"
        :tile="tile"
        size="xs"
        :latest="latestDiscard && i === seat.discards.length - 1"
      />
      <span v-if="!seat.discards.length" class="river-empty">还没出牌</span>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import UserAvatar from '@/components/UserAvatar.vue'
import MahjongTile from './MahjongTile.vue'
import { tileName, type MahjongMeld, type MahjongPublicSeat, type MahjongSeat } from '@/services/mahjongApi'
import type { SeatFlash } from '@/store/modules/mahjong'

const props = withDefaults(defineProps<{
  seat: MahjongPublicSeat
  info?: MahjongSeat | null
  /** 你 / 下家 / 对家 / 上家 */
  relation: string
  dealer?: boolean
  active?: boolean
  waitingClaims?: boolean
  flash?: SeatFlash | null
  wild?: number | null
  latestDiscard?: boolean
  self?: boolean
}>(), {
  info: null,
  dealer: false,
  active: false,
  waitingClaims: false,
  flash: null,
  wild: null,
  latestDiscard: false,
  self: false
})

const MELD_NAMES: Record<string, string> = {
  CHI: '吃',
  PONG: '碰',
  MING_GANG: '明杠',
  JIA_GANG: '加杠',
  AN_GANG: '暗杠'
}

const tileSize = computed(() => (props.self ? 'sm' : 'xs'))
const score = computed(() => props.info?.totalScore ?? 0)
const scoreText = computed(() => (score.value > 0 ? `+${score.value}` : String(score.value)))
const revealedHand = computed(() => (props.seat.hand && props.seat.hand.length ? props.seat.hand : null))

function meldLabel(meld: MahjongMeld): string {
  const name = MELD_NAMES[meld.type] ?? ''
  if (!meld.tiles || !meld.tiles.length) return name
  return `${name} ${tileName(meld.tiles[0])}`
}
</script>

<style scoped>
.seat-panel {
  padding: 10px 12px;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  background: var(--surface);
}

.seat-panel.is-active {
  border-color: var(--accent);
}

.seat-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bot-avatar {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--surface-2);
  color: var(--ink-2);
  font-size: 12px;
}

.seat-id {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.seat-id strong {
  overflow: hidden;
  color: var(--ink);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.seat-sub {
  color: var(--ink-3);
  font-size: 12px;
}

.seat-turn,
.seat-flash {
  flex-shrink: 0;
  color: var(--accent);
  font-size: 12px;
  font-weight: 600;
}

.seat-flash {
  font-size: 15px;
  animation: seat-flash 1.8s ease-out both;
}

@keyframes seat-flash {
  0% {
    opacity: 0;
    transform: scale(0.8);
  }
  12% {
    opacity: 1;
    transform: scale(1.12);
  }
  24%,
  80% {
    opacity: 1;
    transform: scale(1);
  }
  100% {
    opacity: 0;
  }
}

.seat-score {
  flex-shrink: 0;
  min-width: 36px;
  color: var(--ink);
  font-size: 14px;
  font-weight: 600;
  text-align: right;
}

.seat-score.negative {
  color: var(--ink-2);
}

.seat-row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 6px 10px;
  margin-top: 8px;
}

.hand-count {
  align-self: center;
  color: var(--ink-2);
  font-size: 12px;
}

.meld,
.flowers {
  display: inline-flex;
  align-items: flex-end;
  gap: 1px;
}

.flower-count {
  align-self: center;
  margin-right: 3px;
  color: var(--ink-2);
  font-size: 12px;
}

.revealed {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  margin-top: 8px;
}

.river {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  min-height: 25px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--rule);
}

.river-empty {
  color: var(--ink-3);
  font-size: 12px;
  line-height: 23px;
}

@media (prefers-reduced-motion: reduce) {
  .seat-flash {
    animation: none;
  }
}
</style>
