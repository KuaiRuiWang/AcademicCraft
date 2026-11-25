# AcademicCraft · 百夜应用工坊

<img src="wang-ai-code-fronten/src/assets/logo.png" width="72" alt="百夜应用工坊标志">

用自然语言创建 Web 应用，在对话中迭代功能、预览效果并导出源码。项目支持静态网站、Vue 工程和 Vue + Node.js + MySQL 全栈应用，也可以根据应用的实际源码生成可编辑的论文初稿。

[快速开始](#快速开始) · [文档](#文档) · [问题反馈](https://github.com/KuaiRuiWang/AcademicCraft/issues)

## 功能

- **对话生成与修改。** 创建应用后，可继续对话修改。AG-UI 按智能体展示生成阶段、工具执行结果和对话历史。
- **预览、下载与部署。** 生成后可预览页面、下载源码。配置 Nginx 后可访问部署产物，全栈应用通过 Docker Compose 部署。
- **应用与论文初稿。** 按源码版本整理项目材料、逐章生成初稿，支持编辑、重试和 Word 导出。Diagram Design 配图支持页面预览与 Word 插图，并兼容已有 Mermaid 初稿。缺少执行记录的测试标为「待验证」。
- **应用模板。** 内置活动报名表、库存台账、团队看板。模板库中的上架模板可免费领取，来源和许可见模板详情。

后端使用 Spring Boot 3.5、LangChain4j 和 LangGraph4j，前端使用 Vue 3、TypeScript 和 Vite。

## 快速开始

### 环境要求

- JDK 21，Node.js 22.12 或更新版本（含 npm）。后端使用仓库自带的 Maven Wrapper。
- MySQL 8、Redis 和 MinIO。Redis 的 `default` 用户需要设置密码。
- 可用的模型服务账户。Word 导出需要额外安装 [导出工具](agent/应用论文生成接入说明.md#安装-word-导出工具)。

### 下载代码和初始化数据库

```sh
git clone --branch dev https://github.com/KuaiRuiWang/AcademicCraft.git
cd AcademicCraft
mysql -u root -p
```

在从仓库根目录打开的 MySQL 客户端中执行以下命令。

```sql
CREATE DATABASE academiccraft CHARACTER SET utf8mb4;
USE academiccraft;
SOURCE sql/init.sql;
```

已有数据库可按当前库结构执行对应的 [升级脚本](#数据库升级)。

### 配置后端

将 [application-temp.yml](src/main/resources/application-temp.yml) 复制为 `src/main/resources/application-dev.yml`，填写本机的连接信息。默认使用 `dev` 配置，本机文件已加入 Git 忽略规则。

| 配置项 | 填写方式 |
| --- | --- |
| `mysql.host`、`mysql.port`、`mysql.username`、`mysql.password`、`mysql.database` | 使用能访问开发数据库的账户。数据库名与初始化时一致，例如 `academiccraft`。 |
| `redis.host`、`redis.port`、`redis.password` | 填写 Redis 连接信息和 `default` 用户的密码。 |
| `deepseek.api-key`、`deepseek.model-name` | 填写模型密钥和默认模型名。更换服务时，在本地配置中覆盖 `langchain4j.open-ai` 下对应模型的地址、密钥和模型名。 |
| `minio.endpoint`、`minio.accessKey`、`minio.secretKey` | 填写 MinIO API 地址和凭据，并准备 `ai-code-public`、`ai-code-private` 两个桶。 |
| `pexels.api-key` | 使用图片搜索时填写 Pexels 密钥。 |

模型服务、路由和存储的基础配置见 [application.yml](src/main/resources/application.yml)。

### 启动后端和前端

在仓库根目录启动后端。Windows PowerShell 使用以下命令。

```powershell
.\mvnw.cmd spring-boot:run
```

Linux 和 macOS 使用以下命令。

```sh
sh ./mvnw spring-boot:run
```

另开一个终端，从仓库根目录启动前端。

```sh
cd wang-ai-code-fronten
npm ci
npm run dev
```

打开 [http://localhost:5173](http://localhost:5173)，注册、登录后选择「创建应用」。可以输入「创建一个活动报名页，记录报名人和人数，并展示报名汇总」试用生成和预览。

[健康检查](http://localhost:8123/api/health/)返回 `ok` 表示后端已启动，[API 文档](http://localhost:8123/api/doc.html)可查看接口定义。

前端默认调用 `http://localhost:8123/api`。后端端口或部署地址变化时，通过 [前端环境变量](wang-ai-code-fronten/README.md#环境变量)设置 API 地址。

管理端独立运行，默认端口为 5181。管理员使用独立账号登录，可管理平台业务、模型和各 AI 步骤的模型配置。启动配置见[管理端开发文档](wang-ai-code-admin/README.md)。

## 文档

| 文档 | 内容 |
| --- | --- |
| [前端开发](wang-ai-code-fronten/README.md) | 环境变量、路由、构建和接口类型生成 |
| [管理端开发](wang-ai-code-admin/README.md) | 管理员初始化、模型和步骤配置、独立构建与发布 |
| [AG-UI 对话接入](agent/AG-UI对话接入.md) | SSE 事件、历史快照和跨端测试 |
| [论文初稿与 Word 导出](agent/应用论文生成接入说明.md) | 源码快照、章节编辑、导出依赖和校验 |
| [全栈设计记录](agent/全栈项目生成模块设计与实现.md) · [任务记录](agent/全栈项目生成模块-任务文档.md) | Vue + Node.js 全栈生成、构建与部署的设计和开发历史 |

### 目录结构

```text
src/main/              Java 后端、提示词和部署模板
src/test/              后端测试
wang-ai-code-fronten/  Vue 前端、内置模板和前端测试
wang-ai-code-admin/    独立 Vue 管理端
sql/                   数据库初始化与升级脚本
tools/thesis/          Word 导出工具和测试
agent/                 功能接入文档与设计记录
```

### 数据库升级

新环境使用 [init.sql](sql/init.sql)。已有环境升级时，按实际库结构执行尚未运行的脚本，完成后启动新版后端。

| 脚本 | 变更 |
| --- | --- |
| [upgrade_fullstack.sql](sql/upgrade_fullstack.sql) | 增加全栈生成与容器部署字段 |
| [upgrade_chat_history.sql](sql/upgrade_chat_history.sql) | 扩容对话正文 |
| [upgrade_ag_ui.sql](sql/upgrade_ag_ui.sql) | 增加 AG-UI 展示快照字段 |
| [upgrade_app_workshop.sql](sql/upgrade_app_workshop.sql) | 增加模板来源及许可字段。待核对的上架项目转为草稿，可由管理员核对后重新上架。 |
| [upgrade_thesis.sql](sql/upgrade_thesis.sql) | 增加论文生成选项、任务和章节表 |
| [upgrade_platform_admin.sql](sql/upgrade_platform_admin.sql) | 增加管理员、角色、模型和步骤绑定，支持管理员发布平台模板 |

## 参与贡献

可以在 [GitHub Issues](https://github.com/KuaiRuiWang/AcademicCraft/issues) 报告问题并附上复现步骤。提交 Pull Request 时，请基于 `dev` 并说明改动和验证方式。前端执行 `npm run build`，后端按涉及的模块运行对应测试。

## 第三方与模板许可

Word 导出复用了 `lunwen-skill` 的部分代码，来源和授权见 [第三方声明](tools/thesis/NOTICE.md)。[内置 HTML 模板](wang-ai-code-fronten/public/templates)的许可记录在各模板文件中，模板库中其他作品的许可见详情页。
