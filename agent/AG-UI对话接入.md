# AG-UI 智能体分组对话

## 启用

1. 存量数据库先执行 `sql/upgrade_ag_ui.sql` 一次，再部署新版后端。新库使用 `sql/init.sql`。
2. 后端使用 JDK 21 构建、启动；继续使用现有数据库、Redis、模型、MinIO 和部署配置。
3. 前端运行 `npm ci`、`npm run build`。AG-UI 客户端与协议类型固定为 `1.0.4`。

## 数据链路

新入口为 `POST /api/app/{appId}/agent/run`，接收标准文本 `RunAgentInput`，返回 AG-UI 1.0 SSE 事件。
线程标识必须为 `app-{appId}`；会话登录、应用归属与用户限流仍由服务端校验。
模型上下文与工具定义来自服务端，不接受客户端覆盖。
旧入口 `/api/app/chat/gen/code` 保留原有文本响应契约。
新流设置 `X-Accel-Buffering: no`，并每 15 秒发送 SSE 注释心跳，避免架构师设计、构建期间长时间无输出。

- `AgUiEventAdapter`：文本、工具与工作流生命周期的事件转换。
- `AgUiStreamHandler`：终态处理、阶段性历史快照和断流记录。
- `useAgentRun`：官方 `HttpAgent`、Cookie 登录、按帧合并显示更新。
- `agentRun.ts`：实时消息和历史恢复共用的状态模型。
- `AgentRunView`、`AgentSection`、`ToolCallCard`：按角色分组、修复轮次、代码和替换片段展示。

`TOOL_CALL_END` 仅结束参数生成。成功、失败与未改动状态取自工具的结构化真实结果。
工具文本和代码按数据渲染；Markdown 禁止直接执行 HTML。
构建失败和契约修复耗尽返回 `RUN_ERROR`，不会因流正常关闭而显示成功。

## 历史与断流

`chat_history.message` 保留普通正文，继续用于模型记忆。
`renderData` 保存带版本号的紧凑事件快照，连续文本与工具参数片段按标识合并。
工具返回、智能体结束、阶段结束和整轮结束时更新同一条历史记录。
旧消息没有快照，继续显示 Markdown。

AG-UI HTTP/SSE 绑定不提供断点续传。离开页面会关闭客户端连接；未确认的工具结果显示未知，
不表示后台工具已经停止，也不会自动重发生成请求。刷新后读取已保存的快照。

## 验证

在 JDK 21 环境运行：

```powershell
./mvnw.cmd '-Dtest=AgUi*Test,ToolResultTest,AgentWorkspaceTest,JsonMessageStreamHandlerTest' test
cd wang-ai-code-fronten
npm run test:agent
npm run build
```

Java 测试输出 `target/ag-ui-contract.json` 和 `target/ag-ui-interleaved-tools.json`。
第二份样本通过本地模拟模型响应验证同一智能体的交错工具参数，不调用线上模型。
前端测试将这些实际 Java 事件交给官方 `HttpAgent` 校验，并验证并行归属、工具真实状态和历史恢复；
单独运行前端测试时，跨端用例会在缺少对应样本时跳过。
