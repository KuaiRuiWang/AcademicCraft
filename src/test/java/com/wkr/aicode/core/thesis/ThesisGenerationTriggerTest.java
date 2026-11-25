package com.wkr.aicode.core.thesis;

import com.wkr.aicode.ai.AiCodeGeneratorService;
import com.wkr.aicode.ai.AiCodeGeneratorServiceFactory;
import com.wkr.aicode.core.AiCodeGeneratorFacade;
import com.wkr.aicode.core.builder.VueProjectBuilder;
import com.wkr.aicode.core.model.ModelStep;
import com.wkr.aicode.model.enums.CodeGenTypeEnum;
import com.wkr.aicode.service.ThesisService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ThesisGenerationTriggerTest {
    @Test void successfulBuildStartsTheThesisEvenWhenTheBrowserDisconnected() {
        checkCompletion(true);
    }

    @Test void buildFailureDoesNotStartTheThesis() {
        checkCompletion(false);
    }

    private void checkCompletion(boolean built) {
        var facade = new AiCodeGeneratorFacade();
        var factory = mock(AiCodeGeneratorServiceFactory.class);
        var generator = mock(AiCodeGeneratorService.class);
        var builder = mock(VueProjectBuilder.class);
        var thesis = mock(ThesisService.class);
        TokenStream stream = mock(TokenStream.class, RETURNS_SELF);
        AtomicReference<Consumer<ChatResponse>> complete = new AtomicReference<>();
        when(factory.getAiCodeGeneratorService(1L, CodeGenTypeEnum.VUE_PROJECT, ModelStep.VUE_GENERATION)).thenReturn(generator);
        when(generator.generateVueProjectCodeStream(1L, "生成图书管理")).thenReturn(stream);
        when(builder.buildProject(anyString())).thenReturn(built);
        doAnswer(call -> { complete.set(call.getArgument(0)); return stream; }).when(stream).onCompleteResponse(any());
        ReflectionTestUtils.setField(facade, "aiCodeGeneratorServiceFactory", factory);
        ReflectionTestUtils.setField(facade, "vueProjectBuilder", builder);
        ReflectionTestUtils.setField(facade, "thesisService", thesis);
        var subscription = facade.generateAndSaveCodeStream("生成图书管理", CodeGenTypeEnum.VUE_PROJECT, 1L).subscribe();
        subscription.dispose();
        complete.get().accept(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());
        verify(thesis, built ? times(1) : never()).generationSucceeded(1L);
    }
}
