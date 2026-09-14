package com.wkr.aicode.langGrapgh4J.node.fullstack;

import cn.hutool.json.JSONUtil;
import com.wkr.aicode.ai.model.ApiContract;
import com.wkr.aicode.core.AiCodeGeneratorFacade;
import com.wkr.aicode.langGrapgh4J.state.WorkflowContext;
import com.wkr.aicode.model.enums.AgentRoleEnum;
import com.wkr.aicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.time.Duration;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 前端 Agent 节点：按契约生成 Vue3 前端代码，产物限定在 frontend/ 目录
 *
 * @author <a>百夜</a>
 */
@Slf4j
public class FrontendAgentNode {

    public static AsyncNodeAction<MessagesState<String>> create(AgentStreamPipe pipe) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            Long appId = context.getAppId();
            String action = context.isModifyMode() ? "修改" : "生成";
            context.setCurrentStep("前端代码" + action);
            log.info("执行节点: 前端 Agent 代码{}，appId={}", action, appId);
            String userMessage = buildUserMessage(context);
            AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
            long startTime = System.currentTimeMillis();
            // 同步等待流式输出完成，边产出边推送给前端
            codeGeneratorFacade.generateFullstackAgentCodeStream(appId, AgentRoleEnum.FRONTEND, userMessage)
                    .doOnNext(pipe::emit)
                    .blockLast(Duration.ofMinutes(10));
            log.info("前端 Agent 代码{}完成，耗时 {} ms，产物目录: {}",
                    action, System.currentTimeMillis() - startTime, context.getFrontendDir());
            pipe.emitAiResponse("\n前端代码" + action + "完成\n\n");
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 构造前端 Agent 的用户消息：需求描述 + API 契约 + 路径约束（修改模式下改为增量修改指令）
     */
    private static String buildUserMessage(WorkflowContext context) {
        StringBuilder userMessage = new StringBuilder(context.getOriginalPrompt());
        ApiContract contract = context.getApiContract();
        if (contract != null) {
            userMessage.append("\n\n## API 契约（前后端唯一约定，必须严格遵守）\n```json\n")
                    .append(JSONUtil.toJsonPrettyStr(contract))
                    .append("\n```\n");
        }
        if (context.isModifyMode()) {
            userMessage.append("\n这是一次针对已有项目的增量修改，不是重新生成。")
                    .append("请先用【文件读取工具】查看 frontend/ 下的相关文件确认现状，")
                    .append("再用【文件修改工具】做最小化改动，不要重写无关文件，不要触碰 backend/ 目录。")
                    .append("所有文件路径以 frontend/ 开头，接口调用一律使用相对路径 ./api/xxx。");
        } else {
            userMessage.append("\n请严格按契约的接口清单与数据模型实现前端页面，所有文件路径以 frontend/ 开头，")
                    .append("接口调用一律使用相对路径 ./api/xxx。");
        }
        appendFixHints(userMessage, context.getFrontendFixHints());
        return userMessage.toString();
    }

    /**
     * 契约校验未通过时，把前端侧问题追加到消息中，要求用修改工具定向修复
     */
    private static void appendFixHints(StringBuilder userMessage, List<String> fixHints) {
        if (fixHints == null || fixHints.isEmpty()) {
            return;
        }
        userMessage.append("\n\n## 上一轮契约校验未通过，请修复以下问题\n");
        fixHints.forEach(hint -> userMessage.append("- ").append(hint).append("\n"));
        userMessage.append("请用【文件修改工具】修改已生成的文件，不要重新生成整个项目。");
    }
}
