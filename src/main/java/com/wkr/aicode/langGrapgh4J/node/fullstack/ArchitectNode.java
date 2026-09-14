package com.wkr.aicode.langGrapgh4J.node.fullstack;

import com.wkr.aicode.ai.model.ApiContract;
import com.wkr.aicode.constant.AppConstant;
import com.wkr.aicode.core.ApiContractDesignFacade;
import com.wkr.aicode.langGrapgh4J.state.WorkflowContext;
import com.wkr.aicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 架构师节点：设计前后端共用的 API 契约，产出 api-contract.json
 * 该节点位于并行分支之前，产物目录也在此确定，避免并行节点写上下文
 *
 * @author <a>百夜</a>
 */
@Slf4j
public class ArchitectNode {

    public static AsyncNodeAction<MessagesState<String>> create(AgentStreamPipe pipe) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            Long appId = context.getAppId();
            log.info("执行节点: 架构师（API 契约设计），appId={}", appId);
            context.setCurrentStep("API 契约设计");
            // 产物目录在并行之前确定，供后续构建、部署节点使用
            String projectDir = AppConstant.fullstackProjectDir(appId);
            context.setFullstackProjectDir(projectDir);
            context.setFrontendDir(projectDir + "/frontend");
            context.setBackendDir(projectDir + "/backend");
            pipe.emitAiResponse("正在设计 API 契约...\n");
            ApiContractDesignFacade contractDesignFacade = SpringContextUtil.getBean(ApiContractDesignFacade.class);
            ApiContract contract = contractDesignFacade.designAndSaveContract(context.getOriginalPrompt(), projectDir);
            context.setApiContract(contract);
            String summary = String.format("API 契约设计完成：%d 张数据表，%d 个接口\n",
                    contract.getModels().size(), contract.getApis().size());
            log.info(summary.trim());
            pipe.emitAiResponse(summary);
            return WorkflowContext.saveContext(context);
        });
    }
}
