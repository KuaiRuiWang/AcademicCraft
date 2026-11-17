package com.wkr.aicode.core.handler;

import cn.hutool.json.JSONUtil;
import com.wkr.aicode.ai.message.AiResponseMessage;
import com.wkr.aicode.ai.message.ToolResult;
import com.wkr.aicode.ai.message.WorkflowEventMessage;
import com.wkr.aicode.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgUiEventAdapterTest {

    @Test
    void preservesParallelOwnershipAndRealToolResults() throws Exception {
        AgUiEventAdapter adapter = new AgUiEventAdapter(1L, "contract-run", CodeGenTypeEnum.FULLSTACK_VUE_NODEJS);
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(adapter.start());
        events.addAll(adapter.accept(workflow("agent_started", "frontend")));
        events.addAll(adapter.accept(workflow("agent_started", "backend")));
        events.addAll(adapter.accept(JSONUtil.toJsonStr(new AiResponseMessage("正在生成页面", "frontend"))));
        events.addAll(adapter.accept(JSONUtil.toJsonStr(new AiResponseMessage("正在生成接口", "backend"))));
        events.addAll(adapter.accept(tool("tool_request", "frontend", "same-id", "{\"relativeFilePath\":")));
        events.addAll(adapter.accept(tool("tool_request", "backend", "same-id", "{\"relativeFilePath\":\"backend/app.js\"}")));
        events.addAll(adapter.accept(tool("tool_request", "frontend", "same-id", "\"frontend/App.vue\"}")));
        events.addAll(adapter.accept(tool("tool_ready", "frontend", "same-id", "{\"relativeFilePath\":\"frontend/App.vue\"}")));
        events.addAll(adapter.accept(tool("tool_ready", "backend", "same-id", "{\"relativeFilePath\":\"backend/app.js\"}")));
        assertTrue(events.stream().noneMatch(event -> "TOOL_CALL_RESULT".equals(event.get("type"))));
        events.addAll(adapter.accept(result("frontend", "same-id", "error", "文件写入失败")));
        events.addAll(adapter.accept(result("backend", "same-id", "success", "文件已写入")));
        events.addAll(adapter.accept(workflow("agent_finished", "frontend")));
        events.addAll(adapter.accept(workflow("agent_finished", "backend")));
        events.addAll(adapter.complete());

        List<Map<String, Object>> starts = events.stream().filter(event -> "TOOL_CALL_START".equals(event.get("type"))).toList();
        assertEquals(2, starts.size());
        assertNotEquals(starts.get(0).get("toolCallId"), starts.get(1).get("toolCallId"));
        assertNotEquals(starts.get(0).get("subagentRunId"), starts.get(1).get("subagentRunId"));
        Map<String, Object> failure = events.stream().filter(event -> "TOOL_CALL_RESULT".equals(event.get("type"))).findFirst().orElseThrow();
        assertEquals("error", JSONUtil.parseObj(failure.get("content")).getStr("status"));
        assertEquals("RUN_FINISHED", events.getLast().get("type"));

        // 前端测试把实际 Java 事件送入官方 HttpAgent，校验双方协议契约。
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/ag-ui-contract.json"), JSONUtil.toJsonStr(events));
    }

    @Test
    void createsANewInvocationForEachRepairRoundAndCompactsDeltas() {
        AgUiEventAdapter adapter = new AgUiEventAdapter(1L, "run", CodeGenTypeEnum.FULLSTACK_VUE_NODEJS);
        adapter.start();
        List<Map<String, Object>> first = adapter.accept(workflow("agent_started", "frontend"));
        for (int index = 0; index < 1000; index++) {
            adapter.accept(JSONUtil.toJsonStr(new AiResponseMessage("字", "frontend")));
        }
        adapter.accept(workflow("agent_finished", "frontend"));
        List<Map<String, Object>> second = adapter.accept(workflow("agent_started", "frontend"));
        assertNotEquals(first.getFirst().get("subagentRunId"), second.getFirst().get("subagentRunId"));
        List<?> saved = JSONUtil.parseObj(adapter.snapshot()).getJSONArray("events");
        assertEquals(1, saved.stream().filter(item -> "TEXT_MESSAGE_CONTENT".equals(JSONUtil.parseObj(item).getStr("type"))).count(),
                "千个文本片段应合并为一个快照片段");
        assertEquals("字".repeat(1000), adapter.transcript());
    }

    @Test
    void incompleteToolsAndWorkflowFailuresCannotBecomeSuccessfulRuns() {
        AgUiEventAdapter adapter = new AgUiEventAdapter(1L, "run", CodeGenTypeEnum.VUE_PROJECT);
        adapter.start();
        adapter.accept(tool("tool_ready", null, "call", "{}"));
        assertEquals("RUN_ERROR", adapter.complete().getLast().get("type"));
        assertTrue(adapter.complete().isEmpty());

        AgUiEventAdapter failed = new AgUiEventAdapter(1L, "failed", CodeGenTypeEnum.FULLSTACK_VUE_NODEJS);
        failed.start();
        var events = failed.accept(JSONUtil.toJsonStr(new WorkflowEventMessage("run_error", "build", "构建失败", "error", null)));
        assertEquals("RUN_ERROR", events.getFirst().get("type"));
        assertTrue(failed.complete().isEmpty());
    }

    private String workflow(String event, String role) {
        return JSONUtil.toJsonStr(new WorkflowEventMessage(event, role, role, "success", role));
    }

    private String tool(String type, String role, String id, String arguments) {
        return JSONUtil.createObj().set("type", type).set("agentRole", role).set("id", id)
                .set("name", "writeFile").set("arguments", arguments).toString();
    }

    private String result(String role, String id, String status, String summary) {
        return JSONUtil.createObj().set("type", "tool_executed").set("agentRole", role).set("id", id)
                .set("name", "writeFile").set("arguments", "{}")
                .set("result", new ToolResult(status, summary, null).toJson()).toString();
    }
}
