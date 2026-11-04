<template>
  <component
    :is="interactive ? 'button' : 'span'"
    class="mj-tile"
    :class="[
      `mj-tile--${size}`,
      `ink-${kind}`,
      {
        'is-back': isBack,
        'is-selected': selected,
        'is-wild': wild && !isBack,
        'is-dim': dim,
        'is-latest': latest,
        'is-marked': marked,
        'is-white': tile === WHITE_DRAGON,
        'is-fa': tile === GREEN_DRAGON
      }
    ]"
    :type="interactive ? 'button' : undefined"
    :disabled="interactive ? disabled : undefined"
    :aria-label="isBack ? '牌背' : label"
    :aria-pressed="interactive ? selected : undefined"
  >
    <template v-if="!isBack">
      <span class="mj-glyph">{{ glyph }}</span>
      <span v-if="suit" class="mj-suit">{{ suit }}</span>
      <span v-if="wild" class="mj-wild" aria-hidden="true">搭</span>
    </template>
  </component>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { tileGlyph, tileKind, tileName, tileSuitGlyph } from '@/services/mahjongApi'

const GREEN_DRAGON = 46
const WHITE_DRAGON = 47

const props = withDefaults(defineProps<{
  /** 不传或 null 表示牌背 */
  tile?: number | null
  /** xs 牌河 / 花，sm 副露，md 结算，hand 手牌（宽度由父元素的 --tile-w 决定） */
  size?: 'xs' | 'sm' | 'md' | 'hand'
  selected?: boolean
  wild?: boolean
  dim?: boolean
  latest?: boolean
  /** 可敲的牌：底部加一道主色线 */
  marked?: boolean
  interactive?: boolean
  disabled?: boolean
}>(), {
  tile: null,
  size: 'sm',
  selected: false,
  wild: false,
  dim: false,
  latest: false,
  marked: false,
  interactive: false,
  disabled: false
})

const isBack = computed(() => props.tile === null || props.tile === undefined)
const kind = computed(() => (isBack.value ? 'back' : tileKind(props.tile as number)))
const glyph = computed(() => (isBack.value ? '' : tileGlyph(props.tile as number)))
const suit = computed(() => (isBack.value ? '' : tileSuitGlyph(props.tile as number)))
const label = computed(() => {
  if (isBack.value) return ''
  const name = tileName(props.tile)
  return props.wild ? `${name}（百搭）` : name
})
</script>

<style scoped>
/*
 * 牌面：象牙色底 + 发丝边，底边加厚表现牌的厚度（不用投影）。
 * 万、条、筒、花各用一种低饱和墨色，只在牌面上使用，界面其他地方仍然只用 --accent。
 */
.mj-tile {
  --tile-face: #fbf8f1;
  --tile-rule: #d3cbb9;
  --tile-edge: #c4b99f;
  --tile-back: #3f6a5c;
  --tile-back-edge: #2c4d42;
  --ink-wan: #a8281f;
  --ink-tiao: #1f6a45;
  --ink-tong: #1f4c8a;
  --ink-flower: #87561a;
  --tile-w: 22px;

  position: relative;
  display: inline-flex;
  flex-direction: column;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: var(--tile-w);
  height: calc(var(--tile-w) * 1.36);
  margin: 0;
  padding: 0;
  border: 1px solid var(--tile-rule);
  border-bottom: 3px solid var(--tile-edge);
  border-radius: 4px;
  background: var(--tile-face);
  color: var(--ink);
  font-family: var(--font-sans);
  line-height: 1;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  transition: transform 0.12s ease-out, opacity 0.12s ease-out;
}

button.mj-tile {
  cursor: pointer;
}

button.mj-tile:disabled {
  cursor: default;
}

.mj-tile--xs {
  --tile-w: 17px;
  border-bottom-width: 2px;
  border-radius: 3px;
}

.mj-tile--sm {
  --tile-w: 21px;
  border-bottom-width: 2px;
}

.mj-tile--md {
  --tile-w: 28px;
}

.mj-tile--hand {
  --tile-w: var(--hand-tile-w, 26px);
}

.mj-glyph {
  font-size: calc(var(--tile-w) * 0.56);
  font-weight: 600;
}

.mj-suit {
  margin-top: calc(var(--tile-w) * 0.06);
  font-size: calc(var(--tile-w) * 0.36);
}

.ink-wan .mj-glyph,
.ink-wan .mj-suit {
  color: var(--ink-wan);
}

.ink-tiao .mj-glyph,
.ink-tiao .mj-suit {
  color: var(--ink-tiao);
}

.ink-tong .mj-glyph,
.ink-tong .mj-suit {
  color: var(--ink-tong);
}

.ink-wind .mj-glyph {
  font-size: calc(var(--tile-w) * 0.62);
}

.ink-dragon .mj-glyph,
.ink-flower .mj-glyph {
  color: var(--ink-flower);
  font-size: calc(var(--tile-w) * 0.6);
}

/* 中发白分开上色：中红、发绿，白是空框 */
.ink-dragon .mj-glyph {
  color: var(--ink-wan);
}

.ink-dragon.is-fa .mj-glyph {
  color: var(--ink-tiao);
}

.ink-dragon.is-white .mj-glyph {
  padding: 1px 2px;
  border: 1px solid var(--ink-tong);
  color: var(--ink-tong);
  font-size: calc(var(--tile-w) * 0.42);
}

.is-back {
  border-color: var(--tile-back-edge);
  background: var(--tile-back);
}

.is-selected {
  transform: translateY(-8px);
  border-color: var(--accent);
}

.is-wild {
  border-color: var(--accent);
}

.mj-wild {
  position: absolute;
  top: -1px;
  right: -1px;
  padding: 1px 2px;
  border-radius: 0 3px 0 3px;
  background: var(--accent);
  color: #fff;
  font-size: max(8px, calc(var(--tile-w) * 0.3));
  font-weight: 600;
  line-height: 1;
}

/* 最小号的牌放不下角标，只留主色边框 */
.mj-tile--xs .mj-wild {
  display: none;
}

.is-dim {
  opacity: 0.42;
}

.is-latest {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}

.is-marked::after {
  position: absolute;
  right: 3px;
  bottom: 2px;
  left: 3px;
  height: 2px;
  border-radius: 1px;
  background: var(--accent);
  content: '';
}

@media (prefers-reduced-motion: reduce) {
  .mj-tile {
    transition: none;
  }
}
</style>
