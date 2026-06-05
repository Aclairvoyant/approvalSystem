/**
 * 无依赖的 WAV 录音工具。
 *
 * 浏览器原生 MediaRecorder 产出的是 webm/opus 或 mp4，MiMo ASR 不支持，
 * 因此这里用 Web Audio API 采集 PCM，停止时重采样到 16kHz 单声道并编码为
 * 16bit PCM WAV，直接得到 MiMo 接受的格式。
 *
 * 要求运行在 HTTPS 或 localhost 环境（getUserMedia 限制）。
 */

const TARGET_SAMPLE_RATE = 16000

export interface RecorderHandle {
  /** 停止录音并返回编码后的 WAV Blob */
  stop: () => Promise<Blob>
  /** 取消录音，释放资源，不产出文件 */
  cancel: () => void
}

/**
 * 浏览器是否具备录音能力。
 */
export function isRecordingSupported(): boolean {
  return !!(navigator.mediaDevices && typeof navigator.mediaDevices.getUserMedia === 'function' &&
    (window.AudioContext || (window as any).webkitAudioContext))
}

/**
 * 开始录音。返回一个句柄，调用 stop() 取回 WAV，或 cancel() 放弃。
 */
export async function startRecording(): Promise<RecorderHandle> {
  if (!isRecordingSupported()) {
    throw new Error('当前浏览器不支持录音，请更换浏览器或使用手动填写')
  }

  const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
  const AudioCtx = window.AudioContext || (window as any).webkitAudioContext
  const audioContext: AudioContext = new AudioCtx()
  const source = audioContext.createMediaStreamSource(stream)

  // ScriptProcessorNode 已废弃但兼容性最好；4096 帧缓冲
  const processor = audioContext.createScriptProcessor(4096, 1, 1)
  const chunks: Float32Array[] = []
  let recording = true

  processor.onaudioprocess = (e: AudioProcessingEvent): void => {
    if (!recording) return
    const input = e.inputBuffer.getChannelData(0)
    // 必须拷贝，inputBuffer 会被复用
    chunks.push(new Float32Array(input))
  }

  source.connect(processor)
  processor.connect(audioContext.destination)

  const cleanup = (): void => {
    recording = false
    try { processor.disconnect() } catch { /* ignore */ }
    try { source.disconnect() } catch { /* ignore */ }
    stream.getTracks().forEach(t => t.stop())
    if (audioContext.state !== 'closed') {
      audioContext.close().catch(() => { /* ignore */ })
    }
  }

  return {
    stop: async (): Promise<Blob> => {
      recording = false
      const sampleRate = audioContext.sampleRate
      cleanup()
      const merged = mergeChunks(chunks)
      const downsampled = downsampleTo16k(merged, sampleRate)
      return encodeWav(downsampled, TARGET_SAMPLE_RATE)
    },
    cancel: (): void => {
      cleanup()
      chunks.length = 0
    },
  }
}

function mergeChunks(chunks: Float32Array[]): Float32Array {
  let length = 0
  for (const c of chunks) length += c.length
  const result = new Float32Array(length)
  let offset = 0
  for (const c of chunks) {
    result.set(c, offset)
    offset += c.length
  }
  return result
}

/**
 * 线性重采样到 16kHz。源采样率通常为 44100/48000。
 */
function downsampleTo16k(buffer: Float32Array, sourceRate: number): Float32Array {
  if (sourceRate === TARGET_SAMPLE_RATE) return buffer
  if (sourceRate < TARGET_SAMPLE_RATE) return buffer // 不升采样，直接返回
  const ratio = sourceRate / TARGET_SAMPLE_RATE
  const newLength = Math.round(buffer.length / ratio)
  const result = new Float32Array(newLength)
  let offsetResult = 0
  let offsetBuffer = 0
  while (offsetResult < newLength) {
    const nextOffsetBuffer = Math.round((offsetResult + 1) * ratio)
    let accum = 0
    let count = 0
    for (let i = offsetBuffer; i < nextOffsetBuffer && i < buffer.length; i++) {
      accum += buffer[i]
      count++
    }
    result[offsetResult] = count > 0 ? accum / count : 0
    offsetResult++
    offsetBuffer = nextOffsetBuffer
  }
  return result
}

/**
 * 把 Float32 PCM 编码为 16bit 单声道 WAV Blob。
 */
function encodeWav(samples: Float32Array, sampleRate: number): Blob {
  const bytesPerSample = 2
  const blockAlign = bytesPerSample // 单声道
  const dataSize = samples.length * bytesPerSample
  const buffer = new ArrayBuffer(44 + dataSize)
  const view = new DataView(buffer)

  // RIFF header
  writeString(view, 0, 'RIFF')
  view.setUint32(4, 36 + dataSize, true)
  writeString(view, 8, 'WAVE')
  // fmt chunk
  writeString(view, 12, 'fmt ')
  view.setUint32(16, 16, true)          // chunk size
  view.setUint16(20, 1, true)           // PCM
  view.setUint16(22, 1, true)           // mono
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * blockAlign, true) // byte rate
  view.setUint16(32, blockAlign, true)
  view.setUint16(34, 16, true)          // bits per sample
  // data chunk
  writeString(view, 36, 'data')
  view.setUint32(40, dataSize, true)

  // PCM samples
  let offset = 44
  for (let i = 0; i < samples.length; i++) {
    const s = Math.max(-1, Math.min(1, samples[i]))
    view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true)
    offset += 2
  }

  return new Blob([view], { type: 'audio/wav' })
}

function writeString(view: DataView, offset: number, str: string): void {
  for (let i = 0; i < str.length; i++) {
    view.setUint8(offset + i, str.charCodeAt(i))
  }
}
