<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowUpOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { addApp, listMyAppVoByPage } from '@/api/appController'
import { getProjectVoById } from '@/api/projectController'
import { starterTemplates } from '@/config/starterTemplates'
import AppCard from '@/components/AppCard.vue'
import PageHeading from '@/components/PageHeading.vue'
import folioArtwork from '@/assets/folio.webp'

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()
const promptField = ref<HTMLTextAreaElement>()
const userPrompt = ref('')
const creating = ref(false)
const createError = ref('')
const templateError = ref('')
const generateBackend = ref(false)
const generateThesis = ref(true)
const thesisTitle = ref('')
const draftKey = 'baiye:create-after-login'

const myApps = ref<API.AppVO[]>([])
const myAppsLoading = ref(false)
const myAppsError = ref(false)
const myAppsPage = reactive({ current: 1, pageSize: 6, total: 0 })

function setPrompt(prompt: string) {
  userPrompt.value = prompt
  createError.value = ''
  templateError.value = ''
  promptField.value?.focus()
}

async function createApp() {
  if (creating.value) return
  createError.value = ''
  const initPrompt = userPrompt.value.trim()
  if (!initPrompt) {
    createError.value = '请填写项目需求'
    promptField.value?.focus()
    return
  }
  if (!loginUserStore.loginUser.id) {
    sessionStorage.setItem(
      draftKey,
      JSON.stringify({
        prompt: userPrompt.value,
        backend: generateBackend.value,
        thesis: generateThesis.value,
        title: thesisTitle.value,
      }),
    )
    message.warning('登录后即可继续生成')
    await router.push({ path: '/user/login', query: { redirect: route.fullPath } })
    return
  }

  creating.value = true
  try {
    const res = await addApp({
      initPrompt,
      generateBackend: generateBackend.value,
      generateThesis: generateThesis.value,
      thesisTitle: generateThesis.value ? thesisTitle.value.trim() : undefined,
    })
    if (res.data.code === 0 && res.data.data) {
      message.success('应用已创建')
      await router.push('/app/chat/' + String(res.data.data))
    } else {
      createError.value = res.data.message || '创建失败，请重试'
    }
  } catch {
    createError.value = '创建失败，请检查网络后重试'
  } finally {
    creating.value = false
  }
}

async function loadMyApps() {
  myAppsLoading.value = true
  myAppsError.value = false
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
    } else {
      myAppsError.value = true
    }
  } catch {
    myAppsError.value = true
  } finally {
    myAppsLoading.value = false
  }
}

async function loadTemplatePrompt() {
  const template = route.query.template
  if (typeof template !== 'string') return
  const starter = starterTemplates.find((item) => item.id === template)
  if (starter) {
    userPrompt.value = starter.prompt
    return
  }
  if (!/^\d+$/.test(template)) return

  try {
    const res = await getProjectVoById({ id: template as unknown as number })
    const project = res.data.data
    if (res.data.code !== 0 || !project?.name) throw new Error('模板暂不可用')
    if (!userPrompt.value) {
      userPrompt.value = ['创建一个「' + project.name + '」应用。', project.description]
        .filter(Boolean)
        .join('\n')
        .slice(0, 1000)
    }
  } catch {
    if (!userPrompt.value) templateError.value = '无法读取原模板。你可以直接填写项目需求。'
  }
}

onMounted(() => {
  const savedDraft = sessionStorage.getItem(draftKey)
  if (savedDraft) {
    sessionStorage.removeItem(draftKey)
    try {
      const draft = JSON.parse(savedDraft)
      userPrompt.value = draft.prompt
      generateBackend.value = draft.backend
      generateThesis.value = draft.thesis
      thesisTitle.value = draft.title
    } catch {
      void loadTemplatePrompt()
    }
  } else {
    void loadTemplatePrompt()
  }
  if (loginUserStore.loginUser.id) void loadMyApps()
})
</script>

