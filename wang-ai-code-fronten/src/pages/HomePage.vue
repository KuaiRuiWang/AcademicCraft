<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { addApp, listMyAppVoByPage, listGoodAppVoByPage } from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 用户提示词
const userPrompt = ref('')
const creating = ref(false)
const inputShell = ref<HTMLDivElement | null>(null)

// 我的应用数据
const myApps = ref<API.AppVO[]>([])
const myAppsLoading = ref(false)
const myAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

// 精选应用数据
const featuredApps = ref<API.AppVO[]>([])
const featuredAppsLoading = ref(false)
const featuredAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

// 设置提示词
const setPrompt = (prompt: string) => {
  userPrompt.value = prompt
  scrollToInput()
}

// 滚动到输入区
const scrollToInput = () => {
  inputShell.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

// 创建应用
const createApp = async () => {
  if (!userPrompt.value.trim()) {
    message.warning('请输入应用描述')
    return
  }

  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }

  creating.value = true
  try {
    const res = await addApp({
      initPrompt: userPrompt.value.trim(),
    })

    if (res.data.code === 0 && res.data.data) {
      message.success('应用创建成功')
      // 跳转到对话页面，确保ID是字符串类型
      const appId = String(res.data.data)
      await router.push(`/app/chat/${appId}`)
    } else {
      message.error('创建失败：' + res.data.message)
    }
  } catch (error) {
    console.error('创建应用失败：', error)
    message.error('创建失败，请重试')
  } finally {
    creating.value = false
  }
}

