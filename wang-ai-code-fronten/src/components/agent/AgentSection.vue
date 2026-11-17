<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  CheckOutlined,
  CodeOutlined,
  DeploymentUnitOutlined,
  ExclamationCircleOutlined,
  LoadingOutlined,
  RightOutlined,
  DatabaseOutlined,
} from '@ant-design/icons-vue'
import type { AgentGroup } from '@/utils/agentRun'
import { formatDuration } from '@/utils/agentRun'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import ToolCallCard from './ToolCallCard.vue'

const props = defineProps<{ agents: AgentGroup[]; now: number }>()
const latest = computed(() => props.agents[props.agents.length - 1]!)
const expanded = ref(
  props.agents.some((agent) => agent.status === 'running' || agent.status === 'error'),
)
const toolCount = computed(() =>
  props.agents.reduce(
    (count, agent) => count + agent.blocks.filter((block) => block.kind === 'tool').length,
    0,
  ),
)
const errorCount = computed(() =>
  props.agents.reduce(
    (count, agent) =>
      count +
      agent.blocks.filter((block) => block.kind === 'tool' && block.status === 'error').length,
    0,
  ),
)
const roleIcon = computed(() =>
  latest.value.role === 'architect'
    ? DeploymentUnitOutlined
    : latest.value.role === 'backend'
      ? DatabaseOutlined
      : CodeOutlined,
)
const statuses = { running: '执行中', success: '已完成', error: '失败', interrupted: '未完成' }
</script>

<template>
  <details
    class="agent-section"
    :open="expanded"
    @toggle="expanded = ($event.target as HTMLDetailsElement).open"
  >
    <summary>
      <RightOutlined class="disclosure" aria-hidden="true" />
      <component :is="roleIcon" aria-hidden="true" />
      <span class="agent-label">{{ latest.label }}</span>
      <span class="agent-count">{{
        toolCount
          ? `${toolCount} 次工具调用${errorCount ? ` · ${errorCount} 次失败` : ''}`
          : latest.role === 'architect'
            ? '接口契约设计'
            : '代码与说明'
      }}</span>
      <span class="agent-status" :class="latest.status">
        <LoadingOutlined v-if="latest.status === 'running'" spin aria-hidden="true" />
        <ExclamationCircleOutlined v-else-if="latest.status === 'error'" aria-hidden="true" />
        <CheckOutlined v-else-if="latest.status === 'success'" aria-hidden="true" />
        {{ statuses[latest.status] }}
      </span>
    </summary>
    <div v-if="expanded" class="agent-body">
      <div v-for="agent in agents" :key="agent.id" class="agent-invocation">
        <div v-if="agents.length > 1" class="round-label">
          第 {{ agent.round }} 轮 · {{ statuses[agent.status] }}
          <span>{{ formatDuration(agent.startedAt, agent.endedAt || now) }}</span>
        </div>
        <template v-for="block in agent.blocks" :key="block.id">
          <MarkdownRenderer
            v-if="block.kind === 'text' && block.content.trim()"
            :content="block.content"
          />
          <ToolCallCard v-else-if="block.kind === 'tool'" :tool="block" :now="now" />
        </template>
        <p v-if="agent.blocks.length === 0 && agent.status === 'running'" class="preparing">
          正在准备输出…
        </p>
      </div>
    </div>
  </details>
</template>

<style scoped>
.agent-section {
  border-bottom: 1px solid var(--color-border);
  min-width: 0;
}
summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 13px 0;
  list-style: none;
  cursor: pointer;
  min-height: 46px;
  border-radius: 4px;
}
summary::-webkit-details-marker {
  display: none;
}
summary:hover .agent-label {
  color: var(--color-primary);
}
.disclosure {
  font-size: 10px;
  color: var(--color-text-secondary);
  transition: transform 0.15s ease;
}
details[open] > summary .disclosure {
  transform: rotate(90deg);
}
.agent-label {
  font-weight: 500;
  font-size: 14px;
}
.agent-count {
  color: var(--color-text-secondary);
  font-size: 12px;
  flex: 1;
}
.agent-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-inline-start: auto;
}
.agent-status.success,
.agent-status.running {
  color: var(--color-primary);
}
.agent-status.error {
  color: #a1271e;
}
.agent-body {
  margin-inline-start: 6px;
  padding: 0 0 6px 14px;
  border-inline-start: 1px solid var(--color-border);
  min-width: 0;
}
.round-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-block: 6px 12px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.round-label span {
  font-variant-numeric: tabular-nums;
}
.agent-invocation + .agent-invocation {
  border-top: 1px solid var(--color-border);
  margin-top: 16px;
  padding-top: 10px;
}
.preparing {
  margin: 0 0 12px;
  color: var(--color-text-secondary);
  font-size: 13px;
}
@media (max-width: 480px) {
  .agent-count {
    flex-basis: calc(100% - 42px);
    order: 2;
    margin-inline-start: 40px;
  }
  .agent-body {
    margin-inline-start: 0;
    padding-left: 10px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .disclosure {
    transition: none;
  }
}
</style>
