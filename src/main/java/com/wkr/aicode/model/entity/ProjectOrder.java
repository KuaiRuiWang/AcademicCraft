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
 * 购买记录 实体类。
 *
 * @author <a>百夜</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("project_order")
public class ProjectOrder implements Serializable {

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
     * 购买者ID
     */
    @Column("buyerId")
    private Long buyerId;

    /**
     * 卖家ID
     */
    @Column("sellerId")
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
     * 支付时间
     */
    @Column("payTime")
    private LocalDateTime payTime;

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
