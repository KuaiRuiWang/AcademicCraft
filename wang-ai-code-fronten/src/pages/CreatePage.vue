<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { addApp, listMyAppVoByPage } from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import { starterTemplates } from '@/config/starterTemplates'
import AppCard from '@/components/AppCard.vue'
import { ArrowRightOutlined, CodeOutlined, InboxOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()

// 用户提示词
const userPrompt = ref('')
const creating = ref(false)
const createError = ref('')
const inputShell = ref<HTMLDivElement | null>(null)
// 是否同步生成 Node.js 后端（全栈项目）
const generateBackend = ref(false)
const generateThesis = ref(true)
const thesisTitle = ref('')

// 我的作品数据
const myApps = ref<API.AppVO[]>([])
const myAppsLoading = ref(false)
const myAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

// 设置提示词
const setPrompt = (prompt: string) => {
  userPrompt.value = prompt
  createError.value = ''
  scrollToInput()
}

// 滚动到输入区
const scrollToInput = () => {
  inputShell.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

// 创建应用
const createApp = async () => {
  createError.value = ''
  if (!userPrompt.value.trim()) {
    message.warning('请输入应用描述')
    return
  }

  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push({ path: '/user/login', query: { redirect: route.fullPath } })
    return
  }

  creating.value = true
  try {
    const res = await addApp({
      initPrompt: userPrompt.value.trim(),
      generateBackend: generateBackend.value,
      generateThesis: generateThesis.value,
      thesisTitle: generateThesis.value ? thesisTitle.value.trim() : undefined,
    })

    if (res.data.code === 0 && res.data.data) {
      message.success('应用创建成功')
      // 跳转到对话页面，确保ID是字符串类型
      const appId = String(res.data.data)
      await router.push(`/app/chat/${appId}`)
    } else {
      createError.value = res.data.message || '创建失败，请重试'
    }
  } catch (error) {
    console.error('创建应用失败：', error)
    createError.value = '创建失败，请重试'
  } finally {
    creating.value = false
  }
}

// 加载我的作品
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
    console.error('加载我的作品失败：', error)
  } finally {
    myAppsLoading.value = false
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
    const url = getDeployUrl(app.deployKey, app.codeGenType)
    window.open(url, '_blank')
  }
}

onMounted(() => {
  const template = starterTemplates.find((item) => item.id === route.query.template)
  if (template) userPrompt.value = template.prompt
  loadMyApps()
})
</script>

<template>
  <div id="createPage">
    <div class="container">
      <!-- 页面头部 -->
      <div class="hero-section">
        <h1 class="hero-title">用 AI <span class="accent">创建 Web 应用</span></h1>
        <p class="hero-description">
          支持 Vue 网站与全栈应用（Node.js + MySQL），生成后可直接部署上线
        </p>
      </div>

      <div ref="inputShell" class="input-shell">
        <label for="project-prompt" class="prompt-label"
          ><CodeOutlined aria-hidden="true" /> 描述你的项目需求</label
        >
        <a-textarea
          id="project-prompt"
          v-model:value="userPrompt"
          placeholder="例如，做一个带库存提醒的台账…"
          :auto-size="{ minRows: 4, maxRows: 10 }"
          :maxlength="1000"
          :disabled="creating"
          class="prompt-input"
        />
        <div class="composer-footer">
          <span class="prompt-count">{{ userPrompt.length }} / 1000</span>
          <a-button type="primary" size="large" :loading="creating" @click="createApp"
            >开始生成 <ArrowRightOutlined aria-hidden="true"
          /></a-button>
        </div>
      </div>
      <div class="fullstack-option">
        <a-checkbox v-model:checked="generateBackend" :disabled="creating"
          >同步生成 Node.js 后端 + MySQL（全栈项目）</a-checkbox
        >
        <span class="fullstack-hint">勾选后同时生成前后端与数据库，作为完整项目部署</span>
      </div>
      <a-alert
        v-if="createError"
        :message="createError"
        type="error"
        show-icon
        class="create-error"
      />
      <div class="thesis-option">
        <a-checkbox v-model:checked="generateThesis" :disabled="creating">同时生成毕业论文初稿</a-checkbox>
        <div v-if="generateThesis" class="thesis-title-field">
          <label for="thesis-title">论文题目（选填）</label>
          <a-input id="thesis-title" v-model:value="thesisTitle" :maxlength="200" :disabled="creating"
            placeholder="留空时根据应用名称拟定" />
          <p>代码生成后自动开始，可分章编辑并下载 Word。</p>
        </div>
      </div>
      <p class="use-note">
        用于课程或毕业考核时，请遵守学校的 AI 使用规则，自行完成要求独立完成的部分。
      </p>

      <!-- 快捷示例 -->
      <div class="quick-actions">
        <span class="examples-label">试试这些想法</span>
        <a-button
          v-for="template in starterTemplates"
          :key="template.id"
          class="quick-pill"
          @click="setPrompt(template.prompt)"
        >
          {{ template.name }}
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
          <InboxOutlined class="empty-icon" aria-hidden="true" />
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
    </div>
  </div>
