package com.wkr.aicode.langGrapgh4J.node.fullstack;

import cn.hutool.json.JSONUtil;
import com.wkr.aicode.ai.message.AiResponseMessage;
import com.wkr.aicode.ai.message.WorkflowEventMessage;
import com.wkr.aicode.model.enums.AgentRoleEnum;
import reactor.core.publisher.FluxSink;

import java.util.concurrent.Callable;

/**
 * Agent 流式输出管道
 * FluxSink 非线程安全，前端与后端 Agent 并行输出时必须串行写入
 *
 * @author <a>百夜</a>
 */
public class AgentStreamPipe {

    private final FluxSink<String> sink;

    /**
     * 当前管道的智能体角色，为空表示系统级消息（不标注来源 Agent）
     */
    private final AgentRoleEnum agentRole;

    public AgentStreamPipe(FluxSink<String> sink) {
        this(sink, null);
    }

    private AgentStreamPipe(FluxSink<String> sink, AgentRoleEnum agentRole) {
        this.sink = sink;
        this.agentRole = agentRole;
    }

    /**
     * 生成标注了 Agent 角色的管道视图，与原始管道共享同一个 sink
     */
    public AgentStreamPipe forRole(AgentRoleEnum role) {
        return new AgentStreamPipe(sink, role);
    }

    /**
     * 每次节点调用都有开始与结束，契约修复重新进入同一角色时也会产生新一轮记录。
     */
    public <T> T runAgent(Callable<T> action) throws Exception {
        String role = agentRole.getValue();
        emitWorkflowEvent("agent_started", role, agentRole.getText(), "running");
        try {
            T result = action.call();
            emitWorkflowEvent("agent_finished", role, agentRole.getText(), "success");
            return result;
        } catch (Exception error) {
            emitWorkflowEvent("agent_error", role, agentRole.getText() + "执行失败", "error");
            throw error;
        }
    }

    public void stepStarted(String name, String label) {
        emitWorkflowEvent("step_started", name, label, "running");
    }

    public void stepFinished(String name, String label, String status) {
        emitWorkflowEvent("step_finished", name, label, status);
    }

    public void failRun(String message) {
        emitWorkflowEvent("run_error", "generation", message, "error");
    }

    private void emitWorkflowEvent(String event, String name, String label, String status) {
        String role = agentRole == null ? null : agentRole.getValue();
        emit(JSONUtil.toJsonStr(new WorkflowEventMessage(event, name, label, status, role)));
    }

    /**
     * 推送一条 AI 文本消息
     */
    public void emitAiResponse(String data) {
        AiResponseMessage message = new AiResponseMessage(data);
        if (agentRole != null) {
            message.setAgentRole(agentRole.getValue());
        }
        emit(JSONUtil.toJsonStr(message));
    }

    /**
     * 推送原始 JSON 消息
     */
    public void emit(String message) {
        synchronized (sink) {
            sink.next(message);
        }
    }
}
