package com.wkr.aicode.langGrapgh4J.ai;

import com.wkr.aicode.core.model.ModelStep;
import com.wkr.aicode.core.model.PlatformModelRouter;

import com.wkr.aicode.langGrapgh4J.tools.ImageSearchTool;
import com.wkr.aicode.langGrapgh4J.tools.LogoGeneratorTool;
import com.wkr.aicode.langGrapgh4J.tools.MermaidDiagramTool;
import com.wkr.aicode.langGrapgh4J.tools.UndrawIllustrationTool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ImageCollectionServiceFactory {

    @Resource
    private PlatformModelRouter models;

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    @Resource
    private ImageSearchTool imageSearchTool;

    @Resource
    private UndrawIllustrationTool undrawIllustrationTool;

    @Resource
    private MermaidDiagramTool mermaidDiagramTool;

    @Resource
    private LogoGeneratorTool logoGeneratorTool;

    /**
     * 创建图片收集 AI 服务
     */
    @Bean
    public ImageCollectionService createImageCollectionService() {
        return AiServices.builder(ImageCollectionService.class)
                .chatModel(models.chat(ModelStep.IMAGE_COLLECTION, chatModel))
                .tools(
                        imageSearchTool,
                        undrawIllustrationTool,
                        mermaidDiagramTool,
                        logoGeneratorTool
                )
                .build();
    }
}
