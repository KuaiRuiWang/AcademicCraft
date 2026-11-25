package com.wkr.aicode.ai;

import com.wkr.aicode.core.model.ModelStep;
import com.wkr.aicode.core.model.PlatformModelRouter;

import com.wkr.aicode.utils.SpringContextUtil;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.annotation.Resource;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全栈项目修改需求意图路由服务工厂（T-17）
 *
 * @author <a>百夜</a>
 */
@Slf4j
@Configuration
public class AiModifyIntentRoutingServiceFactory {

    @Resource
    private PlatformModelRouter models;

    /**
     * 创建修改需求意图路由服务实例
     */
    public AiModifyIntentRoutingService createAiModifyIntentRoutingService() {
        // 动态获取多例的路由 ChatModel，支持并发
        ChatModel chatModel = SpringContextUtil.getBean("routingChatModelPrototype", ChatModel.class);
        return AiServices.builder(AiModifyIntentRoutingService.class)
                .chatModel(models.chat(ModelStep.MODIFY_INTENT_ROUTING, chatModel))
                .build();
    }

    /**
     * 默认提供一个 Bean
     */
    @Bean
    public AiModifyIntentRoutingService aiModifyIntentRoutingService() {
        return createAiModifyIntentRoutingService();
    }
}
