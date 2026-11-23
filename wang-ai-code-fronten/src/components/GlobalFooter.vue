<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useLoginUserStore } from '@/stores/loginUser'

const { loginUser } = storeToRefs(useLoginUserStore())
</script>

<template>
  <footer class="site-footer">
    <RouterLink to="/">百夜应用工坊</RouterLink>
    <nav aria-label="页脚导航">
      <RouterLink v-if="loginUser.id" to="/user/profile">个人资料</RouterLink>
      <RouterLink v-else to="/user/register">注册账号</RouterLink>
      <RouterLink v-if="loginUser.id" to="/my/projects">我的项目</RouterLink>
      <RouterLink v-if="loginUser.userRole === 'admin'" to="/admin/userManage">管理后台</RouterLink>
    </nav>
  </footer>
</template>

<style scoped>
.site-footer {
  width: var(--page-measure);
  margin: 10px auto 0;
  padding: 22px 0 40px;
  border-top: 1px solid var(--color-border);
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 20px;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.site-footer nav {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
}

.site-footer a {
  color: inherit;
}

.site-footer a:hover {
  color: var(--color-primary);
}
</style>
