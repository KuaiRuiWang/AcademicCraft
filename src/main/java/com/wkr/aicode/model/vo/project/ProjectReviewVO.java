package com.wkr.aicode.model.vo.project;

import com.wkr.aicode.model.vo.user.UserVO;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目评价 VO
 *
 * @author <a>百夜</a>
 */
@Data
public class ProjectReviewVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 评价用户ID
     */
    private Long userId;

    /**
     * 评分：1-5
     */
    private Integer rating;

    /**
     * 评价内容
     */
    private String content;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 评价用户信息
     */
    private UserVO user;

    private static final long serialVersionUID = 1L;
}
