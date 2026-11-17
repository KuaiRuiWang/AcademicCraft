<template>
  <div id="projectDetailPage">
    <a-spin :spinning="loading">
      <template v-if="project.id">
        <div class="container">
          <!-- 返回按钮 -->
          <div class="breadcrumb">
            <a-button type="link" @click="$router.push('/project')">
              <LeftOutlined /> 返回商城
            </a-button>
          </div>

          <header class="project-heading">
            <h1>{{ project.name }}</h1>
            <p>{{ project.description }}</p>
          </header>
          <a-row :gutter="[24, 24]">
            <!-- 左侧：主要信息 -->
            <a-col :xs="24" :lg="17">
              <!-- 项目图片 -->
              <a-card class="detail-card cover-card" :bordered="false">
                <img
                  :src="project.coverImage || defaultCover"
                  :alt="project.name"
                  class="detail-cover"
                  @error="
                    (e: any) => {
                      e.target.src = defaultCover
                    }
                  "
                />
              </a-card>

              <!-- 项目信息 -->
              <a-card class="detail-card" :bordered="false" title="项目详情">
                <template #extra>
                  <a-space :size="4" wrap>
                    <a-tag v-for="tag in parseTags(project.techTags)" :key="tag" class="tech-tag">
                      {{ tag }}
                    </a-tag>
                  </a-space>
                </template>
                <div class="detail-content" v-html="renderedContent"></div>
                <div v-if="!project.detailContent" class="detail-content">
                  <p>{{ project.description || '暂无详细描述' }}</p>
                </div>
              </a-card>

              <!-- 项目截图 -->
              <a-card
                v-if="project.images && project.images.length > 0"
                class="detail-card"
                :bordered="false"
                title="项目截图"
              >
                <div class="screenshot-grid">
                  <a-image
                    v-for="(img, idx) in project.images"
                    :key="idx"
                    :src="img.imageUrl"
                    :width="200"
                    class="screenshot-item"
                  />
                </div>
              </a-card>

              <!-- 评价区域 -->
              <a-card class="detail-card" :bordered="false" title="用户评价">
                <template #extra>
                  <a-space>
                    <StarFilled style="color: #faad14" />
                    <span
                      >{{ project.avgRating ? project.avgRating.toFixed(1) : '-' }} ({{
                        reviews.length
                      }}
                      条评价)</span
                    >
                  </a-space>
                </template>

                <!-- 评价列表 -->
                <div v-if="reviews.length > 0">
                  <div v-for="review in reviews" :key="review.id" class="review-item">
                    <div class="review-header">
                      <a-space>
                        <a-avatar :src="review.user?.userAvatar" :size="32" />
                        <div>
                          <div class="review-author">{{ review.user?.userName || '匿名' }}</div>
                          <a-rate :value="review.rating" disabled :style="{ fontSize: '12px' }" />
                        </div>
                      </a-space>
                      <span class="review-time">{{ review.createTime }}</span>
                    </div>
                    <p class="review-content">{{ review.content }}</p>
                  </div>
                </div>
                <a-empty v-else description="暂无评价" />

                <!-- 发表评价 -->
                <a-divider />
                <div v-if="hasPurchased && !myReview" class="review-form">
                  <h4>发表评价</h4>
                  <div class="rating-row">
                    <span>评分：</span>
                    <a-rate v-model:value="reviewForm.rating" />
                  </div>
                  <a-textarea
                    v-model:value="reviewForm.content"
                    placeholder="分享你的使用体验..."
                    :rows="3"
                    :maxlength="500"
                    class="review-textarea"
                  />
                  <a-button
                    type="primary"
                    :loading="submittingReview"
                    @click="submitReview"
                    style="margin-top: 12px"
                  >
                    提交评价
                  </a-button>
                </div>
              </a-card>
            </a-col>

            <!-- 右侧：购买信息 -->
            <a-col :xs="24" :lg="7">
              <div class="sidebar">
                <a-card class="detail-card purchase-card" :bordered="false">
                  <div class="price-block">
                    <span class="price-value">{{
                      project.price ? `¥${project.price}` : '免费'
                    }}</span>
                  </div>

                  <a-divider />

                  <div class="info-rows">
                    <div class="info-row">
                      <TagOutlined /> 分类：{{ project.categoryName || '-' }}
                    </div>
                    <div class="info-row"><FireOutlined /> 销量：{{ project.salesCount || 0 }}</div>
                    <div class="info-row"><EyeOutlined /> 浏览：{{ project.viewCount || 0 }}</div>
                    <div class="info-row">
                      <StarFilled style="color: #faad14" />
                      评分：{{ project.avgRating ? project.avgRating.toFixed(1) : '暂无' }}
                    </div>
                    <div class="info-row">
                      <ClockCircleOutlined /> 上架时间：{{ project.createTime }}
                    </div>
                  </div>

                  <a-divider />

                  <!-- 按钮组 -->
                  <a-space direction="vertical" style="width: 100%" :size="12">
                    <a-button
                      v-if="canBuy"
                      type="primary"
                      size="large"
                      block
                      :loading="buying"
                      @click="handleBuy"
                    >
                      <ShoppingCartOutlined />
                      {{ project.price ? '立即购买' : '免费获取' }}
                    </a-button>
                    <a-button v-if="project.demoUrl" size="large" block @click="openDemo">
                      <LinkOutlined /> 查看演示
                    </a-button>
                  </a-space>

                  <div v-if="!canBuy && loginUserStore.loginUser.id" class="already-purchased">
                    <a-alert message="您已购买此项目" type="success" show-icon />
                  </div>
                </a-card>

                <!-- 作者信息 -->
                <a-card class="detail-card author-card" :bordered="false" title="作者">
                  <div class="author-info">
                    <a-space>
                      <a-avatar :src="project.author?.userAvatar" :size="48" />
                      <div>
                        <div class="author-name">{{ project.author?.userName || '匿名' }}</div>
                        <div class="author-bio">
                          {{ project.author?.userProfile || '暂无简介' }}
                        </div>
                      </div>
                    </a-space>
                  </div>
                </a-card>
              </div>
            </a-col>
          </a-row>
        </div>
      </template>

      <!-- 项目不存在 -->
      <div v-else-if="!loading" class="not-found">
        <a-result status="404" title="项目不存在" sub-title="可能已下架或链接有误">
          <template #extra>
            <a-button type="primary" @click="$router.push('/project')">返回商城</a-button>
          </template>
        </a-result>
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { marked } from 'marked'
import {
  LeftOutlined,
  StarFilled,
  FireOutlined,
  EyeOutlined,
  ClockCircleOutlined,
  ShoppingCartOutlined,
  LinkOutlined,
  TagOutlined,
} from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { getProjectVoById } from '@/api/projectController.ts'
import { createOrder } from '@/api/projectOrderController.ts'
import { addReview, listReviews } from '@/api/projectReviewController.ts'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const defaultCover =
  'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iODAwIiBoZWlnaHQ9IjQwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iODAwIiBoZWlnaHQ9IjQwMCIgZmlsbD0iI2YwZjJmNSIvPjx0ZXh0IHg9IjQwMCIgeT0iMjEwIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmaWxsPSIjYmZiZmMwIiBmb250LXNpemU9IjI0IiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+Tm8gSW1hZ2U8L3RleHQ+PC9zdmc+'

