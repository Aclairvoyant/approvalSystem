<template>
  <div
    class="mj-tile"
    :class="[
      tileTypeClass,
      {
        selected: selected,
        disabled: disabled,
        horizontal: horizontal,
        back: isBack,
        small: size === 'small',
        large: size === 'large'
      }
    ]"
    @click="handleClick"
  >
    <div v-if="!isBack" class="tile-face">
      <!-- 万筒条数字牌 -->
      <template v-if="isNumberTile">
        <span class="tile-number">{{ displayNumber }}</span>
        <span class="tile-suit">{{ suitChar }}</span>
      </template>

      <!-- 风牌 -->
      <template v-else-if="tileType === 'FENG'">
        <span class="tile-char feng">{{ fengChar }}</span>
      </template>

      <!-- 箭牌 -->
      <template v-else-if="tileType === 'JIAN'">
        <span class="tile-char jian" :class="jianColorClass">{{ jianChar }}</span>
      </template>

      <!-- 花牌 -->
      <template v-else-if="tileType === 'HUA'">
        <span class="tile-char hua">{{ huaChar }}</span>
      </template>

      <!-- 百搭标记 -->
      <div v-if="isWild" class="wild-badge">搭</div>
    </div>

    <!-- 牌背 -->
    <div v-else class="tile-back">
      <div class="back-pattern"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  code?: string       // 牌的编码，如 "1WAN", "DONG", "ZHONG"
  selected?: boolean  // 是否选中
  disabled?: boolean  // 是否禁用
  horizontal?: boolean // 是否横放（用于碰/杠）
  isBack?: boolean    // 是否显示牌背
  isWild?: boolean    // 是否百搭
  size?: 'small' | 'normal' | 'large' // 大小
}

const props = withDefaults(defineProps<Props>(), {
  code: '',
  selected: false,
  disabled: false,
  horizontal: false,
  isBack: false,
  isWild: false,
  size: 'normal'
})

const emit = defineEmits<{
  click: [code: string]
}>()

// 解析牌编码
const parsedTile = computed(() => {
  if (!props.code) return null

  const code = props.code.toUpperCase()

  // 数字牌: 1WAN, 5TONG, 9TIAO
  const numberMatch = code.match(/^(\d)(WAN|TONG|TIAO)$/)
  if (numberMatch) {
    return {
      type: numberMatch[2] as 'WAN' | 'TONG' | 'TIAO',
      number: parseInt(numberMatch[1])
    }
  }

  // 风牌
  if (['DONG', 'NAN', 'XI', 'BEI'].includes(code)) {
    return { type: 'FENG', value: code }
  }

  // 箭牌
  if (['ZHONG', 'FA', 'BAI'].includes(code)) {
    return { type: 'JIAN', value: code }
  }

  // 花牌
  if (['CHUN', 'XIA', 'QIU', 'DONGHUA', 'MEI', 'LAN', 'ZHU', 'JU'].includes(code)) {
    return { type: 'HUA', value: code }
  }

  return null
})

// 牌类型
const tileType = computed(() => parsedTile.value?.type || '')

// 是否是数字牌
const isNumberTile = computed(() =>
  ['WAN', 'TONG', 'TIAO'].includes(tileType.value)
)

// 显示的数字
const displayNumber = computed(() => {
  if (!parsedTile.value || !isNumberTile.value) return ''
  return parsedTile.value.number
})

// 花色字符
const suitChar = computed(() => {
  switch (tileType.value) {
    case 'WAN': return '万'
    case 'TONG': return '筒'
    case 'TIAO': return '条'
    default: return ''
  }
})

// 风牌字符
const fengChar = computed(() => {
  if (tileType.value !== 'FENG' || !parsedTile.value) return ''
  const fengMap: Record<string, string> = {
    'DONG': '东',
    'NAN': '南',
    'XI': '西',
    'BEI': '北'
  }
  return fengMap[parsedTile.value.value] || ''
})

// 箭牌字符
const jianChar = computed(() => {
  if (tileType.value !== 'JIAN' || !parsedTile.value) return ''
  const jianMap: Record<string, string> = {
    'ZHONG': '中',
    'FA': '发',
    'BAI': '白'
  }
  return jianMap[parsedTile.value.value] || ''
})

