<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import BasicLayout from '@/layouts/BasicLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import { themeConfig } from '@/config/theme'

const route = useRoute()

// 根据路由 meta 判断使用哪个布局
const isAdminRoute = computed(() => route.meta?.layout === 'admin')
</script>

<template>
  <a-config-provider :theme="themeConfig">
    <AdminLayout v-if="isAdminRoute" />
    <BasicLayout v-else />
  </a-config-provider>
</template>

<style>
/* 全局样式优化 */
* {
  box-sizing: border-box;
}

body {
  margin: 0;
  padding: 0;
  font-family:
    'Plus Jakarta Sans Variable', 'PingFang SC', 'Microsoft YaHei', -apple-system,
    BlinkMacSystemFont, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  overflow-x: hidden;
}

html {
  overflow-x: hidden;
}
</style>
