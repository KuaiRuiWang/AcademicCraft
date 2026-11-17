<template>
  <div id="projectMarketPage">
    <div class="container">
      <header class="page-header">
        <div>
          <h1 class="page-title">项目商城</h1>
          <p class="page-desc">发现优秀的计算机毕设项目，轻松获取源码</p>
        </div>
        <RouterLink to="/create" class="custom-link"
          >没有合适的？试试 AI 定制 <ThunderboltOutlined aria-hidden="true"
        /></RouterLink>
      </header>
      <a-button
        class="filter-toggle"
        :aria-expanded="filtersOpen"
        aria-controls="market-filters"
        @click="filtersOpen = !filtersOpen"
        ><FilterOutlined aria-hidden="true" /> {{ filtersOpen ? '收起筛选' : '筛选项目' }}</a-button
      >
      <div class="market-layout">
        <aside
          id="market-filters"
          class="filter-panel"
          :class="{ 'is-open': filtersOpen }"
          aria-label="筛选项目"
        >
          <div class="filter-heading">
            <h2>筛选项目</h2>
            <a-button type="text" size="small" @click="resetFilters">重置</a-button>
          </div>
          <div class="filter-section">
            <label class="filter-title" for="project-search"
              ><SearchOutlined aria-hidden="true" /> 搜索</label
            >
            <a-input-search
              id="project-search"
              v-model:value="query.name"
              placeholder="搜索项目名称..."
              allow-clear
              @search="handleSearch"
            />
          </div>
          <div class="filter-section">
            <h3 class="filter-title"><AppstoreOutlined aria-hidden="true" /> 分类</h3>
            <a-radio-group
              v-model:value="query.categoryId"
              class="category-list"
              aria-label="项目分类"
              @change="handleSearch"
            >
              <a-radio :value="undefined" class="category-item">全部</a-radio>
              <a-radio
                v-for="cat in categories"
                :key="cat.id"
                :value="cat.id"
                class="category-item"
                >{{ cat.name }}</a-radio
              >
            </a-radio-group>
          </div>
          <div class="filter-section">
            <h3 class="filter-title"><DollarOutlined aria-hidden="true" /> 价格区间</h3>
            <a-slider
              v-model:value="priceRange"
              :aria-label-group-for-handles="['最低价格', '最高价格']"
              :min="0"
              :max="200"
              range
              :tip-formatter="(v: number) => `¥${v}`"
              @after-change="handlePriceChange"
            />
            <div class="price-labels">
              <span>¥{{ priceRange[0] }}</span
              ><span>¥{{ priceRange[1] }}</span>
            </div>
          </div>
        </aside>
        <section class="market-results" aria-label="项目列表" :aria-busy="loading">
          <div class="toolbar">
            <span class="result-count">{{ loadError ? '项目列表' : `共 ${total} 个项目` }}</span>
            <div class="sort-controls">
              <a-radio-group
                v-model:value="query.sortField"
                button-style="solid"
                size="small"
                aria-label="项目排序"
                @change="handleSearch"
              >
                <a-radio-button value="createTime">最新</a-radio-button
                ><a-radio-button value="salesCount">销量</a-radio-button
                ><a-radio-button value="avgRating">评分</a-radio-button
                ><a-radio-button value="price">价格</a-radio-button>
              </a-radio-group>
              <a-button
                v-if="query.sortField === 'price'"
                size="small"
                type="text"
                @click="togglePriceOrder"
                >{{ query.sortOrder === 'asc' ? '从低到高' : '从高到低' }}</a-button
              >
            </div>
          </div>
          <div v-if="loading" class="project-grid">
            <div v-for="i in 6" :key="i" class="skeleton-card">
              <div class="skel-cover"></div>
              <div class="skel-body">
                <a-skeleton active :title="{ width: '70%' }" :paragraph="{ rows: 2 }" />
              </div>
            </div>
          </div>
          <div v-else-if="loadError" class="empty-state" role="status">
            <DisconnectOutlined class="empty-icon" aria-hidden="true" />
            <h2>项目暂时无法加载</h2>
            <p>请检查网络连接，稍后重试。</p>
            <a-button @click="loadProjects"
              ><ReloadOutlined aria-hidden="true" /> 重新加载</a-button
            >
          </div>
          <div v-else-if="projects.length === 0" class="empty-state">
            <InboxOutlined class="empty-icon" aria-hidden="true" />
            <h2>暂时没有找到项目</h2>
            <p>换个筛选条件试试，或用 AI 定制你的项目。</p>
            <a-button @click="resetFilters">重置筛选</a-button>
          </div>
          <div v-else class="project-grid">
            <ProjectCard v-for="project in projects" :key="project.id" :project="project" />
          </div>
          <div v-if="!loading && !loadError && total > 0" class="pagination-wrapper">
            <a-pagination
              v-model:current="query.pageNum"
              :page-size="query.pageSize"
              :total="total"
              :show-size-changer="false"
              :show-total="(t: number) => `共 ${t} 个项目`"
              @change="loadProjects"
            />
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  SearchOutlined,
  AppstoreOutlined,
  DollarOutlined,
  ThunderboltOutlined,
  FilterOutlined,
  ReloadOutlined,
  InboxOutlined,
  DisconnectOutlined,
} from '@ant-design/icons-vue'
import { listProjectVoByPage } from '@/api/projectController'
import { listCategories } from '@/api/projectCategoryController'
import ProjectCard from '@/components/ProjectCard.vue'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const loadError = ref(false)
const filtersOpen = ref(false)
const projects = ref<API.ProjectVO[]>([])
const categories = ref<API.ProjectCategory[]>([])
const total = ref(0)
const priceRange = ref<[number, number]>([0, 200])

