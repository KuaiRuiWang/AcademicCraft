import { EventType, type Event as AgUiEvent } from '@ag-ui/core'

export type RunStatus = 'running' | 'success' | 'error' | 'interrupted'
export type ToolStatus = 'preparing' | RunStatus | 'unchanged' | 'unknown'
export type StepStatus = RunStatus | 'warning'

export interface AgentTextBlock {
  kind: 'text'
  id: string
  content: string
  complete: boolean
}

export interface AgentToolBlock {
  kind: 'tool'
  id: string
  name: string
  argumentsText: string
  arguments: Record<string, unknown>
  status: ToolStatus
  summary: string
  output: string
  startedAt: number
  endedAt?: number
}

export interface AgentGroup {
  id: string
  role: string
  label: string
  round: number
  status: RunStatus
  startedAt: number
  endedAt?: number
  blocks: (AgentTextBlock | AgentToolBlock)[]
}

export interface AgentStep {
  id: string
  name: string
  label: string
  status: StepStatus
  startedAt: number
  endedAt?: number
}

export interface AgentRun {
  id: string
  status: RunStatus
  startedAt: number
  endedAt?: number
  error?: string
  groups: AgentGroup[]
  steps: AgentStep[]
  messages: Record<string, { groupId: string; block: AgentTextBlock }>
  tools: Record<string, { groupId: string; block: AgentToolBlock }>
}

const roleLabels: Record<string, string> = {
  architect: '架构师',
  frontend: '前端智能体',
  backend: '后端智能体',
  main: '代码智能体',
}

export function createAgentRun(id: string): AgentRun {
  return { id, status: 'running', startedAt: 0, groups: [], steps: [], messages: {}, tools: {} }
}

function groupFor(run: AgentRun, id = 'main', timestamp = Date.now()): AgentGroup {
  let group = run.groups.find((item) => item.id === id)
  if (!group) {
    group = {
      id,
      role: 'main',
      label: roleLabels.main!,
      round: 1,
      status: 'running',
      startedAt: timestamp,
      blocks: [],
    }
    run.groups.push(group)
  }
  return group
}

function parseArguments(text: string): Record<string, unknown> {
  try {
    const value: unknown = JSON.parse(text)
    return value !== null && typeof value === 'object' && !Array.isArray(value)
      ? (value as Record<string, unknown>)
      : {}
  } catch {
    return {}
  }
}

export function finishAgentRun(
  run: AgentRun,
  status: RunStatus,
  error?: string,
  timestamp = Date.now(),
) {
  if (
    status === 'success' &&
    Object.values(run.tools).some(
      ({ block }) => block.status === 'preparing' || block.status === 'running',
    )
  ) {
    status = 'interrupted'
    error = '部分工具未返回执行结果，本轮结果尚未确认'
  }
  run.status = status
  run.endedAt = timestamp
  run.error = error
  for (const group of run.groups) {
    if (group.status === 'running') {
      group.status = status === 'success' ? 'success' : 'interrupted'
      group.endedAt = timestamp
    }
    for (const block of group.blocks) {
      if (block.kind === 'tool' && (block.status === 'preparing' || block.status === 'running')) {
        block.status = 'interrupted'
        block.summary = '未收到执行结果'
        block.endedAt = timestamp
      }
    }
  }
  for (const step of run.steps) {
    if (step.status === 'running') {
      step.status = status === 'success' ? 'success' : 'interrupted'
      step.endedAt = timestamp
    }
  }
}

