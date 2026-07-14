package com.wkr.aicode.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目图片 实体类。
 *
 * @author <a>百夜</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("project_image")
public class ProjectImage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 项目ID
     */
    @Column("projectId")
    private Long projectId;

    /**
     * 图片URL
     */
    @Column("imageUrl")
    private String imageUrl;

    /**
     * 排序
     */
    @Column("sortOrder")
    private Integer sortOrder;

    /**
     * 类型：0-截图预览 1-详情介绍图
     */
    private Integer type;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 是否删除
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