const query = reactive<API.ProjectQueryRequest>({
  pageNum: 1,
  pageSize: 12,
  sortField: 'createTime',
  sortOrder: 'desc',
})

// 加载分类
const loadCategories = async () => {
  try {
    const res = await listCategories()
    if (res.data.code === 0 && res.data.data) {
      categories.value = res.data.data
    }
  } catch {
    // ignore
  }
}

// 加载项目列表
const loadProjects = async () => {
  loading.value = true
  loadError.value = false
  try {
    const res = await listProjectVoByPage(query)
    if (res.data.code === 0 && res.data.data) {
      projects.value = res.data.data.records || []
      total.value = res.data.data.totalRow || 0
    } else {
      loadError.value = true
    }
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  query.pageNum = 1
  loadProjects()
}

// 价格区间变化
const handlePriceChange = () => {
  query.minPrice = priceRange.value[0]
  query.maxPrice = priceRange.value[1]
  query.pageNum = 1
  loadProjects()
}

// 切换价格排序方向
const togglePriceOrder = () => {
  query.sortOrder = query.sortOrder === 'asc' ? 'desc' : 'asc'
  loadProjects()
}

const resetFilters = () => {
  query.name = undefined
  query.categoryId = undefined
  query.minPrice = undefined
  query.maxPrice = undefined
  priceRange.value = [0, 200]
  if (route.query.categoryId) {
    router.replace({ query: { ...route.query, categoryId: undefined } })
  } else {
    handleSearch()
  }
}

// 从路由 query 同步分类筛选（主页分类卡片跳转带入）
// 分类 id 是雪花 Long，字符串传递避免精度丢失，同时与单选组 value 类型一致
const applyRouteCategory = () => {
  const raw = route.query.categoryId
  query.categoryId = typeof raw === 'string' && raw !== '' ? (raw as unknown as number) : undefined
}

// 已在商城页时，query 变化（顶部导航回商城/切换分类）重新加载
watch(
  () => route.query.categoryId,
  () => {
    applyRouteCategory()
    handleSearch()
  },
)

onMounted(() => {
  applyRouteCategory()
  loadCategories()
  loadProjects()
})
</script>

<style scoped>
#projectMarketPage {
  background: var(--color-bg);
}
.container {
  max-width: calc(var(--page-width) + var(--page-gutter) * 2);
  margin-inline: auto;
  padding: 44px var(--page-gutter) 72px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px;
  padding-bottom: 32px;
  margin-bottom: 32px;
  border-bottom: 1px solid var(--color-border);
}
.page-title {
  margin: 0 0 10px;
  font-size: clamp(1.75rem, 1.25rem + 1.5vw, 2.25rem);
  font-weight: 750;
  color: var(--color-text);
  line-height: 1.4;
}
.page-desc {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-secondary);
}
.custom-link {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--color-primary);
  font-size: 13px;
  min-height: 44px;
}
.market-layout {
  display: grid;
  grid-template-columns: 228px minmax(0, 1fr);
  gap: 32px;
  align-items: start;
}
.filter-panel {
  position: sticky;
  top: calc(var(--header-height) + 24px);
  padding: 20px;
  border-radius: 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
}
.filter-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.filter-heading h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
}
.filter-heading :deep(.ant-btn) {
  color: var(--color-text-tertiary);
}
.filter-section + .filter-section {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--color-border);
}
.filter-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  margin: 0 0 12px;
  color: var(--color-text-secondary);
}
.category-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.category-item {
  display: flex;
  align-items: center;
  min-height: 38px;
  margin: 0;
  font-size: 13px;
}
.price-labels {
  display: flex;
  justify-content: space-between;
  color: var(--color-text-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.market-results {
  min-width: 0;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  min-height: 40px;
  margin-bottom: 20px;
}
.result-count {
  font-size: 13px;
  color: var(--color-text-tertiary);
}
.sort-controls {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}
.sort-controls :deep(.ant-radio-button-wrapper) {
  min-height: 36px;
  height: auto;
  line-height: 34px;
  font-size: 12px;
  padding-inline: 14px;
}
.project-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}
.skeleton-card {
  overflow: hidden;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 14px;
}
.skel-cover {
  aspect-ratio: 16 / 10;
  background: var(--color-primary-soft);
}
.skel-body {
  padding: 20px;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 380px;
  padding: 36px 20px;
  background: var(--color-surface);
  border: 1px dashed var(--color-border);
  border-radius: 12px;
  text-align: center;
}
.empty-icon {
  font-size: 36px;
  color: var(--color-primary);
  margin-bottom: 20px;
}
.empty-state h2 {
  margin: 0;
  font-size: 19px;
  line-height: 1.5;
}
.empty-state p {
  margin: 10px 0 24px;
  color: var(--color-text-secondary);
  font-size: 13px;
}
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}
.filter-toggle {
  display: none;
}
@media (max-width: 1100px) {
  .project-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .market-layout {
    grid-template-columns: 210px minmax(0, 1fr);
    gap: 24px;
  }
}
@media (max-width: 760px) {
  .container {
    padding-block: 32px 48px;
  }
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
    padding-bottom: 20px;
    margin-bottom: 24px;
  }
  .market-layout {
    grid-template-columns: minmax(0, 1fr);
    gap: 20px;
  }
  .filter-toggle {
    display: inline-flex;
    align-items: center;
    margin-bottom: 20px;
    min-height: 44px;
  }
  .filter-panel {
    display: none;
    position: static;
  }
  .filter-panel.is-open {
    display: block;
  }
  .category-list {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 4px 16px;
  }
  .category-item {
    min-height: 44px;
  }
  .toolbar {
    gap: 12px;
  }
  .sort-controls :deep(.ant-radio-button-wrapper) {
    min-height: 44px;
    line-height: 42px;
  }
  .empty-state {
    min-height: 300px;
  }
}
@media (max-width: 520px) {
  .project-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .pagination-wrapper :deep(.ant-pagination-total-text) {
    display: none;
  }
}
</style>
