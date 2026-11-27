package com.wkr.aicode.core.thesis;

import com.wkr.aicode.core.model.ModelStep;
import java.util.List;

public enum GraduationDocumentKind {
    assignment("毕业设计任务书", ModelStep.ASSIGNMENT_WRITE, List.of("主要任务", "技术要求", "进度安排", "成果要求")),
    proposal("毕业设计开题报告", ModelStep.PROPOSAL_WRITE, List.of("选题背景与意义", "国内外研究现状", "研究内容",
            "研究方法与技术路线", "可行性分析", "进度安排", "预期成果"));

    public final String title;
    public final ModelStep modelStep;
    public final List<String> fields;

    GraduationDocumentKind(String title, ModelStep modelStep, List<String> fields) {
        this.title = title;
        this.modelStep = modelStep;
        this.fields = fields;
    }
}
