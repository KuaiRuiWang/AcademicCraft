package com.wkr.aicode.core.handler;

import cn.hutool.json.JSONUtil;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.model.enums.CodeGenTypeEnum;
import com.wkr.aicode.model.enums.AgentRoleEnum;
import com.wkr.aicode.langGrapgh4J.node.fullstack.AgentStreamPipe;
import com.wkr.aicode.service.ChatHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgUiStreamHandlerTest {

    private final ChatHistoryService history = mock(ChatHistoryService.class);
    private final AgUiStreamHandler handler = new AgUiStreamHandler();
    private final User user = User.builder().id(2L).build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "chatHistoryService", history);
        when(history.saveAgentRunSnapshot(any(), eq(1L), eq(2L), anyString(), anyString())).thenReturn(10L);
    }

    @Test
    void savesPlainContextSeparatelyFromDisplaySnapshot() {
        List<Map<String, Object>> events = handler.handle(Flux.just("你好", "，完成"), 1L, user,
                CodeGenTypeEnum.HTML, "run").collectList().block();
        assertNotNull(events);
        assertEquals("RUN_FINISHED", events.getLast().get("type"));
        verify(history).saveAgentRunSnapshot(isNull(), eq(1L), eq(2L), eq("你好，完成"), argThat(snapshot ->
                JSONUtil.parseObj(snapshot).getInt("version") == 1));
    }

    @Test
    void savesPartialOutputOnFailureAndDoesNotEmitFinished() {
        List<Map<String, Object>> events = handler.handle(Flux.concat(Flux.just("部分输出"), Flux.error(new IllegalStateException("upstream"))),
                1L, user, CodeGenTypeEnum.HTML, "run").collectList().block();
        assertNotNull(events);
        assertEquals("RUN_ERROR", events.getLast().get("type"));
        assertTrue(events.stream().noneMatch(event -> "RUN_FINISHED".equals(event.get("type"))));
        verify(history).saveAgentRunSnapshot(isNull(), eq(1L), eq(2L), contains("部分输出"), contains("RUN_ERROR"));
    }

    @Test
    void historyFailureIsReportedInsteadOfSuccessfulCompletion() {
        when(history.saveAgentRunSnapshot(any(), anyLong(), anyLong(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("missing renderData"));
        var events = handler.handle(Flux.just("内容"), 1L, user, CodeGenTypeEnum.HTML, "run").collectList().block();
        assertNotNull(events);
        assertEquals("HISTORY_SAVE_FAILED", events.getLast().get("code"));
        assertTrue(events.stream().noneMatch(event -> "RUN_FINISHED".equals(event.get("type"))));
    }

    @Test
    void failingAgentClosesItsOwnInvocationAndPersistsThePartialOutput() {
        Flux<String> source = Flux.create(sink -> {
            AgentStreamPipe pipe = new AgentStreamPipe(sink).forRole(AgentRoleEnum.FRONTEND);
            try {
                pipe.runAgent(() -> {
                    pipe.emitAiResponse("前端部分输出");
                    throw new IllegalStateException("simulated model error");
                });
                sink.complete();
            } catch (Exception error) {
                sink.error(error);
            }
        });
        var events = handler.handle(source, 1L, user, CodeGenTypeEnum.FULLSTACK_VUE_NODEJS, "run").collectList().block();
        assertNotNull(events);
        assertTrue(events.stream().anyMatch(event -> "SUBAGENT_ERROR".equals(event.get("type"))));
        assertEquals("RUN_ERROR", events.getLast().get("type"));
        verify(history).saveAgentRunSnapshot(eq(10L), eq(1L), eq(2L), contains("前端部分输出"), contains("RUN_ERROR"));
    }

    @Test
    void disconnectKeepsUnknownResultsAndPersistsAnInterruptedSnapshot() {
        handler.handle(Flux.concat(Flux.just("开始"), Flux.never()), 1L, user, CodeGenTypeEnum.HTML, "run")
                .take(3).blockLast();
        verify(history).saveAgentRunSnapshot(isNull(), eq(1L), eq(2L), contains("连接已中断"), contains("CONNECTION_CLOSED"));
    }
}
