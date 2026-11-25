package com.wkr.aicode.core;

import com.wkr.aicode.core.model.ModelStep;
import com.wkr.aicode.core.model.ModelStep.CodePhase;
import cn.hutool.json.JSONUtil;
import com.wkr.aicode.ai.AiCodeGeneratorService;
import com.wkr.aicode.ai.AiCodeGeneratorServiceFactory;
import com.wkr.aicode.ai.message.AiResponseMessage;
import com.wkr.aicode.ai.message.ToolExecutedMessage;
import com.wkr.aicode.ai.message.ToolRequestMessage;
import com.wkr.aicode.ai.message.WorkflowEventMessage;
import com.wkr.aicode.ai.model.HtmlCodeResult;
import com.wkr.aicode.ai.model.MultiFileCodeResult;
import com.wkr.aicode.constant.AppConstant;
import com.wkr.aicode.core.builder.VueProjectBuilder;
import com.wkr.aicode.core.parser.CodeParserExecutor;
import com.wkr.aicode.core.saver.CodeFileSaverExecutor;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.langGrapgh4J.FullstackCodeGenWorkflow;
import com.wkr.aicode.langGrapgh4J.FullstackModifyWorkflow;
import com.wkr.aicode.model.enums.AgentRoleEnum;
import com.wkr.aicode.model.enums.CodeGenTypeEnum;
import com.wkr.aicode.service.ThesisService;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ThesisService thesisService;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum,Long appId) {
        // 根据 appId 获取对应的 AI 服务实例
        var step = ModelStep.code(codeGenTypeEnum, existingCodePhase(codeGenTypeEnum, appId));
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum, step);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML,appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE,appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }



    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        return generateAndSaveCodeStream(userMessage, codeGenTypeEnum, appId, existingCodePhase(codeGenTypeEnum, appId));
    }

    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId, CodePhase phase) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 全栈模式由工作流内部按角色创建 Agent 服务，不走单类型服务工厂
        if (CodeGenTypeEnum.FULLSTACK_VUE_NODEJS == codeGenTypeEnum) {
            // 已有完整产物（frontend/ + backend/ + api-contract.json）视为修改需求，走修改工作流（T-17）
            if (isGeneratedFullstackProject(AppConstant.fullstackProjectDir(appId))) {
                return new FullstackModifyWorkflow().executeWorkflowWithFlux(userMessage, appId);
            }
            return new FullstackCodeGenWorkflow().executeWorkflowWithFlux(userMessage, appId);
        }
        // 根据 appId 获取对应的 AI 服务实例
        var step = ModelStep.code(codeGenTypeEnum, phase);
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum, step);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML,appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE,appId);
            }
            case VUE_PROJECT -> {
                TokenStream codeStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(codeStream,appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    private CodePhase existingCodePhase(CodeGenTypeEnum type, long appId) {
        if (type == null || type == CodeGenTypeEnum.FULLSTACK_VUE_NODEJS) return CodePhase.GENERATE;
        String entryFile = type == CodeGenTypeEnum.VUE_PROJECT ? "package.json" : "index.html";
        File entry = new File(AppConstant.codeOutputDir(type.getValue(), appId), entryFile);
        return entry.isFile() ? CodePhase.MODIFY : CodePhase.GENERATE;
    }

    /**
     * 判断全栈项目是否已生成完整产物（前端目录 + 后端目录 + API 契约文件齐全）
     *
     * @param projectDir 全栈项目产物根目录
     */
    static boolean isGeneratedFullstackProject(String projectDir) {
        return new File(projectDir, "frontend").isDirectory()
                && new File(projectDir, "backend").isDirectory()
                && new File(projectDir, ApiContractDesignFacade.CONTRACT_FILE_NAME).isFile();
    }

    /**
     * 生成全栈项目中指定 Agent 的代码（流式）
     * 仅负责生成与流式推送，构建与部署由后续节点处理
     *
     * @param appId       应用 ID
     * @param agentRole   Agent 角色（前端 / 后端）
     * @param userMessage 用户消息（需求描述 + API 契约）
     * @return Flux<String> 流式响应
     */
    public Flux<String> generateFullstackAgentCodeStream(Long appId, AgentRoleEnum agentRole, String userMessage, ModelStep step) {
        AiCodeGeneratorService agentService = aiCodeGeneratorServiceFactory.getFullstackAgentService(appId, agentRole, step);
        TokenStream tokenStream = switch (agentRole) {
            case FRONTEND -> agentService.generateFullstackFrontendCodeStream(appId, userMessage);
            case BACKEND -> agentService.generateNodeBackendCodeStream(appId, userMessage);
            case ARCHITECT -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "架构师不通过代码生成服务产出");
        };
        return processAgentTokenStream(tokenStream, agentRole);
    }

    /**
     * 将全栈 Agent 的 TokenStream 转换为 Flux<String>，并传递工具调用信息
     * 每条消息都标注产出它的 Agent 角色，供前端按角色分栏展示
     */
    private Flux<String> processAgentTokenStream(TokenStream tokenStream, AgentRoleEnum agentRole) {
        String role = agentRole.getValue();
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) ->
                            sink.next(JSONUtil.toJsonStr(new AiResponseMessage(partialResponse, role))))
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) ->
                            sink.next(JSONUtil.toJsonStr(new ToolRequestMessage(toolExecutionRequest, role))))
                    .onCompleteToolExecutionRequest((index, request) ->
                            sink.next(JSONUtil.toJsonStr(ToolRequestMessage.ready(request, role))))
                    .onToolExecuted((ToolExecution toolExecution) ->
                            sink.next(JSONUtil.toJsonStr(new ToolExecutedMessage(toolExecution, role))))
                    .onCompleteResponse((ChatResponse response) -> sink.complete())
                    .onError(sink::error)
                    .start();
        });
    }


    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @param appId       应用 ID
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream,Long appId) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onCompleteToolExecutionRequest((index, request) ->
                            sink.next(JSONUtil.toJsonStr(ToolRequestMessage.ready(request, null))))
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
                        sink.next(JSONUtil.toJsonStr(new WorkflowEventMessage("step_started", "build", "项目构建", "running", null)));
                        boolean built = vueProjectBuilder.buildProject(projectPath);
                        sink.next(JSONUtil.toJsonStr(new WorkflowEventMessage("step_finished", "build",
                                built ? "项目构建完成" : "前端构建失败", built ? "success" : "error", null)));
                        if (!built) {
                            sink.error(new BusinessException(ErrorCode.OPERATION_ERROR, "前端构建失败，可继续对话修复生成的代码"));
                            return;
                        }
                        thesisService.generationSucceeded(appId);
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }


    /**
     * 生成 HTML 模式的代码并保存
     *
     * @param userMessage 用户提示词
     * @return 保存的目录
     */
//    private File generateAndSaveHtmlCode(String userMessage) {
//
//        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
//        return CodeFileSaver.saveHtmlCodeResult(result);
//    }

    /**
     * 生成多文件模式的代码并保存
     *
     * @param userMessage 用户提示词
     * @return 保存的目录
     */
//    private File generateAndSaveMultiFileCode(String userMessage) {
//        MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
//        return CodeFileSaver.saveMultiFileCodeResult(result);
//    }

    /**
     * 通用流式代码处理方法
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType,Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(chunk -> {
            // 实时收集代码片段
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            // 流式返回完成后保存代码
            try {
                String completeCode = codeBuilder.toString();
                log.info(completeCode);
                // 使用执行器解析代码
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 使用执行器保存代码
                File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType,appId);
                log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
                thesisService.generationSucceeded(appId);
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage(), e);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "生成代码保存失败，请重试");
            }
        });
    }

}
