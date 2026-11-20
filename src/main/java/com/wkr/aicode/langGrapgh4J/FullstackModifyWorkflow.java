package com.wkr.aicode.langGrapgh4J;

import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.langGrapgh4J.node.ModifyRouterNode;
import com.wkr.aicode.langGrapgh4J.node.fullstack.AgentStreamPipe;
import com.wkr.aicode.langGrapgh4J.node.fullstack.BackendAgentNode;
import com.wkr.aicode.langGrapgh4J.node.fullstack.FrontendAgentNode;
import com.wkr.aicode.langGrapgh4J.state.WorkflowContext;
import com.wkr.aicode.model.enums.AgentRoleEnum;
import com.wkr.aicode.model.enums.ModifyScopeEnum;
import com.wkr.aicode.monitor.MonitorContext;
import com.wkr.aicode.monitor.MonitorContextHolder;
import com.wkr.aicode.service.ThesisService;
import com.wkr.aicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * 全栈项目修改工作流（T-17）：
 * 修改意图路由分类后，按 FRONTEND / BACKEND / BOTH 分发给对应角色的 Agent 增量修改已有产物，
 * BOTH 时顺序执行（先前端后后端），不重新设计契约、不重新构建
 *
 * @author <a>百夜</a>
 */
@Slf4j
public class FullstackModifyWorkflow {

    /**
     * 创建全栈修改工作流
     *
     * @param pipe 流式输出管道
     */
    public CompiledGraph<MessagesState<String>> createWorkflow(AgentStreamPipe pipe) {
        try {
            return new MessagesStateGraph<String>()
                    .addNode("modify_router", ModifyRouterNode.create(pipe))
                    .addNode("frontend_agent", FrontendAgentNode.create(pipe.forRole(AgentRoleEnum.FRONTEND)))
                    .addNode("backend_agent", BackendAgentNode.create(pipe.forRole(AgentRoleEnum.BACKEND)))

                    .addEdge(START, "modify_router")
                    .addConditionalEdges("modify_router",
                            edge_async(this::routeAfterModifyRouter),
                            Map.of(
                                    "frontend", "frontend_agent",
                                    "backend", "backend_agent",
                                    "both", "frontend_agent"
                            ))
                    // BOTH 时前端改完继续顺序改后端，否则结束
                    .addConditionalEdges("frontend_agent",
                            edge_async(this::routeAfterFrontendAgent),
                            Map.of(
                                    "backend", "backend_agent",
                                    "end", END
                            ))
                    .addEdge("backend_agent", END)
                    .compile();
        } catch (GraphStateException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "全栈修改工作流创建失败");
        }
    }

    /**
     * 修改意图路由后的分发：未知范围按 both 处理
     */
    String routeAfterModifyRouter(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        ModifyScopeEnum modifyScope = context.getModifyScope();
        if (modifyScope == null) {
            return "both";
        }
        return switch (modifyScope) {
            case FRONTEND -> "frontend";
            case BACKEND -> "backend";
            case BOTH -> "both";
        };
    }

    /**
     * 前端 Agent 完成后的路由：BOTH 继续改后端，其余结束
     */
    String routeAfterFrontendAgent(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        return ModifyScopeEnum.BOTH == context.getModifyScope() ? "backend" : "end";
    }

    /**
     * 执行全栈修改工作流（Flux 流式输出版本）
     *
     * @param modifyPrompt 用户的修改需求
     * @param appId        应用 ID
     */
    public Flux<String> executeWorkflowWithFlux(String modifyPrompt, Long appId) {
        return Flux.create(sink -> {
            // 工作流在虚拟线程中执行，ThreadLocal 不会自动继承，需手动捕获监控上下文
            MonitorContext monitorContext = MonitorContextHolder.getContext();
            Thread.startVirtualThread(() -> {
                MonitorContextHolder.setContext(monitorContext);
                try {
                    AgentStreamPipe pipe = new AgentStreamPipe(sink);
                    CompiledGraph<MessagesState<String>> workflow = createWorkflow(pipe);
                    WorkflowContext initialContext = WorkflowContext.builder()
                            .appId(appId)
                            .originalPrompt(modifyPrompt)
                            .currentStep("初始化")
                            .modifyMode(true)
                            .build();
                    GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
                    log.info("全栈修改工作流图:\n{}", graph.content());
                    pipe.emitAiResponse("开始执行全栈项目修改工作流\n");
                    int stepCounter = 1;
                    for (NodeOutput<MessagesState<String>> step : workflow.stream(
                            Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
                        WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                        log.info("--- 第 {} 步完成，当前步骤: {} ---", stepCounter++,
                                currentContext == null ? "unknown" : currentContext.getCurrentStep());
                    }
                    log.info("全栈项目修改工作流执行完成，appId={}", appId);
                    SpringContextUtil.getBean(ThesisService.class).generationSucceeded(appId);
                    sink.complete();
                } catch (Exception e) {
                    log.error("全栈项目修改工作流执行失败，appId={}", appId, e);
                    sink.error(e);
                } finally {
                    MonitorContextHolder.clearContext();
                }
            });
        });
    }
}
