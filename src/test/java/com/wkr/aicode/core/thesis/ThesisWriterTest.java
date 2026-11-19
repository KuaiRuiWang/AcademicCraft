package com.wkr.aicode.core.thesis;

import com.wkr.aicode.exception.BusinessException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ThesisWriterTest {
    @Test void sendsTheFrozenSourceAndDeclinesTruncatedOutput() {
        ChatModel model = mock(ChatModel.class);
        ThesisWriter writer = new ThesisWriter();
        ReflectionTestUtils.setField(writer, "chatModel", model);
        when(model.chat(anyList())).thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("部分正文"))
                .finishReason(FinishReason.LENGTH).build());
        assertThrows(BusinessException.class, () -> writer.writeChapter("图书管理", "第三章 需求分析", "App.vue 实际页面", "绪论摘要"));
        @SuppressWarnings("unchecked") ArgumentCaptor<List<ChatMessage>> messages = ArgumentCaptor.forClass(List.class);
        verify(model).chat(messages.capture());
        String input = ((UserMessage) messages.getValue().get(1)).singleText();
        assertTrue(input.contains("App.vue 实际页面"));
        assertTrue(input.contains("第三章 需求分析"));
    }
}
