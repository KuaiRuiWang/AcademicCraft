<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeftOutlined,
  ArrowUpOutlined,
  DownloadOutlined,
  ExportOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { getProjectVoById } from '@/api/projectController'
import { createOrder, getTemplateDownloadUrl, listMyOrders } from '@/api/projectOrderController'
import { addReview, listReviews } from '@/api/projectReviewController'
import { starterTemplates } from '@/config/starterTemplates'
import { formatTime } from '@/utils/time'
import TemplateArtwork from '@/components/TemplateArtwork.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import PageHeading from '@/components/PageHeading.vue'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const id = route.params.id as string
const starter = starterTemplates.find((template) => template.id === id)
const loading = ref(!starter)
const loadError = ref('')
const project = ref<API.ProjectVO>({})
const claim = ref<API.ProjectOrderVO>()
const claiming = ref(false)
const reviews = ref<API.ProjectReviewVO[]>([])
const reviewError = ref(false)
const submittingReview = ref(false)
const reviewForm = reactive({ rating: 5, content: '' })

const isAuthor = computed(() =>
  Boolean(
    loginUserStore.loginUser.id &&
      String(loginUserStore.loginUser.id) === String(project.value.authorId),
  ),
)
const hasReviewed = computed(() =>
  reviews.value.some((review) => String(review.userId) === String(loginUserStore.loginUser.id)),
)
const tags = computed(() =>
  (project.value.techTags || '')
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean),
)
const name = computed(() => starter?.name || project.value.name || '未命名模板')
const description = computed(() => starter?.description || project.value.description)
const category = computed(() => starter?.category || project.value.categoryName)
const cloneLocation = { path: '/create', query: { template: id } }

const demoUrl = computed(() => {
  if (starter) return starter.url
  if (!project.value.demoUrl) return ''
  try {
    const url = new URL(project.value.demoUrl, window.location.origin)
    if (url.protocol === 'http:' || url.protocol === 'https:') return url.href
  } catch {
    // 没有可访问的演示地址时，详情展示已登记的资料和截图。
  }
  return ''
})

async function loadProject() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getProjectVoById({ id: id as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      project.value = res.data.data
      await Promise.all([loadReviews(), loadClaim()])
    } else {
      loadError.value = res.data.message || '模板暂未开放，可能已下架或链接有误。'
    }
  } catch {
    loadError.value = '暂时无法读取模板，请检查网络后重试。'
  } finally {
    loading.value = false
  }
}

async function loadReviews() {
  reviewError.value = false
  try {
    const res = await listReviews({ projectId: id as unknown as number })
    if (res.data.code !== 0) throw new Error(res.data.message)
    reviews.value = res.data.data || []
  } catch {
    reviewError.value = true
  }
}

async function loadClaim() {
  const projectId = project.value.id
  if (!loginUserStore.loginUser.id || !projectId || isAuthor.value) return
  try {
    const res = await listMyOrders({ projectId })
    if (res.data.code === 0) {
      claim.value = res.data.data?.records?.find((record) => record.downloadAvailable)
    }
  } catch {
    message.warning('暂时无法读取领取记录，可在个人区重试。')
  }
}

async function handleClaim() {
  if (!loginUserStore.loginUser.id) {
    await router.push({ path: '/user/login', query: { redirect: route.fullPath } })
    return
  }
  claiming.value = true
  try {
    const res = await createOrder({ projectId: project.value.id })
    if (res.data.code === 0 && res.data.data) {
      claim.value = { id: res.data.data, downloadAvailable: true }
      message.success('已领取模板')
    } else {
      message.error(res.data.message || '领取失败')
    }
  } catch {
    message.error('领取失败，请稍后重试')
  } finally {
    claiming.value = false
  }
}

async function submitReview() {
  if (!claim.value?.id || !claim.value.downloadAvailable) return
  const content = reviewForm.content.trim()
  if (!content) {
    message.warning('请输入评价内容')
    return
  }

  submittingReview.value = true
  try {
    const res = await addReview({
      projectId: project.value.id,
      orderId: claim.value.id,
      rating: reviewForm.rating,
      content,
    })
    if (res.data.code === 0) {
      message.success('评价已发表')
      reviewForm.content = ''
      await loadReviews()
    } else {
      message.error(res.data.message || '评价发表失败')
    }
  } catch {
    message.error('评价发表失败，请稍后重试')
  } finally {
    submittingReview.value = false
  }
}

