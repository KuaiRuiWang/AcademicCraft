<script setup lang="ts">
import { ArrowUpOutlined } from '@ant-design/icons-vue'
import TemplateArtwork from '@/components/TemplateArtwork.vue'

defineProps<{
  to: string
  title?: string
  category?: string
  description?: string
  illustration?: string
  cover?: string
}>()
</script>

<template>
  <li class="template-entry">
    <RouterLink :to="to" class="template-link">
      <TemplateArtwork :cover="cover" :illustration="illustration" />
      <div class="template-copy">
        <div>
          <span class="template-category">{{ category || '应用模板' }}</span>
          <h3>{{ title || '未命名模板' }}</h3>
        </div>
        <p>{{ description || '暂无模板介绍' }}</p>
      </div>
      <ArrowUpOutlined class="entry-arrow" aria-hidden="true" />
    </RouterLink>
  </li>
</template>

<style scoped>
.template-entry {
  min-width: 0;
  list-style: none;
  border-bottom: 1px solid var(--color-border);
}

.template-entry:hover {
  border-bottom-color: var(--color-primary);
}

.template-link {
  display: grid;
  grid-template-columns: 128px minmax(0, 1fr) auto;
  align-items: center;
  gap: 22px;
  min-width: 0;
  padding: 15px 0;
  color: var(--color-text);
}

.template-copy {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 2fr);
  align-items: center;
  gap: 16px 22px;
}

.template-category,
.template-copy p {
  color: var(--color-text-secondary);
  overflow-wrap: anywhere;
}

.template-category {
  display: block;
  margin-bottom: 4px;
  font-size: 0.76rem;
}

.template-copy h3 {
  font-size: 1.12rem;
}

.template-copy p {
  margin: 0;
  font-size: 0.875rem;
}

.entry-arrow {
  margin-right: 8px;
  font-size: 1.15rem;
  color: var(--color-primary);
  rotate: 45deg;
}

.template-selection .template-link {
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 15px;
  padding: 0 0 18px;
}

.template-selection .template-copy {
  display: block;
}

.template-selection .template-copy p {
  margin-top: 4px;
}

.template-selection .entry-arrow {
  display: none;
}

.template-selection .template-art {
  height: 92px;
}

@media (max-width: 1000px) {
  .template-selection .template-link {
    grid-template-columns: 82px minmax(0, 1fr);
    gap: 12px;
  }
}

@media (max-width: 600px) {
  .template-link {
    grid-template-columns: 76px minmax(0, 1fr) auto;
    gap: 12px;
  }

  .template-copy {
    display: block;
  }

  .template-copy p {
    margin-top: 4px;
  }

  .template-link .template-art {
    height: 72px;
  }

  .entry-arrow {
    margin-right: 2px;
  }

  .template-selection .template-link {
    padding-bottom: 12px;
  }
}
</style>
