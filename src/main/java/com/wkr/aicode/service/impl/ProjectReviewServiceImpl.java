package com.wkr.aicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import com.wkr.aicode.mapper.ProjectReviewMapper;
import com.wkr.aicode.model.dto.project.ProjectReviewAddRequest;
import com.wkr.aicode.model.entity.Project;
import com.wkr.aicode.model.entity.ProjectOrder;
import com.wkr.aicode.model.entity.ProjectReview;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.model.vo.project.ProjectReviewVO;
import com.wkr.aicode.model.vo.user.UserVO;
import com.wkr.aicode.service.ProjectOrderService;
import com.wkr.aicode.service.ProjectReviewService;
import com.wkr.aicode.service.ProjectService;
import com.wkr.aicode.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目评价 服务层实现。
 *
 * @author <a>百夜</a>
 */
@Service
@Slf4j
public class ProjectReviewServiceImpl extends ServiceImpl<ProjectReviewMapper, ProjectReview> implements ProjectReviewService {

    @Resource
    private ProjectService projectService;

    @Resource
    private ProjectOrderService projectOrderService;

    @Resource
    private UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addReview(ProjectReviewAddRequest addRequest, User loginUser) {
        ThrowUtils.throwIf(addRequest.getProjectId() == null || addRequest.getProjectId() <= 0,
                ErrorCode.PARAMS_ERROR, "项目ID无效");
        ThrowUtils.throwIf(addRequest.getOrderId() == null || addRequest.getOrderId() <= 0,
                ErrorCode.PARAMS_ERROR, "订单ID无效");
        ThrowUtils.throwIf(addRequest.getRating() == null || addRequest.getRating() < 1 || addRequest.getRating() > 5,
                ErrorCode.PARAMS_ERROR, "评分须在1-5之间");

        // 验证订单是否属于该用户
        ProjectOrder order = projectOrderService.getById(addRequest.getOrderId());
        ThrowUtils.throwIf(order == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        ThrowUtils.throwIf(!order.getBuyerId().equals(loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "无权评价该订单");
        ThrowUtils.throwIf(order.getStatus() != 1,
                ErrorCode.OPERATION_ERROR, "订单未支付，无法评价");
        ThrowUtils.throwIf(!order.getProjectId().equals(addRequest.getProjectId()),
                ErrorCode.PARAMS_ERROR, "订单与项目不匹配");

        // 检查是否已评价过该订单
        QueryWrapper checkWrapper = QueryWrapper.create()
                .eq("orderId", addRequest.getOrderId());
        long count = this.count(checkWrapper);
        ThrowUtils.throwIf(count > 0, ErrorCode.OPERATION_ERROR, "您已评价过该订单");

        // 保存评价
        ProjectReview review = new ProjectReview();
        review.setProjectId(addRequest.getProjectId());
        review.setUserId(loginUser.getId());
        review.setOrderId(addRequest.getOrderId());
        review.setRating(addRequest.getRating());
        review.setContent(addRequest.getContent());
        boolean result = this.save(review);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "评价失败");

        // 更新项目平均评分
        updateProjectAvgRating(addRequest.getProjectId());

        log.info("评价成功，ID: {}, 用户: {}, 项目: {}", review.getId(), loginUser.getId(), addRequest.getProjectId());
        return review.getId();
    }

    /**
     * 更新项目平均评分
     */
    private void updateProjectAvgRating(Long projectId) {
        QueryWrapper wrapper = QueryWrapper.create().eq("projectId", projectId);
        List<ProjectReview> reviews = this.list(wrapper);
        if (CollUtil.isNotEmpty(reviews)) {
            double avg = reviews.stream()
                    .mapToInt(ProjectReview::getRating)
                    .average()
                    .orElse(0.0);
            BigDecimal avgRating = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP);
            Project updateProject = new Project();
            updateProject.setId(projectId);
            updateProject.setAvgRating(avgRating);
            projectService.updateById(updateProject);
        }
    }

    @Override
    public List<ProjectReviewVO> getReviewVOList(Long projectId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("projectId", projectId)
                .orderBy("createTime", false);
        List<ProjectReview> reviewList = this.list(wrapper);
        if (CollUtil.isEmpty(reviewList)) {
            return new ArrayList<>();
        }

        // 批量获取用户信息
        Set<Long> userIds = reviewList.stream()
                .map(ProjectReview::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));

        return reviewList.stream().map(review -> {
            ProjectReviewVO vo = new ProjectReviewVO();
            BeanUtil.copyProperties(review, vo);
            vo.setUser(userVOMap.get(review.getUserId()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public ProjectReviewVO getReviewVO(ProjectReview review) {
        if (review == null) {
            return null;
        }
        ProjectReviewVO vo = new ProjectReviewVO();
        BeanUtil.copyProperties(review, vo);
        // 关联用户信息
        User user = userService.getById(review.getUserId());
        if (user != null) {
            vo.setUser(userService.getUserVO(user));
        }
        return vo;
    }
}
