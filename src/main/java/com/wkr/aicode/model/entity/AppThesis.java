package com.wkr.aicode.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/** 应用对应的论文任务及冻结的项目资料。 */
@Data
@Table("app_thesis")
public class AppThesis {
    @Id
    @Column("appId")
    private Long appId;
    private String title;
    private String status;
    private String stage;
    private String revision;
    @Column("sourceVersion")
    private String sourceVersion;
    @Column("sourceContext")
    private String sourceContext;
    @Column("referenceSnapshot")
    private String referenceSnapshot;
    @Column("errorMessage")
    private String errorMessage;
    @Column("createTime")
    private LocalDateTime createTime;
    @Column("updateTime")
    private LocalDateTime updateTime;
}
