<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Menu, Button, Layout } from 'ant-design-vue'
import type { MenuProps } from 'ant-design-vue'
import logoImg from '@/assets/logo.png'

export interface MenuItem {
  key: string
  label: string
  path: string
  icon?: string
}

interface Props {
  menuItems?: MenuItem[]
  title?: string
}

const props = withDefaults(defineProps<Props>(), {
  menuItems: () => [
    { key: 'home', label: '首页', path: '/' },
    { key: 'about', label: '关于', path: '/about' },
  ],
  title: '百夜AI代码生成平台',
})

const router = useRouter()
const route = useRoute()

const selectedKeys = computed<string[]>(() => {
  const current = props.menuItems.find((item) => item.path === route.path)
  return current ? [current.key] : []
})

const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
  const menuItem = props.menuItems.find((item) => item.key === key)
  if (menuItem) {
    router.push(menuItem.path)
  }
}

const handleLogin = () => {
  // TODO: 实现登录逻辑
  console.log('登录')
}
</script>

<template>
  <Layout.Header class="global-header">
    <div class="header-left">
      <img :src="logoImg" alt="logo" class="logo" />
      <span class="site-title">{{ title }}</span>
    </div>

    <Menu
      v-model:selectedKeys="selectedKeys"
      mode="horizontal"
      theme="light"
      class="header-menu"
      :items="menuItems.map((item) => ({ key: item.key, label: item.label }))"
      @click="handleMenuClick"
    />

    <div class="header-right">
      <Button type="primary" size="small" @click="handleLogin">
        登录
      </Button>
    </div>
  </Layout.Header>
</template>

<style scoped>
.global-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  border-bottom: 1px solid #f0f0f0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.logo {
  width: 36px;
  height: 36px;
  object-fit: contain;
}

.site-title {
  color: #1677ff;
  font-size: 18px;
  font-weight: 700;
  white-space: nowrap;
}

.header-menu {
  flex: 1;
  display: flex;
  justify-content: center;
  border-bottom: none;
  background: transparent;
  min-width: 0;
}

.header-menu :deep(.ant-menu-item) {
  color: rgba(0, 0, 0, 0.65);
}

.header-menu :deep(.ant-menu-item-selected) {
  color: #1677ff;
}

.header-menu :deep(.ant-menu-item:hover) {
  color: #1677ff;
}

.header-menu :deep(.ant-menu-horizontal) {
  border-bottom: none;
}

.header-right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

@media (max-width: 768px) {
  .global-header {
    padding: 0 12px;
    flex-wrap: wrap;
  }

  .site-title {
    font-size: 14px;
  }

  .header-menu {
    order: 3;
    flex-basis: 100%;
    justify-content: flex-start;
  }
}

@media (max-width: 480px) {
  .logo {
    width: 28px;
    height: 28px;
  }

  .site-title {
    font-size: 12px;
  }
}
</style>