// 加载我的应用
const loadMyApps = async () => {
  if (!loginUserStore.loginUser.id) {
    return
  }

  myAppsLoading.value = true
  try {
    const res = await listMyAppVoByPage({
      pageNum: myAppsPage.current,
      pageSize: myAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records || []
      myAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载我的应用失败：', error)
  } finally {
    myAppsLoading.value = false
  }
}

// 加载精选应用
const loadFeaturedApps = async () => {
  featuredAppsLoading.value = true
  try {
    const res = await listGoodAppVoByPage({
      pageNum: featuredAppsPage.current,
      pageSize: featuredAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      featuredApps.value = res.data.data.records || []
      featuredAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载精选应用失败：', error)
  } finally {
    featuredAppsLoading.value = false
  }
}

// 查看对话
const viewChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}?view=1`)
  }
}

// 查看作品
const viewWork = (app: API.AppVO) => {
  if (app.deployKey) {
    const url = getDeployUrl(app.deployKey)
    window.open(url, '_blank')
  }
}

// 页面加载时获取数据
onMounted(() => {
  loadMyApps()
  loadFeaturedApps()

  // 鼠标跟随光效（单色，柔和）
  const handleMouseMove = (e: MouseEvent) => {
    const { clientX, clientY } = e
    const { innerWidth, innerHeight } = window
    const x = (clientX / innerWidth) * 100
    const y = (clientY / innerHeight) * 100

    document.documentElement.style.setProperty('--mouse-x', `${x}%`)
    document.documentElement.style.setProperty('--mouse-y', `${y}%`)
  }

  document.addEventListener('mousemove', handleMouseMove)

  // 清理事件监听器
  return () => {
    document.removeEventListener('mousemove', handleMouseMove)
  }
})
</script>

<template>
  <div id="homePage">
    <div class="container">
      <!-- 主视觉 -->
      <div class="hero-section">
        <span class="hero-eyebrow">百夜毕设帮</span>
        <h1 class="hero-title">不写一行代码，<br /><span class="accent">生成</span>计算机毕设</h1>
        <p class="hero-description">描述你的想法，一句话生成可部署的网站应用</p>
      </div>

      <!-- 用户提示词输入 -->
      <div ref="inputShell" class="input-shell">
        <a-textarea
          v-model:value="userPrompt"
          placeholder="帮我创建一个个人博客网站…"
          :rows="3"
          :maxlength="1000"
          class="prompt-input"
        />
        <a-button
          type="primary"
          size="large"
          class="submit-btn"
          :loading="creating"
          aria-label="创建应用"
          @click="createApp"
        >
          <span class="btn-arrow">↑</span>
        </a-button>
      </div>

      <!-- 快捷示例 -->
      <div class="quick-actions">
        <a-button
          class="quick-pill"
          @click="
            setPrompt(
              '创建一个现代化的个人博客网站，包含文章列表、详情页、分类标签、搜索功能、评论系统和个人简介页面。采用简洁的设计风格，支持响应式布局，文章支持Markdown格式，首页展示最新文章和热门推荐。',
            )
          "
        >
          个人博客网站
        </a-button>
        <a-button
          class="quick-pill"
          @click="
            setPrompt(
              '设计一个专业的企业官网，包含公司介绍、产品服务展示、新闻资讯、联系我们等页面。采用商务风格的设计，包含轮播图、产品展示卡片、团队介绍、客户案例展示，支持多语言切换和在线客服功能。',
            )
          "
        >
          企业官网
        </a-button>
        <a-button
          class="quick-pill"
          @click="
            setPrompt(
              '构建一个功能完整的在线商城，包含商品展示、购物车、用户注册登录、订单管理、支付结算等功能。设计现代化的商品卡片布局，支持商品搜索筛选、用户评价、优惠券系统和会员积分功能。',
            )
          "
        >
          在线商城
        </a-button>
        <a-button
          class="quick-pill"
          @click="
            setPrompt(
              '制作一个精美的作品展示网站，适合设计师、摄影师、艺术家等创作者。包含作品画廊、项目详情页、个人简历、联系方式等模块。采用瀑布流或网格布局展示作品，支持图片放大预览和作品分类筛选。',
            )
          "
        >
          作品展示网站
        </a-button>
      </div>

      <!-- 我的作品 -->
      <div v-if="loginUserStore.loginUser.id" class="section">
        <h2 class="section-title">我的作品</h2>
        <div v-if="myAppsLoading" class="app-grid">
          <div v-for="i in 3" :key="i" class="skeleton-card">
            <div class="skel-media"></div>
            <div class="skel-body">
              <a-skeleton active :title="{ width: '60%' }" :paragraph="{ rows: 1 }" />
            </div>
          </div>
        </div>
        <div v-else-if="myApps.length" class="app-grid">
          <AppCard
            v-for="app in myApps"
            :key="app.id"
            :app="app"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div v-else class="empty-state">
          <div class="empty-icon">🎨</div>
          <p class="empty-text">还没有作品，描述一个想法开始吧</p>
          <a-button type="primary" shape="round" @click="scrollToInput">创建第一个应用</a-button>
        </div>
        <div v-if="myAppsPage.total > myAppsPage.pageSize" class="pagination-wrapper">
          <a-pagination
            v-model:current="myAppsPage.current"
            v-model:page-size="myAppsPage.pageSize"
            :total="myAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个应用`"
            @change="loadMyApps"
          />
        </div>
      </div>

      <!-- 精选案例 -->
      <div class="section">
        <h2 class="section-title">精选案例</h2>
        <div v-if="featuredAppsLoading" class="app-grid">
          <div v-for="i in 3" :key="i" class="skeleton-card">
            <div class="skel-media"></div>
            <div class="skel-body">
              <a-skeleton active :title="{ width: '60%' }" :paragraph="{ rows: 1 }" />
            </div>
          </div>
        </div>
        <div v-else-if="featuredApps.length" class="app-grid">
          <AppCard
            v-for="app in featuredApps"
            :key="app.id"
            :app="app"
            :featured="true"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div v-else class="empty-state">
          <div class="empty-icon">✨</div>
          <p class="empty-text">精选案例整理中，敬请期待</p>
        </div>
        <div v-if="featuredAppsPage.total > featuredAppsPage.pageSize" class="pagination-wrapper">
          <a-pagination
            v-model:current="featuredAppsPage.current"
            v-model:page-size="featuredAppsPage.pageSize"
            :total="featuredAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个案例`"
            @change="loadFeaturedApps"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
#homePage {
  width: 100%;
  margin: 0;
  padding: 0;
  min-height: 100vh;
  background: var(--color-bg);
  position: relative;
  overflow: hidden;
}

/* 顶部柔和的单色光晕 */
#homePage::before {
  content: '';
  position: absolute;
  top: -180px;
  left: 50%;
  transform: translateX(-50%);
  width: 900px;
  height: 480px;
  background: radial-gradient(ellipse at center, rgba(15, 118, 110, 0.12) 0%, transparent 70%);
  pointer-events: none;
}

/* 鼠标跟随单色光效 */
#homePage::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(
    700px circle at var(--mouse-x, 50%) var(--mouse-y, 50%),
    rgba(15, 118, 110, 0.07) 0%,
    transparent 70%
  );
  pointer-events: none;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 20px 60px;
  position: relative;
  z-index: 2;
  width: 100%;
  box-sizing: border-box;
}

