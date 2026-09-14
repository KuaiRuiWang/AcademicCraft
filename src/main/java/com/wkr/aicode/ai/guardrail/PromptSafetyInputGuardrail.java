package com.wkr.aicode.ai.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class PromptSafetyInputGuardrail implements InputGuardrail {

    /**
     * 默认输入长度上限（用户直接输入场景）
     */
    private static final int DEFAULT_MAX_LENGTH = 1000;

    private final int maxLength;

    public PromptSafetyInputGuardrail() {
        this(DEFAULT_MAX_LENGTH);
    }

    /**
     * 自定义长度上限：Agent 内部消息会拼接 API 契约等机器生成内容，长度天然超过用户输入上限
     */
    public PromptSafetyInputGuardrail(int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String input = userMessage.singleText();
        // 检查输入长度
        if (input.length() > maxLength) {
            return fatal("输入内容过长，不要超过 " + maxLength + " 字");
        }
        // 检查是否为空
        if (input.trim().isEmpty()) {
            return fatal("输入内容不能为空");
        }
        // 检查敏感词
        String lowerInput = input.toLowerCase();
        for (String sensitiveWord : InputGuardrailConstant.SENSITIVE_WORDS) {
            if (lowerInput.contains(sensitiveWord.toLowerCase())) {
                return fatal("输入包含不当内容，请修改后重试");
            }
        }
        // 检查注入攻击模式
        for (Pattern pattern : InputGuardrailConstant.INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return fatal("检测到恶意输入，请求被拒绝");
            }
        }
        return success();
    }
}
