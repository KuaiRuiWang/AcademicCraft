package com.wkr.aicode.model.dto.app;

import lombok.Data;

@Data
public class ThesisSelectionRewriteRequest {
    private String revision;
    private String selectedText;
    private String instruction;
    private String beforeText;
    private String afterText;
}
