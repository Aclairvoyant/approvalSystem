// 与后端 common/enums 保持一致的状态字典

export type StatusTone = 'accent' | 'ok' | 'danger' | 'neutral'

export const ApplicationStatus = {
  PENDING: 1,
  APPROVED: 2,
  REJECTED: 3,
  DRAFT: 4,
  CANCELLED: 5,
} as const

const APPLICATION_STATUS: Record<number, { text: string; tone: StatusTone }> = {
  1: { text: '待审批', tone: 'accent' },
  2: { text: '已通过', tone: 'ok' },
  3: { text: '已驳回', tone: 'danger' },
  4: { text: '草稿', tone: 'neutral' },
  5: { text: '已取消', tone: 'neutral' },
}

export const APPLICATION_STATUS_OPTIONS = [1, 2, 3, 4, 5].map(value => ({
  value,
  text: APPLICATION_STATUS[value].text,
}))

export function applicationStatusText(status?: number): string {
  return (status && APPLICATION_STATUS[status]?.text) || '未知'
}

export function applicationStatusTone(status?: number): StatusTone {
  return (status && APPLICATION_STATUS[status]?.tone) || 'neutral'
}

/** Arco Tag 的 color 取值 */
export function applicationStatusArcoColor(status?: number): string {
  const tone = applicationStatusTone(status)
  return { accent: 'arcoblue', ok: 'green', danger: 'red', neutral: 'gray' }[tone]
}

/** 语音申请的音频上传状态（applications.voice_status） */
export const VoiceStatus = {
  UPLOADING: 1,
  READY: 2,
  FAILED: 3,
} as const

export function voiceStatusText(voiceStatus?: number): string {
  return ({ 1: '音频上传中', 2: '音频就绪', 3: '音频上传失败' } as Record<number, string>)[voiceStatus ?? 0] || ''
}

export const AppType = {
  NORMAL: 1,
  VOICE: 2,
} as const

/** 操作日志类型（operation_logs.operation_type） */
export function operationTypeText(type?: number): string {
  return ({ 1: '提交申请', 2: '批准', 3: '驳回', 4: '修改', 5: '取消' } as Record<number, string>)[type ?? 0] || '操作'
}

/** 通知渠道（NotifyTypeEnum） */
export const NOTIFY_TYPE_OPTIONS = [
  { value: 1, text: '短信' },
  { value: 2, text: '邮件' },
  { value: 3, text: 'App 推送' },
]

export function notifyTypeText(type?: number): string {
  return NOTIFY_TYPE_OPTIONS.find(option => option.value === type)?.text || '未知'
}

/** 通知发送状态 */
export const SEND_STATUS_OPTIONS = [
  { value: 1, text: '待发送', color: 'gray' },
  { value: 2, text: '已发送', color: 'green' },
  { value: 3, text: '发送失败', color: 'red' },
]

export function sendStatusText(status?: number): string {
  return SEND_STATUS_OPTIONS.find(option => option.value === status)?.text || '未知'
}

export function sendStatusColor(status?: number): string {
  return SEND_STATUS_OPTIONS.find(option => option.value === status)?.color || 'gray'
}

/** 日常事项类型 */
export const DAILY_TYPE_OPTIONS = [
  { value: 1, text: '待办' },
  { value: 2, text: '清单' },
  { value: 3, text: '约会' },
  { value: 4, text: '约定' },
]

export function dailyTypeText(type?: number): string {
  return DAILY_TYPE_OPTIONS.find(option => option.value === type)?.text || '事项'
}
