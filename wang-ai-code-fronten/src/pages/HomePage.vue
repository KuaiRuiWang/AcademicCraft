<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRightOutlined,
  CodeOutlined,
  CloudUploadOutlined,
  ThunderboltOutlined,
  AppstoreOutlined,
} from '@ant-design/icons-vue'
import { listGoodAppVoByPage } from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import { starterTemplates } from '@/config/starterTemplates'
import AppCard from '@/components/AppCard.vue'
import StarterTemplateCards from '@/components/StarterTemplateCards.vue'

const router = useRouter()
const featuredApps = ref<API.AppVO[]>([])
const featuredAppsLoading = ref(false)

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
onMounted(loadFeaturedApps)
</script>

<template>
  <div id="homePage">
    <div class="container">
      <section class="hero-section" aria-labelledby="home-title">
        <div class="hero-copy">
          <h1 id="home-title" class="hero-title">
            从一个想法，<br /><span class="accent">做出可运行的应用</span>
          </h1>
          <p class="hero-description">描述你的需求，生成可预览、可修改的 Web 应用。</p>
          <div class="hero-actions">
            <RouterLink to="/create" class="hero-link primary-link">
              <ThunderboltOutlined aria-hidden="true" /> 创建应用
              <ArrowRightOutlined aria-hidden="true" />
            </RouterLink>
            <RouterLink to="/project" class="hero-link secondary-link">
              <AppstoreOutlined aria-hidden="true" /> 浏览模板
            </RouterLink>
          </div>
        </div>
        <aside class="start-panel" aria-label="应用场景示例">
          <CodeOutlined class="panel-mark" aria-hidden="true" />
          <h2>从这些场景开始</h2>
          <RouterLink
            v-for="template in starterTemplates"
            :key="template.id"
            :to="{ path: '/create', query: { template: template.id } }"
            class="path-option"
          >
            <ThunderboltOutlined class="path-icon" aria-hidden="true" />
            <span
              ><strong>{{ template.name }}</strong
              ><small>{{ template.description }}</small></span
            >
            <ArrowRightOutlined aria-hidden="true" />
          </RouterLink>
          <div class="panel-stack"><span>Vue</span><span>Node.js</span><span>MySQL</span></div>
        </aside>
      </section>

      <section class="section" aria-labelledby="templates-title">
        <div class="section-header">
          <h2 id="templates-title" class="section-title">入门模板</h2>
          <RouterLink to="/project" class="section-more">
            打开模板库 <ArrowRightOutlined aria-hidden="true" />
          </RouterLink>
        </div>
        <StarterTemplateCards />
      </section>

      <section class="section value-section" aria-labelledby="values-title">
        <h2 id="values-title" class="section-title">从生成到部署</h2>
        <div class="value-grid">
          <div class="value-item">
            <ThunderboltOutlined class="value-icon" aria-hidden="true" />
            <div>
              <h3>对话生成应用</h3>
              <p>描述需求，生成网站或全栈应用</p>
            </div>
          </div>
          <div class="value-item">
            <CodeOutlined class="value-icon" aria-hidden="true" />
            <div>
              <h3>导出应用源码</h3>
              <p>下载生成的源码，在本地运行和修改</p>
            </div>
          </div>
          <div class="value-item">
            <CloudUploadOutlined class="value-icon" aria-hidden="true" />
            <div>
              <h3>部署并分享</h3>
              <p>部署应用后，通过链接分享可运行的版本</p>
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
@media (max-width: 1000px) {
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
  .section {
    margin-bottom: 40px;
  }
  .section-header {
    align-items: flex-start;
    gap: 12px;
  }
  .value-grid {
    grid-template-columns: 1fr;
    gap: 24px;
  }
}
@media (max-width: 520px) {
  .app-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