/** 同一 reducer 用于实时事件与历史快照，工具结果不会经由 Markdown 猜测。 */
export function applyAgentEvent(run: AgentRun, event: AgUiEvent) {
  const timestamp = event.timestamp ?? Date.now()
  switch (event.type) {
    case EventType.RUN_STARTED:
      run.id = event.runId
      run.startedAt = timestamp
      break
    case EventType.SUBAGENT_STARTED: {
      const group = groupFor(run, event.subagentRunId, timestamp)
      group.role = event.name
      group.label = roleLabels[event.name] || event.description || event.name
      group.round = run.groups.filter((item) => item.role === group.role).length
      break
    }
    case EventType.SUBAGENT_FINISHED:
    case EventType.SUBAGENT_ERROR: {
      const group = groupFor(run, event.subagentRunId, timestamp)
      group.status = event.type === EventType.SUBAGENT_ERROR ? 'error' : 'success'
      group.endedAt = timestamp
      break
    }
    case EventType.TEXT_MESSAGE_START: {
      const group = groupFor(run, event.subagentRunId, timestamp)
      const block: AgentTextBlock = {
        kind: 'text',
        id: event.messageId,
        content: '',
        complete: false,
      }
      group.blocks.push(block)
      run.messages[event.messageId] = { groupId: group.id, block }
      break
    }
    case EventType.TEXT_MESSAGE_CONTENT: {
      const entry = run.messages[event.messageId]
      if (entry) entry.block.content += event.delta
      break
    }
    case EventType.TEXT_MESSAGE_END: {
      const entry = run.messages[event.messageId]
      if (entry) entry.block.complete = true
      break
    }
    case EventType.TOOL_CALL_START: {
      const parent = event.parentMessageId ? run.messages[event.parentMessageId] : undefined
      const group = groupFor(run, event.subagentRunId || parent?.groupId, timestamp)
      const block: AgentToolBlock = {
        kind: 'tool',
        id: event.toolCallId,
        name: event.toolCallName,
        argumentsText: '',
        arguments: {},
        status: 'preparing',
        summary: '',
        output: '',
        startedAt: timestamp,
      }
      group.blocks.push(block)
      run.tools[event.toolCallId] = { groupId: group.id, block }
      break
    }
    case EventType.TOOL_CALL_ARGS: {
      const entry = run.tools[event.toolCallId]
      if (entry) entry.block.argumentsText += event.delta
      break
    }
    case EventType.TOOL_CALL_END: {
      const tool = run.tools[event.toolCallId]?.block
      if (tool) {
        tool.arguments = parseArguments(tool.argumentsText)
        tool.status = 'running'
      }
      break
    }
    case EventType.TOOL_CALL_RESULT: {
      const tool = run.tools[event.toolCallId]?.block
      if (!tool) break
      const content =
        typeof event.content === 'string'
          ? event.content
          : event.content
              .filter((part) => part.type === 'text')
              .map((part) => part.text)
              .join('\n')
      const result = parseArguments(content)
      const status = result.status
      tool.status =
        status === 'success' || status === 'error' || status === 'unchanged' ? status : 'unknown'
      tool.summary = typeof result.summary === 'string' ? result.summary : '工具已返回结果'
      tool.output =
        typeof result.content === 'string'
          ? result.content
          : Object.keys(result).length
            ? ''
            : content
      tool.endedAt = timestamp
      break
    }
    case EventType.STEP_STARTED:
    case EventType.STEP_FINISHED: {
      const id = `${event.subagentRunId || 'main'}:${event.stepName}`
      let step = run.steps.find((item) => item.id === id)
      if (!step) {
        step = {
          id,
          name: event.stepName,
          label: event.stepName,
          status: 'running',
          startedAt: timestamp,
        }
        run.steps.push(step)
      }
      const metadata = event.metadata || {}
      step.label = typeof metadata.label === 'string' ? metadata.label : event.stepName
      if (event.type === EventType.STEP_FINISHED) {
        step.status =
          metadata.status === 'error' || metadata.status === 'warning' ? metadata.status : 'success'
        step.endedAt = timestamp
      }
      break
    }
    case EventType.RUN_FINISHED:
      finishAgentRun(run, 'success', undefined, timestamp)
      break
    case EventType.RUN_ERROR:
      finishAgentRun(
        run,
        event.code === 'CONNECTION_CLOSED' ? 'interrupted' : 'error',
        event.message,
        timestamp,
      )
      break
  }
}

export function restoreAgentRun(renderData?: string | null): AgentRun | undefined {
  if (!renderData) return undefined
  try {
    const snapshot = JSON.parse(renderData) as {
      version?: number
      runId?: string
      events?: AgUiEvent[]
    }
    if (snapshot.version !== 1 || !snapshot.runId || !Array.isArray(snapshot.events))
      return undefined
    const run = createAgentRun(snapshot.runId)
    for (const event of snapshot.events) applyAgentEvent(run, event)
    if (run.status === 'running') {
      const lastRecordedAt =
        [...snapshot.events].reverse().find((event) => typeof event.timestamp === 'number')
          ?.timestamp ?? run.startedAt
      finishAgentRun(
        run,
        'interrupted',
        '上次连接未记录最终结果，可继续对话确认或修复',
        lastRecordedAt,
      )
    }
    return run
  } catch {
    return undefined
  }
}

export function formatDuration(start: number, end: number): string {
  if (!start) return ''
  const seconds = Math.max(0, Math.floor((end - start) / 1000))
  if (seconds === 0) return '不足1秒'
  return seconds < 60 ? `${seconds} 秒` : `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`
}
