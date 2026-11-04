<template>
  <van-popup :show="show" position="bottom" round :close-on-click-overlay="false" @update:show="onUpdateShow">
    <div class="recorder">
      <div class="recorder-title">{{ title }}</div>
      <p class="recorder-hint">{{ hintText }}</p>

      <div class="mic" :class="`mic--${state}`" aria-hidden="true">
        <van-loading v-if="state === 'parsing' || state === 'starting'" color="currentColor" size="28px" />
        <van-icon v-else name="volume" size="34" />
      </div>

      <p class="timer tabular" :class="{ 'is-hidden': state !== 'recording' }">
        {{ formatDuration(seconds) }}<span v-if="maxSeconds"> / {{ formatDuration(maxSeconds) }}</span>
      </p>

      <div class="m-sheet-footer recorder-actions">
        <van-button :disabled="state === 'parsing'" @click="emit('cancel')">取消</van-button>
        <van-button type="primary" :disabled="state !== 'recording'" @click="emit('finish')">完成</van-button>
      </div>
    </div>
  </van-popup>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatDuration } from '@/utils/format'

export type RecorderState = 'starting' | 'recording' | 'parsing' | 'idle'

const props = withDefaults(defineProps<{
  show: boolean
  title: string
  hint?: string
  state: RecorderState
  seconds: number
  maxSeconds?: number
}>(), {
  hint: '',
  maxSeconds: 0,
})

const emit = defineEmits<{
  cancel: []
  finish: []
}>()

const hintText = computed(() => {
  if (props.state === 'starting') return '正在打开麦克风，第一次使用需要允许录音权限'
  if (props.state === 'parsing') return '正在识别并整理内容，请稍候'
  return props.hint
})

function onUpdateShow(value: boolean) {
  if (!value) emit('cancel')
}
</script>

<style scoped>
.recorder {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 0 calc(16px + env(safe-area-inset-bottom));
}

.recorder-title {
  color: var(--ink);
  font-size: 17px;
  font-weight: 600;
}

.recorder-hint {
  min-height: 20px;
  margin-top: 6px;
  padding: 0 24px;
  color: var(--ink-2);
  font-size: 13px;
  text-align: center;
}

.mic {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 84px;
  height: 84px;
  margin-top: 24px;
  border-radius: 50%;
  background: var(--surface-2);
  color: var(--ink-2);
}

.mic--recording {
  background: var(--accent);
  color: #fff;
  animation: breathe 1.6s ease-in-out infinite;
}

.mic--parsing {
  background: var(--accent-soft);
  color: var(--accent);
}

@keyframes breathe {
  50% {
    opacity: 0.72;
  }
}

@media (prefers-reduced-motion: reduce) {
  .mic--recording {
    animation: none;
  }
}

.timer {
  margin-top: 12px;
  color: var(--ink);
  font-size: 15px;
}

.timer span {
  color: var(--ink-3);
}

.timer.is-hidden {
  visibility: hidden;
}

.recorder-actions {
  align-self: stretch;
  padding-top: 20px;
}
</style>
