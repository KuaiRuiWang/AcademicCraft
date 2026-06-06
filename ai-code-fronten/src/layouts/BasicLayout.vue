<script setup lang="ts">
import { Layout } from 'ant-design-vue'
import GlobalHeader from './components/GlobalHeader.vue'
import GlobalFooter from './components/GlobalFooter.vue'
import type { MenuItem } from './components/GlobalHeader.vue'

export type { MenuItem }

interface Props {
  menuItems?: MenuItem[]
  title?: string
}

withDefaults(defineProps<Props>(), {
  menuItems: () => [
    { key: 'home', label: '首页', path: '/' },
    { key: 'about', label: '关于', path: '/about' },
  ],
  title: '百夜AI代码生成平台',
})
</script>

<template>
  <Layout class="basic-layout">
    <GlobalHeader :menu-items="menuItems" :title="title" />
    <Layout.Content class="basic-layout-content">
      <div class="content-wrapper">
        <RouterView />
      </div>
    </Layout.Content>
    <GlobalFooter />
  </Layout>
</template>

<style scoped>
.basic-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.basic-layout-content {
  flex: 1;
  background: #f0f5ff;
}

.content-wrapper {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
  min-height: 100%;
}

@media (max-width: 768px) {
  .content-wrapper {
    padding: 16px;
  }
}

@media (max-width: 480px) {
  .content-wrapper {
    padding: 12px;
  }
}
</style>