onMounted(() => {
  if (!starter) void loadProject()
})
</script>

<template>
  <div class="page-shell">
    <RouterLink to="/project" class="back-link"
      ><ArrowLeftOutlined aria-hidden="true" /> 模板目录</RouterLink
    >

    <div v-if="loading" aria-busy="true" class="paper-panel">
      <a-skeleton active :paragraph="{ rows: 6 }" />
    </div>
    <template v-else-if="starter || project.id">
      <header class="detail-lead">
        <div class="detail-title">
          <h1>{{ name }}</h1>
          <p v-if="description">{{ description }}</p>
          <div class="detail-tags">
            <a-tag v-if="category">{{ category }}</a-tag>
            <a-tag>{{ starter ? 'MIT 许可' : '免费模板' }}</a-tag>
            <a-tag v-for="tag in tags" :key="tag">{{ tag }}</a-tag>
          </div>
          <div class="folio-actions">
            <a-button type="primary" @click="router.push(cloneLocation)">
              用此模板创建 <ArrowUpOutlined class="diagonal-arrow" aria-hidden="true" />
            </a-button>
            <a-button v-if="starter" :href="starter.url" :download="starter.id + '.html'">
              <DownloadOutlined aria-hidden="true" /> 下载示例源码
            </a-button>
            <a-button
              v-else-if="claim?.downloadAvailable && claim.id"
              :href="getTemplateDownloadUrl(claim.id)"
              target="_blank"
              rel="noopener"
            >
              <DownloadOutlined aria-hidden="true" /> 下载源码
            </a-button>
            <a-button v-else-if="!isAuthor" :loading="claiming" @click="handleClaim">
              免费领取
            </a-button>
          </div>
        </div>
        <TemplateArtwork
          class="detail-art"
          :illustration="starter?.id"
          :cover="project.coverImage"
        />
      </header>

      <div class="detail-body">
        <div>
          <section v-if="demoUrl" aria-labelledby="demo-title">
            <header class="section-heading">
              <h2 id="demo-title">在线试用</h2>
              <a class="folio-link" :href="demoUrl" target="_blank" rel="noopener">
                独立打开 <ExportOutlined aria-hidden="true" />
              </a>
            </header>
            <iframe
              class="demo-frame"
              :title="name + '在线试用'"
              :src="demoUrl"
              sandbox="allow-scripts allow-forms"
              referrerpolicy="no-referrer"
            ></iframe>
          </section>
          <section v-if="project.detailContent" class="detail-material paper-panel">
            <h2>模板详情</h2>
            <MarkdownRenderer :content="project.detailContent" />
          </section>
          <section v-if="project.images?.length" class="detail-material">
            <header class="section-heading"><h2>项目截图</h2></header>
            <div class="screenshot-grid">
              <a-image
                v-for="(img, index) in project.images"
                :key="img.imageUrl || index"
                :src="img.imageUrl"
                :alt="name + '项目截图'"
              />
            </div>
          </section>
        </div>

        <aside class="detail-aside">
          <section class="paper-panel">
            <h2>模板说明</h2>
            <p v-if="starter">{{ starter.prompt }}</p>
            <p v-else-if="project.author?.userProfile" class="author-note">
              {{ project.author.userProfile }}
            </p>
            <dl class="folio-spec">
              <template v-if="starter">
                <div>
                  <dt>作者</dt>
                  <dd>百夜应用工坊</dd>
                </div>
                <div>
                  <dt>源码</dt>
                  <dd>单文件 HTML</dd>
                </div>
                <div>
                  <dt>使用许可</dt>
                  <dd>MIT，保留版权及许可声明</dd>
                </div>
              </template>
              <template v-else>
                <div>
                  <dt>作者</dt>
                  <dd>{{ project.author?.userName || '匿名' }}</dd>
                </div>
                <div>
                  <dt>来源</dt>
                  <dd>{{ project.sourceAttribution || '未填写' }}</dd>
                </div>
                <div>
                  <dt>使用许可</dt>
                  <dd>{{ project.licenseTerms || '未填写' }}</dd>
                </div>
                <div>
                  <dt>领取次数</dt>
                  <dd>{{ project.salesCount || 0 }}</dd>
                </div>
                <div>
                  <dt>浏览次数</dt>
                  <dd>{{ project.viewCount || 0 }}</dd>
                </div>
                <div v-if="project.createTime">
                  <dt>上架时间</dt>
                  <dd>{{ formatTime(project.createTime) }}</dd>
                </div>
              </template>
            </dl>
          </section>

          <section v-if="!starter" class="paper-panel reviews">
            <h2>用户评价</h2>
            <p v-if="project.avgRating" class="review-score">
              平均 {{ project.avgRating.toFixed(1) }} 分
            </p>
            <div v-if="reviewError">
              <p>评价暂时无法读取。</p>
              <a-button size="small" @click="loadReviews">重试</a-button>
            </div>
            <ul v-else-if="reviews.length" class="paper-list">
              <li v-for="review in reviews" :key="review.id" class="review">
                <strong>{{ review.user?.userName || '匿名' }}</strong>
                <span>{{ review.rating }} / 5 分</span>
                <p>{{ review.content }}</p>
                <small v-if="review.createTime">{{ formatTime(review.createTime) }}</small>
              </li>
            </ul>
            <p v-else class="review-score">还没有评价。</p>

            <form
              v-if="claim?.downloadAvailable && !reviewError && !hasReviewed"
              class="review-form"
              @submit.prevent="submitReview"
            >
              <h3>发表评价</h3>
              <label for="review-rating">评分</label>
              <a-select
                id="review-rating"
                v-model:value="reviewForm.rating"
                :options="[5, 4, 3, 2, 1].map((value) => ({ value, label: value + ' 分' }))"
              />
              <label for="review-content">使用感受</label>
              <a-textarea
                id="review-content"
                v-model:value="reviewForm.content"
                :rows="3"
                :maxlength="500"
              />
              <a-button type="primary" html-type="submit" :loading="submittingReview"
                >发表评价</a-button
              >
            </form>
          </section>
        </aside>
      </div>
    </template>
    <template v-else>
      <PageHeading title="模板无法打开" :description="loadError" />
      <a-button @click="loadProject">重新读取</a-button>
    </template>
  </div>
