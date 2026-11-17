<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CodeOutlined, EyeOutlined, StarFilled, ArrowRightOutlined } from '@ant-design/icons-vue'

const props = defineProps<{ project: API.ProjectVO }>()
const imageFailed = ref(false)
const tags = computed(() =>
  (props.project.techTags || '')
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean)
    .slice(0, 3),
)
watch(
  () => props.project.coverImage,
  () => {
    imageFailed.value = false
  },
)
</script>

<template>
  <RouterLink class="project-card" :to="project.id ? `/project/${project.id}` : '/project'">
    <div class="card-cover">
      <img
        v-if="project.coverImage && !imageFailed"
        :src="project.coverImage"
        :alt="project.name"
        loading="lazy"
        @error="imageFailed = true"
      />
      <div v-else class="cover-placeholder">
        <CodeOutlined aria-hidden="true" />
        <span>{{ project.categoryName || '毕设项目' }}</span>
      </div>
      <span v-if="project.salesCount" class="card-sales">已售 {{ project.salesCount }}</span>
    </div>
    <div class="card-body">
      <div class="card-heading">
        <h3 class="card-title">{{ project.name }}</h3>
        <span class="card-price">{{ project.price ? `¥${project.price}` : '免费' }}</span>
      </div>
      <p class="card-desc">{{ project.description || '暂无描述' }}</p>
      <div class="card-tags">
        <span v-for="tag in tags" :key="tag" class="tech-tag">{{ tag }}</span>
      </div>
      <div class="card-meta">
        <span class="card-author">
          <a-avatar :src="project.author?.userAvatar" :size="22">{{
            project.author?.userName?.charAt(0) || 'U'
          }}</a-avatar>
          <span>{{ project.author?.userName || '匿名' }}</span>
        </span>
        <span class="card-stats">
          <span
            ><StarFilled class="rating-icon" aria-hidden="true" />
            {{ project.avgRating ? project.avgRating.toFixed(1) : '暂无评分' }}</span
          >
          <span><EyeOutlined aria-hidden="true" /> {{ project.viewCount || 0 }}</span>
        </span>
      </div>
      <div class="card-action">查看项目 <ArrowRightOutlined aria-hidden="true" /></div>
    </div>
  </RouterLink>
</template>

<style scoped>
.project-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  overflow: hidden;
  color: var(--color-text);
}
.project-card:hover {
  border-color: var(--color-primary-muted);
  transform: translateY(-4px);
}
.project-card:focus-visible {
  outline-offset: 4px;
}
.card-cover {
  aspect-ratio: 16 / 10;
  position: relative;
  overflow: hidden;
  background: var(--color-primary-soft);
}
.card-cover img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.4s var(--ease-out-expo);
}
.project-card:hover .card-cover img {
  transform: scale(1.025);
}
.cover-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 12px;
  color: var(--color-primary);
}
.cover-placeholder :deep(svg) {
  width: 44px;
  height: 44px;
}
.cover-placeholder > span:last-child {
  font-size: 14px;
  font-weight: 600;
}
.card-sales {
  position: absolute;
  inset-block-start: 12px;
  inset-inline-end: 12px;
  padding: 3px 9px;
  border-radius: 6px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 12px;
}
.card-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 20px;
}
.card-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.card-title {
  margin: 0;
  font-size: 17px;
  font-weight: 650;
  line-height: 1.5;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.card-price {
  flex-shrink: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--color-primary-strong);
  font-variant-numeric: tabular-nums;
}
.card-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 8px 0 16px;
  min-height: 44px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.7;
}
.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 20px;
  min-height: 24px;
}
.tech-tag {
  font-size: 11px;
  color: var(--color-text-secondary);
  background: var(--color-surface-soft);
  border-radius: 4px;
  padding: 2px 8px;
}
.card-meta {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: auto;
  font-size: 12px;
  color: var(--color-text-tertiary);
}
.card-author {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  max-width: 46%;
}
.card-author > span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-stats {
  display: flex;
  gap: 12px;
  font-variant-numeric: tabular-nums;
}
.rating-icon {
  color: #a16c17;
}
.card-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
}
</style>
