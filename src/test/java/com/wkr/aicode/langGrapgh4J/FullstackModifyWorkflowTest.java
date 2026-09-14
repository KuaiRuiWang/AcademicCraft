package com.wkr.aicode.langGrapgh4J;

import com.wkr.aicode.langGrapgh4J.state.WorkflowContext;
import com.wkr.aicode.model.enums.ModifyScopeEnum;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 全栈修改工作流路由决策测试：修改范围到 Agent 分支的分发与 BOTH 顺序执行
 *
 * @author <a>百夜</a>
 */
class FullstackModifyWorkflowTest {

    private final FullstackModifyWorkflow workflow = new FullstackModifyWorkflow();

    @Test
    void routerDistributesScopeToExpectedBranch() {
        assertEquals("frontend", workflow.routeAfterModifyRouter(stateOf(scopeContext(ModifyScopeEnum.FRONTEND))),
                "仅前端修改应只分发给前端 Agent");
        assertEquals("backend", workflow.routeAfterModifyRouter(stateOf(scopeContext(ModifyScopeEnum.BACKEND))),
                "仅后端修改应只分发给后端 Agent");
        assertEquals("both", workflow.routeAfterModifyRouter(stateOf(scopeContext(ModifyScopeEnum.BOTH))),
                "前后端修改应从前端 Agent 开始顺序执行");
    }

    @Test
    void unknownScopeFallsBackToBoth() {
        WorkflowContext context = WorkflowContext.builder().appId(1L).build();
        assertEquals("both", workflow.routeAfterModifyRouter(stateOf(context)),
                "分类结果为空时应按 BOTH 兜底");
    }

    @Test
    void onlyBothContinuesToBackendAfterFrontend() {
        assertEquals("backend", workflow.routeAfterFrontendAgent(stateOf(scopeContext(ModifyScopeEnum.BOTH))),
                "BOTH 时前端完成后应继续执行后端 Agent");
        assertEquals("end", workflow.routeAfterFrontendAgent(stateOf(scopeContext(ModifyScopeEnum.FRONTEND))),
                "仅前端修改在前端 Agent 完成后应结束");
    }

    private WorkflowContext scopeContext(ModifyScopeEnum scope) {
        WorkflowContext context = WorkflowContext.builder().appId(1L).modifyMode(true).build();
        context.setModifyScope(scope);
        return context;
    }

    private MessagesState<String> stateOf(WorkflowContext context) {
        return new MessagesState<>(Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, context));
    }
}
