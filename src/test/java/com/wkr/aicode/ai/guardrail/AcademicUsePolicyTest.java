package com.wkr.aicode.ai.guardrail;

import com.wkr.aicode.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicUsePolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "帮我代做毕业设计，要能直接交给老师",
            "帮我写一篇本科毕业论文",
            "这是期末大作业，请生成整套代码，原样提交",
            "课程设计是库存台账，帮我做完整的前后端"
    })
    void directsRequestsForSubmittedWorkToPractice(String prompt) {
        BusinessException error = assertThrows(BusinessException.class, () -> AcademicUsePolicy.validate(prompt, null));
        assertTrue(error.getMessage().contains("验收条件"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "帮我生成一个毕业设计管理平台，支持选题和评审",
            "我的毕业设计启动报错，请解释堆栈并给调试思路",
            "这是我需要交的课程设计，请帮我定位已有代码中的问题",
            "帮我做作业收集工具，学生可以直接提交课程作业",
            "不要代写毕业论文，请讲一下参考文献格式",
            "我想做一套完整的团队看板供内部试用"
    })
    void keepsDebuggingAndGeneralAppsAvailable(String prompt) {
        assertDoesNotThrow(() -> AcademicUsePolicy.validate(prompt, null));
    }

    @Test
    void usesSavedAppContextForFollowUpRequests() {
        String initialPrompt = "这是我的毕业设计，主题是小团队库存管理";
        assertThrows(BusinessException.class,
                () -> AcademicUsePolicy.validate("帮我生成完整的前后端，我要交给老师", initialPrompt));
        assertDoesNotThrow(() -> AcademicUsePolicy.validate("这段查询为什么没返回数据？", initialPrompt));
    }
}
