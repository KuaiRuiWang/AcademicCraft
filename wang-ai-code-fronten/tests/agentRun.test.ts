import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import test from 'node:test'
import { EventType, type Event as AgUiEvent } from '@ag-ui/core'
import { HttpAgent } from '@ag-ui/client'
import { applyAgentEvent, createAgentRun, restoreAgentRun } from '../src/utils/agentRun.ts'

test('并行文本和同名工具按调用标识归属各自的智能体', () => {
  const run = createAgentRun('run')
  const events: AgUiEvent[] = [
    { type: EventType.SUBAGENT_STARTED, subagentRunId: 'front', name: 'frontend' },
    { type: EventType.SUBAGENT_STARTED, subagentRunId: 'back', name: 'backend' },
    { type: EventType.TEXT_MESSAGE_START, subagentRunId: 'front', messageId: 'fm' },
    { type: EventType.TEXT_MESSAGE_START, subagentRunId: 'back', messageId: 'bm' },
    { type: EventType.TEXT_MESSAGE_CONTENT, messageId: 'fm', delta: '页面' },
    { type: EventType.TEXT_MESSAGE_CONTENT, messageId: 'bm', delta: '接口' },
    {
      type: EventType.TOOL_CALL_START,
      toolCallId: 'ft',
      toolCallName: 'writeFile',
      parentMessageId: 'fm',
    },
    {
      type: EventType.TOOL_CALL_START,
      toolCallId: 'bt',
      toolCallName: 'writeFile',
      parentMessageId: 'bm',
    },
    { type: EventType.TOOL_CALL_ARGS, toolCallId: 'ft', delta: '{"relativeFilePath":' },
    { type: EventType.TOOL_CALL_ARGS, toolCallId: 'ft', delta: '"frontend/App.vue"}' },
    { type: EventType.TOOL_CALL_END, toolCallId: 'ft' },
  ]
  for (const event of events) applyAgentEvent(run, event)
  assert.equal(run.messages.fm?.block.content, '页面')
  assert.equal(run.messages.bm?.block.content, '接口')
  assert.equal(run.tools.ft?.groupId, 'front')
  assert.equal(run.tools.bt?.groupId, 'back')
  assert.equal(run.tools.ft?.block.arguments.relativeFilePath, 'frontend/App.vue')
  assert.equal(run.tools.ft?.block.status, 'running', '参数结束不代表工具成功')
  applyAgentEvent(run, {
    type: EventType.TOOL_CALL_RESULT,
    messageId: 'result',
    toolCallId: 'ft',
    content: '{"status":"error","summary":"磁盘写入失败"}',
  })
  assert.equal(run.tools.ft?.block.status, 'error')
  assert.equal(run.tools.ft?.block.summary, '磁盘写入失败')
})

test('未收到工具结果时不能随整轮结束显示成功', () => {
  const run = createAgentRun('run')
  applyAgentEvent(run, {
    type: EventType.TOOL_CALL_START,
    toolCallId: 'pending',
    toolCallName: 'writeFile',
  })
  applyAgentEvent(run, { type: EventType.RUN_FINISHED, threadId: 'app-1', runId: 'run' })
  assert.equal(run.status, 'interrupted')
  assert.equal(run.tools.pending?.block.status, 'interrupted')
})

test('同角色的修复轮次独立保存，运行异常保留已完成工具', () => {
  const run = createAgentRun('run')
  for (const event of [
    { type: EventType.SUBAGENT_STARTED, subagentRunId: 'round1', name: 'frontend' },
    {
      type: EventType.TOOL_CALL_START,
      subagentRunId: 'round1',
      toolCallId: 'tool',
      toolCallName: 'modifyFile',
    },
    {
      type: EventType.TOOL_CALL_RESULT,
      subagentRunId: 'round1',
      messageId: 'result',
      toolCallId: 'tool',
      content: '{"status":"unchanged","summary":"文件内容未改变"}',
    },
    { type: EventType.SUBAGENT_FINISHED, subagentRunId: 'round1' },
    { type: EventType.SUBAGENT_STARTED, subagentRunId: 'round2', name: 'frontend' },
    { type: EventType.RUN_ERROR, code: 'GENERATION_FAILED', message: '构建失败' },
  ] as AgUiEvent[])
    applyAgentEvent(run, event)
  assert.deepEqual(
    run.groups.map((group) => group.round),
    [1, 2],
  )
  assert.equal(run.tools.tool?.block.status, 'unchanged')
  assert.equal(run.groups[1]?.status, 'interrupted')
  assert.equal(run.status, 'error')
})

