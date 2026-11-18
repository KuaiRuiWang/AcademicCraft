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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目商品 实体类。
 *
 * @author <a>百夜</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("project")
public class Project implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 项目名称
     */
    private String name;

    /**
     * 分类ID
     */
    @Column("categoryId")
    private Long categoryId;

    /**
     * 自定义技术标签（JSON数组）
     */
    @Column("techTags")
    private String techTags;

    /**
     * 项目简介
     */
    private String description;

    /**
     * 项目详情（富文本HTML）
     */
    @Column("detailContent")
    private String detailContent;

    /**
     * 封面图URL
     */
    @Column("coverImage")
    private String coverImage;

    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * 交付方式：1-ZIP下载 2-网盘链接 3-两者都有
     */
    @Column("sourceType")
    private Integer sourceType;

    /**
     * 源码地址
     */
    @Column("sourceUrl")
    private String sourceUrl;

    /** 模板来源或授权方 */
    @Column("sourceAttribution")
    private String sourceAttribution;

    /** 许可名称、商用和再分发条件 */
    @Column("licenseTerms")
    private String licenseTerms;

    /**
     * 在线演示地址
     */
    @Column("demoUrl")
    private String demoUrl;

    /**
     * 卖家用户ID
     */
    @Column("authorId")
    private Long authorId;

    /**
     * 状态：0-草稿 1-已发布 2-已下架
     */
    private Integer status;

    /**
     * AI审核原因（字段保留）
     */
    @Column("auditReason")
    private String auditReason;

    /**
     * 销量
     */
    @Column("salesCount")
    private Integer salesCount;

    /**
     * 浏览量
     */
    @Column("viewCount")
    private Integer viewCount;

    /**
     * 平均评分
     */
    @Column("avgRating")
    private BigDecimal avgRating;

    /**
     * 编辑时间
     */
    @Column("editTime")
    private LocalDateTime editTime;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
