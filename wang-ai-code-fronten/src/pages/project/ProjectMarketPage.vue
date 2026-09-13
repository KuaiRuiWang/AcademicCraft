<template>
  <div id="projectMarketPage">
    <div class="container">
      <!-- 页面头部 -->
      <div class="page-header">
        <span class="page-eyebrow">PROJECT MARKET</span>
        <h1 class="page-title">项目商城</h1>
        <p class="page-desc">发现优秀的计算机毕设项目，轻松获取源码</p>
      </div>

      <a-row :gutter="24">
        <!-- 左侧筛选栏 -->
        <a-col :xs="24" :md="6">
          <a-card class="filter-card" :bordered="false">
            <div class="filter-section">
              <h4 class="filter-title">
                <SearchOutlined /> 搜索
              </h4>
              <a-input-search
                v-model:value="query.name"
                placeholder="搜索项目名称..."
                allow-clear
                @search="handleSearch"
              />
            </div>

            <a-divider />

            <div class="filter-section">
              <h4 class="filter-title">
                <AppstoreOutlined /> 分类
              </h4>
              <a-radio-group v-model:value="query.categoryId" class="category-list" @change="handleSearch">
                <a-radio :value="undefined" class="category-item">全部</a-radio>
                <a-radio
                  v-for="cat in categories"
                  :key="cat.id"
                  :value="cat.id"
                  class="category-item"
                >
                  {{ cat.name }}
                </a-radio>
              </a-radio-group>
            </div>

            <a-divider />

            <div class="filter-section">
              <h4 class="filter-title">
                <DollarOutlined /> 价格区间
              </h4>
              <a-slider
                v-model:value="priceRange"
                :min="0"
                :max="200"
                range
                :tip-formatter="(v: number) => `¥${v}`"
                @after-change="handlePriceChange"
              />
              <div class="price-labels">
                <span>¥0</span>
                <span>¥200</span>
              </div>
            </div>
          </a-card>
        </a-col>

        <!-- 右侧项目列表 -->
        <a-col :xs="24" :md="18">
          <!-- 排序栏 -->
          <div class="toolbar">
            <a-radio-group v-model:value="query.sortField" button-style="solid" size="small" @change="handleSearch">
              <a-radio-button value="createTime">最新</a-radio-button>
              <a-radio-button value="salesCount">销量</a-radio-button>
              <a-radio-button value="avgRating">评分</a-radio-button>
              <a-radio-button value="price">价格</a-radio-button>
            </a-radio-group>
            <a-button
              v-if="query.sortField === 'price'"
              size="small"
              type="text"
              @click="togglePriceOrder"
            >
              {{ query.sortOrder === 'asc' ? '↑ 从低到高' : '↓ 从高到低' }}
            </a-button>
          </div>

          <!-- 加载骨架屏 -->
          <div v-if="loading" class="project-grid">
            <div v-for="i in 8" :key="i" class="project-card">
              <div class="skel-cover"></div>
              <div class="card-body">
                <a-skeleton active :title="{ width: '70%' }" :paragraph="{ rows: 2 }" />
              </div>
            </div>
          </div>

          <div v-else-if="projects.length === 0" class="empty-state">
            <div class="empty-icon">🗂️</div>
            <p class="empty-text">暂无项目，换个筛选条件试试</p>
          </div>

          <div v-else class="project-grid">
            <div
              v-for="project in projects"
              :key="project.id"
              class="project-card"
              @click="goToDetail(project.id)"
            >
              <div class="card-cover">
                <img
                  :src="project.coverImage || defaultCover"
                  :alt="project.name"
                  @error="(e: any) => { e.target.src = defaultCover }"
                />
                <div class="card-price">{{ project.price ? `¥${project.price}` : '免费' }}</div>
                <div v-if="project.salesCount" class="card-sales">
                  <FireOutlined /> {{ project.salesCount }}
                </div>
              </div>
              <div class="card-body">
                <h3 class="card-title">{{ project.name }}</h3>
                <p class="card-desc">{{ project.description || '暂无描述' }}</p>
                <div class="card-meta">
                  <a-space :size="4" wrap>
                    <a-tag v-for="tag in parseTags(project.techTags)" :key="tag" class="tech-tag">
                      {{ tag }}
                    </a-tag>
                  </a-space>
                </div>
                <div class="card-footer">
                  <a-space>
                    <a-avatar :src="project.author?.userAvatar" :size="20" />
                    <span class="author-name">{{ project.author?.userName || '匿名' }}</span>
                  </a-space>
                  <a-space :size="16">
                    <span class="stat">
                      <StarFilled style="color: #f59e0b" />
                      {{ project.avgRating ? project.avgRating.toFixed(1) : '-' }}
                    </span>
                    <span class="stat">
                      <EyeOutlined />
                      {{ project.viewCount || 0 }}
                    </span>
                  </a-space>
                </div>
              </div>
            </div>
          </div>

          <!-- 分页 -->
          <div v-if="total > 0" class="pagination-wrapper">
            <a-pagination
              v-model:current="query.pageNum"
              :page-size="query.pageSize"
              :total="total"
              :show-size-changer="false"
              :show-total="(t: number) => `共 ${t} 个项目`"
              @change="loadProjects"
            />
          </div>
        </a-col>
      </a-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  SearchOutlined,
  AppstoreOutlined,
  DollarOutlined,
  FireOutlined,
  StarFilled,
  EyeOutlined,
} from '@ant-design/icons-vue'
import { listProjectVoByPage } from '@/api/projectController'
import { listCategories } from '@/api/projectCategoryController'
import { message } from 'ant-design-vue'

