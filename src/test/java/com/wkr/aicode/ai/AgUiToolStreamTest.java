package com.wkr.aicode.ai;

import cn.hutool.json.JSONUtil;
import com.sun.net.httpserver.HttpServer;
import com.wkr.aicode.ai.message.ToolExecutedMessage;
import com.wkr.aicode.ai.message.ToolRequestMessage;
import com.wkr.aicode.ai.message.ToolResult;
import com.wkr.aicode.ai.message.WorkflowEventMessage;
import com.wkr.aicode.core.handler.AgUiEventAdapter;
import com.wkr.aicode.model.enums.CodeGenTypeEnum;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 实际模型 HTTP 适配的本地测试，不使用外部模型或额度。 */
class AgUiToolStreamTest {

    @Test
    void interleavedToolArgumentsEndOnlyAfterBothCallsAreComplete() throws Exception {
        List<Map<String, Object>> chunks = List.of(
                chunk(0, "call-a", "writeFile", "{\"relativeFilePath\":"),
                chunk(1, "call-b", "writeFile", "{\"relativeFilePath\":"),
                chunk(0, null, null, "\"frontend/A.vue\"}"),
                chunk(1, null, null, "\"frontend/B.vue\"}"));
        StringBuilder body = new StringBuilder();
        chunks.forEach(chunk -> body.append("data: ").append(JSONUtil.toJsonStr(chunk)).append("\n\n"));
        body.append("data: [DONE]\n\n");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                    .apiKey("local-test").modelName("test-model").timeout(Duration.ofSeconds(5)).build();
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicBoolean completedTooEarly = new AtomicBoolean();
            List<ToolExecutionRequest> completed = new CopyOnWriteArrayList<>();
            List<Map<String, Object>> events = new ArrayList<>();
            AgUiEventAdapter adapter = new AgUiEventAdapter(1L, "interleaved-run", CodeGenTypeEnum.FULLSTACK_VUE_NODEJS);
            events.add(adapter.start());
            events.addAll(adapter.accept(JSONUtil.toJsonStr(new WorkflowEventMessage("agent_started", "frontend", "前端智能体", "running", "frontend"))));
            model.chat(ChatRequest.builder().messages(List.of(UserMessage.from("本地协议测试"))).build(), new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String text) { }

                @Override
                public void onPartialToolExecutionRequest(int index, ToolExecutionRequest request) {
                    if (!completed.isEmpty()) completedTooEarly.set(true);
                    events.addAll(adapter.accept(JSONUtil.toJsonStr(new ToolRequestMessage(request, "frontend"))));
                }

                @Override
                public void onCompleteToolExecutionRequest(int index, ToolExecutionRequest request) {
                    completed.add(request);
                    events.addAll(adapter.accept(JSONUtil.toJsonStr(ToolRequestMessage.ready(request, "frontend"))));
                }

                @Override
                public void onCompleteResponse(ChatResponse response) { done.countDown(); }

                @Override
                public void onError(Throwable error) { failure.set(error); done.countDown(); }
            });
            assertTrue(done.await(10, TimeUnit.SECONDS), "本地模型响应未结束");
            assertNull(failure.get());
            assertFalse(completedTooEarly.get(), "切换工具索引不能提前结束参数");
            assertEquals(2, completed.size());
            assertEquals(List.of("call-a", "call-b"), completed.stream().map(ToolExecutionRequest::id).toList());
            assertEquals(List.of("frontend/A.vue", "frontend/B.vue"), completed.stream()
                    .map(request -> JSONUtil.parseObj(request.arguments()).getStr("relativeFilePath")).toList());
            for (ToolExecutionRequest request : completed) {
                ToolExecution execution = ToolExecution.builder().request(request)
                        .result(new ToolResult("success", "文件已写入", null).toJson()).build();
                events.addAll(adapter.accept(JSONUtil.toJsonStr(new ToolExecutedMessage(execution, "frontend"))));
            }
            events.addAll(adapter.accept(JSONUtil.toJsonStr(new WorkflowEventMessage("agent_finished", "frontend", "前端智能体", "success", "frontend"))));
            events.addAll(adapter.complete());
            Files.createDirectories(Path.of("target"));
            Files.writeString(Path.of("target/ag-ui-interleaved-tools.json"), JSONUtil.toJsonStr(events));
        } finally {
            server.stop(0);
        }
    }

    private Map<String, Object> chunk(int index, String id, String name, String arguments) {
        var tool = JSONUtil.createObj().set("index", index).set("id", id).set("type", "function")
                .set("function", JSONUtil.createObj().set("name", name).set("arguments", arguments));
        return Map.of("id", "completion", "model", "test-model", "object", "chat.completion.chunk",
                "choices", List.of(Map.of("index", 0, "delta", Map.of("tool_calls", List.of(tool)))));
    }
}
