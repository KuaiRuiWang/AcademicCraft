<script setup lang="ts">
import { onMounted, onUnmounted, ref, useId } from 'vue'
import { rewriteThesisSelection, type ThesisRewriteInput } from '@/api/thesisController'

const props = defineProps<{
  appId: string
  chapterNo: number
  selection: Omit<ThesisRewriteInput, 'instruction'>
}>()
const emit = defineEmits<{ apply: [suggestion: string]; close: [] }>()
const id = useId()
const dialog = ref<HTMLDialogElement>()
const instruction = ref('')
const suggestion = ref('')
const error = ref('')
const generating = ref(false)
let request: AbortController | undefined

function clearSuggestion() {
  suggestion.value = ''
  error.value = ''
}

async function generate() {
  if (generating.value) return
  clearSuggestion()
  const requirement = instruction.value.trim()
  if (!requirement) {
    error.value = '请输入修改要求'
    return
  }
  const controller = new AbortController()
  request = controller
  generating.value = true
  try {
    const response = await rewriteThesisSelection(props.appId, props.chapterNo, {
      ...props.selection,
      instruction: requirement,
    }, controller.signal)
    if (response.data.code !== 0) throw new Error(response.data.message || 'AI 修改失败，请重试')
    if (!controller.signal.aborted) suggestion.value = response.data.data
  } catch (reason) {
    if (!controller.signal.aborted) {
      error.value = reason instanceof Error ? reason.message : 'AI 修改失败，请重试'
    }
  } finally {
    generating.value = false
  }
}

function apply() {
  if (!suggestion.value || generating.value) return
  emit('apply', suggestion.value)
  dialog.value?.close()
}

onMounted(() => dialog.value?.showModal())
onUnmounted(() => request?.abort())
</script>

<template>
  <dialog ref="dialog" class="rewrite-dialog" :aria-labelledby="`${id}-title`" @close="emit('close')">
    <form method="post" class="rewrite-form" :aria-labelledby="`${id}-title`" @submit.prevent="generate">
      <h1 :id="`${id}-title`">AI 局部修改</h1>
      <div class="rewrite-field">
        <label :for="`${id}-instruction`">修改要求</label>
        <a-textarea
          :id="`${id}-instruction`"
          v-model:value="instruction"
          name="instruction"
          :rows="3"
          :maxlength="500"
          :readonly="generating"
          placeholder="例如，精简成一句话并保留技术名词"
          required
          autofocus
          @input="clearSuggestion"
        />
      </div>
      <div class="rewrite-comparison" :class="{ 'has-suggestion': suggestion }">
        <section :aria-labelledby="`${id}-original`">
          <h2 :id="`${id}-original`">选中原文</h2>
          <p tabindex="0">{{ selection.selectedText }}</p>
        </section>
        <section v-if="suggestion" :aria-labelledby="`${id}-suggestion`">
          <h2 :id="`${id}-suggestion`">修改建议</h2>
          <p tabindex="0">{{ suggestion }}</p>
        </section>
      </div>
      <p v-if="error" class="rewrite-error" role="alert">{{ error }}</p>
      <p class="rewrite-status" role="status">
        {{ generating ? '正在修改选中内容…' : suggestion ? '建议已生成。' : '' }}
      </p>
      <div class="rewrite-actions">
        <a-button html-type="button" @click="dialog?.close()">取消</a-button>
        <a-button :type="suggestion ? 'default' : 'primary'" html-type="submit" :loading="generating">
          {{ suggestion ? '重新生成建议' : '生成建议' }}
        </a-button>
        <a-button v-if="suggestion" type="primary" html-type="button" @click="apply">替换选区</a-button>
      </div>
    </form>
  </dialog>
</template>

<style scoped>
.rewrite-dialog {
  box-sizing: border-box;
  inline-size: min(42rem, calc(100% - 1.5rem));
  max-block-size: 85vh;
  max-block-size: 85dvh;
  padding: clamp(1rem, 3vw, 1.5rem);
  border: 1px solid var(--color-border);
  border-radius: 2px;
  background: var(--color-surface);
  color: var(--color-text);
}

.rewrite-dialog:modal {
  overscroll-behavior: contain;
}

.rewrite-dialog::backdrop {
  overflow: hidden;
  overscroll-behavior: contain;
  background: rgb(32 32 25 / 42%);
}

.rewrite-form,
.rewrite-comparison {
  display: grid;
  gap: 1rem;
}

.rewrite-form h1 {
  margin: 0;
  font-size: 1.3rem;
}

.rewrite-field label,
.rewrite-comparison h2 {
  display: block;
  margin: 0 0 0.375rem;
  font: inherit;
  font-weight: 650;
}

.rewrite-field :deep(textarea) {
  font-size: 1rem;
}

.rewrite-comparison section {
  min-inline-size: 0;
}

.rewrite-comparison p {
  margin: 0;
  padding: 0.625rem 0.75rem;
  max-block-size: 10rem;
  overflow: auto;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  border: 1px solid var(--color-border);
  background: var(--color-surface-soft);
}

.rewrite-error,
.rewrite-status {
  margin: 0;
  color: var(--color-text-secondary);
}

.rewrite-error {
  color: var(--color-error);
}

.rewrite-status:empty {
  position: absolute;
  clip-path: inset(50%);
}

.rewrite-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 0.5rem;
}

@media (min-width: 620px) {
  .rewrite-comparison.has-suggestion {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
