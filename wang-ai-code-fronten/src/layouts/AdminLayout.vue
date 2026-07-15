<template>
  <a-layout class="admin-layout">
    <!-- 侧边栏 -->
    <a-layout-sider
      v-model:collapsed="collapsed"
      collapsible
      theme="dark"
      :trigger="null"
      :style="{ overflow: 'auto', height: '100vh', position: 'fixed', left: 0, top: 0, bottom: 0, zIndex: 10 }"
    >
      <div class="admin-logo">
        <img src="@/assets/logo.png" alt="Logo" />
        <span v-show="!collapsed" class="admin-logo-title">管理后台</span>
      </div>
      <a-menu
        v-model:selectedKeys="selectedKeys"
        theme="dark"
        mode="inline"
        :items="adminMenuItems"
        @click="handleMenuClick"
      />
    </a-layout-sider>
    <!-- 右侧内容区域 -->
    <a-layout :style="{ marginLeft: collapsed ? '80px' : '200px', transition: 'all 0.2s' }">
      <!-- 顶部栏 -->
      <a-layout-header class="admin-header">
        <div class="admin-header-left">
          <component
            :is="collapsed ? MenuUnfoldOutlined : MenuFoldOutlined"
            class="trigger"
            @click="collapsed = !collapsed"
          />
          <span class="admin-header-title">管理后台</span>
        </div>
        <div class="admin-header-right">
          <a-button type="link" @click="router.push('/')">
            <template #icon><HomeOutlined /></template>
            返回用户端
          </a-button>
        </div>
      </a-layout-header>
      <!-- 内容区域 -->
      <a-layout-content class="admin-main-content">
        <div class="admin-content-wrapper">
          <router-view />
        </div>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { h, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import type { MenuProps } from 'ant-design-vue'
import {
  HomeOutlined,
  TeamOutlined,
  AppstoreOutlined,
  MessageOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons-vue'

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const selectedKeys = ref<string[]>([route.path])

// 监听路由变化更新选中菜单
watch(
  () => route.path,
  (path) => {
    selectedKeys.value = [path]
  }
)

// 管理端菜单配置
const adminMenuItems: MenuProps['items'] = [
  {
    key: '/admin/userManage',
    icon: () => h(TeamOutlined),
    label: '用户管理',
  },
  {
    key: '/admin/appManage',
    icon: () => h(AppstoreOutlined),
    label: '应用管理',
  },
  {
    key: '/admin/chatManage',
    icon: () => h(MessageOutlined),
    label: '对话管理',
  },
]

// 处理菜单点击
const handleMenuClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  if (key.startsWith('/')) {
    router.push(key)
  }
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
  background: #f0f2f5;
}

.admin-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 64px;
  line-height: 64px;
  padding: 0 16px;
  background: rgba(255, 255, 255, 0.08);
  overflow: hidden;
  white-space: nowrap;
}

.admin-logo img {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
}

.admin-logo-title {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
}

.admin-header {
  background: #fff;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  z-index: 9;
  height: 64px;
  line-height: 64px;
}

.admin-header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.trigger {
  font-size: 18px;
  cursor: pointer;
  transition: color 0.3s;
  color: #595959;
}

.trigger:hover {
  color: #1890ff;
}

.admin-header-title {
  font-size: 16px;
  font-weight: 500;
  color: #262626;
}

.admin-header-right {
  display: flex;
  align-items: center;
}

.admin-main-content {
  margin: 24px;
  min-height: calc(100vh - 64px - 48px);
}

.admin-content-wrapper {
  background: #fff;
  padding: 24px;
  border-radius: 4px;
  min-height: calc(100vh - 64px - 96px);
}

/* 覆盖 Ant Design 默认侧边栏样式 */
:deep(.ant-layout-sider) {
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
}

:deep(.ant-layout-sider .ant-layout-sider-children) {
  display: flex;
  flex-direction: column;
}

/* 菜单项样式微调 */
:deep(.ant-menu-dark) {
  margin-top: 4px;
}
</style>
