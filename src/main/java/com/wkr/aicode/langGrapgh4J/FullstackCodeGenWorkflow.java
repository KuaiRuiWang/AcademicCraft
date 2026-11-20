package com.wkr.aicode.langGrapgh4J;

import cn.hutool.core.thread.ExecutorBuilder;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.langGrapgh4J.model.QualityResult;
import com.wkr.aicode.langGrapgh4J.node.fullstack.AgentStreamPipe;
import com.wkr.aicode.langGrapgh4J.node.fullstack.ArchitectNode;
import com.wkr.aicode.langGrapgh4J.node.fullstack.BackendAgentNode;
import com.wkr.aicode.langGrapgh4J.node.fullstack.BuildNode;
import com.wkr.aicode.langGrapgh4J.node.fullstack.ContractCheckNode;
import com.wkr.aicode.langGrapgh4J.node.fullstack.FrontendAgentNode;
import com.wkr.aicode.langGrapgh4J.state.WorkflowContext;
import com.wkr.aicode.model.enums.AgentRoleEnum;
import com.wkr.aicode.monitor.MonitorContext;
import com.wkr.aicode.monitor.MonitorContextHolder;
import com.wkr.aicode.service.ThesisService;
import com.wkr.aicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * 全栈代码生成工作流：架构师设计 API 契约后，前端/后端 Agent 并行生成
 * T-07 契约校验节点完成后插入并行分支之后
 *
 * @author <a>百夜</a>
 */
@Slf4j
public class FullstackCodeGenWorkflow {

    /**
     * 创建全栈工作流
     *
     * @param pipe 流式输出管道，并行分支共用（内部串行写入 sink）
     */
    public CompiledGraph<MessagesState<String>> createWorkflow(AgentStreamPipe pipe) {
        try {
            return new MessagesStateGraph<String>()
                    .addNode("architect", ArchitectNode.create(pipe.forRole(AgentRoleEnum.ARCHITECT)))
                    .addNode("frontend_agent", FrontendAgentNode.create(pipe.forRole(AgentRoleEnum.FRONTEND)))
                    .addNode("backend_agent", BackendAgentNode.create(pipe.forRole(AgentRoleEnum.BACKEND)))
                    .addNode("contract_check", ContractCheckNode.create(pipe))
                    .addNode("build", BuildNode.create(pipe))

                    .addEdge(START, "architect")
                    // 契约先行：架构师完成后再并行分发给两个 Agent
                    .addEdge("architect", "frontend_agent")
                    .addEdge("architect", "backend_agent")
                    // 两个 Agent 结束后汇聚到契约校验，校验不通过时退回对应 Agent 修复
                    .addEdge("frontend_agent", "contract_check")
                    .addEdge("backend_agent", "contract_check")
                    .addConditionalEdges("contract_check",
                            edge_async(this::routeAfterContractCheck),
                            Map.of(
                                    "pass", "build",
                                    "frontend", "frontend_agent",
                                    "backend", "backend_agent",
                                    "give_up", END
                            ))
                    .addEdge("build", END)
                    .compile();
        } catch (GraphStateException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "全栈工作流创建失败");
        }
    }

    /**
     * 契约校验后的路由：通过则继续（T-10 接入构建），未通过则退回有问题的 Agent，超过上限终止
     */
    String routeAfterContractCheck(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        QualityResult result = context.getContractCheckResult();
        if (result == null || Boolean.TRUE.equals(result.getIsValid())) {
            return "pass";
        }
        if (context.getContractCheckRound() > ContractCheckNode.MAX_FIX_ROUNDS) {
            log.error("契约校验达到修复上限（{} 轮）仍未通过，终止生成，appId={}",
                    ContractCheckNode.MAX_FIX_ROUNDS, context.getAppId());
            return "give_up";
        }
        // 契约缺失接口对整体影响更大，优先修复后端
        if (context.getBackendFixHints() != null && !context.getBackendFixHints().isEmpty()) {
            log.warn("契约校验未通过，退回后端 Agent 修复，appId={}", context.getAppId());
            return "backend";
        }
        if (context.getFrontendFixHints() != null && !context.getFrontendFixHints().isEmpty()) {
            log.warn("契约校验未通过，退回前端 Agent 修复，appId={}", context.getAppId());
            return "frontend";
        }
        return "pass";
    }

    /**
     * 执行全栈工作流（Flux 流式输出版本）
     *
     * @param originalPrompt 用户原始需求
     * @param appId          应用 ID
     */
    public Flux<String> executeWorkflowWithFlux(String originalPrompt, Long appId) {
        return Flux.create(sink -> {
            // 工作流在虚拟线程中执行，ThreadLocal 不会自动继承，需手动捕获监控上下文
            MonitorContext monitorContext = MonitorContextHolder.getContext();
            Thread.startVirtualThread(() -> {
                MonitorContextHolder.setContext(monitorContext);
                // architect 的两个后继节点并行执行，节点内部阻塞等待各自的 Agent 流完成
                ExecutorService pool = ExecutorBuilder.create()
                        .setCorePoolSize(4)
                        .setMaxPoolSize(8)
                        .setWorkQueue(new LinkedBlockingQueue<>(100))
                        .setThreadFactory(ThreadFactoryBuilder.create().setNamePrefix("Fullstack-Agent-").build())
                        .build();
                try {
                    AgentStreamPipe pipe = new AgentStreamPipe(sink);
                    CompiledGraph<MessagesState<String>> workflow = createWorkflow(pipe);
                    RunnableConfig runnableConfig = RunnableConfig.builder()
                            .addParallelNodeExecutor("architect", pool)
                            .build();
                    WorkflowContext initialContext = WorkflowContext.builder()
                            .appId(appId)
                            .originalPrompt(originalPrompt)
                            .currentStep("初始化")
                            .build();
                    GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
                    log.info("全栈工作流图:\n{}", graph.content());
                    pipe.emitAiResponse("开始执行全栈代码生成工作流\n");
                    int stepCounter = 1;
                    WorkflowContext completedContext = null;
                    for (NodeOutput<MessagesState<String>> step : workflow.stream(
                            Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext), runnableConfig)) {
                        WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                        completedContext = currentContext;
                        log.info("--- 第 {} 步完成，当前步骤: {} ---", stepCounter++,
                                currentContext == null ? "unknown" : currentContext.getCurrentStep());
                    }
                    if (completedContext != null && completedContext.getErrorMessage() == null
                            && completedContext.getBuildResultDir() != null
                            && completedContext.getContractCheckResult() != null
                            && Boolean.TRUE.equals(completedContext.getContractCheckResult().getIsValid())) {
                        SpringContextUtil.getBean(ThesisService.class).generationSucceeded(appId);
                    }
                    log.info("全栈代码生成工作流执行完成，appId={}", appId);
                    sink.complete();
                } catch (Exception e) {
                    log.error("全栈代码生成工作流执行失败，appId={}", appId, e);
                    sink.error(e);
                } finally {
                    pool.shutdown();
                    MonitorContextHolder.clearContext();
                }
            });
        });
    }
}
