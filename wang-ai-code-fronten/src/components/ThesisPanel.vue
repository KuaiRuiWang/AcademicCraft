<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { message } from 'ant-design-vue'
import { DownloadOutlined, EditOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import {
  exportThesis,
  generateThesis,
  getThesis,
  saveThesisChapter,
  type Thesis,
} from '@/api/thesisController'

const props = defineProps<{ appId: string; appGenerating: boolean }>()
const thesis = ref<Thesis>()
const error = ref('')
const loading = ref(true)
const starting = ref(false)
const downloading = ref(false)
const saving = ref(false)
const chapterNo = ref(1)
const editing = ref(false)
const draft = ref('')
const original = ref<string | null>(null)
const editRevision = ref<string>()
let timer: ReturnType<typeof setTimeout> | undefined
let disposed = false
let loadId = 0
const chapter = computed(() =>
  thesis.value?.chapters.find((item) => item.chapterNo === chapterNo.value),
)
const generating = computed(() => thesis.value?.status === 'generating')
const completed = computed(
  () => thesis.value?.chapters.filter((item) => item.status === 'ready').length || 0,
)
const dirty = computed(() => editing.value && draft.value !== (original.value || ''))
const manualReviewCount = computed(
  () =>
    thesis.value?.chapters.filter(
      (item) => item.userEdited && item.sourceVersion !== thesis.value?.sourceVersion,
    ).length || 0,
)
const statusLabels = { pending: '待生成', generating: '生成中', ready: '已完成', failed: '需重试' }
const generateLabel = computed(
  () =>
    ({ stale: '更新未编辑章节', failed: '重试未完成章节', ready: '重新生成未编辑章节' })[
      thesis.value?.status as 'stale' | 'failed' | 'ready'
    ] || '生成论文初稿',
)

async function load() {
  const currentLoad = ++loadId
  if (timer) clearTimeout(timer)
  try {
    const response = await getThesis(props.appId)
    if (disposed || currentLoad !== loadId) return
    if (response.data.code !== 0) throw new Error(response.data.message || '论文读取失败')
    thesis.value = response.data.data
    error.value = ''
  } catch (reason) {
    if (!disposed && currentLoad === loadId)
      error.value = reason instanceof Error ? reason.message : '无法读取论文，请重试'
  } finally {
    if (currentLoad === loadId) {
      loading.value = false
      if (!disposed && (generating.value || props.appGenerating)) timer = setTimeout(load, 3000)
    }
  }
}

async function generate() {
  starting.value = true
  try {
    const response = await generateThesis(props.appId)
    if (response.data.code !== 0) throw new Error(response.data.message || '无法开始生成论文')
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '无法开始生成论文，请重试'
  } finally {
    starting.value = false
  }
}

function edit() {
  original.value = chapter.value?.content ?? null
  draft.value = original.value || ''
  editRevision.value = thesis.value?.revision
  editing.value = true
}

async function save() {
  if (!chapter.value) return
  saving.value = true
  try {
    const response = await saveThesisChapter(props.appId, chapter.value.chapterNo, {
      content: draft.value,
      originalContent: original.value,
      revision: editRevision.value,
    })
    if (response.data.code !== 0) throw new Error(response.data.message || '保存失败')
    editing.value = false
    message.success('本章已保存，更新论文时会保留你的编辑')
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '保存失败，当前输入已保留'
  } finally {
    saving.value = false
  }
}

async function download() {
  downloading.value = true
  try {
    const response = await exportThesis(props.appId)
    if (response.data.type.includes('json')) {
      const result = JSON.parse(await response.data.text()) as { message?: string }
      throw new Error(result.message || 'Word 导出失败')
    }
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `论文初稿-${props.appId}.docx`
    anchor.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'Word 导出失败，请重试'
  } finally {
    downloading.value = false
  }
}

watch(
  () => props.appGenerating,
  () => {
    void load()
  },
)
onMounted(load)
onUnmounted(() => {
  disposed = true
  if (timer) clearTimeout(timer)
})
onBeforeRouteLeave(() => !dirty.value || window.confirm('本章还有未保存的修改，确定离开吗？'))
</script>

<template>
  <section class="thesis-panel" aria-label="论文初稿">
    <div class="thesis-toolbar">
      <div>
        <h3>{{ thesis?.title || '论文初稿' }}</h3>
        <p role="status" aria-live="polite">{{ thesis?.stage || '正在读取论文…' }}</p>
      </div>
      <div class="thesis-actions">
        <a-button
          @click="generate"
          :loading="starting"
          :disabled="generating || appGenerating || editing"
        >
          <template #icon><ReloadOutlined /></template>{{ generateLabel }}
        </a-button>
        <a-button
          type="primary"
          @click="download"
          :loading="downloading"
          :disabled="thesis?.status !== 'ready' || editing || appGenerating"
        >
          <template #icon><DownloadOutlined /></template>下载 Word
        </a-button>
      </div>
    </div>
    <a-alert v-if="error" type="error" show-icon :message="error" class="thesis-notice">
      <template #action><a-button size="small" @click="load">重新读取</a-button></template>
    </a-alert>
    <a-alert
      v-if="thesis?.status === 'stale'"
      type="warning"
      show-icon
      message="应用代码已变更。更新时会保留手动编辑的章节，请结合新代码核对这些章节。"
      class="thesis-notice"
    />
    <a-alert
      v-if="thesis?.status === 'ready' && manualReviewCount"
      type="warning"
      show-icon
      class="thesis-notice"
      :message="`已保留 ${manualReviewCount} 个基于旧版本的手动编辑章节，请逐章核对并保存。`"
    />
    <a-alert
      v-if="thesis?.status === 'failed'"
      type="warning"
      show-icon
      :message="thesis.errorMessage || '生成中断，已保存的章节仍可查看。'"
      class="thesis-notice"
    />
    <div v-if="generating" class="thesis-progress">
      <span>已完成 {{ completed }} / {{ thesis?.chapters.length }} 章，离开页面后仍会继续生成</span>
      <a-progress
        :percent="Math.round((completed / (thesis?.chapters.length || 10)) * 100)"
        :show-info="false"
      />
    </div>
    <div v-if="loading" class="thesis-empty"><a-spin tip="读取论文中" /></div>
    <div v-else-if="!thesis?.chapters.length" class="thesis-empty">
      <h4>让论文与应用内容对应</h4>
      <p>应用代码生成完成后，可依据实际实现生成提纲、正文和设计图，并导出 Word 初稿。</p>
      <p>已勾选同步生成时会自动开始，也可以点击上方按钮手动生成。</p>
    </div>
    <div v-else class="thesis-workspace">
      <nav class="thesis-chapters" aria-label="论文章节">
        <button
          v-for="item in thesis.chapters"
          :key="item.chapterNo"
          type="button"
          :aria-current="chapterNo === item.chapterNo ? 'true' : undefined"
          :disabled="editing"
          @click="chapterNo = item.chapterNo"
        >
          <span>{{ item.title }}</span>
          <small>{{ statusLabels[item.status] }}{{ item.userEdited ? ' · 已编辑' : '' }}</small>
        </button>
      </nav>
      <article class="thesis-content" :aria-label="chapter?.title">
        <div class="chapter-toolbar">
          <h4>{{ chapter?.title }}</h4>
          <a-button
            v-if="!editing && chapter?.content"
            size="small"
            @click="edit"
            :disabled="generating || appGenerating"
          >
            <template #icon><EditOutlined /></template>编辑本章
          </a-button>
        </div>
        <form v-if="editing" @submit.prevent="save" class="chapter-editor">
          <label for="thesis-chapter-content">章节正文（Markdown）</label>
          <a-textarea
            id="thesis-chapter-content"
            v-model:value="draft"
            :rows="20"
            :maxlength="100000"
            :disabled="saving"
          />
          <div class="editor-actions">
            <a-button
              type="primary"
              html-type="submit"
              :loading="saving"
              :disabled="generating || appGenerating"
              >保存本章</a-button
            >
            <a-button @click="editing = false" :disabled="saving">取消编辑</a-button>
          </div>
        </form>
        <MarkdownRenderer v-else-if="chapter?.content" :content="chapter.content" />
        <p v-else class="chapter-pending">
          {{
            chapter?.status === 'failed'
              ? '本章生成失败，点击“重试未完成章节”继续。'
              : '本章尚未生成，完成后会自动显示。'
          }}
        </p>
      </article>
    </div>
    <p class="thesis-footnote">
      初稿依据源码生成。测试记录、页面截图、文献和学校格式需核对补充。Word 目录可在打开后更新。
    </p>
  </section>
</template>

<style scoped>
.thesis-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  color: var(--color-text);
}
.thesis-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  align-items: start;
  justify-content: space-between;
  padding: 1.25rem;
  border-bottom: 1px solid var(--color-border);
}
.thesis-toolbar h3 {
  margin: 0 0 0.5rem;
  font-size: 1.125rem;
}
.thesis-toolbar p,
.thesis-footnote {
  margin: 0;
  font-size: 0.875rem;
  color: var(--color-text-secondary);
}
.thesis-actions,
.editor-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.thesis-notice {
  margin: 0.75rem 1rem 0;
}
.thesis-progress {
  padding: 0.75rem 1.25rem;
  color: var(--color-text-secondary);
  font-size: 0.875rem;
}
.thesis-workspace {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 10rem minmax(0, 1fr);
}
.thesis-chapters {
  overflow: auto;
  padding: 0.75rem 0.5rem;
  border-right: 1px solid var(--color-border);
}
.thesis-chapters button {
  width: 100%;
  min-height: 3rem;
  padding: 0.75rem;
  text-align: left;
  border: 0;
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
  border-radius: 6px;
}
.thesis-chapters button:hover,
.thesis-chapters button[aria-current] {
  background: var(--color-primary-soft);
}
.thesis-chapters button[aria-current] {
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 0.25em;
}
.thesis-chapters button:disabled {
  cursor: default;
  opacity: 0.65;
}
.thesis-chapters small {
  display: block;
  margin-top: 0.25rem;
  font-size: 0.75rem;
  color: var(--color-text-secondary);
}
.thesis-content {
  overflow: auto;
  min-width: 0;
  padding: 1.25rem;
}
.chapter-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}
.chapter-toolbar h4 {
  margin: 0;
  font-size: 1rem;
}
.chapter-editor {
  display: grid;
  gap: 0.75rem;
}
.chapter-editor textarea {
  font: inherit;
  resize: vertical;
}
.chapter-pending {
  color: var(--color-text-secondary);
}
.thesis-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 2rem;
  text-align: center;
  color: var(--color-text-secondary);
}
.thesis-empty h4 {
  color: var(--color-text);
  font-size: 1.125rem;
}
.thesis-empty p {
  max-width: 32rem;
  line-height: 1.7;
}
.thesis-footnote {
  padding: 0.75rem 1.25rem;
  border-top: 1px solid var(--color-border);
  line-height: 1.6;
}
@media (max-width: 800px) {
  .thesis-panel {
    overflow-y: auto;
  }
  .thesis-workspace {
    display: flex;
    flex-direction: column;
    flex: none;
  }
  .thesis-chapters {
    display: flex;
    flex: none;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }
  .thesis-chapters button {
    width: auto;
    flex: none;
    min-width: 7rem;
  }
  .thesis-content {
    flex: none;
    overflow: visible;
    min-height: 15rem;
  }
  .thesis-footnote {
    flex-shrink: 0;
  }
}
</style>
