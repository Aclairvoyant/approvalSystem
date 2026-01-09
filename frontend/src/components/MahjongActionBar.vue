<template>
  <div class="mj-action-bar" v-if="showBar">
    <div class="action-buttons">
      <!-- 过 -->
      <van-button
        v-if="canPass"
        type="default"
        size="normal"
        @click="emit('pass')"
      >
        过
      </van-button>

      <!-- 碰 -->
      <van-button
        v-if="canPong"
        type="primary"
        size="normal"
        @click="emit('pong')"
      >
        碰
      </van-button>

      <!-- 杠（明杠/暗杠/补杠） -->
      <van-button
        v-if="canKong"
        type="warning"
        size="normal"
        @click="handleKong"
      >
        杠
      </van-button>

      <!-- 胡 -->
      <van-button
        v-if="canHu"
        type="danger"
        size="normal"
        @click="emit('hu')"
      >
        胡
      </van-button>

      <!-- 出牌 -->
      <van-button
        v-if="canDiscard"
        type="success"
        size="normal"
        :disabled="!selectedTile"
        @click="handleDiscard"
      >
        出牌
      </van-button>
    </div>

    <!-- 杠牌选择弹窗 -->
    <van-action-sheet
      v-model:show="showKongPicker"
      :actions="kongOptions"
      cancel-text="取消"
      @select="onKongSelect"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

interface KongOption {
  tile: string
  type: 'MING_KONG' | 'AN_KONG' | 'BU_KONG'
  name: string
}

interface Props {
  availableActions?: string[]  // 可执行的操作
  selectedTile?: string | null // 选中的牌
  anKongOptions?: string[]     // 可以暗杠的牌
  buKongOptions?: string[]     // 可以补杠的牌
  lastDiscardTile?: string     // 最后打出的牌（用于明杠）
}

const props = withDefaults(defineProps<Props>(), {
  availableActions: () => [],
  selectedTile: null,
  anKongOptions: () => [],
  buKongOptions: () => [],
  lastDiscardTile: ''
})

const emit = defineEmits<{
  pass: []
  pong: []
  kong: [tile: string, type: string]
  hu: []
  discard: [tile: string]
}>()

const showKongPicker = ref(false)

// 是否显示操作栏
const showBar = computed(() => props.availableActions.length > 0)

// 各操作的可用状态
const canPass = computed(() => props.availableActions.includes('PASS'))
const canPong = computed(() => props.availableActions.includes('PONG'))
const canKong = computed(() =>
  props.availableActions.includes('MING_KONG') ||
  props.availableActions.includes('AN_KONG') ||
  props.availableActions.includes('BU_KONG')
)
const canHu = computed(() => props.availableActions.includes('HU'))
const canDiscard = computed(() => props.availableActions.includes('DISCARD'))

// 杠牌选项
const kongOptions = computed(() => {
  const options: { name: string; value: KongOption }[] = []

  // 明杠
  if (props.availableActions.includes('MING_KONG') && props.lastDiscardTile) {
    options.push({
      name: `明杠 ${props.lastDiscardTile}`,
      value: { tile: props.lastDiscardTile, type: 'MING_KONG', name: '明杠' }
    })
  }

  // 暗杠
  if (props.availableActions.includes('AN_KONG')) {
    props.anKongOptions.forEach(tile => {
      options.push({
        name: `暗杠 ${tile}`,
        value: { tile, type: 'AN_KONG', name: '暗杠' }
      })
    })
  }

  // 补杠
  if (props.availableActions.includes('BU_KONG')) {
    props.buKongOptions.forEach(tile => {
      options.push({
        name: `补杠 ${tile}`,
        value: { tile, type: 'BU_KONG', name: '补杠' }
      })
    })
  }

  return options
})

// 处理杠牌
function handleKong() {
  if (kongOptions.value.length === 1) {
    // 只有一个选项，直接执行
    const option = kongOptions.value[0].value
    emit('kong', option.tile, option.type)
  } else if (kongOptions.value.length > 1) {
    // 多个选项，弹出选择
    showKongPicker.value = true
  }
}

// 杠牌选择
function onKongSelect(action: { value: KongOption }) {
  emit('kong', action.value.tile, action.value.type)
  showKongPicker.value = false
}

// 处理出牌
function handleDiscard() {
  if (props.selectedTile) {
    emit('discard', props.selectedTile)
  }
}
</script>

<style scoped>
.mj-action-bar {
  position: fixed;
  bottom: 100px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 100;
}

.action-buttons {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  background: rgba(0, 0, 0, 0.8);
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.action-buttons :deep(.van-button) {
  min-width: 60px;
  font-weight: bold;
}
</style>
