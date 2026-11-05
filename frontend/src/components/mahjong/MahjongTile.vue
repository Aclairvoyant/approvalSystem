<template>
  <component
    :is="interactive ? 'button' : 'span'"
    class="mj-slot"
    :class="[
      `mj-size-${size}`,
      `mj-rot-${rotate}`,
      {
        'is-stand': stand,
        'is-selected': selected,
        'is-dim': dim
      }
    ]"
    :type="interactive ? 'button' : undefined"
    :disabled="interactive ? disabled : undefined"
    :aria-label="isBack ? '牌背' : label"
    :aria-pressed="interactive ? selected : undefined"
  >
    <span
      class="mj-tile"
      :class="[
        `ink-${kind}`,
        {
          'is-back': isBack,
          'is-wild': wild && !isBack,
          'is-latest': latest,
          'is-marked': marked,
          'is-white': tile === WHITE_DRAGON,
          'is-fa': tile === GREEN_DRAGON
        }
      ]"
    >
      <template v-if="!isBack">
        <span class="mj-glyph">{{ glyph }}</span>
        <span v-if="suit" class="mj-suit">{{ suit }}</span>
        <span v-if="wild" class="mj-wild" aria-hidden="true">搭</span>
      </template>
    </span>
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
  /**
   * 固定尺寸：xs / sm / md（结算、弹层里用）。
   * 牌桌上的尺寸随横竖屏和平板变化，取牌桌上的 CSS 变量：
   * river 牌河、seat 别人的副露和亮牌、back 别人立着的暗牌、lg 出牌特写、
   * hand 自己的手牌（--hand-tile-w）、meld 自己的副露。
   */
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'river' | 'seat' | 'back' | 'hand' | 'meld'
  /** 顺时针旋转角度：左右两家的牌横着放 */
  rotate?: 0 | 90 | 180 | 270
  /** 立着的牌（别人手里的暗牌），比平放的牌更窄更高 */
  stand?: boolean
  selected?: boolean
  wild?: boolean
  dim?: boolean
  latest?: boolean
  /** 可敲的牌：底部加一道高亮线 */
  marked?: boolean
  interactive?: boolean
  disabled?: boolean
}>(), {
  tile: null,
  size: 'sm',
  rotate: 0,
  stand: false,
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
 * 一张牌 = 外层占位（决定排版尺寸，横放时宽高互换）+ 内层牌身（绝对定位居中、按角度旋转）。
 * 牌身：象牙色牌面，底边一条玉绿色表示牌的厚度；牌背整面玉绿。
 */
.mj-slot {
  --tile-w: 20px;
  --tile-h: calc(var(--tile-w) * 1.36);
  --tile-face: #fffcf3;
  --tile-face-2: #f1e9d4;
  --tile-rule: #d6cbb0;
  /* 牌背颜色可换主题，由牌桌根元素的 --mj-back* 变量提供 */
  --tile-jade: var(--mj-back, #2f8c65);
  --tile-jade-dark: var(--mj-back-dark, #1f6248);
  --tile-jade-light: var(--mj-back-light, #55b98d);
  --ink-wan: #b42a1f;
  --ink-tiao: #1c6b44;
  --ink-tong: #1f4c8a;
  --ink-honor: #1f2329;
  --ink-flower: #8a5617;
  --mark: #f6c453;

  position: relative;
  display: inline-block;
  flex-shrink: 0;
  width: var(--tile-w);
  height: var(--tile-h);
  margin: 0;
  padding: 0;
  border: 0;
  background: none;
  font-family: var(--font-sans);
  line-height: 1;
  vertical-align: bottom;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  transition: transform 0.14s ease-out, opacity 0.14s ease-out;
}

button.mj-slot {
  cursor: pointer;
  touch-action: manipulation;
}

button.mj-slot:disabled {
  cursor: default;
}

.mj-size-xs {
  --tile-w: 17px;
}

.mj-size-sm {
  --tile-w: 20px;
}

.mj-size-md {
  --tile-w: 28px;
}

.mj-size-lg {
  --tile-w: var(--mj-lg-w, 46px);
}

.mj-size-river {
  --tile-w: var(--mj-river-w, 17px);
}

.mj-size-seat {
  --tile-w: var(--mj-seat-w, 17px);
}

.mj-size-back {
  --tile-w: var(--mj-back-w, 17px);
}

.mj-size-hand {
  --tile-w: var(--hand-tile-w, 26px);
}

.mj-size-meld {
  --tile-w: var(--mj-meld-w, 19px);
}

/* 立着的牌：窄而高，看到的是牌背 */
.mj-slot.is-stand {
  --tile-h: calc(var(--tile-w) * 1.5);
}

/* 横放：占位宽高互换 */
.mj-rot-90,
.mj-rot-270 {
  width: var(--tile-h);
  height: var(--tile-w);
}

.mj-tile {
  position: absolute;
  top: 50%;
  left: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: var(--tile-w);
  height: var(--tile-h);
  border: 1px solid var(--tile-rule);
  border-bottom: max(2px, calc(var(--tile-w) * 0.13)) solid var(--tile-jade);
  border-radius: max(2px, calc(var(--tile-w) * 0.14));
  background: linear-gradient(180deg, var(--tile-face) 0%, var(--tile-face) 55%, var(--tile-face-2) 100%);
  box-shadow: 0 1px 1px rgba(0, 0, 0, 0.18);
  color: var(--ink-honor);
  transform: translate(-50%, -50%);
}

.mj-rot-90 .mj-tile {
  transform: translate(-50%, -50%) rotate(90deg);
}

.mj-rot-180 .mj-tile {
  transform: translate(-50%, -50%) rotate(180deg);
}

.mj-rot-270 .mj-tile {
  transform: translate(-50%, -50%) rotate(270deg);
}

.mj-glyph {
  font-size: calc(var(--tile-w) * 0.56);
  font-weight: 700;
}

.mj-suit {
  margin-top: calc(var(--tile-w) * 0.05);
  font-size: calc(var(--tile-w) * 0.36);
  font-weight: 600;
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
  font-size: calc(var(--tile-w) * 0.64);
}

.ink-dragon .mj-glyph,
.ink-flower .mj-glyph {
  color: var(--ink-flower);
  font-size: calc(var(--tile-w) * 0.6);
}

/* 中发白：中红、发绿，白是空框 */
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

.mj-tile.is-back {
  border-color: var(--tile-jade-dark);
  border-bottom-color: var(--tile-jade-dark);
  background: linear-gradient(180deg, var(--tile-jade-light) 0%, var(--tile-jade) 60%, var(--tile-jade-dark) 100%);
}

/* 立着的牌背：顶上一道亮边表示牌的上沿 */
.is-stand .mj-tile.is-back {
  border-bottom-width: max(2px, calc(var(--tile-w) * 0.2));
  border-top: max(2px, calc(var(--tile-w) * 0.16)) solid #f4efe0;
}

.is-selected {
  transform: translateY(-10px);
}

.is-selected .mj-tile {
  box-shadow: 0 0 0 2px var(--mark), 0 6px 10px rgba(0, 0, 0, 0.3);
}

.mj-tile.is-wild {
  box-shadow: inset 0 0 0 1.5px var(--accent), 0 1px 1px rgba(0, 0, 0, 0.18);
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

/* 小号的牌放不下角标，只留内描边 */
.mj-size-xs .mj-wild,
.mj-size-river .mj-wild,
.mj-size-seat .mj-wild {
  display: none;
}

.is-dim {
  opacity: 0.45;
}

.mj-tile.is-latest {
  box-shadow: 0 0 0 2px var(--mark), 0 0 10px rgba(246, 196, 83, 0.8);
}

.mj-tile.is-marked::after {
  position: absolute;
  right: 3px;
  bottom: 2px;
  left: 3px;
  height: 3px;
  border-radius: 2px;
  background: var(--mark);
  content: '';
}

@media (prefers-reduced-motion: reduce) {
  .mj-slot {
    transition: none;
  }
}
</style>
