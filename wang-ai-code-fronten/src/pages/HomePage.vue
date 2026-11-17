<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRightOutlined,
  CodeOutlined,
  CloudUploadOutlined,
  ThunderboltOutlined,
  ShoppingOutlined,
  InboxOutlined,
  ReloadOutlined,
  DisconnectOutlined,
} from '@ant-design/icons-vue'
import { listProjectVoByPage } from '@/api/projectController'
import { listCategories } from '@/api/projectCategoryController'
import { listGoodAppVoByPage } from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'
import ProjectCard from '@/components/ProjectCard.vue'

const router = useRouter()
const projectsLoading = ref(false)
const projectsError = ref(false)
const projects = ref<API.ProjectVO[]>([])
const categories = ref<API.ProjectCategory[]>([])
const featuredApps = ref<API.AppVO[]>([])
const featuredAppsLoading = ref(false)

const loadHotProjects = async () => {
  projectsLoading.value = true
  projectsError.value = false
  try {
    const res = await listProjectVoByPage({
      pageNum: 1,
      pageSize: 6,
      sortField: 'salesCount',
      sortOrder: 'desc',
    })
    if (res.data.code === 0 && res.data.data) {
      projects.value = res.data.data.records || []
    } else {
      projectsError.value = true
    }
  } catch {
    projectsError.value = true
  } finally {
    projectsLoading.value = false
  }
}

const loadCategories = async () => {
  try {
    const res = await listCategories()
    if (res.data.code === 0 && res.data.data) categories.value = res.data.data
  } catch {
    // 分类不可用时保留商城入口。
  }
}

const loadFeaturedApps = async () => {
  featuredAppsLoading.value = true
  try {
    const res = await listGoodAppVoByPage({
      pageNum: 1,
      pageSize: 3,
      sortField: 'createTime',
      sortOrder: 'desc',
    })
    if (res.data.code === 0 && res.data.data) featuredApps.value = res.data.data.records || []
  } catch {
    // 案例不可用时不展示该区块。
  } finally {
    featuredAppsLoading.value = false
  }
}

const viewChat = (appId: string | number | undefined) => {
  if (appId) router.push(`/app/chat/${appId}?view=1`)
}
const viewWork = (app: API.AppVO) => {
  if (app.deployKey)
    window.open(getDeployUrl(app.deployKey, app.codeGenType), '_blank', 'noopener,noreferrer')
}
onMounted(() => {
  loadHotProjects()
  loadCategories()
  loadFeaturedApps()
})
</script>

