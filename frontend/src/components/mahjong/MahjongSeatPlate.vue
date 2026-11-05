<template>
  <button
    type="button"
    class="seat-plate"
    :class="{ 'is-active': active, compact, large, empty: !info || info.type === 'EMPTY' }"
    :aria-label="ariaLabel"
    @click="emit('inspect')"
  >
    <span class="avatar-ring">
      <UserAvatar v-if="info?.type === 'HUMAN'" :src="info.avatar" :name="info.name" :size="avatarSize" />
      <span v-else-if="info?.type === 'BOT'" class="bot-avatar" aria-hidden="true">电</span>
      <span v-else class="empty-avatar" aria-hidden="true">+</span>
      <span v-if="dealer" class="dealer-badge">庄</span>
      <span v-if="wind" class="wind-badge">{{ wind }}</span>
    </span>
    <span class="plate-text">
      <span class="plate-name">{{ displayName }}</span>
      <span v-if="showScore" class="plate-score tabular" :class="{ negative: score < 0 }">{{ scoreText }}</span>
    </span>
    <span v-if="qiao || autoPlay || flowers" class="plate-tags">
      <span v-if="qiao" class="tag tag-qiao">敲</span>
      <span v-if="autoPlay" class="tag">托管</span>
      <span v-if="flowers" class="tag tag-flower">花{{ flowers }}</span>
    </span>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import UserAvatar from '@/components/UserAvatar.vue'
import type { MahjongSeat } from '@/services/mahjongApi'

const props = withDefaults(defineProps<{
  info?: MahjongSeat | null
  wind?: string
  dealer?: boolean
  active?: boolean
  qiao?: boolean
  autoPlay?: boolean
  flowers?: number
  /** 左右两家竖着排，名字和分数上下放 */
  compact?: boolean
  /** 平板上放大 */
  large?: boolean
  showScore?: boolean
  /** 名字覆盖（比如自己显示「你」） */
  name?: string
}>(), {
  info: null,
  wind: '',
  dealer: false,
  active: false,
  qiao: false,
  autoPlay: false,
  flowers: 0,
  compact: false,
  large: false,
  showScore: true,
  name: ''
})

const emit = defineEmits<{
  (event: 'inspect'): void
}>()

const displayName = computed(() => {
  if (props.name) return props.name
  if (!props.info || props.info.type === 'EMPTY') return '空位'
  return props.info.name || '玩家'
})
const avatarSize = computed(() => (props.large ? 44 : props.compact ? 32 : 34))
const score = computed(() => props.info?.totalScore ?? 0)
const scoreText = computed(() => (score.value > 0 ? `+${score.value}` : String(score.value)))
const ariaLabel = computed(() => {
  const parts = [displayName.value]
  if (props.dealer) parts.push('庄家')
  if (props.showScore) parts.push(`总分 ${scoreText.value}`)
  if (props.flowers) parts.push(`花 ${props.flowers}`)
  return parts.join('，')
})
</script>

<style scoped>
.seat-plate {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 150px;
  padding: 3px 8px 3px 3px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.32);
  color: #fff;
  font-family: var(--font-sans);
  text-align: left;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.seat-plate.compact {
  flex-direction: column;
  gap: 2px;
  max-width: 64px;
  padding: 4px 4px 5px;
  border-radius: 12px;
  text-align: center;
}

.avatar-ring {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;
  border-radius: 50%;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.2);
}

.is-active .avatar-ring {
  animation: turn-ring 1.2s ease-in-out infinite;
}

@keyframes turn-ring {
  0%,
  100% {
    box-shadow: 0 0 0 2px #f6c453, 0 0 0 4px rgba(246, 196, 83, 0.25);
  }
  50% {
    box-shadow: 0 0 0 2px #f6c453, 0 0 0 7px rgba(246, 196, 83, 0);
  }
}

.bot-avatar,
.empty-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
  color: rgba(255, 255, 255, 0.85);
  font-size: 13px;
}

.empty-avatar {
  border: 1px dashed rgba(255, 255, 255, 0.4);
  background: transparent;
  font-size: 18px;
}

.dealer-badge,
.wind-badge {
  position: absolute;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 15px;
  height: 15px;
  border-radius: 50%;
  font-size: 9px;
  font-weight: 700;
  line-height: 1;
}

.dealer-badge {
  top: -4px;
  right: -5px;
  background: #d94a2b;
  color: #fff;
}

.wind-badge {
  bottom: -4px;
  left: -5px;
  background: #f4efe0;
  color: #1f4c3b;
}

.plate-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.plate-name {
  max-width: 76px;
  overflow: hidden;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compact .plate-name {
  max-width: 56px;
}

.plate-score {
  color: #f6c453;
  font-size: 12px;
  font-weight: 600;
}

.plate-score.negative {
  color: rgba(255, 255, 255, 0.7);
}

.plate-tags {
  display: inline-flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 2px;
}

.tag {
  padding: 1px 4px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.16);
  color: rgba(255, 255, 255, 0.92);
  font-size: 10px;
  line-height: 1.4;
}

.tag-qiao {
  background: #7c5cff;
  color: #fff;
}

.tag-flower {
  background: rgba(246, 196, 83, 0.22);
  color: #f6d48a;
}

.empty .plate-name {
  color: rgba(255, 255, 255, 0.6);
}

/* 平板 */
.seat-plate.large {
  gap: 8px;
  max-width: 200px;
  padding: 4px 12px 4px 4px;
}

.seat-plate.large.compact {
  gap: 3px;
  max-width: 84px;
  padding: 6px 6px 7px;
}

.large .bot-avatar,
.large .empty-avatar {
  width: 44px;
  height: 44px;
  font-size: 16px;
}

.large .plate-name {
  max-width: 110px;
  font-size: 15px;
}

.large.compact .plate-name {
  max-width: 74px;
}

.large .plate-score {
  font-size: 15px;
}

.large .tag {
  font-size: 12px;
}

.large .dealer-badge,
.large .wind-badge {
  width: 19px;
  height: 19px;
  font-size: 11px;
}

@media (prefers-reduced-motion: reduce) {
  .is-active .avatar-ring {
    animation: none;
    box-shadow: 0 0 0 2px #f6c453;
  }
}
</style>
