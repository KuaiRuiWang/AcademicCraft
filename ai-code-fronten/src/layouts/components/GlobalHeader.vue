<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Button, Layout, Dropdown, message } from 'ant-design-vue'
import logoImg from '@/assets/logo.png'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { userLogout } from '@/api/userController.ts'

const loginUserStore = useLoginUserStore()
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

const handleMenuClick = (item: MenuItem) => {
  router.push(item.path)
}

const handleLogin = () => {
  router.push('/user/login')
}

/**
 * 退出登录
 */
const handleLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({ userName: '未登录' })
    message.success('已退出登录')
    router.push({
      path: '/',
      replace: true,
    })
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<template>
  <Layout.Header class="global-header">
    <div class="header-left">
      <img :src="logoImg" alt="logo" class="logo" />
      <span class="site-title">{{ title }}</span>
    </div>

    <nav class="header-nav">
      <span
        v-for="item in menuItems"
        :key="item.key"
        class="nav-item"
        :class="{ active: selectedKeys.includes(item.key) }"
        @click="handleMenuClick(item)"
      >
        {{ item.label }}
      </span>
    </nav>

    <div class="header-right">
      <div class="user-login-status">
        <div v-if="loginUserStore.loginUser.id">
          <Dropdown placement="bottomCenter">
            <div class="user-avatar-area">
              <a-avatar :src="loginUserStore.loginUser.userAvatar" />
              <span class="user-name">{{ loginUserStore.loginUser.userName ?? '无名' }}</span>
            </div>
            <template #overlay>
              <a-menu class="user-dropdown-menu">
                <a-menu-item key="logout" @click="handleLogout">
                  退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </Dropdown>
        </div>
        <div v-else>
          <a-button type="primary" href="/user/login">登录</a-button>
        </div>
      </div>
    </div>
  </Layout.Header>
</template>

<style scoped>
.global-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  background: linear-gradient(135deg, #ffffff 0%, #f8faff 100%);
  box-shadow: 0 2px 12px rgba(22, 119, 255, 0.08);
  border-bottom: 1px solid rgba(22, 119, 255, 0.1);
  height: 64px;
}

/* ===== 左侧 Logo + 标题 ===== */
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  margin-right: 32px;
}

.logo {
  width: 38px;
  height: 38px;
  object-fit: contain;
  border-radius: 8px;
  filter: drop-shadow(0 2px 4px rgba(22, 119, 255, 0.2));
}

.site-title {
  color: #1677ff;
  font-size: 17px;
  font-weight: 700;
  white-space: nowrap;
  letter-spacing: 0.5px;
  background: linear-gradient(135deg, #1677ff, #4096ff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* ===== 导航菜单（紧跟 Logo 左侧区域） ===== */
.header-nav {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 4px;
  flex: 1;
}

.nav-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  padding: 6px 18px;
  font-size: 14px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.6);
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.25s ease;
  white-space: nowrap;
  user-select: none;
}

.nav-item::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 50%;
  transform: translateX(-50%) scaleX(0);
  width: 60%;
  height: 2px;
  background: linear-gradient(90deg, #1677ff, #4096ff);
  border-radius: 2px;
  transition: transform 0.25s ease;
}

.nav-item:hover {
  color: #1677ff;
  background: rgba(22, 119, 255, 0.06);
}

.nav-item.active {
  color: #1677ff;
  background: rgba(22, 119, 255, 0.08);
  font-weight: 600;
}

.nav-item.active::after {
  transform: translateX(-50%) scaleX(1);
}

/* ===== 右侧用户区域 ===== */
.header-right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.user-login-status {
  display: flex;
  align-items: center;
}

.user-avatar-area {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.user-avatar-area:hover .user-name {
  color: #1677ff;
}

.user-name {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.65);
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.2s;
}

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .global-header {
    padding: 0 16px;
  }

  .header-left,
  .header-right {
    flex: 0 0 auto;
  }

  .nav-item {
    padding: 6px 12px;
    font-size: 13px;
  }

  .site-title {
    font-size: 14px;
  }
}

@media (max-width: 600px) {
  .site-title {
    display: none;
  }

  .nav-item {
    padding: 5px 10px;
    font-size: 13px;
  }

  .logo {
    width: 32px;
    height: 32px;
  }
}
</style>

<style>
.user-dropdown-menu {
  min-width: 110px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  padding: 4px;
  border: 1px solid rgba(0, 0, 0, 0.06);
}

.user-dropdown-menu .ant-menu-item {
  border-radius: 6px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.65);
  margin: 0;
}

.user-dropdown-menu .ant-menu-item:hover {
  background: rgba(22, 119, 255, 0.06);
  color: #1677ff;
}
</style>
