package com.wkr.aicode.langGrapgh4J.node.fullstack;

import com.wkr.aicode.ai.model.ApiContract;
import com.wkr.aicode.langGrapgh4J.model.QualityResult;
import com.wkr.aicode.langGrapgh4J.state.WorkflowContext;
import com.wkr.aicode.langGrapgh4J.utils.ApiCallExtractor;
import com.wkr.aicode.langGrapgh4J.utils.ApiPathUtil;
import com.wkr.aicode.langGrapgh4J.utils.RouteExtractor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 契约校验节点：三方比对 api-contract.json、后端路由与前端调用
 * 校验不通过时把问题写入上下文，由条件边退回对应 Agent 修复，回环上限 2 次防死循环
 *
 * @author <a>百夜</a>
 */
@Slf4j
public class ContractCheckNode {

    /**
     * 最大修复轮次（退回 Agent 的次数上限）
     */
    public static final int MAX_FIX_ROUNDS = 2;

    public static AsyncNodeAction<MessagesState<String>> create(AgentStreamPipe pipe) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            int round = context.getContractCheckRound() + 1;
            context.setContractCheckRound(round);
            context.setCurrentStep("契约校验");
            log.info("执行节点: 契约一致性校验，appId={}，第 {} 轮", context.getAppId(), round);
            pipe.emitAiResponse("正在校验前后端与 API 契约的一致性...\n");

            ApiContract contract = context.getApiContract();
            Set<String> backendRoutes = RouteExtractor.extractRoutes(toPath(context.getBackendDir()));
            Set<String> frontendCalls = ApiCallExtractor.extractApiCalls(toPath(context.getFrontendDir()));
            ContractDiff diff = compare(contract, backendRoutes, frontendCalls);

            boolean valid = diff.backendErrors().isEmpty() && diff.frontendErrors().isEmpty();
            List<String> allErrors = new ArrayList<>();
            allErrors.addAll(diff.backendErrors());
            allErrors.addAll(diff.frontendErrors());
            QualityResult qualityResult = QualityResult.builder()
                    .isValid(valid)
                    .errors(allErrors)
                    .suggestions(diff.backendOnlyRoutes())
                    .build();
            context.setContractCheckResult(qualityResult);
            context.setBackendFixHints(diff.backendErrors());
            context.setFrontendFixHints(diff.frontendErrors());

            if (valid) {
                log.info("契约校验通过，第 {} 轮，appId={}", round, context.getAppId());
                pipe.emitAiResponse(String.format("契约校验通过：后端实现 %d 个接口，前端调用 %d 个接口，均与契约一致\n",
                        backendRoutes.size(), frontendCalls.size()));
            } else {
                log.warn("契约校验未通过，第 {} 轮，后端问题 {} 项，前端问题 {} 项",
                        round, diff.backendErrors().size(), diff.frontendErrors().size());
                pipe.emitAiResponse(formatIssues(diff, round));
            }
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 三方比对：契约是唯一事实来源
     * - 契约中的每个接口后端都必须实现
     * - 前端只能调用契约中存在的接口路径
     */
    static ContractDiff compare(ApiContract contract, Set<String> backendRoutes, Set<String> frontendCalls) {
        List<String> backendErrors = new ArrayList<>();
        List<String> frontendErrors = new ArrayList<>();
        Set<String> contractRoutes = new LinkedHashSet<>();
        Set<String> contractPaths = new LinkedHashSet<>();
        if (contract == null || contract.getApis() == null) {
            backendErrors.add("API 契约缺失或为空，无法校验接口实现");
            return new ContractDiff(backendErrors, frontendErrors, List.of());
        }
        for (ApiContract.ApiEndpoint api : contract.getApis()) {
            if (api == null || api.getPath() == null) {
                continue;
            }
            String route = ApiPathUtil.normalizeRoute(api.getMethod(), api.getPath());
            contractRoutes.add(route);
            contractPaths.add(ApiPathUtil.normalizePath(api.getPath()));
            if (!backendRoutes.contains(route)) {
                backendErrors.add(String.format("后端缺少接口（或方法不匹配）：%s，请实现该接口", route));
            }
        }
        for (String callPath : frontendCalls) {
            if (!contractPaths.contains(callPath)) {
                frontendErrors.add(String.format("前端调用了契约外接口：%s，请改为契约中的路径或移除该调用", callPath));
            }
        }
        List<String> backendOnlyRoutes = new ArrayList<>();
        for (String route : backendRoutes) {
            if (!contractRoutes.contains(route)) {
                backendOnlyRoutes.add("后端存在契约外的接口（不阻断流程）：" + route);
            }
        }
        return new ContractDiff(backendErrors, frontendErrors, backendOnlyRoutes);
    }

    private static String formatIssues(ContractDiff diff, int round) {
        StringBuilder message = new StringBuilder("契约校验未通过：\n");
        diff.backendErrors().forEach(error -> message.append("- ").append(error).append("\n"));
        diff.frontendErrors().forEach(error -> message.append("- ").append(error).append("\n"));
        if (round > MAX_FIX_ROUNDS) {
            message.append("已达修复上限（").append(MAX_FIX_ROUNDS)
                    .append(" 轮），终止生成，请调整需求后重新生成\n");
        } else {
            message.append("将退回对应 Agent 修复（第 ").append(round).append(" 轮）\n");
        }
        return message.toString();
    }

    private static Path toPath(String dir) {
        return dir == null ? null : Path.of(dir);
    }

    /**
     * 契约校验差异
     *
     * @param backendErrors     后端缺失接口
     * @param frontendErrors    前端越界调用
     * @param backendOnlyRoutes 后端契约外接口（仅提示，不阻断）
     */
    record ContractDiff(List<String> backendErrors, List<String> frontendErrors, List<String> backendOnlyRoutes) {
    }
}
