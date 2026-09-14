package com.wkr.aicode.core.handler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 双 Agent 流式输出标签测试：角色切换处插入来源标签，同角色连续输出不重复插入
 *
 * @author <a>百夜</a>
 */
class JsonMessageStreamHandlerTest {

    @Test
    void insertsHeadingOnlyWhenAgentRoleChanges() {
        AtomicReference<String> lastAgentRole = new AtomicReference<>();

        String first = JsonMessageStreamHandler.prependRoleHeading(lastAgentRole, "frontend", "生成登录页...");
        assertTrue(first.startsWith("\n\n### 前端专家\n\n"), first);
        assertTrue(first.endsWith("生成登录页..."), first);
        assertEquals("frontend", lastAgentRole.get());

        // 同角色连续输出：不再插入标签
        String same = JsonMessageStreamHandler.prependRoleHeading(lastAgentRole, "frontend", "继续...");
        assertEquals("继续...", same);

        // 角色切换：插入新标签
        String switched = JsonMessageStreamHandler.prependRoleHeading(lastAgentRole, "backend", "生成接口...");
        assertTrue(switched.startsWith("\n\n### 后端专家\n\n"), switched);
        assertEquals("backend", lastAgentRole.get());

        // 架构师角色同样有中文标签
        String architect = JsonMessageStreamHandler.prependRoleHeading(lastAgentRole, "architect", "设计契约...");
        assertTrue(architect.startsWith("\n\n### 架构师\n\n"), architect);
    }

    @Test
    void keepsPayloadUntouchedForLegacyAndEmptyMessages() {
        AtomicReference<String> lastAgentRole = new AtomicReference<>();

        // 非全栈模式（无 agentRole）：原样返回，旧行为不变
        assertEquals("普通模式输出", JsonMessageStreamHandler.prependRoleHeading(lastAgentRole, null, "普通模式输出"));
        assertNull(lastAgentRole.get(), "无角色时不应写入状态");

        // 空内容不消耗标签（同一角色的后续消息仍能带上标签）
        assertEquals("", JsonMessageStreamHandler.prependRoleHeading(lastAgentRole, "backend", ""));
        String afterEmpty = JsonMessageStreamHandler.prependRoleHeading(lastAgentRole, "backend", "接口已实现");
        assertTrue(afterEmpty.startsWith("\n\n### 后端专家\n\n"), afterEmpty);
    }
}
