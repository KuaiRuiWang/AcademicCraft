package com.wkr.aicode.langGrapgh4J.node.fullstack;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.wkr.aicode.ai.message.AiResponseMessage;
import com.wkr.aicode.ai.message.StreamMessage;
import com.wkr.aicode.model.enums.AgentRoleEnum;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSE 合并输出测试：双 Agent 并行推送时消息带角色标识、互不丢消息、旧消息保持兼容
 *
 * @author <a>百夜</a>
 */
class AgentStreamPipeTest {

    /**
     * 前后端 Agent 并行推送：两个角色的消息都可达，且各自带上正确的 agentRole
     */
    @Test
    void parallelAgentsMessagesAllCarryOwnRole() throws InterruptedException {
        List<String> emitted = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        int perAgent = 20;
        Flux.<String>create(sink -> {
            AgentStreamPipe basePipe = new AgentStreamPipe(sink);
            AgentStreamPipe frontendPipe = basePipe.forRole(AgentRoleEnum.FRONTEND);
            AgentStreamPipe backendPipe = basePipe.forRole(AgentRoleEnum.BACKEND);
            Thread frontendThread = Thread.startVirtualThread(() -> {
                for (int i = 0; i < perAgent; i++) {
                    frontendPipe.emitAiResponse("前端片段" + i);
                }
            });
            Thread backendThread = Thread.startVirtualThread(() -> {
                for (int i = 0; i < perAgent; i++) {
                    backendPipe.emitAiResponse("后端片段" + i);
                }
            });
            try {
                frontendThread.join();
                backendThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // 系统级进度消息不属于任何 Agent
            basePipe.emitAiResponse("契约校验通过\n");
            sink.complete();
        }).subscribe(emitted::add, errors::add);

        assertTrue(errors.isEmpty(), "推送过程不应出错：" + errors);
        assertEquals(perAgent * 2 + 1, emitted.size(), "并行推送不应丢消息：" + emitted.size());

        long frontendCount = emitted.stream()
                .filter(message -> "frontend".equals(JSONUtil.parseObj(message).getStr("agentRole")))
                .count();
        long backendCount = emitted.stream()
                .filter(message -> "backend".equals(JSONUtil.parseObj(message).getStr("agentRole")))
                .count();
        assertEquals(perAgent, frontendCount, emitted.toString());
        assertEquals(perAgent, backendCount, emitted.toString());

        // 系统消息不带角色字段，前端按「系统」渲染
        String systemMessage = emitted.get(emitted.size() - 1);
        assertFalse(JSONUtil.parseObj(systemMessage).containsKey("agentRole"), systemMessage);
        assertEquals("契约校验通过\n", JSONUtil.parseObj(systemMessage).getStr("data"));
    }

    /**
     * 带角色的消息仍能被对话历史处理器解析（新增字段对旧链路向后兼容）
     */
    @Test
    void roleStampedMessagesStayParsableForChatHistory() {
        String chunk = JSONUtil.toJsonStr(new AiResponseMessage("生成的代码片段", AgentRoleEnum.BACKEND.getValue()));

        // JsonMessageStreamHandler 的解析路径
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);

        assertEquals("ai_response", streamMessage.getType());
        assertEquals("backend", streamMessage.getAgentRole());
        assertEquals("生成的代码片段", aiMessage.getData());
    }

    /**
     * 非全栈模式的消息不带 agentRole 字段，旧前端行为不变
     */
    @Test
    void messagesWithoutRoleKeepLegacyPayload() {
        JSONObject json = JSONUtil.parseObj(JSONUtil.toJsonStr(new AiResponseMessage("普通模式")));

        assertFalse(json.containsKey("agentRole"), json.toString());
        assertEquals(2, json.size(), json.toString());
        assertTrue(json.containsKey("type") && json.containsKey("data"), json.toString());
    }
}
