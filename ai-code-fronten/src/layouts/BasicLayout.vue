<script setup lang="ts">
import { computed } from 'vue'
import { Layout } from 'ant-design-vue'
import GlobalHeader from './components/GlobalHeader.vue'
import GlobalFooter from './components/GlobalFooter.vue'
import type { MenuItem } from './components/GlobalHeader.vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'

export type { MenuItem }

const loginUserStore = useLoginUserStore()

/** 基础菜单项（所有用户可见） */
const baseMenuItems: MenuItem[] = [
  { key: 'home', label: '首页', path: '/' },
  { key: 'about', label: '关于', path: '/about' },
]

/** 管理员菜单项（仅 admin 可见） */
const adminMenuItems: MenuItem[] = [
  { key: 'userManage', label: '用户管理', path: '/admin/userManage' },
]

/** 根据用户角色过滤后的菜单项 */
const filteredMenuItems = computed<MenuItem[]>(() => {
  if (loginUserStore.loginUser.userRole === 'admin') {
    return [...baseMenuItems, ...adminMenuItems]
  }
  return baseMenuItems
})

interface Props {
  title?: string
}

withDefaults(defineProps<Props>(), {
  title: '百夜AI代码生成平台',
})
</script>

<template>
  <Layout class="basic-layout">
    <GlobalHeader :menu-items="filteredMenuItems" :title="title" />
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
