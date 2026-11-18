<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { userLogout } from '@/api/userController'
import {
  HomeOutlined,
  ShoppingOutlined,
  ThunderboltOutlined,
  ProjectOutlined,
  OrderedListOutlined,
  UserOutlined,
  LogoutOutlined,
  DashboardOutlined,
  DownOutlined,
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const menuItems = computed(() => [
  { path: '/', label: '主页', icon: HomeOutlined },
  { path: '/create', label: '创建应用', icon: ThunderboltOutlined },
  { path: '/project', label: '模板库', icon: ShoppingOutlined },
  ...(loginUserStore.loginUser.id
    ? [
        { path: '/my/projects', label: '我的项目', icon: ProjectOutlined },
        { path: '/my/orders', label: '领取记录', icon: OrderedListOutlined },
      ]
    : []),
])
const isCurrent = (path: string) =>
  route.path === path || (path !== '/' && route.path.startsWith(`${path}/`))

const doLogout = async () => {
  try {
    const res = await userLogout()
    if (res.data.code === 0) {
      loginUserStore.setLoginUser({ userName: '未登录' })
      message.success('退出登录成功')
      await router.push('/user/login')
    } else {
      message.error('退出登录失败，' + res.data.message)
    }
  } catch {
    message.error('退出登录失败，请稍后重试')
  }
}
</script>

<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>
  <a-layout-header class="header">
    <div class="header-inner">
      <RouterLink to="/" class="header-brand" aria-label="百夜应用工坊主页">
        <img class="logo" src="@/assets/logo.png" alt="" width="38" height="38" />
        <span class="site-title">百夜应用工坊</span>
      </RouterLink>
      <nav class="header-nav" aria-label="主导航">
        <RouterLink
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-link"
          :aria-current="isCurrent(item.path) ? 'page' : undefined"
        >
          <component :is="item.icon" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="header-actions">
        <template v-if="loginUserStore.loginUser.id">
          <a-button
            v-if="loginUserStore.loginUser.userRole === 'admin'"
            class="admin-link"
            @click="router.push('/admin/userManage')"
          >
            <DashboardOutlined /> 管理端
          </a-button>
          <a-dropdown placement="bottomRight" :trigger="['click']">
            <button class="profile-trigger" type="button" aria-label="打开账户菜单">
              <a-avatar :src="loginUserStore.loginUser.userAvatar" :size="30">
                {{ loginUserStore.loginUser.userName?.charAt(0) || 'U' }}
              </a-avatar>
              <span class="profile-name">{{ loginUserStore.loginUser.userName || '无名' }}</span>
              <DownOutlined aria-hidden="true" />
            </button>
            <template #overlay>
              <a-menu>
                <a-menu-item @click="router.push('/user/profile')"
                  ><UserOutlined /> 个人中心</a-menu-item
                >
                <a-menu-item @click="router.push('/my/projects')"
                  ><ProjectOutlined /> 我的项目</a-menu-item
                >
                <a-menu-item @click="router.push('/my/orders')"
                  ><OrderedListOutlined /> 领取记录</a-menu-item
                >
                <a-menu-divider />
                <a-menu-item @click="doLogout"><LogoutOutlined /> 退出登录</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
        <template v-else>
          <RouterLink class="login-link" to="/user/login">登录</RouterLink>
          <a-button type="primary" @click="router.push('/user/register')">注册账号</a-button>
        </template>
      </div>
    </div>
  </a-layout-header>
</template>

<style scoped>
.header {
  position: sticky;
  inset-block-start: 0;
  z-index: 100;
  height: auto;
  padding-inline: var(--page-gutter);
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  line-height: 1.5;
}
.header-inner {
  max-width: var(--page-width);
  min-height: 80px;
  margin-inline: auto;
  display: flex;
  align-items: center;
  gap: clamp(24px, 3vw, 48px);
}
.header-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.logo {
  border-radius: 10px;
  object-fit: cover;
}
.site-title {
  font-size: 19px;
  font-weight: 750;
  letter-spacing: -0.02em;
  color: var(--color-text);
}
.header-nav {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.nav-link {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 44px;
  padding-inline: 14px;
  color: var(--color-text-secondary);
  border-radius: 8px;
  white-space: nowrap;
  font-size: 14px;
}
.nav-link:hover {
  color: var(--color-primary);
  background: var(--color-surface-soft);
}
.nav-link[aria-current='page'] {
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
  font-weight: 650;
}
.nav-link[aria-current='page']::after {
  content: '';
  position: absolute;
  bottom: 5px;
  width: 14px;
  height: 2px;
  background: currentColor;
  border-radius: 2px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-inline-start: auto;
  flex-shrink: 0;
}
.header-actions :deep(.ant-btn) {
  min-height: 44px;
}
.login-link {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  color: var(--color-text-secondary);
  font-weight: 600;
}
.profile-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 4px 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
}
.profile-trigger:hover {
  background: var(--color-surface-soft);
}
.profile-name {
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.skip-link {
  position: fixed;
  inset-block-start: 8px;
  inset-inline-start: 16px;
  z-index: 1000;
  padding: 8px 16px;
  background: var(--color-primary);
  color: white;
  border-radius: 8px;
  transform: translateY(-160%);
}
.skip-link:focus {
  transform: translateY(0);
}
@media (max-width: 1080px) {
  .header-inner {
    gap: 20px;
  }
  .nav-link {
    padding-inline: 10px;
  }
  .admin-link {
    display: none;
  }
}
@media (max-width: 1000px) {
  .header-inner {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 0 12px;
    padding-block-start: 10px;
  }
  .header-brand {
    min-height: 48px;
  }
  .site-title {
    font-size: 17px;
  }
  .logo {
    width: 32px;
    height: 32px;
  }
  .header-actions {
    gap: 14px;
  }
  .header-actions :deep(.ant-btn) {
    padding-inline: 12px;
  }
  .header-nav {
    grid-column: 1 / -1;
    grid-row: 2;
    overflow-x: auto;
    scrollbar-width: none;
    padding-block: 4px 10px;
  }
  .nav-link {
    flex-shrink: 0;
    min-height: 44px;
    padding-inline: 14px;
  }
  .profile-name {
    display: none;
  }
  .header-nav::-webkit-scrollbar {
    display: none;
  }
}
@media (max-width: 360px) {
  .header-brand {
    gap: 7px;
  }
  .header-actions {
    gap: 10px;
  }
  .nav-link {
    padding-inline: 10px;
    gap: 6px;
  }
  .header-actions :deep(.ant-btn) {
    padding-inline: 8px;
    font-size: 13px;
  }
}
</style>
