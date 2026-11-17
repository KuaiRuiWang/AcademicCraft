<template>
  <div class="app-card" :class="{ 'app-card--featured': featured }">
    <div class="app-preview">
      <img
        v-if="app.cover && !imageFailed"
        :src="app.cover"
        :alt="app.appName"
        loading="lazy"
        @error="imageFailed = true"
      />
      <div v-else class="app-placeholder">
        <AppstoreOutlined aria-hidden="true" />
      </div>
      <div class="app-overlay">
        <a-space>
          <a-button type="primary" size="small" shape="round" @click="handleViewChat"
            >查看对话</a-button
          >
          <a-button
            v-if="app.deployKey"
            size="small"
            shape="round"
            class="overlay-btn"
            @click="handleViewWork"
          >
            查看作品
          </a-button>
        </a-space>
      </div>
    </div>
    <div class="app-info">
      <a-avatar :src="app.user?.userAvatar" :size="40">
        {{ app.user?.userName?.charAt(0) || 'U' }}
      </a-avatar>
      <div class="app-info-right">
        <h3 class="app-title">{{ app.appName || '未命名应用' }}</h3>
        <p class="app-author">
          {{ app.user?.userName || (featured ? '官方' : '未知用户') }}
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { AppstoreOutlined } from '@ant-design/icons-vue'
interface Props {
  app: API.AppVO
  featured?: boolean
}

interface Emits {
  (e: 'view-chat', appId: string | number | undefined): void
  (e: 'view-work', app: API.AppVO): void
}

const props = withDefaults(defineProps<Props>(), {
  featured: false,
})

const emit = defineEmits<Emits>()
const imageFailed = ref(false)
watch(
  () => props.app.cover,
  () => {
    imageFailed.value = false
  },
)

const handleViewChat = () => {
  emit('view-chat', props.app.id)
}

const handleViewWork = () => {
  emit('view-work', props.app)
}
</script>

<style scoped>
.app-card {
  background: var(--color-surface);
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  transition:
    transform 0.35s var(--ease-out-expo),
    box-shadow 0.35s var(--ease-out-expo);
}

.app-card:hover {
  transform: translateY(-4px);
  border-color: var(--color-primary-muted);
}

.app-preview {
  aspect-ratio: 16 / 10;
  background: var(--color-primary-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
}

.app-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s var(--ease-out-expo);
}

.app-card:hover .app-preview img {
  transform: scale(1.05);
}

.app-placeholder {
  font-size: 48px;
  color: var(--color-primary);
}

.app-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(22, 24, 29, 0.5), rgba(22, 24, 29, 0) 60%);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 16px;
  opacity: 1;
  transition: opacity 0.3s var(--ease-out-expo);
}

.app-card:hover .app-overlay,
.app-card:focus-within .app-overlay {
  opacity: 1;
}

.overlay-btn {
  background: rgba(255, 255, 255, 0.9);
  border-color: transparent;
  color: var(--color-text);
}

.overlay-btn:hover {
  background: #ffffff;
  color: var(--color-primary);
  border-color: transparent;
}

.app-info {
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-info-right {
  flex: 1;
  min-width: 0;
}

.app-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 4px;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-author {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-overlay :deep(.ant-btn) {
  min-height: 38px;
  padding-inline: 14px;
  border-radius: 6px;
}

@media (pointer: coarse) {
  .app-overlay :deep(.ant-btn) {
    min-height: 44px;
  }
}
</style>
