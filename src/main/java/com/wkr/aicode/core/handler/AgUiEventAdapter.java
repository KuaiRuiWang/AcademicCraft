package com.wkr.aicode.core.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.wkr.aicode.ai.message.ToolResult;
import com.wkr.aicode.model.enums.CodeGenTypeEnum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 把现有生成流转换为 AG-UI 1.0，并收集可恢复的紧凑展示快照。
 * 每个请求独立创建；用同一把锁保护输出与取消时的快照，角色之间不共享消息或工具状态。
 */
public class AgUiEventAdapter {

    private final String threadId;
    private final String runId;
    private final boolean plainText;
    private final Map<String, String> agentOwners = new LinkedHashMap<>();
    private final Map<String, String> openMessages = new LinkedHashMap<>();
    private final Map<String, ToolCall> tools = new LinkedHashMap<>();
    private final List<Map<String, Object>> snapshotEvents = new ArrayList<>();
    private final Map<String, StringBuilder> deltas = new HashMap<>();
    private final StringBuilder transcript = new StringBuilder();
    private boolean terminal;

    public AgUiEventAdapter(Long appId, String runId, CodeGenTypeEnum codeGenType) {
        this.threadId = "app-" + appId;
        this.runId = runId;
        this.plainText = codeGenType == CodeGenTypeEnum.HTML || codeGenType == CodeGenTypeEnum.MULTI_FILE;
    }

    public synchronized Map<String, Object> start() {
        return record(event("RUN_STARTED", "", "threadId", threadId, "runId", runId, "protocolVersion", "1.0"));
    }

    public synchronized List<Map<String, Object>> accept(String chunk) {
        if (terminal || StrUtil.isEmpty(chunk)) {
            return List.of();
        }
        List<Map<String, Object>> events = new ArrayList<>();
        if (plainText) {
            appendText("", chunk, events);
            return events;
        }
        JSONObject message = JSONUtil.parseObj(chunk);
        String role = message.getStr("agentRole", "");
        if ("workflow_event".equals(message.getStr("type"))) {
            handleWorkflow(message, role, events);
            return events;
        }
        String owner = agentOwners.getOrDefault(role, "");
        switch (message.getStr("type", "")) {
            case "ai_response" -> appendText(owner, message.getStr("data", ""), events);
            case "tool_request" -> appendToolArguments(message, owner, false, events);
            case "tool_ready" -> appendToolArguments(message, owner, true, events);
            case "tool_executed" -> appendToolResult(message, owner, events);
            default -> throw new IllegalArgumentException("不支持的生成事件类型");
        }
        return events;
    }

    public synchronized List<Map<String, Object>> complete() {
        if (terminal) {
            return List.of();
        }
        List<Map<String, Object>> events = new ArrayList<>();
        // 未收到结果的工具不能随整轮完成被标成成功。
        if (tools.values().stream().anyMatch(tool -> !tool.answered)) {
            return fail("INCOMPLETE_TOOL", "部分工具未返回执行结果，本轮生成未完成");
        }
        for (String owner : new ArrayList<>(openMessages.keySet())) {
            closeMessage(owner, events);
        }
        for (String owner : agentOwners.values()) {
            add(events, event("SUBAGENT_FINISHED", "", "subagentRunId", owner));
        }
        agentOwners.clear();
        terminal = true;
        add(events, event("RUN_FINISHED", "", "threadId", threadId, "runId", runId));
        return events;
    }

    public synchronized List<Map<String, Object>> fail(String code, String message) {
        if (terminal) {
            return List.of();
        }
        terminal = true;
        transcript.append("\n").append(message);
        List<Map<String, Object>> events = new ArrayList<>();
        add(events, event("RUN_ERROR", "", "code", code, "message", message));
        return events;
    }

    public synchronized boolean isTerminal() {
        return terminal;
    }

    public synchronized String transcript() {
        return StrUtil.blankToDefault(transcript.toString(), "本轮生成尚未产生正文");
    }

    public synchronized String snapshot() {
        List<Map<String, Object>> compact = new ArrayList<>();
        for (Map<String, Object> source : snapshotEvents) {
            Map<String, Object> saved = new LinkedHashMap<>(source);
            String key = deltaKey(source);
            if (key != null) {
                saved.put("delta", deltas.get(key).toString());
            }
            compact.add(saved);
        }
        return JSONUtil.toJsonStr(Map.of("version", 1, "runId", runId, "events", compact));
    }

    private void handleWorkflow(JSONObject message, String role, List<Map<String, Object>> events) {
        String name = message.getStr("name", "");
        String label = message.getStr("label", name);
        String owner = agentOwners.getOrDefault(role, "");
        switch (message.getStr("event", "")) {
            case "agent_started" -> {
                owner = "agent-" + UUID.randomUUID();
                agentOwners.put(role, owner);
                add(events, event("SUBAGENT_STARTED", "", "subagentRunId", owner,
                        "name", role, "description", label));
            }
            case "agent_finished", "agent_error" -> {
                closeMessage(owner, events);
                if ("agent_error".equals(message.getStr("event"))) {
                    add(events, event("SUBAGENT_ERROR", "", "subagentRunId", owner, "message", label));
                } else {
                    add(events, event("SUBAGENT_FINISHED", "", "subagentRunId", owner));
                }
                agentOwners.remove(role);
            }
            case "step_started", "step_finished" -> {
                boolean starting = "step_started".equals(message.getStr("event"));
                add(events, event(starting ? "STEP_STARTED" : "STEP_FINISHED", owner,
                        "stepName", name, "metadata", Map.of("label", label, "status", message.getStr("status", "running"))));
            }
            case "run_error" -> events.addAll(fail("GENERATION_FAILED", label));
            default -> throw new IllegalArgumentException("不支持的工作流事件");
        }
    }

