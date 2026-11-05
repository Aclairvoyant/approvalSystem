<template>
  <div class="compass" role="status" :aria-label="ariaLabel">
    <span
      v-for="item in winds"
      :key="item.side"
      class="wind"
      :class="[`at-${item.side}`, { active: item.active }]"
    >{{ item.label }}</span>
    <span v-for="item in activeWinds" :key="`bar-${item.side}`" class="turn-bar" :class="`at-${item.side}`" />

    <div class="core">
      <svg v-if="countdown !== null" class="ring" viewBox="0 0 44 44" aria-hidden="true">
        <circle class="ring-track" cx="22" cy="22" r="19" />
        <circle class="ring-progress" cx="22" cy="22" r="19" :style="{ strokeDashoffset: ringOffset }" />
      </svg>
      <strong v-if="countdown !== null" class="seconds tabular" :class="{ urgent: countdown <= 5 }">{{ countdown }}</strong>
      <span v-else class="wall tabular">{{ wallCount }}</span>
      <span class="core-label">{{ countdown !== null ? `剩 ${wallCount}` : '剩余' }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { TableSide } from '@/services/mahjongApi'

const props = withDefaults(defineProps<{
  winds: Array<{ side: TableSide; label: string; active: boolean }>
  wallCount: number
  /** 剩余秒数；不需要倒计时（电脑、托管）时为 null */
  countdown?: number | null
  /** 本轮总限时（秒），用来画进度环 */
  limit?: number
}>(), {
  countdown: null,
  limit: 20
})

const CIRCUMFERENCE = 2 * Math.PI * 19

const activeWinds = computed(() => props.winds.filter(item => item.active))
const ringOffset = computed(() => {
  if (props.countdown === null) return 0
  const ratio = Math.max(0, Math.min(1, props.countdown / props.limit))
  return CIRCUMFERENCE * (1 - ratio)
})
const ariaLabel = computed(() => {
  const active = activeWinds.value.map(item => item.label).join('、')
  const parts = [`剩余 ${props.wallCount} 张`]
  if (active) parts.push(`${active}家行动`)
  if (props.countdown !== null) parts.push(`还剩 ${props.countdown} 秒`)
  return parts.join('，')
})
</script>

<style scoped>
.compass {
  position: relative;
  width: 92px;
  height: 92px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 18px;
  background:
    radial-gradient(circle at 50% 50%, rgba(255, 255, 255, 0.06) 0%, transparent 70%),
    rgba(5, 24, 17, 0.78);
  box-shadow: inset 0 0 0 4px rgba(255, 255, 255, 0.03), 0 6px 18px rgba(0, 0, 0, 0.35);
}

.wind {
  position: absolute;
  color: rgba(255, 255, 255, 0.45);
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
}

.wind.active {
  color: #f6c453;
  text-shadow: 0 0 8px rgba(246, 196, 83, 0.8);
}

.wind.at-bottom {
  bottom: 6px;
  left: 50%;
  transform: translateX(-50%);
}

.wind.at-top {
  top: 6px;
  left: 50%;
  transform: translateX(-50%) rotate(180deg);
}

.wind.at-left {
  top: 50%;
  left: 6px;
  transform: translateY(-50%) rotate(90deg);
}

.wind.at-right {
  top: 50%;
  right: 6px;
  transform: translateY(-50%) rotate(270deg);
}

/* 当前行动那一边的亮条 */
.turn-bar {
  position: absolute;
  border-radius: 3px;
  background: #f6c453;
  box-shadow: 0 0 10px rgba(246, 196, 83, 0.9);
  animation: bar-pulse 1.2s ease-in-out infinite;
}

.turn-bar.at-bottom,
.turn-bar.at-top {
  left: 22px;
  right: 22px;
  height: 3px;
}

.turn-bar.at-bottom {
  bottom: 1px;
}

.turn-bar.at-top {
  top: 1px;
}

.turn-bar.at-left,
.turn-bar.at-right {
  top: 22px;
  bottom: 22px;
  width: 3px;
}

.turn-bar.at-left {
  left: 1px;
}

.turn-bar.at-right {
  right: 1px;
}

@keyframes bar-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}

.core {
  position: absolute;
  inset: 22px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.ring {
  position: absolute;
  inset: -2px;
  width: calc(100% + 4px);
  height: calc(100% + 4px);
  transform: rotate(-90deg);
}

.ring-track,
.ring-progress {
  fill: none;
  stroke-width: 2.5;
}

.ring-track {
  stroke: rgba(255, 255, 255, 0.12);
}

.ring-progress {
  stroke: #f6c453;
  stroke-dasharray: 119.38;
  stroke-linecap: round;
  transition: stroke-dashoffset 0.5s linear;
}

.seconds {
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  line-height: 1;
}

.seconds.urgent {
  color: #ff7a59;
  animation: urgent 0.5s ease-in-out infinite alternate;
}

@keyframes urgent {
  to {
    transform: scale(1.15);
  }
}

.wall {
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
}

.core-label {
  margin-top: 3px;
  color: rgba(255, 255, 255, 0.55);
  font-size: 9px;
  line-height: 1;
}

@media (prefers-reduced-motion: reduce) {
  .turn-bar,
  .seconds.urgent {
    animation: none;
  }
}
</style>
