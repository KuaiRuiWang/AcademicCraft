package com.wkr.aicode.config;

import com.sun.net.httpserver.HttpServer;
import com.wkr.aicode.ai.AiCodeGenTypeRoutingService;
import com.wkr.aicode.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutingAiModelConfigTest {

    @Test
    void routesAppWhenProviderSplitsCompressedResponse() throws Exception {
        String response = """
                {"id":"routing-test","object":"chat.completion","model":"test-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"VUE_PROJECT"},
                             "finish_reason":"stop"}]}
                """;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            boolean compressed = !"identity".equals(exchange.getRequestHeaders().getFirst("Accept-Encoding"));
            if (compressed) {
                exchange.getResponseHeaders().add("Content-Encoding", "gzip");
            }
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                // 复现线上压缩分段只读到首个“{”的响应；禁用压缩后仍分段发送完整 JSON。
                output.write(compressed ? gzip("{") : "{".getBytes(StandardCharsets.UTF_8));
                output.flush();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                String remainder = response.substring(1);
                output.write(compressed ? gzip(remainder) : remainder.getBytes(StandardCharsets.UTF_8));
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            RoutingAiModelConfig config = new RoutingAiModelConfig();
            config.setApiKey("test-key");
            config.setModelName("test-model");
            config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            AiCodeGenTypeRoutingService routingService = AiServices.builder(AiCodeGenTypeRoutingService.class)
                    .chatModel(config.routingChatModelPrototype())
                    .build();

            assertEquals(CodeGenTypeEnum.VUE_PROJECT,
                    routingService.routeCodeGenType("创建包含文章列表、详情页和评论系统的个人博客网站"));
        } finally {
            server.stop(0);
        }
    }

    private static byte[] gzip(String text) throws java.io.IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
}
