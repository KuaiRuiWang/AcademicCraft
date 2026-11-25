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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

        // 锁定模板，确保重复领取和同时下架都以当前状态为准。
        Project project = projectService.getOne(QueryWrapper.create().eq("id", projectId).forUpdate());
        ThrowUtils.throwIf(!projectService.isPublicTemplate(project), ErrorCode.NOT_FOUND_ERROR, "模板暂未开放");

        // 作者已有源码，无需领取自己的模板。
        if (Objects.equals(project.getAuthorId(), buyer.getId())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "不能领取自己的模板");
        }

        // 重试返回原领取记录，兼容旧订单。
        QueryWrapper checkWrapper = QueryWrapper.create()
                .eq("projectId", projectId)
                .eq("buyerId", buyer.getId())
                .eq("status", 1)
                .orderBy("id", true)
                .limit(1);
        ProjectOrder existingOrder = this.getOne(checkWrapper);
        if (existingOrder != null) {
            return existingOrder.getId();
        }

        // 免费领取直接完成，复用历史记录和下载权限。
        ProjectOrder order = ProjectOrder.builder()
                .projectId(projectId)
                .buyerId(buyer.getId())
                .sellerId(project.getAuthorId())
                .amount(BigDecimal.ZERO)
                .status(1)
                .build();

        boolean result = this.save(order);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "领取模板失败");

        // 更新领取次数
        Project updateProject = new Project();
        updateProject.setId(projectId);
        updateProject.setSalesCount(project.getSalesCount() + 1);
        ThrowUtils.throwIf(!projectService.updateById(updateProject), ErrorCode.OPERATION_ERROR, "更新领取次数失败");

        log.info("模板领取成功，记录ID: {}, 用户: {}, 模板: {}", order.getId(), buyer.getId(), projectId);
        return order.getId();
    }

    @Override
    public ProjectOrderVO getOrderVO(ProjectOrder order) {
        if (order == null) {
            return null;
        }
        return toOrderVO(order, projectService.getById(order.getProjectId()));
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

        return orderList.stream()
                .map(order -> toOrderVO(order, projectMap.get(order.getProjectId())))
                .collect(Collectors.toList());
    }

    private ProjectOrderVO toOrderVO(ProjectOrder order, Project project) {
        ProjectOrderVO vo = new ProjectOrderVO();
        BeanUtil.copyProperties(order, vo);
        if (project != null) {
            vo.setProjectName(project.getName());
            vo.setProjectCover(project.getCoverImage());
            vo.setSourceType(project.getSourceType());
        }
        vo.setDownloadAvailable(Integer.valueOf(1).equals(order.getStatus()) && projectService.isPublicTemplate(project));
        return vo;
    }

    @Override
    public QueryWrapper getQueryWrapper(Long buyerId) {
        return QueryWrapper.create()
                .eq("buyerId", buyerId)
                .orderBy("createTime", false);
    }
}
