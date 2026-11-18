#!/usr/bin/env bash
set -euo pipefail

root="$PWD"
mkdir -p "$root/artifacts"

# 实际 Java 事件交给前端官方客户端校验，避免跨端用例因缺少样本而被跳过。
if [[ -f source/src/test/java/com/wkr/aicode/core/handler/AgUiEventAdapterTest.java ]]; then
    (
        cd source
        sh mvnw -B -ntp "-Dmaven.repo.local=${MAVEN_USER_HOME}/repository" \
            '-Dtest=AgUiEventAdapterTest,AgUiToolStreamTest' test
    )
fi

case "$WORKSPACE" in
    "$JENKINS_HOME"/*) host_workspace="${JENKINS_HOST_HOME}${WORKSPACE#"$JENKINS_HOME"}" ;;
    *) echo '工作区不在 Jenkins 持久化目录内' >&2; exit 1 ;;
esac

docker run --rm \
    --mount "type=bind,source=$host_workspace,target=/workspace" \
    --mount type=volume,source=academiccraft-npm-cache,target=/root/.npm \
    --workdir /workspace/source/wang-ai-code-fronten \
    -e VITE_API_BASE_URL=/api \
    "$NODE_IMAGE" bash -euc '
        node --version
        npm ci
        if node -e '\''process.exit(require("./package.json").scripts["test:agent"] ? 0 : 1)'\''; then
            npm run test:agent
        fi
        npm run build
    '

tar -czf "$root/artifacts/frontend-${RELEASE}.tgz" -C source/wang-ai-code-fronten/dist .
cd "$root/artifacts"
sha256sum "frontend-${RELEASE}.tgz" > "frontend-${RELEASE}.tgz.sha256"