/* 主视觉 */
.hero-section {
  text-align: center;
  padding: 88px 0 48px;
  color: var(--color-text);
  position: relative;
}

.hero-eyebrow {
  display: inline-block;
  padding: 6px 16px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--color-primary);
  background: rgba(15, 118, 110, 0.1);
  margin-bottom: 24px;
}

.hero-title {
  font-size: 56px;
  font-weight: 700;
  margin: 0 0 20px;
  line-height: 1.12;
  letter-spacing: -0.03em;
  color: var(--color-text);
}

.hero-title .accent {
  color: var(--color-primary);
}

.hero-description {
  font-size: 18px;
  margin: 0 auto;
  max-width: 560px;
  color: var(--color-text-secondary);
}

/* 输入区：双嵌套结构（外壳 + 内芯） */
.input-shell {
  max-width: 760px;
  margin: 40px auto 24px;
  background: rgba(231, 229, 228, 0.6);
  padding: 8px;
  border-radius: 20px;
  border: 1px solid rgba(231, 229, 228, 0.9);
  position: relative;
}

.prompt-input {
  background: var(--color-surface);
  border: none;
  border-radius: 14px;
  font-size: 16px;
  padding: 16px 64px 16px 16px;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.8);
}

.prompt-input:focus {
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.8);
}

.submit-btn {
  position: absolute;
  right: 20px;
  bottom: 20px;
  width: 44px;
  height: 44px;
  border-radius: 999px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  transition:
    transform 0.3s var(--ease-spring),
    box-shadow 0.3s var(--ease-out-expo);
}

.submit-btn:not(:disabled):hover {
  transform: translateY(-2px) scale(1.04);
}

.submit-btn:not(:disabled):active {
  transform: scale(0.94);
}

.btn-arrow {
  font-size: 18px;
  line-height: 1;
}

/* 快捷示例 */
.quick-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-bottom: 72px;
  flex-wrap: wrap;
}

.quick-pill {
  border-radius: 999px;
  padding: 6px 20px;
  height: auto;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  font-weight: 500;
  transition:
    transform 0.3s var(--ease-out-expo),
    box-shadow 0.3s var(--ease-out-expo),
    color 0.3s var(--ease-out-expo),
    border-color 0.3s var(--ease-out-expo);
}

.quick-pill:hover {
  color: var(--color-primary);
  border-color: rgba(15, 118, 110, 0.4);
  transform: translateY(-2px);
  box-shadow: var(--shadow-pop);
}

/* 区块 */
.section {
  margin-bottom: 72px;
}

.section-title {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 28px;
  color: var(--color-text);
  letter-spacing: -0.02em;
}

/* 网格 */
.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

/* 骨架卡片 */
.skeleton-card {
  background: var(--color-surface);
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(231, 229, 228, 0.6);
}

.skel-media {
  height: 180px;
  background: linear-gradient(90deg, #f0efed 25%, #f7f6f3 37%, #f0efed 63%);
  background-size: 400% 100%;
  animation: skel 1.4s ease infinite;
}

.skel-body {
  padding: 16px;
}

@keyframes skel {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 56px 24px;
  background: var(--color-surface);
  border: 1px dashed var(--color-border);
  border-radius: 16px;
  text-align: center;
}

.empty-icon {
  font-size: 40px;
}

.empty-text {
  margin: 0 0 4px;
  color: var(--color-text-secondary);
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .hero-section {
    padding: 56px 0 32px;
  }

  .hero-title {
    font-size: 34px;
  }

  .hero-description {
    font-size: 16px;
  }

  .app-grid {
    grid-template-columns: 1fr;
  }

  .quick-actions {
    justify-content: flex-start;
    overflow-x: auto;
    flex-wrap: nowrap;
    padding-bottom: 4px;
    margin-bottom: 48px;
  }

  .quick-pill {
    flex-shrink: 0;
  }
}
</style>
