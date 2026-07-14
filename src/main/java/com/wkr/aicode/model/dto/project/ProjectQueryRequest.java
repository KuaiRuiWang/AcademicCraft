package com.wkr.aicode.model.dto.project;

import com.wkr.aicode.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 项目查询请求
 *
 * @author <a>百夜</a>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ProjectQueryRequest extends PageRequest implements Serializable {

    /**
     * 项目ID
     */
    private Long id;

    /**
     * 项目名称（模糊搜索）
     */
    private String name;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 自定义标签关键字
     */
    private String techTag;

    /**
     * 状态：0-草稿 1-已发布 2-已下架
     */
    private Integer status;

    /**
     * 卖家用户ID
     */
    private Long authorId;

    /**
     * 最低价格
     */
    private BigDecimal minPrice;

    /**
     * 最高价格
     */
    private BigDecimal maxPrice;

    private static final long serialVersionUID = 1L;
}
