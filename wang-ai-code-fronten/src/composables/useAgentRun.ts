import { HttpAgent } from '@ag-ui/client'
import { onScopeDispose } from 'vue'
import { useRouter } from 'vue-router'
import type { Event as AgUiEvent } from '@ag-ui/core'
import { API_BASE_URL } from '@/config/env'
import { applyAgentEvent, finishAgentRun, type AgentRun } from '@/utils/agentRun'

export function newRunId() {
  return (
    globalThis.crypto?.randomUUID?.() ||
    `run-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
  )
}

export function useAgentRun() {
  const router = useRouter()
  let activeAgent: HttpAgent | undefined
  let frame: number | undefined
  let flush: (() => void) | undefined

  async function execute(appId: string, prompt: string, run: AgentRun, onUpdate: () => void) {
    const queue: AgUiEvent[] = []
    const partialPaths = new Map<string, Record<string, string>>()
    let receivedEvent = false
    const agent = new HttpAgent({
      url: `${API_BASE_URL}/app/${encodeURIComponent(appId)}/agent/run`,
      threadId: `app-${appId}`,
      initialMessages: [{ id: newRunId(), role: 'user', content: prompt }],
      fetch: async (url, init) => {
        const response = await fetch(url, { ...init, credentials: 'include' })
        if (!response.ok) {
          const error = (await response.json().catch(() => ({}))) as { message?: string }
          if (response.status === 401) {
            void router.push({
              path: '/user/login',
              query: { redirect: router.currentRoute.value.fullPath },
            })
          }
          throw new Error(
            error.message ||
              (response.status === 401 ? '登录已失效，请重新登录' : '无法开始生成，请稍后重试'),
          )
        }
        return response
      },
    })
    activeAgent = agent

    // 每帧合并界面更新；SDK 仍按顺序接收、校验全部协议事件。
    const flushEvents = () => {
      if (frame !== undefined) cancelAnimationFrame(frame)
      frame = undefined
      for (const event of queue.splice(0)) applyAgentEvent(run, event)
      for (const [id, path] of partialPaths) {
        const tool = run.tools[id]?.block
        if (tool?.status === 'preparing') Object.assign(tool.arguments, path)
      }
      partialPaths.clear()
      onUpdate()
    }
    flush = flushEvents
    const schedule = () => {
      if (frame === undefined) frame = requestAnimationFrame(flushEvents)
    }
    try {
      await agent.runAgent(
        { runId: run.id },
        {
          onEvent: ({ event }) => {
            receivedEvent = true
            queue.push(event as AgUiEvent)
            schedule()
          },
          onToolCallArgsEvent: ({ event, partialToolCallArgs }) => {
            const path: Record<string, string> = {}
            for (const key of ['relativeFilePath', 'relativeDirPath']) {
              if (typeof partialToolCallArgs[key] === 'string') path[key] = partialToolCallArgs[key]
            }
            partialPaths.set(event.toolCallId, path)
            schedule()
          },
        },
      )
      flushEvents()
      if (run.status === 'running')
        finishAgentRun(run, 'interrupted', '连接结束但未确认生成结果，请刷新查看已保存的记录')
    } catch (error) {
      flushEvents()
      if (run.status === 'running') {
        const message = error instanceof Error ? error.message : '生成失败，请稍后重试'
        finishAgentRun(
          run,
          receivedEvent ? 'interrupted' : 'error',
          receivedEvent ? '连接已中断，后台执行结果未知，请刷新查看已保存的记录' : message,
        )
      }
    } finally {
      flushEvents()
      if (activeAgent === agent) activeAgent = undefined
      flush = undefined
    }
  }

  onScopeDispose(() => {
    activeAgent?.abortRun()
    flush?.()
  })

  return { execute }
}
