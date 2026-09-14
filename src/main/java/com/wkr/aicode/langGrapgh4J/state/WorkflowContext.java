package com.wkr.aicode.langGrapgh4J.state;

import com.wkr.aicode.ai.model.ApiContract;
import com.wkr.aicode.langGrapgh4J.model.ImageCollectionPlan;
import com.wkr.aicode.langGrapgh4J.model.ImageResource;
import com.wkr.aicode.langGrapgh4J.model.QualityResult;
import com.wkr.aicode.model.enums.CodeGenTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 工作流上下文 - 存储所有状态信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowContext implements Serializable {

    /**
     * WorkflowContext 在 MessagesState 中的存储key
     */
    public static final String WORKFLOW_CONTEXT_KEY = "workflowContext";

    /**
     * 应用 ID
     */
    private Long appId = 0L;


    /**
     * 当前执行步骤
     */
    private String currentStep;

    /**
     * 用户原始输入的提示词
     */
    private String originalPrompt;

    /**
     * 图片资源字符串
     */
    private String imageListStr;

    /**
     * 图片资源列表
     */
    private List<ImageResource> imageList;

    /**
     * 增强后的提示词
     */
    private String enhancedPrompt;

    /**
     * 代码生成类型
     */
    private CodeGenTypeEnum generationType;

    /**
     * 生成的代码目录
     */
    private String generatedCodeDir;

    /**
     * 构建成功的目录
     */
    private String buildResultDir;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 质量检查结果
     */
    private QualityResult qualityResult;

    /**
     * 前后端共用的 API 契约（全栈工作流）
     */
    private ApiContract apiContract;

    /**
     * 全栈项目产物根目录（全栈工作流）
     */
    private String fullstackProjectDir;

    /**
     * 前端产物目录（全栈工作流）
     */
    private String frontendDir;

    /**
     * 后端产物目录（全栈工作流）
     */
    private String backendDir;

    /**
     * 契约一致性校验结果（全栈工作流）
     */
    private QualityResult contractCheckResult;

    /**
     * 契约校验已执行轮次（全栈工作流）：第 1 轮为初次校验，之后每轮为修复后的复检
     */
    private int contractCheckRound;

    /**
     * 前端待修复问题（契约校验产出，由前端 Agent 下一轮消费）
     */
    private List<String> frontendFixHints;

    /**
     * 后端待修复问题（契约校验产出，由后端 Agent 下一轮消费）
     */
    private List<String> backendFixHints;

    @Serial
    private static final long serialVersionUID = 1L;

    // ========== 上下文操作方法 ==========

    /**
     * 从 MessagesState 中获取 WorkflowContext
     */
    public static WorkflowContext getContext(MessagesState<String> state) {
        return (WorkflowContext) state.data().get(WORKFLOW_CONTEXT_KEY);
    }

    /**
     * 将 WorkflowContext 保存到 MessagesState 中
     */
    public static Map<String, Object> saveContext(WorkflowContext context) {
        return Map.of(WORKFLOW_CONTEXT_KEY, context);
    }

    /**
     * 图片收集计划
     */
    private ImageCollectionPlan imageCollectionPlan;

    /**
     * 并发图片收集的中间结果字段
     */
    private List<ImageResource> contentImages;
    private List<ImageResource> illustrations;
    private List<ImageResource> diagrams;
    private List<ImageResource> logos;

}
