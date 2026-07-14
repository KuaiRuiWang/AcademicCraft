package com.wkr.aicode.model.dto.project;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 项目发布请求
 *
 * @author <a>百夜</a>
 */
@Data
public class ProjectAddRequest implements Serializable {

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
     * 源码地址（下载URL或网盘链接）
     */
    private String sourceUrl;

    /**
     * 在线演示地址
     */
    private String demoUrl;

    /**
     * 项目图片列表
     */
    private List<ProjectImageItem> images;

    private static final long serialVersionUID = 1L;

    /**
     * 项目图片项
     */
    @Data
    public static class ProjectImageItem implements Serializable {
        /**
         * 图片URL
         */
        private String imageUrl;

        /**
         * 排序
         */
        private Integer sortOrder;

        /**
         * 类型：0-截图预览 1-详情介绍图
         */
        private Integer type;

        private static final long serialVersionUID = 1L;
    }
}
