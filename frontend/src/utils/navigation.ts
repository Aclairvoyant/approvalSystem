export function homePathFor(role?: number): string {
  return role === 1 ? '/admin/dashboard' : '/mobile/home'
}

/** 只接受站内路径，防止 ?redirect= 带出站外 */
export function safeRedirect(value: unknown): string | null {
  if (typeof value !== 'string') return null
  if (!value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) return null
  if (value.startsWith('/login') || value.startsWith('/register')) return null
  return value
}
