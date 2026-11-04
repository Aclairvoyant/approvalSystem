/**
 * 复制文本。局域网 HTTP 访问时 navigator.clipboard 不存在，退回到 execCommand。
 */
export async function copyText(text: string): Promise<void> {
  if (navigator.clipboard && window.isSecureContext) {
    await navigator.clipboard.writeText(text)
    return
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  try {
    if (!document.execCommand('copy')) {
      throw new Error('复制失败，请手动复制')
    }
  } finally {
    document.body.removeChild(textarea)
  }
}