const loading = ref(true)
const buying = ref(false)
const submittingReview = ref(false)
const project = ref<API.ProjectVO>({})
const reviews = ref<API.ProjectReviewVO[]>([])
const myReview = ref<API.ProjectReviewVO | null>(null)

const reviewForm = reactive<API.ProjectReviewAddRequest>({
  projectId: undefined,
  rating: 5,
  content: '',
})

// 判断当前用户是否作者
const isAuthor = computed(() => {
  return loginUserStore.loginUser.id && loginUserStore.loginUser.id === project.value.authorId
})

// 是否可以购买（未登录用户也不允许直接购买，但按钮仍显示用于引导登录）
const canBuy = computed(() => {
  if (!loginUserStore.loginUser.id) return false
  return !isAuthor.value
})

// 判断是否已购买（简单处理：非作者且登录即显示）
// 实际项目中应由后端接口判断
const hasPurchased = computed(() => {
  return loginUserStore.loginUser.id && !isAuthor.value
})

// 渲染 markdown 内容
const renderedContent = computed(() => {
  if (!project.value.detailContent) return ''
  try {
    return marked.parse(project.value.detailContent) as string
  } catch {
    return project.value.detailContent
  }
})

// 加载项目详情
const loadProject = async () => {
  loading.value = true
  try {
    // 不使用 Number()，避免 JS 大数精度丢失。后端已配置 Long → String 序列化
    const id = route.params.id as string
    const res = await getProjectVoById({ id: id as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      project.value = res.data.data
      reviewForm.projectId = id as unknown as number
      loadReviews()
    }
  } catch {
    message.error('加载项目失败')
  } finally {
    loading.value = false
  }
}

// 加载评价
const loadReviews = async () => {
  try {
    const id = route.params.id as string
    const res = await listReviews({ projectId: id as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      reviews.value = res.data.data
      // 查找我的评价
      myReview.value = res.data.data.find((r) => r.userId === loginUserStore.loginUser.id) || null
    }
  } catch {
    // ignore
  }
}

// 购买
const handleBuy = async () => {
  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    router.push('/user/login')
    return
  }

  buying.value = true
  try {
    const res = await createOrder({ projectId: project.value.id })
    if (res.data.code === 0) {
      message.success('下单成功！')
      router.push('/my/orders')
    } else {
      message.error(res.data.message || '下单失败')
    }
  } catch {
    message.error('下单失败，请稍后重试')
  } finally {
    buying.value = false
  }
}

// 提交评价
const submitReview = async () => {
  if (!reviewForm.content?.trim()) {
    message.warning('请输入评价内容')
    return
  }

  submittingReview.value = true
  try {
    const res = await addReview({
      ...reviewForm,
      content: reviewForm.content.trim(),
    })
    if (res.data.code === 0) {
      message.success('评价成功')
      reviewForm.rating = 5
      reviewForm.content = ''
      loadReviews()
    } else {
      message.error(res.data.message || '评价失败')
    }
  } catch {
    message.error('评价失败')
  } finally {
    submittingReview.value = false
  }
}

// 打开演示
const openDemo = () => {
  if (project.value.demoUrl) {
    window.open(project.value.demoUrl, '_blank')
  }
}

// 解析标签
const parseTags = (tags?: string): string[] => {
  if (!tags) return []
  return tags
    .split(',')
    .filter(Boolean)
    .map((t) => t.trim())
}

onMounted(() => {
  loadProject()
})
</script>

<style scoped>
#projectDetailPage {
  min-height: 60dvh;
  background: var(--color-bg);
}

