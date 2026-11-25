package com.wkr.aicode.langGrapgh4J.ai;

import com.wkr.aicode.core.model.ModelStep;
import com.wkr.aicode.core.model.PlatformModelRouter;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ImageCollectionPlanServiceFactory {

    @Resource
    private PlatformModelRouter models;

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    @Bean
    public ImageCollectionPlanService createImageCollectionPlanService() {
        return AiServices.builder(ImageCollectionPlanService.class)
                .chatModel(models.chat(ModelStep.IMAGE_PLAN, chatModel))
                .build();
    }
}
