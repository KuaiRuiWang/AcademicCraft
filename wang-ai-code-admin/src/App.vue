<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { LogoutOutlined } from '@ant-design/icons-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { can, identity, logout } from '@/session'
import { navigation } from '@/router'

const router = useRouter()
const route = useRoute()
const groups = computed(() => {
  const menu = navigation.filter((item) => can(item.permission))
  return [...new Set(menu.map((item) => item.group))].map((name) => ({
    name, items: menu.filter((item) => item.group === name),
  }))
})
const theme = { token: { colorPrimary: '#2443ae', borderRadius: 6, fontFamily: 'inherit' } }
async function signOut() {
  try { await logout() } finally { await router.replace('/login') }
}
</script>

<template>
  <a-config-provider :locale="zhCN" :theme="theme">
    <RouterView v-if="route.path === '/login'" />
    <div v-else-if="identity" class="console">
      <aside class="sidebar">
        <RouterLink class="brand" to="/">应用工坊 <small>管理端</small></RouterLink>
        <nav aria-label="管理端菜单">
          <section v-for="group in groups" :key="group.name" class="menu-group">
            <h2>{{ group.name }}</h2>
            <RouterLink v-for="item in group.items" :key="item.path" :to="item.path"
              :class="{ 'is-current': route.path === item.path || route.path.startsWith(item.path + '/') }">
              <component :is="item.icon" aria-hidden="true" /> {{ item.title }}
            </RouterLink>
          </section>
        </nav>
      </aside>
      <div class="console-body">
        <header class="console-header">
          <div class="admin-profile">
            <div><strong>{{ identity.displayName }}</strong><small>{{ identity.role }}</small></div>
            <a-button @click="signOut"><LogoutOutlined aria-hidden="true" />退出</a-button>
          </div>
        </header>
        <main class="admin-main"><RouterView /></main>
      </div>
    </div>
  </a-config-provider>
</template>
