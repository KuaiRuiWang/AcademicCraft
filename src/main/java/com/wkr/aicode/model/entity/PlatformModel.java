package com.wkr.aicode.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Table("platform_model")
public class PlatformModel {
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;
    private String name;
    private String protocol;
    @Column("baseUrl")
    private String baseUrl;
    @Column("modelId")
    private String modelId;
    @JsonIgnore
    @ToString.Exclude
    @Column("apiKeyCiphertext")
    private String apiKeyCiphertext;
    @Column("maxTokens")
    private Integer maxTokens;
    private BigDecimal temperature;
    @Column("createTime")
    private LocalDateTime createTime;
    @Column("updateTime")
    private LocalDateTime updateTime;
}
