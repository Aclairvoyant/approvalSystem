<template>
  <div class="mj-discard-area" :class="[`position-${position}`]">
    <div class="discards-grid">
      <MahjongTile
        v-for="(tile, index) in tiles"
        :key="`discard-${index}`"
        :code="tile"
        size="small"
        :class="{ 'last-discard': isLast && index === tiles.length - 1 }"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import MahjongTile from './MahjongTile.vue'

interface Props {
  tiles?: string[]
  position: 'bottom' | 'right' | 'top' | 'left'
  isLast?: boolean  // 是否是最后打出的
}

withDefaults(defineProps<Props>(), {
  tiles: () => [],
  isLast: false
})
</script>

<style scoped>
.mj-discard-area {
  display: flex;
  justify-content: center;
  padding: 4px;
}

.discards-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  max-width: 200px;
}

.position-bottom .discards-grid,
.position-top .discards-grid {
  flex-direction: row;
}

.position-left .discards-grid,
.position-right .discards-grid {
  flex-direction: column;
  max-width: 60px;
}

.last-discard {
  animation: highlight 0.5s ease-in-out;
}

@keyframes highlight {
  0%, 100% {
    box-shadow: 0 0 0 2px rgba(255, 215, 0, 0);
  }
  50% {
    box-shadow: 0 0 0 2px rgba(255, 215, 0, 0.8);
  }
}
</style>
