package com.wkr.aicode.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@Table("platform_admin")
public class PlatformAdmin {
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;
    private String username;
    @Column("displayName")
    private String displayName;
    @JsonIgnore
    @ToString.Exclude
    @Column("passwordHash")
    private String passwordHash;
    @Column("roleId")
    private Long roleId;
    private Boolean enabled;
    @JsonIgnore
    @Column("sessionVersion")
    private Integer sessionVersion;
    @Column("createTime")
    private LocalDateTime createTime;
    @Column("updateTime")
    private LocalDateTime updateTime;
}
