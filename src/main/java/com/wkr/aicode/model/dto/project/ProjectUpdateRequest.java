package com.wkr.aicode.model.dto.project;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 项目更新请求
 *
 * @author <a>百夜</a>
 */
@Data
public class ProjectUpdateRequest implements Serializable {

    /**
     * 项目ID
     */
    private Long id;

    /**
     * 项目名称
     */
    private String name;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 自定义技术标签（JSON数组字符串）
     */
    private String techTags;

    /**
     * 项目简介
     */
    private String description;

    /**
     * 项目详情（富文本HTML）
     */
    private String detailContent;

    /**
     * 封面图URL
     */
    private String coverImage;

    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * 交付方式：1-ZIP下载 2-网盘链接 3-两者都有
     */
    private Integer sourceType;

    /**
     * 源码地址
     */
    private String sourceUrl;

    /** 模板来源或授权方 */
    private String sourceAttribution;

    /** 许可名称、商用和再分发条件 */
    private String licenseTerms;

    /**
     * 在线演示地址
     */
    private String demoUrl;

    /**
     * 状态：0-草稿 1-已发布 2-已下架
     */
    private Integer status;

    /**
     * 项目图片列表（全量替换）
     */
    private List<ProjectAddRequest.ProjectImageItem> images;

    private static final long serialVersionUID = 1L;
}
