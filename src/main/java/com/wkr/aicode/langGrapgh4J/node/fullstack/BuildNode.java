package com.wkr.aicode.langGrapgh4J.node.fullstack;

import com.wkr.aicode.core.builder.FullstackProjectBuilder;
import com.wkr.aicode.langGrapgh4J.state.WorkflowContext;
import com.wkr.aicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 构建节点：契约校验通过后构建前端、复制产物到 backend/public、预构建运行镜像
 * 镜像预构建失败不阻断生成（部署阶段 compose 会重新构建）
 *
 * @author <a>百夜</a>
 */
@Slf4j
public class BuildNode {

    public static AsyncNodeAction<MessagesState<String>> create(AgentStreamPipe pipe) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            Long appId = context.getAppId();
            log.info("执行节点: 全栈项目构建，appId={}", appId);
            context.setCurrentStep("项目构建");
            FullstackProjectBuilder builder = SpringContextUtil.getBean(FullstackProjectBuilder.class);
            File projectDir = new File(context.getFullstackProjectDir());

            pipe.emitAiResponse("\n\n正在构建前端项目（npm install && npm run build）...\n");
            if (!builder.buildFrontend(projectDir)) {
                context.setErrorMessage("前端构建失败");
                pipe.emitAiResponse("前端构建失败：生成的前端代码可能存在问题，请描述现象让 AI 修复\n");
                return WorkflowContext.saveContext(context);
            }
            if (!builder.copyDistToPublic(projectDir)) {
                context.setErrorMessage("前端产物复制失败");
                pipe.emitAiResponse("前端产物复制失败，请重试或联系管理员\n");
                return WorkflowContext.saveContext(context);
            }
            context.setBuildResultDir(new File(projectDir, "backend/public").getAbsolutePath());
            pipe.emitAiResponse("前端构建完成，产物已复制到 backend/public\n");

            if (!builder.isDockerAvailable()) {
                log.warn("未检测到可用的 Docker，跳过镜像预构建，appId={}", appId);
                pipe.emitAiResponse("当前环境未安装 Docker，跳过镜像预构建（部署时自动构建）\n");
                return WorkflowContext.saveContext(context);
            }
            pipe.emitAiResponse("正在预构建运行镜像...\n");
            if (builder.buildImage(projectDir, imageTag(appId))) {
                pipe.emitAiResponse("镜像预构建完成，部署时可直接启动\n");
            } else {
                log.error("镜像预构建失败，appId={}", appId);
                pipe.emitAiResponse("镜像预构建失败，部署时将重新构建\n");
            }
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 生成阶段的镜像名（此时尚无 deployKey），部署阶段由 compose 按 deployKey 重建
     */
    static String imageTag(Long appId) {
        return "fullstack-" + appId;
    }
}