<template>
  <div id="homePage">
    <div class="container">
      <section class="hero-section" aria-labelledby="home-title">
        <div class="hero-copy">
          <h1 id="home-title" class="hero-title">
            选一份成品项目，<br />或让 <span class="accent">AI 为你定制</span>
          </h1>
          <p class="hero-description">
            精选优质毕设项目源码，购买即交付、可部署上线；没有找到合适的，用 AI 从零生成
          </p>
          <div class="hero-actions">
            <RouterLink to="/project" class="hero-link primary-link"
              >逛项目商城 <ArrowRightOutlined aria-hidden="true"
            /></RouterLink>
            <RouterLink to="/create" class="hero-link secondary-link"
              ><ThunderboltOutlined aria-hidden="true" /> AI 定制生成</RouterLink
            >
          </div>
          <p class="hero-note">
            <CodeOutlined aria-hidden="true" /> 百夜毕设帮 · 计算机毕设一站式平台
          </p>
        </div>
        <aside class="start-panel" aria-label="项目获取方式">
          <CodeOutlined class="panel-mark" aria-hidden="true" />
          <h2>一个想法，两种开始。</h2>
          <RouterLink to="/project" class="path-option">
            <ShoppingOutlined class="path-icon" aria-hidden="true" />
            <span><strong>选一份成品项目</strong><small>精选优质毕设项目源码</small></span>
            <ArrowRightOutlined aria-hidden="true" />
          </RouterLink>
          <RouterLink to="/create" class="path-option">
            <ThunderboltOutlined class="path-icon" aria-hidden="true" />
            <span><strong>让 AI 为你定制</strong><small>描述你的需求，从零生成项目</small></span>
            <ArrowRightOutlined aria-hidden="true" />
          </RouterLink>
          <div class="panel-stack"><span>Vue</span><span>Node.js</span><span>MySQL</span></div>
        </aside>
      </section>

      <nav v-if="categories.length" class="category-bar" aria-label="热门分类">
        <span class="category-label">热门分类</span>
        <div class="category-chips">
          <RouterLink
            v-for="cat in categories"
            :key="cat.id"
            :to="{ path: '/project', query: { categoryId: cat.id } }"
            class="category-chip"
            >{{ cat.name }}</RouterLink
          >
        </div>
      </nav>

      <section class="section" aria-labelledby="projects-title">
        <div class="section-header">
          <div>
            <h2 id="projects-title" class="section-title">热门项目</h2>
            <p class="section-desc">精选优质毕设项目源码，找到适合你的起点</p>
          </div>
          <RouterLink to="/project" class="section-more"
            >查看全部 <ArrowRightOutlined aria-hidden="true"
          /></RouterLink>
        </div>
        <div
          v-if="projectsLoading"
          class="project-grid"
          aria-label="正在加载热门项目"
          aria-busy="true"
        >
          <div v-for="i in 6" :key="i" class="skeleton-card">
            <div class="skel-cover"></div>
            <div class="skel-body">
              <a-skeleton active :title="{ width: '70%' }" :paragraph="{ rows: 2 }" />
            </div>
          </div>
        </div>
        <div v-else-if="projectsError" class="empty-state" role="status">
          <DisconnectOutlined class="empty-icon" aria-hidden="true" />
          <h3>项目暂时无法加载</h3>
          <p>请检查网络后重试，也可以先用 AI 定制一个项目。</p>
          <a-button @click="loadHotProjects"
            ><ReloadOutlined aria-hidden="true" /> 重新加载</a-button
          >
        </div>
        <div v-else-if="projects.length" class="project-grid">
          <ProjectCard v-for="project in projects" :key="project.id" :project="project" />
        </div>
        <div v-else class="empty-state">
          <InboxOutlined class="empty-icon" aria-hidden="true" />
          <h3>项目整理中</h3>
          <p>先用 AI 定制一个试试，让想法成为你的第一个项目。</p>
          <a-button type="primary" @click="router.push('/create')">AI 定制生成</a-button>
        </div>
      </section>

      <section class="section value-section" aria-labelledby="values-title">
        <h2 id="values-title" class="section-title">为什么选择我们</h2>
        <div class="value-grid">
          <div class="value-item">
            <CodeOutlined class="value-icon" aria-hidden="true" />
            <div>
              <h3>源码完整交付</h3>
              <p>完整源码 + 数据库脚本 + 说明文档，买断可二次开发</p>
            </div>
          </div>
          <div class="value-item">
            <CloudUploadOutlined class="value-icon" aria-hidden="true" />
            <div>
              <h3>支持部署上线</h3>
              <p>云端一键部署，域名直接访问，答辩演示随时可用</p>
            </div>
          </div>
          <div class="value-item">
            <ThunderboltOutlined class="value-icon" aria-hidden="true" />
            <div>
              <h3>AI 定制生成</h3>
              <p>描述需求自动生成前后端与数据库，支持全栈项目</p>
            </div>
          </div>
        </div>
      </section>

      <section
        v-if="featuredAppsLoading || featuredApps.length"
        class="section"
        aria-labelledby="apps-title"
      >
        <div class="section-header">
          <h2 id="apps-title" class="section-title">AI 生成案例</h2>
          <RouterLink to="/create" class="section-more"
            >我也要定制 <ArrowRightOutlined aria-hidden="true"
          /></RouterLink>
        </div>
        <div v-if="featuredAppsLoading" class="app-grid" aria-busy="true">
          <div v-for="i in 3" :key="i" class="skeleton-card">
            <div class="skel-cover"></div>
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
      </section>

      <section class="cta-band">
        <div>
          <h2>没找到心仪的项目？</h2>
          <p>描述你的需求，让 AI 从零生成一个专属项目</p>
        </div>
        <RouterLink to="/create" class="hero-link cta-link"
          >开始 AI 定制 <ArrowRightOutlined aria-hidden="true"
        /></RouterLink>
      </section>
    </div>
  </div>
</template>

