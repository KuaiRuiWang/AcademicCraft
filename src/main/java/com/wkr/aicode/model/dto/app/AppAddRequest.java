package com.wkr.aicode.model.dto.app;

import lombok.Data;

import java.io.Serializable;

@Data
public class AppAddRequest implements Serializable {

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

    /**
     * 是否同步生成 Node.js 后端（全栈项目）
     */
    private Boolean generateBackend;

    private static final long serialVersionUID = 1L;
}
