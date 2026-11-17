<script setup lang="ts">
import { computed, ref, watchEffect } from 'vue'
import {
  CheckOutlined,
  ExclamationCircleOutlined,
  LoadingOutlined,
  RightOutlined,
} from '@ant-design/icons-vue'
import type { AgentGroup, AgentRun, StepStatus } from '@/utils/agentRun'
import { formatDuration } from '@/utils/agentRun'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AgentSection from './AgentSection.vue'

const props = defineProps<{ run: AgentRun; fullstack?: boolean }>()
const now = ref(Date.now())
watchEffect((onCleanup) => {
  if (props.run.status !== 'running') return
  const timer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
  onCleanup(() => window.clearInterval(timer))
})
const labels = {
  running: '正在生成应用',
  success: '本轮生成已完成',
  error: '本轮生成遇到问题',
  interrupted: '本轮连接已中断',
}
const roles = ['architect', 'frontend', 'backend', 'main']
const hasChildren = computed(() => props.run.groups.some((group) => group.role !== 'main'))
const sections = computed(() => {
  const grouped = new Map<string, AgentGroup[]>()
  for (const group of props.run.groups) {
    if (hasChildren.value && group.role === 'main') continue
    const agents = grouped.get(group.role) || []
    agents.push(group)
    grouped.set(group.role, agents)
  }
  return [...grouped.entries()]
    .sort(([a], [b]) => roles.indexOf(a) - roles.indexOf(b))
    .map(([role, agents]) => ({ role, agents }))
})
const rootLogs = computed(() =>
  props.run.groups
    .filter((group) => group.role === 'main')
    .flatMap((group) => group.blocks)
    .filter((block) => block.kind === 'text' && block.content.trim()),
)

type Phase = { label: string; status: StepStatus | 'pending' }
const phases = computed<Phase[]>(() => {
  const groupStatus = (names: string[]): StepStatus | 'pending' => {
    const groups = names
      .map((name) => props.run.groups.filter((group) => group.role === name).slice(-1)[0])
      .filter(Boolean) as AgentGroup[]
    if (!groups.length) return 'pending'
    if (groups.some((group) => group.status === 'running')) return 'running'
    if (groups.some((group) => group.status === 'error')) return 'error'
    if (groups.some((group) => group.status === 'interrupted')) return 'interrupted'
    return groups.length === names.length || props.run.status === 'success' ? 'success' : 'running'
  }
  const build = [...props.run.steps].reverse().find((step) => step.name === 'build')
  const modify = props.run.steps.find((step) => step.name === 'modify_router')
  if (modify)
    return [
      { label: '分析修改', status: modify.status },
      {
        label: '修改代码',
        status: groupStatus(
          props.run.groups.filter((group) => group.role !== 'main').map((group) => group.role),
        ),
      },
    ]
  if (props.fullstack) {
    const contract = [...props.run.steps]
      .reverse()
      .find((step) => step.name.startsWith('contract_check_'))
    return [
      { label: '接口设计', status: groupStatus(['architect']) },
      { label: '并行生成', status: groupStatus(['frontend', 'backend']) },
      {
        label: contract?.status === 'warning' ? '校验与修复' : '契约校验',
        status: contract?.status || 'pending',
      },
      { label: '项目构建', status: build?.status || 'pending' },
    ]
  }
  return [
    { label: '生成代码', status: build ? 'success' : props.run.status },
    ...(build ? [{ label: '项目构建', status: build.status }] : []),
  ]
})
const elapsed = computed(() => {
  const duration = formatDuration(props.run.startedAt, props.run.endedAt ?? now.value)
  return duration && props.run.status === 'interrupted' ? `已记录 ${duration}` : duration
})
</script>

<template>
  <div class="agent-run-view">
    <div class="run-heading">
      <strong aria-live="polite">{{ labels[run.status] }}</strong>
      <span class="run-time">{{ elapsed }}</span>
    </div>
    <ol class="run-phases" aria-label="本轮生成阶段">
      <li
        v-for="phase in phases"
        :key="phase.label"
        :class="phase.status"
        :aria-current="phase.status === 'running' ? 'step' : undefined"
      >
        <CheckOutlined v-if="phase.status === 'success'" aria-hidden="true" />
        <LoadingOutlined v-else-if="phase.status === 'running'" spin aria-hidden="true" />
        <ExclamationCircleOutlined
          v-else-if="phase.status === 'error' || phase.status === 'warning'"
          aria-hidden="true"
        />
        <span v-else class="phase-dot" aria-hidden="true"></span>
        {{ phase.label }}
      </li>
    </ol>
    <AgentSection
      v-for="section in sections"
      :key="section.role"
      :agents="section.agents"
      :now="now"
    />
    <p v-if="!sections.length && run.status === 'running'" class="waiting-output">
      正在准备生成服务…
    </p>
    <details v-if="hasChildren && (rootLogs.length || run.steps.length)" class="workflow-records">
      <summary><RightOutlined aria-hidden="true" />工作流记录</summary>
      <ol class="step-records">
        <li v-for="step in run.steps" :key="step.id" :class="step.status">
          <span>{{ step.label }}</span
          ><span>{{
            {
              running: '执行中',
              success: '已完成',
              warning: '需注意',
              error: '失败',
              interrupted: '未完成',
            }[step.status]
          }}</span>
        </li>
      </ol>
      <template v-for="block in rootLogs" :key="block.id"
        ><MarkdownRenderer v-if="block.kind === 'text'" :content="block.content"
      /></template>
    </details>
    <p v-if="run.error" class="run-error" role="alert">
      <ExclamationCircleOutlined aria-hidden="true" /><span>{{ run.error }}</span>
    </p>
  </div>
</template>

<style scoped>
.agent-run-view {
  min-width: 0;
  color: var(--color-text);
}
.run-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-block: 2px 9px;
}
.run-heading strong {
  font-size: 14px;
  font-weight: 500;
}
.run-time {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.run-phases {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin: 0;
  padding: 0 0 14px;
  list-style: none;
  border-bottom: 1px solid var(--color-border);
}
.run-phases li {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.run-phases .running {
  color: var(--color-primary);
}
.run-phases .error {
  color: #a1271e;
}
.run-phases .warning {
  color: #86591b;
}
.phase-dot {
  width: 5px;
  height: 5px;
  background: var(--color-border);
  border-radius: 50%;
  margin: 3px;
}
.waiting-output {
  margin: 14px 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.workflow-records {
  margin-top: 12px;
}
.workflow-records summary {
  display: flex;
  align-items: center;
  gap: 7px;
  list-style: none;
  min-height: 44px;
  cursor: pointer;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.workflow-records summary::-webkit-details-marker {
  display: none;
}
.workflow-records summary .anticon {
  font-size: 10px;
}
.workflow-records[open] > summary .anticon {
  transform: rotate(90deg);
}
.step-records {
  list-style: none;
  padding: 0;
  margin: 0 0 12px;
}
.step-records li {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 5px 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.step-records .error {
  color: #a1271e;
}
.step-records .warning {
  color: #86591b;
}
.run-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 0;
  margin: 8px 0 0;
  color: #a1271e;
  font-size: 13px;
  overflow-wrap: anywhere;
}
.run-error .anticon {
  margin-top: 3px;
  flex: 0 0 auto;
}
</style>
