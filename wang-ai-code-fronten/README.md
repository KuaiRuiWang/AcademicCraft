# 百夜应用工坊前端

基于 Vue 3、TypeScript、Vite、Ant Design Vue 和 Pinia，提供应用创建、对话编辑、在线预览、论文初稿和模板库。

完整环境要求和后端启动步骤见 [项目 README](../README.md#快速开始)。

## 本地开发

先启动后端，再在 `wang-ai-code-fronten/` 目录运行以下命令。

```sh
npm ci
npm run dev
```

Vite 默认监听 [localhost:5173](http://localhost:5173)，后端 API 默认位于 `http://localhost:8123/api`。本地联调时，前端页面与 API 地址统一使用 `localhost`，便于浏览器携带会话 Cookie。

### 环境变量

API 地址需要覆盖时，在前端目录创建 `.env.local`。

```dotenv
VITE_API_BASE_URL=http://localhost:8123/api
```

构建生产版本时可在 `.env.production` 中设置 API 地址。同源部署可使用 `/api`，由站点代理转发给后端。部署产物链接使用站点相对路径，全栈应用位于 `/apps/{deployKey}/`。

## 页面与入口

| 路由 | 用途 |
| --- | --- |
| `/`、`/create` | 浏览应用和入门模板，创建静态网站、Vue 工程或全栈应用 |
| `/app/chat/:id` | 对话生成、预览、下载源码、部署及编辑论文初稿 |
| `/app/edit/:id` | 编辑应用信息 |
| `/project`、`/my/orders` | 浏览模板库、查看领取记录 |
| `/admin/appManage`、`/admin/chatManage`、`/admin/projectManage` | 管理应用、对话和模板，需要管理员权限 |

[路由配置](src/router/index.ts)列出了全部页面。

## 源码结构

| 路径 | 用途 |
| --- | --- |
| [`src/pages/`](src/pages) | 应用工作台、模板库、账户与管理页面 |
| [`src/components/`](src/components) | 页面组件、对话展示与论文面板 |
| [`src/composables/useAgentRun.ts`](src/composables/useAgentRun.ts) | AG-UI 流式请求与状态更新 |
| [`src/api/`](src/api) | 接口请求与类型定义 |
| [`src/stores/`](src/stores) | 登录用户状态 |
| [`tests/`](tests) | AG-UI 状态与论文面板回归测试 |

## 常用命令

| 命令 | 用途 |
| --- | --- |
| `npm run build` | 类型检查和生产构建，产物位于 `dist/` |
| `npm run type-check` | 运行 Vue 类型检查 |
| `npx eslint .` | 检查代码规范 |
| `npm run lint` | 校验并自动修复 ESLint 可修复的问题 |
| `npm run test:agent` | 运行 AG-UI 前端测试 |
| `npm run openapi2ts` | 从已启动的后端生成 API 请求和类型，默认读取 `http://localhost:8123/api/v3/api-docs` |

[AG-UI 跨端测试](../agent/AG-UI对话接入.md#验证) · [论文面板回归测试](../agent/应用论文生成接入说明.md#验证)
