<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  FireOutlined,
  StarFilled,
  EyeOutlined,
  ArrowRightOutlined,
  CodeOutlined,
  CloudUploadOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { listProjectVoByPage } from '@/api/projectController'
import { listCategories } from '@/api/projectCategoryController'
import { listGoodAppVoByPage } from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'

const router = useRouter()

const defaultCover =
  'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAwIiBoZWlnaHQ9IjI0MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iNDAwIiBoZWlnaHQ9IjI0MCIgZmlsbD0iI2YwZjJmNSIvPjx0ZXh0IHg9IjIwMCIgeT0iMTI1IiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmaWxsPSIjYmZiZmMwIiBmb250LXNpemU9IjE4IiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+Tm8gSW1hZ2U8L3RleHQ+PC9zdmc+'

// 热门项目
const projectsLoading = ref(false)
const projects = ref<API.ProjectVO[]>([])

// 分类
const categories = ref<API.ProjectCategory[]>([])

// AI 生成案例
const featuredApps = ref<API.AppVO[]>([])
const featuredAppsLoading = ref(false)

// 加载热门项目（按销量）
const loadHotProjects = async () => {
  projectsLoading.value = true
  try {
    const res = await listProjectVoByPage({
      pageNum: 1,
      pageSize: 6,
      sortField: 'salesCount',
      sortOrder: 'desc',
    })
    if (res.data.code === 0 && res.data.data) {
      projects.value = res.data.data.records || []
    }
  } catch (error) {
    console.error('加载热门项目失败：', error)
  } finally {
    projectsLoading.value = false
  }
}

// 加载分类
const loadCategories = async () => {
  try {
    const res = await listCategories()
    if (res.data.code === 0 && res.data.data) {
      categories.value = res.data.data
    }
  } catch (error) {
    console.error('加载分类失败：', error)
  }
}

// 加载 AI 生成案例
const loadFeaturedApps = async () => {
  featuredAppsLoading.value = true
  try {
    const res = await listGoodAppVoByPage({
      pageNum: 1,
      pageSize: 3,
      sortField: 'createTime',
      sortOrder: 'desc',
    })
    if (res.data.code === 0 && res.data.data) {
      featuredApps.value = res.data.data.records || []
    }
  } catch (error) {
    console.error('加载 AI 案例失败：', error)
  } finally {
    featuredAppsLoading.value = false
  }
}

// 解析 techTags 字符串
const parseTags = (tags?: string): string[] => {
  if (!tags) return []
  return tags.split(',').filter(Boolean).map(t => t.trim())
}

// 跳转商城（可带分类）
const goToMarket = (categoryId?: number) => {
  if (categoryId) {
    router.push({ path: '/project', query: { categoryId } })
  } else {
    router.push('/project')
  }
}

// 跳转项目详情
const goToProjectDetail = (id?: number) => {
  if (id) router.push(`/project/${id}`)
}

