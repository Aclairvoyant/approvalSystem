import { ref, type Ref } from 'vue'
import { showFailToast } from 'vant'

export interface PageResult<T> {
  records?: T[]
  total?: number
}

/**
 * 给 van-list 用的分页状态。
 *
 * - 每次请求带编号，切换标签 / 重置后迟到的旧响应直接丢弃，不会串到新列表里；
 * - 请求开始时同步把 loading 置为 true，van-list 的 watcher 不会再额外触发一次 load；
 * - 失败时置 error，van-list 显示"点击重试"，不会无限重试刷 toast。
 *
 * 模板用法：
 *   <van-pull-refresh v-model="refreshing" @refresh="refresh">
 *     <van-list v-model:loading="loading" v-model:error="error" :finished="finished" @load="load">
 */
export function usePagedList<T>(
  fetcher: (pageNum: number, pageSize: number) => Promise<PageResult<T>>,
  options: { pageSize?: number; errorText?: string } = {}
) {
  const pageSize = options.pageSize ?? 10
  const list = ref([]) as Ref<T[]>
  const loading = ref(false)
  const finished = ref(false)
  const error = ref(false)
  const refreshing = ref(false)
  const total = ref(0)

  let nextPage = 1
  let requestId = 0

  async function load(): Promise<void> {
    const id = ++requestId
    const pageNum = nextPage
    loading.value = true
    error.value = false
    try {
      const result = await fetcher(pageNum, pageSize)
      if (id !== requestId) return
      const records = result?.records ?? []
      list.value = pageNum === 1 ? records : [...list.value, ...records]
      total.value = result?.total ?? list.value.length
      nextPage = pageNum + 1
      finished.value = records.length < pageSize || list.value.length >= total.value
    } catch (e: any) {
      if (id !== requestId) return
      error.value = true
      showFailToast(e?.message || options.errorText || '加载失败')
    } finally {
      if (id === requestId) {
        loading.value = false
        refreshing.value = false
      }
    }
  }

  /** 从第一页重新加载，并清空当前列表（切换筛选时用） */
  function reset(): Promise<void> {
    nextPage = 1
    list.value = []
    total.value = 0
    finished.value = false
    return load()
  }

  /** 从第一页重新加载，加载完成前保留旧列表（下拉刷新、操作后刷新时用） */
  function refresh(): Promise<void> {
    nextPage = 1
    finished.value = false
    return load()
  }

  return { list, loading, finished, error, refreshing, total, load, reset, refresh }
}
