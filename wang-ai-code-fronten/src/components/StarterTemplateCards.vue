<script setup lang="ts">
import { starterTemplates } from '@/config/starterTemplates'
</script>

<template>
  <p class="template-license">
    百夜应用工坊原创模板，采用 MIT 许可。商用和再分发时须保留版权及许可声明。
  </p>
  <div class="template-grid">
    <article v-for="template in starterTemplates" :key="template.id" class="template-card">
      <h3>{{ template.name }}</h3>
      <p>{{ template.description }}</p>
      <a-space wrap>
        <a-button :href="template.url" target="_blank" rel="noopener">查看演示</a-button>
        <a-button :href="template.url" :download="`${template.id}.html`">下载源码</a-button>
      </a-space>
    </article>
  </div>
</template>

<style scoped>
.template-license {
  margin: 0 0 18px;
  color: var(--color-text-secondary);
}
.template-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.template-card {
  padding: 22px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-surface);
}
.template-card h3 {
  margin: 0 0 8px;
  font-size: 18px;
}
.template-card p {
  margin: 0 0 18px;
  color: var(--color-text-secondary);
}
@media (max-width: 740px) {
  .template-grid {
    grid-template-columns: 1fr;
  }
}
</style>
