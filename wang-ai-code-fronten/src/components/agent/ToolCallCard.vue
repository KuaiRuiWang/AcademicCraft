<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  CheckOutlined,
  DeleteOutlined,
  DiffOutlined,
  ExclamationCircleOutlined,
  FileAddOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  LoadingOutlined,
  RightOutlined,
  ToolOutlined,
} from '@ant-design/icons-vue'
import { formatDuration, type AgentToolBlock } from '@/utils/agentRun'
import CodeSnippet from './CodeSnippet.vue'

const props = defineProps<{ tool: AgentToolBlock; now: number }>()
const expanded = ref(props.tool.status === 'error')
const interacted = ref(false)
const labels: Record<string, string> = {
  writeFile: '写入文件',
  modifyFile: '修改文件',
  readFile: '读取文件',
  readDir: '读取目录',
  deleteFile: '删除文件',
  exit: '完成工具操作',
}
const icons = {
  writeFile: FileAddOutlined,
  modifyFile: DiffOutlined,
  readFile: FileTextOutlined,
  readDir: FolderOpenOutlined,
  deleteFile: DeleteOutlined,
  exit: CheckOutlined,
}
const icon = computed(() => icons[props.tool.name as keyof typeof icons] || ToolOutlined)
const path = computed(() =>
  String(props.tool.arguments.relativeFilePath || props.tool.arguments.relativeDirPath || ''),
)
const statusLabels = {
  preparing: '准备参数',
  running: '等待结果',
  success: '成功',
  error: '失败',
  unchanged: '未改动',
  unknown: '已返回',
  interrupted: '结果未知',
}
  const elapsed = computed(() =>
  props.tool.status === 'interrupted'
    ? ''
    : formatDuration(props.tool.startedAt, props.tool.endedAt || props.now),
)
const rawArguments = computed(() =>
  Object.keys(props.tool.arguments).length
    ? JSON.stringify(props.tool.arguments, null, 2)
    : props.tool.argumentsText,
)
const pending = computed(() => props.tool.status === 'preparing' || props.tool.status === 'running')
const writeContent = computed(() =>
  typeof props.tool.arguments.content === 'string' ? props.tool.arguments.content : '',
)
const before = computed(() =>
  typeof props.tool.arguments.oldContent === 'string' ? props.tool.arguments.oldContent : '',
)
const after = computed(() =>
  typeof props.tool.arguments.newContent === 'string' ? props.tool.arguments.newContent : '',
)
const applied = computed(() => props.tool.status === 'success')
const targetLabel = computed(() =>
  applied.value
    ? '替换后'
    : pending.value
      ? '目标片段（待执行）'
      : props.tool.status === 'error' || props.tool.status === 'unchanged'
        ? '目标片段（未应用）'
        : '目标片段（未确认）',
)

watch(
  () => props.tool.status,
  (status) => {
    if (status === 'error' && !interacted.value) expanded.value = true
  },
)

function handleToggle(event: Event) {
  expanded.value = (event.target as HTMLDetailsElement).open
}
</script>

<template>
  <details
    class="tool-call"
    :class="{ 'has-error': tool.status === 'error' }"
    :open="expanded"
    @toggle="handleToggle"
  >
    <summary @click="interacted = true">
      <RightOutlined class="disclosure" aria-hidden="true" />
      <component :is="icon" class="tool-icon" aria-hidden="true" />
      <span class="tool-title"
        ><span>{{ labels[tool.name] || tool.name }}</span
        ><code v-if="path">{{ path }}</code></span
      >
      <span class="tool-status" :class="tool.status">
        <LoadingOutlined v-if="pending" spin aria-hidden="true" />
        <ExclamationCircleOutlined v-else-if="tool.status === 'error'" aria-hidden="true" />
        <CheckOutlined v-else-if="tool.status === 'success'" aria-hidden="true" />
        {{ statusLabels[tool.status] }}
      </span>
      <span class="tool-elapsed">{{ elapsed }}</span>
    </summary>
    <div v-if="expanded" class="tool-detail">
      <template v-if="tool.name === 'modifyFile' && (before || after)">
        <div class="detail-label">{{ applied ? '替换前' : '请求匹配的原片段' }}</div>
        <CodeSnippet :content="before" :path="path" :tone="applied ? 'before' : undefined" />
        <div class="detail-label">{{ targetLabel }}</div>
        <CodeSnippet :content="after" :path="path" :tone="applied ? 'after' : undefined" />
      </template>
      <template v-else-if="tool.name === 'writeFile' && writeContent">
        <div class="detail-label">{{ applied ? '写入内容' : '请求写入的内容' }}</div>
        <CodeSnippet :content="writeContent" :path="path" />
      </template>
      <template v-else-if="tool.name !== 'exit' && rawArguments && !tool.output">
        <div class="detail-label">调用参数</div>
        <CodeSnippet :content="rawArguments" path="arguments.json" />
      </template>
      <template v-if="tool.output">
        <div class="detail-label">{{ tool.name === 'readDir' ? '目录内容' : '执行输出' }}</div>
        <CodeSnippet :content="tool.output" :path="tool.name === 'readDir' ? '' : path" />
      </template>
      <p class="tool-result" :class="{ error: tool.status === 'error' }">
        {{ tool.summary || (pending ? '正在等待工具的真实执行结果…' : '暂无执行说明') }}
      </p>
    </div>
  </details>
</template>

<style scoped>
.tool-call {
  margin-block: 8px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface-soft);
  min-width: 0;
}
.tool-call.has-error {
  border-color: #d6a19a;
}
summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 12px;
  list-style: none;
  cursor: pointer;
  border-radius: 12px;
  min-height: 44px;
}
summary::-webkit-details-marker {
  display: none;
}
summary:hover {
  background: rgba(15, 118, 110, 0.035);
}
.disclosure {
  font-size: 10px;
  flex: 0 0 auto;
  color: var(--color-text-secondary);
  transition: transform 0.15s ease;
}
details[open] > summary .disclosure {
  transform: rotate(90deg);
}
.tool-icon {
  color: var(--color-text-secondary);
  flex: 0 0 auto;
}
.tool-title {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 3px 8px;
  font-size: 13px;
}
.tool-title code {
  font:
    12px/1.5 'Cascadia Code',
    Consolas,
    monospace;
  overflow-wrap: anywhere;
}
.tool-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.tool-status.success,
.tool-status.running,
.tool-status.preparing {
  color: var(--color-primary);
}
.tool-status.error,
.tool-result.error {
  color: #a1271e;
}
.tool-elapsed {
  white-space: nowrap;
  font-size: 12px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
.tool-detail {
  padding: 0 12px 12px 34px;
  min-width: 0;
}
.detail-label {
  margin-block: 10px 6px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.tool-result {
  margin: 9px 0 0;
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
@media (max-width: 1100px) {
  .tool-title {
    flex-direction: column;
    align-items: flex-start;
  }
  .tool-elapsed {
    display: none;
  }
}
@media (max-width: 480px) {
  .tool-detail {
    padding-left: 12px;
  }
  summary {
    align-items: flex-start;
  }
  .tool-status {
    margin-top: 1px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .disclosure {
    transition: none;
  }
}
</style>
