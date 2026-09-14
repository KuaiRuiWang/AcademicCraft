package com.wkr.aicode.ai;

import com.wkr.aicode.model.enums.ModifyScopeEnum;
import dev.langchain4j.service.SystemMessage;

/**
 * 全栈项目修改需求意图路由服务（T-17）
 * 用轻量模型对用户的修改需求分类，决定交给哪些角色的 Agent 处理
 *
 * @author <a>百夜</a>
 */
public interface AiModifyIntentRoutingService {

    /**
     * 判断修改需求涉及的范围
     *
     * @param userPrompt 用户的修改需求描述
     * @return 修改范围（frontend / backend / both）
     */
    @SystemMessage(fromResource = "prompt/modify-intent-routing-system-prompt.txt")
    ModifyScopeEnum routeModifyScope(String userPrompt);
}