.container {
  max-width: calc(var(--page-width) + var(--page-gutter) * 2);
  margin: 0 auto;
  padding: 24px var(--page-gutter) 64px;
}

.breadcrumb {
  margin-bottom: 16px;
}

/* 卡片通用 */
.detail-card {
  border-radius: 14px;
  margin-bottom: 16px;
  border: 1px solid var(--color-border);
}

.detail-card :deep(.ant-card-head) {
  border-bottom: 1px solid var(--color-border);
  font-weight: 600;
}

/* 封面 */
.cover-card {
  padding: 0;
  overflow: hidden;
}

.cover-card :deep(.ant-card-body) {
  padding: 0;
}

.detail-cover {
  width: 100%;
  display: block;
  aspect-ratio: 16 / 9;
  max-height: 440px;
  object-fit: cover;
}

/* 技术标签 */
.tech-tag {
  background: rgba(15, 118, 110, 0.08);
  color: var(--color-primary);
  border: none;
  border-radius: 999px;
  font-size: 12px;
  padding-inline: 10px;
  line-height: 22px;
}

/* 详情内容 */
.detail-content {
  line-height: 1.8;
  color: var(--color-text);
  font-size: 15px;
  overflow-wrap: anywhere;
}

.detail-content :deep(h1),
.detail-content :deep(h2),
.detail-content :deep(h3) {
  margin-top: 16px;
  margin-bottom: 8px;
}

.detail-content :deep(pre) {
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  padding: 16px;
  border-radius: 10px;
  overflow-x: auto;
}

.detail-content :deep(code) {
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 13px;
}

.detail-content :deep(img) {
  max-width: 100%;
  border-radius: 10px;
}

/* 截图 */
.screenshot-grid {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.screenshot-item {
  border-radius: 10px;
  cursor: pointer;
}

/* 侧边栏 */
.sidebar {
  position: sticky;
  top: calc(var(--header-height) + 24px);
}

.purchase-card {
  text-align: center;
}

.price-block {
  padding: 8px 0;
}

.price-value {
  font-size: 34px;
  font-weight: 700;
  color: var(--color-primary);
  letter-spacing: -0.02em;
}

.info-rows {
  text-align: left;
}

.info-row {
  padding: 6px 0;
  font-size: 14px;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.already-purchased {
  margin-top: 12px;
}

/* 作者 */
.author-card :deep(.ant-card-head) {
  font-weight: 600;
}

.author-name {
  font-size: 16px;
  font-weight: 600;
}

.author-bio {
  font-size: 13px;
  color: var(--color-text-tertiary);
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 评价 */
.review-item {
  padding: 16px 0;
  border-bottom: 1px solid var(--color-border);
}

.review-item:last-child {
  border-bottom: none;
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.review-author {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 2px;
}

.review-time {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.review-content {
  font-size: 14px;
  color: var(--color-text-secondary);
  line-height: 1.6;
  margin: 0;
}

.review-form h4 {
  margin: 0 0 12px;
}

.rating-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.review-textarea {
  margin-top: 8px;
}

/* 404 */
.not-found {
  padding: 120px 16px;
}
.project-heading {
  margin-bottom: 28px;
}
.project-heading h1 {
  margin: 0 0 12px;
  font-size: clamp(1.75rem, 1rem + 2vw, 2.25rem);
  line-height: 1.5;
  color: var(--color-text);
}
.project-heading p {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-secondary);
  max-width: 70ch;
}
.detail-content :deep(pre code) {
  padding: 0;
  border: 0;
  background: transparent;
}
@media (max-width: 700px) {
  .detail-card :deep(.ant-card-head-wrapper) {
    flex-wrap: wrap;
    gap: 10px;
    padding-block: 14px;
  }
  .detail-card :deep(.ant-card-extra) {
    margin-inline-start: 0;
  }
  .review-header {
    flex-wrap: wrap;
    gap: 8px;
  }
}
</style>
