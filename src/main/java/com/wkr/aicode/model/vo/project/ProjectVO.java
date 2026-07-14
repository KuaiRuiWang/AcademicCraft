package com.wkr.aicode.model.vo.project;

import com.wkr.aicode.model.vo.user.UserVO;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目 VO
 *
 * @author <a>百夜</a>
 */
@Data
public class ProjectVO implements Serializable {

    /**
     * id
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
     * 分类名称
     */
    private String categoryName;

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

    /**
     * 在线演示地址
     */
    private String demoUrl;

    /**
     * 卖家用户ID
     */
    private Long authorId;

    /**
     * 状态：0-草稿 1-已发布 2-已下架
     */
    private Integer status;

    /**
     * 销量
     */
    private Integer salesCount;

    /**
     * 浏览量
     */
    private Integer viewCount;

    /**
     * 平均评分
     */
    private BigDecimal avgRating;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 作者信息
     */
    private UserVO author;

    /**
     * 项目图片列表
     */
    private List<ProjectImageVO> images;

    private static final long serialVersionUID = 1L;

    /**
     * 项目图片 VO
     */
    @Data
    public static class ProjectImageVO implements Serializable {
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
