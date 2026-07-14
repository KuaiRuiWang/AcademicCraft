package com.wkr.aicode.model.vo.project;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目分类 VO
 *
 * @author <a>百夜</a>
 */
@Data
public class ProjectCategoryVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 分类图标URL
     */
    private String icon;

    /**
     * 排序权重
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
