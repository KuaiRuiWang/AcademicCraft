package com.wkr.aicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import com.wkr.aicode.mapper.ProjectOrderMapper;
import com.wkr.aicode.model.entity.Project;
import com.wkr.aicode.model.entity.ProjectOrder;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.model.vo.projectorder.ProjectOrderVO;
import com.wkr.aicode.service.ProjectOrderService;
import com.wkr.aicode.service.ProjectService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 购买记录 服务层实现。
 *
 * @author <a>百夜</a>
 */
@Service
@Slf4j
public class ProjectOrderServiceImpl extends ServiceImpl<ProjectOrderMapper, ProjectOrder> implements ProjectOrderService {

    @Resource
    private ProjectService projectService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(Long projectId, User buyer) {
        ThrowUtils.throwIf(projectId == null || projectId <= 0, ErrorCode.PARAMS_ERROR, "项目ID无效");

        // 查询项目
        Project project = projectService.getById(projectId);
        ThrowUtils.throwIf(project == null, ErrorCode.NOT_FOUND_ERROR, "项目不存在");
        ThrowUtils.throwIf(project.getStatus() != 1, ErrorCode.OPERATION_ERROR, "该项目暂不可购买");

        // 不能购买自己的项目
        if (project.getAuthorId().equals(buyer.getId())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "不能购买自己的项目");
        }

        // 检查是否已购买过（防重）
        QueryWrapper checkWrapper = QueryWrapper.create()
                .eq("projectId", projectId)
                .eq("buyerId", buyer.getId())
                .eq("status", 1);
        long count = this.count(checkWrapper);
        ThrowUtils.throwIf(count > 0, ErrorCode.OPERATION_ERROR, "您已购买过该项目，请勿重复购买");

        // 创建订单（当前无支付，直接已支付）
        ProjectOrder order = ProjectOrder.builder()
                .projectId(projectId)
                .buyerId(buyer.getId())
                .sellerId(project.getAuthorId())
                .amount(project.getPrice())
                .payTime(LocalDateTime.now())
                .build();

        // 免费项目或暂未对接支付，直接完成
        if (project.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            order.setStatus(1);
        } else {
            order.setStatus(1); // 支付暂未对接，直接标记已支付
        }
        if(order.getPayTime() == null) {
            order.setPayTime(LocalDateTime.now());
        }

        boolean result = this.save(order);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建订单失败");

        // 更新项目销量
        Project updateProject = new Project();
        updateProject.setId(projectId);
        updateProject.setSalesCount(project.getSalesCount() + 1);
        projectService.updateById(updateProject);

        log.info("订单创建成功，订单ID: {}, 买家: {}, 项目: {}", order.getId(), buyer.getId(), projectId);
        return order.getId();
    }

    @Override
    public ProjectOrderVO getOrderVO(ProjectOrder order) {
        if (order == null) {
            return null;
        }
        ProjectOrderVO vo = new ProjectOrderVO();
        BeanUtil.copyProperties(order, vo);

        // 关联项目信息
        Project project = projectService.getById(order.getProjectId());
        if (project != null) {
            vo.setProjectName(project.getName());
            vo.setProjectCover(project.getCoverImage());
            vo.setSourceType(project.getSourceType());
            vo.setSourceUrl(project.getSourceUrl());
        }

        return vo;
    }

    @Override
    public List<ProjectOrderVO> getOrderVOList(List<ProjectOrder> orderList) {
        if (CollUtil.isEmpty(orderList)) {
            return new ArrayList<>();
        }

        // 批量获取项目信息
        List<Long> projectIds = orderList.stream()
                .map(ProjectOrder::getProjectId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Project> projectMap = projectService.listByIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));

        return orderList.stream().map(order -> {
            ProjectOrderVO vo = new ProjectOrderVO();
            BeanUtil.copyProperties(order, vo);
            Project project = projectMap.get(order.getProjectId());
            if (project != null) {
                vo.setProjectName(project.getName());
                vo.setProjectCover(project.getCoverImage());
                vo.setSourceType(project.getSourceType());
                vo.setSourceUrl(project.getSourceUrl());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(Long buyerId) {
        return QueryWrapper.create()
                .eq("buyerId", buyerId)
                .orderBy("createTime", false);
    }
}
