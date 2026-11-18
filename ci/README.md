# AcademicCraft Jenkins 部署流水线

Jenkins 位于 `192.168.0.107`，入口为 `http://8.134.185.186:18080/`。
前后端都发布到业务虚拟机 `192.168.0.178`，保持现有双机 Nginx、FRP 和 systemd 结构。

## 任务与源码

| Jenkins 任务 | 流水线文件 | 构建内容 |
| --- | --- | --- |
| `AcademicCraft-Frontend` | `ci/Jenkinsfile.frontend` | Java 协议样本、前端测试、类型检查、Vite 构建 |
| `AcademicCraft-Backend` | `ci/Jenkinsfile.backend` | JDK 21、Maven Wrapper、离线回归测试、Spring Boot JAR |

两条任务使用 Pipeline from SCM：

- 仓库：`https://github.com/KuaiRuiWang/AcademicCraft.git`
- 流水线定义分支：`codex/jenkins-deploy`
- Script Path：上表对应的文件路径。
- 应用源码分支：参数 `SOURCE_BRANCH`，默认 `dev`，与流水线定义分支独立。

流水线定义在工作区根目录检出，应用代码在 `source/` 中检出。
如后续合并流水线分支，可将两个任务的定义分支同时调整到合并后的分支。
任务默认手动触发。

## 执行与发布

点击任务的 **Build with Parameters**：

1. 选择 `SOURCE_BRANCH`。
2. 不勾选 `DEPLOY`：运行构建、测试、归档及生产部署预检，保持线上版本。
3. 勾选 `DEPLOY`：在构建和预检通过后执行发布。
4. 后端首次部署 AG-UI 时，同时勾选 `MIGRATE_SCHEMA`；只补齐缺失列，重复执行不会重复新增。

首次升级建议先发布后端，再发布前端。后端如果仍缺数据库列且没有勾选迁移，会在替换 JAR 前停止。
不会运行真实 AI 模型、真实数据库写入等联调用例；具体离线用例列表在 `build-backend.sh`。
包含 AG-UI 的分支会先生成 Java 协议样本，再执行前端跨端用例，不因样本缺失而静默跳过。

归档产物位于各构建的 Artifacts，包括版本化文件和 SHA-256。构建历史保留 15 次，产物保留最近 5 次。
发布版本号为应用提交的前 12 位加 Jenkins 构建号。

## 生产端目录

| 路径 | 用途 |
| --- | --- |
| `/usr/local/sbin/academiccraft-deploy` | 由本仓库 `ci/deploy.py` 安装的 root 发布入口 |
| `/etc/sudoers.d/academiccraft-jenkins` | 允许 `baiye` 免密调用该发布入口 |
| `/etc/academiccraft-ci/backend.json` | root 可读的生产配置来源，不入库、不归档 |
| `/project/ci-incoming` | 构建产物上传目录 |
| `/project/ci-releases/backend` | 后端版本和原 JAR、原生产配置备份 |
| `/project/config/application-prod.properties` | 后端实际发布时写入的生产配置，`baiye` 可读、权限 600 |
| `/baiye/1panel/www/sites/ai-code/releases` | 前端版本及首次切换前的旧站点备份 |

后端使用原 `ai-code.service`、原 JAR 路径和 `/project` 工作目录，保留生成代码及已部署应用数据。
前端以相对符号链接切换 `index` 入口，使宿主机及 OpenResty 的 `/www` 映射都能解析。
发布串行锁保护前后端的版本切换。健康检查失败时自动恢复旧入口或旧 JAR、旧配置。
数据库新增列保留，不在回滚时删除。

Jenkins 的 SSH 凭据 ID 为 `academiccraft-deploy`。私钥存放在 Jenkins 凭据库，主机公钥写入
`/var/jenkins_home/.ssh/known_hosts`，通过已验证的 SSH 连接核对；流水线启用严格主机校验。
部署过程中不会向 Jenkins 传入生产数据库密码，数据库检查在生产机内完成。

更新发布入口时，用管理员权限安装 `ci/deploy.py` 到上述路径，权限 755、所有者 root。
生产参数变更仅修改 `/etc/academiccraft-ci/backend.json`，不会在普通构建时覆盖正在运行的配置。
Jenkins 使用已安装的 JDK 21 和 Maven Wrapper；前端使用 `node:24-bookworm-slim` 构建容器。
Maven 缓存在 Jenkins 持久化目录，两条任务通过文件锁串行使用该缓存；npm 缓存在 Docker 卷 `academiccraft-npm-cache`。

## 验证

Linux 环境可执行：

```bash
python3 -m unittest discover -s ci -p test_deploy.py -v
for file in ci/*.sh; do bash -n "$file"; done
```

两份 Jenkinsfile 还应通过 Jenkins 的 `/pipeline-model-converter/validate` 校验。
先以 `DEPLOY=false` 分别跑通两条任务，再执行所需的生产发布。
