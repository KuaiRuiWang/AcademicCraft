<script setup lang="ts">
const pages = [
  { to: '/admin/userManage', label: '用户管理' },
  { to: '/admin/appManage', label: '应用管理' },
  { to: '/admin/chatManage', label: '对话管理' },
  { to: '/admin/projectManage', label: '模板管理' },
  { to: '/admin/categoryManage', label: '分类管理' },
]
</script>

<template>
  <div class="page-shell admin-workspace">
    <nav class="folio-tabs admin-tabs" aria-label="管理栏目">
      <RouterLink v-for="page in pages" :key="page.to" :to="page.to">
        {{ page.label }}
      </RouterLink>
    </nav>
    <RouterView />
  </div>
</template>

<style scoped>
.admin-tabs {
  margin-bottom: clamp(24px, 3vw, 36px);
}

.admin-workspace :deep(.admin-filters) {
  margin-bottom: 20px;
  row-gap: 10px;
  align-items: flex-end;
}

.admin-workspace :deep(.admin-filters .ant-select) {
  min-width: 140px;
}

.admin-workspace :deep(.ant-table) {
  background: transparent;
}

.admin-workspace :deep(.ant-table-container) {
  border-block-start: 1px solid var(--color-text);
}

.admin-workspace :deep(.ant-table-cell) {
  overflow-wrap: anywhere;
}

.admin-workspace :deep(.ant-table-thead > tr > th) {
  border-block-end: 1px solid var(--color-text);
}

.admin-workspace :deep(.ant-table-thead > tr > th::before) {
  display: none;
}

@media (max-width: 600px) {
  .admin-tabs {
    column-gap: 8px;
    font-size: 0.875rem;
  }

  .admin-workspace :deep(.admin-filters .ant-form-item) {
    flex: 1 1 135px;
    margin-inline-end: 0;
  }

  .admin-workspace :deep(.admin-filters) {
    column-gap: 12px;
  }
}
</style>