// 箭牌颜色class
const jianColorClass = computed(() => {
  if (!parsedTile.value) return ''
  const value = parsedTile.value.value
  if (value === 'ZHONG') return 'zhong'
  if (value === 'FA') return 'fa'
  if (value === 'BAI') return 'bai'
  return ''
})

// 花牌字符
const huaChar = computed(() => {
  if (tileType.value !== 'HUA' || !parsedTile.value) return ''
  const huaMap: Record<string, string> = {
    'CHUN': '春',
    'XIA': '夏',
    'QIU': '秋',
    'DONGHUA': '冬',
    'MEI': '梅',
    'LAN': '兰',
    'ZHU': '竹',
    'JU': '菊'
  }
  return huaMap[parsedTile.value.value] || ''
})

// 牌类型样式class
const tileTypeClass = computed(() => {
  if (isNumberTile.value) {
    return `tile-${tileType.value.toLowerCase()}`
  }
  return `tile-${tileType.value.toLowerCase()}`
})

// 点击处理
function handleClick() {
  if (!props.disabled && props.code) {
    emit('click', props.code)
  }
}
</script>

<style scoped>
.mj-tile {
  --tile-width: 36px;
  --tile-height: 48px;
  --tile-radius: 4px;

  width: var(--tile-width);
  height: var(--tile-height);
  background: linear-gradient(180deg, #ffffff 0%, #e8e8e8 100%);
  border-radius: var(--tile-radius);
  box-shadow:
    0 2px 4px rgba(0, 0, 0, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  user-select: none;
  transition: transform 0.15s, box-shadow 0.15s;
  position: relative;
  flex-shrink: 0;
}

.mj-tile.small {
  --tile-width: 28px;
  --tile-height: 38px;
}

.mj-tile.large {
  --tile-width: 44px;
  --tile-height: 58px;
}

.mj-tile.horizontal {
  transform: rotate(90deg);
  margin: 0 calc((var(--tile-height) - var(--tile-width)) / 2);
}

.mj-tile.selected {
  transform: translateY(-8px);
  box-shadow:
    0 8px 12px rgba(0, 0, 0, 0.3),
    0 0 0 2px #1989fa;
}

.mj-tile.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.mj-tile:not(.disabled):not(.selected):hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.25);
}

.tile-face {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  padding: 2px;
}

.tile-number {
  font-size: 20px;
  font-weight: bold;
  line-height: 1;
}

.tile-suit {
  font-size: 10px;
  line-height: 1;
  margin-top: 1px;
}

.tile-char {
  font-size: 22px;
  font-weight: bold;
  line-height: 1;
}

/* 万 - 红色 */
.tile-wan .tile-number,
.tile-wan .tile-suit {
  color: #c41e3a;
}

/* 筒 - 蓝色 */
.tile-tong .tile-number,
.tile-tong .tile-suit {
  color: #1e90ff;
}

/* 条 - 绿色 */
.tile-tiao .tile-number,
.tile-tiao .tile-suit {
  color: #228b22;
}

/* 风牌 - 蓝黑色 */
.tile-feng .tile-char {
  color: #2c3e50;
}

/* 箭牌 */
.tile-jian .tile-char.zhong {
  color: #c41e3a;
}

.tile-jian .tile-char.fa {
  color: #228b22;
}

.tile-jian .tile-char.bai {
  color: #2c3e50;
  -webkit-text-stroke: 1px #2c3e50;
  -webkit-text-fill-color: transparent;
}

/* 花牌 - 彩色 */
.tile-hua .tile-char {
  background: linear-gradient(135deg, #ff6b6b, #ffd93d, #6bcb77, #4d96ff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* 百搭标记 */
.wild-badge {
  position: absolute;
  top: 2px;
  right: 2px;
  font-size: 8px;
  color: #ff4757;
  font-weight: bold;
  background: rgba(255, 255, 255, 0.9);
  padding: 1px 2px;
  border-radius: 2px;
}

/* 牌背 */
.tile-back {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #1a5276 0%, #2e86ab 100%);
  border-radius: var(--tile-radius);
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-pattern {
  width: 60%;
  height: 70%;
  background: repeating-linear-gradient(
    45deg,
    transparent,
    transparent 2px,
    rgba(255, 255, 255, 0.1) 2px,
    rgba(255, 255, 255, 0.1) 4px
  );
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 2px;
}

.mj-tile.back {
  background: none;
  box-shadow:
    0 2px 4px rgba(0, 0, 0, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
}
</style>
