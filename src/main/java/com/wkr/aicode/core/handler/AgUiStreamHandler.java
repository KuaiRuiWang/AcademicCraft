package com.wkr.aicode.core.handler;

import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.model.enums.CodeGenTypeEnum;
import com.wkr.aicode.monitor.MonitorContextHolder;
import com.wkr.aicode.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AG-UI 流的结束处理与历史持久化，不改变旧接口的文本流契约。
 */
@Component
@Slf4j
public class AgUiStreamHandler {

    @Resource
    private ChatHistoryService chatHistoryService;

    public Flux<Map<String, Object>> handle(Flux<String> source, Long appId, User user,
                                            CodeGenTypeEnum codeGenType, String runId) {
        return Flux.defer(() -> {
            AgUiEventAdapter adapter = new AgUiEventAdapter(appId, runId, codeGenType);
            AtomicReference<Long> historyId = new AtomicReference<>();
            // 先打开运行，再订阅模型；模型调用失败也会得到明确的 RUN_ERROR。
            return Flux.concat(Flux.just(adapter.start()), source.concatMap(chunk -> Flux.fromIterable(adapter.accept(chunk))))
                    .concatWith(Flux.defer(() -> Flux.fromIterable(adapter.complete())))
                    .onErrorResume(error -> {
                        log.error("AI 生成失败，appId={}，runId={}", appId, runId, error);
                        String message = error instanceof BusinessException ? error.getMessage() : "AI 生成失败，请稍后重试";
                        return Flux.fromIterable(adapter.fail("GENERATION_FAILED", message));
                    })
                    .doOnNext(event -> {
                        String type = String.valueOf(event.get("type"));
                        if (List.of("TOOL_CALL_RESULT", "SUBAGENT_FINISHED", "SUBAGENT_ERROR",
                                "STEP_FINISHED", "RUN_FINISHED", "RUN_ERROR").contains(type)) {
                            historyId.set(chatHistoryService.saveAgentRunSnapshot(historyId.get(), appId,
                                    user.getId(), adapter.transcript(), adapter.snapshot()));
                        }
                    })
                    .onErrorResume(error -> {
                        log.error("对话快照保存失败，appId={}，runId={}", appId, runId, error);
                        return Flux.just(Map.<String, Object>of("type", "RUN_ERROR", "code", "HISTORY_SAVE_FAILED",
                                "message", "对话记录保存失败，未能确认本轮生成结果，请稍后重试"));
                    })
                    .doFinally(signal -> {
                        if (signal == SignalType.CANCEL && !adapter.isTerminal()) {
                            adapter.fail("CONNECTION_CLOSED", "连接已中断，未确认的工具结果保持未知");
                            try {
                                chatHistoryService.saveAgentRunSnapshot(historyId.get(), appId,
                                        user.getId(), adapter.transcript(), adapter.snapshot());
                            } catch (Exception error) {
                                log.error("中断对话保存失败，appId={}，runId={}", appId, runId, error);
                            }
                        }
                        MonitorContextHolder.clearContext();
                    });
        });
    }
}
