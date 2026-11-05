import dayjs from 'dayjs'

type DateInput = string | number | Date | null | undefined

const DAY_MS = 86400000

/**
 * 后端 LocalDateTime 序列化成不带时区的 "YYYY-MM-DDTHH:mm:ss"，
 * dayjs 会按本地时间解析，避免 new Date('YYYY-MM-DD') 被当成 UTC 的问题。
 */
function toDayjs(value: DateInput) {
  if (value === null || value === undefined || value === '') return null
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed : null
}

export function formatDateTime(value: DateInput): string {
  return toDayjs(value)?.format('YYYY-MM-DD HH:mm') ?? '-'
}

export function formatDate(value: DateInput): string {
  return toDayjs(value)?.format('YYYY-MM-DD') ?? '-'
}

export function formatMonthDayTime(value: DateInput): string {
  return toDayjs(value)?.format('MM-DD HH:mm') ?? '-'
}

/** 按自然日计算的相对时间；客户端时钟比服务器慢时不会出现"-1 天前" */
export function relativeTime(value: DateInput): string {
  const target = toDayjs(value)
  if (!target) return '-'

  const now = dayjs()
  const minutes = Math.max(0, now.diff(target, 'minute'))
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`

  const days = now.startOf('day').diff(target.startOf('day'), 'day')
  if (days <= 0) return target.format('HH:mm')
  if (days === 1) return `昨天 ${target.format('HH:mm')}`
  if (days < 7) return `${days}天前`
  if (target.year() === now.year()) return target.format('MM-DD')
  return target.format('YYYY-MM-DD')
}

export function formatDuration(totalSeconds: number): string {
  const sec = Math.max(0, Math.floor(totalSeconds))
  return `${String(Math.floor(sec / 60)).padStart(2, '0')}:${String(sec % 60).padStart(2, '0')}`
}

/** 把 "YYYY-MM-DD" 解析为本地零点 */
export function parseLocalDate(value: string): Date {
  const [year, month, day] = value.slice(0, 10).split('-').map(Number)
  return new Date(year, month - 1, day)
}

export function startOfToday(): Date {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), now.getDate())
}

export function todayValue(): string {
  return dayjs().format('YYYY-MM-DD')
}

/** 每年重复（repeatType === 1）的日期取下一次出现的日子，否则就是原日期 */
export function nextOccurrence(date: string, repeatType?: number): Date {
  const base = parseLocalDate(date)
  if (repeatType !== 1) return base

  const today = startOfToday()
  const next = new Date(today.getFullYear(), base.getMonth(), base.getDate())
  if (next < today) {
    next.setFullYear(next.getFullYear() + 1)
  }
  return next
}

/** 距今天还有多少个自然日，过去为负数 */
export function daysUntil(date: string, repeatType?: number): number {
  return Math.round((nextOccurrence(date, repeatType).getTime() - startOfToday().getTime()) / DAY_MS)
}

export function formatMonthDay(date: Date): string {
  return dayjs(date).format('MM-DD')
}
