<script setup lang="ts">
import { ArrowUpOutlined, ExportOutlined } from '@ant-design/icons-vue'
import { getDeployUrl } from '@/config/env'
import { formatCodeGenType } from '@/utils/codeGenTypes'

defineProps<{ app: API.AppVO; featured?: boolean }>()
</script>

<template>
  <li class="app-entry">
    <RouterLink
      :to="{ path: '/app/chat/' + app.id, query: featured ? { view: '1' } : {} }"
      class="app-entry-link"
    >
      <strong>{{ app.appName || '未命名应用' }}</strong>
      <span class="app-type">{{ formatCodeGenType(app.codeGenType) }}</span>
      <ArrowUpOutlined class="app-entry-arrow" aria-hidden="true" />
    </RouterLink>
    <a
      v-if="app.deployKey"
      class="deployed-link"
      :href="getDeployUrl(app.deployKey, app.codeGenType)"
      target="_blank"
      rel="noopener"
    >
      打开作品 <ExportOutlined aria-hidden="true" />
    </a>
  </li>
</template>

<style scoped>
.app-entry {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0 22px;
}

.app-entry-link {
  display: flex;
  flex: 1 1 14rem;
  min-width: 0;
  align-items: center;
  gap: 12px;
  min-height: 62px;
  padding: 10px 4px;
  color: var(--color-text);
}

.app-entry-link strong {
  margin-inline-end: auto;
  font: 700 1.12rem/1.5 var(--font-display);
  overflow-wrap: anywhere;
}

.app-type,
.deployed-link {
  font-size: 0.83rem;
}

.app-type {
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.app-entry-arrow {
  rotate: 45deg;
}

.app-entry-link:hover strong {
  color: var(--color-primary);
}

.deployed-link {
  display: inline-flex;
  align-items: center;
  min-height: 36px;
  gap: 6px;
  padding-inline: 4px;
}

@media (max-width: 520px) {
  .app-entry-link {
    gap: 8px;
  }

  .deployed-link {
    margin-inline-start: auto;
  }
}
</style>
