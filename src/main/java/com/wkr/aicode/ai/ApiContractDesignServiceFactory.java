package com.wkr.aicode.ai;

import com.wkr.aicode.ai.guardrail.PromptSafetyInputGuardrail;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 架构师 Agent 服务工厂
 * 契约设计无对话记忆、无工具，构建为单例即可
 *
 * @author <a>百夜</a>
 */
@Slf4j
@Configuration
public class ApiContractDesignServiceFactory {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    @Bean
    public ApiContractDesignService apiContractDesignService() {
        return AiServices.builder(ApiContractDesignService.class)
                .chatModel(chatModel)
                // 直接面向用户输入（架构师是用户需求的第一站），上限放宽以容纳重试时拼接的失败说明
                .inputGuardrails(new PromptSafetyInputGuardrail(2000))
                .build();
    }
}