    private void appendText(String owner, String delta, List<Map<String, Object>> events) {
        if (StrUtil.isEmpty(delta)) {
            return;
        }
        String messageId = openMessage(owner, events);
        add(events, event("TEXT_MESSAGE_CONTENT", owner, "messageId", messageId, "delta", delta));
        transcript.append(delta);
    }

    private ToolCall getTool(JSONObject message, String owner, List<Map<String, Object>> events) {
        String sourceId = message.getStr("id");
        if (StrUtil.isBlank(sourceId)) {
            throw new IllegalArgumentException("工具调用缺少标识");
        }
        String key = owner + ":" + sourceId;
        ToolCall existing = tools.get(key);
        if (existing != null) {
            return existing;
        }
        closeMessage(owner, events);
        String parentMessageId = openMessage(owner, events);
        ToolCall tool = new ToolCall("tool-" + UUID.randomUUID(), message.getStr("name"));
        tools.put(key, tool);
        add(events, event("TOOL_CALL_START", owner, "toolCallId", tool.id,
                "toolCallName", tool.name, "parentMessageId", parentMessageId));
        return tool;
    }

    private void appendToolArguments(JSONObject message, String owner, boolean complete, List<Map<String, Object>> events) {
        ToolCall tool = getTool(message, owner, events);
        String arguments = message.getStr("arguments", "");
        if (complete) {
            // 局部回调传的是增量，完成回调传的是全文；只补尚未收到的尾部。
            String received = tool.arguments.toString();
            if (!arguments.startsWith(received)) {
                throw new IllegalArgumentException("工具参数与流式片段不一致");
            }
            arguments = arguments.substring(received.length());
        }
        if (!arguments.isEmpty()) {
            tool.arguments.append(arguments);
            add(events, event("TOOL_CALL_ARGS", owner, "toolCallId", tool.id, "delta", arguments));
        }
        if (complete && !tool.closed) {
            tool.closed = true;
            add(events, event("TOOL_CALL_END", owner, "toolCallId", tool.id));
            closeMessage(owner, events);
        }
    }

    private void appendToolResult(JSONObject message, String owner, List<Map<String, Object>> events) {
        ToolCall tool = getTool(message, owner, events);
        if (tool.answered) {
            return;
        }
        if (!tool.closed) {
            appendToolArguments(message, owner, true, events);
        }
        ToolResult result = ToolResult.parse(message.getStr("result"));
        tool.answered = true;
        add(events, event("TOOL_CALL_RESULT", owner, "messageId", "result-" + UUID.randomUUID(),
                "toolCallId", tool.id, "content", result.toJson(), "metadata", Map.of("status", result.status())));
        transcript.append("\n[").append(tool.name).append("] ").append(result.summary()).append("\n");
    }

    private String openMessage(String owner, List<Map<String, Object>> events) {
        String messageId = openMessages.get(owner);
        if (messageId == null) {
            messageId = "message-" + UUID.randomUUID();
            openMessages.put(owner, messageId);
            add(events, event("TEXT_MESSAGE_START", owner, "messageId", messageId, "role", "assistant"));
        }
        return messageId;
    }

    private void closeMessage(String owner, List<Map<String, Object>> events) {
        String messageId = openMessages.remove(owner);
        if (messageId != null) {
            add(events, event("TEXT_MESSAGE_END", owner, "messageId", messageId));
        }
    }

    private void add(List<Map<String, Object>> events, Map<String, Object> event) {
        events.add(record(event));
    }

    private Map<String, Object> record(Map<String, Object> event) {
        String key = deltaKey(event);
        if (key == null) {
            snapshotEvents.add(new LinkedHashMap<>(event));
        } else {
            if (!deltas.containsKey(key)) {
                snapshotEvents.add(new LinkedHashMap<>(event));
                deltas.put(key, new StringBuilder());
            }
            deltas.get(key).append(event.get("delta"));
        }
        return event;
    }

    private String deltaKey(Map<String, Object> event) {
        return switch (String.valueOf(event.get("type"))) {
            case "TEXT_MESSAGE_CONTENT" -> "text:" + event.get("messageId");
            case "TOOL_CALL_ARGS" -> "args:" + event.get("toolCallId");
            default -> null;
        };
    }

    private Map<String, Object> event(String type, String owner, Object... fields) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("timestamp", System.currentTimeMillis());
        if (StrUtil.isNotBlank(owner)) {
            event.put("subagentRunId", owner);
        }
        for (int index = 0; index < fields.length; index += 2) {
            if (fields[index + 1] != null) {
                event.put(String.valueOf(fields[index]), fields[index + 1]);
            }
        }
        return event;
    }

    private static class ToolCall {
        private final String id;
        private final String name;
        private final StringBuilder arguments = new StringBuilder();
        private boolean closed;
        private boolean answered;

        private ToolCall(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
