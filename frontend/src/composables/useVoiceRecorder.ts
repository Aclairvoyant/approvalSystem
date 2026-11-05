import { onBeforeUnmount, ref } from 'vue'
import { getRecordingSupportStatus, startRecording, type RecorderHandle } from '@/utils/wavRecorder'

/**
 * 录音状态封装：
 * - starting 期间（等待麦克风授权）重复点击不会再开一个录音；
 * - 等待授权时用户取消，授权回来后立刻关掉麦克风，不会在后台继续录；
 * - 到达时长上限时调用 onLimit，由页面决定如何收尾；
 * - 组件卸载时自动释放麦克风。
 */
export function useVoiceRecorder(options: { maxSeconds?: number; onLimit?: () => void } = {}) {
  const recording = ref(false)
  const starting = ref(false)
  const seconds = ref(0)

  let handle: RecorderHandle | null = null
  let timer: ReturnType<typeof setInterval> | null = null
  let session = 0

  function clearTimer(): void {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  async function start(): Promise<void> {
    if (starting.value || recording.value) return
    const support = getRecordingSupportStatus()
    if (!support.supported) {
      throw new Error(support.message)
    }

    const current = ++session
    starting.value = true
    seconds.value = 0
    try {
      const nextHandle = await startRecording()
      if (current !== session) {
        nextHandle.cancel()
        return
      }
      handle = nextHandle
      recording.value = true
      timer = setInterval(() => {
        seconds.value += 1
        if (options.maxSeconds && seconds.value >= options.maxSeconds) {
          options.onLimit?.()
        }
      }, 1000)
    } finally {
      if (current === session) starting.value = false
    }
  }

  /** 结束录音，返回 WAV；没有在录音时返回 null */
  async function stop(): Promise<Blob | null> {
    if (!handle || !recording.value) return null
    clearTimer()
    recording.value = false
    const current = handle
    handle = null
    return current.stop()
  }

  function cancel(): void {
    session += 1
    clearTimer()
    handle?.cancel()
    handle = null
    recording.value = false
    starting.value = false
  }

  onBeforeUnmount(cancel)

  return { recording, starting, seconds, start, stop, cancel }
}
