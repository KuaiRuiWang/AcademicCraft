package com.wkr.aicode.model.vo.projectorder;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单 VO
 *
 * @author <a>百夜</a>
 */
@Data
public class ProjectOrderVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 项目封面
     */
    private String projectCover;

    /**
     * 购买者ID
     */
    private Long buyerId;

    /**
     * 卖家ID
     */
    private Long sellerId;

    /**
     * 金额
     */
    private BigDecimal amount;

    /**
     * 状态：0-待支付 1-已支付 2-已退款
     */
    private Integer status;

    /**
     * 交付方式
     */
    private Integer sourceType;

    /**
     * 当前是否允许下载
     */
    private Boolean downloadAvailable;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
