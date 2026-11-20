#!/usr/bin/env bash
set -euo pipefail

root="$PWD"
mkdir -p "$root/artifacts"
cd source
java -version

# 这些用例使用临时目录、模拟模型和 Mockito，不调用线上模型或业务数据库。
tests='RoutingAiModelConfigTest,AgUi*Test,ToolResultTest,AgentWorkspaceTest,JsonMessageStreamHandlerTest,FullstackCodeGenWorkflowTest,FullstackModifyWorkflowTest,FullstackGraphSemanticsTest,AgentStreamPipeTest,ContractCheckNodeTest,FullstackProjectDetectTest,ChatHistoryServiceImplTest'
if [[ -f src/test/java/com/wkr/aicode/service/ThesisServiceTest.java ]]; then
    tests+=',Thesis*Test,ProjectSnapshotServiceTest'
fi
bash ../ci/maven.sh "-Dtest=$tests" package

jar='target/ai-code-0.0.1-SNAPSHOT.jar'
jar tf "$jar" > target/jar-contents.txt
if grep -Eq 'BOOT-INF/classes/application-(dev|prod)\.(yml|yaml|properties)$' target/jar-contents.txt; then
    echo '构建产物包含私有环境配置，拒绝归档和发布' >&2
    exit 1
fi
cp "$jar" "$root/artifacts/backend-${RELEASE}.jar"
cd "$root/artifacts"
sha256sum "backend-${RELEASE}.jar" > "backend-${RELEASE}.jar.sha256"
