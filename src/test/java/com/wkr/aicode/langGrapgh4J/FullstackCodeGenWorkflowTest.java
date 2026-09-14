package com.wkr.aicode.langGrapgh4J;

import com.wkr.aicode.langGrapgh4J.model.QualityResult;
import com.wkr.aicode.langGrapgh4J.state.WorkflowContext;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 全栈工作流路由决策测试：契约校验未通过时的退回目标与修复回环上限
 *
 * @author <a>百夜</a>
 */
class FullstackCodeGenWorkflowTest {

    private final FullstackCodeGenWorkflow workflow = new FullstackCodeGenWorkflow();

    @Test
    void routesToFixOnlyWithinRoundLimit() {
        WorkflowContext context = WorkflowContext.builder().appId(1L).build();
        context.setContractCheckResult(failingResult());
        context.setBackendFixHints(List.of("后端缺少接口（或方法不匹配）：POST /api/posts，请实现该接口"));
        context.setFrontendFixHints(List.of());

        context.setContractCheckRound(1);
        assertEquals("backend", workflow.routeAfterContractCheck(stateOf(context)), "第 1 轮应退回后端修复");

        // 后端已修复，仅剩前端问题
        context.setBackendFixHints(List.of());
        context.setFrontendFixHints(List.of("前端调用了契约外接口：/api/tags，请改为契约中的路径或移除该调用"));
        context.setContractCheckRound(2);
        assertEquals("frontend", workflow.routeAfterContractCheck(stateOf(context)), "第 2 轮应退回前端修复");

        // 超过修复上限（2 轮），终止
        context.setContractCheckRound(3);
        assertEquals("give_up", workflow.routeAfterContractCheck(stateOf(context)), "第 3 轮应终止生成");

        // 校验通过时直接放行
        context.setContractCheckResult(QualityResult.builder().isValid(true).errors(List.of()).build());
        assertEquals("pass", workflow.routeAfterContractCheck(stateOf(context)));
    }

    private QualityResult failingResult() {
        return QualityResult.builder().isValid(false).errors(List.of("存在契约差异")).build();
    }

    private MessagesState<String> stateOf(WorkflowContext context) {
        return new MessagesState<>(Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, context));
    }
}
