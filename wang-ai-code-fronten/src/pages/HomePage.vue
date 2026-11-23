<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ArrowRightOutlined, ArrowUpOutlined } from '@ant-design/icons-vue'
import { listGoodAppVoByPage } from '@/api/appController'
import AppCard from '@/components/AppCard.vue'
import StarterTemplateCards from '@/components/StarterTemplateCards.vue'
import folioArtwork from '@/assets/folio.webp'

const featuredApps = ref<API.AppVO[]>([])

onMounted(async () => {
  try {
    const res = await listGoodAppVoByPage({
      pageNum: 1,
      pageSize: 3,
      sortField: 'createTime',
      sortOrder: 'desc',
    })
    if (res.data.code === 0) featuredApps.value = res.data.data?.records || []
  } catch {
    // 精选内容加载失败时，首页的创作和模板入口仍然可用。
  }
})
</script>

<template>
  <div class="page-shell folio-home">
    <section class="hero" aria-labelledby="home-title">
      <div class="hero-copy">
        <h1 id="home-title">把想法，<br /><span>写成应用。</span></h1>
        <p>用一段需求展开一个 Web 项目。边对话边修改，再把源码和论文初稿带走。</p>
        <div class="hero-actions">
          <RouterLink class="ant-btn ant-btn-primary ant-btn-lg hero-button" to="/create">
            开始创作 <ArrowUpOutlined class="diagonal-arrow" aria-hidden="true" />
          </RouterLink>
          <RouterLink class="folio-link" to="/project">
            翻阅模板 <ArrowRightOutlined aria-hidden="true" />
          </RouterLink>
        </div>
      </div>
      <figure class="hero-art">
        <img :src="folioArtwork" alt="" fetchpriority="high" />
      </figure>
    </section>

    <section class="home-section" aria-labelledby="templates-title">
      <header class="section-heading">
        <h2 id="templates-title">模板选集</h2>
        <RouterLink class="folio-link" to="/project">
          全部模板 <ArrowRightOutlined aria-hidden="true" />
        </RouterLink>
      </header>
      <ul class="template-selection">
        <StarterTemplateCards />
      </ul>
    </section>

    <section v-if="featuredApps.length" class="home-section" aria-labelledby="apps-title">
      <header class="section-heading">
        <h2 id="apps-title">精选应用案例</h2>
      </header>
      <ul class="paper-list">
        <AppCard v-for="app in featuredApps" :key="app.id" :app="app" featured />
      </ul>
    </section>
  </div>
</template>

<style scoped>
.folio-home {
  padding-top: clamp(26px, 3.4vw, 54px);
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  align-items: center;
  gap: clamp(26px, 5vw, 80px);
  padding-bottom: clamp(28px, 4.2vw, 60px);
  border-bottom: 2px solid var(--color-text);
}

.hero h1 {
  font-size: clamp(3rem, 5.9vw, 5.75rem);
  line-height: 1.19;
  letter-spacing: -0.06em;
}

.hero h1 span {
  color: var(--color-primary);
}

.hero-copy p {
  max-width: 27em;
  margin: 20px 0 24px;
  color: var(--color-text-secondary);
  line-height: 1.85;
}

.hero-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 22px;
}

.hero-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 650;
  padding-inline: 21px;
  min-height: 46px;
  color: white;
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  border-radius: 2px;
}

.hero-button:hover {
  color: white;
  background: var(--color-primary-strong);
  border-color: var(--color-primary-strong);
}

.diagonal-arrow {
  rotate: 45deg;
}

.hero-art {
  position: relative;
  margin: 0;
  aspect-ratio: 1.62;
}

.hero-art img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.home-section {
  margin-top: 30px;
}

.template-selection {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
  margin: 0;
  padding: 0;
}

@media (max-width: 760px) {
  .hero {
    grid-template-columns: minmax(0, 1.1fr) minmax(0, 0.9fr);
    gap: 16px;
  }

  .hero h1 {
    font-size: clamp(2.4rem, 6.4vw, 3.45rem);
  }

  .template-selection {
    grid-template-columns: minmax(0, 1fr);
    gap: 12px;
  }

  .template-selection :deep(.template-art) {
    height: 70px;
  }
}

@media (max-width: 520px) {
  .hero {
    grid-template-columns: minmax(0, 1fr);
    gap: 23px;
  }

  .hero h1 {
    font-size: clamp(2.8rem, 12vw, 3.875rem);
  }

  .hero-copy p {
    margin: 10px 0 14px;
  }

  .hero-art {
    aspect-ratio: 2.15;
  }

  .home-section {
    margin-top: 24px;
  }
}
</style>
