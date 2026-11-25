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
    private String literatureStatus;
    private List<ReferenceCandidate> referenceCandidates = List.of();
    private List<Reference> references = List.of();

    public record ReferenceCandidate(String id, String title, int year, String url) { }

    public record Reference(String id, int number, String text, String url) { }
}
