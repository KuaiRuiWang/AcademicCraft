<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  downloadDocument, generateDocument, importDocumentTemplate, readDocument,
  type DocumentKind, type GraduationDocument,
} from '@/api/graduationDocuments'

const props = defineProps<{ appId: string; kind: DocumentKind; label: string }>()
const document = ref<GraduationDocument>()
const chosen = ref('default')
const action = ref('')
const error = ref('')
const fileInput = ref<HTMLInputElement>()
const busy = computed(() => Boolean(action.value) || document.value?.status === 'generating')
const fields = computed(() => chosen.value === 'default' ? document.value?.defaultFields : document.value?.templateFields)
let poll: ReturnType<typeof setTimeout>
let closed = false

async function refresh(preferImported = false) {
  clearTimeout(poll)
  const state = await readDocument(props.appId, props.kind)
  if (closed) return
  document.value = state
  if (preferImported || (chosen.value !== 'default' && chosen.value !== state.templateId)) {
    chosen.value = state.templateId || 'default'
  }
  if (state.status === 'generating') poll = setTimeout(load, 2200)
}

function report(cause: unknown) {
  error.value = cause instanceof Error ? cause.message : '操作失败，请重试'
}

async function load() {
  error.value = ''
  try { await refresh(!document.value) } catch (cause) { report(cause) }
}

async function upload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  error.value = ''
  if (!/\.docx$/i.test(file.name) || !file.size || file.size > 5 * 1024 * 1024) {
    error.value = '请选择 5 MB 以内的 .docx 文件'
    return
  }
  action.value = 'upload'
  try {
    await importDocumentTemplate(props.appId, props.kind, file)
    await refresh(true)
  } catch (cause) { report(cause) } finally { action.value = '' }
}

async function generate() {
  error.value = ''
  action.value = 'generate'
  try {
    await generateDocument(props.appId, props.kind, chosen.value)
    await refresh()
  } catch (cause) { report(cause) } finally { action.value = '' }
}

async function download() {
  error.value = ''
  action.value = 'download'
  try {
    const file = await downloadDocument(props.appId, props.kind)
    const url = URL.createObjectURL(file)
    const link = window.document.createElement('a')
    link.href = url
    link.download = `${props.label}-${props.appId}.docx`
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (cause) { report(cause) } finally { action.value = '' }
}

onMounted(load)
onUnmounted(() => { closed = true; clearTimeout(poll) })
</script>

<template>
  <section class="document-panel" :aria-label="label">
    <h3>{{ label }}</h3>
    <p v-if="!document && !error" role="status">正在读取材料…</p>
    <template v-if="document">
      <div class="template-controls">
        <label :for="`document-template-${kind}`">Word 模板</label>
        <div class="control-row">
          <select :id="`document-template-${kind}`" v-model="chosen" :disabled="busy">
            <option value="default">内置{{ label }}模板</option>
            <option v-if="document.templateId" :value="document.templateId">{{ document.templateName }}</option>
          </select>
          <input ref="fileInput" hidden type="file" accept=".docx" @change="upload" />
          <a-button :disabled="busy" :loading="action === 'upload'" @click="fileInput?.click()">导入学校模板</a-button>
        </div>
        <p class="hint">支持 5 MB 以内的 .docx。自定义栏目可在填入位置标记 <code v-pre>{{研究内容}}</code>。</p>
        <p v-if="fields?.length" class="fields">生成栏目<span>{{ fields.join('、') }}</span></p>
        <a-button type="primary" :loading="document.status === 'generating' || action === 'generate'"
          :disabled="busy" @click="generate">{{ document.status === 'generating' ? '正在生成' : document.resultTemplateName ? '重新生成' : '生成' + label }}</a-button>
        <p v-if="document.status === 'generating'" role="status">离开页面后仍会继续生成。</p>
        <p v-if="document.error" role="alert">{{ document.error }}</p>
      </div>
      <article v-if="document.resultTemplateName">
        <header class="control-row">
          <div><h4>已生成的{{ label }}</h4><p class="hint">{{ document.resultTemplateName }}</p></div>
          <a-button :disabled="Boolean(action)" :loading="action === 'download'" @click="download">下载 Word</a-button>
        </header>
        <section v-for="(value, title) in document.sections" :key="title" class="document-section">
          <h5>{{ title }}</h5><p>{{ value }}</p>
        </section>
      </article>
    </template>
    <div v-if="error" role="alert" class="control-row">
      <span>{{ error }}</span><a-button size="small" @click="load">重新读取</a-button>
    </div>
  </section>
</template>

<style scoped>
.document-panel { padding: clamp(16px, 3vw, 32px); color: var(--color-text); }
h3, h4, h5 { margin: 0; font-family: var(--font-display); }
h3 { margin-bottom: 1rem; font-size: 1.5rem; }
h4 { font-size: 1.2rem; }
h5 { font-size: 1.05rem; }
.template-controls { display: grid; gap: .65rem; justify-items: start; }
.template-controls label { font-weight: 650; }
.control-row { display: flex; align-items: center; flex-wrap: wrap; gap: .6rem; max-width: 100%; }
select { max-width: 100%; padding: .4rem .6rem; min-height: 34px; border: 1px solid var(--color-border); background: var(--color-surface); color: inherit; font: inherit; }
p { margin: 0; }
.hint, .fields { color: var(--color-text-secondary); font-size: .875rem; line-height: 1.6; overflow-wrap: anywhere; }
.fields span { margin-inline-start: .6rem; }
article { margin-top: 1.6rem; border-top: 1px solid var(--color-border); padding-top: 1.2rem; }
article header { justify-content: space-between; }
.document-section { margin-top: 1rem; }
.document-section p { white-space: pre-line; overflow-wrap: anywhere; line-height: 1.8; }
[role='alert'] { margin-top: .6rem; color: var(--color-error); }
</style>
