package com.wkr.aicode.ai;

import com.wkr.aicode.core.model.ModelStep;
import com.wkr.aicode.core.model.PlatformModelRouter;
import com.wkr.aicode.model.entity.PlatformModel;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wkr.aicode.ai.guardrail.PromptSafetyInputGuardrail;
import com.wkr.aicode.ai.tool.ToolManager;
import com.wkr.aicode.ai.tool.ToolSetFactory;
import com.wkr.aicode.config.RedisChatMemoryStoreWithDb;
import com.wkr.aicode.constant.AppConstant;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.model.enums.AgentRoleEnum;
import com.wkr.aicode.model.enums.CodeGenTypeEnum;
import com.wkr.aicode.service.ChatHistoryService;
import com.wkr.aicode.utils.SpringContextUtil;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    @Resource
    private PlatformModelRouter models;

    private record ServiceKey(long appId, ModelStep step, PlatformModel model) { }


    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    @Resource
    private RedisChatMemoryStoreWithDb redisChatMemoryStoreWithDb;

    @Resource
    private ChatHistoryService chatHistoryService;


    /**
     * 默认提供一个 Bean
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0L);
    }

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<ServiceKey, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，原因: {}", cause);
            })
            .build();

    /**
     * 根据 appId 获取服务（带缓存）这个方法是为了兼容历史逻辑
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }


    
    /**
     * 根据 appId 和代码生成类型获取服务（带缓存）
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        return getAiCodeGeneratorService(appId, codeGenType, ModelStep.code(codeGenType, ModelStep.CodePhase.GENERATE));
    }

    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType, ModelStep step) {
        PlatformModel model = models.configuredModel(step);
        // 本次步骤使用一份配置快照，工具调用期间继续沿用同一模型。
        return serviceCache.get(new ServiceKey(appId, step, model), key -> createAiCodeGeneratorService(appId, codeGenType, model));
    }

    @Resource
    private ToolManager toolManager;

    @Resource
    private ToolSetFactory toolSetFactory;

    /**
     * 创建新的 AI 服务实例
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(Long appId, CodeGenTypeEnum codeGenType, PlatformModel model) {
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStoreWithDb)
                .maxMessages(20)
                .build();
        // 从数据库加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        // 根据代码类型保留部署默认模型，已绑定的模型优先。
        return switch (codeGenType) {
            case VUE_PROJECT -> {
                // 使用多例模式的 StreamingChatModel 解决并发问题
                StreamingChatModel reasoningStreamingChatModel = SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .streamingChatModel(models.stream(model, reasoningStreamingChatModel))
                        .chatMemoryProvider(memoryId -> chatMemory)
                        .tools(toolManager.getAllTools())
                        .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                                toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()
                        ))
                        .inputGuardrails(new PromptSafetyInputGuardrail()) // 添加输入内容安全检查
                        //.outputGuardrails(new RetryOutputGuardrail()) // 添加输出内容安全检查 不能流式输出，会缓存
                        .build();
            }
            case HTML, MULTI_FILE -> {
                // 使用多例模式的 StreamingChatModel 解决并发问题
                StreamingChatModel openAiStreamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(models.chat(model, chatModel))
                        .streamingChatModel(models.stream(model, openAiStreamingChatModel))
                        .chatMemory(chatMemory)
                        .inputGuardrails(new PromptSafetyInputGuardrail()) // 添加输入内容安全检查
                        //.outputGuardrails(new RetryOutputGuardrail()) // 添加输出内容安全检查 不能流式输出，会缓存
                        .build();
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "不支持的代码生成类型: " + codeGenType.getValue());
        };

    }

    /**
     * 获取全栈项目指定 Agent 的 AI 服务（带缓存）
     * 缓存键含角色，保证前端、后端 Agent 的记忆与工具集相互隔离
     */
    public AiCodeGeneratorService getFullstackAgentService(long appId, AgentRoleEnum agentRole, ModelStep step) {
        PlatformModel model = models.configuredModel(step);
        return serviceCache.get(new ServiceKey(appId, step, model), key -> createFullstackAgentService(appId, agentRole, model));
    }

    /**
     * 创建全栈项目 Agent 的 AI 服务实例
     */
    private AiCodeGeneratorService createFullstackAgentService(long appId, AgentRoleEnum agentRole, PlatformModel model) {
        // 每个 Agent 独立的工作区工具集：产物根目录 + 角色绑定，防止并行写冲突与越权
        String projectDir = AppConstant.fullstackProjectDir(appId);
        List<Object> tools = toolSetFactory.createFullstackTools(projectDir, agentRole);
        // 每个 Agent 独立的对话记忆，避免长对话交叉污染
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId + "_" + agentRole.getValue())
                .chatMemoryStore(redisChatMemoryStoreWithDb)
                .maxMessages(20)
                .build();
        // 使用多例模式的 StreamingChatModel 解决并发问题
        StreamingChatModel reasoningStreamingChatModel = SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);
        return AiServices.builder(AiCodeGeneratorService.class)
                .streamingChatModel(models.stream(model, reasoningStreamingChatModel))
                .chatMemoryProvider(memoryId -> chatMemory)
                .tools(tools.toArray())
                .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                        toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()
                ))
                // Agent 消息 = 用户需求 + API 契约 JSON + 修复提示，长度上限需放宽（用户输入已在架构师处校验）
                .inputGuardrails(new PromptSafetyInputGuardrail(20000))
                .build();
    }





}

