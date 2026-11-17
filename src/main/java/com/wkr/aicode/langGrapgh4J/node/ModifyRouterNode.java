package com.wkr.aicode.langGrapgh4J.node;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.wkr.aicode.ai.AiModifyIntentRoutingService;
import com.wkr.aicode.ai.AiModifyIntentRoutingServiceFactory;
import com.wkr.aicode.ai.model.ApiContract;
import com.wkr.aicode.constant.AppConstant;
import com.wkr.aicode.core.ApiContractDesignFacade;
import com.wkr.aicode.langGrapgh4J.node.fullstack.AgentStreamPipe;
import com.wkr.aicode.langGrapgh4J.state.WorkflowContext;
import com.wkr.aicode.model.enums.ModifyScopeEnum;
import com.wkr.aicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 修改意图路由节点（T-17）：用轻量模型把修改需求分类为 FRONTEND / BACKEND / BOTH，
 * 并从已生成产物中加载 API 契约供后续 Agent 节点使用
 *
 * @author <a>百夜</a>
 */
@Slf4j
public class ModifyRouterNode {

    public static AsyncNodeAction<MessagesState<String>> create(AgentStreamPipe pipe) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            Long appId = context.getAppId();
            log.info("执行节点: 修改意图路由，appId={}", appId);
            context.setCurrentStep("修改意图路由");
            pipe.stepStarted("modify_router", "分析修改范围");
            // 已有项目的产物目录与生成流程一致，在此确定供 Agent 节点使用
            String projectDir = AppConstant.fullstackProjectDir(appId);
            context.setFullstackProjectDir(projectDir);
            context.setFrontendDir(projectDir + "/frontend");
            context.setBackendDir(projectDir + "/backend");
            context.setApiContract(loadContract(projectDir));
            pipe.emitAiResponse("正在分析修改需求范围...\n");
            ModifyScopeEnum modifyScope = classifyModifyScope(context.getOriginalPrompt());
            context.setModifyScope(modifyScope);
            String summary = "修改范围：" + modifyScope.getText() + "\n";
            log.info(summary.trim());
            pipe.emitAiResponse(summary);
            pipe.stepFinished("modify_router", "修改范围：" + modifyScope.getText(), "success");
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 轻量模型分类修改范围；调用失败或结果为空时按 BOTH 处理（宁可多改不可漏改）
     */
    static ModifyScopeEnum classifyModifyScope(String originalPrompt) {
        try {
            AiModifyIntentRoutingServiceFactory factory =
                    SpringContextUtil.getBean(AiModifyIntentRoutingServiceFactory.class);
            AiModifyIntentRoutingService routingService = factory.createAiModifyIntentRoutingService();
            ModifyScopeEnum modifyScope = routingService.routeModifyScope(originalPrompt);
            if (modifyScope != null) {
                return modifyScope;
            }
            log.warn("修改意图路由结果为空，按 BOTH 处理");
        } catch (Exception e) {
            log.error("修改意图路由失败，按 BOTH 处理: {}", e.getMessage());
        }
        return ModifyScopeEnum.BOTH;
    }

    /**
     * 从已生成产物根目录加载 api-contract.json；缺失或损坏时返回 null（Agent 按现有代码修改）
     */
    static ApiContract loadContract(String projectDir) {
        File contractFile = new File(projectDir, ApiContractDesignFacade.CONTRACT_FILE_NAME);
        if (!contractFile.isFile()) {
            log.warn("未找到 API 契约文件：{}", contractFile.getAbsolutePath());
            return null;
        }
        try {
            String json = FileUtil.readString(contractFile, StandardCharsets.UTF_8);
            if (StrUtil.isBlank(json)) {
                return null;
            }
            return JSONUtil.toBean(json, ApiContract.class);
        } catch (Exception e) {
            log.warn("API 契约文件解析失败：{}, 错误: {}", contractFile.getAbsolutePath(), e.getMessage());
            return null;
        }
    }
}