test('历史快照与实时事件使用同一模型，未结束快照显示结果未知', () => {
  const events: AgUiEvent[] = [
    { type: EventType.RUN_STARTED, threadId: 'app-1', runId: 'history', timestamp: 1 },
    {
      type: EventType.TOOL_CALL_START,
      toolCallId: 'tool',
      toolCallName: 'writeFile',
      timestamp: 2,
    },
    { type: EventType.TOOL_CALL_ARGS, toolCallId: 'tool', delta: '{"relativeFilePath":"App.vue"}' },
    { type: EventType.TOOL_CALL_END, toolCallId: 'tool' },
  ]
  const restored = restoreAgentRun(JSON.stringify({ version: 1, runId: 'history', events }))
  assert.equal(restored?.status, 'interrupted')
  assert.equal(restored?.endedAt, 2, '历史耗时停留在最后一次记录，不能随刷新时间增长')
  assert.equal(restored?.tools.tool?.block.status, 'interrupted')
  assert.equal(restored?.tools.tool?.block.arguments.relativeFilePath, 'App.vue')
  assert.equal(restoreAgentRun(null), undefined)
  assert.equal(restoreAgentRun('旧版 Markdown'), undefined)
})

test(
  '实际 Java 输出通过官方 HttpAgent 的事件校验并还原到分组界面',
  {
    skip: !existsSync('../target/ag-ui-contract.json'),
  },
  async () => {
    const events = JSON.parse(readFileSync('../target/ag-ui-contract.json', 'utf8')) as AgUiEvent[]
    const run = createAgentRun('contract-run')
    const agent = new HttpAgent({
      url: 'http://localhost/test-agent',
      threadId: 'app-1',
      initialMessages: [{ id: 'user', role: 'user', content: '生成应用' }],
      fetch: async () =>
        new Response(
          ': keep-alive\n\n' + events.map((event) => `data: ${JSON.stringify(event)}\n\n`).join(''),
          {
            headers: { 'Content-Type': 'text/event-stream' },
          },
        ),
    })

    test(
      '同智能体的交错工具参数通过官方客户端并保留各自文件路径',
      {
        skip: !existsSync('../target/ag-ui-interleaved-tools.json'),
      },
      async () => {
        const events = JSON.parse(
          readFileSync('../target/ag-ui-interleaved-tools.json', 'utf8'),
        ) as AgUiEvent[]
        const run = createAgentRun('interleaved-run')
        const agent = new HttpAgent({
          url: 'http://localhost/test-agent',
          threadId: 'app-1',
          initialMessages: [{ id: 'user', role: 'user', content: '生成应用' }],
          fetch: async () =>
            new Response(
              ': keep-alive\n\n' +
                events.map((event) => `data: ${JSON.stringify(event)}\n\n`).join(''),
              {
                headers: { 'Content-Type': 'text/event-stream' },
              },
            ),
        })
        await agent.runAgent(
          { runId: 'interleaved-run' },
          { onEvent: ({ event }) => applyAgentEvent(run, event as AgUiEvent) },
        )
        assert.equal(run.status, 'success')
        const tools = Object.values(run.tools).map((entry) => entry.block)
        assert.equal(tools.length, 2)
        assert.deepEqual(
          tools.map((tool) => tool.arguments.relativeFilePath),
          ['frontend/A.vue', 'frontend/B.vue'],
        )
        assert.ok(tools.every((tool) => tool.status === 'success'))
      },
    )
    await agent.runAgent(
      { runId: 'contract-run' },
      { onEvent: ({ event }) => applyAgentEvent(run, event as AgUiEvent) },
    )
    assert.equal(run.status, 'success')
    assert.deepEqual(
      run.groups.map((group) => group.role),
      ['frontend', 'backend'],
    )
    const toolStates = Object.values(run.tools).map((entry) => entry.block.status)
    assert.deepEqual(toolStates, ['error', 'success'])
  },
)
