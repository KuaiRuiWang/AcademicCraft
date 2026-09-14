package com.wkr.aicode.langGrapgh4J.node.fullstack;

import cn.hutool.json.JSONUtil;
import com.wkr.aicode.ai.message.AiResponseMessage;
import com.wkr.aicode.model.enums.AgentRoleEnum;
import reactor.core.publisher.FluxSink;

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
