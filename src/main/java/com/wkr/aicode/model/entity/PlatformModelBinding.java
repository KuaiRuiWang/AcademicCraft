package com.wkr.aicode.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.Data;

@Data
@Table("platform_model_binding")
public class PlatformModelBinding {
    @Id
    @Column("stepKey")
    private String stepKey;
    @Column("modelConfigId")
    private Long modelConfigId;
}
