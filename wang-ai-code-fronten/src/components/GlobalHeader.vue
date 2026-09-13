<template>
  <a-layout-header class="header">
    <a-row :wrap="false" align="middle" class="header-inner">
      <!-- 左侧：Logo和标题 -->
      <a-col flex="200px">
        <RouterLink to="/">
          <div class="header-left">
            <img class="logo" src="@/assets/logo.png" alt="Logo" />
            <h1 class="site-title">百夜毕设帮</h1>
          </div>
        </RouterLink>
      </a-col>
      <!-- 中间：导航菜单 -->
      <a-col flex="auto">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          mode="horizontal"
          :items="menuItems"
          class="header-menu"
          @click="handleMenuClick"
        />
      </a-col>
      <!-- 右侧：用户操作区域 -->
      <a-col>
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser.id" class="user-actions">
            <a-button
              v-if="loginUserStore.loginUser.userRole === 'admin'"
              type="primary"
              size="small"
              class="admin-switch-btn"
              @click="router.push('/admin/userManage')"
            >
              <template #icon><DashboardOutlined /></template>
              管理端
            </a-button>
            <a-dropdown placement="bottomRight">
              <a-space class="user-avatar-area" @click.stop="goToProfile">
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
                {{ loginUserStore.loginUser.userName ?? '无名' }}
              </a-space>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="goToProfile">
                    <UserOutlined />
                    个人中心
                  </a-menu-item>
                  <a-menu-item @click="router.push('/my/projects')">
                    <ProjectOutlined />
                    我的项目
                  </a-menu-item>
                  <a-menu-item @click="router.push('/my/orders')">
                    <OrderedListOutlined />
                    我的订单
                  </a-menu-item>
                  <a-menu-divider />
                  <a-menu-item @click="doLogout">
                    <LogoutOutlined />
                    退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <div v-else>
            <a-button type="primary" shape="round" href="/user/login">登录</a-button>
          </div>
        </div>
      </a-col>
    </a-row>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRouter } from 'vue-router'
import { type MenuProps, message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { userLogout } from '@/api/userController.ts'
import {
  LogoutOutlined,
  HomeOutlined,
  UserOutlined,
  ShoppingOutlined,
  ProjectOutlined,
  OrderedListOutlined,
  DashboardOutlined,
} from '@ant-design/icons-vue'

const loginUserStore = useLoginUserStore()
const router = useRouter()
// 当前选中菜单
const selectedKeys = ref<string[]>(['/'])
// 监听路由变化，更新当前选中菜单
router.afterEach((to) => {
  selectedKeys.value = [to.path]
})
// 菜单配置项
const originItems = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '主页',
    title: '主页',
  },
  {
    key: '/project',
    icon: () => h(ShoppingOutlined),
    label: '项目商城',
    title: '项目商城',
  },
  {
    key: '/my/projects',
    icon: () => h(ProjectOutlined),
    label: '我的项目',
    title: '我的项目',
  },
  {
    key: '/my/orders',
    icon: () => h(OrderedListOutlined),
    label: '我的订单',
    title: '我的订单',
  },
]
// 过滤菜单项
const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    const menuKey = menu?.key as string
    if (menuKey?.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    // 我的项目和我的订单需要登录
    if (menuKey?.startsWith('/my/')) {
      if (!loginUserStore.loginUser.id) {
        return false
      }
    }
    return true
  })
}

// 展示在菜单的路由数组
const menuItems = computed<MenuProps['items']>(() => filterMenus(originItems))

// 处理菜单点击
const handleMenuClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  selectedKeys.value = [key]
  // 跳转到对应页面
  if (key.startsWith('/')) {
    router.push(key)
  }
}

// 跳转个人中心
const goToProfile = () => {
  router.push('/user/profile')
}

// 退出登录
const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(231, 229, 228, 0.9);
  padding: 0 24px;
  height: 68px;
  line-height: 68px;
}

.header-inner {
  height: 68px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  height: 40px;
  width: 40px;
  border-radius: 10px;
  object-fit: cover;
}

.site-title {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--color-primary);
}

/* 水平菜单：pill 选中态，去掉 antd 默认下划线 */
.header-menu {
  background: transparent;
  border-bottom: none !important;
  font-weight: 500;
}

:deep(.ant-menu-horizontal .ant-menu-item),
:deep(.ant-menu-horizontal .ant-menu-submenu) {
  border-radius: 999px;
  padding-inline: 16px;
  transition:
    color 0.25s var(--ease-out-expo),
    background-color 0.25s var(--ease-out-expo);
}

:deep(.ant-menu-horizontal .ant-menu-item::after),
:deep(.ant-menu-horizontal .ant-menu-submenu::after) {
  display: none;
}

:deep(.ant-menu-horizontal .ant-menu-item:hover) {
  background-color: var(--color-surface-soft);
}

:deep(.ant-menu-horizontal .ant-menu-item-selected) {
  background-color: rgba(15, 118, 110, 0.1);
  color: var(--color-primary);
}

.user-avatar-area {
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 999px;
  font-weight: 500;
  transition: background-color 0.25s var(--ease-out-expo);
}

.user-avatar-area:hover {
  background-color: var(--color-surface-soft);
}

.user-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-switch-btn {
  font-size: 13px;
  border-radius: 999px;
}
</style>
