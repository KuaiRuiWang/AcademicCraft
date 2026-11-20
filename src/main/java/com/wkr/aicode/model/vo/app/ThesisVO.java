package com.wkr.aicode.model.vo.app;

import com.wkr.aicode.model.entity.AppThesisChapter;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ThesisVO {
    private Long appId;
    private String title;
    private String status;
    private String stage;
    private String revision;
    private String sourceVersion;
    private String errorMessage;
    private LocalDateTime updateTime;
    private List<AppThesisChapter> chapters;
}