</template>

<style scoped>
.detail-lead,
.detail-body {
  display: grid;
  gap: clamp(22px, 3vw, 36px);
}

.detail-lead {
  grid-template-columns: minmax(0, 1fr) minmax(0, 0.57fr);
  align-items: center;
  margin-bottom: 32px;
}

.detail-title h1 {
  font-size: clamp(2rem, 4vw, 3.6rem);
}

.detail-title p {
  max-width: 60ch;
  margin: 10px 0 16px;
  color: var(--color-text-secondary);
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 9px;
  margin-bottom: 22px;
}

.detail-tags :deep(.ant-tag) {
  margin: 0;
  padding: 2px 8px;
}

.diagonal-arrow {
  rotate: 45deg;
}

.detail-art {
  height: clamp(150px, 16vw, 230px);
}

.detail-body {
  grid-template-columns: minmax(0, 2.25fr) minmax(0, 1fr);
  align-items: start;
}

.detail-aside {
  display: grid;
  gap: 20px;
}

.detail-aside p {
  color: var(--color-text-secondary);
  overflow-wrap: anywhere;
}

.detail-material:not(:first-child) {
  margin-top: 24px;
}

.demo-frame {
  display: block;
  width: 100%;
  height: clamp(370px, 43vw, 600px);
  border: 1px solid var(--color-border);
  background: white;
}

.screenshot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(180px, 100%), 1fr));
  gap: 12px;
}

.screenshot-grid :deep(img) {
  height: 148px;
  object-fit: cover;
  border: 1px solid var(--color-border);
}

.review {
  padding-block: 10px;
  overflow-wrap: anywhere;
}

.review span,
.review small {
  color: var(--color-text-secondary);
  font-size: 0.8rem;
}

.review span {
  margin-inline-start: 8px;
}

.review p {
  margin: 5px 0;
  white-space: pre-wrap;
}

.review-form {
  display: grid;
  justify-items: start;
  gap: 8px;
  margin-top: 22px;
}

.review-form :deep(.ant-select) {
  width: 112px;
}

.review-form :deep(textarea) {
  width: 100%;
}

@media (max-width: 800px) {
  .detail-body {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 600px) {
  .detail-lead {
    grid-template-columns: minmax(0, 1fr);
  }

  .detail-art {
    height: 132px;
  }
}
</style>
