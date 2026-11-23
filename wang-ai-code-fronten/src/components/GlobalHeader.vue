<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CloseOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { userLogout } from '@/api/userController'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const navigation = ref<HTMLDialogElement | null>(null)
const menuOpen = ref(false)

const pages = [
  { to: '/', label: '首页' },
  { to: '/create', label: '创建应用' },
  { to: '/project', label: '模板目录' },
]
const personalPages = [
  { to: '/user/profile', label: '个人资料' },
  { to: '/my/projects', label: '我的项目' },
  { to: '/my/orders', label: '领取记录' },
]

const isCurrent = (path: string) =>
  route.path === path || (path !== '/' && route.path.startsWith(path + '/'))

function openNavigation() {
  navigation.value?.showModal()
  menuOpen.value = true
}

function closeNavigation() {
  navigation.value?.close()
}

async function doLogout() {
  try {
    const res = await userLogout()
    if (res.data.code === 0) {
      loginUserStore.setLoginUser({ userName: '未登录' })
      closeNavigation()
      message.success('已退出登录')
      await router.push('/user/login')
    } else {
      message.error(res.data.message || '退出登录失败')
    }
  } catch {
    message.error('退出登录失败，请稍后重试')
  }
}
</script>

<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>
  <header class="site-header">
    <div class="header-inner">
      <RouterLink class="brand" to="/" aria-label="百夜应用工坊首页">
        <span class="brand-mark" aria-hidden="true" />
        百夜应用工坊
      </RouterLink>

      <nav class="primary-nav" aria-label="主导航">
        <RouterLink
          v-for="page in pages"
          :key="page.to"
          :to="page.to"
          :aria-current="isCurrent(page.to) ? 'page' : undefined"
        >
          {{ page.label }}
        </RouterLink>
      </nav>

      <div class="header-end">
        <RouterLink v-if="loginUserStore.loginUser.id" class="account-link" to="/user/profile">
          {{ loginUserStore.loginUser.userName || '个人资料' }}
        </RouterLink>
        <RouterLink v-else class="account-link" to="/user/login">登录</RouterLink>

        <button
          type="button"
          class="nav-toggle"
          aria-controls="site-navigation"
          aria-haspopup="dialog"
          :aria-expanded="menuOpen"
          @click="openNavigation"
        >
          <span class="menu-lines" aria-hidden="true" />导航
        </button>
      </div>
    </div>

    <dialog
      id="site-navigation"
      ref="navigation"
      class="navigation-sheet"
      aria-label="站点导航"
      @close="menuOpen = false"
      @click.self="closeNavigation"
    >
      <div class="navigation-content">
        <button type="button" class="nav-close" aria-label="关闭导航" @click="closeNavigation">
          <CloseOutlined aria-hidden="true" />
        </button>
        <nav aria-label="站点导航">
          <div class="navigation-group">
            <span>工坊</span>
            <RouterLink
              v-for="page in pages"
              :key="page.to"
              :to="page.to"
              :aria-current="isCurrent(page.to) ? 'page' : undefined"
              @click="closeNavigation"
            >
              {{ page.label }}
            </RouterLink>
          </div>
          <div class="navigation-group">
            <span>个人区</span>
            <template v-if="loginUserStore.loginUser.id">
              <RouterLink
                v-for="page in personalPages"
                :key="page.to"
                :to="page.to"
                @click="closeNavigation"
              >
                {{ page.label }}
              </RouterLink>
              <RouterLink
                v-if="loginUserStore.loginUser.userRole === 'admin'"
                to="/admin/userManage"
                @click="closeNavigation"
              >
                管理后台
              </RouterLink>
              <button type="button" @click="doLogout">退出登录</button>
            </template>
            <template v-else>
              <RouterLink to="/user/login" @click="closeNavigation">登录</RouterLink>
              <RouterLink to="/user/register" @click="closeNavigation">注册账号</RouterLink>
            </template>
          </div>
        </nav>
      </div>
    </dialog>
  </header>
