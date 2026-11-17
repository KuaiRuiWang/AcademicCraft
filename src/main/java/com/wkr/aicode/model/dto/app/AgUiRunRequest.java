package com.wkr.aicode.model.dto.app;

import lombok.Data;

import java.util.List;

/**
 * AG-UI 文本对话输入；模型、工具与历史上下文仍由服务端管理。
 */
@Data
public class AgUiRunRequest {

    private String threadId;

    private String runId;

    private List<RunMessage> messages;

    @Data
    public static class RunMessage {
        private String role;
        private String content;
    }
}
