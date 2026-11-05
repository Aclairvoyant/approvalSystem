/**
 * 头像等资源地址：
 * - OSS 返回的完整 URL 原样使用；
 * - 旧数据里的 "/uploads/..." 相对路径走同源（开发环境由 vite 代理到后端），
 *   不再写死 http://localhost:8080，手机和部署环境都能访问。
 */
export function resolveAssetUrl(path?: string | null): string {
  if (!path) return ''
  if (/^(https?:|data:|blob:)/i.test(path)) return path
  return path.startsWith('/') ? path : `/${path}`
}

/** 头像占位文字：取名字的第一个字 */
export function avatarInitial(name?: string | null): string {
  const text = (name || '').trim()
  return text ? text.charAt(0).toUpperCase() : '?'
}
