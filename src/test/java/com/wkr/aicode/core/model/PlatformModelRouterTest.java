package com.wkr.aicode.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.wkr.aicode.core.thesis.ThesisWriter;
import com.wkr.aicode.langGrapgh4J.tools.LogoGeneratorTool;
import com.wkr.aicode.mapper.PlatformModelBindingMapper;
import com.wkr.aicode.model.entity.PlatformModel;
import com.wkr.aicode.monitor.AiModelMonitorListener;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(20)
class PlatformModelRouterTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ModelKeyCipher keys = new ModelKeyCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private final Map<String, Request> requests = new ConcurrentHashMap<>();

    @Test
    void usesSeparateModelsForRoutingChapterAndRewriteAndPicksUpNewAssignments() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                var payload = json.readTree(exchange.getRequestBody());
                String modelId = payload.path("model").asText();
                requests.put(modelId, new Request(exchange.getRequestHeaders().getFirst("Authorization"), payload));
                boolean streaming = payload.path("stream").asBoolean();
                String reply = streaming
                        ? "data: {\"id\":\"fixture\",\"model\":\"" + modelId + "\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"结果\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
                        : "{\"id\":\"fixture\",\"model\":\"" + modelId + "\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"vue_project\"},\"finish_reason\":\"stop\"}]}";
                byte[] body = reply.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", streaming ? "text/event-stream" : "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try {
            var mappings = mock(PlatformModelBindingMapper.class);
            var endpoints = mock(ModelEndpoints.class);
            when(endpoints.validate(anyString(), eq(ModelProtocol.OPENAI_COMPATIBLE)))
                    .thenReturn(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"));
            when(mappings.findModel(ModelStep.APP_TYPE_ROUTING.name())).thenReturn(model("routing-model"));
            when(mappings.findModel(ModelStep.THESIS_CHAPTER.name())).thenReturn(model("chapter-model"));
            when(mappings.findModel(ModelStep.THESIS_REWRITE.name())).thenReturn(model("rewrite-model"), model("next-rewrite-model"));
            var router = new PlatformModelRouter(mappings, keys, endpoints, mock(AiModelMonitorListener.class));
            var fallback = mock(ChatModel.class);
            when(fallback.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("default")).build());
            var request = ChatRequest.builder().messages(UserMessage.from("创建应用")).build();
            assertEquals("vue_project", router.chat(ModelStep.APP_TYPE_ROUTING, fallback).chat(request).aiMessage().text());
            assertEquals("default", router.chat(ModelStep.WORKFLOW_TYPE_ROUTING, fallback).chat(request).aiMessage().text());

            var writer = new ThesisWriter();
            ReflectionTestUtils.setField(writer, "models", router);
            ReflectionTestUtils.setField(writer, "chatModel", mock(StreamingChatModel.class));
            assertEquals("结果", writer.writeChapter("图书管理", "摘要", "真实源码", ""));
            assertEquals("结果", writer.rewriteSelection("图书管理", "摘要", "原文", "简写", "", ""));
            assertEquals("结果", writer.rewriteSelection("图书管理", "摘要", "原文", "简写", "", ""));

            assertEquals("Bearer fixture-routing-model", requests.get("routing-model").authorization());
            assertEquals("Bearer fixture-chapter-model", requests.get("chapter-model").authorization());
            assertEquals("Bearer fixture-rewrite-model", requests.get("rewrite-model").authorization());
            assertEquals("Bearer fixture-next-rewrite-model", requests.get("next-rewrite-model").authorization());
            assertTrue(requests.get("chapter-model").payload().path("stream").asBoolean());
            assertFalse(requests.get("chapter-model").payload().has("reasoning_effort"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void usesTheConfiguredImageRegionAndModelForLogoGeneration() throws Exception {
        var imageRequest = new AtomicReference<Request>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/services/aigc/text2image/image-synthesis", exchange -> {
            imageRequest.set(new Request(exchange.getRequestHeaders().getFirst("Authorization"),
                    json.readTree(exchange.getRequestBody())));
            sendJson(exchange, """
                    {"request_id":"fixture","output":{"task_id":"example-task","task_status":"PENDING"}}
                    """);
        });
        server.createContext("/api/v1/tasks/example-task", exchange -> sendJson(exchange, """
                {"request_id":"fixture","output":{"task_id":"example-task","task_status":"SUCCEEDED",
                 "results":[{"url":"https://example.com/logo.png"}]}}
                """));
        server.start();
        try {
            var mappings = mock(PlatformModelBindingMapper.class);
            var endpoints = mock(ModelEndpoints.class);
            var model = model("fixture-image");
            model.setProtocol(ModelProtocol.DASHSCOPE_IMAGE.name());
            model.setBaseUrl("https://dashscope-intl.aliyuncs.com/api/v1");
            when(endpoints.validate(model.getBaseUrl(), ModelProtocol.DASHSCOPE_IMAGE))
                    .thenReturn(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1"));
            when(mappings.findModel(ModelStep.LOGO_IMAGE.name())).thenReturn(model);
            var router = new PlatformModelRouter(mappings, keys, endpoints, mock(AiModelMonitorListener.class));
            var tool = new LogoGeneratorTool();
            ReflectionTestUtils.setField(tool, "models", router);

            var images = tool.generateLogos("图书管理");
            assertNotNull(imageRequest.get(), "图片请求应该发送给配置的服务");
            assertEquals("Bearer fixture-fixture-image", imageRequest.get().authorization());
            assertEquals("fixture-image", imageRequest.get().payload().path("model").asText());
            assertEquals(1, images.size());
            assertEquals("https://example.com/logo.png", images.getFirst().getUrl());
        } finally {
            server.stop(0);
        }
    }

    private void sendJson(HttpExchange exchange, String reply) throws IOException {
        try (exchange) {
            byte[] body = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private PlatformModel model(String modelId) {
        var model = new PlatformModel();
        model.setBaseUrl("https://api.openai.com/v1");
        model.setProtocol(ModelProtocol.OPENAI_COMPATIBLE.name());
        model.setModelId(modelId);
        model.setApiKeyCiphertext(keys.encrypt("fixture-" + modelId));
        return model;
    }

    private record Request(String authorization, JsonNode payload) { }
}
