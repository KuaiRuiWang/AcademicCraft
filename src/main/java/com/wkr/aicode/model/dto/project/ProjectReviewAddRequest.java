package com.wkr.aicode.model.dto.project;

import lombok.Data;

import java.io.Serializable;

/**
 * 项目评价请求
 *
 * @author <a>百夜</a>
 */
@Data
public class ProjectReviewAddRequest implements Serializable {

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 关联订单ID
     */
    private Long orderId;

    /**
     * 评分：1-5
     */
    private Integer rating;

    /**
     * 评价内容
     */
    private String content;

    private static final long serialVersionUID = 1L;
}
