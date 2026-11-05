<template>
  <div class="table-seat" :class="[`side-${side}`, { 'is-active': active, landscape }]">
    <MahjongSeatPlate
      :info="info"
      :wind="wind"
      :dealer="dealer"
      :active="active"
      :qiao="seat.qiao"
      :auto-play="seat.autoPlay"
      :flowers="seat.flowers.length"
      :compact="side !== 'top'"
      :large="large"
      @inspect="emit('inspect')"
    />

    <div class="seat-tiles" :class="{ revealed: !!revealedHand }">
      <div v-if="seat.melds.length" class="melds">
        <span v-for="(meld, index) in seat.melds" :key="`meld-${index}`" class="meld" :aria-label="meldLabel(meld)">
          <template v-if="meld.tiles && meld.tiles.length">
            <MahjongTile
              v-for="(tile, i) in meld.tiles"
              :key="i"
              :tile="tile"
              size="seat"
              :rotate="rotation"
            />
          </template>
          <template v-else>
            <MahjongTile v-for="i in 4" :key="i" size="seat" :rotate="rotation" />
          </template>
        </span>
      </div>

      <div v-if="revealedHand" class="hand is-faceup" aria-label="亮出的手牌">
        <MahjongTile
          v-for="(tile, i) in revealedHand"
          :key="`r-${i}`"
          :tile="tile"
          size="seat"
          :rotate="rotation"
          :wild="tile === wild"
          class="flip-in"
          :style="{ '--i': i }"
        />
      </div>
      <div v-else class="hand" :aria-label="`手牌 ${seat.handCount} 张`">
        <MahjongTile
          v-for="i in seat.handCount"
          :key="`${dealKey}-${i}`"
          size="back"
          stand
          :rotate="rotation"
          class="deal-in"
          :class="{ 'is-drawn': seat.holdingDrawn && i === seat.handCount }"
          :style="{ '--i': i }"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MahjongTile from './MahjongTile.vue'
import MahjongSeatPlate from './MahjongSeatPlate.vue'
import {
  SIDE_ROTATION,
  tileName,
  type MahjongMeld,
  type MahjongPublicSeat,
  type MahjongSeat,
  type TableSide
} from '@/services/mahjongApi'

const props = withDefaults(defineProps<{
  side: Exclude<TableSide, 'bottom'>
  seat: MahjongPublicSeat
  info?: MahjongSeat | null
  wind?: string
  dealer?: boolean
  active?: boolean
  wild?: number | null
  /** 每局变化，让发牌动画重新播放 */
  dealKey?: string | number
  /** 手机横屏：名牌放在牌的旁边，省出高度 */
  landscape?: boolean
  /** 平板：名牌放大 */
  large?: boolean
}>(), {
  info: null,
  wind: '',
  dealer: false,
  active: false,
  wild: null,
  dealKey: 0,
  landscape: false,
  large: false
})

const emit = defineEmits<{
  (event: 'inspect'): void
}>()

const MELD_NAMES: Record<string, string> = {
  CHI: '吃',
  PONG: '碰',
  MING_GANG: '明杠',
  JIA_GANG: '加杠',
  AN_GANG: '暗杠'
}

const rotation = computed(() => SIDE_ROTATION[props.side])
const revealedHand = computed(() => (props.seat.hand && props.seat.hand.length ? props.seat.hand : null))

function meldLabel(meld: MahjongMeld): string {
  const name = MELD_NAMES[meld.type] ?? ''
  if (!meld.tiles || !meld.tiles.length) return name
  return `${name} ${tileName(meld.tiles[0])}`
}
</script>

<style scoped>
.table-seat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

/* 左右两家的立牌横着放，用单独的尺寸 */
.side-left,
.side-right {
  --mj-back-w: var(--mj-side-back-w, 20px);
}

/* 横屏：高度紧，名牌放到牌的外侧 */
.landscape.side-top,
.landscape.side-left {
  flex-direction: row;
}

.landscape.side-right {
  flex-direction: row-reverse;
}

/* 横屏左右两家：副露一列、暗牌一列，并排放，不往上下顶到顶栏和手牌 */
.landscape.side-left .seat-tiles {
  flex-direction: row;
  align-items: center;
}

.landscape.side-right .seat-tiles {
  flex-direction: row-reverse;
  align-items: center;
}

.seat-tiles {
  display: flex;
  align-items: center;
  gap: 6px;
}

.side-top .seat-tiles {
  flex-direction: row;
}

.side-left .seat-tiles,
.side-right .seat-tiles {
  flex-direction: column;
}

.melds {
  display: flex;
  gap: 3px;
}

.side-left .melds,
.side-right .melds {
  flex-direction: column;
}

.meld {
  display: inline-flex;
  gap: 0;
}

.side-left .meld,
.side-right .meld {
  flex-direction: column;
}

.hand {
  display: flex;
  align-items: flex-end;
}

.side-left .hand,
.side-right .hand {
  flex-direction: column;
  align-items: center;
}

/* 立着的暗牌挨得紧一点，看起来是一排 */
.side-top .hand > .mj-slot + .mj-slot {
  margin-left: -1px;
}

.side-left .hand > .mj-slot + .mj-slot,
.side-right .hand > .mj-slot + .mj-slot {
  margin-top: calc(var(--mj-side-back-w, 20px) * -0.25);
}

.side-top .hand > .is-drawn {
  margin-left: 5px;
}

.side-left .hand > .is-drawn,
.side-right .hand > .is-drawn {
  margin-top: 3px;
}

.hand.is-faceup {
  gap: 0;
}

/* 发牌：一张张落到桌上 */
.deal-in {
  animation: deal-in 0.3s ease-out both;
  animation-delay: calc(var(--i, 0) * 25ms);
}

@keyframes deal-in {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* 胡家亮牌：翻开 */
.flip-in {
  animation: flip-in 0.42s ease-out both;
  animation-delay: calc(var(--i, 0) * 40ms);
}

@keyframes flip-in {
  from {
    opacity: 0;
    transform: rotateY(90deg);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .deal-in,
  .flip-in {
    animation: none;
  }
}
</style>
