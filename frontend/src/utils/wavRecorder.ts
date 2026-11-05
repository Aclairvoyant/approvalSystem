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
// 16kHz 16bit 单声道约 32KB/s，5 分钟约 9.6MB，留在后端上传上限之内
const MAX_UPLOAD_SECONDS = 300

export type RecordingUnsupportedReason =
  | 'insecure-context'
  | 'missing-media-devices'
  | 'missing-audio-context'

export interface RecordingSupportStatus {
  supported: boolean
  reason?: RecordingUnsupportedReason
  message: string
}

export interface RecorderHandle {
  /** 停止录音并返回编码后的 WAV Blob */
  stop: () => Promise<Blob>
  /** 取消录音，释放资源，不产出文件 */
  cancel: () => void
}

/**
 * 浏览器录音能力与不可用原因。
 */
export function getRecordingSupportStatus(): RecordingSupportStatus {
  if (!isSecureRecordingContext()) {
    return {
      supported: false,
      reason: 'insecure-context',
      message: '录音需要 HTTPS 或 localhost 访问',
    }
  }

  if (!navigator.mediaDevices || typeof navigator.mediaDevices.getUserMedia !== 'function') {
    return {
      supported: false,
      reason: 'missing-media-devices',
      message: '当前浏览器没有开放麦克风录音能力',
    }
  }

  if (!getAudioContextClass()) {
    return {
      supported: false,
      reason: 'missing-audio-context',
      message: '当前浏览器不支持音频处理',
    }
  }

  return {
    supported: true,
    message: '当前浏览器支持录音',
  }
}

/**
 * 浏览器是否具备录音能力。
 */
export function isRecordingSupported(): boolean {
  return getRecordingSupportStatus().supported
}

/**
 * 开始录音。返回一个句柄，调用 stop() 取回 WAV，或 cancel() 放弃。
 */
export async function startRecording(): Promise<RecorderHandle> {
  const support = getRecordingSupportStatus()
  if (!support.supported) {
    throw new Error(support.message)
  }

  const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
  const AudioCtx = getAudioContextClass()!
  let audioContext: AudioContext | null = null
  let source: MediaStreamAudioSourceNode | null = null
  let processor: ScriptProcessorNode | null = null
  const chunks: Float32Array[] = []
  let recording = true

  const cleanup = (): void => {
    recording = false
    try { processor?.disconnect() } catch { /* ignore */ }
    try { source?.disconnect() } catch { /* ignore */ }
    stream.getTracks().forEach(t => t.stop())
    if (audioContext && audioContext.state !== 'closed') {
      audioContext.close().catch(() => { /* ignore */ })
    }
  }

  // 拿到麦克风之后任何一步失败，都要把麦克风关掉再抛出
  try {
    audioContext = new AudioCtx()
    if (audioContext.state === 'suspended') {
      await audioContext.resume()
    }
    source = audioContext.createMediaStreamSource(stream)

    // ScriptProcessorNode 已废弃但兼容性最好；4096 帧缓冲
    processor = audioContext.createScriptProcessor(4096, 1, 1)
    processor.onaudioprocess = (e: AudioProcessingEvent): void => {
      if (!recording) return
      const input = e.inputBuffer.getChannelData(0)
      // 必须拷贝，inputBuffer 会被复用
      chunks.push(new Float32Array(input))
    }

    source.connect(processor)
    processor.connect(audioContext.destination)
  } catch (error) {
    cleanup()
    throw error
  }

  const context = audioContext

  return {
    stop: async (): Promise<Blob> => {
      recording = false
      const sampleRate = context.sampleRate
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

/**
 * 把用户上传的音频文件解码后转成后端统一接受的 16kHz 单声道 WAV。
 */
export async function normalizeAudioBlobToWav(audioBlob: Blob): Promise<Blob> {
  const AudioCtx = getAudioContextClass()
  if (!AudioCtx) {
    throw new Error('当前浏览器不支持音频处理')
  }

  const audioContext: AudioContext = new AudioCtx()
  try {
    if (audioContext.state === 'suspended') {
      await audioContext.resume()
    }
    const audioBuffer = await decodeAudioBlob(audioContext, audioBlob)
    if (audioBuffer.duration > MAX_UPLOAD_SECONDS) {
      throw new Error(`音频不能超过 ${MAX_UPLOAD_SECONDS / 60} 分钟`)
    }
    const mono = mixToMono(audioBuffer)
    const downsampled = downsampleTo16k(mono, audioBuffer.sampleRate)
    return encodeWav(downsampled, TARGET_SAMPLE_RATE)
  } finally {
    if (audioContext.state !== 'closed') {
      audioContext.close().catch(() => { /* ignore */ })
    }
  }
}

function getAudioContextClass(): typeof AudioContext | undefined {
  return window.AudioContext || (window as any).webkitAudioContext
}

function isSecureRecordingContext(): boolean {
  if (typeof window.isSecureContext === 'boolean') {
    return window.isSecureContext
  }
  return location.protocol === 'https:' ||
    location.hostname === 'localhost' ||
    location.hostname === '127.0.0.1' ||
    location.hostname === '[::1]'
}

function decodeAudioBlob(audioContext: AudioContext, audioBlob: Blob): Promise<AudioBuffer> {
  return new Promise((resolve, reject) => {
    audioBlob.arrayBuffer()
      .then((arrayBuffer) => {
        const decodeResult = audioContext.decodeAudioData(arrayBuffer, resolve, reject)
        if (decodeResult && typeof decodeResult.then === 'function') {
          decodeResult.then(resolve).catch(reject)
        }
      })
      .catch(reject)
  })
}

function mixToMono(audioBuffer: AudioBuffer): Float32Array {
  const channels = Math.max(1, audioBuffer.numberOfChannels)
  const mixed = new Float32Array(audioBuffer.length)
  for (let channel = 0; channel < channels; channel++) {
    const input = audioBuffer.getChannelData(channel)
    for (let i = 0; i < input.length; i++) {
      mixed[i] += input[i] / channels
    }
  }
  return mixed
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
 * 线性重采样到 16kHz。源采样率通常为 44100/48000；
 * 低于 16kHz 的输入（如 8kHz 电话录音）做线性插值升采样，否则 WAV 头写的采样率和数据对不上。
 */
function downsampleTo16k(buffer: Float32Array, sourceRate: number): Float32Array {
  if (sourceRate === TARGET_SAMPLE_RATE) return buffer
  if (sourceRate < TARGET_SAMPLE_RATE) return upsampleTo16k(buffer, sourceRate)
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

function upsampleTo16k(buffer: Float32Array, sourceRate: number): Float32Array {
  if (buffer.length === 0) return buffer
  const ratio = sourceRate / TARGET_SAMPLE_RATE
  const newLength = Math.round(buffer.length / ratio)
  const result = new Float32Array(newLength)
  for (let i = 0; i < newLength; i++) {
    const position = i * ratio
    const left = Math.floor(position)
    const right = Math.min(left + 1, buffer.length - 1)
    const weight = position - left
    result[i] = buffer[left] * (1 - weight) + buffer[right] * weight
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
