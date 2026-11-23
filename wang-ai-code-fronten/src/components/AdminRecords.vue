<script setup lang="ts">
defineProps<{ label: string; error?: string }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <a-alert v-if="error" type="error" :message="error" show-icon>
    <template #action><a-button size="small" @click="$emit('retry')">重新加载</a-button></template>
  </a-alert>
  <div v-else class="table-scroll admin-records" role="region" :aria-label="label" tabindex="0">
    <slot />
  </div>
</template>

<style scoped>
.admin-records :deep(.ant-table-wrapper) {
  min-width: 330px;
}

.admin-records :deep(.ant-table-cell) {
  vertical-align: top;
}

.admin-records :deep(.record-note) {
  display: block;
  margin-top: 4px;
  color: var(--color-text-secondary);
  font-size: 0.8rem;
}

.admin-records :deep(.record-note.record-compact) {
  display: none;
}

@media (max-width: 767px) {
  .admin-records :deep(.record-note.record-compact) {
    display: block;
  }
}

.admin-records :deep(.record-primary) {
  font-weight: 700;
  color: var(--color-text);
}

.admin-records :deep(.record-identity) {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.admin-records :deep(.record-actions) {
  display: flex;
  flex-wrap: wrap;
  gap: 1px 12px;
}

.admin-records :deep(.record-actions .ant-btn) {
  min-height: 35px;
}

@media (max-width: 760px) {
  .admin-records :deep(.ant-table-cell) {
    padding: 10px 7px !important;
    font-size: 0.82rem;
  }

  .admin-records :deep(.record-identity) {
    display: block;
  }

  .admin-records :deep(.record-actions .ant-btn) {
    min-height: 40px;
  }
}
</style>
