import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

/** 平板：短边至少这么宽 */
const LARGE_MIN_SIDE = 700

/**
 * 视口尺寸和方向，随旋转、窗口缩放更新。
 * landscape：横屏（宽大于高）；large：平板或电脑（短边 ≥ 700px）。
 */
export function useViewport() {
  const width = ref(typeof window === 'undefined' ? 375 : window.innerWidth)
  const height = ref(typeof window === 'undefined' ? 812 : window.innerHeight)

  function update(): void {
    width.value = window.innerWidth
    height.value = window.innerHeight
  }

  onMounted(() => {
    update()
    window.addEventListener('resize', update)
    window.addEventListener('orientationchange', update)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', update)
    window.removeEventListener('orientationchange', update)
  })

  const landscape = computed(() => width.value > height.value)
  const large = computed(() => Math.min(width.value, height.value) >= LARGE_MIN_SIDE)

  return { width, height, landscape, large }
}
