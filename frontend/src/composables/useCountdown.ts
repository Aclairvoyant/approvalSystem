import { computed, onBeforeUnmount, ref } from 'vue'

/** 验证码倒计时，组件卸载时自动清理定时器 */
export function useCountdown(defaultSeconds = 60) {
  const remaining = ref(0)
  let timer: ReturnType<typeof setInterval> | null = null

  function reset(): void {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    remaining.value = 0
  }

  function start(seconds = defaultSeconds): void {
    reset()
    remaining.value = seconds
    timer = setInterval(() => {
      remaining.value -= 1
      if (remaining.value <= 0) reset()
    }, 1000)
  }

  onBeforeUnmount(reset)

  return { remaining, running: computed(() => remaining.value > 0), start, reset }
}
