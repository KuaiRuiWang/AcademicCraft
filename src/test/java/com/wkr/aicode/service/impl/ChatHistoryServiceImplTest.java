package com.wkr.aicode.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对话历史服务单元测试：超长消息落库前的字节级截断
 * （背景见任务文档 T-18 用例 7：message 列容量不足会让 SSE 流中断且 done 事件不送达）
 *
 * @author <a>百夜</a>
 */
class ChatHistoryServiceImplTest {

    private final ChatHistoryServiceImpl service = new ChatHistoryServiceImpl();

    @Test
    void keepsShortMessageUnchanged() {
        String message = "把首页标题改成蓝色";
        assertEquals(message, service.truncateIfTooLong(1L, "ai", message));
    }

    @Test
    void truncatesWhenExceedingByteLimit() {
        // 3 字节/汉字，40 万个汉字 = 120 万字节，超出 100 万字节上限
        String message = "中".repeat(400_000);
        String truncated = service.truncateIfTooLong(1L, "ai", message);
        int bytes = truncated.getBytes(StandardCharsets.UTF_8).length;
        assertTrue(bytes <= 1_000_000, "截断后字节数应不超过上限，实际 " + bytes);
        assertTrue(bytes > 999_000, "不应过度截断，实际 " + bytes);
    }

    @Test
    void truncationLandsOnCharacterBoundary() {
        // 混合多字节字符，截断点必须落在字符边界，避免出现替换符乱码
        String message = "a中b文c".repeat(100_000);
        String truncated = service.truncateIfTooLong(1L, "ai", message);
        assertTrue(truncated.getBytes(StandardCharsets.UTF_8).length <= 1_000_000);
        assertTrue(truncated.indexOf('�') < 0, "截断不应产生乱码替换符");
        assertTrue(message.startsWith(truncated), "截断应保留原消息前缀");
    }
}
