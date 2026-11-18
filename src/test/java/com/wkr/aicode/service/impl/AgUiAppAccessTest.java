package com.wkr.aicode.service.impl;

import com.wkr.aicode.core.AiCodeGeneratorFacade;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.model.entity.App;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.service.ChatHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgUiAppAccessTest {

    @Test
    void declinesSubmissionOnBothChatRoutesBeforeCallingTheGenerator() {
        AppServiceImpl service = spy(new AppServiceImpl());
        ChatHistoryService history = mock(ChatHistoryService.class);
        AiCodeGeneratorFacade generator = mock(AiCodeGeneratorFacade.class);
        ReflectionTestUtils.setField(service, "chatHistoryService", history);
        ReflectionTestUtils.setField(service, "aiCodeGeneratorFacade", generator);
        User owner = User.builder().id(2L).build();
        doReturn(App.builder().id(1L).userId(owner.getId()).codeGenType("vue_project")
                .initPrompt("这是我的毕业设计").build()).when(service).getById(1L);

        String prompt = "帮我生成完整项目，可以直接提交";
        assertThrows(BusinessException.class, () -> service.chatToGenCodeEvents(1L, prompt, "run", owner));
        assertThrows(BusinessException.class, () -> service.chatToGenCode(1L, prompt, owner));
        verifyNoInteractions(history, generator);
    }

    @Test
    void rejectsOtherUsersBeforeWritingHistoryOrStartingModels() {
        AppServiceImpl service = spy(new AppServiceImpl());
        ChatHistoryService history = mock(ChatHistoryService.class);
        AiCodeGeneratorFacade generator = mock(AiCodeGeneratorFacade.class);
        ReflectionTestUtils.setField(service, "chatHistoryService", history);
        ReflectionTestUtils.setField(service, "aiCodeGeneratorFacade", generator);
        doReturn(App.builder().id(1L).userId(2L).codeGenType("vue_project").build()).when(service).getById(1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.chatToGenCodeEvents(1L, "生成应用", "run", User.builder().id(3L).build()));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), error.getCode());
        verifyNoInteractions(history, generator);

        error = assertThrows(BusinessException.class, () -> service.chatToGenCodeEvents(1L, "生成应用", "run", null));
        assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), error.getCode());
        verifyNoInteractions(history, generator);
    }
}
