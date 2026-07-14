package com.wkr.aicode.model.dto.projectorder;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建订单请求
 *
 * @author <a>百夜</a>
 */
@Data
public class ProjectOrderCreateRequest implements Serializable {

    /**
     * 项目ID
     */
    private Long projectId;

    private static final long serialVersionUID = 1L;
}
