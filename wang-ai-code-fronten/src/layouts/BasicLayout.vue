<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'

const route = useRoute()
const workspace = computed(() => route.meta.layout === 'workspace')
</script>

<template>
  <div class="site-frame" :class="{ 'site-frame--workspace': workspace }">
    <GlobalHeader />
    <main id="main-content" tabindex="-1">
      <AdminLayout v-if="route.meta.layout === 'admin'" />
      <RouterView v-else v-slot="{ Component }">
        <component :is="Component" :key="route.path" />
      </RouterView>
    </main>
    <GlobalFooter v-if="!workspace" />
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

.site-frame--workspace {
  --page-width: 100%;
  --page-gutter: clamp(12px, 1.5vw, 24px);
  --page-measure: calc(100% - 2 * var(--page-gutter));
  --header-height: 54px;
  height: 100vh;
  height: 100dvh;
  min-height: 480px;
}

.site-frame--workspace main {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

main:focus {
  outline: none;
}
</style>
