package com.wkr.aicode.model.dto.app;

import lombok.Data;

@Data
public class ThesisChapterUpdateRequest {
    private String content;
    /** 编辑时读到的正文，避免另一个窗口的更新被覆盖。 */
    private String originalContent;
    private String revision;
}
