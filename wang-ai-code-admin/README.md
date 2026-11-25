# 应用工坊管理端

管理端是仓库内独立的 Vue 3 和 Vite 应用，使用同一个 Spring Boot 后端。

## 启动

1. 新库执行 [初始化 SQL](../sql/init.sql)。已有库在切换新版后端前执行一次 [管理端升级 SQL](../sql/upgrade_platform_admin.sql)。
2. 第一次启动新版后端前设置 `PLATFORM_ADMIN_BOOTSTRAP_USERNAME` 和 `PLATFORM_ADMIN_BOOTSTRAP_PASSWORD`。后端用这两个变量创建初始超级管理员，创建后可以从服务配置中移除这两个变量。前台用户表中的账号继续用于前台登录。
3. 服务端设置持久的 `PLATFORM_MODELS_ENCRYPTION_KEY`，使用 32 字节随机值的 Base64 编码。例如可以用 `openssl rand -base64 32` 生成一次并存入部署的密钥管理。各实例使用同一份密钥。数据库恢复时需要原密钥才能读取已有模型凭据。
4. 启动后端，从此目录运行以下命令。

```sh
npm ci
npm run dev
```

打开 [管理端](http://localhost:5181)，使用初始化的管理员账号登录。超级管理员可在「管理员与角色」里创建其他管理员、分配角色、启用或停用账号。角色按业务分别授予读取和写入权限。需要在页面操作时，同时授予该页面的读取权限。

## 模型配置

在「模型管理」中填写显示名称、接口协议、Base URL、供应商模型 ID 和 API Key。文本模型的最大输出 Token 和温度可以留空，使用供应商默认值。Base URL 填 API 根地址。编辑时可留空 API Key 沿用原密钥，切换服务域名时需填写新服务的 API Key。API Key 加密入库，界面只显示配置状态。

| 接口 | Base URL 示例 | 说明 |
| --- | --- | --- |
| OpenAI | `https://api.openai.com/v1` | 填实际使用的 Chat Completions 模型 ID。[OpenAI API 文档](https://developers.openai.com/api/reference/overview/#authentication) |
| DeepSeek | `https://api.deepseek.com` | 模型 ID 示例为 `deepseek-flash)。[DeepSeek API 文档](https://api-docs.deepseek.com/zh-cn/) |
| 阿里云百炼文本 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | 使用模型所在地域的地址和 API Key。[百炼 Base URL](https://www.alibabacloud.com/help/en/model-studio/base-url) |
| Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | 使用 Gemini 的 OpenAI 兼容接口和 Gemini API Key。[Gemini 兼容接口文档](https://ai.google.dev/gemini-api/docs/openai) |
| DashScope Logo 图像 | `https://dashscope.aliyuncs.com/api/v1` | 填支持异步文生图的模型 ID，例如 `wan2.2-t2i-flash)。[百炼 Base URL](https://www.alibabacloud.com/help/en/model-studio/base-url) |

自建或第三方兼容网关使用 HTTPS。服务端可通过 `PLATFORM_MODELS_EXTRA_HOSTS` 增加允许的域名，用英文逗号分隔。用于代码 Agent 的文本模型需要支持流式工具调用。

在「步骤配置」中给具体任务选模型，例如「接口契约设计」「前端修复」「章节正文」「AI 局部修改」。每一步可单独保存，下一次进入对应步骤时读取最新配置。选择「部署默认」即可使用后端为该步骤配置的模型。删除模型前，先将引用它的步骤改绑其他模型或切回部署默认。

## 构建与发布

```sh
npm ci
npm run build
```

将此目录的 `dist/` 发布到独立管理端域名。该站点的 `/api` 反向代理到 Spring Boot，其他前端路由回退到 `index.html)。给 `VITE_WORKSHOP_URL` 设置前台地址，管理端通过它打开应用预览和模板详情。

| 环境变量 | 默认值与用法 |
| --- | --- |
| `VITE_ADMIN_API_PROXY` | 本地开发代理目标，默认 `http://127.0.0.1:8123` |
| `VITE_ADMIN_API_BASE_URL` | 浏览器使用的 API 根路径，默认 `/api` |
| `VITE_ADMIN_BASE` | 前端资源和路由的根路径，默认 `/` |
| `VITE_WORKSHOP_URL` | 前台站点地址，本地默认 `http://localhost:5173` |
