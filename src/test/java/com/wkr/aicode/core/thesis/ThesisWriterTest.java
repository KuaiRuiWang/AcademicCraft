package com.wkr.aicode.core.thesis;

import com.wkr.aicode.core.model.ModelStep;
import com.wkr.aicode.core.model.PlatformModelRouter;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.model.entity.PlatformModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import com.sun.net.httpserver.HttpServer;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ThesisWriterTest {
    @Test void keepsTheClosingFenceOfTheLastDiagram() {
        StreamingChatModel model = mock(StreamingChatModel.class);
        ThesisWriter writer = writerUsing(model);
        String chapter = "说明正文\n\n```mermaid\nflowchart LR\nA-->B\n```";
        doAnswer(invocation -> {
            invocation.<StreamingChatResponseHandler>getArgument(1).onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from(chapter)).finishReason(FinishReason.STOP).build());
            return null;
        }).when(model).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        assertEquals(chapter, writer.writeChapter("图书管理", "第四章 系统设计", "App.vue", ""));
    }

    @Test void sendsTheFrozenSourceAndDeclinesTruncatedOutput() {
        StreamingChatModel model = mock(StreamingChatModel.class);
        ThesisWriter writer = writerUsing(model);
        doAnswer(invocation -> {
            invocation.<StreamingChatResponseHandler>getArgument(1).onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("部分正文")).finishReason(FinishReason.LENGTH).build());
            return null;
        }).when(model).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        assertThrows(BusinessException.class, () -> writer.writeChapter("图书管理", "第三章 需求分析", "App.vue 实际页面", "绪论摘要"));
        ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(model).chat(request.capture(), any(StreamingChatResponseHandler.class));
        String input = ((UserMessage) request.getValue().messages().get(1)).singleText();
        assertTrue(input.contains("App.vue 实际页面"));
        assertTrue(input.contains("第三章 需求分析"));
    }

    @Test void collectsTheCompleteStreamingChapterWithNonThinkingParameters() throws Exception {
        AtomicReference<String> payload = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String body = "none".equals(JSONUtil.parseObj(payload.get()).getStr("reasoning_effort"))
                    ? """
                    data: {"id":"local-chapter","model":"test-model","choices":[{"index":0,"delta":{"role":"assistant","content":"章节"}}]}

                    data: {"id":"local-chapter","model":"test-model","choices":[{"index":0,"delta":{"content":"正文"},"finish_reason":"stop"}]}

                    data: [DONE]

                    """ : "{";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            ThesisWriter writer = writerUsing(OpenAiStreamingChatModel.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                    .apiKey("local-test").modelName("test-model").maxTokens(16384)
                    .timeout(Duration.ofSeconds(5)).build());
            assertEquals("章节正文", writer.writeChapter("任务管理", "摘要", "App.vue", ""));
            var request = JSONUtil.parseObj(payload.get());
            assertEquals("none", request.getStr("reasoning_effort"));
            assertTrue(request.getBool("stream"));
            assertEquals("test-model", request.getStr("model"));
            assertEquals(16384, request.getInt("max_tokens"));
        } finally {
            server.stop(0);
        }
    }

    @Test void propagatesStreamFailuresWithoutSavingPartialText() {
        StreamingChatModel model = mock(StreamingChatModel.class);
        ThesisWriter writer = writerUsing(model);
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onPartialResponse("尚未完成的正文");
            handler.onError(new IllegalStateException("stream interrupted"));
            return null;
        }).when(model).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        var error = assertThrows(java.util.concurrent.CompletionException.class,
                () -> writer.writeChapter("任务管理", "摘要", "App.vue", ""));
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }

    @Test void usesTheConfiguredThesisModelToPlanLiteratureQueries() {
        StreamingChatModel defaultModel = mock(StreamingChatModel.class);
        StreamingChatModel literatureModel = mock(StreamingChatModel.class);
        ThesisWriter writer = writerUsing(defaultModel);
        PlatformModelRouter router = (PlatformModelRouter) ReflectionTestUtils.getField(writer, "models");
        PlatformModel configured = new PlatformModel();
        configured.setBaseUrl("https://models.example.test/v1");
        when(router.configuredModel(ModelStep.THESIS_CHAPTER)).thenReturn(configured);
        when(router.stream(configured, defaultModel)).thenReturn(literatureModel);
        doAnswer(invocation -> {
            invocation.<StreamingChatResponseHandler>getArgument(1).onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("图书检索 数据库\nlibrary information retrieval\nhttps://example.test/"))
                    .finishReason(FinishReason.STOP).build());
            return null;
        }).when(literatureModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        assertEquals(List.of("图书检索 数据库", "library information retrieval"),
                writer.planLiteratureSearch("图书管理系统", "App.vue"));
        verifyNoInteractions(defaultModel);
    }

    private ThesisWriter writerUsing(StreamingChatModel defaultModel) {
        ThesisWriter writer = new ThesisWriter();
        PlatformModelRouter router = mock(PlatformModelRouter.class);
        when(router.stream(null, defaultModel)).thenReturn(defaultModel);
        ReflectionTestUtils.setField(writer, "chatModel", defaultModel);
        ReflectionTestUtils.setField(writer, "models", router);
        return writer;
    }
}