</template>

<style scoped>
.thesis-option { margin-top: 1rem; }
.thesis-title-field { display: grid; gap: .5rem; max-width: 36rem; margin: .75rem 0 0; }
.thesis-title-field label { color: var(--color-text); font-size: .875rem; }
.thesis-title-field p { margin: 0; color: var(--color-text-secondary); font-size: .875rem; }
#createPage {
  background: var(--color-bg);
}
.container {
  max-width: calc(var(--page-width) + var(--page-gutter) * 2);
  margin-inline: auto;
  padding: 0 var(--page-gutter) 64px;
}
.hero-section {
  text-align: center;
  max-width: 880px;
  margin-inline: auto;
  padding-block: 60px 32px;
}
.hero-title {
  margin: 0 0 16px;
  font-size: clamp(1.875rem, 1.125rem + 2.5vw, 2.75rem);
  line-height: 1.4;
  font-weight: 750;
  color: var(--color-text);
}
.accent {
  color: var(--color-primary);
}
.hero-description {
  margin: 0 auto;
  font-size: 15px;
  line-height: 1.8;
  color: var(--color-text-secondary);
}
.input-shell {
  max-width: 820px;
  margin: 12px auto 20px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  padding: 24px;
  transition: border-color 0.2s;
}
.input-shell:focus-within {
  border-color: var(--color-primary);
}
.prompt-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 12px;
}
.prompt-label :deep(svg) {
  color: var(--color-primary);
}
.prompt-input {
  border: none;
  padding: 0;
  border-radius: 0;
  background: transparent;
  font-size: 16px;
  line-height: 1.8;
  resize: vertical;
  box-shadow: none;
}
.prompt-input:focus {
  box-shadow: none;
  outline: 2px solid var(--color-primary);
  outline-offset: 4px;
  border-radius: 2px;
}
.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 20px;
  margin-top: 16px;
  border-top: 1px solid var(--color-border);
}
.prompt-count {
  color: var(--color-text-tertiary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.fullstack-option {
  max-width: 820px;
  margin: 0 auto 28px;
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  gap: 6px;
  padding-inline: 4px;
}
.fullstack-option :deep(.ant-checkbox-wrapper) {
  min-height: 28px;
}
.fullstack-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  padding-inline-start: 24px;
}
.create-error,
.use-note {
  max-width: 860px;
  margin: 0 auto 18px;
}
.use-note {
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.quick-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px;
  max-width: 860px;
  margin: 0 auto 64px;
}
.examples-label {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-inline-end: 8px;
}
.quick-pill {
  height: auto;
  min-height: 40px;
  padding: 6px 14px;
  border-radius: 6px;
  background: transparent;
  border-color: var(--color-border);
  color: var(--color-text-secondary);
  font-size: 12px;
  box-shadow: none;
}
.quick-pill:hover {
  color: var(--color-primary);
  border-color: var(--color-primary-muted);
  background: var(--color-primary-soft);
}
.section {
  margin-top: 48px;
  padding-top: 32px;
  border-top: 1px solid var(--color-border);
}
.section-title {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.5;
  color: var(--color-text);
  margin: 0 0 24px;
}
.app-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}
.skeleton-card {
  overflow: hidden;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 14px;
}
.skel-media {
  aspect-ratio: 16 / 10;
  background: var(--color-primary-soft);
}
.skel-body {
  padding: 20px;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 24px;
  background: var(--color-surface);
  border: 1px dashed var(--color-border);
  border-radius: 12px;
  text-align: center;
}
.empty-icon {
  font-size: 36px;
  color: var(--color-primary);
  margin-bottom: 16px;
}
.empty-text {
  margin: 0 0 20px;
  color: var(--color-text-secondary);
}
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}
@media (max-width: 1000px) {
  .app-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 700px) {
  .hero-section {
    padding-block: 36px 24px;
    text-align: left;
  }
  .hero-title {
    font-size: 30px;
  }
  .hero-description {
    font-size: 14px;
  }
  .input-shell {
    padding: 20px;
    margin-top: 0;
  }
  .prompt-input {
    font-size: 16px;
  }
  .fullstack-option :deep(.ant-checkbox-wrapper) {
    min-height: 44px;
    align-items: center;
  }
  .fullstack-hint {
    padding-inline-start: 0;
  }
  .quick-actions {
    justify-content: flex-start;
    gap: 8px;
    margin-bottom: 24px;
  }
  .examples-label {
    flex-basis: 100%;
    margin-bottom: 4px;
  }
  .quick-pill {
    min-height: 44px;
  }
}
@media (max-width: 520px) {
  .app-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
