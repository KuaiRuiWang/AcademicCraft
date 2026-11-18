package com.wkr.aicode.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wkr.aicode.config.JsonConfig;
import com.wkr.aicode.core.handler.AgUiEventAdapter;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.GlobalExceptionHandler;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.model.enums.CodeGenTypeEnum;
import com.wkr.aicode.service.AppService;
import com.wkr.aicode.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgUiControllerTest {

    private final AppService apps = mock(AppService.class);
    private final UserService users = mock(UserService.class);
    private final User user = User.builder().id(2L).build();
    private final ObjectMapper objectMapper = new JsonConfig()
            .jacksonObjectMapper(Jackson2ObjectMapperBuilder.json());
    private MockMvc mvc;
    private static final String INPUT = """
            {"threadId":"app-1","runId":"run","protocolVersion":"1.0","state":{},"tools":[],"context":[],"forwardedProps":{},"messages":[{"role":"user","content":"生成待办应用"}]}
            """;

    @BeforeEach
    void setUp() {
        AppController controller = new AppController();
        ReflectionTestUtils.setField(controller, "appService", apps);
        ReflectionTestUtils.setField(controller, "userService", users);
        when(users.getLoginUser(any())).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void streamsProtocolEventsInSseDataRatherThanLegacyTextWrappers() throws Exception {
        // 使用项目实际的 Long 序列化配置，覆盖转换器输出到 SSE 的完整边界。
        assertEquals("\"9223372036854775807\"", objectMapper.writeValueAsString(Long.MAX_VALUE));
        AgUiEventAdapter adapter = new AgUiEventAdapter(1L, "run", CodeGenTypeEnum.HTML);
        var events = new ArrayList<Map<String, Object>>();
        events.add(adapter.start());
        events.addAll(adapter.accept("生成完成"));
        events.addAll(adapter.complete());
        when(apps.chatToGenCodeEvents(1L, "生成待办应用", "run", user)).thenReturn(Flux.fromIterable(events));
        var result = mvc.perform(post("/app/1/agent/run").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM).content(INPUT)).andExpect(request().asyncStarted()).andReturn();
        var response = mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data:{\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("RUN_FINISHED"))).andReturn();
        String wire = response.getResponse().getContentAsString(StandardCharsets.UTF_8);
        var dataLines = wire.lines().filter(line -> line.startsWith("data:")).toList();
        assertEquals(events.size(), dataLines.size());
        for (String line : dataLines) {
            var event = objectMapper.readTree(line.substring(5));
            assertTrue(event.get("timestamp").isIntegralNumber(), "AG-UI timestamp 必须是 JSON 数字：" + line);
        }
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/ag-ui-http-contract.sse"), wire, StandardCharsets.UTF_8);
    }

    @Test
    void heartbeatsStopWithTheRunAndNeverSubscribeToGenerationTwice() {
        AtomicInteger subscriptions = new AtomicInteger();
        Flux<Map<String, Object>> source = Flux.defer(() -> {
            subscriptions.incrementAndGet();
            return Flux.just(Map.<String, Object>of("type", "RUN_STARTED"), Map.<String, Object>of("type", "RUN_FINISHED"))
                    .delayElements(Duration.ofMillis(25));
        });
        var frames = AppController.keepAlive(source, Duration.ofMillis(5)).collectList().block(Duration.ofSeconds(2));
        org.junit.jupiter.api.Assertions.assertNotNull(frames);
        org.junit.jupiter.api.Assertions.assertEquals(1, subscriptions.get());
        org.junit.jupiter.api.Assertions.assertEquals(2, frames.stream().filter(frame -> frame.data() != null).count());
        org.junit.jupiter.api.Assertions.assertTrue(frames.stream().anyMatch(frame -> "keep-alive".equals(frame.comment())));
    }

    @Test
    void rejectsMismatchedThreadsAndUnauthorizedUsersBeforeOpeningTheStream() throws Exception {
        mvc.perform(post("/app/1/agent/run").contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                .content(INPUT.replace("app-1", "app-9"))).andExpect(status().isBadRequest());
        verifyNoInteractions(apps);
        when(users.getLoginUser(any())).thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));
        mvc.perform(post("/app/1/agent/run").contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                .content(INPUT)).andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
