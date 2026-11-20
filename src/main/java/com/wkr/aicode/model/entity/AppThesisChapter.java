package com.wkr.aicode.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

@Data
@Table("app_thesis_chapter")
public class AppThesisChapter {
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;
    @Column("appId")
    private Long appId;
    @Column("chapterNo")
    private Integer chapterNo;
    private String title;
    private String content;
    private String status;
    @Column("sourceVersion")
    private String sourceVersion;
    @Column("userEdited")
    private Integer userEdited;
    @Column("errorMessage")
    private String errorMessage;
}