<style scoped>
#homePage {
  background: var(--color-bg);
}
.container {
  max-width: calc(var(--page-width) + var(--page-gutter) * 2);
  margin-inline: auto;
  padding: 0 var(--page-gutter) 64px;
}
.hero-section {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  align-items: center;
  gap: clamp(32px, 5vw, 80px);
  padding-block: clamp(48px, 6vw, 88px) 64px;
}
.hero-copy {
  min-width: 0;
}
.hero-title {
  font-size: clamp(2rem, 1.25rem + 2.8vw, 3.5rem);
  font-weight: 750;
  margin: 0 0 24px;
  line-height: 1.25;
  letter-spacing: -0.035em;
  color: var(--color-text);
}
.accent {
  color: var(--color-primary);
}
.hero-description {
  max-width: 510px;
  margin: 0;
  font-size: 16px;
  line-height: 1.9;
  color: var(--color-text-secondary);
}
.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 32px;
}
.hero-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  min-height: 48px;
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  transition:
    background-color 0.2s,
    color 0.2s;
}
.primary-link {
  background: var(--color-primary);
  color: white;
}
.primary-link:hover {
  background: var(--color-primary-strong);
  color: white;
}
.secondary-link {
  color: var(--color-text);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
}
.secondary-link:hover {
  border-color: var(--color-primary-muted);
  color: var(--color-primary);
}
.hero-note {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 22px 0 0;
  font-size: 12px;
  color: var(--color-text-tertiary);
}
.start-panel {
  padding: 32px;
  border-radius: 16px;
  background: var(--color-primary-strong);
  color: #fff;
}
.panel-mark {
  display: inline-flex;
  font-size: 30px;
  color: #a8d5c8;
  margin-bottom: 24px;
}
.start-panel h2 {
  color: inherit;
  font-size: 24px;
  line-height: 1.5;
  margin: 0 0 20px;
  font-weight: 650;
}
.path-option {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-block: 18px;
  color: inherit;
  border-top: 1px solid #427e75;
}
.path-option:hover {
  color: #d5eee5;
}
.path-option > span:nth-child(2) {
  flex: 1;
  min-width: 0;
}
.path-option strong {
  display: block;
  font-size: 15px;
  font-weight: 600;
}
.path-option small {
  display: block;
  margin-top: 3px;
  color: #c1ded5;
  font-size: 12px;
}
.path-icon {
  font-size: 22px;
  color: #c1ded5;
}
.panel-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  border-top: 1px solid #427e75;
  padding-top: 18px;
  font-size: 11px;
  color: #c1ded5;
}
.category-bar {
  display: flex;
  align-items: baseline;
  gap: 24px;
  margin-bottom: 56px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--color-border);
}
.category-label {
  flex-shrink: 0;
  color: var(--color-text-tertiary);
  font-size: 13px;
}
.category-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.category-chip {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding-inline: 14px;
  color: var(--color-text-secondary);
  font-size: 13px;
  border-radius: 6px;
}
.category-chip:hover {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}
.section {
  margin-bottom: 64px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}
.section-title {
  margin: 0;
  font-size: clamp(1.375rem, 1rem + 1vw, 1.75rem);
  line-height: 1.5;
  font-weight: 700;
  color: var(--color-text);
}
.section-desc {
  margin: 6px 0 0;
  color: var(--color-text-tertiary);
  font-size: 13px;
}
.section-more {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  gap: 10px;
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}
.project-grid,
.app-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}
.value-section {
  padding-block: 36px;
  border-block: 1px solid var(--color-border);
}
.value-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 36px;
  margin-top: 28px;
}
.value-item {
  display: flex;
  gap: 16px;
}
.value-icon {
  color: var(--color-primary);
  font-size: 26px;
  margin-top: 3px;
}
.value-item h3 {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 650;
  line-height: 1.5;
}
.value-item p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.8;
}
.skeleton-card {
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-surface);
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
  padding: 44px 24px;
  border: 1px dashed var(--color-border);
  border-radius: 12px;
  background: var(--color-surface);
  text-align: center;
}
.empty-icon {
  font-size: 32px;
  color: var(--color-primary);
  margin-bottom: 16px;
}
.empty-state h3 {
  margin: 0;
  font-size: 17px;
  line-height: 1.6;
}
.empty-state p {
  margin: 8px 0 20px;
  color: var(--color-text-secondary);
  font-size: 13px;
}
.cta-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 24px;
  padding: 36px 40px;
  border-radius: 14px;
  background: var(--color-primary-soft);
}
.cta-band h2 {
  margin: 0 0 8px;
  font-size: 24px;
  color: var(--color-primary-strong);
}
.cta-band p {
  margin: 0;
  font-size: 14px;
  color: var(--color-primary-strong);
}
.cta-link {
  background: var(--color-primary);
  color: white;
}
.cta-link:hover {
  background: var(--color-primary-strong);
  color: white;
}
@media (max-width: 1000px) {
  .project-grid,
  .app-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .start-panel {
    padding: 24px;
  }
  .hero-section {
    gap: 32px;
  }
}
@media (max-width: 700px) {
  .hero-section {
    grid-template-columns: 1fr;
    padding-block: 40px;
    gap: 32px;
  }
  .hero-title {
    font-size: clamp(2rem, 1rem + 4.5vw, 2.75rem);
  }
  .hero-description {
    font-size: 14px;
  }
  .hero-actions {
    margin-top: 24px;
  }
  .hero-link {
    padding-inline: 18px;
    gap: 8px;
  }
  .start-panel {
    padding: 24px;
  }
  .panel-mark {
    display: none;
  }
  .start-panel h2 {
    font-size: 21px;
    margin-bottom: 12px;
  }
  .category-bar {
    gap: 8px;
    flex-direction: column;
    margin-bottom: 36px;
  }
  .category-chips {
    gap: 0;
  }
  .category-chip {
    padding-inline: 10px;
  }
  .section {
    margin-bottom: 40px;
  }
  .section-header {
    align-items: flex-start;
    gap: 12px;
  }
  .section-desc {
    max-width: 220px;
  }
  .value-grid {
    grid-template-columns: 1fr;
    gap: 24px;
  }
  .cta-band {
    padding: 28px 24px;
  }
  .cta-band h2 {
    font-size: 22px;
    line-height: 1.5;
  }
}
@media (max-width: 520px) {
  .project-grid,
  .app-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
