<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { message } from 'ant-design-vue'
import { DownloadOutlined, EditOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import ThesisRewriteDialog from '@/components/ThesisRewriteDialog.vue'
import { createChapterDraft, restoreChapterDraft } from '@/utils/thesisDiagram'
import {
  readChapterSelection,
  REWRITE_CONTEXT_LENGTH,
  REWRITE_SELECTION_LIMIT,
  selectionIncludesProtectedContent,
  type ChapterSelection,
} from '@/utils/thesisRewrite'
import {
  exportThesis,
  generateThesis,
  getThesis,
  saveThesisChapter,
  type Thesis,
  type ThesisRewriteInput,
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
const originalDraft = ref('')
const draftFigures = ref<{ marker: string; block: string }[]>([])
const editRevision = ref<string>()
const selection = ref<ChapterSelection | null>(null)
const selectionError = ref('')
const rewriteTarget = shallowRef<{
  appId: string
  chapterNo: number
  draft: string
  range: ChapterSelection
  input: Omit<ThesisRewriteInput, 'instruction'>
} | null>(null)
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
const dirty = computed(() => editing.value && draft.value !== originalDraft.value)
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
  const editable = createChapterDraft(original.value || '')
  draft.value = editable.text
  originalDraft.value = editable.text
  draftFigures.value = editable.figures
  editRevision.value = thesis.value?.revision
  selection.value = null
  selectionError.value = ''
  editing.value = true
}

function captureSelection(event: Event) {
  if (!(event.target instanceof HTMLTextAreaElement)) return
  const next = readChapterSelection(draft.value, event.target)
  if (selection.value?.start !== next?.start || selection.value?.end !== next?.end) selectionError.value = ''
  selection.value = next
}

function openRewrite() {
  const range = selection.value
  if (!range || !chapter.value || saving.value || generating.value || props.appGenerating) return
  const selectedText = draft.value.slice(range.start, range.end)
  if (selectedText.length > REWRITE_SELECTION_LIMIT) {
    selectionError.value = '请选择 4000 字以内的正文'
    return
  }
  if (selectionIncludesProtectedContent(draft.value, range)) {
    selectionError.value = '请选择正文，避开配图占位行和代码块'
    return
  }
  rewriteTarget.value = {
    appId: props.appId,
    chapterNo: chapter.value.chapterNo,
    draft: draft.value,
    range,
    input: {
      revision: editRevision.value,
      selectedText,
      beforeText: draft.value.slice(Math.max(0, range.start - REWRITE_CONTEXT_LENGTH), range.start),
      afterText: draft.value.slice(range.end, range.end + REWRITE_CONTEXT_LENGTH),
    },
  }
}

function applyRewrite(suggestion: string) {
  const target = rewriteTarget.value
  if (!target || !editing.value || target.appId !== props.appId || target.chapterNo !== chapter.value?.chapterNo
    || target.draft !== draft.value || target.input.revision !== thesis.value?.revision
    || generating.value || props.appGenerating) {
    selectionError.value = '编辑内容或论文版本已变化，请重新选中文字'
    return
  }
  const { start, end } = target.range
  const updated = target.draft.slice(0, start) + suggestion + target.draft.slice(end)
  if (restoreChapterDraft(updated, draftFigures.value).length > 100000) {
    selectionError.value = '替换后正文超过 10 万字，请缩短修改建议'
    return
  }
  draft.value = updated
  message.success('已替换选区，请保存本章')
}

async function save() {
  if (!chapter.value) return
  saving.value = true
  try {
    const response = await saveThesisChapter(props.appId, chapter.value.chapterNo, {
      content: restoreChapterDraft(draft.value, draftFigures.value),
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
    document.body.append(anchor)
    anchor.click()
    anchor.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    message.success('Word 已导出')
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'Word 导出失败，请重试'
  } finally {
    downloading.value = false
  }
}

watch(draft, () => {
  selection.value = null
  selectionError.value = ''
})
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
  <section class="paper-panel thesis-panel" aria-label="论文初稿">
    <div class="thesis-toolbar">
      <div>
        <h3>{{ thesis?.title || '论文初稿' }}</h3>
        <p role="status" aria-live="polite">
          {{ thesis?.stage || (loading ? '正在读取论文…' : '等待生成论文初稿') }}
        </p>
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
    <div v-if="loading" class="thesis-empty">
      <a-skeleton active :title="false" :paragraph="{ rows: 4 }" />
    </div>
    <div v-else-if="!thesis?.chapters.length" class="thesis-empty">
      <p>应用代码生成后，可根据实际实现生成分章初稿并导出 Word。</p>
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
          <small v-if="thesis.status !== 'ready' || item.status !== 'ready' || item.userEdited">
            {{ item.userEdited ? '已编辑' : statusLabels[item.status] }}
          </small>
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
          <div class="editor-selection-toolbar">
            <label for="thesis-chapter-content">章节正文（Markdown）</label>
            <a-button
              size="small"
              html-type="button"
              :disabled="!selection || saving || generating || appGenerating"
              @click="openRewrite"
            >AI 修改选中内容</a-button>
          </div>
          <p v-if="draftFigures.length" class="diagram-edit-note">配图以占位行显示，保存时保留原图；删除占位行可移除对应配图。</p>
          <a-textarea
            id="thesis-chapter-content"
            v-model:value="draft"
            :rows="10"
            :maxlength="100000"
            :disabled="saving"
            @select="captureSelection"
            @keyup="captureSelection"
            @pointerup="captureSelection"
          />
          <p v-if="selectionError" class="selection-error" role="alert">{{ selectionError }}</p>
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
        <MarkdownRenderer v-else-if="chapter?.content" :content="chapter.content" diagrams />
        <p v-else class="chapter-pending">
          {{
            chapter?.status === 'failed'
              ? '本章生成失败，点击“重试未完成章节”继续。'
              : '本章尚未生成，完成后会自动显示。'
          }}
        </p>
      </article>
    </div>
    <ThesisRewriteDialog
      v-if="rewriteTarget"
      :app-id="rewriteTarget.appId"
      :chapter-no="rewriteTarget.chapterNo"
      :selection="rewriteTarget.input"
      @apply="applyRewrite"
      @close="rewriteTarget = null"
    />
    <p class="thesis-footnote">
      初稿依据源码生成。测试记录、页面截图、文献和学校格式需核对补充。Word 目录可在打开后更新。
    </p>
  </section>
</template>

<style scoped>
.thesis-panel {
  container-type: inline-size;
  color: var(--color-text);
}

.thesis-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 18px;
  align-items: start;
  justify-content: space-between;
  margin-bottom: 18px;
}

.thesis-toolbar h3 {
  margin-bottom: 6px;
  font-size: clamp(1.22rem, 1.8vw, 1.55rem);
}

.thesis-toolbar p,
.thesis-footnote {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 0.84rem;
}

.thesis-actions,
.editor-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.thesis-notice + .thesis-notice,
.thesis-workspace,
.thesis-progress {
  margin-top: 14px;
}

.thesis-progress {
  color: var(--color-text-secondary);
  font-size: 0.85rem;
}

.thesis-workspace {
  display: grid;
  grid-template-columns: minmax(126px, 25%) minmax(0, 1fr);
  align-items: start;
  gap: 14px;
}

.thesis-chapters {
  min-width: 0;
  border-inline-start: 1px solid var(--color-border);
}

.thesis-chapters button {
  width: 100%;
  display: block;
  min-height: 37px;
  padding: 7px 9px;
  border: 0;
  border-inline-start: 2px solid transparent;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-family: var(--font-display);
  text-align: start;
  overflow-wrap: anywhere;
}

.thesis-chapters button:hover,
.thesis-chapters button[aria-current] {
  background: var(--color-surface-soft);
  color: var(--color-primary);
}

.thesis-chapters button[aria-current] {
  border-inline-start-color: currentColor;
  font-weight: 650;
}

.thesis-chapters button:disabled {
  cursor: default;
  opacity: 0.74;
}

.thesis-chapters small {
  display: block;
  margin-top: 2px;
  color: var(--color-text-secondary);
  font: 0.72rem/1.45 var(--font-body);
}

.thesis-content {
  min-width: 0;
  overflow-wrap: anywhere;
}

.chapter-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.chapter-toolbar h4 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.1rem;
}

.chapter-editor {
  display: grid;
  gap: 10px;
}

.chapter-editor label {
  font-weight: 650;
  font-size: 0.87rem;
}

.editor-selection-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.selection-error {
  margin: 0;
  color: var(--color-error);
}

.diagram-edit-note {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 0.84rem;
  line-height: 1.6;
}

.chapter-editor textarea {
  color: var(--color-text);
  background: var(--color-bg);
  font: 1.02rem/1.9 var(--font-display);
  resize: vertical;
}

.editor-actions {
  justify-content: end;
}

.chapter-pending,
.thesis-empty {
  padding: 16px;
  color: var(--color-text-secondary);
  background: var(--color-bg);
  border-block: 1px solid var(--color-border);
}

.thesis-empty p {
  margin: 0;
}

.thesis-footnote {
  margin-top: 20px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}

@container (max-width: 640px) {
  .thesis-workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .thesis-chapters {
    display: flex;
    gap: 5px;
    overflow-x: auto;
    padding: 5px 4px;
    border-inline-start: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .thesis-chapters button {
    width: auto;
    min-height: 44px;
    white-space: nowrap;
    border-inline-start: 0;
    border-bottom: 2px solid transparent;
  }

  .thesis-chapters button[aria-current] {
    border-bottom-color: currentColor;
  }
}
</style>
