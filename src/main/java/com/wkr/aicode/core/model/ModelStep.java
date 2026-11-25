package com.wkr.aicode.core.model;

import com.wkr.aicode.model.enums.AgentRoleEnum;
import com.wkr.aicode.model.enums.CodeGenTypeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ModelStep {
    APP_TYPE_ROUTING("应用路由", "新应用类型判断", Defaults.ROUTING),
    WORKFLOW_TYPE_ROUTING("应用路由", "工作流类型判断", Defaults.ROUTING),
    MODIFY_INTENT_ROUTING("应用路由", "全栈修改范围判断", Defaults.ROUTING),
    HTML_GENERATION("代码生成", "单页 HTML 生成", Defaults.STREAMING),
    MULTI_FILE_GENERATION("代码生成", "多文件网站生成", Defaults.STREAMING),
    VUE_GENERATION("代码生成", "Vue 应用生成", Defaults.REASONING),
    HTML_MODIFICATION("代码生成", "单页 HTML 修改", Defaults.STREAMING),
    MULTI_FILE_MODIFICATION("代码生成", "多文件网站修改", Defaults.STREAMING),
    VUE_MODIFICATION("代码生成", "Vue 应用修改", Defaults.REASONING),
    HTML_REPAIR("代码生成", "单页 HTML 修复", Defaults.STREAMING),
    MULTI_FILE_REPAIR("代码生成", "多文件网站修复", Defaults.STREAMING),
    VUE_REPAIR("代码生成", "Vue 应用修复", Defaults.REASONING),
    CONTRACT_DESIGN("全栈应用", "接口契约设计", Defaults.CHAT),
    FRONTEND_GENERATION("全栈应用", "前端生成", Defaults.REASONING),
    BACKEND_GENERATION("全栈应用", "后端生成", Defaults.REASONING),
    FRONTEND_MODIFICATION("全栈应用", "前端修改", Defaults.REASONING),
    BACKEND_MODIFICATION("全栈应用", "后端修改", Defaults.REASONING),
    FRONTEND_REPAIR("全栈应用", "前端修复", Defaults.REASONING),
    BACKEND_REPAIR("全栈应用", "后端修复", Defaults.REASONING),
    QUALITY_REVIEW("工作流辅助", "代码质量检查", Defaults.CHAT),
    IMAGE_PLAN("工作流辅助", "图片资源规划", Defaults.CHAT),
    IMAGE_COLLECTION("工作流辅助", "图片资源收集", Defaults.CHAT),
    LOGO_IMAGE("工作流辅助", "Logo 图片生成", "dashscope.image-model", ModelProtocol.DASHSCOPE_IMAGE),
    THESIS_CHAPTER("论文", "章节正文", Defaults.STREAMING),
    THESIS_DIAGRAM("论文", "配图绘制", Defaults.STREAMING),
    THESIS_REWRITE("论文", "AI 局部修改", Defaults.STREAMING);

    private final String group;
    private final String label;
    private final String defaultProperty;
    private final ModelProtocol protocol;

    ModelStep(String group, String label, String defaultProperty) {
        this(group, label, defaultProperty, ModelProtocol.OPENAI_COMPATIBLE);
    }

    public enum CodePhase { GENERATE, MODIFY, REPAIR }

    public static ModelStep code(CodeGenTypeEnum type, CodePhase phase) {
        return switch (type) {
            case HTML -> switch (phase) {
                case GENERATE -> HTML_GENERATION;
                case MODIFY -> HTML_MODIFICATION;
                case REPAIR -> HTML_REPAIR;
            };
            case MULTI_FILE -> switch (phase) {
                case GENERATE -> MULTI_FILE_GENERATION;
                case MODIFY -> MULTI_FILE_MODIFICATION;
                case REPAIR -> MULTI_FILE_REPAIR;
            };
            case VUE_PROJECT -> switch (phase) {
                case GENERATE -> VUE_GENERATION;
                case MODIFY -> VUE_MODIFICATION;
                case REPAIR -> VUE_REPAIR;
            };
            case FULLSTACK_VUE_NODEJS -> throw new IllegalArgumentException("全栈项目按 Agent 步骤配置");
        };
    }

    public static ModelStep agent(AgentRoleEnum role, boolean modifying, boolean repairing) {
        return switch (role) {
            case FRONTEND -> repairing ? FRONTEND_REPAIR : modifying ? FRONTEND_MODIFICATION : FRONTEND_GENERATION;
            case BACKEND -> repairing ? BACKEND_REPAIR : modifying ? BACKEND_MODIFICATION : BACKEND_GENERATION;
            case ARCHITECT -> CONTRACT_DESIGN;
        };
    }

    private static final class Defaults {
        static final String ROUTING = "langchain4j.open-ai.routing-chat-model.model-name";
        static final String CHAT = "langchain4j.open-ai.chat-model.model-name";
        static final String STREAMING = "langchain4j.open-ai.streaming-chat-model.model-name";
        static final String REASONING = "langchain4j.open-ai.reasoning-streaming-chat-model.model-name";
    }
}