<template>
  <div class="page-shell">
    <PageHeading
      title="起草一个应用"
      description="写下使用场景、主要功能，以及你希望得到的结果。"
    />

    <form class="brief-grid" @submit.prevent="createApp">
      <section class="brief-sheet" aria-labelledby="brief-title">
        <h2 id="brief-title">创作简报</h2>
        <a-alert
          v-if="templateError"
          class="brief-alert"
          type="warning"
          :message="templateError"
          show-icon
        />
        <label class="field-label" for="project-prompt">项目需求</label>
        <textarea
          id="project-prompt"
          ref="promptField"
          v-model="userPrompt"
          class="ant-input brief-textarea"
          placeholder="例如，为工作室做一个库存台账，帮我看见哪些物品需要补货。"
          rows="6"
          maxlength="1000"
          required
          :disabled="creating"
          aria-describedby="prompt-count"
        ></textarea>
        <div id="prompt-count" class="prompt-count tabular">{{ userPrompt.length }} / 1000</div>
        <div class="brief-examples">
          <span>试用示例</span>
          <a-button
            v-for="template in starterTemplates"
            :key="template.id"
            size="small"
            :disabled="creating"
            @click="setPrompt(template.prompt)"
          >
            {{ template.name }}
          </a-button>
        </div>

        <fieldset class="brief-options">
          <legend>输出内容</legend>
          <div class="brief-option">
            <div>
              <label id="backend-label" for="generate-backend">全栈项目</label>
              <small id="backend-note">Vue 前端、Node.js 后端与 MySQL 数据库</small>
            </div>
            <a-switch
              id="generate-backend"
              v-model:checked="generateBackend"
              :disabled="creating"
              aria-labelledby="backend-label"
              aria-describedby="backend-note"
            />
          </div>
          <div class="brief-option">
            <div>
              <label id="thesis-label" for="generate-thesis">毕业论文初稿</label>
              <small id="thesis-note">分章编辑，导出 Word 文档</small>
            </div>
            <a-switch
              id="generate-thesis"
              v-model:checked="generateThesis"
              :disabled="creating"
              aria-labelledby="thesis-label"
              aria-describedby="thesis-note"
            />
          </div>
          <div v-if="generateThesis" class="title-field">
            <label class="field-label" for="thesis-title">论文题目 <small>选填</small></label>
            <a-input
              id="thesis-title"
              v-model:value="thesisTitle"
              :maxlength="200"
              :disabled="creating"
              aria-describedby="thesis-use-note"
              placeholder="留空时根据应用名称拟定"
            />
            <small id="thesis-use-note">用于课程考核时，请遵守学校的 AI 使用规则。</small>
          </div>
        </fieldset>
      </section>

      <aside class="delivery-list" aria-labelledby="delivery-title">
        <h2 id="delivery-title">交付清单</h2>
        <dl class="folio-spec">
          <div>
            <dt>应用</dt>
            <dd>{{ generateBackend ? 'Vue 网站' : 'Web 网站' }}</dd>
          </div>
          <div>
            <dt>后端</dt>
            <dd>{{ generateBackend ? 'Node.js + MySQL' : '仅前端' }}</dd>
          </div>
          <div>
            <dt>文档</dt>
            <dd>{{ generateThesis ? '论文初稿' : '仅源码' }}</dd>
          </div>
        </dl>
        <a-alert
          v-if="createError"
          class="brief-alert"
          :message="createError"
          type="error"
          show-icon
        />
        <a-button type="primary" html-type="submit" :loading="creating" block>
          开始生成 <ArrowUpOutlined class="diagonal-arrow" aria-hidden="true" />
        </a-button>
        <img :src="folioArtwork" alt="" />
      </aside>
    </form>

    <section
      v-if="loginUserStore.loginUser.id"
      id="my-apps"
      class="my-apps"
      aria-labelledby="my-apps-title"
    >
      <header class="section-heading"><h2 id="my-apps-title">我的应用</h2></header>
      <a-skeleton v-if="myAppsLoading" active :title="false" :paragraph="{ rows: 3 }" />
      <a-alert v-else-if="myAppsError" message="应用列表加载失败" type="error" show-icon>
        <template #action><a-button size="small" @click="loadMyApps">重试</a-button></template>
      </a-alert>
      <ul v-else-if="myApps.length" class="paper-list">
        <AppCard v-for="app in myApps" :key="app.id" :app="app" />
      </ul>
      <p v-else class="folio-empty">还没有应用。写下第一个需求即可开始创作。</p>
      <div v-if="myAppsPage.total > myAppsPage.pageSize" class="pagination-wrapper">
        <a-pagination
          v-model:current="myAppsPage.current"
          :page-size="myAppsPage.pageSize"
          :total="myAppsPage.total"
          :show-size-changer="false"
          @change="loadMyApps"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.brief-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(225px, 0.39fr);
  gap: clamp(24px, 5vw, 80px);
  align-items: start;
}

.brief-grid h2 {
  font-size: clamp(1.3rem, 2.2vw, 1.75rem);
  margin-bottom: 20px;
}

.field-label,
.brief-option label {
  display: block;
  font-weight: 650;
}

.field-label {
  margin-bottom: 8px;
}

.brief-textarea {
  display: block;
  width: 100%;
  min-height: 182px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  padding: 16px 18px;
  resize: vertical;
}

.brief-textarea:hover {
  border-color: var(--color-primary);
}

.prompt-count {
  margin: 6px 0 12px;
  text-align: end;
  font-size: 0.73rem;
  color: var(--color-text-secondary);
}

.brief-examples {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  font-size: 0.83rem;
}

.brief-examples > span {
  margin-right: 4px;
  color: var(--color-text-secondary);
}

.brief-examples :deep(.ant-btn) {
  min-height: 36px;
  padding-inline: 11px;
  background: transparent;
  font-size: 0.8rem;
}

.brief-options {
  min-width: 0;
  margin: 30px 0 0;
  padding: 0;
  border: 0;
}

.brief-options legend {
  margin-bottom: 12px;
  font-size: 1.05rem;
  font-weight: 720;
}

.brief-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border);
}

.brief-option label {
  cursor: pointer;
}

.brief-option small,
.title-field small {
  color: var(--color-text-secondary);
  font-size: 0.85rem;
}

.brief-option small {
  display: block;
  margin-top: 3px;
}

.title-field {
  margin-top: 16px;
}

.title-field .field-label small {
  margin-inline-start: 4px;
  font-weight: 400;
}

.title-field input {
  margin-bottom: 5px;
}

.delivery-list {
  padding-top: 16px;
  border-top: 3px solid var(--color-primary);
}

.delivery-list h2 {
  color: var(--color-primary);
}

.delivery-list .folio-spec {
  margin-bottom: 22px;
}

.delivery-list img {
  display: block;
  width: 100%;
  aspect-ratio: 2.45;
  object-fit: cover;
  margin-top: 26px;
}

.brief-alert {
  margin-bottom: 14px;
}

.diagonal-arrow {
  rotate: 45deg;
}

.my-apps {
  margin-top: 54px;
}

@media (max-width: 760px) {
  .brief-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 30px;
  }

  .delivery-list img {
    display: none;
  }
}
</style>
