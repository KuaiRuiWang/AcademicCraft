package com.wkr.aicode.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.wkr.aicode.model.entity.ProjectOrder;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.model.vo.projectorder.ProjectOrderVO;

import java.util.List;

/**
 * 购买记录 服务层。
 *
 * @author <a>百夜</a>
 */
public interface ProjectOrderService extends IService<ProjectOrder> {

    /**
     * 创建订单（购买项目）
     */
    Long createOrder(Long projectId, User buyer);

    /**
     * 获取订单VO
     */
    ProjectOrderVO getOrderVO(ProjectOrder order);

    /**
     * 批量获取订单VO列表
     */
    List<ProjectOrderVO> getOrderVOList(List<ProjectOrder> orderList);

    /**
     * 构建查询条件
     */
    QueryWrapper getQueryWrapper(Long buyerId);
}
