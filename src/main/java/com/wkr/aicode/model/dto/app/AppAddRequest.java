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

    /** 是否在代码生成后自动生成论文初稿 */
    private Boolean generateThesis;

    /** 论文题目，留空时使用应用名称 */
    private String thesisTitle;

    private static final long serialVersionUID = 1L;
}
