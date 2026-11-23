<script setup lang="ts">
import PageHeading from '@/components/PageHeading.vue'
import folioArtwork from '@/assets/folio.webp'

defineProps<{ title: string; description: string }>()
</script>

<template>
  <div class="page-shell">
    <div class="auth-layout">
      <div class="auth-art"><img :src="folioArtwork" alt="" /></div>
      <section class="auth-panel">
        <PageHeading :title="title" :description="description" />
        <slot />
      </section>
    </div>
  </div>
</template>

<style scoped>
.auth-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.15fr);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
}

.auth-art {
  position: relative;
  min-height: 100%;
}

.auth-art img {
  display: block;
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 53%;
}

.auth-panel {
  min-width: 0;
  padding: clamp(24px, 4vw, 64px);
}

.auth-panel :deep(.page-heading) {
  margin-bottom: 22px;
}

.auth-panel :deep(.page-heading h1) {
  font-size: clamp(2rem, 3.6vw, 3.25rem);
}

.auth-panel :deep(.ant-form) {
  max-width: 32rem;
}

.auth-panel :deep(.auth-next) {
  margin: 18px 0 0;
}

.auth-panel :deep(.auth-error) {
  margin-bottom: 18px;
}

@media (max-width: 760px) {
  .auth-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .auth-art {
    min-height: 0;
    aspect-ratio: 2.33;
  }

  .auth-panel :deep(.ant-form) {
    max-width: none;
  }
}
</style>
