<script setup lang="ts">
defineProps<{ label: string; error?: string }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <section class="admin-records">
    <search v-if="$slots.filters" class="record-filters" :aria-label="`筛选${label}`">
      <slot name="filters" />
    </search>
    <a-alert v-if="error" class="record-error" type="error" :message="error" show-icon>
      <template #action><a-button size="small" @click="$emit('retry')">重新加载</a-button></template>
    </a-alert>
    <div v-else class="record-table" role="region" :aria-label="label" tabindex="0">
      <slot />
    </div>
    <div v-if="!error && $slots.pagination" class="record-pagination">
      <slot name="pagination" />
    </div>
  </section>
</template>

<style scoped>
.admin-records {
  min-inline-size: 0;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface);
}

.record-filters {
  display: block;
  padding: 16px 18px;
}

.record-filters :deep(.admin-filters) {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px 14px;
}

.record-filters :deep(.admin-filters > .ant-form-item) {
  flex: 1 1 166px;
  min-inline-size: 0;
  max-inline-size: 220px;
  margin: 0;
}

.record-filters :deep(.ant-form-item-label) {
  padding-block-end: 5px;
}

.record-filters :deep(.ant-form-item-label > label) {
  color: var(--color-text-secondary);
  font-size: 0.8125rem;
  font-weight: 600;
}

.record-filters :deep(.admin-filters > .ant-btn) {
  min-inline-size: 90px;
}

.record-table {
  overflow-x: auto;
  border-block-start: 1px solid var(--color-border);
  font-variant-numeric: tabular-nums;
}

.record-table :deep(.ant-table-wrapper) {
  min-inline-size: 330px;
}

.record-table :deep(.ant-table-thead > tr > th),
.record-table :deep(.ant-table-tbody > tr > td) {
  padding: 12px 18px;
  vertical-align: middle;
  overflow-wrap: anywhere;
}

.record-table :deep(.ant-table-thead > tr > th) {
  color: var(--color-text-secondary);
  font-size: 0.8125rem;
  font-weight: 650;
}

.record-table :deep(.ant-table-thead > tr > th::before) {
  display: none;
}

.record-table :deep(.ant-table-tbody > tr:last-child > td) {
  border-block-end: 0;
}

.record-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 18px;
  border-block-start: 1px solid var(--color-border);
}

.record-pagination :deep(.ant-pagination) {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  row-gap: 6px;
}

.record-error {
  margin: 0 18px 16px;
}

.admin-records :deep(.record-note) {
  display: block;
  margin-block-start: 2px;
  color: var(--color-text-secondary);
  font-size: 0.8rem;
  line-height: 1.45;
}

.admin-records :deep(.record-note.record-compact) {
  display: none;
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

.admin-records :deep(.record-identity > div) {
  min-inline-size: 0;
}

.admin-records :deep(.record-identity .ant-avatar) {
  flex: none;
  color: var(--color-text-secondary);
  background: var(--color-surface-soft);
}

.admin-records :deep(.record-actions) {
  display: flex;
  flex-wrap: wrap;
  gap: 1px 12px;
}

.admin-records :deep(.record-actions .ant-btn) {
  min-height: 35px;
}

@media (max-width: 767px) {
  .record-table :deep(.ant-table-thead > tr > th),
  .record-table :deep(.ant-table-tbody > tr > td) {
    padding: 10px 12px;
    font-size: 0.82rem;
  }

  .admin-records :deep(.record-identity) {
    gap: 8px;
  }

  .admin-records :deep(.record-note.record-compact) {
    display: block;
  }

  .admin-records :deep(.record-actions .ant-btn) {
    min-height: 40px;
  }
}

@media (max-width: 520px) {
  .record-filters,
  .record-pagination {
    padding: 12px;
  }

  .record-filters :deep(.admin-filters > .ant-form-item) {
    flex-basis: 100%;
    max-inline-size: none;
  }

  .record-filters :deep(.admin-filters > .ant-btn) {
    inline-size: 100%;
  }

  .record-error {
    margin: 0 12px 12px;
  }
}
</style>
