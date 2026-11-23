import { computed, onMounted, ref } from 'vue'

type PageQuery = { pageNum: number; pageSize: number }
type PageResponse<T> = {
  data: { code?: number; data?: { records?: T[]; totalRow?: number }; message?: string }
}

export function useAdminTable<T>(fetchPage: (page: PageQuery) => Promise<PageResponse<T>>) {
  const rows = ref<T[]>([])
  const current = ref(1)
  const pageSize = ref(10)
  const total = ref(0)
  const loading = ref(true)
  const error = ref('')
  let latestRequest = 0

  async function load() {
    const request = ++latestRequest
    loading.value = true
    error.value = ''
    try {
      const res = await fetchPage({ pageNum: current.value, pageSize: pageSize.value })
      if (request !== latestRequest) return
      if (res.data.code !== 0 || !res.data.data) throw new Error(res.data.message || '记录加载失败')
      rows.value = res.data.data.records || []
      total.value = res.data.data.totalRow || 0
      const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value))
      if (current.value > lastPage) {
        current.value = lastPage
        await load()
      }
    } catch (failure) {
      if (request === latestRequest)
        error.value = failure instanceof Error ? failure.message : '记录加载失败'
    } finally {
      if (request === latestRequest) loading.value = false
    }
  }

  function search() {
    current.value = 1
    void load()
  }

  function changePage(nextPage: number, nextSize: number) {
    current.value = nextPage
    pageSize.value = nextSize
    void load()
  }

  const pagination = computed(() => ({
    current: current.value,
    pageSize: pageSize.value,
    total: total.value,
    showSizeChanger: true,
  }))

  onMounted(load)
  return { rows, loading, error, pagination, load, search, changePage }
}
