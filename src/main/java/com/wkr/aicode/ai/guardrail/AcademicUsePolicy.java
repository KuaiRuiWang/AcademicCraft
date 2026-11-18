package com.wkr.aicode.ai.guardrail;

import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 校验明确要求代做或直接提交的考核成果。
 */
public final class AcademicUsePolicy {

    private static final String ASSESSED_WORK =
            "(?:毕设|毕业设计|毕业论文|毕业答辩|学位论文|课程(?:设计|大?作业|考核)|期末(?:大?作业|项目|考核))"
                    + "(?!\\s*(?:选题|过程|项目|评审|答辩|审核|预约|登记|提交)?(?:管理|预约|排期|登记|评审|展示)?(?:系统|平台|工具|网站|工作台))";
    private static final String GENERATE_WORK =
            "(?:帮我|替我|给我|请|直接|一键)\\s*(?:直接|从零|一次性)?(?:做|写|生成|完成|输出|实现|提供)";
    private static final Pattern ASSESSMENT = Pattern.compile(ASSESSED_WORK);
    private static final Pattern OUTSOURCE = Pattern.compile(
            "(?:^|[，。！？,!?\\n]|帮我|替我|给我|求)\\s*(?:代做|代写)|包过|包答辩|冒充(?:本人|我的)?原创");
    private static final Pattern GENERATION_REQUEST = Pattern.compile(GENERATE_WORK);
    private static final Pattern READY_TO_SUBMIT = Pattern.compile(
            "(?:直接|拿去|即可)(?:交作业|交差|交给老师|(?:用来|用于)?答辩)|拿来交|原样提交"
                    + "|(?:直接|即可|拿去)提交(?=\\s*(?:的|[，。！？,!?]|$))");
    private static final Pattern FULL_DELIVERY = Pattern.compile(
            "一键生成|" + GENERATE_WORK
                    + "[^。！？!?\\n]{0,16}(?:整套|全套|整篇|全文|完整(?:的)?(?:项目|源码|系统|论文|报告|前后端))");
    private static final Pattern REQUEST_ASSESSED_WORK = Pattern.compile(
            GENERATE_WORK + "\\s*(?:一个|一套|一份|一篇|整套)?(?:我的)?(?:本科|硕士|博士)?"
                    + ASSESSED_WORK + "(?!\\s*的)(?!\\s*(?:提纲|大纲|格式|引用|练习|思路|示例))");

    private static final String PRACTICE_GUIDANCE =
            "这个需求涉及直接提交考核成果。请先按学校要求自行实现核心功能。"
                    + "可以选一个模块，列出输入、输出和验收条件，或贴出你已编写的代码和报错，"
                    + "我可以帮你拆解实现步骤、设计测试用例或一起排查问题。";

    private AcademicUsePolicy() {
    }

    public static void validate(String prompt, String initialPrompt) {
        String message = Objects.toString(prompt, "").strip();
        String appContext = Objects.toString(initialPrompt, "");

        // shortcut: 只判断明确表达的交付意图，学校允许的辅导用途由学生按校规确认。
        boolean assessment = ASSESSMENT.matcher(message).find() || ASSESSMENT.matcher(appContext).find();
        boolean readyToSubmit = READY_TO_SUBMIT.matcher(message).find() && GENERATION_REQUEST.matcher(message).find();
        boolean requestsSubmission = assessment && (OUTSOURCE.matcher(message).find()
                || readyToSubmit || FULL_DELIVERY.matcher(message).find()
                || REQUEST_ASSESSED_WORK.matcher(message).find());
        ThrowUtils.throwIf(requestsSubmission, ErrorCode.PARAMS_ERROR, PRACTICE_GUIDANCE);
    }
}
