<template>
  <div class="river" :class="[`side-${side}`, { wide }]" :style="{ '--per-line': perLine }" aria-label="牌河">
    <MahjongTile
      v-for="(tile, index) in tiles"
      :key="index"
      :tile="tile"
      size="river"
      :rotate="SIDE_ROTATION[side]"
      :wild="tile === wild"
      :latest="highlightLast && index === tiles.length - 1"
      :class="{ 'is-new': animateLast && index === tiles.length - 1 }"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MahjongTile from './MahjongTile.vue'
import { SIDE_ROTATION, type TableSide } from '@/services/mahjongApi'

const props = withDefaults(defineProps<{
  tiles: number[]
  side: TableSide
  wild?: number | null
  highlightLast?: boolean
  /** 刚有人出牌时播放最后一张落下的动画（进房间时不播） */
  animateLast?: boolean
  /** 两人桌左右没人，牌河可以排得更宽 */
  wide?: boolean
  /** 一行（左右两家是一列）放几张；不传按竖屏默认值 */
  perLine?: number | null
}>(), {
  wild: null,
  highlightLast: false,
  animateLast: false,
  wide: false,
  perLine: null
})

/** 上下两家一行 8 张；左右两家一列 10 张（竖着放得下，横向不会挤到座位上）；两人桌一行 10 张 */
const perLine = computed(() => {
  if (props.perLine) return props.perLine
  if (props.side === 'left' || props.side === 'right') return 10
  return props.wide ? 10 : 8
})
</script>

<style scoped>
/*
 * 牌河从桌子中间往出牌人那一边长：
 * 下家（自己）一行行往下，上方那家一行行往上，左右两家一列列往外。
 */
.river {
  --slot-w: var(--mj-river-w, 17px);
  gap: 1px;
}

.side-bottom,
.side-top {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  max-width: calc(var(--per-line) * (var(--slot-w) + 1px));
}

.side-top {
  flex-wrap: wrap-reverse;
}

.side-left,
.side-right {
  display: grid;
  grid-auto-flow: column;
  grid-template-rows: repeat(var(--per-line), auto);
  align-content: center;
}

/* 左边那家：第一列贴着桌子中间（右侧），往左长 */
.side-left {
  direction: rtl;
}

/* 新打出的牌从出牌人那边落到牌河里 */
.is-new {
  animation: tile-drop 0.36s cubic-bezier(0.2, 0.9, 0.3, 1.2) both;
}

.side-bottom {
  --from-x: 0;
  --from-y: 46px;
}

.side-top {
  --from-x: 0;
  --from-y: -46px;
}

.side-left {
  --from-x: -46px;
  --from-y: 0;
}

.side-right {
  --from-x: 46px;
  --from-y: 0;
}

@keyframes tile-drop {
  0% {
    opacity: 0;
    transform: translate(var(--from-x), var(--from-y)) scale(1.8);
  }
  70% {
    opacity: 1;
  }
  100% {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .is-new {
    animation: none;
  }
}
</style>
