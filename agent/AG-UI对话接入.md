# AG-UI 对话接入

应用对话通过 AG-UI 流式展示模型输出、生成阶段和工具执行结果，并按智能体恢复历史记录。

## 启用

后端和前端的启动方式见 [快速开始](../README.md#快速开始)。已有数据库升级前执行一次 [upgrade_ag_ui.sql](../sql/upgrade_ag_ui.sql)，新库使用 [init.sql](../sql/init.sql)。前端的 `@ag-ui/client` 与 `@ag-ui/core` 版本均为 `1.0.4`。

## 请求与事件

- `POST /api/app/{appId}/agent/run` 接收 `RunAgentInput`，返回 AG-UI 1.0 SSE 事件。线程标识使用 `app-{appId}`。
- 服务端校验登录、应用归属和用户限流，并提供模型上下文及工具定义。
- SSE 响应带有 `X-Accel-Buffering: no`，每 15 秒发送一次注释心跳。
- `TOOL_CALL_END` 表示工具参数生成完毕。工具执行状态由结构化结果提供。
- 构建失败、契约修复次数耗尽等情况返回 `RUN_ERROR`，前端依据终态事件展示结果。
- 工具文本和代码以数据渲染，Markdown 禁用内联 HTML。
- 旧入口 `/api/app/chat/gen/code` 保留文本响应契约。

## 历史与断流

`chat_history.message` 保存正文，供模型记忆使用。`renderData` 保存带版本的展示快照，并合并连续文本与工具参数。工具返回、智能体结束、阶段结束和整轮结束时更新同一条历史记录。没有快照的历史消息仍按 Markdown 展示。

页面离开时关闭 SSE 连接。前端将尚未确认的工具结果显示为未知，此时后台工具可能仍在执行。刷新后读取最近一次保存的快照，用户可在确认当前状态后主动发起新的请求。

## 代码入口

| 入口 | 职责 |
| --- | --- |
| [`AppController`](../src/main/java/com/wkr/aicode/controller/AppController.java) | 接收请求、检查权限并建立 SSE 连接 |
| [`AgUiEventAdapter`](../src/main/java/com/wkr/aicode/core/handler/AgUiEventAdapter.java) | 转换文本、工具和工作流生命周期事件 |
| [`AgUiStreamHandler`](../src/main/java/com/wkr/aicode/core/handler/AgUiStreamHandler.java) | 处理终态、历史快照和断流 |
| [`useAgentRun.ts`](../wang-ai-code-fronten/src/composables/useAgentRun.ts) | 调用 `HttpAgent` 并合并前端显示更新 |
| [`agentRun.ts`](../wang-ai-code-fronten/src/utils/agentRun.ts) · [`AgentRunView.vue`](../wang-ai-code-fronten/src/components/agent/AgentRunView.vue) | 维护实时与历史状态，按智能体展示对话 |

## 验证

先在仓库根目录运行后端测试。Windows PowerShell 使用以下命令。

```powershell
.\mvnw.cmd '-Dtest=AgUi*Test,ToolResultTest,AgentWorkspaceTest,JsonMessageStreamHandlerTest' test
```

Linux 和 macOS 使用以下命令。

```sh
sh ./mvnw '-Dtest=AgUi*Test,ToolResultTest,AgentWorkspaceTest,JsonMessageStreamHandlerTest' test
```

Java 测试用模拟模型输出生成 `target/ag-ui-contract.json` 和 `target/ag-ui-interleaved-tools.json`。在前端目录安装依赖后运行以下命令。

```sh
npm run test:agent
```

前端用例覆盖并行事件的归属、工具状态和历史恢复。生成上述样本后，还会用 `HttpAgent` 验证实际 Java 事件流的兼容性。
