<script setup lang="ts">
import { useRoute } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'

const route = useRoute()
</script>

<template>
  <div class="site-frame">
    <GlobalHeader />
    <main id="main-content" tabindex="-1">
      <AdminLayout v-if="route.meta.layout === 'admin'" />
      <RouterView v-else v-slot="{ Component }">
        <component :is="Component" :key="route.path" />
      </RouterView>
    </main>
    <GlobalFooter />
  </div>
</template>

<style scoped>
.site-frame {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
}

main {
  flex: 1;
}

main:focus {
  outline: none;
}
</style>
