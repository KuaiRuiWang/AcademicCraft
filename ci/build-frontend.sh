#!/usr/bin/env bash
set -euo pipefail

root="$PWD"
mkdir -p "$root/artifacts"

# 实际 Java 事件交给前端官方客户端校验，避免跨端用例因缺少样本而被跳过。
if [[ -f source/src/test/java/com/wkr/aicode/core/handler/AgUiEventAdapterTest.java ]]; then
    (
        cd source
        bash ../ci/maven.sh '-Dtest=AgUiEventAdapterTest,AgUiToolStreamTest,AgUiControllerTest' test
    )
fi

: "${NODE_HOME:?需要配置已安装的 Node 24 运行时}"
[[ -x "$NODE_HOME/bin/node" ]]
export PATH="$NODE_HOME/bin:$PATH"
export VITE_API_BASE_URL=/api
(
    cd source/wang-ai-code-fronten
    node --version
    npm ci
    if node -e 'process.exit(require("./package.json").scripts["test:agent"] ? 0 : 1)'; then
        npm run test:agent
    fi
    npm run --if-present test:thesis
    npm run build
)

tar -czf "$root/artifacts/frontend-${RELEASE}.tgz" -C source/wang-ai-code-fronten/dist .
cd "$root/artifacts"
sha256sum "frontend-${RELEASE}.tgz" > "frontend-${RELEASE}.tgz.sha256"
