import { onBeforeUnmount, ref, shallowRef } from 'vue'

/** 持有一个 Blob 及其 object URL，替换或卸载时自动 revoke */
export function useBlobUrl() {
  const blob = shallowRef<Blob | null>(null)
  const url = ref('')

  function clear(): void {
    if (url.value) URL.revokeObjectURL(url.value)
    url.value = ''
    blob.value = null
  }

  function set(next: Blob): void {
    clear()
    blob.value = next
    url.value = URL.createObjectURL(next)
  }

  onBeforeUnmount(clear)

  return { blob, url, set, clear }
}
