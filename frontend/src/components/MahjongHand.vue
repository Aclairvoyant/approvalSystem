<template>
  <div class="mj-hand" :class="[positionClass, { 'is-my-turn': isMyTurn }]">
    <!-- 手牌区域 -->
    <div class="hand-tiles">
      <template v-if="isMe">
        <!-- 自己的手牌（可见） -->
        <MahjongTile
          v-for="(tile, index) in tiles"
          :key="`hand-${index}`"
          :code="tile"
          :selected="selectedTile === tile"
          :disabled="!canSelect"
          @click="selectTile(tile, index)"
        />
      </template>
      <template v-else>
        <!-- 其他玩家的手牌（背面） -->
        <MahjongTile
          v-for="index in handCount"
          :key="`back-${index}`"
          :is-back="true"
          :size="position === 'top' ? 'small' : 'normal'"
        />
      </template>
    </div>

    <!-- 明牌区域（碰/杠） -->
    <div v-if="melds.length > 0" class="melds-area">
      <div v-for="(meld, meldIndex) in melds" :key="`meld-${meldIndex}`" class="meld-group">
        <MahjongTile
          v-for="(tile, tileIndex) in meld.tiles"
          :key="`meld-${meldIndex}-${tileIndex}`"
          :code="tile"
          :horizontal="meld.type !== 'AN_KONG' || tileIndex === 1 || tileIndex === 2"
          :is-back="meld.concealed && (tileIndex === 0 || tileIndex === 3)"
          :size="position !== 'bottom' ? 'small' : 'normal'"
        />
      </div>
    </div>

    <!-- 花牌区域 -->
    <div v-if="flowers.length > 0" class="flowers-area">
      <MahjongTile
        v-for="(flower, index) in flowers"
        :key="`flower-${index}`"
        :code="flower"
        size="small"
      />
    </div>

    <!-- 玩家信息 -->
    <div class="player-info">
      <div class="player-avatar">
        <img v-if="avatar" :src="avatar" :alt="name" />
        <span v-else class="avatar-placeholder">{{ name?.charAt(0) || '?' }}</span>
      </div>
      <div class="player-details">
        <span class="player-name">{{ name || '等待中...' }}</span>
        <span class="player-score" :class="{ positive: score > 0, negative: score < 0 }">
          {{ score >= 0 ? '+' : '' }}{{ score }}
        </span>
      </div>
      <div v-if="isDealer" class="dealer-badge">庄</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import MahjongTile from './MahjongTile.vue'

interface Meld {
  type: 'PONG' | 'MING_KONG' | 'AN_KONG' | 'BU_KONG'
  tiles: string[]
  concealed?: boolean
}

interface Props {
  position: 'bottom' | 'right' | 'top' | 'left' // 玩家位置
  tiles?: string[]           // 手牌（仅自己可见）
  handCount?: number         // 手牌数量（其他玩家显示背面）
  melds?: Meld[]            // 明牌
  flowers?: string[]         // 花牌
  name?: string             // 玩家名
  avatar?: string           // 头像
  score?: number            // 分数
  isMe?: boolean            // 是否是自己
  isMyTurn?: boolean        // 是否轮到操作
  isDealer?: boolean        // 是否是庄家
  canSelect?: boolean       // 是否可以选牌
}

const props = withDefaults(defineProps<Props>(), {
  tiles: () => [],
  handCount: 0,
  melds: () => [],
  flowers: () => [],
  name: '',
  avatar: '',
  score: 0,
  isMe: false,
  isMyTurn: false,
  isDealer: false,
  canSelect: true
})

const emit = defineEmits<{
  selectTile: [tile: string, index: number]
}>()

const selectedTile = ref<string | null>(null)

const positionClass = computed(() => `position-${props.position}`)

function selectTile(tile: string, index: number) {
  if (!props.canSelect) return

  if (selectedTile.value === tile) {
    selectedTile.value = null
  } else {
    selectedTile.value = tile
    emit('selectTile', tile, index)
  }
}

// 暴露方法供父组件调用
defineExpose({
  getSelectedTile: () => selectedTile.value,
  clearSelection: () => { selectedTile.value = null }
})
</script>

<style scoped>
.mj-hand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 8px;
}

/* 底部（自己）的位置 */
.position-bottom {
  flex-direction: column;
}

.position-bottom .hand-tiles {
  display: flex;
  flex-direction: row;
  gap: 2px;
  justify-content: center;
}

/* 顶部（对面玩家）的位置 */
.position-top {
  flex-direction: column-reverse;
}

.position-top .hand-tiles {
  display: flex;
  flex-direction: row;
  gap: 1px;
  justify-content: center;
}

/* 左边玩家的位置 */
.position-left {
  flex-direction: row-reverse;
}

.position-left .hand-tiles {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.position-left .hand-tiles :deep(.mj-tile) {
  transform: rotate(90deg);
}

/* 右边玩家的位置 */
.position-right {
  flex-direction: row;
}

.position-right .hand-tiles {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.position-right .hand-tiles :deep(.mj-tile) {
  transform: rotate(-90deg);
}

/* 轮到操作时的提示 */
.is-my-turn {
  position: relative;
}

.is-my-turn::after {
  content: '';
  position: absolute;
  top: -4px;
  left: 50%;
  transform: translateX(-50%);
  width: 0;
  height: 0;
  border-left: 8px solid transparent;
  border-right: 8px solid transparent;
  border-top: 8px solid #ff4757;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* 明牌区域 */
.melds-area {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.meld-group {
  display: flex;
  gap: 1px;
}

/* 花牌区域 */
.flowers-area {
  display: flex;
  gap: 2px;
  padding: 4px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
}

/* 玩家信息 */
.player-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  background: rgba(0, 0, 0, 0.3);
  border-radius: 8px;
}

.player-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  overflow: hidden;
  background: #666;
  display: flex;
  align-items: center;
  justify-content: center;
}

.player-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  color: #fff;
  font-size: 14px;
  font-weight: bold;
}

.player-details {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.player-name {
  color: #fff;
  font-size: 14px;
  font-weight: 500;
}

.player-score {
  font-size: 12px;
  color: #aaa;
}

.player-score.positive {
  color: #4cd137;
}

.player-score.negative {
  color: #ff4757;
}

.dealer-badge {
  background: #ff6b6b;
  color: #fff;
  font-size: 10px;
  font-weight: bold;
  padding: 2px 6px;
  border-radius: 4px;
}
</style>
