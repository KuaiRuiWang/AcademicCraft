package com.wkr.aicode.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 架构师 Agent：API 契约设计服务
 * 依据用户需求产出前后端共用的契约（纯 JSON），由 {@link com.wkr.aicode.core.ApiContractDesignFacade} 负责解析与校验
 *
 * @author <a>百夜</a>
 */
public interface ApiContractDesignService {

    /**
     * 设计 API 契约
     *
     * @param requirement 用户需求描述
     * @return 契约 JSON 文本
     */
    @SystemMessage(fromResource = "prompt/api-contract-design-system-prompt.txt")
    String designApiContract(@UserMessage String requirement);
}
