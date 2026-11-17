<script setup lang="ts">
import { computed, ref } from 'vue'
import hljs from 'highlight.js'

const props = withDefaults(
  defineProps<{ content: string; path?: string; tone?: 'before' | 'after' }>(),
  { path: '' },
)
const showAll = ref(false)
const lines = computed(() => props.content.split('\n'))
const clipped = computed(() => lines.value.length > 80 || props.content.length > 16000)
const visible = computed(() =>
  showAll.value ? props.content : lines.value.slice(0, 80).join('\n').slice(0, 16000),
)
const highlighted = computed(() => {
  const extension = props.path.split('.').pop()?.toLowerCase() || ''
  const language = (
    {
      vue: 'xml',
      ts: 'typescript',
      js: 'javascript',
      json: 'json',
      css: 'css',
      html: 'xml',
      sql: 'sql',
    } as Record<string, string>
  )[extension]
  if (language && hljs.getLanguage(language))
    return hljs.highlight(visible.value, { language, ignoreIllegals: true }).value
  return visible.value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
})
</script>

<template>
  <div class="code-snippet" :class="tone">
    <pre><code v-html="highlighted"></code></pre>
    <button
      v-if="clipped"
      type="button"
      class="code-toggle"
      :aria-expanded="showAll"
      @click="showAll = !showAll"
    >
      {{ showAll ? '收起内容' : `展开完整内容（${lines.length} 行）` }}
    </button>
  </div>
</template>

<style scoped>
.code-snippet {
  min-width: 0;
  background: var(--color-surface-soft);
  border-radius: 8px;
  overflow: hidden;
}
pre {
  margin: 0;
  padding: 12px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  tab-size: 2;
}
code {
  font:
    12px/1.65 'Cascadia Code',
    Consolas,
    monospace;
  color: var(--color-text);
}
.before {
  background: #fdf0ee;
  border-inline-start: 1px solid #d48780;
}
.after {
  background: #edf6f1;
  border-inline-start: 1px solid var(--color-primary);
}
.code-toggle {
  display: block;
  width: 100%;
  padding: 8px 12px;
  border: 0;
  border-top: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-primary);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  text-align: start;
}
.code-toggle:hover {
  background: rgba(15, 118, 110, 0.05);
}
</style>