// 查看对话
const viewChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}?view=1`)
  }
}

// 查看作品
const viewWork = (app: API.AppVO) => {
  if (app.deployKey) {
    const url = getDeployUrl(app.deployKey, app.codeGenType)
    window.open(url, '_blank')
  }
}

onMounted(() => {
  loadHotProjects()
  loadCategories()
  loadFeaturedApps()

  // 鼠标跟随光效（单色，柔和）
  const handleMouseMove = (e: MouseEvent) => {
    const { clientX, clientY } = e
    const { innerWidth, innerHeight } = window
    const x = (clientX / innerWidth) * 100
    const y = (clientY / innerHeight) * 100

    document.documentElement.style.setProperty('--mouse-x', `${x}%`)
    document.documentElement.style.setProperty('--mouse-y', `${y}%`)
  }

  document.addEventListener('mousemove', handleMouseMove)

  // 清理事件监听器
  return () => {
    document.removeEventListener('mousemove', handleMouseMove)
  }
})
</script>

<template>
  <div id="homePage">
    <div class="container">
      <!-- 主视觉 -->
      <div class="hero-section">
        <span class="hero-eyebrow">百夜毕设帮 · 计算机毕设一站式平台</span>
        <h1 class="hero-title">选一份成品项目，<br />或让 <span class="accent">AI 为你定制</span></h1>
        <p class="hero-description">
          精选优质毕设项目源码，购买即交付、可部署上线；没有找到合适的，用 AI 从零生成
        </p>
        <div class="hero-actions">
          <a-button type="primary" size="large" shape="round" @click="goToMarket()">
            逛项目商城
          </a-button>
          <a-button size="large" shape="round" class="ghost-btn" @click="router.push('/create')">
            AI 定制生成
            <ArrowRightOutlined />
          </a-button>
        </div>
      </div>

      <!-- 分类快捷入口 -->
      <div v-if="categories.length" class="category-bar">
        <span class="category-label">热门分类</span>
        <div class="category-chips">
          <a-button
            v-for="cat in categories"
            :key="cat.id"
            class="category-chip"
            @click="goToMarket(cat.id)"
          >
            {{ cat.name }}
          </a-button>
        </div>
      </div>

      <!-- 热门项目 -->
      <div class="section">
        <div class="section-header">
          <h2 class="section-title">热门项目</h2>
          <a class="section-more" @click="goToMarket()">
            查看全部 <ArrowRightOutlined />
          </a>
        </div>

        <div v-if="projectsLoading" class="project-grid">
          <div v-for="i in 6" :key="i" class="project-card">
            <div class="skel-cover"></div>
            <div class="card-body">
              <a-skeleton active :title="{ width: '70%' }" :paragraph="{ rows: 2 }" />
            </div>
          </div>
        </div>

        <div v-else-if="projects.length" class="project-grid">
          <div
            v-for="project in projects"
            :key="project.id"
            class="project-card"
            @click="goToProjectDetail(project.id)"
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
                  <a-tag v-for="tag in parseTags(project.techTags).slice(0, 3)" :key="tag" class="tech-tag">
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

        <div v-else class="empty-state">
          <div class="empty-icon">🗂️</div>
          <p class="empty-text">项目整理中，先用 AI 定制一个试试</p>
          <a-button type="primary" shape="round" @click="router.push('/create')">
            AI 定制生成
          </a-button>
        </div>
      </div>

      <!-- 平台优势 -->
      <div class="section">
        <div class="section-header">
          <h2 class="section-title">为什么选择我们</h2>
        </div>
        <div class="value-grid">
          <div class="value-card">
            <div class="value-icon"><CodeOutlined /></div>
            <h3 class="value-title">源码完整交付</h3>
            <p class="value-desc">完整源码 + 数据库脚本 + 说明文档，买断可二次开发</p>
          </div>
          <div class="value-card">
            <div class="value-icon"><CloudUploadOutlined /></div>
            <h3 class="value-title">支持部署上线</h3>
            <p class="value-desc">云端一键部署，域名直接访问，答辩演示随时可用</p>
          </div>
          <div class="value-card">
            <div class="value-icon"><ThunderboltOutlined /></div>
            <h3 class="value-title">AI 定制生成</h3>
            <p class="value-desc">描述需求自动生成前后端与数据库，支持全栈项目</p>
          </div>
        </div>
      </div>

      <!-- AI 生成案例 -->
      <div v-if="featuredAppsLoading || featuredApps.length" class="section">
        <div class="section-header">
          <h2 class="section-title">AI 生成案例</h2>
          <a class="section-more" @click="router.push('/create')">
            我也要定制 <ArrowRightOutlined />
          </a>
        </div>
        <div v-if="featuredAppsLoading" class="app-grid">
          <div v-for="i in 3" :key="i" class="skeleton-card">
            <div class="skel-media"></div>
            <div class="skel-body">
              <a-skeleton active :title="{ width: '60%' }" :paragraph="{ rows: 1 }" />
            </div>
          </div>
        </div>
        <div v-else class="app-grid">
          <AppCard
            v-for="app in featuredApps"
            :key="app.id"
            :app="app"
            :featured="true"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
      </div>

      <!-- 底部引导 -->
      <div class="cta-band">
        <div class="cta-text">
          <h3>没找到心仪的项目？</h3>
          <p>描述你的需求，让 AI 从零生成一个专属项目</p>
        </div>
        <a-button type="primary" size="large" shape="round" @click="router.push('/create')">
          开始 AI 定制
        </a-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
#homePage {
  width: 100%;
  margin: 0;
  padding: 0;
  min-height: 100vh;
  background: var(--color-bg);
  position: relative;
  overflow: hidden;
}

/* 顶部柔和的单色光晕 */
#homePage::before {
  content: '';
  position: absolute;
  top: -180px;
  left: 50%;
  transform: translateX(-50%);
  width: 900px;
  height: 480px;
  background: radial-gradient(ellipse at center, rgba(15, 118, 110, 0.12) 0%, transparent 70%);
  pointer-events: none;
}

/* 鼠标跟随单色光效 */
#homePage::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(
    700px circle at var(--mouse-x, 50%) var(--mouse-y, 50%),
    rgba(15, 118, 110, 0.07) 0%,
    transparent 70%
  );
  pointer-events: none;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 20px 60px;
  position: relative;
  z-index: 2;
  width: 100%;
  box-sizing: border-box;
}

/* 主视觉 */
.hero-section {
  text-align: center;
  padding: 84px 0 40px;
  color: var(--color-text);
  position: relative;
}

.hero-eyebrow {
  display: inline-block;
  padding: 6px 16px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--color-primary);
  background: rgba(15, 118, 110, 0.1);
  margin-bottom: 24px;
}

.hero-title {
  font-size: 52px;
  font-weight: 700;
  margin: 0 0 20px;
  line-height: 1.12;
  letter-spacing: -0.03em;
  color: var(--color-text);
}

.hero-title .accent {
  color: var(--color-primary);
}

.hero-description {
  font-size: 18px;
  margin: 0 auto;
  max-width: 600px;
  color: var(--color-text-secondary);
}

.hero-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin-top: 36px;
}

.ghost-btn {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  color: var(--color-text);
}

.ghost-btn:hover {
  color: var(--color-primary);
  border-color: rgba(15, 118, 110, 0.4);
}

/* 分类快捷入口 */
.category-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  justify-content: center;
  margin: 16px 0 64px;
  flex-wrap: wrap;
}

.category-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-tertiary);
}

.category-chips {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
}

.category-chip {
  border-radius: 999px;
  padding: 4px 18px;
  height: auto;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  font-weight: 500;
}

.category-chip:hover {
  color: var(--color-primary);
  border-color: rgba(15, 118, 110, 0.4);
  box-shadow: var(--shadow-pop);
}

/* 区块 */
.section {
  margin-bottom: 72px;
}

.section-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 28px;
}

.section-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0;
  color: var(--color-text);
  letter-spacing: -0.02em;
}

.section-more {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: color 0.25s var(--ease-out-expo);
}

.section-more:hover {
  color: var(--color-primary);
}

/* 项目网格 */
.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
}

.project-card {
  background: var(--color-surface);
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(231, 229, 228, 0.6);
  cursor: pointer;
  display: flex;
  flex-direction: column;
}

.project-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-card);
}

.card-cover {
  position: relative;
  aspect-ratio: 5 / 3;
  overflow: hidden;
  background: #f0f2f5;
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s var(--ease-out-expo);
}

.project-card:hover .card-cover img {
  transform: scale(1.04);
}

.card-price {
  position: absolute;
  left: 12px;
  top: 12px;
  padding: 3px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--color-primary-strong);
  font-weight: 700;
  font-size: 14px;
}

.card-sales {
  position: absolute;
  right: 12px;
  top: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(28, 25, 23, 0.6);
  color: #fff;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.card-body {
  padding: 16px;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  margin: 0 0 6px;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0 0 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 38px;
}

.card-meta {
  margin-bottom: 12px;
}

.tech-tag {
  border-radius: 999px;
  font-size: 12px;
}

.card-footer {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.author-name {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.stat {
  font-size: 13px;
  color: var(--color-text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* 平台优势 */
.value-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
}

.value-card {
  background: var(--color-surface);
  border: 1px solid rgba(231, 229, 228, 0.6);
  border-radius: 16px;
  padding: 28px 24px;
  transition:
    transform 0.35s var(--ease-out-expo),
    box-shadow 0.35s var(--ease-out-expo);
}

.value-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-card);
}

.value-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: rgba(15, 118, 110, 0.1);
  color: var(--color-primary);
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.value-title {
  font-size: 17px;
  font-weight: 700;
  margin: 0 0 8px;
  color: var(--color-text);
}

.value-desc {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 0;
  line-height: 1.6;
}

/* AI 案例网格 */
.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
}

.skeleton-card {
  background: var(--color-surface);
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(231, 229, 228, 0.6);
}

.skel-media,
.skel-cover {
  height: 180px;
  background: linear-gradient(90deg, #f0efed 25%, #f7f6f3 37%, #f0efed 63%);
  background-size: 400% 100%;
  animation: skel 1.4s ease infinite;
}

.skel-cover {
  aspect-ratio: 5 / 3;
  height: auto;
}

.skel-body {
  padding: 16px;
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
  padding: 56px 24px;
  background: var(--color-surface);
  border: 1px dashed var(--color-border);
  border-radius: 16px;
  text-align: center;
}

.empty-icon {
  font-size: 40px;
}

.empty-text {
  margin: 0 0 4px;
  color: var(--color-text-secondary);
}

/* 底部引导 */
.cta-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 40px 48px;
  border-radius: 20px;
  background: linear-gradient(120deg, rgba(15, 118, 110, 0.12), rgba(15, 118, 110, 0.04));
  border: 1px solid rgba(15, 118, 110, 0.18);
  flex-wrap: wrap;
}

.cta-text h3 {
  font-size: 22px;
  font-weight: 700;
  margin: 0 0 6px;
  color: var(--color-text);
}

.cta-text p {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-secondary);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .hero-section {
    padding: 56px 0 32px;
  }

  .hero-title {
    font-size: 32px;
  }

  .hero-description {
    font-size: 16px;
  }

  .hero-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .category-bar {
    margin-bottom: 48px;
  }

  .project-grid,
  .app-grid {
    grid-template-columns: 1fr;
  }

  .cta-band {
    padding: 28px 24px;
  }
}
</style>
