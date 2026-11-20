package com.wkr.aicode.core.thesis;

import com.wkr.aicode.exception.BusinessException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.openai.OpenAiChatModel;
import com.sun.net.httpserver.HttpServer;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ThesisWriterTest {
    @Test void sendsTheFrozenSourceAndDeclinesTruncatedOutput() {
        ChatModel model = mock(ChatModel.class);
        ThesisWriter writer = new ThesisWriter();
        ReflectionTestUtils.setField(writer, "chatModel", model);
        when(model.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("部分正文"))
                .finishReason(FinishReason.LENGTH).build());
        assertThrows(BusinessException.class, () -> writer.writeChapter("图书管理", "第三章 需求分析", "App.vue 实际页面", "绪论摘要"));
        ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(model).chat(request.capture());
        String input = ((UserMessage) request.getValue().messages().get(1)).singleText();
        assertTrue(input.contains("App.vue 实际页面"));
        assertTrue(input.contains("第三章 需求分析"));
    }

    @Test void sendsNonThinkingParametersThroughTheActualModelClient() throws Exception {
        AtomicReference<String> payload = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String body = "none".equals(JSONUtil.parseObj(payload.get()).getStr("reasoning_effort"))
                    ? """
                    {"id":"local-chapter","model":"test-model","choices":[{"index":0,
                    "message":{"role":"assistant","content":"章节正文"},"finish_reason":"stop"}]}
                    """ : "{";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            ThesisWriter writer = new ThesisWriter();
            ReflectionTestUtils.setField(writer, "chatModel", OpenAiChatModel.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                    .apiKey("local-test").modelName("test-model").maxTokens(16384)
                    .maxRetries(0).timeout(Duration.ofSeconds(5)).build());
            assertEquals("章节正文", writer.writeChapter("任务管理", "摘要", "App.vue", ""));
            var request = JSONUtil.parseObj(payload.get());
            assertEquals("none", request.getStr("reasoning_effort"));
            assertEquals("test-model", request.getStr("model"));
            assertEquals(16384, request.getInt("max_tokens"));
        } finally {
            server.stop(0);
        }
    }
}