</template>

<style scoped>
.site-header {
  border-bottom: 1px solid var(--color-text);
}

.header-inner {
  width: var(--page-measure);
  min-height: var(--header-height);
  margin-inline: auto;
  display: flex;
  align-items: center;
  gap: clamp(24px, 7.7vw, 110px);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--color-text);
  font-family: var(--font-display);
  font-size: 1.27rem;
  font-weight: 760;
  white-space: nowrap;
}

.brand-mark {
  position: relative;
  flex: 0 0 34px;
  height: 34px;
  background: var(--color-primary);
  color: var(--color-bg);
  border-radius: 2px 13px 2px 2px;
}

.brand-mark::before {
  content: '';
  position: absolute;
  width: 55%;
  height: 55%;
  inset: 21% auto auto 23%;
  border-radius: 50%;
  box-shadow: -4px 3px 0 currentColor;
  transform: rotate(-28deg);
}

.primary-nav,
.header-end {
  display: flex;
  align-items: center;
}

.primary-nav {
  gap: clamp(16px, 2vw, 32px);
}

.primary-nav a {
  display: grid;
  align-items: center;
  min-height: 36px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.primary-nav [aria-current='page'] {
  color: var(--color-primary);
  box-shadow: inset 0 -2px var(--color-primary);
}

.header-end {
  gap: 12px;
  margin-inline-start: auto;
  min-width: 0;
  white-space: nowrap;
}

.account-link {
  max-width: min(13ch, 18vw);
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--color-text-secondary);
}

.nav-toggle,
.navigation-group button,
.nav-close {
  font: inherit;
  border: 0;
  color: var(--color-text);
  background: transparent;
  cursor: pointer;
}

.nav-toggle {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 44px;
  padding: 0 8px 0 16px;
  position: relative;
}

.nav-toggle::before {
  content: '';
  position: absolute;
  inset: 5px auto 5px 0;
  border-inline-start: 1px solid var(--color-border);
}

.menu-lines {
  width: 17px;
  height: 9px;
  border-block: 1px solid;
}

.navigation-sheet {
  position: fixed;
  inset: calc(var(--header-height) + 10px) max(var(--page-gutter), (100% - var(--page-width)) / 2)
    auto auto;
  width: min(340px, calc(100% - 2 * var(--page-gutter)));
  max-height: calc(100dvh - var(--header-height) - 24px);
  margin: 0;
  padding: 0;
  border: 1px solid var(--color-border);
  border-top: 2px solid var(--color-primary);
  background: var(--color-surface);
  color: var(--color-text);
}

.navigation-sheet::backdrop {
  background: rgba(32, 32, 25, 0.15);
}

.navigation-content {
  position: relative;
  padding: 18px 24px;
}

.navigation-content nav {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.navigation-group {
  display: grid;
  align-content: start;
}

.navigation-group > span {
  color: var(--color-text-secondary);
  font-size: 0.78rem;
}

.navigation-group a,
.navigation-group button {
  display: flex;
  align-items: center;
  min-height: 40px;
  padding: 0;
  text-align: start;
}

.navigation-group [aria-current='page'] {
  color: var(--color-primary);
  text-decoration: underline;
}

.nav-close {
  position: absolute;
  inset: 4px 4px auto auto;
  width: 40px;
  height: 40px;
}

.skip-link {
  position: fixed;
  inset: 8px auto auto 16px;
  z-index: 1000;
  padding: 8px 16px;
  background: var(--color-primary);
  color: white;
  transform: translateY(-160%);
}

.skip-link:focus {
  transform: translateY(0);
  color: white;
}

@media (max-width: 760px) {
  .header-inner {
    gap: 8px;
  }

  .primary-nav {
    display: none;
  }
}

@media (max-width: 359px) {
  .brand {
    gap: 7px;
    font-size: 1.03rem;
  }

  .brand-mark {
    flex-basis: 28px;
    height: 28px;
  }

  .header-end {
    gap: 6px;
  }
}
</style>