const router = useRouter()

const defaultCover = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAwIiBoZWlnaHQ9IjI0MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iNDAwIiBoZWlnaHQ9IjI0MCIgZmlsbD0iI2YwZjJmNSIvPjx0ZXh0IHg9IjIwMCIgeT0iMTI1IiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmaWxsPSIjYmZiZmMwIiBmb250LXNpemU9IjE4IiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+Tm8gSW1hZ2U8L3RleHQ+PC9zdmc+'

const loading = ref(false)
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
  try {
    const res = await listProjectVoByPage(query)
    if (res.data.code === 0 && res.data.data) {
      projects.value = res.data.data.records || []
      total.value = res.data.data.totalRow || 0
    }
  } catch {
    message.error('加载项目失败')
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

// 跳转详情
const goToDetail = (id?: number) => {
  if (id) router.push(`/project/${id}`)
}

// 解析 techTags 字符串
const parseTags = (tags?: string): string[] => {
  if (!tags) return []
  return tags.split(',').filter(Boolean).map(t => t.trim())
}

onMounted(() => {
  loadCategories()
  loadProjects()
})
</script>

<style scoped>
#projectMarketPage {
  min-height: 100vh;
  background: var(--color-bg);
}

.container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px 16px 64px;
}

/* 头部 */
.page-header {
  text-align: center;
  padding: 40px 0 32px;
}

.page-eyebrow {
  display: inline-block;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-primary);
  margin-bottom: 12px;
}

.page-title {
  font-size: 34px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--color-text);
  margin: 0 0 10px;
}

.page-desc {
  font-size: 16px;
  color: var(--color-text-secondary);
  margin: 0;
}

/* 筛选卡片 */
.filter-card {
  border-radius: 14px;
  position: sticky;
  top: 84px;
  box-shadow: var(--shadow-card);
  border: 1px solid rgba(231, 229, 228, 0.6);
}

.filter-section {
  margin-bottom: 4px;
}

.filter-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.category-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.category-item {
  margin: 0 !important;
}

.price-labels {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

/* 工具栏 */
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

/* 项目网格 */
.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.project-card {
  background: var(--color-surface);
  border-radius: 14px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid rgba(231, 229, 228, 0.6);
  box-shadow: var(--shadow-card);
  transition:
    transform 0.35s var(--ease-out-expo),
    box-shadow 0.35s var(--ease-out-expo);
}

.project-card:hover {
  transform: translateY(-6px);
  box-shadow: var(--shadow-lift);
}

.card-cover {
  position: relative;
  height: 160px;
  overflow: hidden;
  background: var(--color-surface-soft);
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s var(--ease-out-expo);
}

.project-card:hover .card-cover img {
  transform: scale(1.05);
}

.card-price {
  position: absolute;
  top: 12px;
  right: 12px;
  background: var(--color-primary);
  color: #fff;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 6px 16px -6px rgba(15, 118, 110, 0.6);
}

.card-sales {
  position: absolute;
  top: 12px;
  left: 12px;
  background: rgba(22, 24, 29, 0.55);
  backdrop-filter: blur(6px);
  color: #fff;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
}

.card-body {
  padding: 16px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0 0 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
  min-height: 39px;
}

.card-meta {
  margin-bottom: 12px;
  min-height: 24px;
}

.tech-tag {
  background: rgba(15, 118, 110, 0.08);
  color: var(--color-primary);
  border: none;
  border-radius: 999px;
  font-size: 12px;
  padding-inline: 10px;
  line-height: 22px;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
  font-size: 13px;
  color: var(--color-text-tertiary);
}

.author-name {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.stat {
  font-size: 13px;
  color: var(--color-text-tertiary);
  display: flex;
  align-items: center;
  gap: 2px;
}

/* 骨架屏 */
.skel-cover {
  height: 160px;
  background: linear-gradient(90deg, #f0efed 25%, #f7f6f3 37%, #f0efed 63%);
  background-size: 400% 100%;
  animation: skel 1.4s ease infinite;
}

@keyframes skel {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 72px 24px;
  background: var(--color-surface);
  border: 1px dashed var(--color-border);
  border-radius: 14px;
  text-align: center;
}

.empty-icon {
  font-size: 40px;
}

.empty-text {
  margin: 0;
  color: var(--color-text-secondary);
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}
</style>
