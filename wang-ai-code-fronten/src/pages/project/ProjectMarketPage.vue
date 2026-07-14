<template>
  <div id="projectMarketPage">
    <div class="container">
      <!-- 页面头部 -->
      <div class="page-header">
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
          <a-spin :spinning="loading">
            <div v-if="projects.length === 0 && !loading" class="empty-state">
              <a-empty description="暂无项目" />
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
                    <a-space :size="4">
                      <a-tag v-for="tag in parseTags(project.techTags)" :key="tag" color="blue">
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
                        <StarFilled style="color: #faad14" />
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
          </a-spin>

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
  background: #f5f7fa;
}

.container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px 16px;
}

/* 头部 */
.page-header {
  text-align: center;
  padding: 32px 0 24px;
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 8px;
}

.page-desc {
  font-size: 16px;
  color: #8c8c8c;
  margin: 0;
}

/* 筛选卡片 */
.filter-card {
  border-radius: 12px;
  position: sticky;
  top: 24px;
}

.filter-section {
  margin-bottom: 4px;
}

.filter-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
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
  color: #999;
  margin-top: 4px;
}

/* 工具栏 */
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

/* 项目网格 */
.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.project-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.project-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.card-cover {
  position: relative;
  height: 160px;
  overflow: hidden;
  background: #f0f2f5;
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}

.project-card:hover .card-cover img {
  transform: scale(1.05);
}

.card-price {
  position: absolute;
  top: 12px;
  right: 12px;
  background: linear-gradient(135deg, #ff4d4f, #ff7875);
  color: #fff;
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 14px;
  font-weight: 600;
}

.card-sales {
  position: absolute;
  top: 12px;
  left: 12px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
}

.card-body {
  padding: 16px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  font-size: 13px;
  color: #8c8c8c;
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

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
  font-size: 13px;
  color: #999;
}

.author-name {
  font-size: 13px;
  color: #666;
}

.stat {
  font-size: 13px;
  color: #999;
  display: flex;
  align-items: center;
  gap: 2px;
}

/* 空状态 */
.empty-state {
  padding: 80px 0;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}
</style>
